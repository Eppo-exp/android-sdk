package cloud.eppo.kotlin.internal.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Environment information from the server response.
 *
 * @property name Environment name (e.g., "production", "staging")
 */
@Serializable
internal data class Environment(
    @SerialName("name") val name: String
)
