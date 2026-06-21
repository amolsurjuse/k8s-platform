import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.UUID

def slurper = new JsonSlurper()
def action = (Parameters ?: 'full').trim()
def baseUrl = props.getProperty('base_url', 'https://api.dev.electrahub.net').replaceAll('/+$', '')
def requestHostHeader = props.getProperty('request_host_header', '').trim()
def runId = props.getProperty('run_id')
if (!runId) {
  runId = String.valueOf(System.currentTimeMillis())
  props.put('run_id', runId)
}

int userOffset = props.getProperty('user_offset', '0') as int
int threadIndex = ctx.getThreadNum() + 1 + userOffset
String userNumber = String.format('%03d', threadIndex)
String defaultPassword = props.getProperty('test_password', 'LoadTest@12345')
String email = (vars.get('userEmail') ?: "jmeter+${runId}-${userNumber}@electrahub.test").trim()
String password = (vars.get('userPassword') ?: defaultPassword).trim()

String connectorId = vars.get('connectorId')
String chargerId = vars.get('chargerId')
String locationId = vars.get('locationId')
String connectorType = vars.get('connectorType') ?: 'CCS-2'
int connectorNumber = (vars.get('connectorNumber') ?: String.valueOf(threadIndex)) as int

int holdSeconds = props.getProperty('hold_seconds', '900') as int
int sseSeconds = props.getProperty('sse_seconds', String.valueOf(Math.min(holdSeconds, 120))) as int
BigDecimal walletTopupAmount = new BigDecimal(props.getProperty('wallet_topup_amount', '120.00'))
String usersOutput = props.getProperty('users_output', 'scripts/jmeter/data/generated-users.csv')
boolean dynamicConnectorSelection = props.getProperty('dynamic_connector_selection', 'false').toBoolean()

def logLine = { String message ->
  log.info("[electrahub-jmeter][${action}][${userNumber}] ${message}")
}

def json = { Object value -> JsonOutput.toJson(value) }

def parseJson = { String body ->
  if (!body) return null
  try {
    slurper.parseText(body)
  } catch (Exception ignored) {
    null
  }
}

def decodeJwtPayload = { String token ->
  def parts = token.split('\\.')
  if (parts.length < 2) return [:]
  String payload = parts[1]
  int padding = (4 - payload.length() % 4) % 4
  payload = payload + ('=' * padding)
  byte[] decoded = Base64.getUrlDecoder().decode(payload)
  parseJson(new String(decoded, StandardCharsets.UTF_8)) as Map
}

def request = { String method, String path, Object body = null, String token = null, int timeoutMs = 45000 ->
  URL url = new URL("${baseUrl}${path}")
  HttpURLConnection conn = (HttpURLConnection) url.openConnection()
  conn.setRequestMethod(method)
  conn.setConnectTimeout(timeoutMs)
  conn.setReadTimeout(timeoutMs)
  conn.setRequestProperty('Accept', 'application/json')
  conn.setRequestProperty('User-Agent', 'ElectraHubRegression/1.0')
  conn.setRequestProperty('X-ElectraHub-Test-Run', runId)
  if (requestHostHeader) {
    conn.setRequestProperty('Host', requestHostHeader)
  }
  if (token) {
    conn.setRequestProperty('Authorization', "Bearer ${token}")
  }
  if (body != null) {
    conn.setDoOutput(true)
    conn.setRequestProperty('Content-Type', 'application/json')
    byte[] bytes = json(body).getBytes(StandardCharsets.UTF_8)
    conn.setRequestProperty('Content-Length', String.valueOf(bytes.length))
    conn.outputStream.withCloseable { it.write(bytes) }
  }

  int status = conn.responseCode
  InputStream stream = status >= 400 ? conn.errorStream : conn.inputStream
  String responseBody = stream == null ? '' : stream.getText('UTF-8')
  [status: status, body: responseBody, json: parseJson(responseBody)]
}

def requireStatus = { Map response, List<Integer> statuses, String step ->
  if (!statuses.contains(response.status as int)) {
    throw new IllegalStateException("${step} failed with HTTP ${response.status}: ${response.body}")
  }
  response
}

def login = {
  def response = request('POST', '/auth/api/auth/login', [
    email: email,
    password: password
  ])
  requireStatus(response, [200], 'login')
  String token = response.json.accessToken as String
  if (!token) throw new IllegalStateException('login returned no accessToken')
  vars.put('accessToken', token)
  def claims = decodeJwtPayload(token)
  String uid = (claims.uid ?: claims.sub ?: vars.get('userId') ?: '') as String
  if (uid) vars.put('userId', uid)
  logLine("logged in uid=${uid ?: 'unknown'}")
  token
}

