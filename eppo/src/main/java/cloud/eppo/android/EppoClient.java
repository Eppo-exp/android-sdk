package cloud.eppo.android;

import static cloud.eppo.android.util.Utils.logTag;
import static cloud.eppo.android.util.Utils.safeCacheKey;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cloud.eppo.BaseEppoClient;
import cloud.eppo.IConfigurationStore;
import cloud.eppo.android.exceptions.MissingApiKeyException;
import cloud.eppo.android.exceptions.MissingApplicationException;
import cloud.eppo.android.exceptions.NotInitializedException;
import cloud.eppo.api.Attributes;
import cloud.eppo.api.Configuration;
import cloud.eppo.api.EppoValue;
import cloud.eppo.logging.AssignmentLogger;
import cloud.eppo.ufc.dto.VariationType;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public class EppoClient extends BaseEppoClient {
  private static final String TAG = logTag(EppoClient.class);
  private static final String DEFAULT_HOST = "https://fscdn.eppo.cloud";
  private static final boolean DEFAULT_IS_GRACEFUL_MODE = true;
  private static final boolean DEFAULT_OBFUSCATE_CONFIG = true;

  @Nullable private static EppoClient instance;

  private EppoClient(
      String apiKey,
      String host,
      String sdkName,
      String sdkVersion,
      @Nullable AssignmentLogger assignmentLogger,
      IConfigurationStore configurationStore,
      boolean isGracefulMode,
      boolean obfuscateConfig,
      @Nullable CompletableFuture<Configuration> initialConfiguration) {
    super(
        apiKey,
        sdkName,
        sdkVersion,
        host,
        assignmentLogger,
        null,
        configurationStore,
        isGracefulMode,
        obfuscateConfig,
        false,
        initialConfiguration);
  }

  /**
   * @noinspection unused
   */
  public static EppoClient init(
      @NonNull Application application,
      @NonNull String apiKey,
      @NonNull String host,
      @Nullable AssignmentLogger assignmentLogger,
      boolean isGracefulMode) {
    return new Builder()
        .application(application)
        .apiKey(apiKey)
        .host(host)
        .assignmentLogger(assignmentLogger)
        .isGracefulMode(isGracefulMode)
        .obfuscateConfig(DEFAULT_OBFUSCATE_CONFIG)
        .buildAndInit();
  }

  /**
   * @noinspection unused
   */
  public static CompletableFuture<EppoClient> initAsync(
      @NonNull Application application,
      @NonNull String apiKey,
      @NonNull String host,
      @Nullable AssignmentLogger assignmentLogger,
      boolean isGracefulMode) {
    return new Builder()
        .application(application)
        .apiKey(apiKey)
        .host(host)
        .assignmentLogger(assignmentLogger)
        .isGracefulMode(isGracefulMode)
        .obfuscateConfig(DEFAULT_OBFUSCATE_CONFIG)
        .buildAndInitAsync();
  }

  public static EppoClient getInstance() throws NotInitializedException {
    if (EppoClient.instance == null) {
      throw new NotInitializedException();
    }

    return EppoClient.instance;
  }

  protected EppoValue getTypedAssignment(
      String flagKey,
      String subjectKey,
      Attributes subjectAttributes,
      EppoValue defaultValue,
      VariationType expectedType) {
    return super.getTypedAssignment(
        flagKey, subjectKey, subjectAttributes, defaultValue, expectedType);
  }

  public static class Builder {
    @NonNull private String host = DEFAULT_HOST;
    @Nullable private Application application;
    @Nullable private String apiKey;
    @Nullable private AssignmentLogger assignmentLogger;
    @Nullable private ConfigurationStore configStore;
    private boolean isGracefulMode = DEFAULT_IS_GRACEFUL_MODE;
    private boolean obfuscateConfig = DEFAULT_OBFUSCATE_CONFIG;
    private boolean forceReinitialize = false;
    private boolean offlineMode = false;
    private CompletableFuture<Configuration> initialConfiguration;

    public Builder apiKey(String apiKey) {
      this.apiKey = apiKey;
      return this;
    }

    public Builder application(Application application) {
      this.application = application;
      return this;
    }

    public Builder host(String host) {
      this.host = host;
      return this;
    }

    public Builder assignmentLogger(AssignmentLogger assignmentLogger) {
      this.assignmentLogger = assignmentLogger;
      return this;
    }

    public Builder isGracefulMode(boolean isGracefulMode) {
      this.isGracefulMode = isGracefulMode;
      return this;
    }

    public Builder obfuscateConfig(boolean obfuscateConfig) {
      this.obfuscateConfig = obfuscateConfig;
      return this;
    }

    public Builder forceReinitialize(boolean forceReinitialize) {
      this.forceReinitialize = forceReinitialize;
      return this;
    }

    public Builder offlineMode(boolean offlineMode) {
      this.offlineMode = offlineMode;
      return this;
    }

    public Builder withInitialFlagConfigResponse(
        String initialFlagConfigResponse, boolean isConfigObfuscated) {
      this.initialConfiguration =
          CompletableFuture.completedFuture(
              Configuration.builder(initialFlagConfigResponse.getBytes(), isConfigObfuscated)
                  .build());
      return this;
    }

    Builder configStore(ConfigurationStore configStore) {
      this.configStore = configStore;
      return this;
    }

    public CompletableFuture<EppoClient> buildAndInitAsync() {
      if (application == null) {
        throw new MissingApplicationException();
      }
      if (apiKey == null) {
        throw new MissingApiKeyException();
      }

      if (instance != null && !forceReinitialize) {
        Log.w(TAG, "Eppo Client instance already initialized");
        return CompletableFuture.completedFuture(instance);
      } else if (instance != null) {
        // Always recreate for tests
        Log.d(TAG, "`forceReinitialize` triggered reinitializing Eppo Client");
      }

      String sdkName = obfuscateConfig ? "android" : "android-debug";
      String sdkVersion = BuildConfig.EPPO_VERSION;

      // Get caching from config store
      if (configStore == null) {
        // Cache at a per-API key level (useful for development)
        String cacheFileNameSuffix = safeCacheKey(apiKey);
        configStore = new ConfigurationStore(application, cacheFileNameSuffix);
      }

      // If the initial config was not set, use the ConfigurationStore's cache as the initial
      // config.
      if (initialConfiguration == null) {
        initialConfiguration = configStore.loadConfigFromCache();
      }

      instance =
          new EppoClient(
              apiKey,
              host,
              sdkName,
              sdkVersion,
              assignmentLogger,
              configStore,
              isGracefulMode,
              obfuscateConfig,
              initialConfiguration);

      final CompletableFuture<EppoClient> ret = new CompletableFuture<>();

      AtomicInteger failCount = new AtomicInteger(0);

      if (!offlineMode) {

        // Not offline mode. Kick off a fetch.
        instance
            .loadConfigurationAsync()
            .handle(
                (success, ex) -> {
                  if (ex == null) {
                    ret.complete(instance);
                  } else if (failCount.incrementAndGet() == 2
                      || instance.getInitialConfigFuture() == null) {
                    ret.completeExceptionally(
                        new RuntimeException(
                            "Unable to initialize client; Configuration could not be loaded", ex));
                  }
                  return null;
                });
      }

      if (instance.getInitialConfigFuture() != null) {
        instance
            .getInitialConfigFuture()
            .handle(
                (success, ex) -> {
                  if (ex == null && success) {
                    ret.complete(instance);
                  } else if (offlineMode || failCount.incrementAndGet() == 2) {
                    ret.completeExceptionally(
                        new RuntimeException(
                            "Unable to initialize client; Configuration could not be loaded", ex));
                  } else {
                    Log.d(TAG, "Initial config was not used.");
                    failCount.incrementAndGet();
                  }
                  return null;
                });
      }
      return ret;
    }

    /** Builds and initializes an `EppoClient`, immediately available to compute assignments. */
    public EppoClient buildAndInit() {
      try {
        return buildAndInitAsync().get();
      } catch (ExecutionException | InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
