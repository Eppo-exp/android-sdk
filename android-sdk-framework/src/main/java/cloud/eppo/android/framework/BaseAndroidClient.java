package cloud.eppo.android.framework;

import static cloud.eppo.android.framework.util.Utils.logTag;
import static cloud.eppo.android.framework.util.Utils.safeCacheKey;

import android.app.Application;
import android.util.Log;
import cloud.eppo.BaseEppoClient;
import cloud.eppo.android.framework.exceptions.EppoInitializationException;
import cloud.eppo.android.framework.storage.CachingConfigurationStore;
import cloud.eppo.android.framework.storage.ConfigurationCodec;
import cloud.eppo.android.framework.storage.FileBackedConfigStore;
import cloud.eppo.api.Configuration;
import cloud.eppo.api.IAssignmentCache;
import cloud.eppo.http.EppoConfigurationClient;
import cloud.eppo.logging.AssignmentLogger;
import cloud.eppo.parser.ConfigurationParser;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Generic EppoClient that extends BaseEppoClient with JSON type parameter.
 *
 * <p>Requires callers to provide implementations of ConfigurationParser and
 * EppoConfigurationClient.
 *
 * @param <JsonFlagType> The JSON type used for JSON flag values (e.g., JsonNode, JsonElement)
 */
public class BaseAndroidClient<JsonFlagType> extends BaseEppoClient<JsonFlagType> {
  private static final String TAG = logTag(BaseAndroidClient.class);
  private static final boolean DEFAULT_IS_GRACEFUL_MODE = true;
  private static final boolean DEFAULT_OBFUSCATE_CONFIG = true;
  private static final long DEFAULT_POLLING_INTERVAL_MS = 5 * 60 * 1000;
  private static final long DEFAULT_JITTER_INTERVAL_RATIO = 10;

  private volatile long pollingIntervalMs;
  private volatile long pollingJitterMs;

  /**
   * Protected constructor. Use Builder to construct instances.
   *
   * <p>Singleton management is intentionally absent from this class. Concrete subclasses (e.g.
   * EppoClient in :eppo) declare their own {@code static volatile} instance field and {@code
   * getInstance()} method. Framework-only consumers hold the reference returned by {@link
   * Builder#buildAndInitAsync()} directly — typically in the Application class.
   *
   * @param apiKey API key for Eppo
   * @param sdkName SDK name identifier
   * @param sdkVersion SDK version string
   * @param apiBaseUrl Base URL for API calls
   * @param assignmentLogger Logger for assignments
   * @param configurationStore Store for configuration persistence
   * @param isGracefulMode Whether to operate in graceful mode
   * @param expectObfuscatedConfig Whether configuration is obfuscated
   * @param initialConfiguration Initial configuration future
   * @param assignmentCache Cache for assignments
   * @param configurationParser Parser for configuration JSON
   * @param configurationClient HTTP client for configuration fetching
   */
  protected BaseAndroidClient(
      String apiKey,
      String sdkName,
      String sdkVersion,
      @Nullable String apiBaseUrl,
      @Nullable AssignmentLogger assignmentLogger,
      CachingConfigurationStore configurationStore,
      boolean isGracefulMode,
      boolean expectObfuscatedConfig,
      @Nullable CompletableFuture<Configuration> initialConfiguration,
      @Nullable IAssignmentCache assignmentCache,
      ConfigurationParser<JsonFlagType> configurationParser,
      EppoConfigurationClient configurationClient) {
    super(
        apiKey,
        sdkName,
        sdkVersion,
        apiBaseUrl,
        assignmentLogger,
        null, // banditLogger is not supported in Android
        configurationStore,
        isGracefulMode,
        expectObfuscatedConfig,
        false, // no bandits.
        initialConfiguration,
        assignmentCache,
        null,
        configurationParser,
        configurationClient);
  }

