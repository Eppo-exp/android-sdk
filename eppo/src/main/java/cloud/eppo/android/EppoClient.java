package cloud.eppo.android;

import static cloud.eppo.Utils.getMD5Hex;
import static cloud.eppo.android.util.Utils.logTag;
import static cloud.eppo.android.util.Utils.safeCacheKey;
import static cloud.eppo.android.util.Utils.validateNotEmptyOrNull;

import android.app.ActivityManager;
import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cloud.eppo.android.exceptions.MissingApiKeyException;
import cloud.eppo.android.exceptions.MissingApplicationException;
import cloud.eppo.android.exceptions.NotInitializedException;
import cloud.eppo.android.logging.Assignment;
import cloud.eppo.android.logging.AssignmentLogger;
import cloud.eppo.ufc.dto.EppoValue;
import cloud.eppo.ufc.dto.FlagConfig;
import cloud.eppo.ufc.dto.SubjectAttributes;
import cloud.eppo.ufc.dto.VariationType;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

public class EppoClient {
  private static final String TAG = logTag(EppoClient.class);
  private static final String DEFAULT_HOST = "https://fscdn.eppo.cloud";
  private static final boolean DEFAULT_IS_GRACEFUL_MODE = true;
  private static final boolean DEFAULT_OBFUSCATE_CONFIG = true;

  @NonNull private final ConfigurationRequestor requestor;
  @Nullable private final AssignmentLogger assignmentLogger;
  @Nullable private static EppoClient instance;
  private final boolean isGracefulMode;
  private final boolean obfuscateConfig;

  private EppoClient(
      @NonNull ConfigurationRequestor configurationRequestor,
      @Nullable AssignmentLogger assignmentLogger,
      boolean isGracefulMode,
      boolean obfuscateConfig) {
    this.requestor = configurationRequestor;
    this.isGracefulMode = isGracefulMode;
    this.assignmentLogger = assignmentLogger;
    this.obfuscateConfig = obfuscateConfig;
  }

  /**
   * @noinspection unused
   */
  public static EppoClient init(Application application, String apiKey) {
    return new Builder().application(application).apiKey(apiKey).buildAndInit();
  }

  /**
   * @noinspection unused
   */
  public static EppoClient init(
      @NonNull Application application,
      @NonNull String apiKey,
      @NonNull String host,
      @Nullable InitializationCallback callback,
      @Nullable AssignmentLogger assignmentLogger,
      boolean isGracefulMode) {
    return new Builder()
        .application(application)
        .apiKey(apiKey)
        .host(host)
        .callback(callback)
        .assignmentLogger(assignmentLogger)
        .isGracefulMode(isGracefulMode)
        .obfuscateConfig(DEFAULT_OBFUSCATE_CONFIG)
        .buildAndInit();
  }

  /**
   * Ability to ad-hoc kick off a configuration load. Will load from a filesystem cached file as
   * well as fire off a HTTPS request for an updated configuration. If the cache load finishes
   * first, those assignments will be used until the fetch completes.
   *
   * @param callback methods to call when loading succeeds/fails. Note that the success callback
   *     will be called as soon as either a configuration is loaded from the cache or
   *     fetched--whichever finishes first. Error callback will called if both attempts fail.
   */
  public void refreshConfiguration(@Nullable InitializationCallback callback) {
    requestor.load(callback);
  }

