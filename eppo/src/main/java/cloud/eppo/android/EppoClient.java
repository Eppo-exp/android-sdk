package cloud.eppo.android;

import static cloud.eppo.android.util.Utils.logTag;
import static cloud.eppo.android.util.Utils.safeCacheKey;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cloud.eppo.BaseEppoClient;
import cloud.eppo.IConfigurationStore;
import cloud.eppo.android.cache.LRUAssignmentCache;
import cloud.eppo.android.exceptions.NotInitializedException;
import cloud.eppo.api.Attributes;
import cloud.eppo.api.Configuration;
import cloud.eppo.api.EppoActionCallback;
import cloud.eppo.api.EppoValue;
import cloud.eppo.api.IAssignmentCache;
import cloud.eppo.logging.AssignmentLogger;
import cloud.eppo.ufc.dto.VariationType;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EppoClient extends BaseEppoClient {
  private static final String TAG = logTag(EppoClient.class);
  private static final boolean DEFAULT_IS_GRACEFUL_MODE = true;
  private static final long DEFAULT_POLLING_INTERVAL_MS = 5 * 60 * 1000;
  private static final long DEFAULT_JITTER_INTERVAL_RATIO = 10;
  private static final Logger log = LoggerFactory.getLogger(EppoClient.class);

  private long pollingIntervalMs, pollingJitterMs;

  @Nullable private static EppoClient instance;

  private EppoClient(
      String sdkKey,
      String sdkName,
      String sdkVersion,
      @Nullable String apiBaseUrl,
      @Nullable AssignmentLogger assignmentLogger,
      IConfigurationStore configurationStore,
      boolean isGracefulMode,
      @Nullable Configuration initialConfiguration,
      @Nullable IAssignmentCache assignmentCache) {

    super(
        sdkKey,
        sdkName,
        sdkVersion,
        apiBaseUrl,
        assignmentLogger,
        null,
        configurationStore,
        isGracefulMode,
        false,
        initialConfiguration,
        assignmentCache,
        null);
  }

  /**
   * @noinspection unused
   */
  public static EppoClient init(
      @NonNull Application application,
      @NonNull String sdkKey,
      @Nullable String apiBaseUrl,
      @Nullable AssignmentLogger assignmentLogger,
      boolean isGracefulMode) {
    return new Builder(sdkKey, application)
        .apiBaseUrl(apiBaseUrl)
        .assignmentLogger(assignmentLogger)
        .isGracefulMode(isGracefulMode)
        .buildAndInit();
  }

  /**
   * @noinspection unused
   */
  public static CompletableFuture<EppoClient> initAsync(
      @NonNull Application application,
      @NonNull String sdkKey,
      @NonNull String apiBaseUrl,
      @Nullable AssignmentLogger assignmentLogger,
      boolean isGracefulMode) {
    return new Builder(sdkKey, application)
        .apiBaseUrl(apiBaseUrl)
        .assignmentLogger(assignmentLogger)
        .isGracefulMode(isGracefulMode)
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

  /** (Re)loads flag and experiment configuration from the API server. */
  @Override
  public void fetchAndActivateConfiguration() {
    try {
      super.fetchAndActivateConfiguration();
    } catch (Exception e) {
      if (!isGracefulMode) {
        throw new RuntimeException(e);
      }
    }
  }

  /** Asynchronously (re)loads flag and experiment configuration from the API server. */
  public CompletableFuture<Configuration> loadConfigurationAsync() {
    CompletableFutureCallback<Configuration> callback = new CompletableFutureCallback<>();
    super.fetchAndActivateConfigurationAsync(callback);
    return callback.future;
  }

  public static class CompletableFutureCallback<T> implements EppoActionCallback<T> {
    public final CompletableFuture<T> future;

    public CompletableFutureCallback() {
      future = new CompletableFuture<>();
    }

    @Override
    public void onSuccess(T data) {
      future.complete(data);
    }

    @Override
    public void onFailure(Throwable error) {
      future.completeExceptionally(error);
    }
  }

  public static class Builder {
    private String apiBaseUrl;
    private final Application application;
    private final String sdkKey;
    @Nullable private AssignmentLogger assignmentLogger;
    @Nullable private ConfigurationStore configStore;
    private boolean isGracefulMode = DEFAULT_IS_GRACEFUL_MODE;
    private boolean forceReinitialize = false;
    private boolean offlineMode = false;
    private Configuration initialConfiguration;
    private boolean ignoreCachedConfiguration = false;
    private boolean pollingEnabled = false;
    private long pollingIntervalMs = DEFAULT_POLLING_INTERVAL_MS;

    /**
     * -1 causes the default jitter to be used (which is a % of the interval, not a constant
     * amount).
     */
    private long pollingJitterMs = -1;

    // Assignment caching on by default. To disable, call `builder.assignmentCache(null);`
    private IAssignmentCache assignmentCache = new LRUAssignmentCache(100);
    @Nullable private Configuration.Callback configChangeCallback;

    public Builder(@NonNull String sdkKey, @NonNull Application application) {
      this.application = application;
      this.sdkKey = sdkKey;
    }

    public Builder apiBaseUrl(@Nullable String apiBaseUrl) {
      this.apiBaseUrl = apiBaseUrl;
      return this;
    }

    public Builder assignmentLogger(AssignmentLogger assignmentLogger) {
      this.assignmentLogger = assignmentLogger;
      return this;
    }

    public Builder ignoreCachedConfiguration(boolean ignoreCache) {
      this.ignoreCachedConfiguration = ignoreCache;
      return this;
    }

    public Builder isGracefulMode(boolean isGracefulMode) {
      this.isGracefulMode = isGracefulMode;
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

    public Builder assignmentCache(IAssignmentCache assignmentCache) {
      this.assignmentCache = assignmentCache;
      return this;
    }

    public Builder initialConfiguration(byte[] initialFlagConfigResponse) {
      this.initialConfiguration = Configuration.builder(initialFlagConfigResponse).build();
      return this;
    }

    Builder configStore(ConfigurationStore configStore) {
      this.configStore = configStore;
      return this;
    }

    /**
     * Sets whether the client should periodically check for updated configuration. Used in
     * conjunction with `pollingIntervalMs` default 60000 and `pollingJitterMs` default 600.
     */
    public Builder pollingEnabled(boolean pollingEnabled) {
      this.pollingEnabled = pollingEnabled;
      return this;
    }

    /**
     * Sets how often the client should check for updated configurations, in milliseconds. Setting
     * to 0 prevents the client from polling. A suggested interval is one minute (60000).
     */
    public Builder pollingIntervalMs(long pollingIntervalMs) {
      this.pollingIntervalMs = pollingIntervalMs;
      return this;
    }

    /**
     * Sets the amount of jitter to use when scheduling next poll call. The jitter is the maximum
     * difference between the specified `pollingIntervalMs` and the effective interval used for each
     * time the polling waits for the next call.
     */
    public Builder pollingJitterMs(long pollingJitterMs) {
      this.pollingJitterMs = pollingJitterMs;
      return this;
    }

    /**
     * Registers a callback for when a new configuration is applied to the `EppoClient` instance.
     */
    public Builder onConfigurationChange(Configuration.Callback configChangeCallback) {
      this.configChangeCallback = configChangeCallback;
      return this;
    }

    public CompletableFuture<EppoClient> buildAndInitAsync() {
      if (instance != null && !forceReinitialize) {
        Log.w(TAG, "Eppo Client instance already initialized");
        return CompletableFuture.completedFuture(instance);
      } else if (instance != null) {
        // Stop polling (if the client is polling for configuration)
        instance.stopPolling();

        // Always recreate for tests
        Log.d(TAG, "`forceReinitialize` triggered reinitializing Eppo Client");
      }

      //      String sdkName = obfuscateConfig ? "android" : "android-debug";
      String sdkName = "android";
      String sdkVersion = BuildConfig.EPPO_VERSION;

      // Get caching from config store
      if (configStore == null) {
        // Cache at a per-API key level (useful for development)
        String cacheFileNameSuffix = safeCacheKey(sdkKey);
        configStore = new ConfigurationStore(application, cacheFileNameSuffix);
      }
      //
      //      // If the initial config was not set, attempt to use the ConfigurationStore's cache as
      // the
      //      // initial config.
      //      if (initialConfiguration == null && !ignoreCachedConfiguration) {
      //        initialConfiguration = configStore.loadConfigFromCache();
      //      }

      instance =
          new EppoClient(
              sdkKey,
              sdkName,
              sdkVersion,
              apiBaseUrl,
              assignmentLogger,
              configStore,
              isGracefulMode,
              initialConfiguration,
              assignmentCache);

      if (configChangeCallback != null) {
        instance.onConfigurationChange(configChangeCallback);
      }

      if (offlineMode) {
        // Offline mode means initializing without making/waiting on any fetches or polling.
        // Note: breaking change
        if (pollingEnabled) {
          log.warn("Ignoring pollingEnabled parameter as offlineMode is set to true");
        }
        return CompletableFuture.completedFuture(instance);
      }

      final CompletableFuture<EppoClient> ret = new CompletableFuture<>();
      AtomicInteger attemptCompleteCount = new AtomicInteger(0);
      AtomicInteger failCount = new AtomicInteger(0);

      // Not in offline mode. We'll kick off a fetch and attempt to load from the cache async.
      AtomicBoolean configLoaded = new AtomicBoolean(false);

      configStore.loadConfigFromCacheAsync(
          configuration -> {
            if (configuration != null && !configuration.isEmpty()) {
              if (!configLoaded.getAndSet(true)) {
                // Config is not null, not empty and has not yet been set so set this one.
                instance.activateConfiguration(configuration);
                ret.complete(instance);
              } // else config has already been set
            } else {
              if (failCount.incrementAndGet() == 2) {
                ret.completeExceptionally(
                    new EppoInitializationException(
                        "Unable to initialize client; Configuration could not be loaded", null));
              }
            }
          });

      instance.fetchAndActivateConfigurationAsync(
          new EppoActionCallback<Configuration>() {
            @Override
            public void onSuccess(Configuration data) {
              if (!configLoaded.getAndSet(true)) {
                // Cache has not yet set the config
                ret.complete(instance);
              }
            }

            @Override
            public void onFailure(Throwable error) {
              // If the local load already failed, throw an error
              if (failCount.incrementAndGet() == 2) {
                ret.completeExceptionally(
                    new EppoInitializationException(
                        "Unable to initialize client; Configuration could not be loaded", null));
              }
            }
          });

      // Not offline mode. Kick off a fetch.
      //          instance
      //              .loadConfigurationAsync()
      //              .handle(
      //                  (success, ex) -> {
      //                    if (ex == null) {
      //                      ret.complete(instance);
      //                    } else if (failCount.incrementAndGet() == 2
      //                        || instance.getInitialConfigFuture() == null) {
      //                      ret.completeExceptionally(
      //                          new EppoInitializationException(
      //                              "Unable to initialize client; Configuration could not be
      // loaded", ex));
      //                    }
      //                    return null;
      //                  });

      // Start polling, if configured.
      if (pollingEnabled && pollingIntervalMs > 0) {
        Log.d(TAG, "Starting poller");
        if (pollingJitterMs < 0) {
          pollingJitterMs = pollingIntervalMs / DEFAULT_JITTER_INTERVAL_RATIO;
        }

        instance.startPolling(pollingIntervalMs, pollingJitterMs);
      }

      return ret.exceptionally(
          e -> {
            Log.e(TAG, "Exception caught during initialization: " + e.getMessage(), e);
            if (!isGracefulMode) {
              throw new RuntimeException(e);
            }
            instance.activateConfiguration(Configuration.emptyConfig());
            return instance;
          });
    }

    /** Builds and initializes an `EppoClient`, immediately available to compute assignments. */
    public EppoClient buildAndInit() {
      try {
        return buildAndInitAsync().get();
      } catch (ExecutionException | InterruptedException | CompletionException e) {
        // If the exception was an `EppoInitializationException`, we know for sure that
        // `buildAndInitAsync` logged it (and wrapped it with a RuntimeException) which was then
        // wrapped by `CompletableFuture` with a `CompletionException`.
        if (e instanceof CompletionException) {
          Throwable cause = e.getCause();
          if (cause instanceof RuntimeException
              && cause.getCause() instanceof EppoInitializationException) {
            return instance;
          }
        }
        Log.e(TAG, "Exception caught during initialization: " + e.getMessage(), e);
        if (!isGracefulMode) {
          throw new RuntimeException(e);
        }
      }
      return instance;
    }
  }

  protected void stopPolling() {
    super.stopPolling();
  }

  protected void startPolling(long pollingIntervalMs, long pollingJitterMs) {
    // Store the polling params for resuming later.
    this.pollingIntervalMs = pollingIntervalMs;
    this.pollingJitterMs = pollingJitterMs;
    super.startPolling(pollingIntervalMs, pollingJitterMs);
  }

  public void pausePolling() {
    super.stopPolling();
  }

  public void resumePolling() {
    if (pollingIntervalMs <= 0) {
      Log.w(
          TAG,
          "resumePolling called, but polling was not started due to invalid polling interval.");
      return;
    }

    super.startPolling(pollingIntervalMs, pollingJitterMs);
  }
}