  /**
   * Builder for constructing and initializing EppoClient instances.
   *
   * <p>This is the only way to create an EppoClient. The Builder is generic on JsonFlagType and
   * builds an EppoClient with the same type parameter.
   *
   * @param <JsonFlagType> The JSON type used for JSON flag values
   */
  public static class Builder<JsonFlagType> {
    // Required parameters
    private final String apiKey;
    private final Application application;
    private final ConfigurationParser<JsonFlagType> configurationParser;
    private final EppoConfigurationClient configurationClient;

    // Optional parameters with defaults
    @Nullable private String apiBaseUrl;
    @Nullable private AssignmentLogger assignmentLogger;
    @Nullable private CachingConfigurationStore configStore;
    private boolean isGracefulMode = DEFAULT_IS_GRACEFUL_MODE;
    private boolean obfuscateConfig = DEFAULT_OBFUSCATE_CONFIG;
    private boolean forceReinitialize = false;
    private boolean offlineMode = false;
    @Nullable private CompletableFuture<Configuration> initialConfiguration;
    private boolean ignoreCachedConfiguration = false;
    private boolean pollingEnabled = false;
    private long pollingIntervalMs = DEFAULT_POLLING_INTERVAL_MS;
    private long pollingJitterMs = -1;
    @Nullable private IAssignmentCache assignmentCache;
    @Nullable private Consumer<Configuration> configChangeCallback;
    // Set during buildAndInitAsync() once the instance is constructed, before any async work begins.
    // Used by buildAndInit() as a last-resort fallback so it never returns null in graceful mode.
    // Safety: if the BaseAndroidClient constructor itself throws, buildAndInitAsync() propagates a
    // RuntimeException synchronously (before returning a Future), so buildAndInit()'s
    // ExecutionException catch is never reached and builtInstance being null is not a concern.
    @Nullable private BaseAndroidClient<JsonFlagType> builtInstance;

    /**
     * Creates a new Builder with required parameters.
     *
     * @param apiKey API key for Eppo (required)
     * @param application Application context (required)
     * @param configurationParser Parser for configuration JSON (required)
     * @param configurationClient HTTP client for configuration fetching (required)
     */
    public Builder(
        @NotNull String apiKey,
        @NotNull Application application,
        @NotNull ConfigurationParser<JsonFlagType> configurationParser,
        @NotNull EppoConfigurationClient configurationClient) {
      if (apiKey == null || apiKey.isEmpty()) {
        throw new IllegalArgumentException("apiKey must not be null or empty");
      }
      if (application == null) {
        throw new IllegalArgumentException("application must not be null");
      }
      if (configurationParser == null) {
        throw new IllegalArgumentException("configurationParser must not be null");
      }
      if (configurationClient == null) {
        throw new IllegalArgumentException("configurationClient must not be null");
      }
      this.apiKey = apiKey;
      this.application = application;
      this.configurationParser = configurationParser;
      this.configurationClient = configurationClient;
    }

    public Builder<JsonFlagType> apiBaseUrl(@Nullable String apiBaseUrl) {
      this.apiBaseUrl = apiBaseUrl;
      return this;
    }

    public Builder<JsonFlagType> assignmentLogger(@Nullable AssignmentLogger assignmentLogger) {
      this.assignmentLogger = assignmentLogger;
      return this;
    }

    public Builder<JsonFlagType> configStore(@Nullable CachingConfigurationStore configStore) {
      this.configStore = configStore;
      return this;
    }

    public Builder<JsonFlagType> isGracefulMode(boolean isGracefulMode) {
      this.isGracefulMode = isGracefulMode;
      return this;
    }

    public Builder<JsonFlagType> obfuscateConfig(boolean obfuscateConfig) {
      this.obfuscateConfig = obfuscateConfig;
      return this;
    }

    public Builder<JsonFlagType> forceReinitialize(boolean forceReinitialize) {
      this.forceReinitialize = forceReinitialize;
      return this;
    }

    public Builder<JsonFlagType> offlineMode(boolean offlineMode) {
      this.offlineMode = offlineMode;
      return this;
    }

    public Builder<JsonFlagType> initialConfiguration(
        @Nullable CompletableFuture<Configuration> initialConfiguration) {
      this.initialConfiguration = initialConfiguration;
      return this;
    }

