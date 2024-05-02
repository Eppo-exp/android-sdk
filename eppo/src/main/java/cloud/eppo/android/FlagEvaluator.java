package cloud.eppo.android;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import cloud.eppo.android.dto.Allocation;
import cloud.eppo.android.dto.FlagConfig;
import cloud.eppo.android.dto.Range;
import cloud.eppo.android.dto.Shard;
import cloud.eppo.android.dto.Split;
import cloud.eppo.android.dto.SubjectAttributes;
import cloud.eppo.android.dto.Variation;
import cloud.eppo.android.util.Utils;

public class FlagEvaluator {

    public static FlagEvaluationResult evaluateFlag(FlagConfig flag, String subjectKey, SubjectAttributes subjectAttributes, boolean isConfigObfuscated) {
        Date now = new Date();

        Variation variation = null;
        String allocationKey = null;
        Map<String, String> extraLogging = new HashMap<>();
        boolean doLog = true;

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

            // This allocation has matched; find variation
            for (Split split : allocation.getSplits()) {
                if (allShardsMatch(split, subjectKey, flag.getTotalShards())) {
                    // Variation and extra logging is determined by the relevant split
                    variation = flag.getVariations().get(split.getVariationKey());
                    extraLogging = split.getExtraLogging();
                    break;
                }
            }

            // We only evaluate the first relevant allocation
            allocationKey = allocation.getKey();
            // doLog is determined by the allocation
            doLog = allocation.doLog();
            break;
        }

        FlagEvaluationResult evaluationResult = new FlagEvaluationResult();
        evaluationResult.setFlagKey(flag.getKey());
        evaluationResult.setSubjectKey(subjectKey);
        evaluationResult.setSubjectAttributes(subjectAttributes);
        evaluationResult.setAllocationKey(allocationKey);
        evaluationResult.setVariation(variation);
        evaluationResult.setExtraLogging(extraLogging);
        evaluationResult.setDoLog(doLog);

        return evaluationResult;
    }

    private static boolean allShardsMatch(Split split, String subjectKey, int totalShards) {
        boolean allShardsMatch = true; // Default to matching if no explicit shards
        for (Shard shard : split.getShards()) {
            if (!matchesShard(shard, subjectKey, totalShards)) {
                allShardsMatch = false;
                break;
            }
        }
        return allShardsMatch;
    }

    private static boolean matchesShard(Shard shard, String subjectKey, int totalShards) {
        String hashKey = shard.getSalt()+"-"+subjectKey;
        int assignedShard = Utils.getShard(hashKey, totalShards);
        boolean inRange = false;
        for (Range range : shard.getRanges()) {
            if (assignedShard >= range.getStart() && assignedShard < range.getEnd()) {
                inRange = true;
                break;
            }
        }
        return inRange;
    }

}