  protected EppoValue getTypedAssignment(
      String flagKey,
      String subjectKey,
      SubjectAttributes subjectAttributes,
      EppoValue defaultValue,
      VariationType expectedType) {
    validateNotEmptyOrNull(flagKey, "flagKey must not be empty");
    validateNotEmptyOrNull(subjectKey, "subjectKey must not be empty");

    String flagKeyForLookup = flagKey;
    if (this.obfuscateConfig) {
      flagKeyForLookup = getMD5Hex(flagKey);
    }

    FlagConfig flag = requestor.getConfiguration(flagKeyForLookup);
    if (flag == null) {
      Log.w(TAG, "no configuration found for key: " + flagKey);
      return defaultValue;
    }

    if (!flag.isEnabled()) {
      Log.i(
          TAG,
          "no assigned variation because the experiment or feature flag is disabled: " + flagKey);
      return defaultValue;
    }

    if (flag.getVariationType() != expectedType) {
      Log.w(
          TAG,
          "no assigned variation because the flag type doesn't match the requested type: "
              + flagKey
              + " has type "
              + flag.getVariationType()
              + ", requested "
              + expectedType);
      return defaultValue;
    }

    FlagEvaluationResult evaluationResult =
        FlagEvaluator.evaluateFlag(
            flag, flagKey, subjectKey, subjectAttributes, this.obfuscateConfig);
    EppoValue assignedValue =
        evaluationResult.getVariation() != null ? evaluationResult.getVariation().getValue() : null;

    if (assignedValue != null && !valueTypeMatchesExpected(expectedType, assignedValue)) {
      Log.w(
          TAG,
          "no assigned variation because the flag type doesn't match the variation type: "
              + flagKey
              + " has type "
              + flag.getVariationType()
              + ", variation value is "
              + assignedValue);
      return defaultValue;
    }

    if (assignedValue != null && assignmentLogger != null && evaluationResult.doLog()) {
      String allocationKey = evaluationResult.getAllocationKey();
      String experimentKey =
          flagKey
              + '-'
              + allocationKey; // Our experiment key is derived by hyphenating the flag key and
      // allocation key
      String variationKey = evaluationResult.getVariation().getKey();
      Map<String, String> extraLogging = evaluationResult.getExtraLogging();
      Map<String, String> metaData = new HashMap<>();
      metaData.put("obfuscated", Boolean.valueOf(this.obfuscateConfig).toString());
      metaData.put("sdkLanguage", "Java (Android)");
      metaData.put("sdkLibVersion", BuildConfig.EPPO_VERSION);
      Assignment assignment =
          Assignment.createWithCurrentDate(
              experimentKey,
              flagKey,
              allocationKey,
              variationKey,
              subjectKey,
              subjectAttributes,
              extraLogging,
              metaData);
      try {
        assignmentLogger.logAssignment(assignment);
      } catch (Exception e) {
        Log.w(TAG, "Error logging assignment: " + e.getMessage(), e);
      }
    }

    return assignedValue != null ? assignedValue : defaultValue;
  }

  private boolean valueTypeMatchesExpected(VariationType expectedType, EppoValue value) {
    boolean typeMatch;
    switch (expectedType) {
      case BOOLEAN:
        typeMatch = value.isBoolean();
        break;
      case INTEGER:
        typeMatch =
            value.isNumeric()
                // Java has no isInteger check so we check using mod
                && value.doubleValue() % 1 == 0;
        break;
      case NUMERIC:
        typeMatch = value.isNumeric();
        break;
      case STRING:
        typeMatch = value.isString();
        break;
      case JSON:
        typeMatch =
            value.isString()
                // Eppo leaves JSON as a JSON string; to verify it's valid we attempt to parse
                && parseJsonString(value.stringValue()) != null;
        break;
      default:
        throw new IllegalArgumentException("Unexpected type for type checking: " + expectedType);
    }

    return typeMatch;
  }

  public boolean getBooleanAssignment(String flagKey, String subjectKey, boolean defaultValue) {
    return this.getBooleanAssignment(flagKey, subjectKey, new SubjectAttributes(), defaultValue);
  }

  public boolean getBooleanAssignment(
      String flagKey,
      String subjectKey,
      SubjectAttributes subjectAttributes,
      boolean defaultValue) {
    try {
      EppoValue value =
          this.getTypedAssignment(
              flagKey,
              subjectKey,
              subjectAttributes,
              EppoValue.valueOf(defaultValue),
              VariationType.BOOLEAN);
      return value.booleanValue();
    } catch (Exception e) {
      return throwIfNotGraceful(e, defaultValue);
    }
  }

