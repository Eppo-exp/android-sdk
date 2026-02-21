package cloud.eppo.android;

import androidx.annotation.NonNull;
import cloud.eppo.api.dto.BanditParametersResponse;
import cloud.eppo.api.dto.FlagConfigResponse;
import cloud.eppo.parser.ConfigurationParseException;
import cloud.eppo.parser.ConfigurationParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Jackson implementation of {@link ConfigurationParser}.
 *
 * <p>Parses flag configuration and bandit parameters using Jackson ObjectMapper. Uses the Default
 * implementations of DTO interfaces which are Jackson-compatible.
 */
public class JacksonConfigurationParser implements ConfigurationParser<JsonNode> {

  private final ObjectMapper objectMapper;

  /** Creates a new parser with a default ObjectMapper. */
  public JacksonConfigurationParser() {
    this.objectMapper = createDefaultObjectMapper();
  }

  private static ObjectMapper createDefaultObjectMapper() {
    return new ObjectMapper();
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
      return objectMapper.readValue(flagConfigJson, FlagConfigResponse.Default.class);
    } catch (Exception e) {
      throw new ConfigurationParseException("Failed to parse flag configuration", e);
    }
  }

  @NonNull @Override
  public BanditParametersResponse parseBanditParams(@NonNull byte[] banditParamsJson)
      throws ConfigurationParseException {
    try {
      return objectMapper.readValue(banditParamsJson, BanditParametersResponse.Default.class);
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
