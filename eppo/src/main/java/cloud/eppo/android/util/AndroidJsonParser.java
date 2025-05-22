package cloud.eppo.android.util;

import static cloud.eppo.Utils.parseUtcISODateString;

import android.util.Log;
import androidx.annotation.Nullable;
import cloud.eppo.Utils;
import cloud.eppo.api.EppoValue;
import cloud.eppo.exception.JsonParsingException;
import cloud.eppo.model.ShardRange;
import cloud.eppo.ufc.dto.Allocation;
import cloud.eppo.ufc.dto.BanditFlagVariation;
import cloud.eppo.ufc.dto.BanditParametersResponse;
import cloud.eppo.ufc.dto.BanditReference;
import cloud.eppo.ufc.dto.FlagConfig;
import cloud.eppo.ufc.dto.FlagConfigResponse;
import cloud.eppo.ufc.dto.OperatorType;
import cloud.eppo.ufc.dto.Shard;
import cloud.eppo.ufc.dto.Split;
import cloud.eppo.ufc.dto.TargetingCondition;
import cloud.eppo.ufc.dto.TargetingRule;
import cloud.eppo.ufc.dto.Variation;
import cloud.eppo.ufc.dto.VariationType;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class AndroidJsonParser implements Utils.JsonDeserializer {
  private static final String TAG = AndroidUtils.logTag(AndroidJsonParser.class);
  private final cloud.eppo.android.adapters.EppoValueDeserializer eppoValueDeserializer =
      new cloud.eppo.android.adapters.EppoValueDeserializer();

  @Override
  public FlagConfigResponse parseFlagConfigResponse(byte[] jsonBytes) throws JsonParsingException {
    try {
      String jsonString = new String(jsonBytes);
      JSONObject root = new JSONObject(jsonString);

      if (!root.has("flags")) {
        Log.w(TAG, "No root-level flags object");
        return new FlagConfigResponse();
      }

      // Default is to assume that the config is not obfuscated.
      FlagConfigResponse.Format format =
          root.has("format")
              ? FlagConfigResponse.Format.valueOf(root.getString("format"))
              : FlagConfigResponse.Format.SERVER;

      // Parse flags
      Map<String, FlagConfig> flags = new HashMap<>();
      JSONObject flagsNode = root.getJSONObject("flags");
      Iterator<String> flagKeys = flagsNode.keys();

      while (flagKeys.hasNext()) {
        String flagKey = flagKeys.next();
        flags.put(flagKey, deserializeFlag(flagsNode.getJSONObject(flagKey)));
      }

      // Parse bandit references if they exist
      Map<String, BanditReference> banditRefs = new HashMap<>();
      if (root.has("banditReferences")) {
        JSONObject banditRefsNode = root.getJSONObject("banditReferences");
        Iterator<String> banditRefKeys = banditRefsNode.keys();

        while (banditRefKeys.hasNext()) {
          String banditKey = banditRefKeys.next();
          banditRefs.put(
              banditKey, deserializeBanditReference(banditRefsNode.getJSONObject(banditKey)));
        }
      }

      return new FlagConfigResponse(flags, banditRefs);
    } catch (JSONException e) {
      //      throw new JsonParsingException("Error parsing flag config response", e);
      return new FlagConfigResponse();
    }
  }

  @Override
  public BanditParametersResponse parseBanditParametersResponse(byte[] jsonBytes)
      throws JsonParsingException {
    try {
      String jsonString = new String(jsonBytes);
      JSONObject root = new JSONObject(jsonString);

      // Implementation for BanditParametersResponse parsing
      // This would need to be completed based on the BanditParametersResponse class structure

      return new BanditParametersResponse();
    } catch (JSONException e) {
      //      throw new JsonParsingException("Error parsing bandit parameters response", e);
      return new BanditParametersResponse();
    }
  }

  @Override
  public boolean isValidJson(String json) {
    try {
      new JSONObject(json);
      return true;
    } catch (JSONException ex) {
      try {
        new JSONArray(json);
        return true;
      } catch (JSONException ex1) {
        return false;
      }
    }
  }

  @Override
  public String serializeAttributesToJSONString(Map<String, EppoValue> map, boolean omitNulls) {
    try {
      JSONObject jsonObject = new JSONObject();

      for (Map.Entry<String, EppoValue> entry : map.entrySet()) {
        EppoValue value = entry.getValue();
        String key = entry.getKey();

        // Skip null values if omitNulls is true
        if (omitNulls && (value == null || value.isNull())) {
          continue;
        }

        if (value == null || value.isNull()) {
          jsonObject.put(key, JSONObject.NULL);
        } else if (value.isBoolean()) {
          jsonObject.put(key, value.booleanValue());
        } else if (value.isNumeric()) {
          jsonObject.put(key, value.doubleValue());
        } else if (value.isString()) {
          jsonObject.put(key, value.stringValue());
        } else if (value.isStringArray()) {
          JSONArray jsonArray = new JSONArray();
          for (String str : value.stringArrayValue()) {
            jsonArray.put(str);
          }
          jsonObject.put(key, jsonArray);
        }
      }

      return jsonObject.toString();
    } catch (JSONException e) {
      // In case of serialization error, return empty JSON object
      return "{}";
    }
  }

  private FlagConfig deserializeFlag(JSONObject jsonNode) throws JSONException {
    String key = jsonNode.getString("key");
    boolean enabled = jsonNode.getBoolean("enabled");
    int totalShards = jsonNode.getInt("totalShards");
    VariationType variationType = VariationType.fromString(jsonNode.getString("variationType"));
    Map<String, Variation> variations = deserializeVariations(jsonNode.getJSONObject("variations"));
    List<Allocation> allocations = deserializeAllocations(jsonNode.getJSONArray("allocations"));

    return new FlagConfig(key, enabled, totalShards, variationType, variations, allocations);
  }

  private Map<String, Variation> deserializeVariations(JSONObject jsonNode) throws JSONException {
    Map<String, Variation> variations = new HashMap<>();
    if (jsonNode == null) {
      return variations;
    }

    Iterator<String> keys = jsonNode.keys();
    while (keys.hasNext()) {
      String variationKey = keys.next();
      JSONObject variationNode = jsonNode.getJSONObject(variationKey);
      String key = variationNode.getString("key");
      EppoValue value = eppoValueDeserializer.deserialize(variationNode.opt("value"));
      variations.put(key, new Variation(key, value));
    }

    return variations;
  }

  private List<Allocation> deserializeAllocations(JSONArray jsonArray) throws JSONException {
    List<Allocation> allocations = new ArrayList<>();
    if (jsonArray == null) {
      return allocations;
    }

    for (int i = 0; i < jsonArray.length(); i++) {
      JSONObject allocationNode = jsonArray.getJSONObject(i);

      try {
        String key = allocationNode.getString("key");
        Set<TargetingRule> rules = deserializeTargetingRules(allocationNode.optJSONArray("rules"));
        Date startAt = parseUtcISODateString(allocationNode.optString("startAt"));
        Date endAt = parseUtcISODateString(allocationNode.optString("endAt"));
        List<Split> splits = deserializeSplits(allocationNode.optJSONArray("splits"));
        boolean doLog = allocationNode.getBoolean("doLog");

        allocations.add(new Allocation(key, rules, startAt, endAt, splits, doLog));
      } catch (JSONException e) {
        Log.w(TAG, "Error deserializing allocation at index " + i, e);
      }
    }

    return allocations;
  }

  private Set<TargetingRule> deserializeTargetingRules(@Nullable JSONArray jsonArray) {
    Set<TargetingRule> targetingRules = new HashSet<>();
    if (jsonArray == null || jsonArray.length() == 0) {
      return targetingRules;
    }

    // Better approach to the nested loops - process each rule separately
    for (int i = 0; i < jsonArray.length(); i++) {
      try {
        JSONObject ruleNode = jsonArray.getJSONObject(i);
        Set<TargetingCondition> conditions =
            deserializeConditions(ruleNode.optJSONArray("conditions"));
        targetingRules.add(new TargetingRule(conditions));
      } catch (JSONException e) {
        Log.w(TAG, "Error deserializing targeting rule at index " + i, e);
      }
    }

    return targetingRules;
  }

  private Set<TargetingCondition> deserializeConditions(JSONArray conditionArray) {
    Set<TargetingCondition> conditions = new HashSet<>();
    if (conditionArray == null || conditionArray.length() == 0) {
      return conditions;
    }

    // Process each condition in a separate method for better readability
    for (int i = 0; i < conditionArray.length(); i++) {
      try {
        JSONObject conditionNode = conditionArray.getJSONObject(i);
        String attribute = conditionNode.getString("attribute");
        String operatorKey = conditionNode.getString("operator");
        OperatorType operator = OperatorType.fromString(operatorKey);

        if (operator == null) {
          Log.w(TAG, "Unknown operator: " + operatorKey);
          continue;
        }

        EppoValue value = eppoValueDeserializer.deserialize(conditionNode.getJSONObject("value"));
        conditions.add(new TargetingCondition(operator, attribute, value));
      } catch (JSONException e) {
        Log.w(TAG, "Error deserializing condition at index " + i, e);
      }
    }

    return conditions;
  }

  private List<Split> deserializeSplits(@Nullable JSONArray jsonArray) throws JSONException {
    List<Split> splits = new ArrayList<>();
    if (jsonArray == null) {
      return splits;
    }

    for (int i = 0; i < jsonArray.length(); i++) {
      try {
        JSONObject splitNode = jsonArray.getJSONObject(i);
        String key = splitNode.getString("key");
        Set<Shard> shards = deserializeShards(splitNode.getJSONArray("shards"));

        Map<String, String> extraLogging = new HashMap<>();
        if (splitNode.has("extraLogging")) {
          JSONObject extraLoggingNode = splitNode.getJSONObject("extraLogging");

          Iterator<String> logKeys = extraLoggingNode.keys();
          while (logKeys.hasNext()) {
            String logKey = logKeys.next();
            extraLogging.put(logKey, extraLoggingNode.getString(logKey));
          }
        }

        splits.add(new Split(key, shards, extraLogging));
      } catch (JSONException e) {
        Log.w(TAG, "Error deserializing split at index " + i, e);
      }
    }

    return splits;
  }

  private Set<Shard> deserializeShards(JSONArray jsonArray) throws JSONException {
    Set<Shard> shards = new HashSet<>();
    if (jsonArray == null) {
      return shards;
    }

    for (int i = 0; i < jsonArray.length(); i++) {
      try {
        JSONObject shardNode = jsonArray.getJSONObject(i);
        Set<ShardRange> ranges = deserializeShardRanges(shardNode.optJSONArray("ranges"));

        String key = shardNode.getString("key");
        shards.add(new Shard(key, ranges));
      } catch (JSONException e) {
        Log.w(TAG, "Error deserializing shard at index " + i, e);
      }
    }

    return shards;
  }

  private Set<ShardRange> deserializeShardRanges(JSONArray rangeArray) throws JSONException {
    Set<ShardRange> ranges = new HashSet<>();
    for (int j = 0; j < rangeArray.length(); j++) {
      JSONObject rangeNode = rangeArray.getJSONObject(j);
      int startShardInclusive = rangeNode.getInt("start");
      int endShardExclusive = rangeNode.getInt("end");
      ranges.add(new ShardRange(startShardInclusive, endShardExclusive));
    }
    return ranges;
  }

  private BanditReference deserializeBanditReference(JSONObject jsonNode) throws JSONException {
    String modelVersion = jsonNode.getString("modelVersion");
    List<BanditFlagVariation> flagVariations = new ArrayList<>();

    if (jsonNode.has("flagVariations")) {
      JSONArray flagVariationsArray = jsonNode.getJSONArray("flagVariations");

      for (int i = 0; i < flagVariationsArray.length(); i++) {
        try {
          JSONObject flagVariationNode = flagVariationsArray.getJSONObject(i);
          String banditKey = flagVariationNode.getString("key");
          String flagKey = flagVariationNode.getString("flagKey");
          String allocationKey = flagVariationNode.getString("allocationKey");
          String variationKey = flagVariationNode.getString("variationKey");
          String variationValue = flagVariationNode.getString("variationValue");

          BanditFlagVariation flagVariation =
              new BanditFlagVariation(
                  banditKey, flagKey, allocationKey, variationKey, variationValue);
          flagVariations.add(flagVariation);
        } catch (JSONException e) {
          Log.w(TAG, "Error deserializing bandit flag variation at index " + i, e);
        }
      }
    }

    return new BanditReference(modelVersion, flagVariations);
  }

  // Method to help handle parsing dates from JSON nodes
  public static Date parseUtcISODateNode(JSONObject jsonObject, String fieldName) {
    if (jsonObject == null || !jsonObject.has(fieldName)) {
      return null;
    }

    try {
      String dateString = jsonObject.getString(fieldName);
      return parseUtcISODateString(dateString);
    } catch (JSONException e) {
      Log.w(
          AndroidUtils.logTag(AndroidJsonParser.class),
          "Error parsing date field: " + fieldName,
          e);
      return null;
    }
  }
}
