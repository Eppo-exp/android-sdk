package cloud.eppo;

import java.util.Map;

public class EppoClient {
    private static final String URL = "https://eppo.cloud/api";

    private final ConfigurationStore store;
    private final ConfigurationRequestor requestor;
    private static EppoClient instance;

    private EppoClient(String apiKey) {
        store = new ConfigurationStore();
        EppoHttpClient httpClient = new EppoHttpClient(URL, apiKey);
        requestor = new ConfigurationRequestor(store, httpClient);
    }

    public static EppoClient init(String apiKey) {
        if (instance == null) {
            instance = new EppoClient(apiKey);
            instance.refreshConfiguration();
        }
        return instance;
    }

    private void refreshConfiguration() {
        requestor.load();
    }

    public String getAssignment(
            String subjectKey,
            String experimentKey,
            Map<String, String> subjectAttributes) {
        return ""; // TODO
    }
}