def registerOrLogin = {
  def payload = [
    email: email,
    password: password,
    firstName: 'JMeter',
    lastName: "User${userNumber}",
    phoneNumber: "+1555${String.format('%08d', threadIndex)}",
    address: [
      line1: '100 Load Test Way',
      line2: null,
      city: 'Test City',
      state: 'CA',
      postalCode: '94016',
      country: 'US'
    ]
  ]
  def response = request('POST', '/auth/api/auth/register', payload)
  if ((response.status as int) == 200) {
    String token = response.json.accessToken as String
    vars.put('accessToken', token)
    def claims = decodeJwtPayload(token)
    String uid = (claims.uid ?: claims.sub ?: '') as String
    if (uid) vars.put('userId', uid)
    logLine("registered uid=${uid ?: 'unknown'}")
    return token
  }
  logLine("register returned HTTP ${response.status}; falling back to login")
  login()
}

def appendGeneratedUser = {
  String uid = vars.get('userId') ?: ''
  if (!uid) return
  File file = new File(usersOutput)
  file.parentFile?.mkdirs()
  synchronized (this.class) {
    if (!file.exists()) {
      file.text = 'userEmail,userPassword,userId\n'
    }
    String line = "${email},${password},${uid}\n"
    if (!file.text.contains("${email},")) {
      file.append(line)
    }
  }
}

def acceptTerms = { String token ->
  def response = request('POST', '/auth/api/terms/accept', [
    platform: 'Web',
    deviceModel: 'TeamCity',
    appVersion: '1.0',
    deviceId: "jmeter-${runId}-${userNumber}",
    osVersion: 'load-test'
  ], token)
  requireStatus(response, [200, 201, 204], 'accept terms')
  String refreshed = response.json?.accessToken as String
  if (refreshed) {
    vars.put('accessToken', refreshed)
    logLine('accepted terms and received refreshed token')
    return refreshed
  }
  logLine('accepted terms; refreshing token through login')
  login()
}

def setupPayment = { String token ->
  def initialState = request('GET', '/payment/api/v1/payment/state', null, token)
  if ((initialState.status as int) == 451) {
    token = acceptTerms(token)
    initialState = request('GET', '/payment/api/v1/payment/state', null, token)
  }
  requireStatus(initialState, [200], 'payment state before setup')

  def card = request('POST', '/payment/api/v1/payment/cards', [
    brand: 'Visa',
    nickname: "JMeter ${userNumber}",
    cardNumber: "411111111111${String.format('%04d', threadIndex % 10000)}",
    expiry: '12/30'
  ], token)
  requireStatus(card, [201, 200, 409], 'add card')

  def topup = request('POST', '/payment/api/v1/payment/wallet/topups', [
    amount: walletTopupAmount,
    source: 'MANUAL',
    note: "JMeter ${runId}"
  ], token)
  requireStatus(topup, [201, 200], 'wallet topup')

  def state = requireStatus(request('GET', '/payment/api/v1/payment/state', null, token), [200], 'payment state after setup')
  logLine("payment ready wallet=${state.json?.wallet?.balance}")
  token
}

