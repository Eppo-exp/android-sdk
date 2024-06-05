package cloud.eppo.android;

import static cloud.eppo.android.util.Utils.getMD5Hex;
import static cloud.eppo.android.util.Utils.logTag;
import static cloud.eppo.android.util.Utils.safeCacheKey;
import static cloud.eppo.android.util.Utils.validateNotEmptyOrNull;

import android.app.ActivityManager;
import android.app.Application;
import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.util.HashMap;
import java.util.Map;

import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.FlagConfig;
import cloud.eppo.android.dto.SubjectAttributes;
import cloud.eppo.android.dto.VariationType;
import cloud.eppo.android.exceptions.MissingApiKeyException;
import cloud.eppo.android.exceptions.MissingApplicationException;
import cloud.eppo.android.exceptions.NotInitializedException;
import cloud.eppo.android.logging.Assignment;
import cloud.eppo.android.logging.AssignmentLogger;
import cloud.eppo.android.util.Utils;

public class EppoClient {
    private static final String TAG = logTag(EppoClient.class);
    private static final String DEFAULT_HOST = "https://fscdn.eppo.cloud";
    private static final boolean DEFAULT_IS_GRACEFUL_MODE = true;

    private final ConfigurationRequestor requestor;
    private final AssignmentLogger assignmentLogger;
    private final boolean isGracefulMode;
    private static EppoClient instance;

    // Field useful for toggling off obfuscation for development and testing (accessed via reflection)
    /** @noinspection FieldMayBeFinal*/
    private static boolean isConfigObfuscated = true;

    // Fields useful for testing in situations where we want to mock the http client or configuration store (accessed via reflection)
    /** @noinspection FieldMayBeFinal*/
    private static EppoHttpClient httpClientOverride = null;
    /** @noinspection FieldMayBeFinal*/
    private static ConfigurationStore configurationStoreOverride = null;


    private EppoClient(Application application, String apiKey, String host, AssignmentLogger assignmentLogger,
            boolean isGracefulMode) {
        EppoHttpClient httpClient = buildHttpClient(apiKey, host);
        String cacheFileNameSuffix = safeCacheKey(apiKey); // Cache at a per-API key level (useful for development)
        ConfigurationStore configStore = configurationStoreOverride == null ? new ConfigurationStore(application, cacheFileNameSuffix) : configurationStoreOverride;
        requestor = new ConfigurationRequestor(configStore, httpClient);
        this.isGracefulMode = isGracefulMode;
        this.assignmentLogger = assignmentLogger;
    }

    private EppoHttpClient buildHttpClient(String apiKey, String host) {
        EppoHttpClient httpClient;
        if (httpClientOverride != null) {
            // Test/Debug - Client is mocked entirely
            httpClient = httpClientOverride;
        } else if (!isConfigObfuscated) {
            // Test/Debug - Client should request unobfuscated configurations; done by changing SDK name
            httpClient = new EppoHttpClient(host, apiKey) {
                @Override
                protected String getSdkName() {
                    return "android-debug";
                }
            };
        } else {
            // Normal operation
            httpClient = new EppoHttpClient(host, apiKey);
        }
        return httpClient;
    }

    /** @noinspection unused*/
    public static EppoClient init(Application application, String apiKey) {
        return init(application, apiKey, DEFAULT_HOST, null, null, DEFAULT_IS_GRACEFUL_MODE);
    }

    public static EppoClient init(Application application, String apiKey, String host, InitializationCallback callback,
                                  AssignmentLogger assignmentLogger, boolean isGracefulMode) {
        if (application == null) {
            throw new MissingApplicationException();
        }

        if (apiKey == null) {
            throw new MissingApiKeyException();
        }

        boolean shouldCreateInstance = instance == null;
        if (!shouldCreateInstance && ActivityManager.isRunningInTestHarness()) {
            // Always recreate for tests
            Log.d(TAG, "Recreating instance on init() for test");
            shouldCreateInstance = true;
        } else {
            Log.w(TAG, "Eppo Client instance already initialized");
        }

        if (shouldCreateInstance) {
            instance = new EppoClient(application, apiKey, host, assignmentLogger, isGracefulMode);
            instance.refreshConfiguration(callback);
        }

        return instance;
    }

