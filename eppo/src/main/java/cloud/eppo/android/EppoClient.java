package cloud.eppo.android;

import static cloud.eppo.android.util.AndroidUtils.logTag;
import static cloud.eppo.android.util.AndroidUtils.safeCacheKey;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cloud.eppo.BaseEppoClient;
import cloud.eppo.IConfigurationStore;
import cloud.eppo.Utils;
import cloud.eppo.android.cache.LRUAssignmentCache;
import cloud.eppo.android.exceptions.NotInitializedException;
import cloud.eppo.android.util.AndroidJsonParser;
import cloud.eppo.android.util.AndroidUtils;
import cloud.eppo.api.Attributes;
import cloud.eppo.api.Configuration;
import cloud.eppo.api.EppoActionCallback;
import cloud.eppo.api.EppoValue;
import cloud.eppo.api.IAssignmentCache;
import cloud.eppo.logging.AssignmentLogger;
import cloud.eppo.ufc.dto.VariationType;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONException;
import org.json.JSONObject;

public class EppoClient extends BaseEppoClient {
  private static final String TAG = logTag(EppoClient.class);
  private static final boolean DEFAULT_IS_GRACEFUL_MODE = true;
  private static final long DEFAULT_POLLING_INTERVAL_MS = 5 * 60 * 1000;
  private static final long DEFAULT_JITTER_INTERVAL_RATIO = 10;

  private long pollingIntervalMs, pollingJitterMs;

  @Nullable private static EppoClient instance;

