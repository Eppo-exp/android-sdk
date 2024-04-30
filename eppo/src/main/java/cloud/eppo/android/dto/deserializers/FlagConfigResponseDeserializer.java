package cloud.eppo.android.dto.deserializers;

import static cloud.eppo.android.util.Utils.logTag;

import android.util.Log;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

import cloud.eppo.android.dto.Allocation;
import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.FlagConfig;
import cloud.eppo.android.dto.FlagConfigResponse;
import cloud.eppo.android.dto.OperatorType;
import cloud.eppo.android.dto.Range;
import cloud.eppo.android.dto.Shard;
import cloud.eppo.android.dto.Split;
import cloud.eppo.android.dto.TargetingCondition;
import cloud.eppo.android.dto.TargetingRule;
import cloud.eppo.android.dto.Variation;
import cloud.eppo.android.dto.VariationType;

/**
 *  Hand-rolled deserializer so that we don't rely on annotations and method names, which can be
 *  unreliable when ProGuard minification is in-use and not configured to protect
 *  JSON-deserialization-related classes and annotations.
 */
public class FlagConfigResponseDeserializer implements JsonDeserializer<FlagConfigResponse> {
    private static final String TAG = logTag(FlagConfigResponseDeserializer.class);

    private final EppoValueAdapter eppoValueAdapter = new EppoValueAdapter();

    @Override
    public FlagConfigResponse deserialize(JsonElement rootElement, Type type, JsonDeserializationContext context) throws JsonParseException {
        Map<String, FlagConfig> flags = new ConcurrentHashMap<>();
        FlagConfigResponse configResponse = new FlagConfigResponse();
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
        String key = flagObject.get("key").getAsString();
        boolean enabled = flagObject.get("enabled").getAsBoolean();
        int totalShards = flagObject.get("totalShards").getAsInt();
        VariationType variationType = VariationType.valueOf(flagObject.get("variationType").getAsString());
        Map<String, Variation> variations = deserializeVariations(flagObject.get("variations"), type, context);
        List<Allocation> allocations = deserializeAllocations(flagObject.get("allocations"), type, context);

        FlagConfig flagConfig = new FlagConfig();
        flagConfig.setKey(key);
        flagConfig.setEnabled(enabled);
        flagConfig.setTotalShards(totalShards);
        flagConfig.setVariationType(variationType);
        flagConfig.setVariations(variations);
        flagConfig.setAllocations(allocations);
        return flagConfig;
    }

    private Map<String, Variation> deserializeVariations(JsonElement jsonElement, Type type, JsonDeserializationContext context) {
        Map<String, Variation> variations = new HashMap<>();
        if (jsonElement == null) {
            return variations;
        }

        for (Map.Entry<String, JsonElement> variationEntry : jsonElement.getAsJsonObject().entrySet()) {
            JsonObject variationObject = variationEntry.getValue().getAsJsonObject();
            String key = variationObject.get("key").getAsString();
            EppoValue value = eppoValueAdapter.deserialize(variationObject.get("value"), type, context);

            Variation variation = new Variation();
            variation.setKey(key);
            variation.setValue(value);

            variations.put(variationEntry.getKey(), variation);
        }

        return variations;
    }

    private List<Allocation> deserializeAllocations(JsonElement jsonElement, Type type, JsonDeserializationContext context) {
        List<Allocation> allocations = new ArrayList<>();
        if (jsonElement == null) {
            return allocations;
        }

        for (JsonElement allocationElement : jsonElement.getAsJsonArray()) {
            JsonObject allocationObject = allocationElement.getAsJsonObject();
            String key = allocationObject.get("key").getAsString();
            Set<TargetingRule> rules = deserializeTargetingRules(allocationObject.get("rules"), type, context);

            Date startAt = parseUtcISODateElement(allocationObject.get("startAt"));
            Date endAt = parseUtcISODateElement(allocationObject.get("endAt"));

            List<Split> splits = deserializeSplits(allocationObject.get("splits"));

            boolean doLog = allocationObject.get("doLog").getAsBoolean();

            Allocation allocation = new Allocation();
            allocation.setKey(key);
            allocation.setRules(rules);
            allocation.setStartAt(startAt);
            allocation.setEndAt(endAt);
            allocation.setSplits(splits);
            allocation.setDoLog(doLog);

            allocations.add(allocation);
        }

        return allocations;
    }

    private Set<TargetingRule> deserializeTargetingRules(JsonElement jsonElement, Type type, JsonDeserializationContext context) {
        Set<TargetingRule> targetingRules = new HashSet<>();
        if (jsonElement == null) {
            return targetingRules;
        }

        for (JsonElement ruleElement: jsonElement.getAsJsonArray()) {
            Set<TargetingCondition> conditions = new HashSet<>();

            for (JsonElement conditionElement : ruleElement.getAsJsonObject().get("conditions").getAsJsonArray()) {
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
            }

            TargetingRule targetingRule = new TargetingRule();
            targetingRule.setConditions(conditions);
            targetingRules.add(targetingRule);
        }

        return targetingRules;
    }

    private List<Split> deserializeSplits(JsonElement jsonElement) {
        List<Split> splits = new ArrayList<>();
        if (jsonElement == null) {
            return splits;
        }

        for (JsonElement splitElement : jsonElement.getAsJsonArray()) {
            JsonObject splitObject = splitElement.getAsJsonObject();
            String variationKey = splitObject.get("variationKey").getAsString();
            Set<Shard> shards = deserializeShards(splitObject.get("shards"));

            Map<String, String>  extraLogging = new HashMap<>();
            JsonElement extraLoggingElement = splitObject.get("extraLogging");
            if (extraLoggingElement != null) {
                for (Map.Entry<String, JsonElement> extraLoggingEntry: extraLoggingElement.getAsJsonObject().entrySet()) {
                    extraLogging.put(extraLoggingEntry.getKey(), extraLoggingEntry.getValue().getAsString());
                }
            }

            Split split = new Split();
            split.setVariationKey(variationKey);
            split.setShards(shards);
            split.setExtraLogging(extraLogging);
        }

        return splits;
    }

    private Set<Shard> deserializeShards(JsonElement jsonElement) {
        Set<Shard> shards = new HashSet<>();
        if (jsonElement == null) {
            return shards;
        }

        for (JsonElement shardElement : jsonElement.getAsJsonArray()) {
            JsonObject shardObject = shardElement.getAsJsonObject();
            String salt = shardObject.get("salt").getAsString();
            Set<Range> ranges = new HashSet<>();
            for (JsonElement rangeElement : shardObject.get("ranges").getAsJsonArray()) {
                JsonObject rangeObject = rangeElement.getAsJsonObject();
                int start = rangeObject.get("start").getAsInt();
                int end = rangeObject.get("end").getAsInt();

                Range range = new Range();
                range.setStart(start);
                range.setEnd(end);
                ranges.add(range);
            }
            Shard shard = new Shard();
            shard.setSalt(salt);
            shard.setRanges(ranges);
        }

        return shards;
    }

    private Date parseUtcISODateElement(JsonElement isoDateStringElement) {
        if (isoDateStringElement == null) {
            return null;
        }
        String isoDateString = isoDateStringElement.getAsString();
        // Note: we don't use DateTimeFormatter.ISO_DATE so that this supports older Android versions
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        Date result = null;
        try {
            result = formatter.parse(isoDateString);
        } catch (ParseException e) {
            Log.w(TAG, "Date \"+isoDateString+\" not in ISO date format");
        }
        return result;
    }
}
