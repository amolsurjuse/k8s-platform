import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets

def slurper = new JsonSlurper()
def baseUrl = props.getProperty('base_url', 'https://api.dev.electrahub.net').replaceAll('/+$', '')
def loginEmail = props.getProperty('login_email', 'sysadmin.dev@electrahub.com')
def loginPassword = props.getProperty('login_password', 'Admin@12345')
def chargerId = props.getProperty('sparky_charger_id', 'EH-SFO-CHG-001')
def connectorId = props.getProperty('sparky_connector_id', 'CON-SFO-001')
def locationId = props.getProperty('sparky_location_id', 'US*EHB*LOC*SFO001')
def sessionId = props.getProperty('sparky_session_id', '').trim()
def streamTimeoutMs = (props.getProperty('sparky_stream_timeout_ms', '60000') as int)
def requestHostHeader = props.getProperty('request_host_header', '').trim()
def runId = props.getProperty('run_id', String.valueOf(System.currentTimeMillis()))
def requireChargerStatus = props.getProperty('sparky_require_charger_status', 'false').toBoolean()

def prompts = [
  [
    name: 'charging start unavailable',
    content: 'I got 503 charging service temporarily unavailable. Why did charging start fail?',
    expectedTool: 'diagnose_charging_start',
    expectedText: ['503 during start', 'Live backend checks', 'wallet balance']
  ],
  [
    name: 'already active connector',
    content: 'Why am I getting ALREADY_ACTIVE and a session is already in progress for this connector?',
    expectedTool: 'diagnose_active_connector',
    expectedText: ['already in progress', 'Live backend checks', 'charger: ' + chargerId]
  ],
  [
    name: 'stuck preparing',
    content: 'Why is my charging session stuck in preparing?',
    expectedTool: 'diagnose_session_state',
    expectedText: ['stuck in Preparing', 'Live backend checks', 'OCPP connection']
  ],
  [
    name: 'charger heartbeat',
    content: 'Is this charger online or offline? What does heartbeat mean?',
    expectedTool: 'check_charger_liveness',
    expectedText: ['heartbeat messages', 'Live backend checks', 'last OCPP heartbeat']
  ],
  [
    name: 'general help',
    content: 'What can Sparky help me with during charging?',
    expectedTool: 'driver_support_context',
    expectedText: ['charging start failures', 'Live backend checks', 'connector: ' + connectorId]
  ]
]

def json = { Object value -> JsonOutput.toJson(value) }

def parseJson = { String body ->
  if (!body) return null
  try {
    slurper.parseText(body)
  } catch (Exception ignored) {
    null
  }
}

def request = { String method, String path, Object body = null, String token = null, int timeoutMs = 45000, String accept = 'application/json' ->
  URL url = new URL("${baseUrl}${path}")
  HttpURLConnection conn = (HttpURLConnection) url.openConnection()
  conn.setRequestMethod(method)
  conn.setConnectTimeout(timeoutMs)
  conn.setReadTimeout(timeoutMs)
  conn.setRequestProperty('Accept', accept)
  conn.setRequestProperty('User-Agent', 'ElectraHubSparkyRegression/1.0')
  conn.setRequestProperty('X-ElectraHub-Test-Run', runId)
  if (requestHostHeader) {
    conn.setRequestProperty('Host', requestHostHeader)
  }
  if (token) {
    conn.setRequestProperty('Authorization', "Bearer ${token}")
  }
  if (body != null) {
    byte[] bytes = json(body).getBytes(StandardCharsets.UTF_8)
    conn.setDoOutput(true)
    conn.setRequestProperty('Content-Type', 'application/json')
    conn.setRequestProperty('Content-Length', String.valueOf(bytes.length))
    conn.outputStream.withCloseable { it.write(bytes) }
  }

  int status = conn.responseCode
  InputStream stream = status >= 400 ? conn.errorStream : conn.inputStream
  String responseBody = stream == null ? '' : stream.getText('UTF-8')
  [
    status: status,
    body: responseBody,
    json: parseJson(responseBody),
    contentType: conn.getHeaderField('Content-Type')
  ]
}

def requireStatus = { Map response, List<Integer> expected, String step ->
  if (!expected.contains(response.status as int)) {
    throw new IllegalStateException("${step} failed with HTTP ${response.status}: ${response.body}")
  }
  response
}

def login = {
  def response = request('POST', '/auth/api/auth/login', [
    email: loginEmail,
    password: loginPassword
  ])
  requireStatus(response, [200], 'login')
  String token = response.json?.accessToken as String
  if (!token) {
    throw new IllegalStateException('login returned no accessToken')
  }
  token
}

