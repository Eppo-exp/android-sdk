package cloud.eppo.android;

import java.util.concurrent.CompletableFuture;

import cloud.eppo.IConfigurationStore;
import cloud.eppo.api.Configuration;

public interface AndroidConfigurationStore extends IConfigurationStore {
  CompletableFuture<Configuration> loadConfigFromCache();
}
