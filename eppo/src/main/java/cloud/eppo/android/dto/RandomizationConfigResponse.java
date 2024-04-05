package cloud.eppo.android.dto;

import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.annotations.SerializedName;

public class RandomizationConfigResponse {
    @SerializedName("flags")
    private ConcurrentHashMap<String, FlagConfig> flags;

    public ConcurrentHashMap<String, FlagConfig> getFlags() {
        return flags;
    }
}
