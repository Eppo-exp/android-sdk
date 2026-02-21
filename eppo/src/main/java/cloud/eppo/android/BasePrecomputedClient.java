package cloud.eppo.android;

import static cloud.eppo.android.util.Utils.logTag;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cloud.eppo.android.api.PrecomputedConfigParser;
import cloud.eppo.android.api.PrecomputedParseException;
import cloud.eppo.android.dto.BanditResult;
import cloud.eppo.android.dto.PrecomputedBandit;
import cloud.eppo.android.dto.PrecomputedConfigurationResponse;
import cloud.eppo.android.dto.PrecomputedFlag;
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
 * Base precomputed client that requires implementations of parser interface.
 *
 * <p>This class provides the core precomputed flag evaluation logic but requires consumers to
 * provide a {@link PrecomputedConfigParser} for JSON parsing.
 *
 * <p>The batteries-included {@link EppoPrecomputedClient} extends this class and provides
 * Jackson-based defaults.
 *
 * <p><b>Note:</b> This class currently uses OkHttp directly for HTTP operations. In a future
 * version, this will be refactored to use {@code EppoConfigurationClient} interface from the common
 * SDK framework.
 *
 * @param <JSONFlagType> The JSON tree type for JSON flag values
 */
public abstract class BasePrecomputedClient<JSONFlagType> {
  private static final String TAG = logTag(BasePrecomputedClient.class);
  private static final String ASSIGNMENTS_ENDPOINT = "/assignments";
  private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
  private static final String NO_ACTION_CACHE_KEY = "__eppo_no_action";

  protected final String apiKey;
  protected final String subjectKey;
  @Nullable protected final Attributes subjectAttributes;
  @Nullable protected final Map<String, Map<String, Attributes>> banditActions;
  @Nullable protected final AssignmentLogger assignmentLogger;
  @Nullable protected final BanditLogger banditLogger;
  @Nullable protected final IAssignmentCache assignmentCache;
  @Nullable protected final IAssignmentCache banditCache;
  protected final PrecomputedConfigurationStore configurationStore;
  protected final boolean isGracefulMode;
  protected final String baseUrl;
  protected final OkHttpClient httpClient;

  // Interface dependencies (injected)
  protected final PrecomputedConfigParser<JSONFlagType> configParser;

  protected volatile long pollingIntervalMs;
  protected volatile long pollingJitterMs;
  @Nullable protected ScheduledExecutorService poller;
  @Nullable protected ScheduledFuture<?> pollFuture;
  protected final AtomicBoolean isPolling = new AtomicBoolean(false);

  protected BasePrecomputedClient(
      @NonNull String apiKey,
      @NonNull String subjectKey,
      @Nullable Attributes subjectAttributes,
      @Nullable Map<String, Map<String, Attributes>> banditActions,
      @Nullable AssignmentLogger assignmentLogger,
      @Nullable BanditLogger banditLogger,
      @Nullable IAssignmentCache assignmentCache,
      @Nullable IAssignmentCache banditCache,
      @NonNull PrecomputedConfigurationStore configurationStore,
      boolean isGracefulMode,
      @NonNull String baseUrl,
      @NonNull OkHttpClient httpClient,
      @NonNull PrecomputedConfigParser<JSONFlagType> configParser) {
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
    this.configParser = configParser;
  }

