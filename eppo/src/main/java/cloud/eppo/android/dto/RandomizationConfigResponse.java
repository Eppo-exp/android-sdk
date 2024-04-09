package cloud.eppo.android.dto;

import java.util.concurrent.ConcurrentHashMap;

public class RandomizationConfigResponse {
    private ConcurrentHashMap<String, FlagConfig> flags;

    public ConcurrentHashMap<String, FlagConfig> getFlags() {
        return flags;
    }

    public void setFlags(ConcurrentHashMap<String, FlagConfig> flags) {
        this.flags = flags;
    }
}
