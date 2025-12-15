package cloud.eppo.kotlin.internal.network

import cloud.eppo.kotlin.EppoPrecomputedConfig
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class PrecomputedRequestorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var requestor: PrecomputedRequestor
    private lateinit var httpClient: OkHttpClient
    private lateinit var json: Json
    private lateinit var requestFactory: RequestFactory

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        json = Json { ignoreUnknownKeys = true }
        requestFactory = RequestFactory(json)
        httpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        requestor = PrecomputedRequestor(httpClient, requestFactory, json)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `fetchPrecomputedFlags returns success on valid response`() = runTest {
        val responseBody = """
            {
                "format": "PRECOMPUTED",
                "obfuscated": true,
                "createdAt": "2025-01-28T12:00:00Z",
                "salt": "abc123",
                "flags": {
                    "hash1": {
                        "variationType": "BOOLEAN",
                        "variationValue": "dHJ1ZQ==",
                        "doLog": true
                    }
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

        val config = createTestConfig(baseUrl = mockWebServer.url("/").toString())
        val subject = EppoPrecomputedConfig.Subject("user-123")

        val result = requestor.fetchPrecomputedFlags(config, subject)

        assertThat(result.isSuccess).isTrue()
        val response = result.getOrThrow()
        assertThat(response.format).isEqualTo("PRECOMPUTED")
        assertThat(response.obfuscated).isTrue()
        assertThat(response.salt).isEqualTo("abc123")
        assertThat(response.flags).hasSize(1)
    }

    @Test
    fun `fetchPrecomputedFlags sends correct request`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody(createValidResponse()).setResponseCode(200))

        val config = createTestConfig(
            baseUrl = mockWebServer.url("/").toString(),
            apiKey = "test-api-key-123"
        )
        val subject = EppoPrecomputedConfig.Subject(
            "user-456",
            mapOf("country" to "UK")
        )

        requestor.fetchPrecomputedFlags(config, subject)

        val recordedRequest = mockWebServer.takeRequest()
        assertThat(recordedRequest.method).isEqualTo("POST")
        assertThat(recordedRequest.path).isEqualTo("/api/v1/precomputed-flags")
        assertThat(recordedRequest.getHeader("x-eppo-token")).isEqualTo("test-api-key-123")
        assertThat(recordedRequest.getHeader("Content-Type")).contains("application/json")

        val bodyJson = json.parseToJsonElement(recordedRequest.body.readUtf8()).jsonObject
        assertThat(bodyJson["subject_key"]?.jsonPrimitive?.content).isEqualTo("user-456")
    }

    @Test
    fun `fetchPrecomputedFlags returns failure on network error`() = runTest {
        mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val config = createTestConfig(baseUrl = mockWebServer.url("/").toString())
        val subject = EppoPrecomputedConfig.Subject("user-123")

        val result = requestor.fetchPrecomputedFlags(config, subject)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IOException::class.java)
    }

    @Test
    fun `fetchPrecomputedFlags returns failure on HTTP 404`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))

        val config = createTestConfig(baseUrl = mockWebServer.url("/").toString())
        val subject = EppoPrecomputedConfig.Subject("user-123")

        val result = requestor.fetchPrecomputedFlags(config, subject)

        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull() as? HttpException
        assertThat(exception).isNotNull()
        assertThat(exception?.code).isEqualTo(404)
    }

    @Test
    fun `fetchPrecomputedFlags returns failure on HTTP 500`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        val config = createTestConfig(baseUrl = mockWebServer.url("/").toString())
        val subject = EppoPrecomputedConfig.Subject("user-123")

        val result = requestor.fetchPrecomputedFlags(config, subject)

        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull() as? HttpException
        assertThat(exception?.code).isEqualTo(500)
    }

    @Test
    fun `fetchPrecomputedFlags returns failure on invalid JSON`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{ invalid json }")
        )

        val config = createTestConfig(baseUrl = mockWebServer.url("/").toString())
        val subject = EppoPrecomputedConfig.Subject("user-123")

        val result = requestor.fetchPrecomputedFlags(config, subject)

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `fetchPrecomputedFlags returns failure on empty response body`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val config = createTestConfig(baseUrl = mockWebServer.url("/").toString())
        val subject = EppoPrecomputedConfig.Subject("user-123")

        val result = requestor.fetchPrecomputedFlags(config, subject)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `fetchPrecomputedFlags parses bandits correctly`() = runTest {
        val responseBody = """
            {
                "format": "PRECOMPUTED",
                "obfuscated": true,
                "createdAt": "2025-01-28T12:00:00Z",
                "salt": "abc123",
                "flags": {},
                "bandits": {
                    "bandit-hash-1": {
                        "banditKey": "YmFuZGl0LWtleQ==",
                        "action": "YWN0aW9uLTE=",
                        "modelVersion": "djEuMA==",
                        "actionProbability": 0.75,
                        "optimalityGap": 0.1
                    }
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

        val config = createTestConfig(baseUrl = mockWebServer.url("/").toString())
        val subject = EppoPrecomputedConfig.Subject("user-123")

        val result = requestor.fetchPrecomputedFlags(config, subject)

        assertThat(result.isSuccess).isTrue()
        val response = result.getOrThrow()
        assertThat(response.bandits).isNotNull()
        assertThat(response.bandits).hasSize(1)

        val bandit = response.bandits?.get("bandit-hash-1")
        assertThat(bandit?.banditKey).isEqualTo("YmFuZGl0LWtleQ==")
        assertThat(bandit?.action).isEqualTo("YWN0aW9uLTE=")
        assertThat(bandit?.actionProbability).isEqualTo(0.75)
    }

    @Test
    fun `fetchPrecomputedFlags includes bandit actions in request when configured`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody(createValidResponse()).setResponseCode(200))

        val banditActions = mapOf(
            "product-rec" to mapOf(
                "action-1" to mapOf("price" to 10.0),
                "action-2" to mapOf("price" to 20.0)
            )
        )

        val config = createTestConfig(
            baseUrl = mockWebServer.url("/").toString(),
            banditActions = banditActions
        )
        val subject = EppoPrecomputedConfig.Subject("user-123")

        requestor.fetchPrecomputedFlags(config, subject)

        val recordedRequest = mockWebServer.takeRequest()
        val bodyJson = json.parseToJsonElement(recordedRequest.body.readUtf8()).jsonObject

        val requestBanditActions = bodyJson["bandit_actions"]?.jsonObject
        assertThat(requestBanditActions).isNotNull()
        assertThat(requestBanditActions?.containsKey("product-rec")).isTrue()
    }

    @Test
    fun `fetchPrecomputedFlags handles response with environment`() = runTest {
        val responseBody = """
            {
                "format": "PRECOMPUTED",
                "obfuscated": true,
                "createdAt": "2025-01-28T12:00:00Z",
                "environment": {
                    "name": "production"
                },
                "salt": "abc123",
                "flags": {}
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

        val config = createTestConfig(baseUrl = mockWebServer.url("/").toString())
        val subject = EppoPrecomputedConfig.Subject("user-123")

        val result = requestor.fetchPrecomputedFlags(config, subject)

        assertThat(result.isSuccess).isTrue()
        val response = result.getOrThrow()
        assertThat(response.environment).isNotNull()
        assertThat(response.environment?.name).isEqualTo("production")
    }

    // Helper functions

    private fun createTestConfig(
        apiKey: String = "test-key",
        baseUrl: String = "https://test.api.com",
        banditActions: Map<String, Map<String, Map<String, Any>>>? = null
    ): EppoPrecomputedConfig {
        return EppoPrecomputedConfig.Builder()
            .apiKey(apiKey)
            .subject("default-subject")
            .baseUrl(baseUrl)
            .apply { banditActions?.let { banditActions(it) } }
            .build()
    }

    private fun createValidResponse(): String {
        return """
            {
                "format": "PRECOMPUTED",
                "obfuscated": true,
                "createdAt": "2025-01-28T12:00:00Z",
                "salt": "test-salt",
                "flags": {}
            }
        """.trimIndent()
    }
}
