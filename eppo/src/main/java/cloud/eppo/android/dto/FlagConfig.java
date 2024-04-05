package cloud.eppo.android.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class FlagConfig {
    @SerializedName("subjectShards")
    private int subjectShards;

    @SerializedName("enabled")
    private boolean enabled;

    @SerializedName("typedOverrides")
    private Map<String, String> typedOverrides = new HashMap<>();

    @SerializedName("rules")
    private List<TargetingRule> rules;

    @SerializedName("allocations")
    private Map<String, Allocation> allocations;

    public int getSubjectShards() {
        return subjectShards;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Map<String, String> getTypedOverrides() {
        return typedOverrides;
    }

    public Map<String, Allocation> getAllocations() {
        return allocations;
    }

    public List<TargetingRule> getRules() {
        return rules;
    }
}
