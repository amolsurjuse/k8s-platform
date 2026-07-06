import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.Locale
import java.util.UUID

def slurper = new JsonSlurper()
def action = (Parameters ?: 'full').trim()
def baseUrl = props.getProperty('base_url', 'https://api.dev.electrahub.net').replaceAll('/+$', '')
def simulatorUrl = props.getProperty('simulator_url', 'https://ocpp-simulator-dev.electrahub.net').replaceAll('/+$', '')
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
int connectorNumber = (vars.get('connectorNumber') ?: '1') as int

int holdSeconds = props.getProperty('hold_seconds', '900') as int
int sseSeconds = props.getProperty('sse_seconds', String.valueOf(Math.min(holdSeconds, 120))) as int
int requestTimeoutMs = props.getProperty('request_timeout_ms', '120000') as int
int sessionCommandTimeoutMs = props.getProperty('session_command_timeout_ms', '180000') as int
BigDecimal walletTopupAmount = new BigDecimal(props.getProperty('wallet_topup_amount', '120.00'))
String usersOutput = props.getProperty('users_output', 'scripts/jmeter/data/generated-users.csv')
String connectorsCsv = props.getProperty('connectors_csv', 'scripts/jmeter/data/connectors-100.csv')
boolean dynamicConnectorSelection = props.getProperty('dynamic_connector_selection', 'false').toBoolean()
String currentStep = 'init'

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

def atStep = { String stepName, Closure work ->
  currentStep = stepName
  logLine("step=${stepName}")
  work()
}

def readCsvConnectorCandidates = {
  File file = new File(connectorsCsv)
  if (!file.exists()) {
    logLine("connector csv not found: ${connectorsCsv}")
    return []
  }
  def lines = file.readLines('UTF-8').findAll { it?.trim() }
  if (lines.size() <= 1) return []
  def header = lines[0].split(',', -1).collect { it.trim() }
  def indexOf = { String name -> header.findIndexOf { it == name } }
  int chargerIndex = indexOf('chargerId')
  int locationIndex = indexOf('locationId')
  int connectorIndex = indexOf('connectorId')
  int connectorNumberIndex = indexOf('connectorNumber')
  int connectorTypeIndex = indexOf('connectorType')
  if ([chargerIndex, locationIndex, connectorIndex, connectorNumberIndex, connectorTypeIndex].any { it < 0 }) {
    throw new IllegalStateException("connector csv missing required headers: ${connectorsCsv}")
  }
  lines.drop(1).collect { String line ->
    def cols = line.split(',', -1).collect { it.trim() }
    [
      chargerId: cols[chargerIndex],
      locationId: cols[locationIndex],
      connectorId: cols[connectorIndex],
      connectorNumber: (cols[connectorNumberIndex] ?: '1') as int,
      connectorType: cols[connectorTypeIndex] ?: connectorType
    ]
  }.findAll { it.chargerId && it.locationId && it.connectorId }
}

def request = { String method, String path, Object body = null, String token = null, int timeoutMs = requestTimeoutMs ->
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

  try {
    long started = System.currentTimeMillis()
    int status = conn.responseCode
    InputStream stream = status >= 400 ? conn.errorStream : conn.inputStream
    String responseBody = stream == null ? '' : stream.getText('UTF-8')
    long elapsed = System.currentTimeMillis() - started
    logLine("http ${method} ${path} status=${status} elapsedMs=${elapsed}")
    [status: status, body: responseBody, json: parseJson(responseBody)]
  } catch (SocketTimeoutException timeout) {
    throw new SocketTimeoutException("step=${currentStep} ${method} ${path} timed out after ${timeoutMs}ms")
  } finally {
    conn.disconnect()
  }
}

