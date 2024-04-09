package cloud.eppo.android.dto.deserializers;

import static cloud.eppo.android.util.Utils.logTag;

import android.util.Log;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cloud.eppo.android.EppoClient;
import cloud.eppo.android.dto.Allocation;
import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.FlagConfig;
import cloud.eppo.android.dto.OperatorType;
import cloud.eppo.android.dto.RandomizationConfigResponse;
import cloud.eppo.android.dto.ShardRange;
import cloud.eppo.android.dto.TargetingCondition;
import cloud.eppo.android.dto.TargetingRule;
import cloud.eppo.android.dto.Variation;

/**
 *  Hand-rolled deserializer so that we don't rely on annotations and method names, which can be
 *  unreliable when ProGuard minification is in-use and not configured to protect
 *  JSON-deserialization-related classes and annotations.
 */
public class RandomizationConfigResponseDeserializer implements JsonDeserializer<RandomizationConfigResponse> {
    private static final String TAG = logTag(EppoClient.class);

    private final EppoValueAdapter eppoValueAdapter = new EppoValueAdapter();

    @Override
    public RandomizationConfigResponse deserialize(JsonElement rootElement, Type type, JsonDeserializationContext context) throws JsonParseException {
        ConcurrentHashMap<String, FlagConfig> flags = new ConcurrentHashMap<>();
        RandomizationConfigResponse configResponse = new RandomizationConfigResponse();
        configResponse.setFlags(flags); // Default to a response with an empty map of flags

        if (rootElement == null || !rootElement.isJsonObject()) {
            Log.w(TAG, "no top-level JSON object");
            return configResponse;
        }

        JsonObject rootObject = rootElement.getAsJsonObject();
        JsonElement flagsElement = rootObject.get("flags");
        if (flagsElement == null) {
            Log.w(TAG, "no root-level flags property");
            return configResponse;
        }

        JsonObject flagsObject = flagsElement.getAsJsonObject();
        for (Map.Entry<String, JsonElement> flagEntry : flagsObject.entrySet()) {
            String flagKey = flagEntry.getKey();
            FlagConfig flagConfig = deserializeFlag(flagEntry.getValue(), type, context);
            flags.put(flagKey, flagConfig); // Note this is adding to the map already plugged into configResponse
        }

        return configResponse;
    }

    private FlagConfig deserializeFlag(JsonElement jsonElement, Type type, JsonDeserializationContext context) {
        JsonObject flagObject = jsonElement.getAsJsonObject();
        int subjectShards = flagObject.get("subjectShards").getAsInt();
        boolean enabled = flagObject.get("enabled").getAsBoolean();
        Map<String, String> typedOverrides = deserializeTypedOverrides(flagObject.get("typedOverrides"));
        List<TargetingRule> rules = deserializeTargetingRules(flagObject.get("rules"), type, context);
        Map<String, Allocation> allocations = deserializeAllocations(flagObject.get("allocations"), type, context);

        FlagConfig flagConfig = new FlagConfig();
        flagConfig.setEnabled(enabled);
        flagConfig.setSubjectShards(subjectShards);
        flagConfig.setTypedOverrides(typedOverrides);
        flagConfig.setRules(rules);
        flagConfig.setAllocations(allocations);
        return flagConfig;
    }

    private Map<String, String> deserializeTypedOverrides(JsonElement jsonElement) {
        Map<String, String> typedOverrides = new HashMap<>();
        if (jsonElement == null) {
            return typedOverrides;
        }

        for (Map.Entry<String, JsonElement> typedOverridesEntry : jsonElement.getAsJsonObject().entrySet()) {
            typedOverrides.put(typedOverridesEntry.getKey(), typedOverridesEntry.getValue().getAsString());

        }

        return typedOverrides;
    }

    private List<TargetingRule> deserializeTargetingRules(JsonElement jsonElement, Type type, JsonDeserializationContext context) {
        List<TargetingRule> targetingRules = new ArrayList<>();
        if (jsonElement == null) {
            return targetingRules;
        }

        for (JsonElement ruleElement : jsonElement.getAsJsonArray()) {
            JsonObject rulesObject = ruleElement.getAsJsonObject();
            String allocationKey = rulesObject.get("allocationKey").getAsString();
            List<TargetingCondition> conditions = deserializeTargetingCondition(rulesObject.get("conditions"), type, context);

            TargetingRule targetingRule = new TargetingRule();
            targetingRule.setAllocationKey(allocationKey);
            targetingRule.setConditions(conditions);
            targetingRules.add(targetingRule);
        }

        return targetingRules;
    }

    private List<TargetingCondition> deserializeTargetingCondition(JsonElement jsonElement, Type type, JsonDeserializationContext context) {
        List<TargetingCondition> conditions = new ArrayList<>();
        if (jsonElement == null) {
            return conditions;
        }

        for (JsonElement conditionElement : jsonElement.getAsJsonArray()) {
            JsonObject conditionObject = conditionElement.getAsJsonObject();
            String attribute = conditionObject.get("attribute").getAsString();
            String operatorKey = conditionObject.get("operator").getAsString();
            OperatorType operator = OperatorType.fromString(operatorKey);
            if (operator == null) {
                Log.w(TAG, "Unknown operator \""+operatorKey+"\"");
                continue;
            }
            EppoValue value = eppoValueAdapter.deserialize(conditionObject.get("value"), type, context);

            TargetingCondition condition = new TargetingCondition();
            condition.setAttribute(attribute);
            condition.setOperator(operator);
            condition.setValue(value);
            conditions.add(condition);
        }

        return conditions;
    }

    private Map<String, Allocation> deserializeAllocations(JsonElement jsonElement, Type type, JsonDeserializationContext context) {
        Map<String, Allocation> allocations = new HashMap<>();
        if (jsonElement == null) {
            return allocations;
        }

        for (Map.Entry<String, JsonElement> allocationEntry : jsonElement.getAsJsonObject().entrySet()) {
            String allocationKey = allocationEntry.getKey();
            JsonObject allocationObject = allocationEntry.getValue().getAsJsonObject();
            float percentExposure = allocationObject.get("percentExposure").getAsFloat();
            List<Variation> variations = deserializeVariations(allocationObject.get("variations"), type, context);

            Allocation allocation = new Allocation();
            allocation.setPercentExposure(percentExposure);
            allocation.setVariations(variations);
            allocations.put(allocationKey, allocation);
        }

        return allocations;
    }

    private List<Variation> deserializeVariations(JsonElement jsonElement, Type type, JsonDeserializationContext context) {
        List<Variation> variations = new ArrayList<>();
        if (jsonElement == null) {
            return variations;
        }

        for (JsonElement variationElement : jsonElement.getAsJsonArray()) {
            JsonObject variationObject = variationElement.getAsJsonObject();

            JsonObject shardRangeObject = variationObject.get("shardRange").getAsJsonObject();
            int shardRangeStart = shardRangeObject.get("start").getAsInt();
            int shardRangeEnd = shardRangeObject.get("end").getAsInt();
            ShardRange shardRange = new ShardRange();
            shardRange.setStart(shardRangeStart);
            shardRange.setEnd(shardRangeEnd);

            EppoValue typedValue = eppoValueAdapter.deserialize(variationObject.get("typedValue"), type, context);

            Variation variation = new Variation();
            variation.setShardRange(shardRange);
            variation.setTypedValue(typedValue);
            variations.add(variation);
        }

        return variations;
    }
}
