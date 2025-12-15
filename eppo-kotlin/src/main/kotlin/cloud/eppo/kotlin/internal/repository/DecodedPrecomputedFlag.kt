package cloud.eppo.kotlin.internal.repository

import cloud.eppo.ufc.dto.VariationType

/**
 * Decoded (unobfuscated) precomputed flag.
 *
 * All Base64 encoded values have been decoded to their original form.
 *
 * @property allocationKey Decoded allocation key
 * @property variationKey Decoded variation key
 * @property variationType Type of the variation value
 * @property variationValue Decoded and typed variation value
 * @property extraLogging Decoded extra logging metadata
 * @property doLog Whether to log this assignment
 */
internal data class DecodedPrecomputedFlag(
    val allocationKey: String?,
    val variationKey: String?,
    val variationType: VariationType,
    val variationValue: Any,
    val extraLogging: Map<String, String>?,
    val doLog: Boolean
)
