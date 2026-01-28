package cloud.eppo.android.dto;

import androidx.annotation.Nullable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Represents a precomputed bandit assignment from the edge endpoint. String fields are Base64
 * encoded.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrecomputedBandit {

  private final String banditKey;
  private final String action;
  private final String modelVersion;
  @Nullable private final Map<String, String> actionNumericAttributes;
  @Nullable private final Map<String, String> actionCategoricalAttributes;
  private final double actionProbability;
  private final double optimalityGap;

  @JsonCreator
  public PrecomputedBandit(
      @JsonProperty("banditKey") String banditKey,
      @JsonProperty("action") String action,
      @JsonProperty("modelVersion") String modelVersion,
      @JsonProperty("actionNumericAttributes") @Nullable Map<String, String> actionNumericAttributes,
      @JsonProperty("actionCategoricalAttributes") @Nullable Map<String, String> actionCategoricalAttributes,
      @JsonProperty("actionProbability") double actionProbability,
      @JsonProperty("optimalityGap") double optimalityGap) {
    this.banditKey = banditKey;
    this.action = action;
    this.modelVersion = modelVersion;
    this.actionNumericAttributes = actionNumericAttributes;
    this.actionCategoricalAttributes = actionCategoricalAttributes;
    this.actionProbability = actionProbability;
    this.optimalityGap = optimalityGap;
  }

  /** Returns the Base64-encoded bandit key. */
  public String getBanditKey() {
    return banditKey;
  }

  /** Returns the Base64-encoded action. */
  public String getAction() {
    return action;
  }

  /** Returns the Base64-encoded model version. */
  public String getModelVersion() {
    return modelVersion;
  }

  /** Returns the Base64-encoded numeric attributes for the action. */
  @Nullable public Map<String, String> getActionNumericAttributes() {
    return actionNumericAttributes;
  }

  /** Returns the Base64-encoded categorical attributes for the action. */
  @Nullable public Map<String, String> getActionCategoricalAttributes() {
    return actionCategoricalAttributes;
  }

  /** Returns the probability of taking this action. */
  public double getActionProbability() {
    return actionProbability;
  }

  /** Returns the gap to the optimal action. */
  public double getOptimalityGap() {
    return optimalityGap;
  }
}
