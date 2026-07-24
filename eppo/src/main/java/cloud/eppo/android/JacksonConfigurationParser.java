package cloud.eppo.android;

import cloud.eppo.android.dto.adapters.EppoModule;
import cloud.eppo.api.Configuration;
import cloud.eppo.api.dto.BanditParameters;
import cloud.eppo.api.dto.BanditParametersResponse;
import cloud.eppo.api.dto.BanditReference;
import cloud.eppo.api.dto.FlagConfigResponse;
import cloud.eppo.parser.ConfigurationParseException;
import cloud.eppo.parser.ConfigurationParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link ConfigurationParser} using Jackson.
 *
 * <p>This parser uses Jackson's ObjectMapper with custom deserializers for Eppo's configuration
 * format. The deserializers are hand-rolled to avoid reliance on annotations and method names,
 * which can be unreliable when ProGuard minification is in use.
 */
public class JacksonConfigurationParser implements ConfigurationParser<Configuration, JsonNode> {
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
  public Configuration buildConfig(
      byte[] flagConfigBytes,
      @Nullable String flagsSnapshotId,
      @Nullable Configuration previousConfig) {
    try {
      FlagConfigResponse flagConfigResponse =
          objectMapper.readValue(flagConfigBytes, FlagConfigResponse.class);
      Configuration.Builder builder = new Configuration.Builder(flagConfigResponse);
      if (previousConfig != null) {
        builder.banditParametersFromConfig(previousConfig);
      }
      builder.flagsSnapshotId(flagsSnapshotId);
      return builder.build();
    } catch (IOException e) {
      throw new ConfigurationParseException("Failed to parse flag configuration", e);
    }
  }

  @Override
  public boolean requiresUpdatedBanditModels(Configuration config) {
    Set<String> neededModelVersions =
        config.getBanditReferences().values().stream()
            .map(BanditReference::getModelVersion)
            .collect(Collectors.toSet());
    Set<String> loadedModelVersions =
        config.getBandits().values().stream()
            .map(BanditParameters::getModelVersion)
            .collect(Collectors.toSet());
    return !loadedModelVersions.containsAll(neededModelVersions);
  }

  @Override
  public Configuration applyBanditParameters(Configuration config, byte[] banditParamsBytes) {
    try {
      BanditParametersResponse response =
          objectMapper.readValue(banditParamsBytes, BanditParametersResponse.class);
      return config.toBuilder().banditParameters(response).build();
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
}
