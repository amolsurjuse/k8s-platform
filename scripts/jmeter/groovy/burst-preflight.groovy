import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Locale

def slurper = new JsonSlurper()
String baseUrl = props.getProperty('base_url', 'https://api.dev.electrahub.net').replaceAll('/+$', '')
String simulatorUrl = props.getProperty('simulator_url', 'https://ocpp-simulator-dev.electrahub.net').replaceAll('/+$', '')
int requiredUsers = props.getProperty('users', '10') as int
int pageSize = Math.min(200, Math.max(1, props.getProperty('charger_page_size', '200') as int))
int maxPages = Math.max(1, props.getProperty('charger_max_pages', '10') as int)
String connectorsFile = props.getProperty('connectors_file', 'outputs/jmeter/burst/connectors.csv')
String runId = props.getProperty('run_id', String.valueOf(System.currentTimeMillis()))
String chargerIdPrefix = props.getProperty('charger_id_prefix', 'EH-US-').trim().toUpperCase(Locale.ROOT)

def parseJson = { String value ->
  value ? slurper.parseText(value) : null
}

def writeRequest = { HttpURLConnection connection, Object body ->
  if (body == null) return
  byte[] bytes = JsonOutput.toJson(body).getBytes(StandardCharsets.UTF_8)
  connection.doOutput = true
  connection.setRequestProperty('Content-Type', 'application/json')
  connection.setRequestProperty('Content-Length', String.valueOf(bytes.length))
  connection.outputStream.withCloseable { stream -> stream.write(bytes) }
}

def request = { String method, String endpoint, Object body = null, String authorization = null ->
  HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection()
  connection.requestMethod = method
  connection.connectTimeout = 30000
  connection.readTimeout = 60000
  connection.setRequestProperty('Accept', 'application/json')
  connection.setRequestProperty('User-Agent', 'ElectraHubBurstPreflight/1.0')
  connection.setRequestProperty('X-ElectraHub-Test-Run', runId)
  if (authorization != null && !authorization.trim().isEmpty()) {
    connection.setRequestProperty('Authorization', "Bearer ${authorization.trim()}")
  }
  writeRequest(connection, body)
  try {
    int status = connection.responseCode
    InputStream stream = status >= 400 ? connection.errorStream : connection.inputStream
    String response = stream == null ? '' : stream.getText('UTF-8')
    if (status != 200) {
      throw new IllegalStateException("preflight ${method} ${endpoint} failed with HTTP ${status}: ${response}")
    }
    return parseJson(response)
  } finally {
    connection.disconnect()
  }
}

