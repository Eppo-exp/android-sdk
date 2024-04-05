package cloud.eppo.android.dto;

import java.util.concurrent.ConcurrentHashMap;

public class RandomizationConfigResponse {
    @SerializedName("flags")
    private ConcurrentHashMap<String, FlagConfig> flags;

    public ConcurrentHashMap<String, FlagConfig> getFlags() {
        return flags;
    }
}