  public int getIntegerAssignment(String flagKey, String subjectKey, int defaultValue) {
    return getIntegerAssignment(flagKey, subjectKey, new SubjectAttributes(), defaultValue);
  }

  public int getIntegerAssignment(
      String flagKey, String subjectKey, SubjectAttributes subjectAttributes, int defaultValue) {
    try {
      EppoValue value =
          this.getTypedAssignment(
              flagKey,
              subjectKey,
              subjectAttributes,
              EppoValue.valueOf(defaultValue),
              VariationType.INTEGER);
      return Double.valueOf(value.doubleValue()).intValue();
    } catch (Exception e) {
      return throwIfNotGraceful(e, defaultValue);
    }
  }

  public Double getDoubleAssignment(String flagKey, String subjectKey, double defaultValue) {
    return getDoubleAssignment(flagKey, subjectKey, new SubjectAttributes(), defaultValue);
  }

  public Double getDoubleAssignment(
      String flagKey, String subjectKey, SubjectAttributes subjectAttributes, double defaultValue) {
    try {
      EppoValue value =
          this.getTypedAssignment(
              flagKey,
              subjectKey,
              subjectAttributes,
              EppoValue.valueOf(defaultValue),
              VariationType.NUMERIC);
      return value.doubleValue();
    } catch (Exception e) {
      return throwIfNotGraceful(e, defaultValue);
    }
  }

  public String getStringAssignment(String flagKey, String subjectKey, String defaultValue) {
    return this.getStringAssignment(flagKey, subjectKey, new SubjectAttributes(), defaultValue);
  }

  public String getStringAssignment(
      String flagKey, String subjectKey, SubjectAttributes subjectAttributes, String defaultValue) {
    try {
      EppoValue value =
          this.getTypedAssignment(
              flagKey,
              subjectKey,
              subjectAttributes,
              EppoValue.valueOf(defaultValue),
              VariationType.STRING);
      return value.stringValue();
    } catch (Exception e) {
      return throwIfNotGraceful(e, defaultValue);
    }
  }

  /**
   * Returns the assignment for the provided feature flag key and subject key as a {@link
   * JSONObject}. If the flag is not found, does not match the requested type or is disabled,
   * defaultValue is returned.
   *
   * @param flagKey the feature flag key
   * @param subjectKey the subject key
   * @param defaultValue the default value to return if the flag is not found
   * @return the JSON string value of the assignment
   */
  public JSONObject getJSONAssignment(String flagKey, String subjectKey, JSONObject defaultValue) {
    return getJSONAssignment(flagKey, subjectKey, new SubjectAttributes(), defaultValue);
  }

  /**
   * Returns the assignment for the provided feature flag key and subject key as a {@link
   * JSONObject}. If the flag is not found, does not match the requested type or is disabled,
   * defaultValue is returned.
   *
   * @param flagKey the feature flag key
   * @param subjectKey the subject key
   * @param defaultValue the default value to return if the flag is not found
   * @return the JSON string value of the assignment
   */
  public JSONObject getJSONAssignment(
      String flagKey,
      String subjectKey,
      SubjectAttributes subjectAttributes,
      JSONObject defaultValue) {
    try {
      EppoValue value =
          this.getTypedAssignment(
              flagKey,
              subjectKey,
              subjectAttributes,
              EppoValue.valueOf(defaultValue.toString()),
              VariationType.JSON);
      return parseJsonString(value.stringValue());
    } catch (Exception e) {
      return throwIfNotGraceful(e, defaultValue);
    }
  }

