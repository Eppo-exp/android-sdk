package cloud.eppo.android;

import static cloud.eppo.android.util.Utils.logTag;
import static cloud.eppo.android.util.Utils.safeCacheKey;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cloud.eppo.android.cache.LRUAssignmentCache;
import cloud.eppo.android.dto.BanditResult;
import cloud.eppo.android.dto.PrecomputedBandit;
import cloud.eppo.android.dto.PrecomputedConfigurationResponse;
import cloud.eppo.android.dto.PrecomputedFlag;
import cloud.eppo.android.exceptions.MissingApiKeyException;
import cloud.eppo.android.exceptions.MissingApplicationException;
import cloud.eppo.android.exceptions.MissingSubjectKeyException;
import cloud.eppo.android.exceptions.NotInitializedException;
import cloud.eppo.android.util.ObfuscationUtils;
import cloud.eppo.android.util.Utils;
import cloud.eppo.api.Attributes;
import cloud.eppo.api.EppoValue;
import cloud.eppo.api.IAssignmentCache;
import cloud.eppo.cache.AssignmentCacheEntry;
import cloud.eppo.cache.AssignmentCacheKey;
import cloud.eppo.cache.BanditCacheValue;
import cloud.eppo.cache.VariationCacheValue;
import cloud.eppo.logging.Assignment;
import cloud.eppo.logging.AssignmentLogger;
import cloud.eppo.logging.BanditAssignment;
import cloud.eppo.logging.BanditLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Precomputed client for Eppo feature flags. All flag assignments are computed server-side and
 * delivered as a batch, providing instant lookups with zero client-side evaluation time.
 */
public class EppoPrecomputedClient {
  private static final String TAG = logTag(EppoPrecomputedClient.class);
  private static final boolean DEFAULT_IS_GRACEFUL_MODE = true;
  private static final long DEFAULT_POLLING_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes
  private static final long DEFAULT_JITTER_INTERVAL_RATIO = 10;
  private static final String DEFAULT_BASE_URL = "https://fs-edge-assignment.eppo.cloud";
  private static final String ASSIGNMENTS_ENDPOINT = "/assignments";
  private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Nullable private static EppoPrecomputedClient instance;

  private final String apiKey;
  private final String subjectKey;
  @Nullable private final Attributes subjectAttributes;
  @Nullable private final Map<String, Map<String, Attributes>> banditActions;
  @Nullable private final AssignmentLogger assignmentLogger;
  @Nullable private final BanditLogger banditLogger;
  @Nullable private final IAssignmentCache assignmentCache;
  @Nullable private final IAssignmentCache banditCache;
  private final PrecomputedConfigurationStore configurationStore;
  private final boolean isGracefulMode;
  private final String baseUrl;
  private final OkHttpClient httpClient;

