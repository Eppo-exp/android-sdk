package cloud.eppo.android.dto;

import java.util.Map;
import java.util.Set;

public class Split {

    private String variationKey;

    private Set<Shard> shards;

    private Map<String, String> extraLogging;

    public String getVariationKey() {
        return variationKey;
    }

    public void setVariationKey(String variationKey) {
        this.variationKey = variationKey;
    }

    public Set<Shard> getShards() {
        return shards;
    }

    public void setShards(Set<Shard> shards) {
        this.shards = shards;
    }

    public Map<String, String> getExtraLogging() {
        return extraLogging;
    }

    public void setExtraLogging(Map<String, String> extraLogging) {
        this.extraLogging = extraLogging;
    }
}