def streamAnswer = { String threadId, String messageId, String token ->
  URL url = new URL("${baseUrl}/ai/api/v1/chat/threads/${threadId}/stream?since=${messageId}")
  HttpURLConnection conn = (HttpURLConnection) url.openConnection()
  conn.setRequestMethod('GET')
  conn.setConnectTimeout(15000)
  conn.setReadTimeout(streamTimeoutMs)
  conn.setRequestProperty('Accept', 'text/event-stream')
  conn.setRequestProperty('Authorization', "Bearer ${token}")
  conn.setRequestProperty('User-Agent', 'ElectraHubSparkyRegression/1.0')
  if (requestHostHeader) {
    conn.setRequestProperty('Host', requestHostHeader)
  }

  int status = conn.responseCode
  if (status != 200) {
    String errorBody = conn.errorStream == null ? '' : conn.errorStream.getText('UTF-8')
    throw new IllegalStateException("stream failed with HTTP ${status}: ${errorBody}")
  }
  String contentType = conn.getHeaderField('Content-Type') ?: ''
  if (!contentType.toLowerCase(Locale.ROOT).contains('text/event-stream')) {
    throw new IllegalStateException("stream content type was ${contentType}, expected text/event-stream")
  }

  StringBuilder answer = new StringBuilder()
  List<String> tools = []
  List<String> errors = []
  boolean done = false
  conn.inputStream.withReader('UTF-8') { reader ->
    String line
    while ((line = reader.readLine()) != null) {
      String trimmed = line.trim()
      if (!trimmed.startsWith('data:')) {
        continue
      }
      def event = parseJson(trimmed.substring(5).trim())
      if (!event) {
        continue
      }
      switch (event.type as String) {
        case 'TOKEN':
          answer.append(event.delta ?: '')
          break
        case 'TOOL_CALL':
          tools.add(event.tool as String)
          break
        case 'ERROR':
          errors.add("${event.code}:${event.message}")
          break
        case 'DONE':
          done = true
          break
      }
    }
  }
  [answer: answer.toString(), tools: tools, errors: errors, done: done]
}

long started = System.currentTimeMillis()
String token = login()
List<Map> results = []

prompts.each { prompt ->
  def context = [
    screen: 'liveCharging',
    audience: 'driver',
    chargerId: chargerId,
    connectorId: connectorId,
    locationId: locationId
  ]
  if (sessionId) {
    context.sessionId = sessionId
  }

  def sendResponse = request('POST', '/ai/api/v1/chat/messages', [
    content: prompt.content,
    context: context
  ], token)
  requireStatus(sendResponse, [201], "send prompt '${prompt.name}'")
  String threadId = sendResponse.json?.threadId as String
  String messageId = sendResponse.json?.messageId as String
  if (!threadId || !messageId) {
    throw new IllegalStateException("send prompt '${prompt.name}' returned no threadId/messageId: ${sendResponse.body}")
  }

  def stream = streamAnswer(threadId, messageId, token)
  if (!stream.done) {
    throw new IllegalStateException("prompt '${prompt.name}' did not receive DONE event; answer=${stream.answer}")
  }
  if (!stream.errors.isEmpty()) {
    throw new IllegalStateException("prompt '${prompt.name}' returned stream errors: ${stream.errors}")
  }
  if (!stream.tools.contains(prompt.expectedTool)) {
    throw new IllegalStateException("prompt '${prompt.name}' expected tool ${prompt.expectedTool}, got ${stream.tools}")
  }
  if (requireChargerStatus && !stream.answer.contains('charger ' + chargerId + ' status is')) {
    throw new IllegalStateException("prompt '${prompt.name}' answer missing charger status diagnostic. Answer=${stream.answer}")
  }
  prompt.expectedText.each { expected ->
    if (!stream.answer.contains(expected)) {
      throw new IllegalStateException("prompt '${prompt.name}' answer missing '${expected}'. Answer=${stream.answer}")
    }
  }
  results.add([
    name: prompt.name,
    tool: prompt.expectedTool,
    answerLength: stream.answer.length()
  ])
}

long elapsed = System.currentTimeMillis() - started
SampleResult.setResponseCode('200')
SampleResult.setResponseMessage("Validated ${results.size()} Sparky prompt families in ${elapsed} ms")
SampleResult.setResponseData(JsonOutput.prettyPrint(JsonOutput.toJson([
  baseUrl: baseUrl,
  chargerId: chargerId,
  connectorId: connectorId,
  locationId: locationId,
  prompts: results,
  elapsedMs: elapsed
])), 'UTF-8')
SampleResult.setSuccessful(true)
