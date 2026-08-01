import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Locale

def slurper = new JsonSlurper()
String action = (Parameters ?: '').trim()
String baseUrl = props.getProperty('base_url', 'https://api.dev.electrahub.net').replaceAll('/+$', '')
String requestHostHeader = props.getProperty('request_host_header', '').trim()
String runId = props.getProperty('run_id', String.valueOf(System.currentTimeMillis()))
int requestTimeoutMs = props.getProperty('request_timeout_ms', '30000') as int
boolean validateOnly = props.getProperty('payment_gateway_validate_only', 'false').toBoolean()
String summaryPath = props.getProperty(
  'payment_gateway_summary',
  'outputs/jmeter/teamcity/results/payment-gateway-provider-summary.json'
)

def providerSpecs = [
  STRIPE: [
    profile: 'stripe-sandbox',
    adapter: 'stripe-rest-v1',
    credential: true,
    webhook: true,
    certificate: false,
    paymentMethod: 'CARD_ON_FILE',
    capabilities: ['AUTHORIZE', 'MANUAL_CAPTURE', 'CAPTURE', 'PARTIAL_CAPTURE', 'VOID', 'REFUND', 'STATUS_QUERY', 'THREE_DS_SCA'] as Set
  ],
  RAZORPAY: [
    profile: 'razorpay-sandbox',
    adapter: 'razorpay-rest-v1',
    credential: true,
    webhook: true,
    certificate: false,
    paymentMethod: 'HOSTED_CHECKOUT',
    capabilities: ['AUTHORIZE', 'MANUAL_CAPTURE', 'CAPTURE', 'PARTIAL_CAPTURE', 'REFUND', 'STATUS_QUERY', 'THREE_DS_SCA'] as Set
  ],
  TWO_C2P: [
    profile: '2c2p-sandbox',
    adapter: '2c2p-v4.3',
    credential: true,
    webhook: false,
    certificate: true,
    paymentMethod: 'HOSTED_CHECKOUT',
    capabilities: ['AUTHORIZE', 'MANUAL_CAPTURE', 'CAPTURE', 'VOID', 'REFUND', 'STATUS_QUERY', 'THREE_DS_SCA'] as Set
  ],
  MOLLIE: [
    profile: 'mollie-sandbox',
    adapter: 'mollie-rest-v2',
    credential: true,
    webhook: false,
    certificate: false,
    paymentMethod: 'HOSTED_CHECKOUT',
    capabilities: ['AUTHORIZE', 'MANUAL_CAPTURE', 'CAPTURE', 'PARTIAL_CAPTURE', 'VOID', 'REFUND', 'STATUS_QUERY', 'THREE_DS_SCA'] as Set
  ],
  ADYEN: [
    profile: 'adyen-sandbox',
    adapter: 'adyen-checkout-v72',
    credential: true,
    webhook: true,
    certificate: false,
    paymentMethod: 'HOSTED_CHECKOUT',
    capabilities: ['AUTHORIZE', 'MANUAL_CAPTURE', 'CAPTURE', 'PARTIAL_CAPTURE', 'VOID', 'REFUND', 'THREE_DS_SCA'] as Set
  ]
]

def parseJson = { String body ->
  if (!body) return null
  try {
    slurper.parseText(body)
  } catch (Exception ignored) {
    null
  }
}

def json = { Object value -> JsonOutput.toJson(value) }

def request = { String method, String path, Object body = null, String token = null, Map extraHeaders = [:] ->
  HttpURLConnection connection = (HttpURLConnection) new URL("${baseUrl}${path}").openConnection()
  try {
    connection.requestMethod = method
    connection.instanceFollowRedirects = false
    connection.connectTimeout = Math.min(requestTimeoutMs, 10000)
    connection.readTimeout = requestTimeoutMs
    connection.setRequestProperty('Accept', 'application/json')
    connection.setRequestProperty('User-Agent', 'ElectraHubPaymentGatewayRegression/1.0')
    connection.setRequestProperty('X-ElectraHub-Test-Run', runId)
    if (requestHostHeader) connection.setRequestProperty('Host', requestHostHeader)
    if (token) connection.setRequestProperty('Authorization', "Bearer ${token}")
    extraHeaders.each { name, value -> connection.setRequestProperty(String.valueOf(name), String.valueOf(value)) }
    if (body != null) {
      byte[] bytes = json(body).getBytes(StandardCharsets.UTF_8)
      connection.doOutput = true
      connection.setRequestProperty('Content-Type', 'application/json')
      connection.setRequestProperty('Content-Length', String.valueOf(bytes.length))
      connection.outputStream.withCloseable { it.write(bytes) }
    }
    long started = System.currentTimeMillis()
    int status = connection.responseCode
    InputStream stream = status >= 400 ? connection.errorStream : connection.inputStream
    String responseBody = stream == null ? '' : stream.getText('UTF-8')
    long elapsedMs = System.currentTimeMillis() - started
    log.info("[payment-gateway-jmeter] action={} method={} path={} status={} elapsedMs={}", action, method, path, status, elapsedMs)
    [status: status, json: parseJson(responseBody), elapsedMs: elapsedMs]
  } finally {
    connection.disconnect()
  }
}

