package cloud.eppo.android;

import java.util.concurrent.CompletableFuture;

import androidx.annotation.NonNull;
import cloud.eppo.api.Configuration;

public class ListenableConfigurationStore implements AndroidConfigurationStore {

  private final AndroidConfigurationStore store;
  private final ConfigurationChangeListener listener;

  public ListenableConfigurationStore(AndroidConfigurationStore store, ConfigurationChangeListener listener) {
    this.store = store;
    this.listener = listener;
  }

  @NonNull
  @Override
  public Configuration getConfiguration() {
    return store.getConfiguration();
  }

  @Override
  public CompletableFuture<Void> saveConfiguration(Configuration configuration) {
    return store
        .saveConfiguration(configuration)
        .thenRun(listener::onConfigurationChanged);
  }

  @Override
  public CompletableFuture<Configuration> loadConfigFromCache() {
    return store.loadConfigFromCache();
  }
}