  // ==================== Assignment Methods ====================

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
   * @return The assigned JSON value of the generic type or default
   */
  public JSONFlagType getJSONAssignment(String flagKey, JSONFlagType defaultValue) {
    try {
      Object result = getPrecomputedAssignment(flagKey, defaultValue, "JSON");
      if (result != null) {
        @SuppressWarnings("unchecked")
        JSONFlagType typed = (JSONFlagType) result;
        return typed;
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

  // ==================== Internal Assignment Logic ====================

  protected Object getPrecomputedAssignment(
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
    Object decodedValue =
        decodeValue(flag.getVariationValue(), flag.getVariationType(), defaultValue);

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

  protected BanditResult getPrecomputedBanditAction(String flagKey, String defaultValue) {
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

    // Decode bandit values (with null safety)
    String decodedBanditKey =
        bandit.getBanditKey() != null ? Utils.base64Decode(bandit.getBanditKey()) : null;
    String decodedAction =
        bandit.getAction() != null ? Utils.base64Decode(bandit.getAction()) : null;
    String decodedModelVersion =
        bandit.getModelVersion() != null ? Utils.base64Decode(bandit.getModelVersion()) : null;

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
        String actionKey = decodedAction != null ? decodedAction : NO_ACTION_CACHE_KEY;
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

  protected boolean checkTypeMatch(String expected, String actual) {
    if (expected.equalsIgnoreCase(actual)) {
      return true;
    }
    // Integer is compatible with numeric
    if ("NUMERIC".equalsIgnoreCase(expected) && "INTEGER".equalsIgnoreCase(actual)) {
      return true;
    }
    return false;
  }

  /**
   * Decodes a value from its Base64-encoded string representation.
   *
   * <p>Subclasses should override this method if they need custom decoding for JSON types. The
   * default implementation handles STRING, BOOLEAN, INTEGER, and NUMERIC types, but delegates JSON
   * parsing to {@link #parseJsonValue(String)}.
   *
   * @param encodedValue Base64-encoded value
   * @param variationType The type of the variation
   * @param defaultValue The default value if decoding fails
   * @return The decoded value
   */
  protected Object decodeValue(String encodedValue, String variationType, Object defaultValue) {
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
          return defaultValue;
        }
      case "NUMERIC":
        try {
          return Double.parseDouble(decoded);
        } catch (NumberFormatException e) {
          Log.w(TAG, "Failed to parse numeric value: " + decoded);
          return defaultValue;
        }
      case "JSON":
        try {
          return parseJsonValue(encodedValue);
        } catch (PrecomputedParseException e) {
          Log.w(TAG, "Failed to parse JSON value: " + decoded);
          return defaultValue;
        }
      default:
        return decoded;
    }
  }

  /**
   * Parses a JSON value from its Base64-encoded representation.
   *
   * <p>This method delegates to the configured {@link PrecomputedConfigParser}.
   *
   * @param base64EncodedValue Base64-encoded JSON string
   * @return Parsed JSON value of the generic type
   * @throws PrecomputedParseException if parsing fails
   */
  protected JSONFlagType parseJsonValue(String base64EncodedValue)
      throws PrecomputedParseException {
    return configParser.parseJsonValue(base64EncodedValue);
  }

  // ==================== Logging Methods ====================

  protected void logAssignment(
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
        decodedExtraLogging.put(
            Utils.base64Decode(entry.getKey()), Utils.base64Decode(entry.getValue()));
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

  protected void logBanditAction(
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

  protected Map<String, String> buildMetaData() {
    Map<String, String> metaData = new HashMap<>();
    metaData.put("obfuscated", "true");
    metaData.put("sdkLanguage", "android");
    metaData.put("sdkLibVersion", BuildConfig.EPPO_VERSION);
    return metaData;
  }

  // ==================== Error Handling ====================

  protected <T> T handleException(Exception e, T defaultValue) {
    Log.e(TAG, "Error getting assignment: " + e.getMessage(), e);
    if (!isGracefulMode) {
      throw new RuntimeException(e);
    }
    return defaultValue;
  }

  // ==================== HTTP Methods ====================

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
      byte[] requestBody = buildRequestBody();

      Log.d(TAG, "Fetching precomputed flags from: " + baseUrl + ASSIGNMENTS_ENDPOINT);

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
                    PrecomputedConfigurationResponse config = configParser.parse(bytes);

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

  protected String buildRequestUrl() {
    return baseUrl
        + ASSIGNMENTS_ENDPOINT
        + "?apiKey="
        + apiKey
        + "&sdkVersion="
        + BuildConfig.EPPO_VERSION
        + "&sdkName=android";
  }

  /**
   * Builds the POST request body as JSON bytes.
   *
   * <p>Subclasses must implement this method to serialize subject data using their JSON library.
   *
   * @return The request body as JSON-encoded bytes
   */
  protected abstract byte[] buildRequestBody();

  // ==================== Polling Methods ====================

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

  protected void scheduleNextPoll() {
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
}
