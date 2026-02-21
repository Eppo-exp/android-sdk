package cloud.eppo.android;

import androidx.annotation.NonNull;
import cloud.eppo.api.dto.BanditParametersResponse;
import cloud.eppo.api.dto.FlagConfigResponse;
import cloud.eppo.parser.ConfigurationParseException;
import cloud.eppo.parser.ConfigurationParser;
import cloud.eppo.android.dto.adapters.EppoModule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Jackson implementation of {@link ConfigurationParser}.
 *
 * <p>Parses flag configuration and bandit parameters using Jackson ObjectMapper. Uses EppoModule
 * for custom deserializers that handle Eppo's configuration format, including proper handling of
 * nested DTOs (FlagConfig, Variation, Allocation, etc.) and base64-encoded values.
 */
public class JacksonConfigurationParser implements ConfigurationParser<JsonNode> {

  private final ObjectMapper objectMapper;

  /** Creates a new parser with a default ObjectMapper configured with EppoModule. */
  public JacksonConfigurationParser() {
    this.objectMapper = createDefaultObjectMapper();
  }

  private static ObjectMapper createDefaultObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(EppoModule.eppoModule());
    return mapper;
  }

  /**
   * Creates a new parser with a custom ObjectMapper.
   *
   * @param objectMapper the ObjectMapper to use for parsing
   */
  public JacksonConfigurationParser(@NonNull ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @NonNull @Override
  public FlagConfigResponse parseFlagConfig(@NonNull byte[] flagConfigJson)
      throws ConfigurationParseException {
    try {
      // Use interface type - EppoModule provides custom deserializer
      return objectMapper.readValue(flagConfigJson, FlagConfigResponse.class);
    } catch (Exception e) {
      throw new ConfigurationParseException("Failed to parse flag configuration", e);
    }
  }

  @NonNull @Override
  public BanditParametersResponse parseBanditParams(@NonNull byte[] banditParamsJson)
      throws ConfigurationParseException {
    try {
      // Use interface type - EppoModule provides custom deserializer
      return objectMapper.readValue(banditParamsJson, BanditParametersResponse.class);
    } catch (Exception e) {
      throw new ConfigurationParseException("Failed to parse bandit parameters", e);
    }
  }

  @NonNull @Override
  public JsonNode parseJsonValue(@NonNull String jsonValue) throws ConfigurationParseException {
    try {
      return objectMapper.readTree(jsonValue);
    } catch (Exception e) {
      throw new ConfigurationParseException("Failed to parse JSON value", e);
    }
  }
}