    public Builder<JsonFlagType> ignoreCachedConfiguration(boolean ignoreCache) {
      this.ignoreCachedConfiguration = ignoreCache;
      return this;
    }

    public Builder<JsonFlagType> pollingEnabled(boolean pollingEnabled) {
      this.pollingEnabled = pollingEnabled;
      return this;
    }

    public Builder<JsonFlagType> pollingIntervalMs(long pollingIntervalMs) {
      this.pollingIntervalMs = pollingIntervalMs;
      return this;
    }

    public Builder<JsonFlagType> pollingJitterMs(long pollingJitterMs) {
      this.pollingJitterMs = pollingJitterMs;
      return this;
    }

    public Builder<JsonFlagType> assignmentCache(@Nullable IAssignmentCache assignmentCache) {
      this.assignmentCache = assignmentCache;
      return this;
    }

    public Builder<JsonFlagType> onConfigurationChange(
        @Nullable Consumer<Configuration> configChangeCallback) {
      this.configChangeCallback = configChangeCallback;
      return this;
    }

    /**
     * Builds and initializes the EppoClient asynchronously.
     *
     * <p>This method performs the full initialization flow:
     *
     * <ol>
     *   <li>Validates required fields
     *   <li>Handles singleton/reinitialize logic
     *   <li>Loads initial configuration from cache if needed
     *   <li>Constructs the client
     *   <li>Fetches configuration if not in offline mode
     *   <li>Starts polling if enabled
     *   <li>Returns a CompletableFuture that completes when initialization is done
     * </ol>
     *
     * @return CompletableFuture that completes with the initialized EppoClient
     */
    public CompletableFuture<BaseAndroidClient<JsonFlagType>> buildAndInitAsync() {
      String sdkName = obfuscateConfig ? "android" : "android-debug";
      String sdkVersion = BuildConfig.EPPO_VERSION;

      if (configStore == null) {
        configStore =
            new FileBackedConfigStore(
                application,
                safeCacheKey(apiKey),
                new ConfigurationCodec.Default<>(Configuration.class));
      }

      // Use the persisted cache as the initial configuration if none was explicitly provided.
      // Also seed the in-memory cache so getConfiguration() returns the cached value immediately
      // rather than emptyConfig() while the network fetch is in-flight.
      if (initialConfiguration == null && !ignoreCachedConfiguration) {
        initialConfiguration =
            configStore
                .loadAndSeedFromStorage()
                .exceptionally(
                    ex -> {
                      // Storage failure is non-fatal: proceed without a cached config.
                      // Losing the cause here would make offline-mode failures opaque, so log.
                      Log.w(TAG, "Failed to load config from storage; starting without cache", ex);
                      return null;
                    });
      }

      // Construct the client
      final BaseAndroidClient<JsonFlagType> newInstance =
          new BaseAndroidClient<>(
              apiKey,
              sdkName,
              sdkVersion,
              apiBaseUrl,
              assignmentLogger,
              configStore,
              isGracefulMode,
              obfuscateConfig,
              initialConfiguration,
              assignmentCache,
              configurationParser,
              configurationClient);
      // Track the instance so buildAndInit()'s defensive catch can return it instead of null.
      this.builtInstance = newInstance;

      // Register config change callback if provided
      if (configChangeCallback != null) {
        newInstance.onConfigurationChange(configChangeCallback);
      }

      final CompletableFuture<BaseAndroidClient<JsonFlagType>> ret = new CompletableFuture<>();
      AtomicInteger failCount = new AtomicInteger(0);
      // Captures the HTTP exception so that when the initial-config future completes the
      // combined failure path can include the original network error as the cause.
      AtomicReference<Throwable> httpFailure = new AtomicReference<>();

      if (!offlineMode) {
        newInstance
            .loadConfigurationAsync()
            .handle(
                (success, ex) -> {
                  if (ex == null) {
                    ret.complete(newInstance);
                  } else {
                    httpFailure.set(ex);
                    if (failCount.incrementAndGet() == 2
                        || newInstance.getInitialConfigFuture() == null) {
                      ret.completeExceptionally(
                          new EppoInitializationException(
                              "Unable to initialize client; Configuration could not be loaded",
                              ex));
                    }
                  }
                  return null;
                });
      }

      // Start polling if configured
      if (pollingEnabled && pollingIntervalMs > 0) {
        Log.i(TAG, "Starting poller");
        long effectiveJitterMs = pollingJitterMs;
        if (effectiveJitterMs < 0) {
          effectiveJitterMs = pollingIntervalMs / DEFAULT_JITTER_INTERVAL_RATIO;
        }

        // Store interval/jitter on the instance so resumePolling() can restart with the same
        // values.
        newInstance.pollingIntervalMs = pollingIntervalMs;
        newInstance.pollingJitterMs = effectiveJitterMs;
        newInstance.startPolling(pollingIntervalMs, effectiveJitterMs);
      }

      if (newInstance.getInitialConfigFuture() != null) {
        newInstance
            .getInitialConfigFuture()
            .handle(
                (success, ex) -> {
                  if (ex == null && Boolean.TRUE.equals(success)) {
                    ret.complete(newInstance);
                  } else if (offlineMode || failCount.incrementAndGet() == 2) {
                    // When both the HTTP fetch and initial config load fail, prefer the HTTP
                    // exception as the cause since it is more actionable than a null or false
                    // result from the initial config handler.
                    Throwable cause = httpFailure.get() != null ? httpFailure.get() : ex;
                    ret.completeExceptionally(
                        new EppoInitializationException(
                            "Unable to initialize client; Configuration could not be loaded",
                            cause));
                  } else {
                    Log.i(TAG, "Initial config was not used.");
                  }
                  return null;
                });
      } else if (offlineMode) {
        ret.complete(newInstance);
      }

      return ret.exceptionally(
          e -> {
            Log.e(TAG, "Exception caught during initialization: " + e.getMessage(), e);
            if (!isGracefulMode) {
              throw new RuntimeException(e);
            }
            return newInstance;
          });
    }