  private long pollingIntervalMs;
  private long pollingJitterMs;
  @Nullable private ScheduledExecutorService poller;
  @Nullable private ScheduledFuture<?> pollFuture;
  private final AtomicBoolean isPolling = new AtomicBoolean(false);

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
      OkHttpClient httpClient) {
    this.apiKey = apiKey;
    this.subjectKey = subjectKey;
    this.subjectAttributes = subjectAttributes;
    this.banditActions = banditActions;
    this.assignmentLogger = assignmentLogger;
    this.banditLogger = banditLogger;
    this.assignmentCache = assignmentCache;
    this.banditCache = banditCache;
    this.configurationStore = configurationStore;
    this.isGracefulMode = isGracefulMode;
    this.baseUrl = baseUrl;
    this.httpClient = httpClient;
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

  // Assignment methods

  /**
   * Gets a string assignment for a flag.
   *
   * @param flagKey The flag key
   * @param defaultValue The default value if not found
   * @return The assigned string value or default
   */
  public String getStringAssignment(String flagKey, String defaultValue) {
    try {
      Object result = getPrecomputedAssignment(flagKey, defaultValue, "STRING");
      return result != null ? result.toString() : defaultValue;
    } catch (Exception e) {
      return handleException(e, defaultValue);
    }
  }

  /**
   * Gets a boolean assignment for a flag.
   *
   * @param flagKey The flag key
   * @param defaultValue The default value if not found
   * @return The assigned boolean value or default
   */
  public boolean getBooleanAssignment(String flagKey, boolean defaultValue) {
    try {
      Object result = getPrecomputedAssignment(flagKey, defaultValue, "BOOLEAN");
      if (result instanceof Boolean) {
        return (Boolean) result;
      }
      return defaultValue;
    } catch (Exception e) {
      return handleException(e, defaultValue);
    }
  }

  /**
   * Gets an integer assignment for a flag.
   *
   * @param flagKey The flag key
   * @param defaultValue The default value if not found
   * @return The assigned integer value or default
   */
  public int getIntegerAssignment(String flagKey, int defaultValue) {
    try {
      Object result = getPrecomputedAssignment(flagKey, defaultValue, "INTEGER");
      if (result instanceof Number) {
        return ((Number) result).intValue();
      }
      return defaultValue;
    } catch (Exception e) {
      return handleException(e, defaultValue);
    }
  }

  /**
   * Gets a numeric (double) assignment for a flag.
   *
   * @param flagKey The flag key
   * @param defaultValue The default value if not found
   * @return The assigned numeric value or default
   */
  public double getNumericAssignment(String flagKey, double defaultValue) {
    try {
      Object result = getPrecomputedAssignment(flagKey, defaultValue, "NUMERIC");
      if (result instanceof Number) {
        return ((Number) result).doubleValue();
      }
      return defaultValue;
    } catch (Exception e) {
      return handleException(e, defaultValue);
    }
  }

  /**
   * Gets a JSON assignment for a flag.
   *
   * @param flagKey The flag key
   * @param defaultValue The default value if not found
   * @return The assigned JSON value or default
   */
  public JsonNode getJSONAssignment(String flagKey, JsonNode defaultValue) {
    try {
      Object result = getPrecomputedAssignment(flagKey, defaultValue, "JSON");
      if (result instanceof JsonNode) {
        return (JsonNode) result;
      }
      return defaultValue;
    } catch (Exception e) {
      return handleException(e, defaultValue);
    }
  }

  /**
   * Gets a bandit action for a flag.
   *
   * @param flagKey The flag key
   * @param defaultValue The default variation value if not found
   * @return The bandit result containing variation and action
   */
  public BanditResult getBanditAction(String flagKey, String defaultValue) {
    try {
      return getPrecomputedBanditAction(flagKey, defaultValue);
    } catch (Exception e) {
      return handleException(e, new BanditResult(defaultValue, null));
    }
  }

  // Internal assignment logic

  private Object getPrecomputedAssignment(
      String flagKey, Object defaultValue, String expectedType) {
    if (flagKey == null || flagKey.isEmpty()) {
      Log.w(TAG, "Invalid argument: flagKey cannot be blank");
      return defaultValue;
    }

    String salt = configurationStore.getSalt();
    if (salt == null) {
      Log.w(TAG, "Missing salt for flag store");
      return defaultValue;
    }

    String hashedKey = ObfuscationUtils.md5Hex(flagKey, salt);
    PrecomputedFlag flag = configurationStore.getFlag(hashedKey);

    if (flag == null) {
      Log.d(TAG, "No assigned variation because flag not found: " + flagKey);
      return defaultValue;
    }

    // Check type match
    if (!checkTypeMatch(expectedType, flag.getVariationType())) {
      Log.w(
          TAG,
          "Type mismatch for flag "
              + flagKey
              + ": expected "
              + expectedType
              + ", got "
              + flag.getVariationType());
      return defaultValue;
    }

    // Decode the value
    Object decodedValue = decodeValue(flag.getVariationValue(), flag.getVariationType());

    // Log assignment if needed
    if (flag.isDoLog() && assignmentLogger != null) {
      String decodedAllocationKey =
          flag.getAllocationKey() != null ? Utils.base64Decode(flag.getAllocationKey()) : null;
      String decodedVariationKey =
          flag.getVariationKey() != null ? Utils.base64Decode(flag.getVariationKey()) : null;

      // Check assignment cache for deduplication
      boolean shouldLog = true;
      if (assignmentCache != null && decodedAllocationKey != null && decodedVariationKey != null) {
        AssignmentCacheEntry cacheEntry =
            new AssignmentCacheEntry(
                new AssignmentCacheKey(subjectKey, flagKey),
                new VariationCacheValue(decodedAllocationKey, decodedVariationKey));
        shouldLog = assignmentCache.putIfAbsent(cacheEntry);
      }

      if (shouldLog) {
        logAssignment(flagKey, decodedAllocationKey, decodedVariationKey, flag.getExtraLogging());
      }
    }

    return decodedValue;
  }

  private BanditResult getPrecomputedBanditAction(String flagKey, String defaultValue) {
    if (flagKey == null || flagKey.isEmpty()) {
      Log.w(TAG, "Invalid argument: flagKey cannot be blank");
      return new BanditResult(defaultValue, null);
    }

    String salt = configurationStore.getSalt();
    if (salt == null) {
      Log.w(TAG, "Missing salt for bandit store");
      return new BanditResult(defaultValue, null);
    }

    String hashedKey = ObfuscationUtils.md5Hex(flagKey, salt);
    PrecomputedBandit bandit = configurationStore.getBandit(hashedKey);

    if (bandit == null) {
      Log.d(TAG, "No assigned bandit action because bandit not found: " + flagKey);
      return new BanditResult(defaultValue, null);
    }

    // Decode bandit values
    String decodedBanditKey = Utils.base64Decode(bandit.getBanditKey());
    String decodedAction = Utils.base64Decode(bandit.getAction());
    String decodedModelVersion = Utils.base64Decode(bandit.getModelVersion());

    // Get the variation from the flag assignment
    String assignedVariation = getStringAssignment(flagKey, defaultValue);

    // Decode action attributes (both keys and values are Base64 encoded)
    Attributes decodedNumericAttrs = new Attributes();
    if (bandit.getActionNumericAttributes() != null) {
      for (Map.Entry<String, String> entry : bandit.getActionNumericAttributes().entrySet()) {
        try {
          String decodedKey = Utils.base64Decode(entry.getKey());
          String decodedValue = Utils.base64Decode(entry.getValue());
          decodedNumericAttrs.put(decodedKey, EppoValue.valueOf(Double.parseDouble(decodedValue)));
        } catch (NumberFormatException e) {
          Log.w(TAG, "Failed to parse numeric attribute: " + entry.getKey());
        }
      }
    }

    Attributes decodedCategoricalAttrs = new Attributes();
    if (bandit.getActionCategoricalAttributes() != null) {
      for (Map.Entry<String, String> entry : bandit.getActionCategoricalAttributes().entrySet()) {
        String decodedKey = Utils.base64Decode(entry.getKey());
        String decodedValue = Utils.base64Decode(entry.getValue());
        decodedCategoricalAttrs.put(decodedKey, EppoValue.valueOf(decodedValue));
      }
    }

    // Log bandit event if needed
    if (banditLogger != null) {
      // Check bandit cache for deduplication
      boolean shouldLog = true;
      if (banditCache != null) {
        String actionKey = decodedAction != null ? decodedAction : "__eppo_no_action";
        AssignmentCacheEntry cacheEntry =
            new AssignmentCacheEntry(
                new AssignmentCacheKey(subjectKey, flagKey),
                new BanditCacheValue(decodedBanditKey, actionKey));
        shouldLog = banditCache.putIfAbsent(cacheEntry);
      }

      if (shouldLog) {
        logBanditAction(
            flagKey,
            decodedBanditKey,
            decodedAction,
            bandit.getActionProbability(),
            bandit.getOptimalityGap(),
            decodedModelVersion,
            decodedNumericAttrs,
            decodedCategoricalAttrs);
      }
    }

    return new BanditResult(assignedVariation, decodedAction);
  }

  private boolean checkTypeMatch(String expected, String actual) {
    if (expected.equalsIgnoreCase(actual)) {
      return true;
    }
    // Integer is compatible with numeric
    if ("NUMERIC".equalsIgnoreCase(expected) && "INTEGER".equalsIgnoreCase(actual)) {
      return true;
    }
    return false;
  }

  private Object decodeValue(String encodedValue, String variationType) {
    String decoded = Utils.base64Decode(encodedValue);

    switch (variationType.toUpperCase()) {
      case "STRING":
        return decoded;
      case "BOOLEAN":
        return "true".equalsIgnoreCase(decoded);
      case "INTEGER":
        try {
          return Integer.parseInt(decoded);
        } catch (NumberFormatException e) {
          Log.w(TAG, "Failed to parse integer value: " + decoded);
          return 0;
        }
      case "NUMERIC":
        try {
          return Double.parseDouble(decoded);
        } catch (NumberFormatException e) {
          Log.w(TAG, "Failed to parse numeric value: " + decoded);
          return 0.0;
        }
      case "JSON":
        try {
          return objectMapper.readTree(decoded);
        } catch (Exception e) {
          Log.w(TAG, "Failed to parse JSON value: " + decoded);
          return objectMapper.createObjectNode();
        }
      default:
        return decoded;
    }
  }

  // Logging methods

  private void logAssignment(
      String flagKey,
      @Nullable String allocationKey,
      @Nullable String variationKey,
      @Nullable Map<String, String> extraLogging) {
    if (assignmentLogger == null) {
      return;
    }

    String experiment = allocationKey != null ? flagKey + "-" + allocationKey : null;

    Map<String, String> decodedExtraLogging = new HashMap<>();
    if (extraLogging != null) {
      for (Map.Entry<String, String> entry : extraLogging.entrySet()) {
        decodedExtraLogging.put(entry.getKey(), Utils.base64Decode(entry.getValue()));
      }
    }

    Map<String, String> metaData = buildMetaData();

    Assignment assignment =
        new Assignment(
            experiment,
            flagKey,
            allocationKey,
            variationKey,
            subjectKey,
            subjectAttributes != null ? subjectAttributes : new Attributes(),
            decodedExtraLogging,
            metaData);

    try {
      assignmentLogger.logAssignment(assignment);
    } catch (Exception e) {
      Log.e(TAG, "Failed to log assignment", e);
    }
  }

  private void logBanditAction(
      String flagKey,
      String banditKey,
      String action,
      double actionProbability,
      double optimalityGap,
      String modelVersion,
      Attributes actionNumericAttrs,
      Attributes actionCategoricalAttrs) {
    if (banditLogger == null) {
      return;
    }

    Attributes subjectNumericAttrs = new Attributes();
    Attributes subjectCategoricalAttrs = new Attributes();

    if (subjectAttributes != null) {
      for (String key : subjectAttributes.keySet()) {
        EppoValue value = subjectAttributes.get(key);
        if (value != null) {
          if (value.isNumeric()) {
            subjectNumericAttrs.put(key, value);
          } else {
            subjectCategoricalAttrs.put(key, value);
          }
        }
      }
    }

    Map<String, String> metaData = buildMetaData();

    BanditAssignment banditAssignment =
        new BanditAssignment(
            flagKey,
            banditKey,
            subjectKey,
            action,
            actionProbability,
            optimalityGap,
            modelVersion,
            subjectNumericAttrs,
            subjectCategoricalAttrs,
            actionNumericAttrs,
            actionCategoricalAttrs,
            metaData);

    try {
      banditLogger.logBanditAssignment(banditAssignment);
    } catch (Exception e) {
      Log.e(TAG, "Failed to log bandit assignment", e);
    }
  }

  private Map<String, String> buildMetaData() {
    Map<String, String> metaData = new HashMap<>();
    metaData.put("obfuscated", "true");
    metaData.put("sdkLanguage", "android");
    metaData.put("sdkLibVersion", BuildConfig.EPPO_VERSION);
    return metaData;
  }

  // Error handling

  private <T> T handleException(Exception e, T defaultValue) {
    Log.e(TAG, "Error getting assignment: " + e.getMessage(), e);
    if (!isGracefulMode) {
      throw new RuntimeException(e);
    }
    return defaultValue;
  }

  // HTTP methods

  /** Fetches precomputed flags from the server. */
  public void fetchPrecomputedFlags() {
    try {
      fetchPrecomputedFlagsAsync().get();
    } catch (InterruptedException | ExecutionException e) {
      Log.e(TAG, "Error fetching precomputed flags", e);
      if (!isGracefulMode) {
        throw new RuntimeException(e);
      }
    }
  }

  /** Fetches precomputed flags from the server asynchronously. */
  public CompletableFuture<Void> fetchPrecomputedFlagsAsync() {
    CompletableFuture<Void> future = new CompletableFuture<>();

    try {
      String url = buildRequestUrl();
      String requestBody = buildRequestBody();

      Log.d(TAG, "Fetching precomputed flags from: " + url);
      Log.d(TAG, "Request payload: " + requestBody);

      Request request =
          new Request.Builder()
              .url(url)
              .post(RequestBody.create(requestBody, JSON_MEDIA_TYPE))
              .build();

      httpClient
          .newCall(request)
          .enqueue(
              new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                  Log.e(TAG, "Failed to fetch precomputed flags", e);
                  future.completeExceptionally(e);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                  try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful()) {
                      String responseText = body != null ? body.string() : "(no body)";
                      String errorMsg = "HTTP error: " + response.code() + " - " + responseText;
                      Log.e(TAG, errorMsg);
                      future.completeExceptionally(new IOException(errorMsg));
                      return;
                    }

                    if (body == null) {
                      future.completeExceptionally(new IOException("Empty response body"));
                      return;
                    }

                    byte[] bytes = body.bytes();
                    PrecomputedConfigurationResponse config =
                        PrecomputedConfigurationResponse.fromBytes(bytes);

                    configurationStore
                        .saveConfiguration(config)
                        .thenRun(
                            () -> {
                              Log.d(
                                  TAG,
                                  "Successfully fetched precomputed flags: "
                                      + config.getFlags().size()
                                      + " flags, "
                                      + config.getBandits().size()
                                      + " bandits");
                              future.complete(null);
                            })
                        .exceptionally(
                            ex -> {
                              future.completeExceptionally(ex);
                              return null;
                            });
                  } catch (Exception e) {
                    Log.e(TAG, "Error processing response", e);
                    future.completeExceptionally(e);
                  }
                }
              });
    } catch (Exception e) {
      future.completeExceptionally(e);
    }

    return future;
  }

  private String buildRequestUrl() {
    return baseUrl
        + ASSIGNMENTS_ENDPOINT
        + "?apiKey="
        + apiKey
        + "&sdkVersion="
        + BuildConfig.EPPO_VERSION
        + "&sdkName=android";
  }

  private String buildRequestBody() throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("subject_key", subjectKey);

    Map<String, Object> subjectAttrsMap = new HashMap<>();
    Map<String, Number> numericAttrs = new HashMap<>();
    Map<String, String> categoricalAttrs = new HashMap<>();

    if (subjectAttributes != null) {
      for (String key : subjectAttributes.keySet()) {
        EppoValue value = subjectAttributes.get(key);
        if (value != null) {
          if (value.isNumeric()) {
            numericAttrs.put(key, value.doubleValue());
          } else {
            categoricalAttrs.put(key, value.stringValue());
          }
        }
      }
    }

    subjectAttrsMap.put("numericAttributes", numericAttrs);
    subjectAttrsMap.put("categoricalAttributes", categoricalAttrs);
    body.put("subject_attributes", subjectAttrsMap);

    if (banditActions != null && !banditActions.isEmpty()) {
      body.put("bandit_actions", banditActions);
    }

    return objectMapper.writeValueAsString(body);
  }

  // Polling methods

  /** Starts polling for configuration updates. */
  public void startPolling(long intervalMs, long jitterMs) {
    if (isPolling.getAndSet(true)) {
      Log.w(TAG, "Polling is already running");
      return;
    }

    this.pollingIntervalMs = intervalMs;
    this.pollingJitterMs = jitterMs;

    poller = Executors.newSingleThreadScheduledExecutor();
    scheduleNextPoll();
    Log.d(TAG, "Started polling with interval: " + intervalMs + "ms, jitter: " + jitterMs + "ms");
  }

  private void scheduleNextPoll() {
    if (poller == null || poller.isShutdown()) {
      return;
    }

    long jitter = (long) (Math.random() * pollingJitterMs);
    long delay = pollingIntervalMs + jitter;

    pollFuture =
        poller.schedule(
            () -> {
              try {
                fetchPrecomputedFlags();
              } catch (Exception e) {
                Log.e(TAG, "Error during polling fetch", e);
              }
              if (isPolling.get()) {
                scheduleNextPoll();
              }
            },
            delay,
            TimeUnit.MILLISECONDS);
  }

  /** Pauses polling for configuration updates. */
  public void pausePolling() {
    isPolling.set(false);
    if (pollFuture != null) {
      pollFuture.cancel(false);
      pollFuture = null;
    }
    Log.d(TAG, "Paused polling");
  }

  /** Resumes polling for configuration updates. */
  public void resumePolling() {
    if (pollingIntervalMs <= 0) {
      Log.w(TAG, "Cannot resume polling - no polling interval configured");
      return;
    }

    if (isPolling.getAndSet(true)) {
      Log.w(TAG, "Polling is already running");
      return;
    }

    if (poller == null || poller.isShutdown()) {
      poller = Executors.newSingleThreadScheduledExecutor();
    }

    scheduleNextPoll();
    Log.d(TAG, "Resumed polling");
  }

  /** Stops polling for configuration updates and releases resources. */
  public void stopPolling() {
    isPolling.set(false);
    if (pollFuture != null) {
      pollFuture.cancel(false);
      pollFuture = null;
    }
    if (poller != null) {
      poller.shutdown();
      poller = null;
    }
    Log.d(TAG, "Stopped polling");
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
    private String baseUrl = DEFAULT_BASE_URL;
    @Nullable private byte[] initialConfiguration;
    private boolean ignoreCachedConfiguration = false;
    @Nullable private OkHttpClient httpClient;

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
    public Builder baseUrl(String baseUrl) {
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
        // Use MD5 hash of subject key to ensure consistent length and privacy
        String subjectKeyHash = ObfuscationUtils.md5Hex(subjectKey, null).substring(0, 8);
        String cacheFileNameSuffix = safeCacheKey(apiKey) + "-" + subjectKeyHash;
        configStore = new PrecomputedConfigurationStore(application, cacheFileNameSuffix);
      }

      // Create HTTP client
      OkHttpClient client = httpClient != null ? httpClient : new OkHttpClient();

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
              baseUrl,
              client);

      CompletableFuture<EppoPrecomputedClient> result = new CompletableFuture<>();

      // Load initial configuration
      CompletableFuture<PrecomputedConfigurationResponse> initialConfigFuture = null;

      if (initialConfiguration != null) {
        // Use provided initial configuration
        try {
          PrecomputedConfigurationResponse config =
              PrecomputedConfigurationResponse.fromBytes(initialConfiguration);
          configStore.setConfiguration(config);
          initialConfigFuture = CompletableFuture.completedFuture(config);
        } catch (Exception e) {
          Log.e(TAG, "Failed to parse initial configuration", e);
        }
      } else if (!ignoreCachedConfiguration) {
        // Try to load from cache
        initialConfigFuture = configStore.loadConfigFromCache();
      }

      if (initialConfigFuture != null) {
        initialConfigFuture.thenAccept(
            config -> {
              if (config != null && !config.getFlags().isEmpty()) {
                configStore.setConfiguration(config);
                Log.d(
                    TAG,
                    "Loaded initial configuration with " + config.getFlags().size() + " flags");
              }
            });
      }

      if (!offlineMode) {
        // Fetch configuration from server
        instance
            .fetchPrecomputedFlagsAsync()
            .thenRun(
                () -> {
                  result.complete(instance);
                })
            .exceptionally(
                ex -> {
                  Log.e(TAG, "Failed to fetch precomputed flags", ex);
                  if (isGracefulMode) {
                    // Still complete successfully in graceful mode
                    result.complete(instance);
                  } else {
                    result.completeExceptionally(
                        new EppoInitializationException(
                            "Unable to initialize client; Configuration could not be loaded", ex));
                  }
                  return null;
                });
      } else {
        // In offline mode, complete immediately
        result.complete(instance);
      }

      // Start polling if enabled
      if (pollingEnabled && pollingIntervalMs > 0) {
        if (pollingJitterMs < 0) {
          pollingJitterMs = pollingIntervalMs / DEFAULT_JITTER_INTERVAL_RATIO;
        }
        instance.startPolling(pollingIntervalMs, pollingJitterMs);
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
}
