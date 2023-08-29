package cloud.eppo.android.dto;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class FlagConfig {
    private int subjectShards;
    private boolean enabled;
    private Map<String, String> typedOverrides = new HashMap<>();
    private List<TargetingRule> rules;
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
