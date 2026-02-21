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
import cloud.eppo.android.exceptions.MissingApiKeyException;
import cloud.eppo.android.exceptions.MissingApplicationException;
import cloud.eppo.android.exceptions.NotInitializedException;
import cloud.eppo.api.Configuration;
import cloud.eppo.api.IAssignmentCache;
import cloud.eppo.api.dto.FlagConfigResponse;
import cloud.eppo.http.EppoConfigurationClient;
import cloud.eppo.logging.AssignmentLogger;
import cloud.eppo.parser.ConfigurationParser;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Main Eppo client for feature flag evaluation.
 *
 * <p>This is the batteries-included implementation that extends {@link BaseEppoClient} with Jackson
 * for JSON parsing and OkHttp for HTTP operations.
 */
public class EppoClient extends BaseEppoClient<JsonNode> {
  private static final String TAG = logTag(EppoClient.class);
  private static final boolean DEFAULT_IS_GRACEFUL_MODE = true;
  private static final boolean DEFAULT_OBFUSCATE_CONFIG = true;
  private static final long DEFAULT_POLLING_INTERVAL_MS = 5 * 60 * 1000;
  private static final long DEFAULT_JITTER_INTERVAL_RATIO = 10;

  private long pollingIntervalMs, pollingJitterMs;

  @Nullable private static EppoClient instance;

  private EppoClient(
      String apiKey,
      String sdkName,
      String sdkVersion,
      @Nullable String apiBaseUrl,
      @Nullable AssignmentLogger assignmentLogger,
      IConfigurationStore configurationStore,
      boolean isGracefulMode,
      boolean obfuscateConfig,
      @Nullable CompletableFuture<Configuration> initialConfiguration,
      @Nullable IAssignmentCache assignmentCache,
      @NonNull ConfigurationParser<JsonNode> configurationParser,
      @NonNull EppoConfigurationClient configurationClient) {
    super(
        apiKey,
        sdkName,
        sdkVersion,
        apiBaseUrl,
        assignmentLogger,
        null, // banditLogger
        configurationStore,
        isGracefulMode,
        obfuscateConfig,
        false, // supportBandits
        initialConfiguration,
        assignmentCache,
        null, // banditAssignmentCache
        configurationParser,
        configurationClient);
  }

