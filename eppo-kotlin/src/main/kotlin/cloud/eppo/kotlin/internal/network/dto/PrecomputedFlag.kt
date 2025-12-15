package cloud.eppo.kotlin.internal.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Precomputed flag data from the server.
 *
 * All string fields (except variationType and doLog) are Base64 encoded.
 *
 * @property flagKey MD5 hash of the flag key (optional, may be null)
 * @property allocationKey Base64 encoded allocation key
 * @property variationKey Base64 encoded variation key
 * @property variationType Type of the variation value (BOOLEAN, INTEGER, NUMERIC, STRING, JSON)
 * @property variationValue Base64 encoded variation value
 * @property extraLogging Base64 encoded extra logging metadata (keys and values)
 * @property doLog Whether to log this assignment
 */
@Serializable
internal data class PrecomputedFlag(
    @SerialName("flagKey") val flagKey: String? = null,
    @SerialName("allocationKey") val allocationKey: String? = null,
    @SerialName("variationKey") val variationKey: String? = null,
    @SerialName("variationType") val variationType: String,
    @SerialName("variationValue") val variationValue: String,
    @SerialName("extraLogging") val extraLogging: Map<String, String>? = null,
    @SerialName("doLog") val doLog: Boolean
)
