package cloud.eppo.android;

import static cloud.eppo.android.util.Utils.logTag;
import static cloud.eppo.android.util.Utils.safeCacheKey;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cloud.eppo.android.api.PrecomputedConfigParser;
import cloud.eppo.android.api.PrecomputedParseException;
import cloud.eppo.android.cache.LRUAssignmentCache;
import cloud.eppo.android.dto.PrecomputedConfigurationResponse;
import cloud.eppo.android.exceptions.MissingApiKeyException;
import cloud.eppo.android.exceptions.MissingApplicationException;
import cloud.eppo.android.exceptions.MissingSubjectKeyException;
import cloud.eppo.android.exceptions.NotInitializedException;
import cloud.eppo.android.util.ContextAttributesSerializer;
import cloud.eppo.android.util.ObfuscationUtils;
import cloud.eppo.android.util.Utils;
import cloud.eppo.api.Attributes;
import cloud.eppo.api.IAssignmentCache;
import cloud.eppo.logging.AssignmentLogger;
import cloud.eppo.logging.BanditLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import okhttp3.OkHttpClient;

/**
 * Precomputed client for Eppo feature flags. All flag assignments are computed server-side and
 * delivered as a batch, providing instant lookups with zero client-side evaluation time.
 *
 * <p>This is the batteries-included implementation that extends {@link BasePrecomputedClient} with
 * Jackson for JSON parsing and OkHttp for HTTP operations.
 */
public class EppoPrecomputedClient extends BasePrecomputedClient<JsonNode> {
  private static final String TAG = logTag(EppoPrecomputedClient.class);
  private static final boolean DEFAULT_IS_GRACEFUL_MODE = true;
  private static final long DEFAULT_POLLING_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes
  private static final long DEFAULT_JITTER_INTERVAL_RATIO = 10;
  private static final String DEFAULT_EDGE_HOST = "fs-edge-assignment.eppo.cloud";
  // Hash prefix length for cache file naming; 8 hex chars = 32 bits of entropy
  private static final int SUBJECT_KEY_HASH_LENGTH = 8;
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Nullable private static EppoPrecomputedClient instance;

  private EppoPrecomputedClient(
      String apiKey,
      String subjectKey,
      @Nullable Attributes subjectAttributes,
      @Nullable Map<String, Map<String, Attributes>> banditActions,
      @Nullable AssignmentLogger assignmentLogger,
      @Nullable BanditLogger banditLogger,
      @Nullable IAssignmentCache assignmentCache,
      @Nullable IAssignmentCache banditCache,
      PrecomputedConfigurationStore configurationStore,
      boolean isGracefulMode,
      String baseUrl,
      OkHttpClient httpClient,
      PrecomputedConfigParser<JsonNode> configParser) {
    super(
        apiKey,
        subjectKey,
        subjectAttributes,
        banditActions,
        assignmentLogger,
        banditLogger,
        assignmentCache,
        banditCache,
        configurationStore,
        isGracefulMode,
        baseUrl,
        httpClient,
        configParser);
  }

  /**
   * Returns the singleton instance of the client.
   *
   * @throws NotInitializedException if the client has not been initialized
   */
  public static EppoPrecomputedClient getInstance() throws NotInitializedException {
    if (EppoPrecomputedClient.instance == null) {
      throw new NotInitializedException();
    }
    return EppoPrecomputedClient.instance;
  }

  @Override
  protected byte[] buildRequestBody() {
    try {
      Map<String, Object> body = new HashMap<>();
      body.put("subject_key", subjectKey);
      body.put("subject_attributes", ContextAttributesSerializer.serialize(subjectAttributes));

      if (banditActions != null && !banditActions.isEmpty()) {
        // Transform banditActions to match the expected wire format with numericAttributes and
        // categoricalAttributes (same structure as subject_attributes)
        Map<String, Map<String, Map<String, Object>>> serializedBanditActions = new HashMap<>();
        for (Map.Entry<String, Map<String, Attributes>> flagEntry : banditActions.entrySet()) {
          Map<String, Map<String, Object>> actionsForFlag = new HashMap<>();
          for (Map.Entry<String, Attributes> actionEntry : flagEntry.getValue().entrySet()) {
            actionsForFlag.put(
                actionEntry.getKey(), ContextAttributesSerializer.serialize(actionEntry.getValue()));
          }
          serializedBanditActions.put(flagEntry.getKey(), actionsForFlag);
        }
        body.put("bandit_actions", serializedBanditActions);
      }

      return objectMapper.writeValueAsBytes(body);
    } catch (Exception e) {
      Log.e(TAG, "Failed to build request body", e);
      // Return empty object as fallback
      return "{}".getBytes();
    }
  }

