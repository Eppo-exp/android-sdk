package cloud.eppo.kotlinexample

import cloud.eppo.JacksonConfigurationParser
import cloud.eppo.api.dto.BanditParametersResponse
import cloud.eppo.api.dto.FlagConfigResponse
import cloud.eppo.parser.ConfigurationParseException
import cloud.eppo.parser.ConfigurationParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * [ConfigurationParser] implementation that uses kotlinx.serialization for JSON flag values.
 *
 * Flag configuration and bandit parameter parsing is delegated to [JacksonConfigurationParser]
 * because the framework DTO types ([FlagConfigResponse], [BanditParametersResponse]) have
 * hand-rolled Jackson deserializers in sdk-common-jvm that handle Eppo's obfuscated config
 * format. Only [parseJsonValue] uses kotlinx.serialization, since that is the method that
 * returns the user-facing [JsonElement] type.
 */
class KotlinxConfigurationParser : ConfigurationParser<JsonElement> {

    private val delegate = JacksonConfigurationParser()
    private val json = Json { ignoreUnknownKeys = true }

    override fun parseFlagConfig(flagConfigJson: ByteArray): FlagConfigResponse =
        delegate.parseFlagConfig(flagConfigJson)

    override fun parseBanditParams(banditParamsJson: ByteArray): BanditParametersResponse =
        delegate.parseBanditParams(banditParamsJson)

    override fun parseJsonValue(jsonValue: String): JsonElement =
        try {
            json.parseToJsonElement(jsonValue)
        } catch (e: Exception) {
            throw ConfigurationParseException("Failed to parse JSON value: $jsonValue", e)
        }
}
