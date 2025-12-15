package cloud.eppo.kotlin.internal.repository

import androidx.datastore.core.DataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Repository for managing precomputed flags and bandits.
 *
 * Provides thread-safe access to flags with:
 * - In-memory cache via StateFlow (fast, lock-free reads)
 * - Persistent storage via DataStore (survives app restarts)
 * - Atomic updates (readers see consistent state)
 *
 * Thread Safety: All methods are thread-safe. Multiple readers can access
 * flags concurrently. Updates are serialized via Mutex.
 *
 * @property dataStore DataStore for persistent storage
 * @property scope CoroutineScope for async persistence
 */
internal class FlagsRepository(
    private val dataStore: DataStore<FlagsState?>,
    private val scope: CoroutineScope
) {
    private val logger = LoggerFactory.getLogger(FlagsRepository::class.java)

    // In-memory state (lock-free reads)
    private val _stateFlow = MutableStateFlow<FlagsState?>(null)
    val stateFlow: StateFlow<FlagsState?> = _stateFlow.asStateFlow()

    // Mutex for serializing write operations
    private val mutex = Mutex()

    /**
     * Load persisted state from DataStore.
     *
     * Should be called during initialization. If persistence is disabled
     * or data doesn't exist, this is a no-op.
     */
    suspend fun loadPersistedState() {
        try {
            val persisted = dataStore.data.first()
            if (persisted != null) {
                _stateFlow.value = persisted
                logger.debug(
                    "Loaded ${persisted.flags.size} flags and " +
                            "${persisted.bandits.size} bandits from persistent storage"
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to load persisted state, starting fresh", e)
            // Continue with null state - not a fatal error
        }
    }

    /**
     * Update flags state atomically.
     *
     * This method:
     * 1. Acquires mutex lock (serializes updates)
     * 2. Updates StateFlow atomically (readers see old or new, never partial)
     * 3. Persists to DataStore asynchronously (non-blocking)
     *
     * If persistence fails, in-memory state remains valid.
     *
     * @param flags Map of hashed keys to decoded flags
     * @param bandits Map of hashed keys to decoded bandits
     * @param salt Salt for MD5 hashing
     * @param environment Environment name
     * @param createdAt Timestamp
     * @param format Response format
     */
    suspend fun updateFlags(
        flags: Map<String, DecodedPrecomputedFlag>,
        bandits: Map<String, DecodedPrecomputedBandit>,
        salt: String,
        environment: String?,
        createdAt: Instant,
        format: String
    ) = mutex.withLock {
        val newState = FlagsState(
            flags = flags,
            bandits = bandits,
            salt = salt,
            environment = environment,
            createdAt = createdAt,
            format = format
        )

        // Atomic swap - readers immediately see new state
        _stateFlow.value = newState

        logger.debug("Updated repository with ${flags.size} flags and ${bandits.size} bandits")

        // Persist asynchronously (don't block the update)
        scope.launch(Dispatchers.IO) {
            try {
                dataStore.updateData { newState }
                logger.debug("Successfully persisted flags to DataStore")
            } catch (e: Exception) {
                logger.error("Failed to persist flags, in-memory state remains valid", e)
                // Don't throw - in-memory state is still valid
            }
        }
    }

    /**
     * Get a flag by its hashed key.
     *
     * Thread-safe, lock-free read.
     *
     * @param hashedKey MD5 hash of the flag key
     * @return Decoded flag or null if not found
     */
    fun getFlag(hashedKey: String): DecodedPrecomputedFlag? {
        return _stateFlow.value?.getFlag(hashedKey)
    }

    /**
     * Get a bandit by its hashed key.
     *
     * Thread-safe, lock-free read.
     *
     * @param hashedKey MD5 hash of the flag key
     * @return Decoded bandit or null if not found
     */
    fun getBandit(hashedKey: String): DecodedPrecomputedBandit? {
        return _stateFlow.value?.getBandit(hashedKey)
    }

    /**
     * Get the salt used for MD5 hashing.
     *
     * @return Salt string or null if not initialized
     */
    fun getSalt(): String? {
        return _stateFlow.value?.salt
    }

    /**
     * Check if repository has been initialized with data.
     *
     * @return true if flags have been loaded
     */
    fun isInitialized(): Boolean {
        return _stateFlow.value != null
    }

    /**
     * Clear all flags and bandits.
     *
     * This also clears the persisted state.
     */
    suspend fun clear() = mutex.withLock {
        _stateFlow.value = null

        scope.launch(Dispatchers.IO) {
            try {
                dataStore.updateData { null }
                logger.debug("Cleared persisted flags")
            } catch (e: Exception) {
                logger.error("Failed to clear persisted flags", e)
            }
        }
    }
}
