package cloud.eppo.android;

import static cloud.eppo.android.util.Utils.getMD5Hex;
import static cloud.eppo.android.util.Utils.logTag;
import static cloud.eppo.android.util.Utils.validateNotEmptyOrNull;

import android.app.ActivityManager;
import android.app.Application;
import android.os.Build;
import android.util.Log;

import com.google.gson.JsonElement;

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

public class EppoClient {
    private static final String TAG = logTag(EppoClient.class);
    private static final String DEFAULT_HOST = "https://fscdn.eppo.cloud";
    private static final boolean DEFAULT_IS_GRACEFUL_MODE = true;

    private final ConfigurationRequestor requestor;
    private final AssignmentLogger assignmentLogger;
    private final boolean isGracefulMode;
    private static EppoClient instance;

    // Useful for development and testing
    private static boolean isConfigObfuscated = true;

    // Useful for testing in situations where we want to mock the http client
    private static EppoHttpClient httpClientOverride = null;

    private EppoClient(Application application, String apiKey, String host, AssignmentLogger assignmentLogger,
            boolean isGracefulMode) {
        EppoHttpClient httpClient = buildHttpClient(apiKey, host);
        ConfigurationStore configStore = new ConfigurationStore(application);
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
        if (!shouldCreateInstance && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            shouldCreateInstance = ActivityManager.isRunningInTestHarness();
        }

        if (shouldCreateInstance) {
            instance = new EppoClient(application, apiKey, host, assignmentLogger, isGracefulMode);
            instance.refreshConfiguration(callback);
        }

        return instance;
    }

    private void refreshConfiguration(InitializationCallback callback) {
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
            String experimentKey = evaluationResult.getFlagKey();
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
                        // Java has no isInteger check so we check value == floor == ceil
                        && Math.floor(value.doubleValue()) == Math.ceil(value.doubleValue())
                        && Math.floor(value.doubleValue()) == value.doubleValue();
                break;
            case NUMERIC:
                typeMatch = value.isNumeric();
                break;
            case STRING:
                typeMatch = value.isString();
                break;
            case JSON:
                typeMatch = value.isJson();
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

    public Double getDoubleAssignment(String subjectKey, String flagKey, double defaultValue) {
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

    public String getStringAssignment(String subjectKey, String flagKey, String defaultValue) {
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

    public JsonElement getJSONAssignment(String subjectKey, String flagKey, JsonElement defaultValue) {
        return getJSONAssignment(flagKey, subjectKey, new SubjectAttributes(), defaultValue);
    }

    public JsonElement getJSONAssignment(String flagKey, String subjectKey, SubjectAttributes subjectAttributes, JsonElement defaultValue) {
        try {
            EppoValue value = this.getTypedAssignment(flagKey, subjectKey, subjectAttributes, EppoValue.valueOf(defaultValue), VariationType.JSON);
            return value.jsonValue();
        } catch (Exception e) {
            return throwIfNotGraceful(e, defaultValue);
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
