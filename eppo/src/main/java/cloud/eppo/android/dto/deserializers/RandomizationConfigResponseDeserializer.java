package cloud.eppo.android.dto.deserializers;

import static cloud.eppo.android.util.Utils.logTag;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;

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
import cloud.eppo.android.dto.adapters.EppoValueAdapter;

/**
 *  Hand-rolled deserializer so that we don't rely on annotations and method names, which can be
 *  unreliable when ProGuard minification is in-use and not configured to protect JSON-related
 *  classes.
 */
public class RandomizationConfigResponseDeserializer implements JsonDeserializer<RandomizationConfigResponse> {
    private static final String TAG = logTag(EppoClient.class);

    private final EppoValueAdapter eppoValueAdapter = new EppoValueAdapter();

    @Override
    public RandomizationConfigResponse deserialize(JsonElement rootElement, Type type, JsonDeserializationContext context) throws JsonParseException {
        ConcurrentHashMap<String, FlagConfig> flags = new ConcurrentHashMap<>();
        RandomizationConfigResponse configResponse = new RandomizationConfigResponse();
        configResponse.setFlags(flags);
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
            JsonObject flagObject = flagEntry.getValue().getAsJsonObject();
            int subjectShards = flagObject.get("subjectShards").getAsInt();
            boolean enabled = flagObject.get("enabled").getAsBoolean();

            Map<String, String> typedOverrides = new HashMap<>();
            JsonElement typedOverridesElement = flagObject.get("typedOverrides");
            if (typedOverridesElement != null) {
                for (Map.Entry<String, JsonElement> typedOverridesEntry : typedOverridesElement.getAsJsonObject().entrySet()) {
                    typedOverrides.put(typedOverridesEntry.getKey(), typedOverridesEntry.getValue().getAsString());

                }
            }

            Map<String, Allocation> allocations = new HashMap<>();
            JsonObject allocationsObject = flagObject.get("allocations").getAsJsonObject();
            for (Map.Entry<String, JsonElement> allocationEntry : allocationsObject.entrySet()) {
                String allocationKey = allocationEntry.getKey();
                JsonObject allocationObject = allocationEntry.getValue().getAsJsonObject();
                float percentExposure = allocationObject.get("percentExposure").getAsFloat();
                JsonArray variationsArray = allocationObject.get("variations").getAsJsonArray();
                List<Variation> variations = new ArrayList<>();
                for (JsonElement variationElement : variationsArray) {
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

                Allocation allocation = new Allocation();
                allocation.setPercentExposure(percentExposure);
                allocation.setVariations(variations);
                allocations.put(allocationKey, allocation);
            }

            List<TargetingRule> targetingRules = new ArrayList<>();
            for (JsonElement ruleElement : flagObject.getAsJsonArray("rules").getAsJsonArray()) {
                JsonObject rulesObject = ruleElement.getAsJsonObject();
                String allocationKey = rulesObject.get("allocationKey").getAsString();

                List<TargetingCondition> conditions = new ArrayList<>();
                for (JsonElement conditionElement : rulesObject.get("conditions").getAsJsonArray()) {
                    JsonObject conditionObject = conditionElement.getAsJsonObject();
                    String attribute = conditionObject.get("attribute").getAsString();
                    OperatorType operator = OperatorType.fromString(conditionObject.get("operator").getAsString());
                    EppoValue value = eppoValueAdapter.deserialize(conditionObject.get("value"), type, context);

                    TargetingCondition condition = new TargetingCondition();
                    condition.setAttribute(attribute);
                    condition.setOperator(operator);
                    condition.setValue(value);
                    conditions.add(condition);
                }

                TargetingRule targetingRule = new TargetingRule();
                targetingRule.setAllocationKey(allocationKey);
                targetingRule.setConditions(conditions);
                targetingRules.add(targetingRule);
            }

            FlagConfig flagConfig = new FlagConfig();
            flagConfig.setEnabled(enabled);
            flagConfig.setSubjectShards(subjectShards);
            flagConfig.setTypedOverrides(typedOverrides);
            flagConfig.setAllocations(allocations);
            flagConfig.setRules(targetingRules);
            flags.put(flagKey, flagConfig);
        }

        return configResponse;
    }

    private EppoValue deserializeToEppoValue(JsonElement element) {
        EppoValue typedValue = EppoValue.valueOf(); // Default to null
        if (element == null) {
            return typedValue;
        }

        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                typedValue = EppoValue.valueOf(primitive.getAsBoolean());
            } else if (primitive.isNumber()) {
                typedValue = EppoValue.valueOf(primitive.getAsDouble());
            } else {
                String stringPrimitive = primitive.getAsString();
                if (stringPrimitive == "null") {
                    // TODO: do we want to keep doing this?
                } else {
                    typedValue = EppoValue.valueOf();
                }
            }
        } else if (element.isJsonArray()){
            ArrayList<String> valueArray = new ArrayList<>();
            for (JsonElement arrayElement : element.getAsJsonArray()) {
                valueArray.add(arrayElement.getAsString());
            }
            typedValue = EppoValue.valueOf(valueArray);
        } else if (element.isJsonObject()) {
            typedValue = EppoValue.valueOf(element);
        }

        return typedValue;
    }
}
