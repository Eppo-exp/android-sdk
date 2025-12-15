package cloud.eppo.kotlin.internal.repository

/**
 * Decoded (unobfuscated) precomputed bandit.
 *
 * All Base64 encoded values have been decoded to their original form.
 *
 * @property banditKey Decoded bandit identifier
 * @property action Decoded recommended action
 * @property modelVersion Decoded model version
 * @property actionProbability Probability of this action being selected
 * @property optimalityGap Optimality gap metric
 * @property actionNumericAttributes Decoded numeric attributes
 * @property actionCategoricalAttributes Decoded categorical attributes
 */
internal data class DecodedPrecomputedBandit(
    val banditKey: String,
    val action: String,
    val modelVersion: String,
    val actionProbability: Double,
    val optimalityGap: Double,
    val actionNumericAttributes: Map<String, Double>,
    val actionCategoricalAttributes: Map<String, String>
)
