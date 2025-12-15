package cloud.eppo.kotlin.internal.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from the precomputed flags API.
 *
 * Contains precomputed flag assignments for the requested subject.
 *
 * @property format Response format (should be "PRECOMPUTED")
 * @property obfuscated Whether values are obfuscated (should be true)
 * @property createdAt ISO 8601 timestamp when response was created
 * @property environment Environment information
 * @property salt Salt used for MD5 hashing flag keys
 * @property flags Map of hashed flag keys to precomputed flag data
 * @property bandits Map of hashed flag keys to precomputed bandit data
 */
@Serializable
internal data class PrecomputedFlagsResponse(
    @SerialName("format") val format: String,
    @SerialName("obfuscated") val obfuscated: Boolean,
    @SerialName("createdAt") val createdAt: String,
    @SerialName("environment") val environment: Environment? = null,
    @SerialName("salt") val salt: String,
    @SerialName("flags") val flags: Map<String, PrecomputedFlag>,
    @SerialName("bandits") val bandits: Map<String, PrecomputedBandit>? = null
)
