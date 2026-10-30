package cloud.eppo.android.framework;

import static cloud.eppo.android.framework.util.Utils.logTag;

import android.app.Application;
import android.util.Log;
import cloud.eppo.BaseEppoClient;
import cloud.eppo.android.framework.exceptions.EppoInitializationException;
import cloud.eppo.android.framework.exceptions.NotInitializedException;
import cloud.eppo.android.framework.storage.CachingConfigurationStore;
import cloud.eppo.api.IAssignmentCache;
import cloud.eppo.api.SerializableEppoConfiguration;
import cloud.eppo.http.EppoConfigurationClient;
import cloud.eppo.logging.AssignmentLogger;
import cloud.eppo.parser.ConfigurationParser;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
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
public class AndroidBaseClient<
        ConfigurationType extends SerializableEppoConfiguration, JsonFlagType>
    extends BaseEppoClient<ConfigurationType, JsonFlagType> {
  private static final String TAG = logTag(AndroidBaseClient.class);
  private static final boolean DEFAULT_IS_GRACEFUL_MODE = true;
  private static final boolean DEFAULT_OBFUSCATE_CONFIG = true;
  private static final long DEFAULT_POLLING_INTERVAL_MS = 5 * 60 * 1000;
  private static final long DEFAULT_JITTER_INTERVAL_RATIO = 10;

  long pollingIntervalMs;
  long pollingJitterMs;

  @Nullable private static volatile AndroidBaseClient<?, ?> instance;
  private static final Object INITIALIZATION_LOCK = new Object();
  @Nullable private static InitializationAttempt initialization;

  private static final class InitializationAttempt {
    private final Class<?> builderClass;
    private final CompletableFuture<AndroidBaseClient<?, ?>> result = new CompletableFuture<>();
    @Nullable private AndroidBaseClient<?, ?> candidate;

    private InitializationAttempt(Class<?> builderClass) {
      this.builderClass = builderClass;
    }
  }

  /**
   * Private constructor. Use Builder to construct instances.
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
  protected AndroidBaseClient(
      String apiKey,
      String sdkName,
      String sdkVersion,
      @Nullable String apiBaseUrl,
      @Nullable AssignmentLogger assignmentLogger,
      CachingConfigurationStore<ConfigurationType> configurationStore,
      boolean isGracefulMode,
      boolean expectObfuscatedConfig,
      @Nullable CompletableFuture<ConfigurationType> initialConfiguration,
      @Nullable IAssignmentCache assignmentCache,
      ConfigurationParser<ConfigurationType, JsonFlagType> configurationParser,
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
   * Gets the singleton instance of EppoClient.
   *
   * @return The singleton instance
   * @throws NotInitializedException if the client has not been initialized
   * @param <ConfigurationType> The Configuration type parameter
   * @param <ConfigurationBuilderType> The Configuration Builder type parameter
   * @param <JsonFlagType> The JSON type parameter
   */
  @SuppressWarnings("unchecked")
  public static <ConfigurationType extends SerializableEppoConfiguration, JsonFlagType>
      AndroidBaseClient<ConfigurationType, JsonFlagType> getInstance()
          throws NotInitializedException {
    AndroidBaseClient<?, ?> current = instance;
    if (current == null) {
      throw new NotInitializedException();
    }
    return (AndroidBaseClient<ConfigurationType, JsonFlagType>) current;
  }

  /**
   * Builder for constructing and initializing EppoClient instances.
   *
   * <p>This is the only way to create an EppoClient. The Builder is generic on JsonFlagType and
   * builds an EppoClient with the same type parameter.
   *
   * @param <JsonFlagType> The JSON type used for JSON flag values
   */
  public abstract static class Builder<
      SelfType extends Builder<SelfType, AndroidBaseClientType, ConfigurationType, JsonFlagType>,
      AndroidBaseClientType extends AndroidBaseClient<ConfigurationType, JsonFlagType>,
      ConfigurationType extends SerializableEppoConfiguration,
      JsonFlagType> {
    // Required parameters
    protected final Class<SelfType> selfClass;
    protected final String apiKey;
    protected final Application application;
    protected final ConfigurationParser<ConfigurationType, JsonFlagType> configurationParser;
    protected final CachingConfigurationStore<ConfigurationType> configStore;
    protected final EppoConfigurationClient configurationClient;

    // Optional parameters with defaults
    @Nullable protected String apiBaseUrl;
    @Nullable protected AssignmentLogger assignmentLogger;
    protected boolean isGracefulMode = DEFAULT_IS_GRACEFUL_MODE;
    protected boolean obfuscateConfig = DEFAULT_OBFUSCATE_CONFIG;
    protected boolean forceReinitialize = false;
    protected boolean offlineMode = false;
    @Nullable protected CompletableFuture<ConfigurationType> initialConfiguration;
    protected boolean ignoreCachedConfiguration = false;
    protected boolean pollingEnabled = false;
    protected long pollingIntervalMs = DEFAULT_POLLING_INTERVAL_MS;
    protected long pollingJitterMs = -1;
    @Nullable protected IAssignmentCache assignmentCache;
    @Nullable protected Consumer<ConfigurationType> configChangeCallback;

    /**
     * Creates a new Builder with required parameters.
     *
     * @param selfClass The class of the instance you're instantiating so that builder methods can
     *     return the right type. This is only for sublcasses.
     * @param apiKey API key for Eppo (required)
     * @param application Application context (required)
     * @param configurationParser Parser for configuration JSON (required)
     * @param configStore Store for configurations (required)
     * @param configurationClient HTTP client for configuration fetching (required)
     */
    protected Builder(
        @NotNull Class<SelfType> selfClass,
        @NotNull String apiKey,
        @NotNull Application application,
        @NotNull ConfigurationParser<ConfigurationType, JsonFlagType> configurationParser,
        @NotNull CachingConfigurationStore<ConfigurationType> configStore,
        @NotNull EppoConfigurationClient configurationClient) {
      if (selfClass == null) {
        throw new IllegalArgumentException("Missing self class. Bad subclass");
      }
      if (apiKey == null) {
        throw new IllegalArgumentException("Missing API Key");
      }
      if (application == null) {
        throw new IllegalArgumentException("Missing Application");
      }
      if (configurationParser == null) {
        throw new IllegalArgumentException("Missing ConfigurationParser");
      }
      if (configStore == null) {
        throw new IllegalArgumentException("Missing CachingConfigurationStore");
      }
      if (configurationClient == null) {
        throw new IllegalArgumentException("Missing EppoConfigurationClient");
      }
      this.selfClass = selfClass;
      this.apiKey = apiKey;
      this.application = application;
      this.configurationParser = configurationParser;
      this.configStore = configStore;
      this.configurationClient = configurationClient;
    }

    public SelfType apiBaseUrl(@Nullable String apiBaseUrl) {
      this.apiBaseUrl = apiBaseUrl;
      return selfClass.cast(this);
    }

    public SelfType assignmentLogger(@Nullable AssignmentLogger assignmentLogger) {
      this.assignmentLogger = assignmentLogger;
      return selfClass.cast(this);
    }

    public SelfType isGracefulMode(boolean isGracefulMode) {
      this.isGracefulMode = isGracefulMode;
      return selfClass.cast(this);
    }

    public SelfType obfuscateConfig(boolean obfuscateConfig) {
      this.obfuscateConfig = obfuscateConfig;
      return selfClass.cast(this);
    }

    public SelfType forceReinitialize(boolean forceReinitialize) {
      this.forceReinitialize = forceReinitialize;
      return selfClass.cast(this);
    }

    public SelfType offlineMode(boolean offlineMode) {
      this.offlineMode = offlineMode;
      return selfClass.cast(this);
    }

    public SelfType initialConfiguration(
        @Nullable CompletableFuture<ConfigurationType> initialConfiguration) {
      this.initialConfiguration = initialConfiguration;
      return selfClass.cast(this);
    }

    public SelfType ignoreCachedConfiguration(boolean ignoreCache) {
      this.ignoreCachedConfiguration = ignoreCache;
      return selfClass.cast(this);
    }

    public SelfType pollingEnabled(boolean pollingEnabled) {
      this.pollingEnabled = pollingEnabled;
      return selfClass.cast(this);
    }

    public SelfType pollingIntervalMs(long pollingIntervalMs) {
      this.pollingIntervalMs = pollingIntervalMs;
      return selfClass.cast(this);
    }

    public SelfType pollingJitterMs(long pollingJitterMs) {
      this.pollingJitterMs = pollingJitterMs;
      return selfClass.cast(this);
    }

    public SelfType assignmentCache(@Nullable IAssignmentCache assignmentCache) {
      this.assignmentCache = assignmentCache;
      return selfClass.cast(this);
    }

    public SelfType onConfigurationChange(
        @Nullable Consumer<ConfigurationType> configChangeCallback) {
      this.configChangeCallback = configChangeCallback;
      return selfClass.cast(this);
    }

    /**
     * For subclasses to initialize the proper type
     *
     * @see AndroidBaseClient constructor
     */
    protected abstract AndroidBaseClientType newInstance(
        String apiKey,
        String sdkName,
        String sdkVersion,
        @Nullable String apiBaseUrl,
        @Nullable AssignmentLogger assignmentLogger,
        CachingConfigurationStore<ConfigurationType> configurationStore,
        boolean isGracefulMode,
        boolean expectObfuscatedConfig,
        @Nullable CompletableFuture<ConfigurationType> initialConfiguration,
        @Nullable IAssignmentCache assignmentCache,
        ConfigurationParser<ConfigurationType, JsonFlagType> configurationParser,
        EppoConfigurationClient configurationClient);

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
     * <p>Repeated calls from the same concrete builder share initialization. A different builder
     * must use forceReinitialize to replace the singleton. Forced replacement cancels pending
     * callers of the previous attempt. A failed strict initialization can be retried; graceful
     * configuration failures complete with a usable fallback client. A concrete builder class must
     * use one consistent pair of configuration and JSON types.
     *
     * @return CompletableFuture that completes with the initialized EppoClient
     */
    public CompletableFuture<AndroidBaseClientType> buildAndInitAsync() {
      final InitializationAttempt attempt;
      final InitializationAttempt previous;
      final AndroidBaseClient<?, ?> previousClient;
      synchronized (INITIALIZATION_LOCK) {
        if (initialization != null && !forceReinitialize) {
          if (initialization.builderClass != selfClass) {
            CompletableFuture<AndroidBaseClientType> failed = new CompletableFuture<>();
            failed.completeExceptionally(
                new IllegalStateException(
                    "A different client builder owns the singleton; use forceReinitialize to replace it"));
            return failed;
          }
          return resultFor(initialization);
        }
        previous = initialization;
        previousClient = previous == null ? null : previous.candidate;
        attempt = new InitializationAttempt(selfClass);
        initialization = attempt;
        instance = null;
      }
      try {
        if (previousClient != null) {
          previousClient.stopPolling();
        }
        initialize(attempt);
      } catch (Throwable failure) {
        finish(attempt, null, failure);
      } finally {
        // Start the replacement before notifying callbacks that may join its initialization.
        if (previous != null) {
          previous.result.completeExceptionally(
              new CancellationException("Initialization superseded by forceReinitialize"));
        }
      }
      return resultFor(attempt);
    }

    // A dependent future prevents one caller's cancellation from cancelling the shared attempt.
    @SuppressWarnings("unchecked")
    private CompletableFuture<AndroidBaseClientType> resultFor(InitializationAttempt attempt) {
      return attempt.result.thenApply(client -> (AndroidBaseClientType) client);
    }

    private void finish(
        InitializationAttempt attempt,
        @Nullable AndroidBaseClientType client,
        @Nullable Throwable failure) {
      Throwable outcome = failure;
      synchronized (INITIALIZATION_LOCK) {
        if (initialization != attempt) {
          outcome = new CancellationException("Initialization superseded by forceReinitialize");
        } else {
          if (outcome == null) {
            try {
              if (pollingEnabled && pollingIntervalMs > 0) {
                long jitter =
                    pollingJitterMs < 0
                        ? pollingIntervalMs / DEFAULT_JITTER_INTERVAL_RATIO
                        : pollingJitterMs;
                client.pollingIntervalMs = pollingIntervalMs;
                client.pollingJitterMs = jitter;
                client.startPolling(pollingIntervalMs, jitter);
              }
              if (initialization == attempt) {
                instance = client;
              } else {
                outcome =
                    new CancellationException("Initialization superseded by forceReinitialize");
              }
            } catch (Throwable pollingFailure) {
              outcome = pollingFailure;
            }
          }
          if (outcome != null && initialization == attempt) {
            initialization = null;
            instance = null;
          }
        }
      }
      if (outcome == null) {
        attempt.result.complete(client);
      } else {
        // The failed/superseded client owns its own poller, never the replacement's.
        AndroidBaseClient<?, ?> failedClient = client != null ? client : attempt.candidate;
        if (failedClient != null) {
          try {
            failedClient.stopPolling();
          } catch (Throwable cleanupFailure) {
            outcome.addSuppressed(cleanupFailure);
          }
        }
        attempt.result.completeExceptionally(outcome);
      }
    }

    private void initialize(InitializationAttempt attempt) {
      String sdkName = obfuscateConfig ? "android" : "android-debug";
      String sdkVersion = BuildConfig.EPPO_VERSION;

      // Use the persisted cache as the initial configuration if none was explicitly provided.
      if (initialConfiguration == null && !ignoreCachedConfiguration) {
        initialConfiguration = configStore.loadFromStorage();
      }

      // Construct the client
      AndroidBaseClientType newInstance =
          newInstance(
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

      synchronized (INITIALIZATION_LOCK) {
        if (initialization != attempt) {
          newInstance.stopPolling();
          return;
        }
        attempt.candidate = newInstance;
      }

      // Register config change callback if provided
      if (configChangeCallback != null) {
        newInstance.onConfigurationChange(configChangeCallback);
      }

      final CompletableFuture<AndroidBaseClientType> ret = new CompletableFuture<>();
      AtomicInteger failCount = new AtomicInteger(0);

      if (!offlineMode) {
        newInstance
            .loadConfigurationAsync()
            .handle(
                (success, ex) -> {
                  if (ex == null) {
                    ret.complete(newInstance);
                  } else if (failCount.incrementAndGet() == 2
                      || newInstance.getInitialConfigFuture() == null) {
                    ret.completeExceptionally(
                        new EppoInitializationException(
                            "Unable to initialize client; Configuration could not be loaded", ex));
                  }
                  return null;
                });
      }

      if (newInstance.getInitialConfigFuture() != null) {
        newInstance
            .getInitialConfigFuture()
            .handle(
                (success, ex) -> {
                  if (ex == null && Boolean.TRUE.equals(success)) {
                    ret.complete(newInstance);
                  } else if (offlineMode || ex != null || failCount.incrementAndGet() == 2) {
                    ret.completeExceptionally(
                        new EppoInitializationException(
                            "Unable to initialize client; Configuration could not be loaded", ex));
                  } else {
                    Log.i(TAG, "Initial config was not used; waiting for fetch.");
                  }
                  return null;
                });
      } else if (offlineMode) {
        ret.complete(newInstance);
      }

      ret.exceptionally(
              e -> {
                Log.e(TAG, "Exception caught during initialization: " + e.getMessage(), e);
                if (!isGracefulMode) {
                  throw new RuntimeException(e);
                }
                return newInstance;
              })
          .whenComplete((client, failure) -> finish(attempt, client, failure));
    }

    /**
     * Builds and initializes the EppoClient synchronously (blocking).
     *
     * <p>This is a blocking wrapper around buildAndInitAsync().
     *
     * @return The initialized EppoClient
     */
    public AndroidBaseClient<ConfigurationType, JsonFlagType> buildAndInit() {
      try {
        return buildAndInitAsync().get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Interrupted while initializing client", e);
      } catch (ExecutionException e) {
        throw new RuntimeException("Unable to initialize client", e.getCause());
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
