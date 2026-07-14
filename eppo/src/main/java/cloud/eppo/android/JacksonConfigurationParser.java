package cloud.eppo.android;

import androidx.annotation.NonNull;

import cloud.eppo.android.dto.adapters.EppoModule;
import cloud.eppo.api.Configuration;
import cloud.eppo.api.SerializableEppoConfiguration;
import cloud.eppo.api.dto.BanditParametersResponse;
import cloud.eppo.api.dto.FlagConfigResponse;
import cloud.eppo.parser.ConfigurationParseException;
import cloud.eppo.parser.ConfigurationParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link ConfigurationParser} using Jackson.
 *
 * <p>This parser uses Jackson's ObjectMapper with custom deserializers for Eppo's configuration
 * format. The deserializers are hand-rolled to avoid reliance on annotations and method names,
 * which can be unreliable when ProGuard minification is in use.
 */
public class JacksonConfigurationParser implements ConfigurationParser<
  Configuration,
  Configuration.Builder,
  JsonNode
> {
  private static final Logger log = LoggerFactory.getLogger(JacksonConfigurationParser.class);

  private final ObjectMapper objectMapper;

  /** Creates a new parser with the default ObjectMapper configuration. */
  public JacksonConfigurationParser() {
    this(createDefaultObjectMapper());
  }

  /**
   * Creates a new parser with a custom ObjectMapper.
   *
   * <p>Note: The provided ObjectMapper must be configured with {@link EppoModule#eppoModule()} for
   * proper deserialization of Eppo configuration types.
   *
   * @param objectMapper the ObjectMapper instance to use
   */
  public JacksonConfigurationParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  private static ObjectMapper createDefaultObjectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(EppoModule.eppoModule());
    return mapper;
  }

  @Override
  public FlagConfigResponse parseFlagConfig(byte[] flagConfigJson)
      throws ConfigurationParseException {
    try {
      log.debug("Parsing flag configuration, {} bytes", flagConfigJson.length);
      return objectMapper.readValue(flagConfigJson, FlagConfigResponse.class);
    } catch (IOException e) {
      throw new ConfigurationParseException("Failed to parse flag configuration", e);
    }
  }

  @Override
  public BanditParametersResponse parseBanditParams(byte[] banditParamsJson)
      throws ConfigurationParseException {
    try {
      log.debug("Parsing bandit parameters, {} bytes", banditParamsJson.length);
      return objectMapper.readValue(banditParamsJson, BanditParametersResponse.class);
    } catch (IOException e) {
      throw new ConfigurationParseException("Failed to parse bandit parameters", e);
    }
  }

  @Override
  public @NotNull JsonNode parseJsonValue(@NotNull String jsonValue)
      throws ConfigurationParseException {
    try {
      return objectMapper.readTree(jsonValue);
    } catch (IOException e) {
      throw new ConfigurationParseException("Failed to parse JSON value", e);
    }
  }

  @NonNull
  @Override
  public Configuration.Builder configurationBuilder(@NotNull FlagConfigResponse flagConfigResponse) {
    return new Configuration.Builder(flagConfigResponse);
  }

  @NonNull
  @Override
  public Configuration.Builder configurationBuilder(@NotNull FlagConfigResponse flagConfigResponse, boolean isConfigObfuscated) {
    return new Configuration.Builder(flagConfigResponse, isConfigObfuscated);
  }
}
