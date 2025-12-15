package cloud.eppo.kotlin.internal.repository

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Immutable state container for precomputed flags and bandits.
 *
 * This represents a complete snapshot of flags for a specific subject.
 * All flags are stored by their MD5-hashed key.
 *
 * @property flags Map of hashed flag keys to decoded flags
 * @property bandits Map of hashed flag keys to decoded bandits
 * @property salt Salt used for MD5 hashing flag keys
 * @property environment Environment name (e.g., "production")
 * @property createdAt Timestamp when this state was created
 * @property format Response format (should be "PRECOMPUTED")
 */
internal data class FlagsState(
    val flags: Map<String, DecodedPrecomputedFlag>,
    val bandits: Map<String, DecodedPrecomputedBandit>,
    val salt: String,
    val environment: String?,
    val createdAt: Instant,
    val format: String
) {
    /**
     * Get a flag by its hashed key.
     *
     * @param hashedKey MD5 hash of the flag key
     * @return Decoded flag or null if not found
     */
    fun getFlag(hashedKey: String): DecodedPrecomputedFlag? {
        return flags[hashedKey]
    }

    /**
     * Get a bandit by its hashed key.
     *
     * @param hashedKey MD5 hash of the flag key
     * @return Decoded bandit or null if not found
     */
    fun getBandit(hashedKey: String): DecodedPrecomputedBandit? {
        return bandits[hashedKey]
    }

    /**
     * Check if this state contains any flags.
     *
     * @return true if at least one flag exists
     */
    fun hasFlags(): Boolean {
        return flags.isNotEmpty()
    }
}