def simulatorRequest = { String method, String path, Object body = null, int timeoutMs = requestTimeoutMs ->
  URL url = new URL("${simulatorUrl}${path}")
  HttpURLConnection conn = (HttpURLConnection) url.openConnection()
  conn.setRequestMethod(method)
  conn.setConnectTimeout(timeoutMs)
  conn.setReadTimeout(timeoutMs)
  conn.setRequestProperty('Accept', 'application/json')
  conn.setRequestProperty('User-Agent', 'ElectraHubRegression/1.0')
  conn.setRequestProperty('X-ElectraHub-Test-Run', runId)
  if (body != null) {
    conn.setDoOutput(true)
    conn.setRequestProperty('Content-Type', 'application/json')
    byte[] bytes = json(body).getBytes(StandardCharsets.UTF_8)
    conn.setRequestProperty('Content-Length', String.valueOf(bytes.length))
    conn.outputStream.withCloseable { it.write(bytes) }
  }

  try {
    long started = System.currentTimeMillis()
    int status = conn.responseCode
    InputStream stream = status >= 400 ? conn.errorStream : conn.inputStream
    String responseBody = stream == null ? '' : stream.getText('UTF-8')
    long elapsed = System.currentTimeMillis() - started
    logLine("simulator ${method} ${path} status=${status} elapsedMs=${elapsed}")
    [status: status, body: responseBody, json: parseJson(responseBody)]
  } catch (SocketTimeoutException timeout) {
    throw new SocketTimeoutException("step=${currentStep} simulator ${method} ${path} timed out after ${timeoutMs}ms")
  } finally {
    conn.disconnect()
  }
}

def requireStatus = { Map response, List<Integer> statuses, String step ->
  if (!statuses.contains(response.status as int)) {
    throw new IllegalStateException("${step} failed with HTTP ${response.status}: ${response.body}")
  }
  response
}

def login = {
  def response = atStep('login') { request('POST', '/auth/api/auth/login', [
    email: email,
    password: password
  ]) }
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
  def response = atStep('register') { request('POST', '/auth/api/auth/register', payload) }
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
  def response = atStep('accept terms') { request('POST', '/auth/api/terms/accept', [
    platform: 'Web',
    deviceModel: 'TeamCity',
    appVersion: '1.0',
    deviceId: "jmeter-${runId}-${userNumber}",
    osVersion: 'load-test'
  ], token) }
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
  def initialState = atStep('payment state before setup') { request('GET', '/payment/api/v1/payment/state', null, token) }
  if ((initialState.status as int) == 451) {
    token = acceptTerms(token)
    initialState = atStep('payment state after terms') { request('GET', '/payment/api/v1/payment/state', null, token) }
  }
  requireStatus(initialState, [200], 'payment state before setup')

  def card = atStep('add card') { request('POST', '/payment/api/v1/payment/cards', [
    brand: 'Visa',
    nickname: "JMeter ${userNumber}",
    cardNumber: "411111111111${String.format('%04d', threadIndex % 10000)}",
    expiry: '12/30'
  ], token) }
  requireStatus(card, [201, 200, 409], 'add card')

  def topup = atStep('wallet topup') { request('POST', '/payment/api/v1/payment/wallet/topups', [
    amount: walletTopupAmount,
    source: 'MANUAL',
    note: "JMeter ${runId}"
  ], token) }
  requireStatus(topup, [201, 200], 'wallet topup')

  def state = requireStatus(atStep('payment state after setup') { request('GET', '/payment/api/v1/payment/state', null, token) }, [200], 'payment state after setup')
  logLine("payment ready wallet=${state.json?.wallet?.balance}")
  token
}