  /**
   * Returns the assignment for the provided feature flag key, subject key and subject attributes as
   * a JSON string. If the flag is not found, does not match the requested type or is disabled,
   * defaultValue is returned.
   *
   * @param flagKey the feature flag key
   * @param subjectKey the subject key
   * @param defaultValue the default value to return if the flag is not found
   * @return the JSON string value of the assignment
   */
  public String getJSONStringAssignment(
      String flagKey, String subjectKey, SubjectAttributes subjectAttributes, String defaultValue) {
    try {
      EppoValue value =
          this.getTypedAssignment(
              flagKey,
              subjectKey,
              subjectAttributes,
              EppoValue.valueOf(defaultValue),
              VariationType.JSON);
      return value.stringValue();
    } catch (Exception e) {
      return throwIfNotGraceful(e, defaultValue);
    }
  }

  /**
   * Returns the assignment for the provided feature flag key and subject key as a JSON String. If
   * the flag is not found, does not match the requested type or is disabled, defaultValue is
   * returned.
   *
   * @param flagKey the feature flag key
   * @param subjectKey the subject key
   * @param defaultValue the default value to return if the flag is not found
   * @return the JSON string value of the assignment
   */
  public String getJSONStringAssignment(String flagKey, String subjectKey, String defaultValue) {
    return this.getJSONStringAssignment(flagKey, subjectKey, new SubjectAttributes(), defaultValue);
  }

  @Nullable private JSONObject parseJsonString(String jsonString) {
    try {
      return new JSONObject(jsonString);
    } catch (JSONException e) {
      return null;
    }
  }

  private <T> T throwIfNotGraceful(Exception e, T defaultValue) {
    if (this.isGracefulMode) {
      Log.i(TAG, "error getting assignment value: " + e.getMessage());
      return defaultValue;
    }
    throw new RuntimeException(e);
  }

  public static EppoClient getInstance() throws NotInitializedException {
    if (EppoClient.instance == null) {
      throw new NotInitializedException();
    }

    return EppoClient.instance;
  }

  public static class Builder {
    @NonNull private String host = DEFAULT_HOST;
    @Nullable private Application application;
    @Nullable private String apiKey;
    @Nullable private InitializationCallback callback;
    @Nullable private AssignmentLogger assignmentLogger;
    @Nullable private EppoHttpClient httpClient;
    @Nullable private ConfigurationStore configStore;
    private boolean isGracefulMode = DEFAULT_IS_GRACEFUL_MODE;
    private boolean obfuscateConfig = DEFAULT_OBFUSCATE_CONFIG;

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

    public Builder callback(InitializationCallback callback) {
      this.callback = callback;
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

    Builder httpClient(@Nullable EppoHttpClient httpClient) {
      this.httpClient = httpClient;
      return this;
    }

    Builder configStore(ConfigurationStore configStore) {
      this.configStore = configStore;
      return this;
    }

    public EppoClient buildAndInit() {
      if (application == null) {
        throw new MissingApplicationException();
      }
      if (apiKey == null) {
        throw new MissingApiKeyException();
      }
      boolean shouldInstantiate = instance == null;
      if (!shouldInstantiate && ActivityManager.isRunningInTestHarness()) {
        // Always recreate for tests
        Log.d(TAG, "Recreating instance on init() for test");
        shouldInstantiate = true;
      } else {
        Log.w(TAG, "Eppo Client instance already initialized");
      }
      if (!shouldInstantiate) {
        return instance;
      }
      if (httpClient == null) {
        String sdkName = obfuscateConfig ? "android" : "android-debug";
        httpClient = new EppoHttpClient(host, apiKey, sdkName);
      }
      if (configStore == null) {
        // Cache at a per-API key level (useful for development)
        String cacheFileNameSuffix = safeCacheKey(apiKey);
        configStore = new ConfigurationStore(application, cacheFileNameSuffix);
      }
      ConfigurationRequestor configurationRequestor =
          new ConfigurationRequestor(configStore, httpClient);
      instance =
          new EppoClient(configurationRequestor, assignmentLogger, isGracefulMode, obfuscateConfig);
      instance.refreshConfiguration(callback);
      return instance;
    }
  }
}