def discoverChargers = { String token ->
  def listQuery = '''query {
    ocpiChargers(countryCode: "US", limit: 100, offset: 0) {
      chargerId
      chargerName
      status
      availablePorts
      busyPorts
      location { ocpiLocationId name }
      evses { uid status connectors { id status available standard powerType } }
    }
  }'''
  def list = requireStatus(request('POST', '/charger/graphql', [query: listQuery], token), [200], 'charger graphql list')
  if (list.body.contains('"errors"')) {
    throw new IllegalStateException("charger graphql list returned errors: ${list.body}")
  }

  if (dynamicConnectorSelection) {
    def chargers = list.json?.data?.ocpiChargers ?: []
    def candidates = []
    for (def charger : chargers) {
      if (String.valueOf(charger.status ?: '').equalsIgnoreCase('OFFLINE')) continue
      for (def evse : (charger.evses ?: [])) {
        for (def connector : (evse.connectors ?: [])) {
          String connectorStatus = String.valueOf(connector.status ?: '')
          boolean connectorAvailable = connector.available == true || connectorStatus.equalsIgnoreCase('AVAILABLE')
          if (connectorAvailable) {
            candidates << [charger: charger, connector: connector]
          }
        }
      }
    }
    candidates.sort { left, right ->
      String leftKey = "${left.charger.chargerId ?: ''}/${left.connector.id ?: ''}"
      String rightKey = "${right.charger.chargerId ?: ''}/${right.connector.id ?: ''}"
      leftKey <=> rightKey
    }
    def selected = candidates.isEmpty() ? null : candidates[Math.floorMod(threadIndex - 1, candidates.size())]
    if (selected != null) {
      chargerId = selected.charger.chargerId as String
      locationId = selected.charger.location?.ocpiLocationId as String
      connectorId = selected.connector.id as String
      connectorType = (selected.connector.standard ?: connectorType) as String
      def match = connectorId =~ /(\\d+)$/
      if (match.find()) {
        connectorNumber = (match.group(1) as int)
      }
      vars.put('chargerId', chargerId)
      vars.put('locationId', locationId)
      vars.put('connectorId', connectorId)
      vars.put('connectorNumber', String.valueOf(connectorNumber))
      vars.put('connectorType', connectorType)
      logLine("selected available connector ${chargerId}/${connectorId} location=${locationId} candidate=${Math.floorMod(threadIndex - 1, candidates.size()) + 1}/${candidates.size()}")
    } else {
      logLine('dynamic connector selection found no available connector; using CSV connector')
    }
  }

  def viewQuery = """query {
    ocpiCharger(chargerId: "${chargerId}", connectorId: "${connectorId}") {
      chargerId
      chargerName
      status
      availablePorts
      busyPorts
      location { ocpiLocationId name }
      evses { uid status connectors { id status available standard powerType } }
    }
  }"""
  def view = requireStatus(request('POST', '/charger/graphql', [query: viewQuery], token), [200], 'charger graphql view')
  if (view.body.contains('"errors"')) {
    throw new IllegalStateException("charger graphql view returned errors for ${chargerId}/${connectorId}: ${view.body}")
  }
}

def startSession = { String token ->
  if (!chargerId || !connectorId || !locationId) {
    throw new IllegalStateException('chargerId, connectorId, and locationId are required. Check connectors CSV.')
  }
  String uid = vars.get('userId') ?: ''
  def payload = [
    chargerId: chargerId,
    locationId: locationId,
    connectorId: connectorId,
    connectorNumber: connectorNumber,
    connectorType: connectorType,
    idToken: uid,
    paymentMethod: 'WALLET',
    currency: 'USD',
    idempotencyKey: UUID.randomUUID().toString()
  ]
  def response = request('POST', '/session/api/v1/sessions/start', payload, token, 60000)
  requireStatus(response, [201], 'start charging session')
  String sessionId = response.json.sessionId as String
  if (!sessionId) throw new IllegalStateException("start response missing sessionId: ${response.body}")
  vars.put('sessionId', sessionId)
  logLine("started session=${sessionId} status=${response.json.status} remote=${response.json.remoteStartStatus}")
  sessionId
}

def activeSession = { String token ->
  requireStatus(request('GET', '/session/api/v1/sessions/active', null, token), [200], 'active sessions')
}

