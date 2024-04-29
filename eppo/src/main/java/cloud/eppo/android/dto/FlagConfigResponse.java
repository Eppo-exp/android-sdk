package cloud.eppo.android.dto;

import java.util.Map;

public class FlagConfigResponse {
    private Map<String, FlagConfig> flags;

    public Map<String, FlagConfig> getFlags() {
        return this.flags;
    }

    public void setFlags(Map<String, FlagConfig> flags) {
        this.flags = flags;
    }
}