def requireCondition = { boolean condition, String message ->
  if (!condition) throw new IllegalStateException(message)
}

def requireStatus = { Map response, int expected, String step ->
  requireCondition(response.status == expected, "${step} returned HTTP ${response.status}, expected ${expected}")
  requireCondition(response.json != null, "${step} returned an invalid JSON response")
  response.json
}

def secretFields = [
  'credentialSecretReference', 'webhookSecretReference', 'certificateSecretReference',
  'apiKey', 'password', 'token', 'secret'
] as Set

def assertNoSecretMaterial
assertNoSecretMaterial = { Object node ->
  if (node instanceof Map) {
    node.each { key, value ->
      requireCondition(!secretFields.contains(String.valueOf(key)), "configuration response exposed a forbidden secret field")
      assertNoSecretMaterial(value)
    }
  } else if (node instanceof Collection) {
    node.each { assertNoSecretMaterial(it) }
  } else if (node instanceof String) {
    String value = (node as String).toLowerCase(Locale.ROOT)
    requireCondition(
      !(value.startsWith('env:') || value.startsWith('vault:') || value.startsWith('kubernetes:')),
      'configuration response exposed a secret reference value'
    )
  }
}

def readSnapshot = {
  String encoded = vars.get('paymentGatewaySnapshot')
  requireCondition(encoded != null && !encoded.isBlank(), 'configuration preflight did not complete')
  parseJson(encoded) as Map
}

def connectionFor = { Map snapshot, String provider, Map spec ->
  List matches = (snapshot.connections ?: []).findAll { connection ->
    connection.provider == provider &&
      connection.environment == 'SANDBOX' &&
      connection.endpointProfile == spec.profile &&
      connection.status == 'ACTIVE'
  }
  requireCondition(matches.size() == 1, "${provider} requires exactly one ACTIVE SANDBOX ${spec.profile} connection; found ${matches.size()}")
  matches[0] as Map
}

def recordResult = { String provider, String outcome, String code, Long elapsedMs = null ->
  vars.put("paymentGatewaySummary.${provider}", json([
    provider: provider,
    environment: 'SANDBOX',
    endpointProfile: providerSpecs[provider]?.profile,
    outcome: outcome,
    code: code,
    elapsedMs: elapsedMs
  ]))
}

def sanitizeFailureCode = { Exception exception ->
  String value = (exception.message ?: exception.class.simpleName).toUpperCase(Locale.ROOT)
  String code = value.replaceAll('[^A-Z0-9]+', '_').replaceAll('^_+|_+$', '')
  code ? code.take(160) : 'ASSERTION_FAILED'
}

