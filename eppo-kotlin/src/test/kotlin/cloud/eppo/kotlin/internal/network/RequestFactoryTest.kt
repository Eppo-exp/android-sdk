package cloud.eppo.kotlin.internal.network

import cloud.eppo.kotlin.EppoPrecomputedConfig
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Before
import org.junit.Test

class RequestFactoryTest {

    private lateinit var requestFactory: RequestFactory
    private lateinit var json: Json

    @Before
    fun setup() {
        json = Json { ignoreUnknownKeys = true }
        requestFactory = RequestFactory(json)
    }

    @Test
    fun `buildFetchRequest creates correct URL`() {
        val config = createTestConfig()
        val subject = EppoPrecomputedConfig.Subject("user-123")

        val request = requestFactory.buildFetchRequest(config, subject)

        assertThat(request.url.toString())
            .isEqualTo("https://fs-edge-assignment.eppo.cloud/api/v1/precomputed-flags")
    }

    @Test
    fun `buildFetchRequest includes API key header`() {
        val config = createTestConfig(apiKey = "test-api-key")
        val subject = EppoPrecomputedConfig.Subject("user-123")

        val request = requestFactory.buildFetchRequest(config, subject)

        assertThat(request.header("x-eppo-token")).isEqualTo("test-api-key")
    }

    @Test
    fun `buildFetchRequest includes content type header`() {
        val config = createTestConfig()
        val subject = EppoPrecomputedConfig.Subject("user-123")

        val request = requestFactory.buildFetchRequest(config, subject)

        assertThat(request.header("Content-Type"))
            .isEqualTo("application/json; charset=utf-8")
    }

    @Test
    fun `buildFetchRequest uses POST method`() {
        val config = createTestConfig()
        val subject = EppoPrecomputedConfig.Subject("user-123")

        val request = requestFactory.buildFetchRequest(config, subject)

        assertThat(request.method).isEqualTo("POST")
    }

    @Test
    fun `buildFetchRequest includes subject key in body`() {
        val config = createTestConfig()
        val subject = EppoPrecomputedConfig.Subject("user-456")

        val request = requestFactory.buildFetchRequest(config, subject)
        val bodyJson = parseRequestBody(request)

        assertThat(bodyJson["subject_key"]?.jsonPrimitive?.content).isEqualTo("user-456")
    }

    @Test
    fun `buildFetchRequest includes subject attributes in body`() {
        val config = createTestConfig()
        val subject = EppoPrecomputedConfig.Subject(
            "user-123",
            mapOf(
                "country" to "US",
                "plan" to "premium",
                "age" to 25
            )
        )

        val request = requestFactory.buildFetchRequest(config, subject)
        val bodyJson = parseRequestBody(request)

        val attributes = bodyJson["subject_attributes"]?.jsonObject
        assertThat(attributes).isNotNull()
        assertThat(attributes?.get("country")?.jsonPrimitive?.content).isEqualTo("US")
        assertThat(attributes?.get("plan")?.jsonPrimitive?.content).isEqualTo("premium")
        assertThat(attributes?.get("age")?.jsonPrimitive?.content).isEqualTo("25")
    }

    @Test
    fun `buildFetchRequest includes bandit actions when configured`() {
        val banditActions = mapOf(
            "product-recommendation" to mapOf(
                "action-1" to mapOf("price" to 9.99, "color" to "red"),
                "action-2" to mapOf("price" to 14.99, "color" to "blue")
            )
        )

        val config = createTestConfig(banditActions = banditActions)
        val subject = EppoPrecomputedConfig.Subject("user-123")

        val request = requestFactory.buildFetchRequest(config, subject)
        val bodyJson = parseRequestBody(request)

        val banditActionsJson = bodyJson["bandit_actions"]?.jsonObject
        assertThat(banditActionsJson).isNotNull()

        val productRec = banditActionsJson?.get("product-recommendation")?.jsonObject
        assertThat(productRec).isNotNull()

        val action1 = productRec?.get("action-1")?.jsonObject
        assertThat(action1?.get("price")?.jsonPrimitive?.content).isEqualTo("9.99")
        assertThat(action1?.get("color")?.jsonPrimitive?.content).isEqualTo("red")
    }

    @Test
    fun `buildFetchRequest excludes bandit actions when not configured`() {
        val config = createTestConfig(banditActions = null)
        val subject = EppoPrecomputedConfig.Subject("user-123")

        val request = requestFactory.buildFetchRequest(config, subject)
        val bodyJson = parseRequestBody(request)

        assertThat(bodyJson.containsKey("bandit_actions")).isFalse()
    }

    @Test
    fun `buildFetchRequest uses custom base URL when provided`() {
        val config = createTestConfig(baseUrl = "https://custom.api.com")
        val subject = EppoPrecomputedConfig.Subject("user-123")

        val request = requestFactory.buildFetchRequest(config, subject)

        assertThat(request.url.toString())
            .startsWith("https://custom.api.com/api/v1/precomputed-flags")
    }

    @Test
    fun `buildFetchRequest handles empty subject attributes`() {
        val config = createTestConfig()
        val subject = EppoPrecomputedConfig.Subject("user-123", emptyMap())

        val request = requestFactory.buildFetchRequest(config, subject)
        val bodyJson = parseRequestBody(request)

        val attributes = bodyJson["subject_attributes"]?.jsonObject
        assertThat(attributes).isNotNull()
        assertThat(attributes?.size).isEqualTo(0)
    }

    // Helper functions

    private fun createTestConfig(
        apiKey: String = "test-key",
        baseUrl: String = "https://fs-edge-assignment.eppo.cloud",
        banditActions: Map<String, Map<String, Map<String, Any>>>? = null
    ): EppoPrecomputedConfig {
        return EppoPrecomputedConfig.Builder()
            .apiKey(apiKey)
            .subject("default-subject")
            .baseUrl(baseUrl)
            .apply { banditActions?.let { banditActions(it) } }
            .build()
    }

    private fun parseRequestBody(request: okhttp3.Request): JsonObject {
        val buffer = okio.Buffer()
        request.body?.writeTo(buffer)
        val bodyString = buffer.readUtf8()
        return json.parseToJsonElement(bodyString).jsonObject
    }
}
