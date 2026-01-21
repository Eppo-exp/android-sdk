package cloud.eppo.android.dto;

import androidx.annotation.Nullable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Represents a precomputed flag assignment from the edge endpoint. All string fields except
 * variationType are Base64 encoded.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrecomputedFlag {

  @Nullable private final String allocationKey;
  @Nullable private final String variationKey;
  private final String variationType;
  private final String variationValue;
  @Nullable private final Map<String, String> extraLogging;
  private final boolean doLog;

  @JsonCreator
  public PrecomputedFlag(
      @JsonProperty("allocationKey") @Nullable String allocationKey,
      @JsonProperty("variationKey") @Nullable String variationKey,
      @JsonProperty("variationType") String variationType,
      @JsonProperty("variationValue") String variationValue,
      @JsonProperty("extraLogging") @Nullable Map<String, String> extraLogging,
      @JsonProperty("doLog") boolean doLog) {
    this.allocationKey = allocationKey;
    this.variationKey = variationKey;
    this.variationType = variationType;
    this.variationValue = variationValue;
    this.extraLogging = extraLogging;
    this.doLog = doLog;
  }

  /** Returns the Base64-encoded allocation key, or null if not assigned. */
  @Nullable
  public String getAllocationKey() {
    return allocationKey;
  }

  /** Returns the Base64-encoded variation key, or null if not assigned. */
  @Nullable
  public String getVariationKey() {
    return variationKey;
  }

  /** Returns the variation type (STRING, BOOLEAN, INTEGER, NUMERIC, JSON). */
  public String getVariationType() {
    return variationType;
  }

  /** Returns the Base64-encoded variation value. */
  public String getVariationValue() {
    return variationValue;
  }

  /** Returns the Base64-encoded extra logging map, or null if not present. */
  @Nullable
  public Map<String, String> getExtraLogging() {
    return extraLogging;
  }

  /** Returns whether this assignment should be logged. */
  public boolean isDoLog() {
    return doLog;
  }
}
