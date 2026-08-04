import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.io.IOException
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.Locale
import java.util.UUID

def slurper = new JsonSlurper()
def action = (Parameters ?: 'full').trim()
def baseUrl = props.getProperty('base_url', 'https://api.dev.electrahub.net').replaceAll('/+$', '')
def simulatorUrl = props.getProperty('simulator_url', 'https://ocpp-simulator.electrahub.net').replaceAll('/+$', '')
def requestHostHeader = props.getProperty('request_host_header', '').trim()
def runId = props.getProperty('run_id')
if (!runId) {
  runId = String.valueOf(System.currentTimeMillis())
  props.put('run_id', runId)
}

int userOffset = props.getProperty('user_offset', '0') as int
int threadIndex = ctx.getThreadNum() + 1 + userOffset
String userNumber = String.format('%03d', threadIndex)
String explicitJourneyCountry = String.valueOf(vars.get('journeyCountryCode') ?: '').trim().toUpperCase(Locale.ROOT)
String journeyCountryCode = String.valueOf(
  explicitJourneyCountry ?: props.getProperty('journey_country_code', props.getProperty('charger_country_code', 'US'))
).trim().toUpperCase(Locale.ROOT)
if (!journeyCountryCode) journeyCountryCode = 'US'
String defaultJourneyCurrency = [US: 'USD', IN: 'INR', NL: 'EUR', SG: 'SGD'][journeyCountryCode] ?: 'USD'
String journeyCurrency = String.valueOf(
  vars.get('journeyCurrency') ?: props.getProperty('session_currency', defaultJourneyCurrency)
).trim().toUpperCase(Locale.ROOT)
String journeyRegionKey = explicitJourneyCountry ? "-${explicitJourneyCountry.toLowerCase(Locale.ROOT)}" : ''
String actionUserKey = action.replaceAll('[^A-Za-z0-9]+', '-').toLowerCase(Locale.ROOT)
int phoneRunSeed = Math.floorMod("${runId}:${action}:${journeyCountryCode}".hashCode(), 1_000_000_000)
String defaultPassword = props.getProperty('test_password', 'LoadTest@12345')
String email = (vars.get('userEmail') ?: "jmeter+${runId}${journeyRegionKey}-${actionUserKey}-${userNumber}@electrahub.test").trim()
String password = (vars.get('userPassword') ?: defaultPassword).trim()
vars.put('userEmail', email)
vars.put('userPassword', password)

String connectorId = vars.get('connectorId')
String chargerId = vars.get('chargerId')
String locationId = vars.get('locationId')
String connectorType = vars.get('connectorType') ?: 'CCS-2'
int connectorNumber = (vars.get('connectorNumber') ?: '1') as int

int holdSeconds = props.getProperty('hold_seconds', '900') as int
int sseSeconds = props.getProperty('sse_seconds', String.valueOf(Math.min(holdSeconds, 120))) as int
int requestTimeoutMs = props.getProperty('request_timeout_ms', '120000') as int
int sessionCommandTimeoutMs = props.getProperty('session_command_timeout_ms', '180000') as int
BigDecimal walletTopupAmount = new BigDecimal(String.valueOf(
  vars.get('journeyWalletTopupAmount') ?: props.getProperty('wallet_topup_amount', '120.00')
))
BigDecimal lowBalanceContinueWalletBalance = new BigDecimal(props.getProperty('low_balance_continue_wallet_balance', '55.00'))
BigDecimal lowBalanceThreshold = new BigDecimal(props.getProperty('low_balance_threshold', '10.00'))
String usersOutput = props.getProperty('users_output', 'scripts/jmeter/data/generated-users.csv')
String connectorsCsv = props.getProperty('connectors_csv', 'scripts/jmeter/data/connectors-100.csv')
boolean dynamicConnectorSelection = props.getProperty('dynamic_connector_selection', 'false').toBoolean()
String sessionPaymentMethod = String.valueOf(
  vars.get('journeyPaymentMethod') ?: props.getProperty('session_payment_method', action == 'card-burst' ? 'CARD' : 'WALLET')
).trim().toUpperCase(Locale.ROOT)
boolean cardOnlyPayment = sessionPaymentMethod in ['CARD', 'CREDIT_CARD']
boolean exclusiveConnectorAllocation = props.getProperty('exclusive_connector_allocation', action == 'card-burst' ? 'true' : 'false').toBoolean()
boolean cleanupTestAccount = String.valueOf(
  vars.get('journeyCleanup') ?: props.getProperty('cleanup_test_account', action == 'card-burst' ? 'true' : 'false')
).toBoolean()
boolean requireCleanupCredential = String.valueOf(
  vars.get('journeyRequireCleanupCredential') ?: cleanupTestAccount
).toBoolean()
boolean cleanupUnexpectedSessions = String.valueOf(
  vars.get('journeyCleanupUnexpectedSessions') ?: 'false'
).toBoolean()
boolean validateChargingProgress = String.valueOf(
  vars.get('journeyValidateChargingProgress') ?: props.getProperty('validate_charging_progress', 'false')
).toBoolean()
boolean persistGeneratedUser = String.valueOf(
  vars.get('journeyPersistGeneratedUser') ?: props.getProperty('persist_generated_user', action == 'card-burst' ? 'false' : 'true')
).toBoolean()
String cleanupAdminToken = props.getProperty(
  'cleanup_admin_token',
  System.getenv('ELECTRAHUB_LOAD_CLEANUP_ADMIN_TOKEN') ?: ''
).trim()
String currentStep = 'init'

def logLine = { String message ->
  log.info("[electrahub-jmeter][${action}][${userNumber}] ${message}")
}

def json = { Object value -> JsonOutput.toJson(value) }

def connectorNumberFromId = { String id, int fallback ->
  def matcher = (id ?: '') =~ /(\d+)\D*$/
  matcher.find() ? (matcher.group(1) as int) : fallback
}

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
  int attempts = (method == 'GET' || path == '/charger/graphql') ? 3 : 1
  Throwable lastError = null
  for (int attempt = 1; attempt <= attempts; attempt++) {
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
      if (attempt < attempts && [429, 502, 503, 504].contains(status)) {
        logLine("http ${method} ${path} retryable status=${status} attempt=${attempt}/${attempts}; retrying")
        sleep(500L * attempt)
        continue
      }
      return [status: status, body: responseBody, json: parseJson(responseBody)]
    } catch (SocketTimeoutException timeout) {
      lastError = new SocketTimeoutException("step=${currentStep} ${method} ${path} timed out after ${timeoutMs}ms")
    } catch (IOException ioe) {
      lastError = ioe
    } finally {
      conn.disconnect()
    }
    if (attempt < attempts) {
      logLine("http ${method} ${path} transient failure attempt=${attempt}/${attempts}: ${lastError.message ?: lastError.class.name}; retrying")
      sleep(500L * attempt)
    }
  }
  throw lastError
}

def simulatorRequest = { String method, String path, Object body = null, int timeoutMs = requestTimeoutMs ->
  int attempts = 3
  Throwable lastError = null
  for (int attempt = 1; attempt <= attempts; attempt++) {
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
      return [status: status, body: responseBody, json: parseJson(responseBody)]
    } catch (SocketTimeoutException timeout) {
      lastError = new SocketTimeoutException("step=${currentStep} simulator ${method} ${path} timed out after ${timeoutMs}ms")
    } catch (IOException ioe) {
      lastError = ioe
    } finally {
      conn.disconnect()
    }
    if (attempt < attempts) {
      logLine("simulator ${method} ${path} transient failure attempt=${attempt}/${attempts}: ${lastError.message ?: lastError.class.name}; retrying")
      sleep(500L * attempt)
    }
  }
  throw lastError
}