  // Provide a base64 codec based on Androids base64 util.
  static {
    Utils.setBase64Codec(new AndroidUtils());
    Utils.setJsonDeserializer(new AndroidJsonParser());
  }

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
      boolean isGracefulMode,
      long timeoutMs) {
    return new Builder(sdkKey, application)
        .apiBaseUrl(apiBaseUrl)
        .assignmentLogger(assignmentLogger)
        .isGracefulMode(isGracefulMode)
        .buildAndInit(timeoutMs <= 0 ? 5000 : timeoutMs);
  }

  /**
   * @noinspection unused
   */
  public static EppoClient initAsync(
      @NonNull Application application,
      @NonNull String sdkKey,
      @NonNull String apiBaseUrl,
      @Nullable AssignmentLogger assignmentLogger,
      boolean isGracefulMode,
      @NonNull EppoActionCallback<EppoClient> onInitializedCallback) {
    return new Builder(sdkKey, application)
        .apiBaseUrl(apiBaseUrl)
        .assignmentLogger(assignmentLogger)
        .isGracefulMode(isGracefulMode)
        .buildAndInitAsync(onInitializedCallback);
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

  public JSONObject getJSONAssignment(String flagKey, String subjectKey, JSONObject defaultValue) {
    String result = super.getJSONStringAssignment(flagKey, subjectKey, defaultValue.toString());
    return getJsonObject(defaultValue, result);
  }

  public JSONObject getJSONAssignment(
      String flagKey, String subjectKey, Attributes subjectAttributes, JSONObject defaultValue) {
    String result =
        super.getJSONStringAssignment(
            flagKey, subjectKey, subjectAttributes, defaultValue.toString());
    return getJsonObject(defaultValue, result);
  }

  private JSONObject getJsonObject(JSONObject defaultValue, String result) {
    try {
      return new JSONObject(result);
    } catch (JSONException e) {

      return throwIfNotGraceful(e, defaultValue);
    }
  }

  /** (Re)loads flag and experiment configuration from the API server. */
  @Override
  public void fetchAndActivateConfiguration() {
    super.fetchAndActivateConfiguration();
  }

  /** (Re)loads flag and experiment configuration from the API server. */
  @Override
  public void fetchAndActivateConfigurationAsync(EppoActionCallback<Configuration> callback) {
    super.fetchAndActivateConfigurationAsync(callback);
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

    private static class GracefulInitCallback implements EppoActionCallback<EppoClient> {
      private final EppoActionCallback<EppoClient> wrappedCallback;
      private final boolean isGracefulMode;
      private final EppoClient fallback;

      public GracefulInitCallback(
          EppoActionCallback<EppoClient> wrappedCallback,
          EppoClient fallbackInstance,
          boolean isGracefulMode) {
        this.isGracefulMode = isGracefulMode;
        this.wrappedCallback = wrappedCallback;
        this.fallback = fallbackInstance;
      }

      @Override
      public void onSuccess(EppoClient data) {
        wrappedCallback.onSuccess(data);
      }

      @Override
      public void onFailure(Throwable error) {
        Log.e(TAG, "Exception caught during initialization: " + error.getMessage(), error);
        if (!isGracefulMode) {

          wrappedCallback.onFailure(error);
        } else {

          fallback.activateConfiguration(Configuration.emptyConfig());
          wrappedCallback.onSuccess(fallback);
        }
      }
    }

    public EppoClient buildAndInitAsync(EppoActionCallback<EppoClient> onInitializedCallback) {

      if (instance != null && !forceReinitialize) {
        Log.w(TAG, "Eppo Client instance already initialized");
        onInitializedCallback.onSuccess(instance);
        return instance;
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

      GracefulInitCallback initCallback =
          new GracefulInitCallback(onInitializedCallback, instance, isGracefulMode);

      if (configChangeCallback != null) {
        instance.onConfigurationChange(configChangeCallback);
      }

      // Early return for offline mode.
      if (offlineMode) {
        // Offline mode means initializing without making/waiting on any fetches or polling.
        // Note: breaking change
        if (pollingEnabled) {
          Log.w(TAG, "Ignoring pollingEnabled parameter as offlineMode is set to true");
        }
        // If there is not an `initialConfiguration`, attempt to load from the cache, or use an
        // empty config.
        if (initialConfiguration == null) {
          if (!ignoreCachedConfiguration) {
            configStore.loadConfigFromCacheAsync(
                config -> {
                  instance.activateConfiguration(
                      config != null ? config : Configuration.emptyConfig());
                  initCallback.onSuccess(instance);
                });

          } else {
            // No initial config, offline mode, and ignore cache means bootstrap with an empty
            // config
            instance.activateConfiguration(Configuration.emptyConfig());
            initCallback.onSuccess(instance);
          }
        } else {
          initCallback.onSuccess(instance);
        }
        return instance;
      }

      AtomicInteger failCount = new AtomicInteger(0);

      // Not in offline mode. We'll kick off a fetch and attempt to load from the cache async.
      AtomicBoolean configLoaded = new AtomicBoolean(false);

      configStore.loadConfigFromCacheAsync(
          configuration -> {
            if (configuration != null && !configuration.isEmpty()) {
              if (!configLoaded.getAndSet(true)) {
                // Config is not null, not empty and has not yet been set so set this one.
                instance.activateConfiguration(configuration);
                initCallback.onSuccess(instance);
              } // else config has already been set
            } else {
              if (failCount.incrementAndGet() == 2) {
                initCallback.onFailure(
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
                initCallback.onSuccess(instance);
              }
            }

            @Override
            public void onFailure(Throwable error) {
              // If the local load already failed, throw an error
              if (failCount.incrementAndGet() == 2) {
                initCallback.onFailure(
                    new EppoInitializationException(
                        "Unable to initialize client; Configuration could not be loaded", null));
              }
            }
          });

      // Start polling, if configured.
      if (pollingEnabled && pollingIntervalMs > 0) {
        Log.d(TAG, "Starting poller");
        if (pollingJitterMs < 0) {
          pollingJitterMs = pollingIntervalMs / DEFAULT_JITTER_INTERVAL_RATIO;
        }
        instance.startPolling(pollingIntervalMs, pollingJitterMs);
      }
      return instance;
    }

    /** Builds and initializes an `EppoClient`, immediately available to compute assignments. */
    public EppoClient buildAndInit() {
      return buildAndInit(5000);
    }

    public EppoClient buildAndInit(long timeoutMs) {
      final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

      // Using 1 element arrays as a shortcut for passing results back to the main thread
      // (AtomicReference would also work)
      final EppoClient[] resultClient = new EppoClient[1];
      final Throwable[] resultError = new Throwable[1];

      buildAndInitAsync(
          new EppoActionCallback<EppoClient>() {
            @Override
            public void onSuccess(EppoClient data) {
              resultClient[0] = data;
              latch.countDown();
            }

            @Override
            public void onFailure(Throwable error) {
              resultError[0] = error;
              latch.countDown();
            }
          });

      try {
        // Wait for initialization to complete with timeout
        if (!latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
          // Check for graceful mode here.
          if (isGracefulMode) {
            Log.e(
                TAG,
                "Timed out waiting for Eppo initialization, using empty config",
                new RuntimeException("Initialization timeout"));
            if (instance != null) {
              instance.activateConfiguration(Configuration.emptyConfig());
              return instance;
            }
          }
          throw new RuntimeException("Timed out waiting for Eppo initialization");
        }

        if (resultError[0] != null) {
          if (isGracefulMode) {
            Log.e(TAG, "Failed to initialize Eppo, using empty config", resultError[0]);
            if (instance != null) {
              instance.activateConfiguration(Configuration.emptyConfig());
              return instance;
            }
          }

          if (resultError[0] instanceof RuntimeException) {
            throw (RuntimeException) resultError[0];
          }
          throw new RuntimeException("Failed to initialize Eppo", resultError[0]);
        }

        return resultClient[0];
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        // Check graceful mode here.
        if (isGracefulMode) {
          Log.e(TAG, "Interrupted while waiting for Eppo initialization, using empty config", e);
          if (instance != null) {
            instance.activateConfiguration(Configuration.emptyConfig());
            return instance;
          }
        }
        throw new RuntimeException("Interrupted while waiting for Eppo initialization", e);
      }
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
