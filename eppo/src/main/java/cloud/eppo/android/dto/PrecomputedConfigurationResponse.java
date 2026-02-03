package cloud.eppo.android.dto;

import androidx.annotation.Nullable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Map;

/** Wire protocol response from the precomputed edge endpoint. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrecomputedConfigurationResponse {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private static final PrecomputedConfigurationResponse EMPTY =
      new PrecomputedConfigurationResponse(
          "PRECOMPUTED", true, "", null, "", Collections.emptyMap(), Collections.emptyMap());

  private final String format;
  private final boolean obfuscated;
  private final String createdAt;
  @Nullable private final String environmentName;
  private final String salt;
  private final Map<String, PrecomputedFlag> flags;
  private final Map<String, PrecomputedBandit> bandits;

  @JsonCreator
  public PrecomputedConfigurationResponse(
      @JsonProperty("format") String format,
      @JsonProperty("obfuscated") boolean obfuscated,
      @JsonProperty("createdAt") String createdAt,
      @JsonProperty("environment") @Nullable JsonNode environment,
      @JsonProperty("salt") String salt,
      @JsonProperty("flags") @Nullable Map<String, PrecomputedFlag> flags,
      @JsonProperty("bandits") @Nullable Map<String, PrecomputedBandit> bandits) {
    this.format = format;
    this.obfuscated = obfuscated;
    this.createdAt = createdAt;
    this.environmentName = extractEnvironmentName(environment);
    this.salt = salt;
    this.flags = flags != null ? flags : Collections.emptyMap();
    this.bandits = bandits != null ? bandits : Collections.emptyMap();
  }

  @Nullable private static String extractEnvironmentName(@Nullable JsonNode environment) {
    if (environment == null) {
      return null;
    }
    if (environment.isTextual()) {
      return environment.asText();
    }
    if (environment.isObject() && environment.has("name")) {
      return environment.get("name").asText();
    }
    return null;
  }

  /** Returns the format of the configuration (always "PRECOMPUTED"). */
  public String getFormat() {
    return format;
  }

  /** Returns whether this configuration is obfuscated (always true for precomputed). */
  public boolean isObfuscated() {
    return obfuscated;
  }

  /** Returns the ISO 8601 timestamp when this configuration was created. */
  public String getCreatedAt() {
    return createdAt;
  }

  /** Returns the environment name, or null if not present. */
  @JsonIgnore
  @Nullable public String getEnvironmentName() {
    return environmentName;
  }

  /** Returns the environment as a map for JSON serialization. */
  @JsonGetter("environment")
  @Nullable public Map<String, String> getEnvironment() {
    if (environmentName == null) {
      return null;
    }
    return Collections.singletonMap("name", environmentName);
  }

  /** Returns the salt used for MD5 hashing flag keys. */
  public String getSalt() {
    return salt;
  }

  /** Returns the map of MD5-hashed flag keys to precomputed flags. */
  public Map<String, PrecomputedFlag> getFlags() {
    return flags;
  }

  /** Returns the map of MD5-hashed bandit keys to precomputed bandits. */
  public Map<String, PrecomputedBandit> getBandits() {
    return bandits;
  }

  /** Returns a singleton empty configuration response. */
  public static PrecomputedConfigurationResponse empty() {
    return EMPTY;
  }

  /**
   * Parses a JSON byte array into a PrecomputedConfigurationResponse.
   *
   * @param bytes JSON byte array
   * @return Parsed response
   * @throws RuntimeException if parsing fails
   */
  public static PrecomputedConfigurationResponse fromBytes(byte[] bytes) {
    try {
      return objectMapper.readValue(bytes, PrecomputedConfigurationResponse.class);
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse precomputed configuration", e);
    }
  }

  /**
   * Serializes this response to a JSON byte array.
   *
   * @return JSON byte array
   * @throws RuntimeException if serialization fails
   */
  public byte[] toBytes() {
    try {
      return objectMapper.writeValueAsBytes(this);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize precomputed configuration", e);
    }
  }
}