    /**
     * Ability to ad-hoc kick off a configuration load.
     * Will load from a filesystem cached file as well as fire off a HTTPS request for an updated
     * configuration. If the cache load finishes first, those assignments will be used until the
     * fetch completes.
     *
     * Deprecated, as we plan to make a more targeted and configurable way to do so in the future.
     * @param callback methods to call when loading succeeds/fails. Note that the success callback
     *                 will be called as soon as either a configuration is loaded from the cache or
     *                 fetched--whichever finishes first. Error callback will called if both
     *                 attempts fail.
     */
    @Deprecated
    public void refreshConfiguration(InitializationCallback callback) {
        requestor.load(callback);
    }

    protected EppoValue getTypedAssignment(String flagKey, String subjectKey, SubjectAttributes subjectAttributes, EppoValue defaultValue, VariationType expectedType) {
        validateNotEmptyOrNull(flagKey, "flagKey must not be empty");
        validateNotEmptyOrNull(subjectKey, "subjectKey must not be empty");

        String flagKeyForLookup = flagKey;
        if (isConfigObfuscated) {
            flagKeyForLookup = getMD5Hex(flagKey);
        }

        FlagConfig flag = requestor.getConfiguration(flagKeyForLookup);
        if (flag == null) {
            Log.w(TAG, "no configuration found for key: " + flagKey);
            return defaultValue;
        }

        if (!flag.isEnabled()) {
            Log.i(TAG, "no assigned variation because the experiment or feature flag is disabled: " + flagKey);
            return defaultValue;
        }

        if (flag.getVariationType() != expectedType) {
            Log.w(TAG, "no assigned variation because the flag type doesn't match the requested type: "+flagKey+" has type "+flag.getVariationType()+", requested "+expectedType);
            return defaultValue;
        }

        FlagEvaluationResult evaluationResult = FlagEvaluator.evaluateFlag(flag, flagKey, subjectKey, subjectAttributes, isConfigObfuscated);
        EppoValue assignedValue = evaluationResult.getVariation() != null ? evaluationResult.getVariation().getValue() : null;

        if (assignedValue != null && !valueTypeMatchesExpected(expectedType, assignedValue)) {
            Log.w(TAG, "no assigned variation because the flag type doesn't match the variation type: "+flagKey+" has type "+flag.getVariationType()+", variation value is "+assignedValue);
            return defaultValue;
        }

        if (assignedValue != null && assignmentLogger != null && evaluationResult.doLog()) {
            String experimentKey = Utils.generateExperimentKey(evaluationResult.getFlagKey(), evaluationResult.getAllocationKey());
            String variationKey = evaluationResult.getVariation().getKey();
            String allocationKey = evaluationResult.getAllocationKey();
            Map<String, String> extraLogging = evaluationResult.getExtraLogging();
            Map<String, String> metaData = new HashMap<>();
            metaData.put("obfuscated", Boolean.valueOf(isConfigObfuscated).toString());
            metaData.put("sdkLanguage", "Java (Android)");
            metaData.put("sdkLibVersion", BuildConfig.EPPO_VERSION);

            Assignment assignment = Assignment.createWithCurrentDate(
                    experimentKey,
                    flagKey,
                    allocationKey,
                    variationKey,
                    subjectKey,
                    subjectAttributes,
                    extraLogging,
                    metaData
            );
            try {
                assignmentLogger.logAssignment(assignment);
            } catch (Exception e) {
                Log.w(TAG, "Error logging assignment: "+e.getMessage(), e);
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
                typeMatch = value.isNumeric()
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
                typeMatch = value.isString()
                        // Eppo leaves JSON as a JSON string; to verify it's valid we attempt to parse
                        && parseJsonString(value.stringValue()) != null;
                break;
            default:
                throw new IllegalArgumentException("Unexpected type for type checking: "+expectedType);
        }

        return typeMatch;
    }

    public boolean getBooleanAssignment(String flagKey, String subjectKey, boolean defaultValue) {
        return this.getBooleanAssignment(flagKey, subjectKey, new SubjectAttributes(), defaultValue);
    }

    public boolean getBooleanAssignment(String flagKey, String subjectKey, SubjectAttributes subjectAttributes, boolean defaultValue) {
        try {
            EppoValue value = this.getTypedAssignment(flagKey, subjectKey, subjectAttributes, EppoValue.valueOf(defaultValue), VariationType.BOOLEAN);
            return value.booleanValue();
        } catch (Exception e) {
            return throwIfNotGraceful(e, defaultValue);
        }
    }

    public int getIntegerAssignment(String flagKey, String subjectKey, int defaultValue) {
        return getIntegerAssignment(flagKey, subjectKey, new SubjectAttributes(), defaultValue);
    }

    public int getIntegerAssignment(String flagKey, String subjectKey, SubjectAttributes subjectAttributes, int defaultValue) {
        try {
            EppoValue value = this.getTypedAssignment(flagKey, subjectKey, subjectAttributes, EppoValue.valueOf(defaultValue), VariationType.INTEGER);
            return Double.valueOf(value.doubleValue()).intValue();
        } catch (Exception e) {
            return throwIfNotGraceful(e, defaultValue);
        }
    }

    public Double getDoubleAssignment(String flagKey, String subjectKey, double defaultValue) {
       return getDoubleAssignment(flagKey, subjectKey, new SubjectAttributes(), defaultValue);
    }

    public Double getDoubleAssignment(String flagKey, String subjectKey, SubjectAttributes subjectAttributes, double defaultValue) {
        try {
            EppoValue value = this.getTypedAssignment(flagKey, subjectKey, subjectAttributes, EppoValue.valueOf(defaultValue), VariationType.NUMERIC);
            return value.doubleValue();
        } catch (Exception e) {
            return throwIfNotGraceful(e, defaultValue);
        }
    }

    public String getStringAssignment(String flagKey, String subjectKey, String defaultValue) {
        return this.getStringAssignment(flagKey, subjectKey, new SubjectAttributes(), defaultValue);
    }

    public String getStringAssignment(String flagKey, String subjectKey, SubjectAttributes subjectAttributes, String defaultValue) {
        try {
            EppoValue value = this.getTypedAssignment(flagKey, subjectKey, subjectAttributes, EppoValue.valueOf(defaultValue), VariationType.STRING);
            return value.stringValue();
        } catch (Exception e) {
            return throwIfNotGraceful(e, defaultValue);
        }
    }

    public JsonElement getJSONAssignment(String flagKey, String subjectKey, JsonElement defaultValue) {
        return getJSONAssignment(flagKey, subjectKey, new SubjectAttributes(), defaultValue);
    }

    public JsonElement getJSONAssignment(String flagKey, String subjectKey, SubjectAttributes subjectAttributes, JsonElement defaultValue) {
        try {
            EppoValue value = this.getTypedAssignment(flagKey, subjectKey, subjectAttributes, EppoValue.valueOf(defaultValue.toString()), VariationType.JSON);
            return parseJsonString(value.stringValue());
        } catch (Exception e) {
            return throwIfNotGraceful(e, defaultValue);
        }
    }

    private JsonElement parseJsonString(String jsonString) {

        JsonElement result = null;
        try {
            result = JsonParser.parseString(jsonString);
        } catch (JsonSyntaxException e) {
            // no-op
        }
        return result;
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
        private Application application;
        private String apiKey;
        private String host = DEFAULT_HOST;
        private InitializationCallback callback;
        private AssignmentLogger assignmentLogger;
        private boolean isGracefulMode = DEFAULT_IS_GRACEFUL_MODE;

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

        public EppoClient buildAndInit() {
            return EppoClient.init(application, apiKey, host, callback, assignmentLogger, isGracefulMode);
        }
    }
}
