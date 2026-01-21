package cloud.eppo.android.dto;

import androidx.annotation.Nullable;

/** Result of a bandit action assignment containing the variation and optional action. */
public class BanditResult {

  private final String variation;
  @Nullable private final String action;

  public BanditResult(String variation, @Nullable String action) {
    this.variation = variation;
    this.action = action;
  }

  /** Returns the assigned variation value. */
  public String getVariation() {
    return variation;
  }

  /** Returns the action associated with the assignment, or null if not available. */
  @Nullable
  public String getAction() {
    return action;
  }
}
