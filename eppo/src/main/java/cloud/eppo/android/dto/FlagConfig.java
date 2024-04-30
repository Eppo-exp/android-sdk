package cloud.eppo.android.dto;

import java.util.List;
import java.util.Map;

public class FlagConfig {
    private String key;

    private boolean enabled;

    private int totalShards;

    private VariationType variationType;

    private Map<String, Variation> variations;

    private List<Allocation> allocations;

    public String getKey() {
        return this.key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public int getTotalShards() {
        return totalShards;
    }

    public void setTotalShards(int totalShards) {
        this.totalShards = totalShards;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public VariationType getVariationType() {
        return variationType;
    }

    public void setVariationType(VariationType variationType) {
        this.variationType = variationType;
    }

    public Map<String, Variation> getVariations() {
        return variations;
    }

    public void setVariations(Map<String, Variation> variations) {
        this.variations = variations;
    }

    public List<Allocation> getAllocations() {
        return allocations;
    }

    public void setAllocations(List<Allocation> allocations) {
        this.allocations = allocations;
    }
}