def monitorSse = { String token, String sessionId ->
  URL url = new URL("${baseUrl}/session/api/v1/sessions/active/stream")
  HttpURLConnection conn = (HttpURLConnection) url.openConnection()
  conn.setRequestMethod('GET')
  conn.setConnectTimeout(30000)
  conn.setReadTimeout(10000)
  conn.setRequestProperty('Accept', 'text/event-stream')
  conn.setRequestProperty('User-Agent', 'ElectraHubRegression/1.0')
  conn.setRequestProperty('Authorization', "Bearer ${token}")
  conn.setRequestProperty('X-ElectraHub-Test-Run', runId)
  if (requestHostHeader) {
    conn.setRequestProperty('Host', requestHostHeader)
  }

  int status = conn.responseCode
  if (status != 200) {
    String body = conn.errorStream == null ? '' : conn.errorStream.getText('UTF-8')
    throw new IllegalStateException("SSE open failed HTTP ${status}: ${body}")
  }

  long deadline = System.currentTimeMillis() + (sseSeconds * 1000L)
  int connected = 0
  int snapshots = 0
  int updates = 0
  int receipts = 0
  int heartbeats = 0
  String lastData = ''
  BufferedReader reader = new BufferedReader(new InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
  while (System.currentTimeMillis() < deadline) {
    try {
      String line = reader.readLine()
      if (line == null) break
      if (line.startsWith('event:')) {
        String eventName = line.substring(6).trim()
        if (eventName == 'connected') connected++
        if (eventName == 'snapshot') snapshots++
        if (eventName == 'session') updates++
        if (eventName == 'receipt') receipts++
        if (eventName == 'heartbeat') heartbeats++
      } else if (line.startsWith('data:')) {
        lastData = line.substring(5).trim()
        if (lastData.contains(sessionId) && lastData.contains('SESSION_UPDATED')) updates++
        if (lastData.contains(sessionId) && lastData.contains('SNAPSHOT')) snapshots++
      }
    } catch (SocketTimeoutException ignored) {
      // Keep the SSE connection open until the monitoring window expires.
    }
  }
  try { reader.close() } catch (Exception ignored) {}
  conn.disconnect()

  vars.put('sseConnectedEvents', String.valueOf(connected))
  vars.put('sseSnapshotEvents', String.valueOf(snapshots))
  vars.put('sseSessionEvents', String.valueOf(updates))
  vars.put('sseReceiptEvents', String.valueOf(receipts))
  vars.put('sseHeartbeatEvents', String.valueOf(heartbeats))

  if ((snapshots + updates) <= 0) {
    throw new IllegalStateException("SSE produced no snapshot/session updates for ${sessionId}; heartbeats=${heartbeats}, last=${lastData}")
  }
  logLine("sse ok connected=${connected} snapshot=${snapshots} updates=${updates} receipts=${receipts} heartbeats=${heartbeats}")
}

def stopSession = { String token, String sessionId ->
  def response = request('POST', "/session/api/v1/sessions/${sessionId}/stop", [
    reason: 'REMOTE',
    userInitiated: true
  ], token, 60000)
  requireStatus(response, [204, 200], 'stop charging session')
  logLine("stop requested session=${sessionId}")
}

def validateStoppedAndReceipt = { String token, String sessionId ->
  long deadline = System.currentTimeMillis() + 120000L
  boolean goneFromActive = false
  while (System.currentTimeMillis() < deadline) {
    def active = activeSession(token)
    if (!active.body.contains(sessionId)) {
      goneFromActive = true
      break
    }
    sleep(5000)
  }
  if (!goneFromActive) {
    throw new IllegalStateException("session ${sessionId} still appears in /sessions/active after stop")
  }

  def receipt = null
  deadline = System.currentTimeMillis() + 120000L
  while (System.currentTimeMillis() < deadline) {
    def response = request('GET', "/session/api/v1/sessions/${sessionId}/receipt", null, token, 30000)
    if ((response.status as int) == 200) {
      receipt = response
      break
    }
    sleep(5000)
  }
  if (receipt == null) {
    throw new IllegalStateException("receipt not available for session ${sessionId}")
  }
  requireStatus(request('GET', '/session/api/v1/sessions/history?page=0&size=10', null, token), [200], 'session history')
  requireStatus(request('GET', '/session/api/v1/sessions/dashboard-stats', null, token), [200], 'dashboard stats')
  logLine("receipt ok session=${sessionId} status=${receipt.json?.status} total=${receipt.json?.totalCost ?: receipt.json?.costUsd}")
}

try {
  long startedAt = System.currentTimeMillis()
  String token

  if (action == 'setup' || action == 'full') {
    token = registerOrLogin()
    appendGeneratedUser()
    token = setupPayment(token)
  } else if (action == 'charging') {
    token = login()
  } else {
    throw new IllegalArgumentException("Unsupported action '${action}'. Use setup, charging, or full.")
  }

  if (action == 'charging' || action == 'full') {
    discoverChargers(token)
    requireStatus(request('GET', '/payment/api/v1/payment/state', null, token), [200], 'payment state before start')
    String sessionId = startSession(token)
    activeSession(token)
    monitorSse(token, sessionId)

    long elapsedSeconds = (System.currentTimeMillis() - startedAt) / 1000L
    long remaining = Math.max(0L, holdSeconds - elapsedSeconds)
    if (remaining > 0L) {
      logLine("holding session for ${remaining}s before stop")
      sleep(remaining * 1000L)
    }

    stopSession(token, sessionId)
    validateStoppedAndReceipt(token, sessionId)
  }

  SampleResult.setSuccessful(true)
  SampleResult.setResponseCode('200')
  SampleResult.setResponseMessage("${action} completed for ${email}")
  SampleResult.setResponseData(JsonOutput.prettyPrint(json([
    action: action,
    email: email,
    userId: vars.get('userId'),
    chargerId: chargerId,
    connectorId: connectorId,
    sessionId: vars.get('sessionId'),
    sseSessionEvents: vars.get('sseSessionEvents'),
    sseSnapshotEvents: vars.get('sseSnapshotEvents'),
    completedAt: Instant.now().toString()
  ])), 'UTF-8')
} catch (Throwable t) {
  SampleResult.setSuccessful(false)
  SampleResult.setResponseCode('500')
  SampleResult.setResponseMessage(t.message ?: t.class.name)
  SampleResult.setResponseData((t.message ?: t.toString()), 'UTF-8')
  log.error("[electrahub-jmeter][${action}][${userNumber}] failed", t)
  throw t
}