  /**
   * @noinspection unused
   */
  public static EppoClient init(
      @NonNull Application application,
      @NonNull String apiKey,
      @Nullable String host,
      @Nullable String apiBaseUrl,
      @Nullable AssignmentLogger assignmentLogger,
      boolean isGracefulMode) {
    return new Builder(apiKey, application)
        .apiBaseUrl(apiBaseUrl)
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
    return new Builder(apiKey, application)
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

  /** (Re)loads flag and experiment configuration from the API server. */
  @Override
  public void loadConfiguration() {
    super.loadConfiguration();
  }

  /** Asynchronously (re)loads flag and experiment configuration from the API server. */
  @Override
  public CompletableFuture<Void> loadConfigurationAsync() {
    return super.loadConfigurationAsync();
  }

  public static class Builder {
    private String apiBaseUrl;
    private final Application application;
    private final String apiKey;
    @Nullable private AssignmentLogger assignmentLogger;
    @Nullable private ConfigurationStore configStore;
    private boolean isGracefulMode = DEFAULT_IS_GRACEFUL_MODE;
    private boolean obfuscateConfig = DEFAULT_OBFUSCATE_CONFIG;
    private boolean forceReinitialize = false;
    private boolean offlineMode = false;
    private CompletableFuture<Configuration> initialConfiguration;
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
    @Nullable private Consumer<Configuration> configChangeCallback;

    // Optional custom implementations (defaults provided)
    @Nullable private ConfigurationParser<JsonNode> configurationParser;
    @Nullable private EppoConfigurationClient configurationClient;

    // Raw bytes for initial configuration (parsed at build time)
    @Nullable private byte[] initialConfigurationBytes;
    @Nullable private CompletableFuture<byte[]> initialConfigurationBytesFuture;

    public Builder(@NonNull String apiKey, @NonNull Application application) {
      this.application = application;
      this.apiKey = apiKey;
    }

    /**
     * @deprecated Use {@link #apiBaseUrl(String)} instead. Host parameter is no longer used.
     */
    @Deprecated
    public Builder host(@Nullable String host) {
      // Ignored - host is no longer used in v4
      return this;
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

    public Builder assignmentCache(IAssignmentCache assignmentCache) {
      this.assignmentCache = assignmentCache;
      return this;
    }

    /**
     * Sets the initial configuration from raw flag config bytes.
     *
     * <p>Note: In v4, the bytes will be parsed using the ConfigurationParser.
     */
    public Builder initialConfiguration(byte[] initialFlagConfigResponse) {
      // Store bytes to be parsed at build time when we have the parser
      this.initialConfigurationBytes = initialFlagConfigResponse;
      return this;
    }

    /**
     * Sets the initial configuration from a future that resolves to raw flag config bytes.
     *
     * <p>Note: In v4, the bytes will be parsed using the ConfigurationParser.
     */
    public Builder initialConfiguration(CompletableFuture<byte[]> initialFlagConfigResponse) {
      // Store future to be parsed at build time when we have the parser
      this.initialConfigurationBytesFuture = initialFlagConfigResponse;
      return this;
    }

    public Builder configStore(ConfigurationStore configStore) {
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
    public Builder onConfigurationChange(Consumer<Configuration> configChangeCallback) {
      this.configChangeCallback = configChangeCallback;
      return this;
    }

    /**
     * Sets a custom configuration parser. If not set, uses JacksonConfigurationParser.
     *
     * @param parser The parser to use for configuration responses
     * @return This builder
     */
    public Builder configurationParser(ConfigurationParser<JsonNode> parser) {
      this.configurationParser = parser;
      return this;
    }

    /**
     * Sets a custom HTTP client. If not set, uses OkHttpConfigurationClient.
     *
     * @param client The HTTP client to use for configuration requests
     * @return This builder
     */
    public Builder configurationClient(EppoConfigurationClient client) {
      this.configurationClient = client;
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
        // Stop polling (if the client is polling for configuration)
        instance.stopPolling();

        // Always recreate for tests
        Log.d(TAG, "`forceReinitialize` triggered reinitializing Eppo Client");
      }

      String sdkName = obfuscateConfig ? "android" : "android-debug";
      String sdkVersion = BuildConfig.EPPO_VERSION;

      // Create default implementations if not provided
      ConfigurationParser<JsonNode> parser =
          configurationParser != null ? configurationParser : new JacksonConfigurationParser();
      EppoConfigurationClient httpClient =
          configurationClient != null ? configurationClient : new OkHttpConfigurationClient();

      // Get caching from config store
      if (configStore == null) {
        // Cache at a per-API key level (useful for development)
        String cacheFileNameSuffix = safeCacheKey(apiKey);
        configStore = new ConfigurationStore(application, cacheFileNameSuffix, parser);
      }

      // Parse initial configuration bytes if provided
      if (initialConfigurationBytes != null) {
        initialConfiguration = parseInitialConfiguration(parser, initialConfigurationBytes);
      } else if (initialConfigurationBytesFuture != null) {
        // For future-based bytes, chain the parsing
        final ConfigurationParser<JsonNode> finalParser = parser;
        initialConfiguration =
            initialConfigurationBytesFuture.thenApply(
                bytes -> {
                  CompletableFuture<Configuration> parsedFuture =
                      parseInitialConfiguration(finalParser, bytes);
                  return parsedFuture.join();
                });
      }

      // If the initial config was not set, use the ConfigurationStore's cache as the initial
      // config.
      if (initialConfiguration == null && !ignoreCachedConfiguration) {
        initialConfiguration = configStore.loadConfigFromCache();
      }

      instance =
          new EppoClient(
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
              parser,
              httpClient);

      if (configChangeCallback != null) {
        instance.onConfigurationChange(configChangeCallback);
      }

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
                        new EppoInitializationException(
                            "Unable to initialize client; Configuration could not be loaded", ex));
                  }
                  return null;
                });
      }

      // Start polling, if configured.
      if (pollingEnabled && pollingIntervalMs > 0) {
        Log.d(TAG, "Starting poller");
        if (pollingJitterMs < 0) {
          pollingJitterMs = pollingIntervalMs / DEFAULT_JITTER_INTERVAL_RATIO;
        }

        instance.startPolling(pollingIntervalMs, pollingJitterMs);
      }

      if (instance.getInitialConfigFuture() != null) {
        instance
            .getInitialConfigFuture()
            .handle(
                (success, ex) -> {
                  if (ex == null && Boolean.TRUE.equals(success)) {
                    ret.complete(instance);
                  } else if (offlineMode || failCount.incrementAndGet() == 2) {
                    ret.completeExceptionally(
                        new EppoInitializationException(
                            "Unable to initialize client; Configuration could not be loaded",
                            ex instanceof Throwable ? (Throwable) ex : null));
                  } else {
                    Log.d(TAG, "Initial config was not used.");
                    failCount.incrementAndGet();
                  }
                  return null;
                });
      }
      return ret.exceptionally(
          e -> {
            Log.e(TAG, "Exception caught during initialization: " + e.getMessage(), e);
            if (!isGracefulMode) {
              throw new RuntimeException(e);
            }
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

    /**
     * Parses raw configuration bytes into a Configuration using the provided parser.
     *
     * @param parser The configuration parser to use
     * @param bytes The raw configuration bytes
     * @return A CompletableFuture containing the parsed Configuration
     */
    private CompletableFuture<Configuration> parseInitialConfiguration(
        ConfigurationParser<JsonNode> parser, byte[] bytes) {
      try {
        FlagConfigResponse flagConfig = parser.parseFlagConfig(bytes);
        Configuration config = Configuration.builder(flagConfig).build();
        return CompletableFuture.completedFuture(config);
      } catch (Exception e) {
        CompletableFuture<Configuration> failed = new CompletableFuture<>();
        failed.completeExceptionally(e);
        return failed;
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
