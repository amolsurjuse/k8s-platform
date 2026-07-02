import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

def simulatorUrl = props.get('simulator_url') ?: 'https://ocpp-simulator-dev.electrahub.net'
def chargerId = props.get('charger_id') ?: 'EH-SFO-CHG-001'
def connectorNumber = (props.get('connector_number') ?: '1') as int
def holdSeconds = (props.get('hold_seconds') ?: '15') as int
def targetPowerW = (props.get('target_power_w') ?: '44000') as long
def meterStartWh = (props.get('meter_start_wh') ?: '0') as long
def runId = props.get('run_id') ?: UUID.randomUUID().toString()

def client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
def json = new JsonSlurper()

def postJson = { String url, Object body ->
    def payload = JsonOutput.toJson(body)
    def request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header('Accept', 'application/json')
            .header('Content-Type', 'application/json')
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()
    def response = client.send(request, HttpResponse.BodyHandlers.ofString())
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException("POST ${url} failed HTTP ${response.statusCode()}: ${response.body()}")
    }
    return response.body() == null || response.body().isBlank() ? [:] : json.parseText(response.body())
}

def tapUrl = "${simulatorUrl}/api/v1/chargers/${chargerId}/connectors/${connectorNumber}/tap"
def idempotencyKey = "jmeter-card-present-${runId}-${ctx.getThreadNum()}"
def tap = postJson(tapUrl, [
        terminalId: "JMETER-TERM-${chargerId}",
        amount: 75.00,
        currency: 'USD',
        countryCode: 'US',
        requestStart: true,
        idempotencyKey: idempotencyKey,
        card: [
                pan: '5555555555554444',
                expiryMonth: 12,
                expiryYear: 2030,
                cvv: '123',
                postalCode: '94105'
        ],
        transaction: [
                meterStartWh: meterStartWh,
                targetPowerW: targetPowerW
        ]
])

def idTag = (tap.idTag ?: '').toString()
def transactionId = (tap.transactionId ?: '').toString()
if (!idTag.startsWith('CP:')) {
    throw new IllegalStateException("Expected card-present CP idTag but received '${idTag}'")
}
if (transactionId.isBlank()) {
    throw new IllegalStateException("Card-present tap did not start a transaction")
}
Thread.sleep(Math.max(1, holdSeconds) * 1000L)

def stopUrl = "${simulatorUrl}/api/v1/chargers/${chargerId}/connectors/${connectorNumber}/charging/stop"
def stop = postJson(stopUrl, [
        transactionId: transactionId,
        reason: 'EVDisconnected'
])
if (!'STOPPED'.equalsIgnoreCase((stop.status ?: '').toString())) {
    throw new IllegalStateException("Expected STOPPED response for card-present transaction ${transactionId} but received ${stop}")
}

SampleResult.setResponseData(JsonOutput.prettyPrint(JsonOutput.toJson([
        chargerId: chargerId,
        connectorNumber: connectorNumber,
        idTagPrefix: 'CP',
        transactionId: transactionId,
        stopStatus: stop.status
])), 'UTF-8')
SampleResult.setDataType(org.apache.jmeter.samplers.SampleResult.TEXT)
SampleResult.setSuccessful(true)
SampleResult.setResponseCode('200')
SampleResult.setResponseMessage('Card-present charging flow completed')