    /**
     * Builds and initializes the EppoClient synchronously (blocking).
     *
     * <p>This is a blocking wrapper around buildAndInitAsync(). The underlying future has no
     * deadline: if the HTTP fetch stalls permanently (e.g. due to network unavailability with no
     * timeout configured on the HTTP client), this call blocks indefinitely. Callers that require a
     * bounded wait should use {@link #buildAndInitAsync()} with {@code
     * CompletableFuture.orTimeout()} (API 31+) or a timed {@code get(long, TimeUnit)}.
     *
     * @return The initialized EppoClient
     * @throws RuntimeException if initialization fails and {@code isGracefulMode} is false
     */
    public BaseAndroidClient<JsonFlagType> buildAndInit() {
      try {
        return buildAndInitAsync().get();
      } catch (InterruptedException e) {
        // Thread interruption is not a graceful-degradation scenario — the caller's thread
        // was cancelled while waiting for init. Always re-throw so the caller can handle it.
        Thread.currentThread().interrupt();
        throw new RuntimeException("Eppo client initialization interrupted", e);
      } catch (ExecutionException e) {
        // In graceful mode, buildAndInitAsync()'s .exceptionally() handler converts failures
        // into the client instance, so .get() should not throw ExecutionException. This catch
        // is a defensive fallback for unexpected .exceptionally() failures. Return the partially-
        // constructed instance (which returns defaults for all flag evaluations) rather than null.
        Log.e(TAG, "Exception caught during initialization: " + e.getMessage(), e);
        if (!isGracefulMode) {
          throw new RuntimeException(e);
        }
        return builtInstance;
      }
    }
  }

  /**
   * Pauses polling for configuration updates.
   *
   * <p>Can be resumed later with resumePolling().
   */
  public void pausePolling() {
    super.stopPolling();
  }

  /**
   * Resumes polling for configuration updates.
   *
   * <p>Only works if polling was previously started via Builder.
   */
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