def validateProvider = { String provider ->
  Map spec = providerSpecs[provider]
  Map snapshot = readSnapshot()
  Map connection = connectionFor(snapshot, provider, spec)

  requireCondition(connection.adapterVersion == spec.adapter, "${provider} adapter version was ${connection.adapterVersion}, expected ${spec.adapter}")
  requireCondition(connection.lastErrorCode == null, "${provider} has lastErrorCode ${connection.lastErrorCode}")
  requireCondition(connection.validatedAt != null, "${provider} has never been validated")
  requireCondition(connection.lastHealthAt != null, "${provider} has no health timestamp")
  requireCondition(connection.credentialConfigured == spec.credential, "${provider} credentialConfigured was not ${spec.credential}")
  requireCondition(connection.webhookSecretConfigured == spec.webhook, "${provider} webhookSecretConfigured was not ${spec.webhook}")
  requireCondition(connection.certificateConfigured == spec.certificate, "${provider} certificateConfigured was not ${spec.certificate}")

  Set configuredCapabilities = (connection.capabilities ?: []) as Set
  requireCondition(configuredCapabilities.containsAll(spec.capabilities), "${provider} ACTIVE connection is missing required capabilities")

  List merchants = (snapshot.merchantAccounts ?: []).findAll { merchant ->
    merchant.connectionId == connection.id && merchant.status == 'ACTIVE'
  }
  requireCondition(!merchants.isEmpty(), "${provider} has no ACTIVE merchant account")
  Set merchantIds = merchants.collect { it.id } as Set
  List routes = (snapshot.paymentRoutes ?: []).findAll { route ->
    route.connectionId == connection.id && merchantIds.contains(route.merchantAccountId) && route.enabled == true
  }
  requireCondition(!routes.isEmpty(), "${provider} has no enabled route for an ACTIVE merchant")
  requireCondition(routes.any { it.paymentMethod == spec.paymentMethod }, "${provider} has no enabled ${spec.paymentMethod} route")
  if (spec.paymentMethod == 'HOSTED_CHECKOUT') {
    Set hostedCapabilities = ['AUTHORIZE', 'MANUAL_CAPTURE', 'CAPTURE'] as Set
    requireCondition(
      routes.findAll { it.paymentMethod == 'HOSTED_CHECKOUT' }.any { route ->
        ((route.requiredCapabilities ?: []) as Set).containsAll(hostedCapabilities)
      },
      "${provider} hosted-checkout route does not require authorize and manual capture"
    )
  }

  String token = vars.get('paymentGatewayAdminToken')
  requireCondition(token != null && !token.isBlank(), 'configuration preflight returned no administrator token')
  Map response = null
  int attempts = 19
  for (int attempt = 1; attempt <= attempts; attempt++) {
    response = request(
      'POST',
      "/payment-gateway/api/v1/gateway/admin/connections/${connection.id}/probe",
      null,
      token
    )
    if (![404, 502, 503, 504].contains(response.status) || attempt == attempts) break
    log.info('[payment-gateway-jmeter] waiting for read-only probe deployment attempt={}/{}', attempt, attempts)
    sleep(10000L)
  }
  Map validation = requireStatus(response, 200, "${provider} provider probe") as Map
  requireCondition(validation.valid == true, "${provider} provider probe returned ${validation.code ?: 'INVALID'}")
  requireCondition(validation.adapterVersion == spec.adapter, "${provider} probe used unexpected adapter ${validation.adapterVersion}")
  requireCondition(((validation.capabilities ?: []) as Set).containsAll(spec.capabilities), "${provider} probe is missing required capabilities")
  recordResult(provider, 'PASSED', validation.code ?: 'READY', response.elapsedMs as Long)
}

def writeSummary = { boolean configurationStable, String finalCode ->
  List providers = providerSpecs.keySet().collect { provider ->
    String value = vars.get("paymentGatewaySummary.${provider}")
    value ? parseJson(value) : [
      provider: provider,
      environment: 'SANDBOX',
      endpointProfile: providerSpecs[provider].profile,
      outcome: 'FAILED',
      code: 'NO_RESULT',
      elapsedMs: null
    ]
  }
  File output = new File(summaryPath)
  output.parentFile?.mkdirs()
  output.text = JsonOutput.prettyPrint(json([
    generatedAt: Instant.now().toString(),
    runId: runId,
    mode: validateOnly ? 'PLAN_VALIDATION' : 'SANDBOX_PROVIDER_PROBE',
    configurationStable: configurationStable,
    finalCode: finalCode,
    providers: providers
  ])) + System.lineSeparator()
}

if (validateOnly) {
  if (action == 'finalize') writeSummary(true, 'PLAN_VALID')
  log.info('[payment-gateway-jmeter] plan validation action={}', action)
  return
}

Set approvedApiOrigins = [
  'https://api-dev.electrahub.net',
  'https://api.electrahub.net'
] as Set
requireCondition(approvedApiOrigins.contains(baseUrl), 'payment gateway credential checks require an approved ElectraHub API origin')
requireCondition(requestHostHeader.isEmpty(), 'payment gateway credential checks do not permit a Host header override')