  // Builder class

  public static class Builder {
    private final String apiKey;
    private final Application application;
    @Nullable private String subjectKey;
    @Nullable private Attributes subjectAttributes;
    @Nullable private Map<String, Map<String, Attributes>> banditActions;
    @Nullable private AssignmentLogger assignmentLogger;
    @Nullable private BanditLogger banditLogger;
    private IAssignmentCache assignmentCache = new LRUAssignmentCache(100);
    @Nullable private IAssignmentCache banditCache;
    @Nullable private PrecomputedConfigurationStore configStore;
    private boolean isGracefulMode = DEFAULT_IS_GRACEFUL_MODE;
    private boolean forceReinitialize = false;
    private boolean offlineMode = false;
    private boolean pollingEnabled = false;
    private long pollingIntervalMs = DEFAULT_POLLING_INTERVAL_MS;
    private long pollingJitterMs = -1;
    @Nullable private String baseUrl;
    @Nullable private byte[] initialConfiguration;
    private boolean ignoreCachedConfiguration = false;
    @Nullable private OkHttpClient httpClient;
    @Nullable private PrecomputedConfigParser<JsonNode> configParser;

    public Builder(@NonNull String apiKey, @NonNull Application application) {
      this.apiKey = apiKey;
      this.application = application;
    }

    /** Sets the subject key (required). */
    public Builder subjectKey(@NonNull String subjectKey) {
      this.subjectKey = subjectKey;
      return this;
    }

    /** Sets the subject attributes (optional). */
    public Builder subjectAttributes(@Nullable Attributes subjectAttributes) {
      this.subjectAttributes = subjectAttributes;
      return this;
    }

    /** Sets the bandit actions (optional). */
    public Builder banditActions(@Nullable Map<String, Map<String, Attributes>> banditActions) {
      this.banditActions = banditActions;
      return this;
    }

    /** Sets the assignment logger (optional). */
    public Builder assignmentLogger(@Nullable AssignmentLogger assignmentLogger) {
      this.assignmentLogger = assignmentLogger;
      return this;
    }

    /** Sets the bandit logger (optional). */
    public Builder banditLogger(@Nullable BanditLogger banditLogger) {
      this.banditLogger = banditLogger;
      return this;
    }

    /** Sets the assignment cache (optional). Default is LRUAssignmentCache(100). */
    public Builder assignmentCache(@Nullable IAssignmentCache assignmentCache) {
      this.assignmentCache = assignmentCache;
      return this;
    }

    /** Sets the bandit cache (optional). */
    public Builder banditCache(@Nullable IAssignmentCache banditCache) {
      this.banditCache = banditCache;
      return this;
    }

    /** Sets the configuration store (optional). */
    public Builder configStore(@Nullable PrecomputedConfigurationStore configStore) {
      this.configStore = configStore;
      return this;
    }

    /** Sets graceful mode (optional). Default is true. */
    public Builder isGracefulMode(boolean isGracefulMode) {
      this.isGracefulMode = isGracefulMode;
      return this;
    }

    /** Forces reinitialization even if an instance already exists. */
    public Builder forceReinitialize(boolean forceReinitialize) {
      this.forceReinitialize = forceReinitialize;
      return this;
    }

    /** Sets offline mode (optional). Default is false. */
    public Builder offlineMode(boolean offlineMode) {
      this.offlineMode = offlineMode;
      return this;
    }

    /** Enables polling for configuration updates. */
    public Builder pollingEnabled(boolean pollingEnabled) {
      this.pollingEnabled = pollingEnabled;
      return this;
    }

