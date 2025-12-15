package cloud.eppo.kotlin

typealias BanditFlagKey = String
typealias BanditActionKey = String
typealias BanditActionAttributes = Map<BanditActionKey, Map<String, Any>>

/**
 * Configuration for EppoPrecomputedClient.
 *
 * Use [Builder] to construct instances.
 *
 * Example:
 * ```
 * val config = EppoPrecomputedConfig.Builder()
 *     .apiKey("your-api-key")
 *     .subject("user-123", mapOf("plan" to "premium"))
 *     .gracefulMode(true)
 *     .build()
 * ```
 */
data class EppoPrecomputedConfig internal constructor(
    val apiKey: String,
    val subject: Subject,
    val baseUrl: String,
    val requestTimeoutMs: Long,
    val enablePolling: Boolean,
    val pollingIntervalMs: Long,
    val enablePersistence: Boolean,
    val enableAssignmentCache: Boolean,
    val assignmentCacheMaxSize: Int,
    val banditActions: Map<BanditFlagKey, BanditActionAttributes>? = null,
    val gracefulMode: Boolean
) {
    /**
     * Subject information for flag evaluation.
     *
     * @property subjectKey Unique identifier for the subject (e.g., user ID)
     * @property subjectAttributes Additional attributes for targeting
     */
    data class Subject(
        val subjectKey: String,
        val subjectAttributes: Map<String, Any> = emptyMap()
    )

    /**
     * Builder for EppoPrecomputedConfig.
     *
     * Graceful Mode Behavior:
     * - When enabled (default): get*Value() methods never throw exceptions,
     *   always return defaultValue on error. Errors logged internally.
     * - When disabled: Exceptions can be thrown from get*Value() methods for
     *   critical errors (e.g., TypeMismatchException, InvalidFlagException).
     */
    class Builder {
        private var apiKey: String? = null
        private var subject: Subject? = null
        private var baseUrl: String = "https://fs-edge-assignment.eppo.cloud"
        private var requestTimeoutMs: Long = 5_000
        private var enablePolling: Boolean = false
        private var pollingIntervalMs: Long = 30_000
        private var enablePersistence: Boolean = true
        private var enableAssignmentCache: Boolean = true
        private var assignmentCacheMaxSize: Int = 1000
        private var banditActions: Map<BanditFlagKey, BanditActionAttributes>? = null
        private var gracefulMode: Boolean = true

        /**
         * Set the API key for authentication.
         *
         * @param key Eppo API key (required)
         * @return This builder
         */
        fun apiKey(key: String) = apply {
            this.apiKey = key
        }

        /**
         * Set the subject for flag evaluation.
         *
         * @param key Subject identifier (required)
         * @param attributes Subject attributes for targeting (optional)
         * @return This builder
         */
        fun subject(key: String, attributes: Map<String, Any> = emptyMap()) = apply {
            this.subject = Subject(key, attributes)
        }

        /**
         * Set the base URL for the Eppo API.
         *
         * @param url Base URL (default: https://fs-edge-assignment.eppo.cloud)
         * @return This builder
         */
        fun baseUrl(url: String) = apply {
            this.baseUrl = url
        }

        /**
         * Set the request timeout.
         *
         * @param ms Timeout in milliseconds (default: 5000)
         * @return This builder
         */
        fun requestTimeout(ms: Long) = apply {
            this.requestTimeoutMs = ms
        }

        /**
         * Enable or disable automatic polling.
         *
         * @param enable Whether to enable polling (default: false)
         * @param intervalMs Polling interval in milliseconds (default: 30000)
         * @return This builder
         */
        fun enablePolling(enable: Boolean, intervalMs: Long = 30_000) = apply {
            this.enablePolling = enable
            this.pollingIntervalMs = intervalMs
        }

        /**
         * Enable or disable persistence to disk.
         *
         * @param enable Whether to enable persistence (default: true)
         * @return This builder
         */
        fun enablePersistence(enable: Boolean) = apply {
            this.enablePersistence = enable
        }

        /**
         * Enable or disable assignment caching.
         *
         * @param enable Whether to enable caching (default: true)
         * @param maxSize Maximum cache size (default: 1000)
         * @return This builder
         */
        fun enableAssignmentCache(enable: Boolean, maxSize: Int = 1000) = apply {
            this.enableAssignmentCache = enable
            this.assignmentCacheMaxSize = maxSize
        }

        /**
         * Set bandit actions for precomputation.
         *
         * Format: Map<FlagKey, Map<ActionKey, Map<AttributeKey, AttributeValue>>>
         *
         * Example:
         * ```
         * mapOf(
         *     "product-recommendation" to mapOf(
         *         "action-1" to mapOf("price" to 9.99, "color" to "red"),
         *         "action-2" to mapOf("price" to 14.99, "color" to "blue")
         *     )
         * )
         * ```
         *
         * @param actions Bandit actions configuration
         * @return This builder
         * @throws IllegalArgumentException if actions are invalid
         */
        fun banditActions(actions: Map<String, Map<String, Map<String, Any>>>) = apply {
            require(actions.isNotEmpty()) { "Bandit actions cannot be empty" }
            actions.forEach { (flagKey, actionMap) ->
                require(flagKey.isNotBlank()) { "Bandit flag key cannot be blank" }
                require(actionMap.isNotEmpty()) {
                    "Bandit flag '$flagKey' must have at least one action"
                }
                actionMap.forEach { (actionKey, _) ->
                    require(actionKey.isNotBlank()) {
                        "Action key cannot be blank for flag '$flagKey'"
                    }
                }
            }
            this.banditActions = actions
        }

        /**
         * Enable or disable graceful error handling.
         *
         * When enabled (default):
         * - get*Value() methods never throw exceptions
         * - Always return defaultValue on error
         * - Errors logged internally
         *
         * When disabled:
         * - Exceptions can be thrown for critical errors
         * - More explicit error handling required
         *
         * @param enabled Whether to enable graceful mode (default: true)
         * @return This builder
         */
        fun gracefulMode(enabled: Boolean) = apply {
            this.gracefulMode = enabled
        }

        /**
         * Build the configuration.
         *
         * @return EppoPrecomputedConfig instance
         * @throws IllegalStateException if required fields are missing
         */
        fun build(): EppoPrecomputedConfig {
            require(apiKey != null) { "API key is required" }
            require(subject != null) { "Subject is required" }
            require(requestTimeoutMs > 0) { "Request timeout must be positive" }
            require(pollingIntervalMs > 0) { "Polling interval must be positive" }
            require(assignmentCacheMaxSize > 0) { "Assignment cache max size must be positive" }

            return EppoPrecomputedConfig(
                apiKey = apiKey!!,
                subject = subject!!,
                baseUrl = baseUrl,
                requestTimeoutMs = requestTimeoutMs,
                enablePolling = enablePolling,
                pollingIntervalMs = pollingIntervalMs,
                enablePersistence = enablePersistence,
                enableAssignmentCache = enableAssignmentCache,
                assignmentCacheMaxSize = assignmentCacheMaxSize,
                banditActions = banditActions,
                gracefulMode = gracefulMode
            )
        }
    }
}
