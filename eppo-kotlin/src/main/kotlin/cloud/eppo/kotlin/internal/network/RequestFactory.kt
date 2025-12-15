package cloud.eppo.kotlin.internal.network

import cloud.eppo.kotlin.EppoPrecomputedConfig
import cloud.eppo.kotlin.internal.network.dto.PrecomputedFlagsRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Factory for building HTTP requests to the precomputed flags API.
 *
 * @property json JSON serializer instance
 */
internal class RequestFactory(
    private val json: Json
) {
    companion object {
        private const val HEADER_API_KEY = "x-eppo-token"
        private const val CONTENT_TYPE = "application/json; charset=utf-8"
        private const val ASSIGNMENTS_PATH = "/api/v1/precomputed-flags"
    }

    /**
     * Build a request for fetching precomputed flags.
     *
     * @param config Client configuration
     * @param subject Subject information (may differ from config if updateSubject was called)
     * @return OkHttp Request object
     */
    fun buildFetchRequest(
        config: EppoPrecomputedConfig,
        subject: EppoPrecomputedConfig.Subject
    ): Request {
        val url = "${config.baseUrl}$ASSIGNMENTS_PATH"

        val requestBody = PrecomputedFlagsRequest(
            subjectKey = subject.subjectKey,
            subjectAttributes = subject.subjectAttributes.toJsonElementMap(),
            banditActions = config.banditActions?.mapValues { (_, actionMap) ->
                actionMap.mapValues { (_, attributeMap) ->
                    attributeMap.toJsonElementMap()
                }
            }
        )

        val bodyJson = json.encodeToString(
            PrecomputedFlagsRequest.serializer(),
            requestBody
        )

        return Request.Builder()
            .url(url)
            .addHeader(HEADER_API_KEY, config.apiKey)
            .addHeader("Content-Type", CONTENT_TYPE)
            .post(bodyJson.toRequestBody(CONTENT_TYPE.toMediaType()))
            .build()
    }

    /**
     * Convert a Map<String, Any> to Map<String, JsonElement>.
     *
     * Handles common primitive types (String, Number, Boolean, null).
     */
    private fun Map<String, Any>.toJsonElementMap(): Map<String, JsonElement> {
        return mapValues { (_, value) ->
            value.toJsonElement()
        }
    }

    /**
     * Convert Any value to JsonElement.
     *
     * @throws IllegalArgumentException if value type is not supported
     */
    private fun Any?.toJsonElement(): JsonElement {
        return when (this) {
            null -> JsonPrimitive(null as String?)
            is String -> JsonPrimitive(this)
            is Number -> JsonPrimitive(this)
            is Boolean -> JsonPrimitive(this)
            is Map<*, *> -> buildJsonObject {
                @Suppress("UNCHECKED_CAST")
                (this@toJsonElement as Map<String, Any>).forEach { (key, value) ->
                    put(key, value.toJsonElement())
                }
            }
            else -> throw IllegalArgumentException(
                "Unsupported attribute type: ${this::class.simpleName}"
            )
        }
    }
}
