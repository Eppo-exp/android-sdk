package cloud.eppo.android;

import static cloud.eppo.android.util.Utils.logTag;
import static cloud.eppo.android.util.Utils.validateNotEmptyOrNull;

import android.app.ActivityManager;
import android.app.Application;
import android.os.Build;
import android.util.Log;

import com.google.gson.JsonElement;

import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.FlagConfig;
import cloud.eppo.android.dto.SubjectAttributes;
import cloud.eppo.android.exceptions.MissingApiKeyException;
import cloud.eppo.android.exceptions.MissingApplicationException;
import cloud.eppo.android.exceptions.NotInitializedException;
import cloud.eppo.android.logging.AssignmentLogger;

public class EppoClient {
    private static final String TAG = logTag(EppoClient.class);
    private static final String DEFAULT_HOST = "https://fscdn.eppo.cloud";
    private static final boolean DEFAULT_IS_GRACEFUL_MODE = true;

    private final ConfigurationRequestor requestor;
    private final AssignmentLogger assignmentLogger;
    private boolean isGracefulMode;
    private static EppoClient instance;

    // Useful for testing in situations where we want to mock the http client
    private static EppoHttpClient httpClientOverride = null;

    private EppoClient(Application application, String apiKey, String host, AssignmentLogger assignmentLogger,
            boolean isGracefulMode) {
        EppoHttpClient httpClient = httpClientOverride == null ? new EppoHttpClient(host, apiKey) : httpClientOverride;
        ConfigurationStore configStore = new ConfigurationStore(application);
        requestor = new ConfigurationRequestor(configStore, httpClient);
        this.isGracefulMode = isGracefulMode;
        this.assignmentLogger = assignmentLogger;
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

    /*
    private Variation getAssignedVariation(String subjectKey, String experimentKey, int subjectShards,
            List<Variation> variations) {
        int shard = Utils.getShard("assignment-" + subjectKey + "-" + experimentKey, subjectShards);

        for (Variation variation : variations) {
            if (Utils.isShardInRange(shard, variation.getShardRange())) {
                return variation;
            }
        }
        return null;
    }
     */

    //TODO: fix
    protected EppoValue getTypedAssignment(String subjectKey, String flagKey, SubjectAttributes subjectAttributes, EppoValue defaultValue) {
        validateNotEmptyOrNull(subjectKey, "subjectKey must not be empty");
        validateNotEmptyOrNull(flagKey, "flagKey must not be empty");

        FlagConfig flag = requestor.getConfiguration(flagKey);
        if (flag == null) {
            Log.w(TAG, "no configuration found for key: " + flagKey);
            return defaultValue;
        }

        if (!flag.isEnabled()) {
            Log.i(TAG, "no assigned variation because the experiment or feature flag is disabled: " + flagKey);
            return null;
        }

        // TODO: new UFC logic
        /*
        TargetingRule rule = RuleEvaluator.findMatchingRule(subjectAttributes, flag.getRules());
        if (rule == null) {
            Log.i(TAG, "no assigned variation. The subject attributes did not match any targeting rules");
            return null;
        }

        String allocationKey = rule.getAllocationKey();
        Allocation allocation = flag.getAllocations().get(allocationKey);
        if (allocation == null) {
            Log.w(TAG, "unexpected unknown allocation key \"" + allocationKey + "\"");
            return null;
        }

        if (!isInExperimentSample(subjectKey, flagKey, flag.getTotalShards(), allocation.getPercentExposure())) {
            Log.i(TAG, "no assigned variation. The subject is not part of the sample population");
            return null;
        }

        Variation assignedVariation = getAssignedVariation(subjectKey, flagKey, flag.getTotalShards(),
                allocation.getVariations());
        if (assignedVariation == null) {
            Log.i(TAG, "no assigned variation. The subject was not bucketed to a variation.");
            return null;
        }

        if (assignmentLogger != null) {
            String experimentKey = Utils.generateExperimentKey(flagKey, allocationKey);
            String variationToLog = null;
            EppoValue typedValue = assignedVariation.getValue();
            if (typedValue != null) {
                variationToLog = typedValue.stringValue();
            }

            Assignment assignment = Assignment.createWithCurrentDate(
                experimentKey,
                flagKey, 
                allocationKey, 
                variationToLog,
                subjectKey, 
                subjectAttributes
            );
            assignmentLogger.logAssignment(assignment);
        }

        return assignedVariation.getValue();
         */
        return null;
    }

    public boolean getBooleanAssignment(String flagKey, String subjectKey, boolean defaultValue) {
        return this.getBooleanAssignment(flagKey, subjectKey, new SubjectAttributes(), defaultValue);
    }

    public boolean getBooleanAssignment(String flagKey, String subjectKey, SubjectAttributes subjectAttributes, boolean defaultValue) {
        try {
            EppoValue value = this.getTypedAssignment(flagKey, subjectKey, subjectAttributes, EppoValue.valueOf(defaultValue));
            return value.boolValue();
        } catch (Exception e) {
            return throwIfNotGraceful(e, defaultValue);
        }
    }

    public int getIntegerAssignment(String flagKey, String subjectKey, int defaultValue) {
        return getIntegerAssignment(flagKey, subjectKey, new SubjectAttributes(), defaultValue);
    }

    public int getIntegerAssignment(String flagKey, String subjectKey, SubjectAttributes subjectAttributes, int defaultValue) {
        try {
            EppoValue value = this.getTypedAssignment(flagKey, subjectKey, subjectAttributes, EppoValue.valueOf(defaultValue));
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
            EppoValue value = this.getTypedAssignment(subjectKey, flagKey, subjectAttributes, EppoValue.valueOf(defaultValue));
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
            EppoValue value = this.getTypedAssignment(flagKey, subjectKey, subjectAttributes, EppoValue.valueOf(defaultValue));
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
            EppoValue value = this.getTypedAssignment(subjectKey, flagKey, subjectAttributes, EppoValue.valueOf(defaultValue));
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