try {
  if (requiredUsers <= 0) {
    throw new IllegalArgumentException('users must be greater than zero')
  }

  def simulatorResponse = request('GET', "${simulatorUrl}/api/v1/chargers?limit=1000")
  def simulatorChargers = simulatorResponse?.items instanceof List ? simulatorResponse.items : []
  Map<String, Map> availableSimulatorConnectors = [:]
  int simulatorConnectedChargers = 0
  int simulatorAvailableConnectors = 0

  simulatorChargers.each { charger ->
    boolean connected = String.valueOf(charger.connectionState ?: '').equalsIgnoreCase('CONNECTED')
    if (connected) simulatorConnectedChargers++
    (charger.connectorSummary instanceof List ? charger.connectorSummary : []).each { connector ->
      String chargerId = String.valueOf(charger.chargerId ?: '').trim()
      String connectorRef = String.valueOf(connector.connectorRef ?: '').trim()
      String status = String.valueOf(connector.status ?: '').trim()
      boolean active = (charger.activeConnectorRefs instanceof List) && charger.activeConnectorRefs.contains(connectorRef)
      if (connected && !active && chargerId && connectorRef && status.equalsIgnoreCase('AVAILABLE')) {
        String key = "${chargerId}/${connectorRef}"
        availableSimulatorConnectors[key] = [
          connectorNumber: (connector.connectorId ?: 1) as int,
          connectorType: String.valueOf(connector.type ?: 'UNKNOWN')
        ]
        simulatorAvailableConnectors++
      }
    }
  }

  Map<String, Map> candidatesByKey = [:]
  int graphQlChargers = 0
  int graphQlAvailableConnectors = 0
  for (int page = 0; page < maxPages; page++) {
    int offset = page * pageSize
    String query = """query {
      ocpiChargers(limit: ${pageSize}, offset: ${offset}) {
        chargerId
        status
        location { ocpiLocationId }
        evses { connectors { id status available standard } }
      }
    }"""
    def graphqlResponse = request('POST', "${baseUrl}/charger/graphql", [query: query])
    if (graphqlResponse?.errors) {
      throw new IllegalStateException("charger inventory returned GraphQL errors at offset ${offset}: ${JsonOutput.toJson(graphqlResponse.errors)}")
    }
    def chargers = graphqlResponse?.data?.ocpiChargers instanceof List ? graphqlResponse.data.ocpiChargers : []
    graphQlChargers += chargers.size()
    chargers.each { charger ->
      String chargerId = String.valueOf(charger.chargerId ?: '').trim()
      String locationId = String.valueOf(charger.location?.ocpiLocationId ?: '').trim()
      (charger.evses instanceof List ? charger.evses : []).each { evse ->
        (evse.connectors instanceof List ? evse.connectors : []).each { connector ->
          String connectorId = String.valueOf(connector.id ?: '').trim()
          String connectorStatus = String.valueOf(connector.status ?: '').trim()
          boolean available = connector.available == true || connectorStatus.equalsIgnoreCase('AVAILABLE')
          if (available) graphQlAvailableConnectors++
          String key = "${chargerId}/${connectorId}"
          def simulator = availableSimulatorConnectors[key]
          if (available && chargerId && locationId && connectorId && simulator != null && (!chargerIdPrefix || chargerId.toUpperCase(Locale.ROOT).startsWith(chargerIdPrefix))) {
            candidatesByKey[key] = [
              chargerId: chargerId,
              locationId: locationId,
              connectorId: connectorId,
              connectorNumber: simulator.connectorNumber,
              connectorType: String.valueOf(connector.standard ?: simulator.connectorType ?: 'UNKNOWN')
            ]
          }
        }
      }
    }
    if (chargers.size() < pageSize) break
  }

  String cleanupAdminToken = String.valueOf(System.getenv('ELECTRAHUB_LOAD_CLEANUP_ADMIN_TOKEN') ?: '').trim()
  if (cleanupAdminToken.isEmpty()) {
    throw new IllegalStateException('burst preflight requires ELECTRAHUB_LOAD_CLEANUP_ADMIN_TOKEN to exclude sessions already active in the session ledger')
  }

  Set<String> activeSessionPairs = new LinkedHashSet<>()
  List<String> candidateChargerIds = candidatesByKey.values()
    .collect { String.valueOf(it.chargerId ?: '').trim() }
    .findAll { !it.isEmpty() }
    .unique()

  candidateChargerIds.collate(100).each { chargerIds ->
    String query = chargerIds.collect { chargerId ->
      "chargerIds=${URLEncoder.encode(chargerId, StandardCharsets.UTF_8)}"
    }.join('&')
    def activeResponse = request(
      'GET',
      "${baseUrl}/session/api/v1/sessions/internal/active-by-chargers?${query}",
      null,
      cleanupAdminToken
    )
    def activeSessions = activeResponse instanceof List ? activeResponse : []
    activeSessions.each { session ->
      String chargerId = String.valueOf(session.chargerId ?: '').trim()
      String connectorRef = String.valueOf(session.connectorRef ?: '').trim()
      if (!chargerId.isEmpty() && !connectorRef.isEmpty()) {
        activeSessionPairs.add("${chargerId}/${connectorRef}")
      }
    }
  }

  def candidates = candidatesByKey.values()
    .findAll { candidate -> !activeSessionPairs.contains("${candidate.chargerId}/${candidate.connectorId}") }
    .toList()
    .sort { left, right ->
    "${left.chargerId}/${left.connectorId}" <=> "${right.chargerId}/${right.connectorId}"
  }
  if (candidates.size() < requiredUsers) {
    throw new IllegalStateException("burst preflight found only ${candidates.size()} exclusive live connectors for ${requiredUsers} requested users; simulatorConnected=${simulatorConnectedChargers}, simulatorAvailable=${simulatorAvailableConnectors}, graphQlChargers=${graphQlChargers}, graphQlAvailable=${graphQlAvailableConnectors}")
  }

  File target = new File(connectorsFile)
  target.parentFile?.mkdirs()
  List<Map> selected = candidates.take(requiredUsers)
  target.withWriter('UTF-8') { writer ->
    writer.write('chargerId,locationId,connectorId,connectorNumber,connectorType\n')
    selected.each { connector ->
      writer.write("${connector.chargerId},${connector.locationId},${connector.connectorId},${connector.connectorNumber},${connector.connectorType}\n")
    }
  }

  File summary = new File(target.parentFile, 'preflight-summary.json')
  summary.setText(JsonOutput.prettyPrint(JsonOutput.toJson([
    runId: runId,
    generatedAt: Instant.now().toString(),
    requestedUsers: requiredUsers,
    allocatedConnectors: selected.size(),
    simulatorConnectedChargers: simulatorConnectedChargers,
    simulatorAvailableConnectors: simulatorAvailableConnectors,
    graphQlChargersScanned: graphQlChargers,
    graphQlAvailableConnectors: graphQlAvailableConnectors,
    eligibleExclusiveConnectors: candidates.size(),
    activeSessionPairsFiltered: activeSessionPairs.size(),
    chargerIdPrefix: chargerIdPrefix,
    selected: selected
  ])), 'UTF-8')

  SampleResult.setSuccessful(true)
  SampleResult.setResponseCode('200')
  SampleResult.setResponseMessage("allocated ${selected.size()} exclusive connectors")
  SampleResult.setResponseData(JsonOutput.toJson([
    requestedUsers: requiredUsers,
    allocatedConnectors: selected.size(),
    eligibleExclusiveConnectors: candidates.size(),
    connectorFile: target.path
  ]), 'UTF-8')
  log.info("[electrahub-jmeter][burst-preflight] allocated=${selected.size()} eligible=${candidates.size()} simulatorAvailable=${simulatorAvailableConnectors} graphQlAvailable=${graphQlAvailableConnectors}")
} catch (Throwable failure) {
  SampleResult.setSuccessful(false)
  SampleResult.setResponseCode('500')
  SampleResult.setResponseMessage(failure.message ?: failure.class.name)
  SampleResult.setResponseData(failure.message ?: failure.toString(), 'UTF-8')
  log.error('[electrahub-jmeter][burst-preflight] failed', failure)
  throw failure
}
