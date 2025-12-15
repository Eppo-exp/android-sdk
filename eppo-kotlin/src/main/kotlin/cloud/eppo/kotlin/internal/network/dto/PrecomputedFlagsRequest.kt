package cloud.eppo.kotlin.internal.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Request payload for fetching precomputed flags.
 *
 * POST to /assignments endpoint.
 *
 * Format of banditActions: Map<FlagKey, Map<ActionKey, Map<AttributeKey, AttributeValue>>>
 *
 * @property subjectKey Subject identifier (e.g., user ID)
 * @property subjectAttributes Subject attributes for targeting
 * @property banditActions Optional bandit actions for precomputation
 */
@Serializable
internal data class PrecomputedFlagsRequest(
    @SerialName("subject_key") val subjectKey: String,
    @SerialName("subject_attributes") val subjectAttributes: Map<String, JsonElement>,
    @SerialName("bandit_actions") val banditActions: Map<String, Map<String, Map<String, JsonElement>>>? = null
)