    /** Sets the polling interval in milliseconds. Default is 5 minutes. */
    public Builder pollingIntervalMs(long pollingIntervalMs) {
      this.pollingIntervalMs = pollingIntervalMs;
      return this;
    }

    /** Sets the polling jitter in milliseconds. Default is 10% of polling interval. */
    public Builder pollingJitterMs(long pollingJitterMs) {
      this.pollingJitterMs = pollingJitterMs;
      return this;
    }

    /** Sets the base URL for the API. Default is the edge endpoint. */
    public Builder baseUrl(@NonNull String baseUrl) {
      this.baseUrl = baseUrl;
      return this;
    }

    /** Sets the initial configuration for offline mode. */
    public Builder initialConfiguration(@Nullable byte[] initialConfiguration) {
      this.initialConfiguration = initialConfiguration;
      return this;
    }

    /** Ignores cached configuration and always fetches fresh. */
    public Builder ignoreCachedConfiguration(boolean ignoreCachedConfiguration) {
      this.ignoreCachedConfiguration = ignoreCachedConfiguration;
      return this;
    }

    /** Sets a custom HTTP client (optional, for testing). */
    public Builder httpClient(@Nullable OkHttpClient httpClient) {
      this.httpClient = httpClient;
      return this;
    }

    /**
     * Sets a custom configuration parser (optional). Default is JacksonPrecomputedConfigParser.
     *
     * @param configParser The parser to use for configuration responses
     * @return This builder
     */
    public Builder configParser(@Nullable PrecomputedConfigParser<JsonNode> configParser) {
      this.configParser = configParser;
      return this;
    }

