package cloud.eppo.android.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class FlagConfig {
    private int subjectShards;

    private boolean enabled;

    private Map<String, String> typedOverrides = new HashMap<>();

    private List<TargetingRule> rules;

    private Map<String, Allocation> allocations;

    public void setSubjectShards(int subjectShards) {
        this.subjectShards = subjectShards;
    }
    public int getSubjectShards() {
        return subjectShards;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    public boolean isEnabled() {
        return enabled;
    }

    public void setTypedOverrides(Map<String, String> typedOverrides) {
        this.typedOverrides = typedOverrides;
    }
    public Map<String, String> getTypedOverrides() {
        return typedOverrides;
    }

    public  void setAllocations(Map<String, Allocation> allocations) {
        this.allocations = allocations;
    }

    public Map<String, Allocation> getAllocations() {
        return allocations;
    }

    public void setRules(List<TargetingRule> rules) {
        this.rules = rules;
    }

    public List<TargetingRule> getRules() {
        return rules;
    }
}