if (action == 'preflight') {
  String email = System.getenv('PAYMENT_GATEWAY_TEST_ADMIN_EMAIL') ?: ''
  String password = System.getenv('PAYMENT_GATEWAY_TEST_ADMIN_PASSWORD') ?: ''
  requireCondition(email && email != 'SET_IN_TEAMCITY', 'PAYMENT_GATEWAY_TEST_ADMIN_EMAIL is not configured')
  requireCondition(password && password != 'SET_IN_TEAMCITY', 'PAYMENT_GATEWAY_TEST_ADMIN_PASSWORD is not configured')

  String deviceId = UUID.nameUUIDFromBytes(
    'electrahub-payment-gateway-teamcity'.getBytes(StandardCharsets.UTF_8)
  ).toString()
  Map login = request(
    'POST',
    '/auth/api/auth/login',
    [email: email, password: password],
    null,
    ['Cookie': "did=${deviceId}"]
  )
  Map loginPayload = requireStatus(login, 200, 'system administrator login') as Map
  String token = loginPayload.accessToken as String
  requireCondition(token != null && !token.isBlank(), 'system administrator login returned no access token')
  vars.put('paymentGatewayAdminToken', token)
  vars.put('paymentGatewayDeviceId', deviceId)

  Map health = request('GET', '/payment-gateway/actuator/health/readiness', null, token)
  Map healthPayload = requireStatus(health, 200, 'payment gateway readiness') as Map
  requireCondition(healthPayload.status == 'UP', "payment gateway readiness was ${healthPayload.status ?: 'UNKNOWN'}")

  Map configuration = request('GET', '/payment-gateway/api/v1/gateway/admin/configuration', null, token)
  Map snapshot = requireStatus(configuration, 200, 'payment gateway configuration') as Map
  requireCondition(snapshot.connections instanceof Collection, 'configuration response has no connections array')
  requireCondition(snapshot.merchantAccounts instanceof Collection, 'configuration response has no merchantAccounts array')
  requireCondition(snapshot.paymentRoutes instanceof Collection, 'configuration response has no paymentRoutes array')
  assertNoSecretMaterial(snapshot)
  vars.put('paymentGatewaySnapshot', json(snapshot))
  return
}

if (providerSpecs.containsKey(action)) {
  try {
    validateProvider(action)
  } catch (Exception exception) {
    recordResult(action, 'FAILED', sanitizeFailureCode(exception))
    throw exception
  }
  return
}

if (action == 'finalize') {
  boolean stable = false
  String finalCode = 'CONFIGURATION_CHANGED'
  Exception finalError = null
  try {
    Map before = readSnapshot()
    String token = vars.get('paymentGatewayAdminToken')
    Map response = request('GET', '/payment-gateway/api/v1/gateway/admin/configuration', null, token)
    Map after = requireStatus(response, 200, 'post-probe payment gateway configuration') as Map
    providerSpecs.each { provider, spec ->
      Map original = connectionFor(before, provider, spec)
      Map current = connectionFor(after, provider, spec)
      ['status', 'configurationVersion', 'validatedAt', 'lastHealthAt', 'updatedAt'].each { field ->
        requireCondition(original[field] == current[field], "${provider} ${field} changed during read-only probes")
      }
    }
    providerSpecs.keySet().each { provider ->
      String resultValue = vars.get("paymentGatewaySummary.${provider}")
      requireCondition(resultValue != null && !resultValue.isBlank(), "${provider} provider sampler produced no result")
      Map providerResult = parseJson(resultValue) as Map
      requireCondition(providerResult.outcome == 'PASSED', "${provider} provider sampler did not pass")
    }
    stable = true
    finalCode = 'UNCHANGED'
  } catch (Exception exception) {
    finalError = exception
    finalCode = sanitizeFailureCode(exception)
  } finally {
    String token = vars.get('paymentGatewayAdminToken')
    String deviceId = vars.get('paymentGatewayDeviceId')
    if (token && deviceId) {
      try {
        Map logout = request(
          'POST',
          '/auth/api/auth/logout-device',
          null,
          token,
          ['Cookie': "did=${deviceId}"]
        )
        requireCondition(logout.status == 204, "device logout returned HTTP ${logout.status}, expected 204")
      } catch (Exception exception) {
        if (finalError == null) {
          finalError = exception
          finalCode = sanitizeFailureCode(exception)
          stable = false
        }
      }
    }
    vars.remove('paymentGatewayAdminToken')
    vars.remove('paymentGatewayDeviceId')
  }
  writeSummary(stable, finalCode)
  if (finalError != null) throw finalError
  return
}

throw new IllegalArgumentException("Unknown payment gateway regression action: ${action}")
