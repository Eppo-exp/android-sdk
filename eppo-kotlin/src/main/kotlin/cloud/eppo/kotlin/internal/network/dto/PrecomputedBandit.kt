package cloud.eppo.kotlin.internal.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Precomputed bandit data from the server.
 *
 * Key string fields (banditKey, action, modelVersion) are Base64 encoded.
 * Attribute maps have Base64 encoded keys and values.
 *
 * @property banditKey Base64 encoded bandit identifier
 * @property action Base64 encoded recommended action
 * @property modelVersion Base64 encoded model version
 * @property actionProbability Probability of this action being selected
 * @property optimalityGap Optimality gap metric
 * @property actionNumericAttributes Numeric attributes (Base64 encoded keys/values)
 * @property actionCategoricalAttributes Categorical attributes (Base64 encoded keys/values)
 */
@Serializable
internal data class PrecomputedBandit(
    @SerialName("banditKey") val banditKey: String,
    @SerialName("action") val action: String,
    @SerialName("modelVersion") val modelVersion: String,
    @SerialName("actionProbability") val actionProbability: Double,
    @SerialName("optimalityGap") val optimalityGap: Double,
    @SerialName("actionNumericAttributes") val actionNumericAttributes: Map<String, String> = emptyMap(),
    @SerialName("actionCategoricalAttributes") val actionCategoricalAttributes: Map<String, String> = emptyMap()
)