    /** Builds and initializes the client asynchronously. */
    public CompletableFuture<EppoPrecomputedClient> buildAndInitAsync() {
      if (application == null) {
        throw new MissingApplicationException();
      }
      if (apiKey == null || apiKey.isEmpty()) {
        throw new MissingApiKeyException();
      }
      if (subjectKey == null || subjectKey.isEmpty()) {
        throw new MissingSubjectKeyException();
      }

      if (instance != null && !forceReinitialize) {
        Log.w(TAG, "EppoPrecomputedClient instance already initialized");
        return CompletableFuture.completedFuture(instance);
      } else if (instance != null) {
        instance.stopPolling();
        Log.d(TAG, "`forceReinitialize` triggered reinitializing EppoPrecomputedClient");
      }

      // Create configuration store
      if (configStore == null) {
        // Use MD5 hash prefix of subject key to ensure consistent length and privacy
        String subjectKeyHash =
            ObfuscationUtils.md5HexPrefix(subjectKey, null, SUBJECT_KEY_HASH_LENGTH);
        String cacheFileNameSuffix = safeCacheKey(apiKey) + "-" + subjectKeyHash;
        configStore = new PrecomputedConfigurationStore(application, cacheFileNameSuffix);
      }

      // Create HTTP client
      OkHttpClient client = httpClient != null ? httpClient : new OkHttpClient();

      // Create config parser (default to Jackson implementation)
      PrecomputedConfigParser<JsonNode> parser =
          configParser != null ? configParser : new JacksonPrecomputedConfigParser();

      // Derive base URL from API key if not explicitly set
      String effectiveBaseUrl = baseUrl;
      if (effectiveBaseUrl == null) {
        String envPrefix = Utils.getEnvironmentFromSdkKey(apiKey);
        if (envPrefix != null) {
          effectiveBaseUrl = "https://" + envPrefix + "." + DEFAULT_EDGE_HOST;
        } else {
          effectiveBaseUrl = "https://" + DEFAULT_EDGE_HOST;
        }
      }

      instance =
          new EppoPrecomputedClient(
              apiKey,
              subjectKey,
              subjectAttributes,
              banditActions,
              assignmentLogger,
              banditLogger,
              assignmentCache,
              banditCache,
              configStore,
              isGracefulMode,
              effectiveBaseUrl,
              client,
              parser);

      CompletableFuture<EppoPrecomputedClient> result = new CompletableFuture<>();

      // Load initial configuration
      if (initialConfiguration != null) {
        // Use provided initial configuration
        try {
          PrecomputedConfigurationResponse config =
              PrecomputedConfigurationResponse.fromBytes(initialConfiguration);
          configStore.setConfiguration(config);
          Log.d(TAG, "Loaded initial configuration with " + config.getFlags().size() + " flags");
        } catch (Exception e) {
          Log.e(TAG, "Failed to parse initial configuration", e);
        }
      } else if (!ignoreCachedConfiguration) {
        // Try to load from cache (runs concurrently with network fetch)
        configStore
            .loadConfigFromCache()
            .thenAccept(
                config -> {
                  if (config != null && !config.getFlags().isEmpty()) {
                    configStore.setConfiguration(config);
                    Log.d(
                        TAG,
                        "Loaded cached configuration with " + config.getFlags().size() + " flags");
                  }
                });
      }

      // Capture final values for lambda
      final long finalPollingIntervalMs = pollingIntervalMs;
      // Default jitter to 10% of polling interval if not explicitly set
      final long finalPollingJitterMs =
          pollingJitterMs < 0 ? pollingIntervalMs / DEFAULT_JITTER_INTERVAL_RATIO : pollingJitterMs;
      final boolean shouldStartPolling = pollingEnabled && pollingIntervalMs > 0;

      if (!offlineMode) {
        // Fetch configuration from server
        instance
            .fetchPrecomputedFlagsAsync()
            .thenRun(
                () -> {
                  // Start polling after initial fetch completes
                  if (shouldStartPolling) {
                    instance.startPolling(finalPollingIntervalMs, finalPollingJitterMs);
                  }
                  result.complete(instance);
                })
            .exceptionally(
                ex -> {
                  Log.e(TAG, "Failed to fetch precomputed flags", ex);
                  if (isGracefulMode) {
                    // Still complete successfully in graceful mode
                    // Start polling even on failure so we can retry
                    if (shouldStartPolling) {
                      instance.startPolling(finalPollingIntervalMs, finalPollingJitterMs);
                    }
                    result.complete(instance);
                  } else {
                    result.completeExceptionally(
                        new EppoInitializationException(
                            "Unable to initialize client; Configuration could not be loaded", ex));
                  }
                  return null;
                });
      } else {
        // In offline mode, complete immediately (no polling in offline mode)
        result.complete(instance);
      }

      return result.exceptionally(
          e -> {
            Log.e(TAG, "Exception caught during initialization: " + e.getMessage(), e);
            if (!isGracefulMode) {
              throw new RuntimeException(e);
            }
            return instance;
          });
    }

    /** Builds and initializes the client synchronously. */
    public EppoPrecomputedClient buildAndInit() {
      try {
        return buildAndInitAsync().get();
      } catch (ExecutionException | InterruptedException e) {
        Log.e(TAG, "Exception caught during initialization: " + e.getMessage(), e);
        if (!isGracefulMode) {
          throw new RuntimeException(e);
        }
        return instance;
      }
    }
  }

  /**
   * Internal Jackson-based implementation of PrecomputedConfigParser.
   *
   * <p>This class will be moved to the batteries-included module in a future PR.
   */
  private static class JacksonPrecomputedConfigParser implements PrecomputedConfigParser<JsonNode> {
    private final ObjectMapper mapper = new ObjectMapper();

    @NonNull
    @Override
    public PrecomputedConfigurationResponse parse(@NonNull byte[] responseBytes)
        throws PrecomputedParseException {
      try {
        return PrecomputedConfigurationResponse.fromBytes(responseBytes);
      } catch (Exception e) {
        throw new PrecomputedParseException("Failed to parse precomputed configuration", e);
      }
    }

    @NonNull
    @Override
    public JsonNode parseJsonValue(@NonNull String base64EncodedValue)
        throws PrecomputedParseException {
      try {
        String decoded = Utils.base64Decode(base64EncodedValue);
        return mapper.readTree(decoded);
      } catch (Exception e) {
        throw new PrecomputedParseException("Failed to parse JSON value", e);
      }
    }
  }
}