/// Confirms the sandbox SetupIntent without handling raw card data. The
/// publishable key and client secret are short-lived values returned to this
/// test user's authenticated enrollment request; neither value is logged or
/// persisted in JMeter result data.
def stripeFormRequest = { String setupIntentId, String publishableKey, String clientSecret ->
  if (!(setupIntentId ==~ /^seti_[A-Za-z0-9_]+$/)) {
    throw new IllegalStateException('secure card enrollment returned an invalid Stripe SetupIntent id')
  }
  if (!publishableKey.startsWith('pk_test_')) {
    throw new IllegalStateException('regional card E2E requires a Stripe sandbox publishable key')
  }
  if (!clientSecret.startsWith("${setupIntentId}_secret_")) {
    throw new IllegalStateException('secure card enrollment returned a mismatched Stripe client secret')
  }

  URL url = new URL("https://api.stripe.com/v1/setup_intents/${setupIntentId}/confirm")
  HttpURLConnection conn = (HttpURLConnection) url.openConnection()
  conn.setRequestMethod('POST')
  conn.setConnectTimeout(requestTimeoutMs)
  conn.setReadTimeout(requestTimeoutMs)
  conn.setDoOutput(true)
  conn.setRequestProperty('Accept', 'application/json')
  conn.setRequestProperty('Content-Type', 'application/x-www-form-urlencoded')
  conn.setRequestProperty('Authorization', "Bearer ${publishableKey}")
  conn.setRequestProperty('User-Agent', 'ElectraHubRegression/1.0')
  String form = [
    payment_method: 'pm_card_visa',
    client_secret: clientSecret,
    return_url: 'https://electrahub.net/payment/card-setup-test',
    use_stripe_sdk: 'false'
  ].collect { key, value ->
    "${URLEncoder.encode(String.valueOf(key), 'UTF-8')}=${URLEncoder.encode(String.valueOf(value), 'UTF-8')}"
  }.join('&')
  byte[] bytes = form.getBytes(StandardCharsets.UTF_8)
  conn.setRequestProperty('Content-Length', String.valueOf(bytes.length))

  try {
    long started = System.currentTimeMillis()
    conn.outputStream.withCloseable { it.write(bytes) }
    int status = conn.responseCode
    InputStream stream = status >= 400 ? conn.errorStream : conn.inputStream
    String responseBody = stream == null ? '' : stream.getText('UTF-8')
    logLine("stripe setup confirmation status=${status} elapsedMs=${System.currentTimeMillis() - started}")
    return [status: status, body: responseBody, json: parseJson(responseBody)]
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
  Map regionalAddress = [
    US: [line1: '100 Load Test Way', city: 'Test City', state: 'CA', postalCode: '94016', phonePrefix: '+1555', phoneDigits: 7],
    IN: [line1: '100 Load Test Marg', city: 'Bengaluru', state: 'KA', postalCode: '560001', phonePrefix: '+919', phoneDigits: 9],
    NL: [line1: '100 Laadteststraat', city: 'Amsterdam', state: 'NH', postalCode: '1011AB', phonePrefix: '+316', phoneDigits: 8]
  ][journeyCountryCode] ?: [line1: '100 Load Test Way', city: 'Test City', state: 'NA', postalCode: '00000', phonePrefix: '+1999', phoneDigits: 7]
  String phonePrefix = String.valueOf(vars.get('journeyPhonePrefix') ?: regionalAddress.phonePrefix)
  int phoneDigits = (regionalAddress.phoneDigits ?: 8) as int
  long phoneModulo = (long) Math.pow(10, phoneDigits)
  long phoneSuffix = Math.floorMod((long) phoneRunSeed + threadIndex, phoneModulo)
  String phoneFormat = "%0${phoneDigits}d"
  String generatedPhone = "${phonePrefix}${String.format(phoneFormat, phoneSuffix)}"
  def payload = [
    email: email,
    password: password,
    firstName: 'JMeter',
    lastName: "${journeyCountryCode}User${userNumber}",
    phoneNumber: String.valueOf(vars.get('journeyPhoneNumber') ?: generatedPhone),
    address: [
      line1: String.valueOf(vars.get('journeyAddressLine1') ?: regionalAddress.line1),
      line2: null,
      city: String.valueOf(vars.get('journeyCity') ?: regionalAddress.city),
      state: String.valueOf(vars.get('journeyState') ?: regionalAddress.state),
      postalCode: String.valueOf(vars.get('journeyPostalCode') ?: regionalAddress.postalCode),
      country: journeyCountryCode
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
  if (!persistGeneratedUser) return
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
    deviceId: "jmeter-${runId}-${journeyCountryCode}-${userNumber}",
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

def ensureTermsAccepted = { String token ->
  def state = atStep('terms acceptance probe') { request('GET', '/payment/api/v1/payment/state', null, token) }
  if ((state.status as int) == 451) {
    return acceptTerms(token)
  }
  requireStatus(state, [200], 'terms acceptance probe')
  token
}

def setupPayment = { String token ->
  def initialState = atStep('payment state before setup') { request('GET', '/payment/api/v1/payment/state', null, token) }
  if ((initialState.status as int) == 451) {
    token = acceptTerms(token)
    initialState = atStep('payment state after terms') { request('GET', '/payment/api/v1/payment/state', null, token) }
  }
  requireStatus(initialState, [200], 'payment state before setup')

  def options = requireStatus(
    atStep('secure card enrollment options') {
      request('GET', '/payment/api/v1/payment/cards/enrollment-options', null, token)
    },
    [200],
    'secure card enrollment options'
  )
  def regionalOptions = (options.json instanceof List ? options.json : []).findAll { option ->
    String.valueOf(option.countryCode ?: '').equalsIgnoreCase(journeyCountryCode) &&
      String.valueOf(option.currency ?: '').equalsIgnoreCase(journeyCurrency)
  }
  String discoveredNetworkId = String.valueOf(
    vars.get('journeyNetworkId') ?: props.getProperty('journey_network_id', '')
  ).trim()
  def enrollmentOption = discoveredNetworkId
    ? regionalOptions.find { String.valueOf(it.networkId ?: '').equalsIgnoreCase(discoveredNetworkId) }
    : regionalOptions.sort { left, right ->
        String.valueOf(left.networkId ?: '') <=> String.valueOf(right.networkId ?: '')
      }.find()
  if (enrollmentOption == null) {
    throw new IllegalStateException(
      "no unambiguous secure card enrollment route for ${journeyCountryCode}/${journeyCurrency}" +
        (discoveredNetworkId ? " network=${discoveredNetworkId}" : '')
    )
  }

  def enrollment = requireStatus(
    atStep('start secure card enrollment') {
      request('POST', '/payment/api/v1/payment/cards/enrollments', [
        enterpriseId: enrollmentOption.enterpriseId,
        networkId: enrollmentOption.networkId,
        chargingCountry: enrollmentOption.countryCode,
        currency: enrollmentOption.currency,
        returnUrl: 'https://electrahub.net/payment/card-setup-test'
      ], token)
    },
    [200, 201],
    'start secure card enrollment'
  )
  String enrollmentId = String.valueOf(enrollment.json?.enrollmentId ?: '').trim()
  String provider = String.valueOf(enrollment.json?.provider ?: '').trim().toUpperCase(Locale.ROOT)
  String environment = String.valueOf(enrollment.json?.environment ?: '').trim().toUpperCase(Locale.ROOT)
  String clientSecret = String.valueOf(enrollment.json?.clientSecret ?: '').trim()
  String publishableKey = String.valueOf(enrollment.json?.publishableKey ?: '').trim()
  if (!enrollmentId || provider != 'STRIPE' || environment != 'SANDBOX') {
    throw new IllegalStateException(
      "secure card enrollment must use Stripe SANDBOX for this saved-card E2E; provider=${provider} environment=${environment}"
    )
  }
  def setupIntentMatcher = clientSecret =~ /^(seti_[A-Za-z0-9_]+)_secret_[A-Za-z0-9_]+$/
  if (!setupIntentMatcher.matches()) {
    throw new IllegalStateException('secure card enrollment returned an invalid Stripe client secret')
  }
  String setupIntentId = setupIntentMatcher.group(1)
  def stripeConfirmation = atStep('confirm Stripe sandbox SetupIntent') {
    stripeFormRequest(setupIntentId, publishableKey, clientSecret)
  }
  if ((stripeConfirmation.status as int) != 200) {
    // Stripe error payloads can echo the SetupIntent client secret. Keep the
    // failure status-only so neither TeamCity logs nor JMeter results expose it.
    throw new IllegalStateException(
      "confirm Stripe sandbox SetupIntent failed with HTTP ${stripeConfirmation.status}; response body redacted"
    )
  }
  if (stripeConfirmation.json?.livemode != false ||
      !String.valueOf(stripeConfirmation.json?.status ?: '').equalsIgnoreCase('succeeded') ||
      !String.valueOf(stripeConfirmation.json?.payment_method ?: '').startsWith('pm_')) {
    throw new IllegalStateException('Stripe sandbox SetupIntent did not produce a reusable test payment method')
  }

  def card = requireStatus(
    atStep('complete secure card enrollment') {
      request(
        'POST',
        "/payment/api/v1/payment/cards/enrollments/${URLEncoder.encode(enrollmentId, 'UTF-8')}/complete",
        [nickname: "JMeter ${journeyCountryCode} ${userNumber}"],
        token
      )
    },
    [200, 201],
    'complete secure card enrollment'
  )
  if (!card.json?.id || card.json?.providerReady != true ||
      !String.valueOf(card.json?.gatewayProvider ?: '').equalsIgnoreCase('STRIPE')) {
    throw new IllegalStateException("secure card enrollment did not return a provider-backed Stripe card: ${card.body}")
  }
  vars.put('paymentCardId', String.valueOf(card.json.id))
  vars.put('paymentGatewayProvider', 'STRIPE')

  if (!cardOnlyPayment) {
    BigDecimal setupTopupAmount = action == 'idle-fee-wallet-reserve'
      ? new BigDecimal(props.getProperty('idle_fee_wallet_insufficient_balance', '54.99'))
      : action == 'low-balance-continue'
        ? lowBalanceContinueWalletBalance
        : walletTopupAmount
    BigDecimal walletBefore = new BigDecimal(String.valueOf(initialState.json?.wallet?.balance ?: 0))
    String topUpIdempotencyKey = UUID.randomUUID().toString()
    def topup = atStep('provider-backed wallet topup') { request('POST', '/payment/api/v1/payment/wallet/topups', [
      amount: setupTopupAmount,
      source: 'MANUAL',
      note: "JMeter ${runId}",
      cardId: vars.get('paymentCardId'),
      idempotencyKey: topUpIdempotencyKey,
      returnUrl: 'https://electrahub.net/payment/topup-test'
    ], token) }
    requireStatus(topup, [201, 200], 'provider-backed wallet topup')
    String topUpId = String.valueOf(topup.json?.topUp?.id ?: '').trim()
    int topUpPolls = 0
    while (!String.valueOf(topup.json?.status ?: '').equalsIgnoreCase('COMPLETED') && topUpPolls < 30) {
      if (topup.json?.customerAction != null) {
        throw new IllegalStateException('pm_card_visa sandbox top-up unexpectedly requires customer action')
      }
      if (!topUpId) {
        throw new IllegalStateException('provider-backed wallet top-up returned no top-up id')
      }
      sleep(1000)
      topup = requireStatus(
        atStep('complete provider-backed wallet topup') {
          request(
            'POST',
            "/payment/api/v1/payment/wallet/topups/${URLEncoder.encode(topUpId, 'UTF-8')}/customer-action-completions",
            null,
            token
          )
        },
        [200],
        'complete provider-backed wallet topup'
      )
      topUpPolls++
    }
    if (!String.valueOf(topup.json?.status ?: '').equalsIgnoreCase('COMPLETED')) {
      throw new IllegalStateException("provider-backed wallet top-up did not complete: ${topup.body}")
    }
    BigDecimal walletAfter = new BigDecimal(String.valueOf(topup.json?.walletBalance ?: 0))
    if (walletAfter.compareTo(walletBefore.add(setupTopupAmount)) < 0) {
      throw new IllegalStateException(
        "provider-backed wallet top-up balance mismatch: before=${walletBefore} amount=${setupTopupAmount} after=${walletAfter}"
      )
    }
  } else {
    logLine('card-only payment selected; wallet top-up is intentionally skipped')
  }

  def cards = requireStatus(atStep('payment cards after setup') { request('GET', '/payment/api/v1/payment/cards', null, token) }, [200], 'payment cards after setup')
  if (!vars.get('paymentCardId')) {
    def firstCard = cards.json instanceof List && !cards.json.isEmpty() ? cards.json[0] : null
    if (firstCard?.id) {
      vars.put('paymentCardId', String.valueOf(firstCard.id))
    }
  }
  def state = requireStatus(atStep('payment state after setup') { request('GET', '/payment/api/v1/payment/state', null, token) }, [200], 'payment state after setup')
  if (validateChargingProgress) {
    String walletCountry = String.valueOf(state.json?.wallet?.countryCode ?: '').toUpperCase(Locale.ROOT)
    String walletCurrency = String.valueOf(state.json?.wallet?.currency ?: '').toUpperCase(Locale.ROOT)
    if (walletCountry != journeyCountryCode || walletCurrency != journeyCurrency) {
      throw new IllegalStateException(
        "wallet geography mismatch: expected=${journeyCountryCode}/${journeyCurrency} actual=${walletCountry}/${walletCurrency}"
      )
    }
  }
  logLine("payment ready method=${sessionPaymentMethod} provider=${vars.get('paymentGatewayProvider')} country=${journeyCountryCode} currency=${journeyCurrency} wallet=${state.json?.wallet?.balance} card=${vars.get('paymentCardId') ?: 'unknown'}")
  token
}

def paymentState = { String token, String stepName = 'payment state' ->
  requireStatus(atStep(stepName) { request('GET', '/payment/api/v1/payment/state', null, token) }, [200], stepName)
}

def paymentCardId = { String token ->
  String cardId = vars.get('paymentCardId')
  if (cardId) return cardId
  def cards = requireStatus(atStep('payment cards') { request('GET', '/payment/api/v1/payment/cards', null, token) }, [200], 'payment cards')
  def firstCard = cards.json instanceof List && !cards.json.isEmpty() ? cards.json[0] : null
  cardId = firstCard?.id ? String.valueOf(firstCard.id) : ''
  if (!cardId) {
    throw new IllegalStateException('payment card is required for auto top-up regression')
  }
  vars.put('paymentCardId', cardId)
  cardId
}

def deleteTestPaymentCard = { String token ->
  String cardId = vars.get('paymentCardId') ?: ''
  if (!cardId) {
    logLine('test account cleanup did not find an active card to remove')
    return
  }
  def response = atStep('remove test payment card') {
    request('DELETE', "/payment/api/v1/payment/cards/${URLEncoder.encode(cardId, 'UTF-8')}", null, token)
  }
  requireStatus(response, [200, 204, 404], 'remove test payment card')
  vars.put('paymentCardDeleted', 'true')
  logLine("removed generated payment card=${cardId}")
}

def deleteTestUser = {
  if (!cleanupTestAccount) return
  String normalizedEmail = email.toLowerCase(Locale.ROOT)
  if (!normalizedEmail.endsWith('@electrahub.test')) {
    throw new IllegalStateException("refusing to delete a non-test account: ${email}")
  }
  if (!cleanupAdminToken || cleanupAdminToken.startsWith('%')) {
    throw new IllegalStateException('cleanup_test_account requires the protected cleanup_admin_token parameter')
  }
  String uid = vars.get('userId') ?: ''
  if (!uid) {
    throw new IllegalStateException('generated test user has no userId; refusing account cleanup')
  }
  def response = atStep('delete generated test user') {
    request('DELETE', "/user/api/v1/admin/users/${URLEncoder.encode(uid, 'UTF-8')}", null, cleanupAdminToken)
  }
  requireStatus(response, [200, 202, 204, 404], 'delete generated test user')
  vars.put('testUserDeleted', 'true')
  logLine("deleted generated test user=${uid}")
}

def cleanupGeneratedTestArtifacts = { String token ->
  if (!cleanupTestAccount || !token) return
  deleteTestPaymentCard(token)
  deleteTestUser()
}

def configureAutoTopUp = { String token, boolean enabled, BigDecimal threshold, BigDecimal amount ->
  String cardId = paymentCardId(token)
  def response = requireStatus(atStep(enabled ? 'enable auto top-up' : 'disable auto top-up') {
    request('PUT', '/payment/api/v1/payment/auto-topup', [
      enabled: enabled,
      threshold: threshold,
      amount: amount,
      cardId: cardId
    ], token)
  }, [200], enabled ? 'enable auto top-up' : 'disable auto top-up')
  logLine("auto top-up configured enabled=${response.json?.enabled} threshold=${response.json?.threshold} amount=${response.json?.amount} card=${response.json?.cardId}")
  response
}

def walletBalance = { Map state ->
  ((state.json?.wallet?.balance ?: 0) as BigDecimal)
}

def runSessionBalanceCheck = { String token, BigDecimal projectedCharge, String idempotencySuffix ->
  String accountId = vars.get('userId') ?: ''
  if (!accountId) {
    throw new IllegalStateException('userId is required for session balance check')
  }
  def response = requireStatus(atStep('session balance check') {
    request('POST', '/payment/api/v1/payment/internal/session-balance-checks', [
      accountId: accountId,
      sessionId: "jmeter-${runId}-${userNumber}-${idempotencySuffix}",
      projectedCharge: projectedCharge,
      lowBalanceThreshold: lowBalanceThreshold,
      currency: journeyCurrency,
      idempotencyKey: UUID.randomUUID().toString()
    ], token)
  }, [200], 'session balance check')
  logLine("balance check status=${response.json?.status} sufficient=${response.json?.sufficientBalance} autoTopUp=${response.json?.autoTopUpApplied} before=${response.json?.walletBalanceBefore} after=${response.json?.walletBalanceAfter} projected=${projectedCharge}")
  response
}

def validateLowBalanceDecisionFlow = { String token ->
  configureAutoTopUp(token, false, new BigDecimal(props.getProperty('auto_topup_threshold', '100.00')), new BigDecimal(props.getProperty('auto_topup_amount', '50.00')))
  BigDecimal balance = walletBalance(paymentState(token, 'payment state before low balance check'))
  BigDecimal projectedCharge = balance.add(lowBalanceThreshold).add(new BigDecimal(props.getProperty('low_balance_extra_charge', '25.00')))
  def response = runSessionBalanceCheck(token, projectedCharge, 'low-balance')
  if (response.json?.autoTopUpApplied == true || response.json?.sufficientBalance == true || String.valueOf(response.json?.status ?: '') != 'LOW_BALANCE') {
    throw new IllegalStateException("low-balance decision mismatch: ${response.body}")
  }
}

def validateAutoTopUpDecisionFlow = { String token ->
  BigDecimal threshold = new BigDecimal(props.getProperty('auto_topup_threshold', '100.00'))
  BigDecimal amount = new BigDecimal(props.getProperty('auto_topup_amount', '50.00'))
  configureAutoTopUp(token, true, threshold, amount)
  BigDecimal balance = walletBalance(paymentState(token, 'payment state before auto top-up check'))
  BigDecimal projectedCharge = balance.add(new BigDecimal(props.getProperty('auto_topup_projected_extra_charge', '10.00')))
  def response = runSessionBalanceCheck(token, projectedCharge, 'auto-topup')
  String status = String.valueOf(response.json?.status ?: '')
  if (response.json?.autoTopUpApplied != true || response.json?.sufficientBalance != true || !(status in ['OK', 'AUTO_TOP_UP_APPLIED', 'SUFFICIENT'])) {
    throw new IllegalStateException("auto top-up decision mismatch: ${response.body}")
  }
  BigDecimal before = ((response.json?.walletBalanceBefore ?: 0) as BigDecimal)
  BigDecimal after = ((response.json?.walletBalanceAfter ?: 0) as BigDecimal)
  if (!(after > before)) {
    throw new IllegalStateException("auto top-up did not increase wallet balance: before=${before} after=${after}")
  }
}

def discoverChargers = { String token ->
  String countryArg = explicitJourneyCountry ?: props.getProperty('charger_country_code', '').trim()
  if (!countryArg && ['charging', 'full', 'idle-fee', 'idle-fee-wallet-reserve', 'subscription-discount', 'low-balance-continue'].contains(action)) {
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
      location {
        ocpiLocationId
        name
        network { id }
        enterprise { id }
      }
      pricing { idleFee { enabled pricePerMinute currency sourceTariffId } }
      evses { uid status connectors { id status available standard powerType tariffIds tariffs { tariffId energyPrice parkingPrice currency } } }
    }
  }"""
  def list = requireStatus(atStep('charger graphql list') { request('POST', '/charger/graphql', [query: listQuery], token) }, [200], 'charger graphql list')
  if (list.body.contains('"errors"')) {
    throw new IllegalStateException("charger graphql list returned errors: ${list.body}")
  }

  if (dynamicConnectorSelection) {
    String preferredJourneyNetworkId = String.valueOf(
      vars.get('journeyPreferredNetworkId') ?: props.getProperty('journey_network_id', '')
    ).trim()
    def chargers = (list.json?.data?.ocpiChargers ?: []).findAll { charger ->
      !preferredJourneyNetworkId ||
        String.valueOf(charger.location?.network?.id ?: '').equalsIgnoreCase(preferredJourneyNetworkId)
    }
    if (preferredJourneyNetworkId && chargers.isEmpty()) {
      throw new IllegalStateException(
        "no ${journeyCountryCode} chargers were found for required network ${preferredJourneyNetworkId}"
      )
    }
    def activePairs = [] as Set
    def simulatorAvailablePairs = [] as Set
    def simulatorConnectorNumbers = [:]
    def simulatorInventory = simulatorRequest('GET', '/api/v1/chargers?limit=1000', null, requestTimeoutMs)
    if ((simulatorInventory.status as int) == 200) {
      def simulatorChargers = simulatorInventory.json?.items instanceof List ? simulatorInventory.json.items : []
      for (def simulatorCharger : simulatorChargers) {
        String simulatorChargerId = String.valueOf(simulatorCharger.chargerId ?: '')
        for (def summary : (simulatorCharger.connectorSummary ?: [])) {
          String simulatorConnectorRef = String.valueOf(summary.connectorRef ?: '')
          int simulatorConnectorNumber = (summary.connectorId ?: connectorNumberFromId(simulatorConnectorRef, 1)) as int
          String simulatorStatus = String.valueOf(summary.status ?: '')
          boolean simulatorAvailable = simulatorStatus.equalsIgnoreCase('AVAILABLE') || simulatorStatus.equalsIgnoreCase('PREPARING')
          if (simulatorChargerId && simulatorConnectorRef) {
            String simulatorPair = "${simulatorChargerId}/${simulatorConnectorRef}"
            simulatorConnectorNumbers[simulatorPair] = simulatorConnectorNumber
            if (simulatorAvailable) {
              simulatorAvailablePairs << simulatorPair
            }
          }
        }
      }
      logLine("simulator available connector refs=${simulatorAvailablePairs.size()}")
    } else {
      logLine("simulator connector inventory unavailable HTTP ${simulatorInventory.status}; using charger service inventory only")
    }
    def chargerIds = chargers.collect { String.valueOf(it.chargerId ?: '') }.findAll { it }
    if (!chargerIds.isEmpty()) {
      String query = chargerIds.collect { "chargerIds=${URLEncoder.encode(it, 'UTF-8')}" }.join('&')
      def activeBeforeSelection = requireStatus(atStep('global active sessions before connector selection') {
        request('GET', "/session/api/v1/sessions/internal/active-by-chargers?${query}", null, token)
      }, [200], 'global active sessions before connector selection')
      def activeSessions = activeBeforeSelection.json instanceof List ? activeBeforeSelection.json : []
      for (def session : activeSessions) {
        String activeChargerId = String.valueOf(session.chargerId ?: '')
        String activeConnectorId = String.valueOf(session.connectorRef ?: '')
        if (activeChargerId && activeConnectorId) {
          activePairs << "${activeChargerId}/${activeConnectorId}"
        }
      }
    }
    def candidates = []
    for (def charger : chargers) {
      if (String.valueOf(charger.status ?: '').equalsIgnoreCase('OFFLINE')) continue
      for (def evse : (charger.evses ?: [])) {
        for (def connector : (evse.connectors ?: [])) {
          String connectorPair = "${charger.chargerId ?: ''}/${connector.id ?: ''}"
          String connectorStatus = String.valueOf(connector.status ?: '')
          boolean connectorAvailable = connector.available == true || connectorStatus.equalsIgnoreCase('AVAILABLE')
          boolean idleFeeOk = !['idle-fee', 'idle-fee-wallet-reserve'].contains(action) || charger.pricing?.idleFee?.enabled == true
          boolean notAlreadyActive = !activePairs.contains(connectorPair)
          boolean simulatorHasConnector = simulatorAvailablePairs.isEmpty() || simulatorAvailablePairs.contains(connectorPair)
          if (connectorAvailable && idleFeeOk && notAlreadyActive && simulatorHasConnector) {
            candidates << [charger: charger, connector: connector, simulatorConnectorNumber: simulatorConnectorNumbers[connectorPair]]
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
        networkId: item.charger.location?.network?.id as String,
        enterpriseId: item.charger.location?.enterprise?.id as String,
        connectorId: item.connector.id as String,
        connectorNumber: (item.simulatorConnectorNumber ?: connectorNumberFromId(item.connector.id as String, 1)) as int,
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
      connectorNumber = (selected.simulatorConnectorNumber ?: connectorNumberFromId(connectorId, connectorNumber ?: 1)) as int
      vars.put('chargerId', chargerId)
      vars.put('locationId', locationId)
      vars.put('connectorId', connectorId)
      vars.put('connectorNumber', String.valueOf(connectorNumber))
      vars.put('connectorType', connectorType)
      String journeyNetworkId = String.valueOf(selected.charger.location?.network?.id ?: '').trim()
      String journeyEnterpriseId = String.valueOf(selected.charger.location?.enterprise?.id ?: '').trim()
      if (!journeyNetworkId || !journeyEnterpriseId) {
        throw new IllegalStateException("selected charger ${chargerId}/${connectorId} is missing network/enterprise ownership")
      }
      vars.put('journeyNetworkId', journeyNetworkId)
      vars.put('journeyEnterpriseId', journeyEnterpriseId)
      logLine("selected available connector ${chargerId}/${connectorId} simulatorConnectorNumber=${connectorNumber} location=${locationId} network=${journeyNetworkId} candidate=${Math.floorMod(threadIndex - 1, candidates.size()) + 1}/${candidates.size()}")
    } else if (['idle-fee', 'idle-fee-wallet-reserve'].contains(action)) {
      throw new IllegalStateException('dynamic connector selection found no available idle-fee connector')
    } else if (dynamicConnectorSelection) {
      throw new IllegalStateException('dynamic connector selection found no globally available connector')
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
      location {
        ocpiLocationId
        name
        network { id }
        enterprise { id }
      }
      pricing { tariffs { tariffId energyPrice parkingPrice currency } idleFee { enabled pricePerMinute currency sourceTariffId } }
      evses { uid status connectors { id status available standard powerType tariffIds tariffs { tariffId energyPrice parkingPrice currency } } }
    }
  }"""
  def view = requireStatus(atStep('charger graphql view') { request('POST', '/charger/graphql', [query: viewQuery], token) }, [200], 'charger graphql view')
  if (view.body.contains('"errors"')) {
    throw new IllegalStateException("charger graphql view returned errors for ${chargerId}/${connectorId}: ${view.body}")
  }
  if (['idle-fee', 'idle-fee-wallet-reserve'].contains(action)) {
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
  String startCardId = cardOnlyPayment ? paymentCardId(token) : null
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
    if (exclusiveConnectorAllocation) {
      attempts << [
        chargerId: chargerId,
        locationId: locationId,
        connectorId: connectorId,
        connectorNumber: connectorNumber,
        connectorType: connectorType
      ]
      logLine("using exclusively allocated connector ${chargerId}/${connectorId}")
    } else {
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
      paymentMethod: sessionPaymentMethod,
      cardId: startCardId,
      currency: journeyCurrency,
      idempotencyKey: UUID.randomUUID().toString(),
      acknowledgeNegativeBalanceRisk: action == 'low-balance-continue'
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
    if (!exclusiveConnectorAllocation && [409, 503].contains(response.status as int) && attemptNumber < attempts.size()) {
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

def validateIdleFeeWalletReserveStart = { String token ->
  if (!chargerId || !connectorId || !locationId) {
    throw new IllegalStateException('idle-fee wallet reserve validation requires a discovered connector')
  }
  def state = paymentState(token, 'payment state before idle-fee reserve start')
  BigDecimal balance = walletBalance(state)
  BigDecimal configuredBalance = new BigDecimal(props.getProperty('idle_fee_wallet_insufficient_balance', '54.99'))
  if (balance.compareTo(configuredBalance) != 0) {
    throw new IllegalStateException("idle-fee reserve test wallet mismatch: expected=${configuredBalance} actual=${balance}")
  }

  String uid = vars.get('userId') ?: ''
  def connectorAttempts = []
  String candidatesJson = vars.get('connectorCandidatesJson')
  if (dynamicConnectorSelection && candidatesJson) {
    def parsedCandidates = parseJson(candidatesJson) ?: []
    if (!parsedCandidates.isEmpty()) {
      int startAt = Math.floorMod(threadIndex - 1, parsedCandidates.size())
      int maxAttempts = Math.min(
        parsedCandidates.size(),
        Math.max(5, props.getProperty('connector_start_attempts', String.valueOf(parsedCandidates.size())) as int)
      )
      for (int i = 0; i < maxAttempts; i++) {
        connectorAttempts << parsedCandidates[(startAt + i) % parsedCandidates.size()]
      }
    }
  }
  if (connectorAttempts.isEmpty()) {
    connectorAttempts << [
      chargerId: chargerId,
      locationId: locationId,
      connectorId: connectorId,
      connectorNumber: connectorNumber,
      connectorType: connectorType
    ]
  }

  def isExpectedConnectorContention = { Map response ->
    if ((response.status as int) != 409) return false
    String message = String.valueOf(response.json?.message ?: '').trim()
    message == 'Connector is not available for charging.' ||
      message == 'Connector is occupied. Please unplug the vehicle before starting a new session.'
  }

  int candidateAttempt = 0
  for (def candidate : connectorAttempts) {
    candidateAttempt++
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

    String idempotencyKey = UUID.randomUUID().toString()
    def startPayload = [
      chargerId: chargerId,
      locationId: locationId,
      connectorId: connectorId,
      connectorNumber: connectorNumber,
      connectorType: connectorType,
      idToken: uid,
      paymentMethod: 'WALLET',
      currency: 'USD',
      idempotencyKey: idempotencyKey,
      acknowledgeNegativeBalanceRisk: false
    ]

    def warning = atStep("idle-fee wallet negative-balance warning candidate ${candidateAttempt}") {
      request('POST', '/session/api/v1/sessions/start', startPayload, token, sessionCommandTimeoutMs)
    }
    if (isExpectedConnectorContention(warning) && candidateAttempt < connectorAttempts.size()) {
      logLine("idle-fee warning connector contention charger=${chargerId}/${connectorId}; retrying the warning and acknowledgement contract on another connector")
      sleep(1000)
      continue
    }
    requireStatus(warning, [200], 'idle-fee wallet negative-balance warning')
    if (String.valueOf(warning.json?.status ?: '') != 'WARNING') {
      throw new IllegalStateException("idle-fee reserve warning response has unexpected status: ${warning.body}")
    }
    if (String.valueOf(warning.json?.warningCode ?: '') != 'WALLET_BALANCE_MAY_GO_NEGATIVE') {
      throw new IllegalStateException("idle-fee reserve warning response has unexpected warningCode: ${warning.body}")
    }
    if (warning.json?.sessionId != null && String.valueOf(warning.json.sessionId).trim()) {
      throw new IllegalStateException("idle-fee reserve warning must not create a session: ${warning.body}")
    }
    if (warning.json?.walletBalance == null || warning.json?.availableWalletBalance == null || warning.json?.activeAuthorizationHolds == null || warning.json?.requiredWalletBalance == null || warning.json?.projectedBalance == null) {
      throw new IllegalStateException("idle-fee reserve warning is missing wallet, available, hold, required, or projected balance fields: ${warning.body}")
    }
    if (String.valueOf(warning.json?.currency ?: '').toUpperCase(Locale.ROOT) != 'USD') {
      throw new IllegalStateException("idle-fee reserve warning has unexpected currency: ${warning.body}")
    }

    BigDecimal warningBalance = new BigDecimal(String.valueOf(warning.json.walletBalance))
    BigDecimal availableBalance = new BigDecimal(String.valueOf(warning.json.availableWalletBalance))
    BigDecimal activeAuthorizationHolds = new BigDecimal(String.valueOf(warning.json.activeAuthorizationHolds))
    BigDecimal requiredBalance = new BigDecimal(String.valueOf(warning.json.requiredWalletBalance))
    BigDecimal projectedBalance = new BigDecimal(String.valueOf(warning.json.projectedBalance))
    if (warningBalance.compareTo(balance) != 0) {
      throw new IllegalStateException("idle-fee reserve warning wallet balance mismatch: expected=${balance} actual=${warningBalance}")
    }
    BigDecimal expectedAvailableBalance = warningBalance.subtract(activeAuthorizationHolds).setScale(2, RoundingMode.HALF_UP)
    if (availableBalance.compareTo(expectedAvailableBalance) != 0) {
      throw new IllegalStateException("idle-fee reserve warning available balance mismatch: expected=${expectedAvailableBalance} actual=${availableBalance}")
    }
    if (requiredBalance.compareTo(availableBalance) <= 0) {
      throw new IllegalStateException("idle-fee reserve warning must report required balance above available balance: available=${availableBalance} required=${requiredBalance}")
    }
    BigDecimal expectedProjectedBalance = availableBalance.subtract(requiredBalance).setScale(2, RoundingMode.HALF_UP)
    if (projectedBalance.compareTo(expectedProjectedBalance) != 0 || projectedBalance.signum() >= 0) {
      throw new IllegalStateException("idle-fee reserve warning projected balance mismatch: expected=${expectedProjectedBalance} actual=${projectedBalance}")
    }

    def beforeAcknowledgement = activeSession(token)
    def sessionsBeforeAcknowledgement = beforeAcknowledgement.json instanceof List ? beforeAcknowledgement.json : []
    if (!sessionsBeforeAcknowledgement.isEmpty()) {
      throw new IllegalStateException("idle-fee reserve warning created an active session before acknowledgement: ${beforeAcknowledgement.body}")
    }

    def acknowledgedPayload = new LinkedHashMap(startPayload)
    acknowledgedPayload.acknowledgeNegativeBalanceRisk = true
    def accepted = atStep("acknowledge idle-fee wallet negative-balance risk candidate ${candidateAttempt}") {
      request('POST', '/session/api/v1/sessions/start', acknowledgedPayload, token, sessionCommandTimeoutMs)
    }
    if (isExpectedConnectorContention(accepted)) {
      def afterContention = activeSession(token)
      def sessionsAfterContention = afterContention.json instanceof List ? afterContention.json : []
      if (!sessionsAfterContention.isEmpty()) {
        throw new IllegalStateException("connector contention response created an active session during acknowledged retry: ${afterContention.body}")
      }
      if (candidateAttempt < connectorAttempts.size()) {
        logLine("idle-fee acknowledgement connector contention charger=${chargerId}/${connectorId}; retrying the complete contract on another connector")
        sleep(1000)
        continue
      }
    }
    requireStatus(accepted, [200, 201], 'acknowledge idle-fee wallet negative-balance risk')
    String sessionId = String.valueOf(accepted.json?.sessionId ?: '').trim()
    if (!sessionId) {
      throw new IllegalStateException("acknowledged idle-fee wallet start did not create a session: ${accepted.body}")
    }
    if (String.valueOf(accepted.json?.status ?: '').equalsIgnoreCase('WARNING') || accepted.json?.warningCode != null) {
      throw new IllegalStateException("acknowledged idle-fee wallet start returned another warning: ${accepted.body}")
    }
    vars.put('sessionId', sessionId)

    long activeDeadline = System.currentTimeMillis() + 60000L
    def activeAfterAcknowledgement = null
    def sessionsAfterAcknowledgement = []
    while (System.currentTimeMillis() < activeDeadline) {
      activeAfterAcknowledgement = activeSession(token)
      sessionsAfterAcknowledgement = activeAfterAcknowledgement.json instanceof List ? activeAfterAcknowledgement.json : []
      if (sessionsAfterAcknowledgement.size() == 1 && String.valueOf(sessionsAfterAcknowledgement[0].id ?: '') == sessionId) {
        break
      }
      if (sessionsAfterAcknowledgement.size() > 1) {
        throw new IllegalStateException("acknowledged idle-fee wallet start created more than one active session: ${activeAfterAcknowledgement.body}")
      }
      sleep(3000)
    }
    if (sessionsAfterAcknowledgement.size() != 1 || String.valueOf(sessionsAfterAcknowledgement[0].id ?: '') != sessionId) {
      throw new IllegalStateException("acknowledged idle-fee wallet start did not expose exactly one active session ${sessionId}: ${activeAfterAcknowledgement?.body}")
    }

    logLine("idle-fee wallet warning acknowledged session=${sessionId} balance=${warningBalance} holds=${activeAuthorizationHolds} available=${availableBalance} required=${requiredBalance} projected=${projectedBalance} charger=${chargerId}/${connectorId} candidate=${candidateAttempt}/${connectorAttempts.size()}")
    return sessionId
  }

  throw new IllegalStateException("idle-fee wallet warning acknowledgement exhausted ${connectorAttempts.size()} connector candidate(s)")
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

def validateSessionChargingProgress = { String token, String sessionId ->
  int timeoutSeconds = props.getProperty('charging_progress_timeout_seconds', '120') as int
  def progressing = waitForActiveSessionPredicate(token, sessionId, 'charging state with meter and cost progress', timeoutSeconds) { session ->
    String status = String.valueOf(session.status ?: '').toUpperCase(Locale.ROOT)
    BigDecimal energy = new BigDecimal(String.valueOf(session.energyDeliveredKwh ?: 0))
    BigDecimal cost = new BigDecimal(String.valueOf(session.estimatedCost ?: 0))
    String currency = String.valueOf(session.currency ?: '').toUpperCase(Locale.ROOT)
    ['CHARGING', 'ACTIVE', 'STARTED'].contains(status) && energy > 0 && cost > 0 && currency == journeyCurrency
  }
  logLine(
    "charging progress ok session=${sessionId} status=${progressing.status} energyKwh=${progressing.energyDeliveredKwh} " +
      "powerKw=${progressing.currentPowerKw} cost=${progressing.estimatedCost} currency=${progressing.currency}"
  )
  progressing
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

def authorizeSimulatorUnplugIfRequired = { String token, String sessionId ->
  def active = findActiveSession(token, sessionId)
  if (active == null) {
    logLine("simulator unplug authorization skipped because session is already terminal session=${sessionId}")
    return
  }

  boolean requiresAuthorization = active.idleFeeEnabled == true || active.unplugRequiredToStop == true
  if (!requiresAuthorization) {
    return
  }

  def simulator = active.simulator instanceof Map ? active.simulator : [:]
  String code = simulator.securityCode == null ? null : String.valueOf(simulator.securityCode).trim()
  String simulatorChargerId = simulator.chargerId == null ? String.valueOf(chargerId) : String.valueOf(simulator.chargerId)
  String simulatorConnectorId = simulator.connectorId == null ? String.valueOf(connectorId) : String.valueOf(simulator.connectorId)
  if (!code) {
    throw new IllegalStateException("idle-fee session ${sessionId} is missing its simulator security code")
  }

  def response = atStep('authorize simulator unplug') {
    request('POST', "/session/api/v1/sessions/${sessionId}/simulator/verify-code", [
      code: code,
      action: 'UNPLUG',
      chargerId: simulatorChargerId,
      connectorId: simulatorConnectorId
    ], token, sessionCommandTimeoutMs)
  }
  requireStatus(response, [200], 'authorize simulator unplug')
  if (response.json?.valid != true || String.valueOf(response.json?.action ?: '').toUpperCase(Locale.ROOT) != 'UNPLUG') {
    throw new IllegalStateException("simulator unplug authorization was not granted for ${sessionId}: ${response.body}")
  }
  logLine("simulator unplug authorized session=${sessionId} charger=${simulatorChargerId} connector=${simulatorConnectorId}")
}

def sendSimulatorChargingStop = { String token, String sessionId ->
  authorizeSimulatorUnplugIfRequired(token, sessionId)
  int number = (vars.get('connectorNumber') ?: String.valueOf(connectorNumber ?: 1)) as int
  long meterStopWh = (props.getProperty('idle_flow_meter_stop_wh', props.getProperty('idle_flow_meter_wh', '1201500')) as long)
  def response = atStep('simulator charging stop') {
    simulatorRequest('POST', "/api/v1/chargers/${chargerId}/connectors/${number}/charging/stop", [
      reason: 'EVDisconnected',
      meterStopWh: meterStopWh,
      forwardOcpp: true
    ])
  }
  String stopBody = String.valueOf(response.body ?: '')
  String normalizedStopBody = stopBody.toLowerCase(Locale.ROOT)
  if ((response.status as int) == 400 &&
    (normalizedStopBody.contains('"status":"stopped"') ||
      normalizedStopBody.contains('connector not found') ||
      normalizedStopBody.contains('transaction not found'))) {
    logLine("simulator charging stop returned non-fatal HTTP 400 after unplug signal; publishing Available fallback charger=${chargerId} connectorNumber=${number} body=${stopBody}")
    sendSimulatorStatus('Available')
    if ((props.getProperty('allow_backend_ocpp_status_fallback', 'false') as String).toBoolean()) {
      sendBackendOcppStatus('Available')
    }
    return
  }
  if ((response.status as int) == 404) {
    logLine("simulator charging stop returned 404; falling back to OCPP Available unplug signal charger=${chargerId} connectorNumber=${number} body=${response.body}")
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
  int chargingStateEvents = 0
  int receipts = 0
  int heartbeats = 0
  String currentEventName = ''
  List<String> currentDataLines = []
  String lastData = ''
  String lastEventName = ''
  def processEvent = {
    if (!currentEventName && currentDataLines.isEmpty()) return

    String eventName = currentEventName
    String data = currentDataLines.join('\n')
    lastEventName = eventName
    lastData = data.length() > 512 ? data.substring(0, 512) : data
    def payload = parseJson(data)
    String payloadType = String.valueOf(payload?.type ?: '').trim().toUpperCase(Locale.ROOT)
    String normalizedEventName = eventName.trim().toLowerCase(Locale.ROOT)

    if (normalizedEventName == 'connected' || payloadType == 'CONNECTED') connected++
    if (normalizedEventName == 'heartbeat' || payloadType == 'HEARTBEAT') heartbeats++

    def targetSessionPayload = String.valueOf(payload?.session?.id ?: '') == sessionId ? payload.session : null
    def targetSnapshotPayload = payload?.sessions instanceof Collection
      ? payload.sessions.find { String.valueOf(it?.id ?: '') == sessionId }
      : null
    boolean targetSession = targetSessionPayload != null
    boolean targetInSnapshot = targetSnapshotPayload != null
    boolean targetReceipt = String.valueOf(payload?.receipt?.sessionId ?: payload?.receipt?.session?.id ?: '') == sessionId

    if ((normalizedEventName == 'snapshot' || payloadType == 'SNAPSHOT') && targetInSnapshot) snapshots++
    if ((normalizedEventName == 'session' || payloadType == 'SESSION_UPDATED') && targetSession) updates++
    if ((normalizedEventName == 'receipt' || payloadType == 'RECEIPT') && targetReceipt) receipts++
    def targetStatePayload = targetSessionPayload ?: targetSnapshotPayload
    String targetStatus = String.valueOf(targetStatePayload?.status ?: '').toUpperCase(Locale.ROOT)
    if (['CHARGING', 'ACTIVE', 'STARTED'].contains(targetStatus)) chargingStateEvents++

    currentEventName = ''
    currentDataLines.clear()
  }
  BufferedReader reader = new BufferedReader(new InputStreamReader(conn.inputStream, StandardCharsets.UTF_8))
  while (System.currentTimeMillis() < deadline) {
    try {
      String line = reader.readLine()
      if (line == null) break
      if (line.isEmpty()) {
        processEvent()
      } else if (line.startsWith('event:')) {
        currentEventName = line.substring(6).trim()
      } else if (line.startsWith('data:')) {
        String dataLine = line.substring(5)
        currentDataLines.add(dataLine.startsWith(' ') ? dataLine.substring(1) : dataLine)
      }
    } catch (SocketTimeoutException ignored) {
      // Keep the SSE connection open until the monitoring window expires.
    } catch (IOException ioe) {
      logLine("sse stream ended while reading for ${sessionId}: ${ioe.message ?: ioe.class.name}")
      break
    }
  }
  processEvent()
  try { reader.close() } catch (Exception ignored) {}
  conn.disconnect()

  vars.put('sseConnectedEvents', String.valueOf(connected))
  vars.put('sseSnapshotEvents', String.valueOf(snapshots))
  vars.put('sseSessionEvents', String.valueOf(updates))
  vars.put('sseChargingStateEvents', String.valueOf(chargingStateEvents))
  vars.put('sseReceiptEvents', String.valueOf(receipts))
  vars.put('sseHeartbeatEvents', String.valueOf(heartbeats))

  if (chargingStateEvents <= 0) {
    throw new IllegalStateException(
      "SSE produced no target charging-state snapshot/session update for ${sessionId}; " +
        "snapshots=${snapshots}, updates=${updates}, connected=${connected}, heartbeats=${heartbeats}, " +
        "lastEvent=${lastEventName}, last=${lastData}"
    )
  }
  logLine("sse target charging state ok session=${sessionId} connected=${connected} snapshot=${snapshots} updates=${updates} chargingState=${chargingStateEvents} receipts=${receipts} heartbeats=${heartbeats}")
}

def stopSession = { String token, String sessionId ->
  def response = atStep('stop charging session') { request('POST', "/session/api/v1/sessions/${sessionId}/stop", [
    reason: 'REMOTE',
    userInitiated: true
  ], token, sessionCommandTimeoutMs) }
  requireStatus(response, [204, 200], 'stop charging session')
  logLine("stop requested session=${sessionId}")
}

def validateStoppedAndReceipt = { String token, String sessionId, boolean expectSubscriptionDiscount = false, boolean enforceFlowContract = true ->
  long deadline = System.currentTimeMillis() + 120000L
  boolean goneFromActive = false
  boolean unplugRequested = false
  long activeFirstSeenAt = 0L
  while (System.currentTimeMillis() < deadline) {
    def active = activeSession(token)
    def sessions = active.json instanceof List ? active.json : []
    def activeMatch = sessions.find { String.valueOf(it.id ?: '') == sessionId }
    if (activeMatch == null) {
      goneFromActive = true
      break
    }
    if (activeFirstSeenAt == 0L) {
      activeFirstSeenAt = System.currentTimeMillis()
    }
    if (!unplugRequested &&
      ((activeMatch.idleFeeEnabled == true &&
        activeMatch.unplugRequiredToStop == true &&
        String.valueOf(activeMatch.status ?: '').equalsIgnoreCase('SUSPENDED')) ||
        System.currentTimeMillis() - activeFirstSeenAt >= 10000L)) {
      logLine("session ${sessionId} still active after remote stop; sending simulator physical stop before receipt validation")
      sendSimulatorChargingStop(token, sessionId)
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
  if (validateChargingProgress && enforceFlowContract) {
    BigDecimal receiptEnergy = new BigDecimal(String.valueOf(receipt.json?.energyKwh ?: 0))
    BigDecimal receiptCost = new BigDecimal(String.valueOf(receipt.json?.totalCost ?: receipt.json?.costUsd ?: 0))
    String receiptCurrency = String.valueOf(receipt.json?.currency ?: '').toUpperCase(Locale.ROOT)
    String receiptPaymentMethod = String.valueOf(receipt.json?.paymentMethod ?: '').toUpperCase(Locale.ROOT)
    boolean paymentMethodMatches = cardOnlyPayment
      ? receiptPaymentMethod in ['CARD', 'CREDIT_CARD']
      : receiptPaymentMethod == 'WALLET'
    if (receiptEnergy <= 0 || receiptCost <= 0 || receiptCurrency != journeyCurrency || !paymentMethodMatches) {
      throw new IllegalStateException(
        "receipt flow mismatch for ${sessionId}: energy=${receiptEnergy} cost=${receiptCost} " +
          "currency=${receiptCurrency} paymentMethod=${receiptPaymentMethod} expected=${journeyCurrency}/${sessionPaymentMethod}"
      )
    }

    def completedTransaction = null
    deadline = System.currentTimeMillis() + 120000L
    while (System.currentTimeMillis() < deadline) {
      def transactionsResponse = requireStatus(
        atStep('session payment settlement') {
          request('GET', '/payment/api/v1/payment/session-transactions?limit=50', null, token, requestTimeoutMs)
        },
        [200],
        'session payment settlement'
      )
      def transactions = transactionsResponse.json instanceof List ? transactionsResponse.json : []
      def transaction = transactions.find { String.valueOf(it?.sessionId ?: '') == sessionId }
      if (transaction != null) {
        String transactionStatus = String.valueOf(transaction.status ?: '').trim().toUpperCase(Locale.ROOT)
        if (transactionStatus == 'FAILED') {
          throw new IllegalStateException("payment settlement failed for session ${sessionId}: ${json(transaction)}")
        }
        if (transactionStatus == 'COMPLETED') {
          completedTransaction = transaction
          break
        }
      }
      sleep(2000)
    }
    if (completedTransaction == null) {
      throw new IllegalStateException("payment settlement did not complete for session ${sessionId}")
    }

    String transactionPaymentMethod = String.valueOf(completedTransaction.paymentMethod ?: '').toUpperCase(Locale.ROOT)
    String transactionCurrency = String.valueOf(completedTransaction.currency ?: '').toUpperCase(Locale.ROOT)
    BigDecimal transactionAmount = new BigDecimal(String.valueOf(completedTransaction.amount ?: 0))
    boolean transactionPaymentMethodMatches = cardOnlyPayment
      ? transactionPaymentMethod in ['CARD', 'CREDIT_CARD']
      : transactionPaymentMethod == 'WALLET'
    BigDecimal settlementDifference = transactionAmount.subtract(receiptCost).abs()
    if (transactionAmount <= 0 || transactionCurrency != journeyCurrency ||
        !transactionPaymentMethodMatches || settlementDifference > new BigDecimal('0.01')) {
      throw new IllegalStateException(
        "payment settlement mismatch for ${sessionId}: amount=${transactionAmount} receiptCost=${receiptCost} " +
          "currency=${transactionCurrency} paymentMethod=${transactionPaymentMethod} " +
          "expected=${journeyCurrency}/${sessionPaymentMethod}"
      )
    }
    vars.put('paymentTransactionId', String.valueOf(completedTransaction.transactionId ?: ''))
    vars.put('paymentTransactionStatus', 'COMPLETED')
    logLine(
      "payment settlement ok session=${sessionId} transaction=${completedTransaction.transactionId} " +
        "amount=${transactionAmount} currency=${transactionCurrency} paymentMethod=${transactionPaymentMethod}"
    )
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
  logLine("receipt ok session=${sessionId} status=${receipt.json?.status} energyKwh=${receipt.json?.energyKwh} total=${receipt.json?.totalCost ?: receipt.json?.costUsd} currency=${receipt.json?.currency} paymentMethod=${receipt.json?.paymentMethod}")
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

  validateStoppedAndReceipt(token, sessionId)
}

def validateLowBalanceContinueFlow = { String token, String sessionId ->
  def beforeMeter = waitForActiveSessionPredicate(token, sessionId, 'charging active before high meter value', 60) { session ->
    ['CHARGING', 'ACTIVE', 'STARTED'].contains(String.valueOf(session.status ?: '').toUpperCase(Locale.ROOT))
  }
  BigDecimal startingWalletBalance = walletBalance(paymentState(token, 'payment state before high meter value'))
  if (startingWalletBalance.compareTo(lowBalanceContinueWalletBalance) != 0) {
    throw new IllegalStateException("low-balance continue wallet mismatch: expected=${lowBalanceContinueWalletBalance} actual=${startingWalletBalance}")
  }
  BigDecimal energyBefore = new BigDecimal(String.valueOf(beforeMeter.energyDeliveredKwh ?: beforeMeter.energyKwh ?: 0))
  BigDecimal costBefore = new BigDecimal(String.valueOf(beforeMeter.estimatedCost ?: 0))

  long meterWh = props.getProperty('low_balance_meter_wh', '99999999') as long
  sendSimulatorMeterValue(meterWh)
  def updated = waitForActiveSessionPredicate(token, sessionId, 'high meter energy or cost update while charging continues', 120) { session ->
    BigDecimal energyNow = new BigDecimal(String.valueOf(session.energyDeliveredKwh ?: session.energyKwh ?: 0))
    BigDecimal costNow = new BigDecimal(String.valueOf(session.estimatedCost ?: 0))
    energyNow.compareTo(energyBefore) > 0 || costNow.compareTo(costBefore) > 0
  }
  String status = String.valueOf(updated.status ?: '').toUpperCase(Locale.ROOT)
  if (!['PENDING', 'PREPARING', 'ACTIVE', 'SUSPENDED', 'CHARGING', 'STARTED'].contains(status)) {
    throw new IllegalStateException("high meter value did not leave session ${sessionId} active and nonterminal: ${json(updated)}")
  }
  if (String.valueOf(updated.stopReason ?: '').equalsIgnoreCase('LOW_BALANCE')) {
    throw new IllegalStateException("high meter value assigned LOW_BALANCE stop reason to continuing session ${sessionId}: ${json(updated)}")
  }
  if (updated.remoteStopRequestedAt != null && String.valueOf(updated.remoteStopRequestedAt).trim()) {
    throw new IllegalStateException("high meter value requested a remote stop for continuing session ${sessionId}: ${json(updated)}")
  }
  BigDecimal costAfter = new BigDecimal(String.valueOf(updated.estimatedCost ?: 0))
  BigDecimal projectedBalance = startingWalletBalance.subtract(costAfter)
  if (costAfter.compareTo(startingWalletBalance) <= 0 || projectedBalance.signum() >= 0) {
    throw new IllegalStateException("high meter value did not create exposure above the starting wallet for ${sessionId}: wallet=${startingWalletBalance} cost=${costAfter} session=${json(updated)}")
  }
  logLine("low-balance continue observed session=${sessionId} status=${updated.status} wallet=${startingWalletBalance} projectedBalance=${projectedBalance} energyBefore=${energyBefore} energyAfter=${updated.energyDeliveredKwh ?: updated.energyKwh} costBefore=${costBefore} costAfter=${costAfter}")

  stopSession(token, sessionId)
  validateStoppedAndReceipt(token, sessionId)
}

def cleanupActiveTestSessionsOnFailure = { String token ->
  def before = activeSession(token)
  def sessions = before.json instanceof List ? before.json : []
  for (def session : sessions) {
    String activeSessionId = String.valueOf(session.id ?: '').trim()
    if (!activeSessionId) continue
    logLine("failure cleanup stopping active session=${activeSessionId} status=${session.status}")
    stopSession(token, activeSessionId)
    validateStoppedAndReceipt(token, activeSessionId, false, false)
  }
  def after = activeSession(token)
  def remaining = after.json instanceof List ? after.json : []
  if (!remaining.isEmpty()) {
    throw new IllegalStateException("failure cleanup left active sessions: ${after.body}")
  }
  true
}

try {
  String token

  if (requireCleanupCredential && (!cleanupAdminToken || cleanupAdminToken.startsWith('%') || cleanupAdminToken == 'SET_IN_TEAMCITY')) {
    throw new IllegalStateException('this test plan requires the protected cleanup_admin_token so generated accounts are removed')
  }
  if (requireCleanupCredential) {
    def cleanupClaims
    try {
      cleanupClaims = decodeJwtPayload(cleanupAdminToken)
    } catch (Exception ignored) {
      throw new IllegalStateException('protected cleanup_admin_token is not a valid JWT')
    }
    if (!(cleanupClaims?.exp instanceof Number)) {
      throw new IllegalStateException('protected cleanup_admin_token is missing a numeric expiry')
    }
    def cleanupRoles = cleanupClaims.roles instanceof Collection
      ? cleanupClaims.roles.collect { String.valueOf(it).toUpperCase(Locale.ROOT).replaceFirst('^ROLE_', '') }
      : [String.valueOf(cleanupClaims.roles ?: '').toUpperCase(Locale.ROOT).replaceFirst('^ROLE_', '')]
    if (!cleanupRoles.contains('SYSTEM_ADMIN')) {
      throw new IllegalStateException('protected cleanup_admin_token must carry the SYSTEM_ADMIN role')
    }
    long cleanupExpiresAt = (cleanupClaims.exp as Number).longValue()
    long minimumRemainingSeconds = props.getProperty('cleanup_admin_min_ttl_seconds', '600') as long
    long remainingSeconds = cleanupExpiresAt - Instant.now().epochSecond
    if (remainingSeconds < minimumRemainingSeconds) {
      throw new IllegalStateException(
        "protected cleanup_admin_token expires too soon: remaining=${remainingSeconds}s required=${minimumRemainingSeconds}s"
      )
    }
    requireStatus(
      atStep('validate cleanup admin authorization') {
        request('GET', '/user/api/v1/admin/users?limit=1&offset=0', null, cleanupAdminToken, requestTimeoutMs)
      },
      [200],
      'validate cleanup admin authorization'
    )
  }

  if (action == 'setup' || action == 'full' || action == 'card-burst' || action == 'idle-fee' || action == 'idle-fee-wallet-reserve' || action == 'subscription-discount' || action == 'low-balance-continue' || action == 'low-balance-check' || action == 'auto-top-up-check') {
    token = registerOrLogin()
    appendGeneratedUser()
    // Newly registered users must accept the current terms before charger
    // discovery. The payment-state probe is idempotent for returning users.
    token = ensureTermsAccepted(token)
    if (action != 'card-burst' &&
        ['full', 'idle-fee', 'idle-fee-wallet-reserve', 'subscription-discount', 'low-balance-continue'].contains(action)) {
      discoverChargers(token)
    }
    token = setupPayment(token)
  } else if (action == 'charging') {
    token = login()
  } else {
    throw new IllegalArgumentException("Unsupported action '${action}'. Use setup, charging, full, card-burst, idle-fee, idle-fee-wallet-reserve, subscription-discount, low-balance-continue, low-balance-check, or auto-top-up-check.")
  }

  if (action == 'low-balance-check') {
    validateLowBalanceDecisionFlow(token)
  } else if (action == 'auto-top-up-check') {
    validateAutoTopUpDecisionFlow(token)
  } else if (action == 'idle-fee-wallet-reserve') {
    discoverChargers(token)
    String warnedSessionId = validateIdleFeeWalletReserveStart(token)
    stopSession(token, warnedSessionId)
    validateStoppedAndReceipt(token, warnedSessionId)
  }

  if (action == 'charging' || action == 'full' || action == 'card-burst' || action == 'idle-fee' || action == 'subscription-discount' || action == 'low-balance-continue') {
    if (action != 'card-burst' && (!chargerId || !connectorId || !locationId)) {
      discoverChargers(token)
    } else {
      logLine("using connector allocated by burst preflight ${chargerId}/${connectorId}")
    }
    requireStatus(atStep('payment state before start') { request('GET', '/payment/api/v1/payment/state', null, token) }, [200], 'payment state before start')
    String sessionId = startSession(token)
    activeSession(token)
    monitorSse(token, sessionId)
    if (validateChargingProgress) {
      validateSessionChargingProgress(token, sessionId)
    }

    if (action == 'idle-fee') {
      validateIdleFeeFlow(token, sessionId)
    } else if (action == 'subscription-discount') {
      validateSubscriptionDiscountFlow(token, sessionId)
    } else if (action == 'low-balance-continue') {
      validateLowBalanceContinueFlow(token, sessionId)
    } else {
      if (holdSeconds > 0) {
        // The load dwell starts only after this exact session has proven
        // metered charging progress. Registration, provider setup, SSE, and
        // readiness polling must not consume the configured steady-load hold.
        logLine("holding confirmed charging session for ${holdSeconds}s before stop")
        sleep(holdSeconds * 1000L)
      }

      stopSession(token, sessionId)
      validateStoppedAndReceipt(token, sessionId)
    }
  }

  cleanupGeneratedTestArtifacts(token)

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
    paymentMethod: sessionPaymentMethod,
    countryCode: journeyCountryCode,
    currency: journeyCurrency,
    paymentCardDeleted: vars.get('paymentCardDeleted'),
    testUserDeleted: vars.get('testUserDeleted'),
    sseSessionEvents: vars.get('sseSessionEvents'),
    sseSnapshotEvents: vars.get('sseSnapshotEvents'),
    sseChargingStateEvents: vars.get('sseChargingStateEvents'),
    completedAt: Instant.now().toString()
  ])), 'UTF-8')
} catch (Throwable t) {
  try {
    String cleanupToken = vars.get('accessToken')
    String cleanupSessionId = vars.get('sessionId')
    boolean testSessionsClean = false
    if (cleanupToken && cleanupUnexpectedSessions) {
      testSessionsClean = cleanupActiveTestSessionsOnFailure(cleanupToken)
    } else if (cleanupToken && cleanupSessionId) {
      logLine("cleanup after failed sample for session=${cleanupSessionId}")
      stopSession(cleanupToken, cleanupSessionId)
      validateStoppedAndReceipt(cleanupToken, cleanupSessionId, false, false)
      testSessionsClean = true
    }
    if (cleanupToken && cleanupTestAccount && testSessionsClean) {
      cleanupGeneratedTestArtifacts(cleanupToken)
    }
  } catch (Throwable cleanupError) {
    log.warn("[electrahub-jmeter][${action}][${userNumber}] cleanup failed: ${cleanupError.message ?: cleanupError.class.name}", cleanupError)
  }
  SampleResult.setSuccessful(false)
  SampleResult.setResponseCode('500')
  SampleResult.setResponseMessage("step=${currentStep}: ${t.message ?: t.class.name}")
  SampleResult.setResponseData((t.message ?: t.toString()), 'UTF-8')
  log.error("[electrahub-jmeter][${action}][${userNumber}] failed", t)
  throw t
}