def discoverChargers = { String token ->
  String countryArg = props.getProperty('charger_country_code', '').trim()
  if (!countryArg && action == 'idle-fee') {
    countryArg = 'US'
  }
  String countryFilter = countryArg ? "countryCode: \"${countryArg}\", " : ''
  def listQuery = """query {
    ocpiChargers(${countryFilter}limit: 100, offset: 0) {
      chargerId
      chargerName
      status
      availablePorts
      busyPorts
      location { ocpiLocationId name }
      pricing { idleFee { enabled pricePerMinute currency sourceTariffId } }
      evses { uid status connectors { id status available standard powerType tariffIds tariffs { tariffId energyPrice parkingPrice currency } } }
    }
  }"""
  def list = requireStatus(atStep('charger graphql list') { request('POST', '/charger/graphql', [query: listQuery], token) }, [200], 'charger graphql list')
  if (list.body.contains('"errors"')) {
    throw new IllegalStateException("charger graphql list returned errors: ${list.body}")
  }

  if (dynamicConnectorSelection) {
    def chargers = list.json?.data?.ocpiChargers ?: []
    def activePairs = [] as Set
    def activeBeforeSelection = requireStatus(atStep('active sessions before connector selection') {
      request('GET', '/session/api/v1/sessions/active', null, token)
    }, [200], 'active sessions before connector selection')
    def activeSessions = activeBeforeSelection.json instanceof List ? activeBeforeSelection.json : []
    for (def session : activeSessions) {
      String activeChargerId = String.valueOf(session.simulator?.chargerId ?: session.chargerId ?: '')
      String activeConnectorId = String.valueOf(session.simulator?.connectorId ?: session.connectorId ?: '')
      if (activeChargerId && activeConnectorId) {
        activePairs << "${activeChargerId}/${activeConnectorId}"
      }
    }
    def candidates = []
    for (def charger : chargers) {
      if (String.valueOf(charger.status ?: '').equalsIgnoreCase('OFFLINE')) continue
      for (def evse : (charger.evses ?: [])) {
        for (def connector : (evse.connectors ?: [])) {
          String connectorStatus = String.valueOf(connector.status ?: '')
          boolean connectorAvailable = connector.available == true || connectorStatus.equalsIgnoreCase('AVAILABLE')
          boolean idleFeeOk = action != 'idle-fee' || charger.pricing?.idleFee?.enabled == true
          boolean notAlreadyActive = !activePairs.contains("${charger.chargerId ?: ''}/${connector.id ?: ''}")
          if (connectorAvailable && idleFeeOk && notAlreadyActive) {
            candidates << [charger: charger, connector: connector]
          }
        }
      }
    }
    logLine("dynamic connector selection candidates=${candidates.size()} activePairs=${activePairs.size()}")
    candidates.sort { left, right ->
      String leftKey = "${left.charger.chargerId ?: ''}/${left.connector.id ?: ''}"
      String rightKey = "${right.charger.chargerId ?: ''}/${right.connector.id ?: ''}"
      leftKey <=> rightKey
    }
    def serializedCandidates = candidates.collect { item ->
      [
        chargerId: item.charger.chargerId as String,
        locationId: item.charger.location?.ocpiLocationId as String,
        connectorId: item.connector.id as String,
        connectorNumber: 1,
        connectorType: (item.connector.standard ?: connectorType) as String
      ]
    }
    vars.put('connectorCandidatesJson', json(serializedCandidates))
    def selected = candidates.isEmpty() ? null : candidates[Math.floorMod(threadIndex - 1, candidates.size())]
    if (selected != null) {
      chargerId = selected.charger.chargerId as String
      locationId = selected.charger.location?.ocpiLocationId as String
      connectorId = selected.connector.id as String
      connectorType = (selected.connector.standard ?: connectorType) as String
      connectorNumber = 1
      vars.put('chargerId', chargerId)
      vars.put('locationId', locationId)
      vars.put('connectorId', connectorId)
      vars.put('connectorNumber', String.valueOf(connectorNumber))
      vars.put('connectorType', connectorType)
      logLine("selected available connector ${chargerId}/${connectorId} location=${locationId} candidate=${Math.floorMod(threadIndex - 1, candidates.size()) + 1}/${candidates.size()}")
    } else if (action == 'idle-fee') {
      throw new IllegalStateException('dynamic connector selection found no available idle-fee connector')
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
      pricing { tariffs { tariffId energyPrice parkingPrice currency } idleFee { enabled pricePerMinute currency sourceTariffId } }
      evses { uid status connectors { id status available standard powerType tariffIds tariffs { tariffId energyPrice parkingPrice currency } } }
    }
  }"""
  def view = requireStatus(atStep('charger graphql view') { request('POST', '/charger/graphql', [query: viewQuery], token) }, [200], 'charger graphql view')
  if (view.body.contains('"errors"')) {
    throw new IllegalStateException("charger graphql view returned errors for ${chargerId}/${connectorId}: ${view.body}")
  }
  if (action == 'idle-fee') {
    def charger = view.json?.data?.ocpiCharger
    def idleFee = charger?.pricing?.idleFee
    if (idleFee?.enabled != true) {
      throw new IllegalStateException("selected charger ${chargerId}/${connectorId} does not expose enabled idle fee: ${view.body}")
    }
    if ((idleFee?.pricePerMinute ?: 0) as BigDecimal <= 0) {
      throw new IllegalStateException("selected charger ${chargerId}/${connectorId} has no positive idle fee rate: ${view.body}")
    }
    vars.put('idleFeePerMinute', String.valueOf(idleFee.pricePerMinute))
    vars.put('idleFeeCurrency', String.valueOf(idleFee.currency ?: 'USD'))
    logLine("idle fee discovery ok charger=${chargerId}/${connectorId} rate=${idleFee.pricePerMinute} ${idleFee.currency}")
  }
}

def startSession = { String token ->
  if (!chargerId || !connectorId || !locationId) {
    throw new IllegalStateException('chargerId, connectorId, and locationId are required. Check connectors CSV.')
  }
  String uid = vars.get('userId') ?: ''
  def attempts = []
  String candidatesJson = vars.get('connectorCandidatesJson')
  if (dynamicConnectorSelection && candidatesJson) {
    def parsedCandidates = parseJson(candidatesJson) ?: []
    if (!parsedCandidates.isEmpty()) {
      int startAt = Math.floorMod(threadIndex - 1, parsedCandidates.size())
      int maxAttempts = Math.min(parsedCandidates.size(), Math.max(5, props.getProperty('connector_start_attempts', String.valueOf(parsedCandidates.size())) as int))
      for (int i = 0; i < maxAttempts; i++) {
        attempts << parsedCandidates[(startAt + i) % parsedCandidates.size()]
      }
    }
  }
  if (attempts.isEmpty()) {
    def csvCandidates = readCsvConnectorCandidates()
    if (!csvCandidates.isEmpty()) {
      int startAt = Math.max(0, Math.min(threadIndex - 1, csvCandidates.size() - 1))
      int maxAttempts = Math.min(csvCandidates.size(), Math.max(5, props.getProperty('connector_start_attempts', String.valueOf(csvCandidates.size())) as int))
      for (int i = 0; i < maxAttempts; i++) {
        attempts << csvCandidates[(startAt + i) % csvCandidates.size()]
      }
      logLine("using connector csv fallback attempts=${attempts.size()} startIndex=${startAt + 1}/${csvCandidates.size()}")
    } else {
      attempts << [
        chargerId: chargerId,
        locationId: locationId,
        connectorId: connectorId,
        connectorNumber: connectorNumber,
        connectorType: connectorType
      ]
    }
  }

  def lastResponse = null
  int attemptNumber = 0
  for (def candidate : attempts) {
    attemptNumber++
    chargerId = candidate.chargerId as String
    locationId = candidate.locationId as String
    connectorId = candidate.connectorId as String
    connectorType = (candidate.connectorType ?: connectorType) as String
    connectorNumber = (candidate.connectorNumber ?: connectorNumber ?: 1) as int
    vars.put('chargerId', chargerId)
    vars.put('locationId', locationId)
    vars.put('connectorId', connectorId)
    vars.put('connectorNumber', String.valueOf(connectorNumber))
    vars.put('connectorType', connectorType)

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
    def response = atStep("start charging attempt ${attemptNumber}") { request('POST', '/session/api/v1/sessions/start', payload, token, sessionCommandTimeoutMs) }
    lastResponse = response
    if ((response.status as int) == 201) {
      String sessionId = response.json.sessionId as String
      if (!sessionId) throw new IllegalStateException("start response missing sessionId: ${response.body}")
      vars.put('sessionId', sessionId)
      logLine("started session=${sessionId} charger=${chargerId}/${connectorId} status=${response.json.status} remote=${response.json.remoteStartStatus} attempt=${attemptNumber}/${attempts.size()}")
      return sessionId
    }
    if ([409, 503].contains(response.status as int) && attemptNumber < attempts.size()) {
      logLine("start skipped ${chargerId}/${connectorId} HTTP ${response.status}; trying next connector")
      sleep(1000)
      continue
    }
    break
  }

  requireStatus(lastResponse, [201], 'start charging session')
}

def activeSession = { String token ->
  requireStatus(atStep('active sessions') { request('GET', '/session/api/v1/sessions/active', null, token) }, [200], 'active sessions')
}

def findActiveSession = { String token, String sessionId ->
  def response = activeSession(token)
  def sessions = response.json instanceof List ? response.json : []
  sessions.find { String.valueOf(it.id ?: '') == sessionId }
}

def waitForActiveSessionPredicate = { String token, String sessionId, String description, int timeoutSeconds, Closure<Boolean> predicate ->
  long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L)
  def lastSession = null
  while (System.currentTimeMillis() < deadline) {
    lastSession = findActiveSession(token, sessionId)
    if (lastSession != null && predicate(lastSession)) {
      logLine("active session predicate ok ${description} session=${sessionId}")
      return lastSession
    }
    sleep(3000)
  }
  throw new IllegalStateException("session ${sessionId} did not satisfy ${description}; last=${json(lastSession)}")
}

def sendSimulatorStatus = { String status, String errorCode = 'NoError' ->
  int number = (vars.get('connectorNumber') ?: String.valueOf(connectorNumber ?: 1)) as int
  def response = atStep("simulator status ${status}") {
    simulatorRequest('POST', "/api/v1/chargers/${chargerId}/connectors/${number}/status", [
      status: status,
      errorCode: errorCode
    ])
  }
  requireStatus(response, [200, 202], "simulator status ${status}")
}

def sendBackendOcppStatus = { String status, String errorCode = 'NoError' ->
  int number = (vars.get('connectorNumber') ?: String.valueOf(connectorNumber ?: 1)) as int
  String token = vars.get('accessToken')
  def response = atStep("backend ocpp status ${status}") {
    request('POST', '/session/api/v1/sessions/ocpp/status-notification', [
      chargePointId: chargerId,
      connectorId: number,
      status: status,
      errorCode: errorCode,
      timestamp: Instant.now().toString()
    ], token)
  }
  requireStatus(response, [200, 202, 204], "backend ocpp status ${status}")
  logLine("backend OCPP status accepted status=${status} charger=${chargerId} connectorNumber=${number}")
}

def sendSimulatorMeterValue = { long meterWh ->
  int number = (vars.get('connectorNumber') ?: String.valueOf(connectorNumber ?: 1)) as int
  def response = atStep('simulator meter value') {
    simulatorRequest('POST', "/api/v1/chargers/${chargerId}/connectors/${number}/meter-values/send", [
      meterWh: meterWh,
      energyWh: Math.max(0L, meterWh),
      currentPowerW: props.getProperty('idle_flow_power_w', '44000') as long
    ])
  }
  requireStatus(response, [200, 202], 'simulator meter value')
}

def sendSimulatorChargingStop = {
  int number = (vars.get('connectorNumber') ?: String.valueOf(connectorNumber ?: 1)) as int
  long meterStopWh = (props.getProperty('idle_flow_meter_stop_wh', props.getProperty('idle_flow_meter_wh', '1201500')) as long)
  def response = atStep('simulator charging stop') {
    simulatorRequest('POST', "/api/v1/chargers/${chargerId}/connectors/${number}/charging/stop", [
      reason: 'EVDisconnected',
      meterStopWh: meterStopWh,
      forwardOcpp: true
    ])
  }
  if ((response.status as int) == 404 && String.valueOf(response.body ?: '').toLowerCase(Locale.ROOT).contains('transaction not found')) {
    logLine("simulator charging stop had no active transaction; falling back to OCPP Available unplug signal charger=${chargerId} connectorNumber=${number}")
    sendSimulatorStatus('Available')
    if ((props.getProperty('allow_backend_ocpp_status_fallback', 'false') as String).toBoolean()) {
      sendBackendOcppStatus('Available')
    }
    return
  }
  requireStatus(response, [200, 202], 'simulator charging stop')
  logLine("simulator charging stop accepted charger=${chargerId} connectorNumber=${number} meterStopWh=${meterStopWh}")
}

def monitorSse = { String token, String sessionId ->
  currentStep = 'monitor SSE'
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
  def response = atStep('stop charging session') { request('POST', "/session/api/v1/sessions/${sessionId}/stop", [
    reason: 'REMOTE',
    userInitiated: true
  ], token, sessionCommandTimeoutMs) }
  requireStatus(response, [204, 200], 'stop charging session')
  logLine("stop requested session=${sessionId}")
}

def validateStoppedAndReceipt = { String token, String sessionId, boolean expectSubscriptionDiscount = false ->
  long deadline = System.currentTimeMillis() + 120000L
  boolean goneFromActive = false
  boolean unplugRequested = false
  while (System.currentTimeMillis() < deadline) {
    def active = activeSession(token)
    def sessions = active.json instanceof List ? active.json : []
    def activeMatch = sessions.find { String.valueOf(it.id ?: '') == sessionId }
    if (activeMatch == null) {
      goneFromActive = true
      break
    }
    if (!unplugRequested &&
      activeMatch.idleFeeEnabled == true &&
      activeMatch.unplugRequiredToStop == true &&
      String.valueOf(activeMatch.status ?: '').equalsIgnoreCase('SUSPENDED')) {
      logLine("session ${sessionId} is idle-fee protected after remote stop; simulating physical unplug before receipt validation")
      sendSimulatorChargingStop()
      unplugRequested = true
    }
    sleep(5000)
  }
  if (!goneFromActive) {
    throw new IllegalStateException("session ${sessionId} still appears in /sessions/active after stop")
  }

  def receipt = null
  deadline = System.currentTimeMillis() + 120000L
  while (System.currentTimeMillis() < deadline) {
    def response = atStep('receipt') { request('GET', "/session/api/v1/sessions/${sessionId}/receipt", null, token, requestTimeoutMs) }
    if ((response.status as int) == 200) {
      receipt = response
      break
    }
    sleep(5000)
  }
  if (receipt == null) {
    throw new IllegalStateException("receipt not available for session ${sessionId}")
  }
  if (expectSubscriptionDiscount) {
    BigDecimal regularCost = ((receipt.json?.regularCost ?: 0) as BigDecimal)
    BigDecimal totalCost = ((receipt.json?.totalCost ?: receipt.json?.costUsd ?: 0) as BigDecimal)
    BigDecimal discount = ((receipt.json?.subscriptionDiscountAmount ?: 0) as BigDecimal)
    if (!(discount > 0 && regularCost > totalCost && receipt.json?.subscriptionPlanCode)) {
      throw new IllegalStateException("receipt missing expected subscription discount fields for ${sessionId}: regular=${regularCost} total=${totalCost} discount=${discount} plan=${receipt.json?.subscriptionPlanCode}")
    }
  }
  requireStatus(atStep('session history') { request('GET', '/session/api/v1/sessions/history?page=0&size=10', null, token) }, [200], 'session history')
  requireStatus(atStep('dashboard stats') { request('GET', '/session/api/v1/sessions/dashboard-stats', null, token) }, [200], 'dashboard stats')
  logLine("receipt ok session=${sessionId} status=${receipt.json?.status} total=${receipt.json?.totalCost ?: receipt.json?.costUsd}")
}

def validateSubscriptionDiscountFlow = { String token, String sessionId ->
  def discounted = waitForActiveSessionPredicate(token, sessionId, 'subscription discount active price', 120) { session ->
    BigDecimal regularCost = ((session.regularCost ?: 0) as BigDecimal)
    BigDecimal discountedCost = ((session.discountedCost ?: session.estimatedCost ?: 0) as BigDecimal)
    BigDecimal discount = ((session.subscriptionDiscountAmount ?: 0) as BigDecimal)
    session.subscriptionDiscountApplied == true && discount > 0 && regularCost > discountedCost && session.subscriptionPlanCode != null
  }
  logLine("subscription discount active session=${sessionId} regular=${discounted.regularCost} discounted=${discounted.discountedCost ?: discounted.estimatedCost} discount=${discounted.subscriptionDiscountAmount} plan=${discounted.subscriptionPlanCode}")
  stopSession(token, sessionId)
  sendSimulatorStatus('Available')
  validateStoppedAndReceipt(token, sessionId, true)
}

def validateIdleFeeFlow = { String token, String sessionId ->
  waitForActiveSessionPredicate(token, sessionId, 'idle fee policy snapshot', 60) { session ->
    session.idleFeeEnabled == true && ((session.idleFeePerMinute ?: 0) as BigDecimal) > 0
  }

  sendSimulatorMeterValue(props.getProperty('idle_flow_meter_wh', '1201500') as long)
  stopSession(token, sessionId)
  def afterRemoteStop = waitForActiveSessionPredicate(token, sessionId, 'remote stop requires unplug', 60) { session ->
    session.idleFeeEnabled == true &&
      session.unplugRequiredToStop == true &&
      String.valueOf(session.status ?: '').equalsIgnoreCase('SUSPENDED') &&
      session.idleStartedAt != null
  }
  logLine("remote stop kept idle-fee session active session=${sessionId} status=${afterRemoteStop.status} idleSeconds=${afterRemoteStop.idleSeconds} idleFeeAmount=${afterRemoteStop.idleFeeAmount} estimatedCost=${afterRemoteStop.estimatedCost}")

  long idleTickWaitSeconds = (props.getProperty('idle_tick_wait_seconds', '70') as long)
  logLine("waiting ${idleTickWaitSeconds}s for backend idle-fee ZSET ticker")
  Thread.sleep(Math.max(60L, idleTickWaitSeconds) * 1000L)
  def afterBackendTick = waitForActiveSessionPredicate(token, sessionId, 'backend idle ticker minute update', 30) { session ->
    session.idleFeeEnabled == true &&
      session.unplugRequiredToStop == true &&
      String.valueOf(session.status ?: '').equalsIgnoreCase('SUSPENDED') &&
      ((session.idleSeconds ?: 0) as long) >= 60L &&
      ((session.idleFeeAmount ?: 0) as BigDecimal) > 0
  }
  long wholeIdleMinutes = (((afterBackendTick.idleSeconds ?: 0) as long) / 60L) as long
  if (wholeIdleMinutes < 1L) {
    throw new IllegalStateException("backend idle ticker did not reach a whole billable minute for ${sessionId}: ${afterBackendTick}")
  }
  logLine("backend idle tick ok session=${sessionId} billableMinutes=${wholeIdleMinutes} idleSeconds=${afterBackendTick.idleSeconds} idleFeeAmount=${afterBackendTick.idleFeeAmount} estimatedCost=${afterBackendTick.estimatedCost}")

  sendSimulatorChargingStop()
  validateStoppedAndReceipt(token, sessionId)
}

try {
  long startedAt = System.currentTimeMillis()
  String token

  if (action == 'setup' || action == 'full' || action == 'idle-fee' || action == 'subscription-discount') {
    token = registerOrLogin()
    appendGeneratedUser()
    token = setupPayment(token)
  } else if (action == 'charging') {
    token = login()
  } else {
    throw new IllegalArgumentException("Unsupported action '${action}'. Use setup, charging, full, idle-fee, or subscription-discount.")
  }

  if (action == 'charging' || action == 'full' || action == 'idle-fee' || action == 'subscription-discount') {
    discoverChargers(token)
    requireStatus(atStep('payment state before start') { request('GET', '/payment/api/v1/payment/state', null, token) }, [200], 'payment state before start')
    String sessionId = startSession(token)
    activeSession(token)
    monitorSse(token, sessionId)

    if (action == 'idle-fee') {
      validateIdleFeeFlow(token, sessionId)
    } else if (action == 'subscription-discount') {
      validateSubscriptionDiscountFlow(token, sessionId)
    } else {

      long elapsedSeconds = (System.currentTimeMillis() - startedAt) / 1000L
      long remaining = Math.max(0L, holdSeconds - elapsedSeconds)
      if (remaining > 0L) {
        logLine("holding session for ${remaining}s before stop")
        sleep(remaining * 1000L)
      }

      stopSession(token, sessionId)
      validateStoppedAndReceipt(token, sessionId)
    }
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
  SampleResult.setResponseMessage("step=${currentStep}: ${t.message ?: t.class.name}")
  SampleResult.setResponseData((t.message ?: t.toString()), 'UTF-8')
  log.error("[electrahub-jmeter][${action}][${userNumber}] failed", t)
  throw t
}
