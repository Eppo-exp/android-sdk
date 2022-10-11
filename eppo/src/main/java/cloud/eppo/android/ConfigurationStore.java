package cloud.eppo.android;

import java.util.Map;

import cloud.eppo.android.dto.FlagConfig;

public class ConfigurationStore {

    private Map<String, FlagConfig> flags;

    public void setFlags(Map<String, FlagConfig> flags) {
        this.flags = flags;

        // TODO persist to local file (for use when offline/before new config loads)
    }

    public FlagConfig getFlag(String flagKey) {
        if (flags == null) {
            return null;
        }
        return flags.get(flagKey);
    }

}
