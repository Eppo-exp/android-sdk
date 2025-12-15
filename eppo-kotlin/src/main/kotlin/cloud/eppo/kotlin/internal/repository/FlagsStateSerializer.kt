package cloud.eppo.kotlin.internal.repository

import androidx.datastore.core.Serializer
import cloud.eppo.ufc.dto.VariationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

/**
 * Serializer for persisting FlagsState to DataStore.
 *
 * Converts FlagsState to/from JSON for disk storage.
 */
internal object FlagsStateSerializer : Serializer<FlagsState?> {
    private val logger = LoggerFactory.getLogger(FlagsStateSerializer::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override val defaultValue: FlagsState? = null

    override suspend fun readFrom(input: InputStream): FlagsState? {
        return withContext(Dispatchers.IO) {
            try {
                val bytes = input.readBytes()
                if (bytes.isEmpty()) {
                    return@withContext null
                }

                val persistable = json.decodeFromString<PersistableFlagsState>(
                    bytes.toString(Charsets.UTF_8)
                )

                persistable.toDomain()
            } catch (e: Exception) {
                logger.error("Failed to deserialize FlagsState", e)
                null
            }
        }
    }

    override suspend fun writeTo(t: FlagsState?, output: OutputStream) {
        withContext(Dispatchers.IO) {
            if (t == null) {
                return@withContext
            }

            try {
                val persistable = PersistableFlagsState.fromDomain(t)
                val jsonString = json.encodeToString(
                    PersistableFlagsState.serializer(),
                    persistable
                )
                output.write(jsonString.toByteArray(Charsets.UTF_8))
            } catch (e: Exception) {
                logger.error("Failed to serialize FlagsState", e)
                throw e
            }
        }
    }
}

/**
 * Serializable version of FlagsState for DataStore persistence.
 *
 * Uses primitive types that kotlinx.serialization can handle.
 */
@Serializable
private data class PersistableFlagsState(
    val flags: Map<String, PersistableFlag>,
    val bandits: Map<String, PersistableBandit>,
    val salt: String,
    val environment: String?,
    val createdAtMillis: Long,
    val format: String
) {
    fun toDomain(): FlagsState {
        return FlagsState(
            flags = flags.mapValues { it.value.toDomain() },
            bandits = bandits.mapValues { it.value.toDomain() },
            salt = salt,
            environment = environment,
            createdAt = Instant.ofEpochMilli(createdAtMillis),
            format = format
        )
    }

    companion object {
        fun fromDomain(state: FlagsState): PersistableFlagsState {
            return PersistableFlagsState(
                flags = state.flags.mapValues { PersistableFlag.fromDomain(it.value) },
                bandits = state.bandits.mapValues { PersistableBandit.fromDomain(it.value) },
                salt = state.salt,
                environment = state.environment,
                createdAtMillis = state.createdAt.toEpochMilli(),
                format = state.format
            )
        }
    }
}

@Serializable
private data class PersistableFlag(
    val allocationKey: String?,
    val variationKey: String?,
    val variationType: String,
    val variationValue: String,  // Serialized as string
    val extraLogging: Map<String, String>?,
    val doLog: Boolean
) {
    fun toDomain(): DecodedPrecomputedFlag {
        val type = VariationType.fromString(variationType)
            ?: throw IllegalArgumentException("Unknown variation type: $variationType")

        val typedValue = when (type) {
            VariationType.BOOLEAN -> variationValue.toBoolean()
            VariationType.INTEGER -> variationValue.toInt()
            VariationType.NUMERIC -> variationValue.toDouble()
            VariationType.STRING, VariationType.JSON -> variationValue
        }

        return DecodedPrecomputedFlag(
            allocationKey = allocationKey,
            variationKey = variationKey,
            variationType = type,
            variationValue = typedValue,
            extraLogging = extraLogging,
            doLog = doLog
        )
    }

    companion object {
        fun fromDomain(flag: DecodedPrecomputedFlag): PersistableFlag {
            return PersistableFlag(
                allocationKey = flag.allocationKey,
                variationKey = flag.variationKey,
                variationType = flag.variationType.value,
                variationValue = flag.variationValue.toString(),
                extraLogging = flag.extraLogging,
                doLog = flag.doLog
            )
        }
    }
}

@Serializable
private data class PersistableBandit(
    val banditKey: String,
    val action: String,
    val modelVersion: String,
    val actionProbability: Double,
    val optimalityGap: Double,
    val actionNumericAttributes: Map<String, Double>,
    val actionCategoricalAttributes: Map<String, String>
) {
    fun toDomain(): DecodedPrecomputedBandit {
        return DecodedPrecomputedBandit(
            banditKey = banditKey,
            action = action,
            modelVersion = modelVersion,
            actionProbability = actionProbability,
            optimalityGap = optimalityGap,
            actionNumericAttributes = actionNumericAttributes,
            actionCategoricalAttributes = actionCategoricalAttributes
        )
    }

    companion object {
        fun fromDomain(bandit: DecodedPrecomputedBandit): PersistableBandit {
            return PersistableBandit(
                banditKey = bandit.banditKey,
                action = bandit.action,
                modelVersion = bandit.modelVersion,
                actionProbability = bandit.actionProbability,
                optimalityGap = bandit.optimalityGap,
                actionNumericAttributes = bandit.actionNumericAttributes,
                actionCategoricalAttributes = bandit.actionCategoricalAttributes
            )
        }
    }
}
