package cloud.eppo.kotlin.internal.network

import cloud.eppo.kotlin.EppoPrecomputedConfig
import cloud.eppo.kotlin.internal.network.dto.PrecomputedFlagsResponse
import cloud.eppo.kotlin.internal.util.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Response
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * HTTP client for fetching precomputed flags from the Eppo API.
 *
 * @property httpClient OkHttp client instance
 * @property requestFactory Factory for building requests
 * @property json JSON serializer for parsing responses
 */
internal class PrecomputedRequestor(
    private val httpClient: OkHttpClient,
    private val requestFactory: RequestFactory,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(PrecomputedRequestor::class.java)

    /**
     * Fetch precomputed flags for the given subject.
     *
     * This is a suspending function that executes on the IO dispatcher.
     *
     * @param config Client configuration
     * @param subject Subject to fetch flags for
     * @return Result containing the response or an error
     */
    suspend fun fetchPrecomputedFlags(
        config: EppoPrecomputedConfig,
        subject: EppoPrecomputedConfig.Subject
    ): Result<PrecomputedFlagsResponse> = withContext(Dispatchers.IO) {
        runCatching {
            logger.debug("Fetching precomputed flags for subject: ${subject.subjectKey}")

            val request = requestFactory.buildFetchRequest(config, subject)

            val response: Response = try {
                httpClient.newCall(request).await()
            } catch (e: IOException) {
                logger.error("Network error fetching precomputed flags", e)
                throw e
            }

            if (!response.isSuccessful) {
                val errorMessage = "HTTP ${response.code}: ${response.message}"
                logger.error("Failed to fetch precomputed flags: $errorMessage")
                throw HttpException(response.code, response.message)
            }

            val body = response.body?.string()
                ?: throw IllegalStateException("Empty response body")

            response.close()

            val parsedResponse = try {
                json.decodeFromString<PrecomputedFlagsResponse>(body)
            } catch (e: Exception) {
                logger.error("Failed to parse precomputed flags response", e)
                throw e
            }

            logger.debug(
                "Successfully fetched ${parsedResponse.flags.size} flags " +
                        "and ${parsedResponse.bandits?.size ?: 0} bandits"
            )

            parsedResponse
        }
    }
}

/**
 * Exception thrown when HTTP request fails.
 *
 * @property code HTTP status code
 * @property message HTTP status message
 */
internal class HttpException(
    val code: Int,
    message: String
) : IOException("HTTP $code: $message")
