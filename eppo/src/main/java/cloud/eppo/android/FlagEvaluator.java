package cloud.eppo.android;

import java.util.Date;

import cloud.eppo.android.dto.Allocation;
import cloud.eppo.android.dto.FlagConfig;
import cloud.eppo.android.dto.SubjectAttributes;

public class FlagEvaluator {

    public static FlagEvaluationResult evaluateFlag(FlagConfig flag, String subjectKey, SubjectAttributes subjectAttributes, boolean isConfigObfuscated) {
        Date now = new Date();

        for (Allocation allocation : flag.getAllocations()) {
            if (allocation.getStartAt() != null && allocation.getStartAt().after(now)) {
                // Allocation not yet active
                continue;
            }
            if (allocation.getEndAt() != null && allocation.getEndAt().before(now)) {
                // Allocation no longer active
                continue;
            }

            if (allocation.getRules() != null && !allocation.getRules().isEmpty() && RuleEvaluator.findMatchingRule(subjectAttributes, allocation.getRules()) == null) {
                // Rules are defined, but none match
                continue;
            }

            // TODO: more logic
        }

        return null;
    }

}
