package cloud.eppo.android;

import static cloud.eppo.android.util.Utils.validateNotEmptyOrNull;

import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import cloud.eppo.android.dto.Allocation;
import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.FlagConfig;
import cloud.eppo.android.dto.SubjectAttributes;
import cloud.eppo.android.dto.TargetingRule;
import cloud.eppo.android.dto.Variation;
import cloud.eppo.android.exceptions.NotInitializedException;
import cloud.eppo.android.util.Utils;

public class EppoClient {
    private static final String TAG = EppoClient.class.getCanonicalName();
    private static final String URL = "https://eppo.cloud/api";

    private final ConfigurationRequestor requestor;
    private static EppoClient instance;

    private EppoClient(String apiKey) {
        EppoHttpClient httpClient = new EppoHttpClient(URL, apiKey);
        requestor = new ConfigurationRequestor(new ConfigurationStore(), httpClient);
    }

    public static EppoClient init(String apiKey) {
        return init(apiKey, null);
    }

    public static EppoClient init(String apiKey, InitializationCallback callback) {
        if (instance == null) {
            instance = new EppoClient(apiKey);
            instance.refreshConfiguration(callback);
        }
        return instance;
    }

    private void refreshConfiguration(InitializationCallback callback) {
        requestor.load(callback);
    }

    private EppoValue getSubjectVariationOverride(String subjectKey, FlagConfig flagConfig) {
        String subjectHash = Utils.getMD5Hex(subjectKey);
        if (flagConfig.getOverrides().containsKey(subjectHash)) {
            return EppoValue.valueOf(flagConfig.getOverrides().get(subjectHash));
        }
        return EppoValue.valueOf();
    }

    private boolean isInExperimentSample(String subjectKey, String experimentKey, int subjectShards, float percentageExposure) {
        int shard = Utils.getShard("exposure-" + subjectKey + "-" + experimentKey, subjectShards);
        return shard <= percentageExposure * subjectShards;
    }

    private Variation getAssignedVariation(String subjectKey, String experimentKey, int subjectShards, List<Variation> variations) {
        int shard = Utils.getShard("assignment-" + subjectKey + "-" + experimentKey, subjectShards);

        for (Variation variation : variations) {
            if (Utils.isShardInRange(shard, variation.getShardRange())) {
                return variation;
            }
        }
        return null;
    }

    public String getAssignment(String subjectKey, String flagKey) {
        return getAssignment(subjectKey, flagKey, new SubjectAttributes());
    }

    public String getAssignment(String subjectKey, String flagKey, SubjectAttributes subjectAttributes) {
        validateNotEmptyOrNull(subjectKey, "subjectKey must not be empty");
        validateNotEmptyOrNull(flagKey, "flagKey must not be empty");

        FlagConfig flag = requestor.getConfiguration(flagKey);
        if (flag == null) {
            Log.w(TAG, "no configuration found for key: " + flagKey);
            return null;
        }

        EppoValue subjectVariationOverride = getSubjectVariationOverride(subjectKey, flag);
        if (!subjectVariationOverride.isNull()) {
            return subjectVariationOverride.stringValue();
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

        Allocation allocation = flag.getAllocations().get(rule.getAllocationKey());
        if (!isInExperimentSample(subjectKey, flagKey, flag.getSubjectShards(), allocation.getPercentExposure())) {
            Log.i(TAG, "no assigned variation. The subject is not part of the sample population");
            return null;
        }

        Variation assignedVariation = getAssignedVariation(subjectKey, flagKey, flag.getSubjectShards(), allocation.getVariations());
        if (assignedVariation == null) {
            return null;
        }

        // TODO assignment logging

        return assignedVariation.getValue();
    }

    public static EppoClient getInstance() throws NotInitializedException {
        if (EppoClient.instance == null) {
            throw new NotInitializedException();
        }

        return EppoClient.instance;
    }
}

