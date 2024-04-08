package cloud.eppo.android;

import static cloud.eppo.android.util.Utils.logTag;
import static cloud.eppo.android.util.Utils.validateNotEmptyOrNull;

import android.app.ActivityManager;
import android.app.Application;
import android.os.Build;
import android.util.Log;

import com.google.gson.JsonElement;

import java.util.Date;
import java.util.List;

import cloud.eppo.android.dto.Allocation;
import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.FlagConfig;
import cloud.eppo.android.dto.SubjectAttributes;
import cloud.eppo.android.dto.TargetingRule;
import cloud.eppo.android.dto.Variation;
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
    private boolean isGracefulMode;
    private static EppoClient instance;

    // Useful for testing in situations where we want to mock the http client
    private static EppoHttpClient httpClientOverride = null;

    private EppoClient(Application application, String apiKey, String host, AssignmentLogger assignmentLogger, boolean isGracefulMode) {
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

    private EppoValue getSubjectVariationOverride(String subjectKey, FlagConfig flagConfig) {
        String subjectHash = Utils.getMD5Hex(subjectKey);
        if (flagConfig.getTypedOverrides().containsKey(subjectHash)) {
            return EppoValue.valueOf(flagConfig.getTypedOverrides().get(subjectHash));
        }
        return EppoValue.valueOf();
    }

    private boolean isInExperimentSample(String subjectKey, String experimentKey, int subjectShards,
            float percentageExposure) {
        int shard = Utils.getShard("exposure-" + subjectKey + "-" + experimentKey, subjectShards);
        return shard <= percentageExposure * subjectShards;
    }

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

    protected EppoValue getTypedAssignment(String subjectKey, String flagKey, SubjectAttributes subjectAttributes) {
        validateNotEmptyOrNull(subjectKey, "subjectKey must not be empty");
        validateNotEmptyOrNull(flagKey, "flagKey must not be empty");

        FlagConfig flag = requestor.getConfiguration(flagKey);
        if (flag == null) {
            Log.w(TAG, "no configuration found for key: " + flagKey);
            return null;
        }

        EppoValue subjectVariationOverride = getSubjectVariationOverride(subjectKey, flag);
        if (!subjectVariationOverride.isNull()) {
            return subjectVariationOverride;
        }

        if (!flag.isEnabled()) {
            Log.i(TAG, "no assigned variation because the experiment or feature flag is disabled: " + flagKey);
            return null;
        }

        TargetingRule rule = RuleEvaluator.findMatchingRule(subjectAttributes, flag.getRules());
        if (rule == null) {
            Log.i(TAG, "no assigned variation. The subject attributes did not match any targeting rules");
            return null;
        }

        String allocationKey = rule.getAllocationKey();
        Allocation allocation = flag.getAllocations().get(allocationKey);
        if (allocation == null) {
            Log.w(TAG, "unexpected unknown allocation key \""+allocationKey+"\"");
            return null;
        }

        if (!isInExperimentSample(subjectKey, flagKey, flag.getSubjectShards(), allocation.getPercentExposure())) {
            Log.i(TAG, "no assigned variation. The subject is not part of the sample population");
            return null;
        }

        Variation assignedVariation = getAssignedVariation(subjectKey, flagKey, flag.getSubjectShards(),
                allocation.getVariations());
        if (assignedVariation == null) {
            Log.i(TAG, "no assigned variation. The subject was not bucketed to a variation.");
            return null;
        }

        if (assignmentLogger != null) {
            String experimentKey = Utils.generateExperimentKey(flagKey, allocationKey);
            String variationToLog = null;
            EppoValue typedValue = assignedVariation.getTypedValue();
            if (typedValue != null) {
                variationToLog = typedValue.stringValue();
            }

            Assignment assignment = new Assignment(experimentKey,
                    flagKey, allocationKey, variationToLog,
                    subjectKey, Utils.getISODate(new Date()), subjectAttributes);
            assignmentLogger.logAssignment(assignment);
        }

        return assignedVariation.getTypedValue();
    }

    public String getAssignment(String subjectKey, String flagKey, SubjectAttributes subjectAttributes) {
        try {
            return this.getStringAssignment(subjectKey, flagKey, subjectAttributes);
        } catch (Exception e) {
            return throwIfNotGraceful(e);
        }
    }

    public String getAssignment(String subjectKey, String flagKey) {
        try {
            return this.getStringAssignment(subjectKey, flagKey);
        } catch (Exception e) {
            return throwIfNotGraceful(e);
        }
    }

    public String getStringAssignment(String subjectKey, String flagKey, SubjectAttributes subjectAttributes) {
        try {
            EppoValue value = this.getTypedAssignment(subjectKey, flagKey, subjectAttributes);
            if (value == null) {
                return null;
            }

            return value.stringValue();
        } catch (Exception e) {
            return throwIfNotGraceful(e);
        }
    }

    public String getStringAssignment(String subjectKey, String flagKey) {
        try {
            return this.getStringAssignment(subjectKey, flagKey, new SubjectAttributes());
        } catch (Exception e) {
            return throwIfNotGraceful(e);
        }
    }

    public Boolean getBooleanAssignment(String subjectKey, String flagKey, SubjectAttributes subjectAttributes) {
        try {
            EppoValue value = this.getTypedAssignment(subjectKey, flagKey, subjectAttributes);
            if (value == null) {
                return null;
            }

            return value.boolValue();
        } catch (Exception e) {
            return throwIfNotGraceful(e);
        }
    }

    public Boolean getBooleanAssignment(String subjectKey, String flagKey) {
        try {
            return this.getBooleanAssignment(subjectKey, flagKey, new SubjectAttributes());
        } catch (Exception e) {
            return throwIfNotGraceful(e);
        }
    }

    public Double getDoubleAssignment(String subjectKey, String flagKey, SubjectAttributes subjectAttributes) {
        try {
            EppoValue value = this.getTypedAssignment(subjectKey, flagKey, subjectAttributes);
            if (value == null) {
                return null;
            }

            return value.doubleValue();
        } catch (Exception e) {
            return throwIfNotGraceful(e);
        }
    }

    public Double getDoubleAssignment(String subjectKey, String flagKey) {
        try {
            return this.getDoubleAssignment(subjectKey, flagKey, new SubjectAttributes());
        } catch (Exception e) {
            return throwIfNotGraceful(e);
        }
    }

    public String getJSONStringAssignment(String subjectKey, String flagKey, SubjectAttributes subjectAttributes) {
        try {
            return this.getParsedJSONAssignment(subjectKey, flagKey, subjectAttributes).toString();
        } catch (Exception e) {
            return throwIfNotGraceful(e);
        }
    }

    public String getJSONStringAssignment(String subjectKey, String flagKey) {
        try {
            return this.getParsedJSONAssignment(subjectKey, flagKey, new SubjectAttributes()).toString();
        } catch (Exception e) {
            return throwIfNotGraceful(e);
        }
    }

    public JsonElement getParsedJSONAssignment(String subjectKey, String flagKey, SubjectAttributes subjectAttributes) {
        try {
            EppoValue value = this.getTypedAssignment(subjectKey, flagKey, subjectAttributes);
            if (value == null) {
                return null;
            }

            return value.jsonValue();
        } catch (Exception e) {
            return throwIfNotGraceful(e);
        }
    }

    public JsonElement getParsedJSONAssignment(String subjectKey, String flagKey) {
        try {
            return this.getParsedJSONAssignment(subjectKey, flagKey, new SubjectAttributes());
        } catch (Exception e) {
            return throwIfNotGraceful(e);
        }
    }

    private <T> T throwIfNotGraceful(Exception e) {
        if (this.isGracefulMode) {
            Log.i(TAG, "error getting assignment value: " + e.getMessage());
            return null;
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
