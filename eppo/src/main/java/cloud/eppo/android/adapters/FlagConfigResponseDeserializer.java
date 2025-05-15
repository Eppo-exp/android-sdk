package cloud.eppo.android.adapters;

import static cloud.eppo.android.util.AndroidUtils.logTag;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cloud.eppo.api.EppoValue;
import cloud.eppo.model.ShardRange;
import cloud.eppo.ufc.dto.Allocation;
import cloud.eppo.ufc.dto.BanditFlagVariation;
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

public class FlagConfigResponseDeserializer {
  private static final String TAG = logTag(FlagConfigResponseDeserializer.class);
  private final EppoValueDeserializer eppoValueDeserializer = new EppoValueDeserializer();
  public FlagConfigResponse deserialize(String json) throws JSONException {
    JSONObject root = new JSONObject(json);


    if (!root.has("flags") ) {
      Log.w(TAG, "no root-level flags object");
      return new FlagConfigResponse();
    }
    JSONObject flagsNode = root.getJSONObject("flags");

    // Default is to assume that the config is not obfuscated.
    FlagConfigResponse.Format format = root.has("format") ? FlagConfigResponse.Format.valueOf(root.getString("format")) :
        FlagConfigResponse.Format.SERVER;


    Map<String, FlagConfig> flags = new HashMap<>();
    JSONObject flagNode = root.getJSONObject("flags");
    Iterator<String> keys = flagNode.keys();
    while (keys.hasNext()) {
      String flagKey = keys.next();
   flags.put(flagKey, deserializeFlag(flagNode.getJSONObject(flagKey)));
    }

    Map<String, BanditReference> banditRefs = new HashMap<>();
    JSONObject banditRefsNode = root.getJSONObject("banditReferences");
    Iterator<String> banditRefKeys = banditRefsNode.keys();
    while (banditRefKeys.hasNext()) {
      String banditKey = keys.next();
      banditRefs.put(banditKey, deserializeBanditReference(banditRefsNode.getJSONObject(banditKey)));
    }

    return new FlagConfigResponse(flags, banditRefs);
  }
//
//
//  public FlagConfigResponse deserialize(JsonParser jp, DeserializationContext ctxt)
//      throws IOException, JacksonException {
//    JsonNode rootNode = jp.getCodec().readTree(jp);
//
//    if (rootNode == null || !rootNode.isObject()) {
//      log.warn("no top-level JSON object");
//      return new FlagConfigResponse();
//    }
//    JsonNode flagsNode = rootNode.get("flags");
//    if (flagsNode == null || !flagsNode.isObject()) {
//      log.warn("no root-level flags object");
//      return new FlagConfigResponse();
//    }
//
//    // Default is to assume that the config is not obfuscated.
//    JsonNode formatNode = rootNode.get("format");
//    FlagConfigResponse.Format dataFormat =
//        formatNode == null
//            ? FlagConfigResponse.Format.SERVER
//            : FlagConfigResponse.Format.valueOf(formatNode.asText());
//
//    Map<String, FlagConfig> flags = new ConcurrentHashMap<>();
//
//    flagsNode
//        .fields()
//        .forEachRemaining(
//            field -> {
//              FlagConfig flagConfig = deserializeFlag(field.getValue());
//              flags.put(field.getKey(), flagConfig);
//            });
//
//    Map<String, BanditReference> banditReferences = new ConcurrentHashMap<>();
//    if (rootNode.has("banditReferences")) {
//      JsonNode banditReferencesNode = rootNode.get("banditReferences");
//      if (!banditReferencesNode.isObject()) {
//        log.warn("root-level banditReferences property is present but not a JSON object");
//      } else {
//        banditReferencesNode
//            .fields()
//            .forEachRemaining(
//                field -> {
//                  BanditReference banditReference = deserializeBanditReference(field.getValue());
//                  banditReferences.put(field.getKey(), banditReference);
//                });
//      }
//    }
//
//    return new FlagConfigResponse(flags, banditReferences, dataFormat);
//  }

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
    for(Iterator<String> it = jsonNode.keys(); it.hasNext();) {
      String variationKey = it.next();
      JSONObject variationNode= jsonNode.getJSONObject(variationKey);
      String key = variationNode.getString("key");
      EppoValue value = eppoValueDeserializer.deserialize(variationNode.getJSONObject("value"));
      variations.put(key, new Variation(key, value));
    }
    return variations;
  }

  private List<Allocation> deserializeAllocations(JSONArray jsonNode) throws JSONException {
    List<Allocation> allocations = new ArrayList<>();
    if (jsonNode == null) {
      return allocations;
    }
    for (int i = 0; i < jsonNode.length(); i++ ) {
      JSONObject allocationNode= jsonNode.getJSONObject(i);

      String key = allocationNode.getString("key");
      Set<TargetingRule> rules = deserializeTargetingRules(allocationNode.getJSONArray("rules"));
      Date startAt = parseUtcISODateNode(allocationNode.get("startAt"));
      Date endAt = parseUtcISODateNode(allocationNode.get("endAt"));
      List<Split> splits = deserializeSplits(allocationNode.getJSONArray("splits"));
      boolean doLog = allocationNode.getBoolean("doLog");
      allocations.add(new Allocation(key, rules, startAt, endAt, splits, doLog));
    }
    return allocations;
  }

  private Set<TargetingRule> deserializeTargetingRules(JSONArray jsonNode) {
    Set<TargetingRule> targetingRules = new HashSet<>();
    if (jsonNode == null || jsonNode.length() == 0) {
      return targetingRules;
    }
    for (JsonNode ruleNode : jsonNode) {
      Set<TargetingCondition> conditions = new HashSet<>();
      for (JsonNode conditionNode : ruleNode.get("conditions")) {
        String attribute = conditionNode.get("attribute").asText();
        String operatorKey = conditionNode.get("operator").asText();
        OperatorType operator = OperatorType.fromString(operatorKey);
        if (operator == null) {
          log.warn("Unknown operator \"{}\"", operatorKey);
          continue;
        }
        EppoValue value = eppoValueDeserializer.deserializeNode(conditionNode.get("value"));
        conditions.add(new TargetingCondition(operator, attribute, value));
      }
      targetingRules.add(new TargetingRule(conditions));
    }

    return targetingRules;
  }

  private List<Split> deserializeSplits(JSONArray jsonNode) {
    List<Split> splits = new ArrayList<>();
    if (jsonNode == null || !jsonNode.isArray()) {
      return splits;
    }
    for (JsonNode splitNode : jsonNode) {
      String variationKey = splitNode.get("variationKey").asText();
      Set<Shard> shards = deserializeShards(splitNode.get("shards"));
      Map<String, String> extraLogging = new HashMap<>();
      JsonNode extraLoggingNode = splitNode.get("extraLogging");
      if (extraLoggingNode != null && extraLoggingNode.isObject()) {
        for (Iterator<Map.Entry<String, JsonNode>> it = extraLoggingNode.fields(); it.hasNext(); ) {
          Map.Entry<String, JsonNode> entry = it.next();
          extraLogging.put(entry.getKey(), entry.getValue().asText());
        }
      }
      splits.add(new Split(variationKey, shards, extraLogging));
    }

    return splits;
  }

  private Set<Shard> deserializeShards(JsonNode jsonNode) {
    Set<Shard> shards = new HashSet<>();
    if (jsonNode == null || !jsonNode.isArray()) {
      return shards;
    }
    for (JsonNode shardNode : jsonNode) {
      String salt = shardNode.get("salt").asText();
      Set<ShardRange> ranges = new HashSet<>();
      for (JsonNode rangeNode : shardNode.get("ranges")) {
        int start = rangeNode.get("start").asInt();
        int end = rangeNode.get("end").asInt();
        ranges.add(new ShardRange(start, end));
      }
      shards.add(new Shard(salt, ranges));
    }
    return shards;
  }

  private BanditReference deserializeBanditReference(JSONObject jsonNode) {
    String modelVersion = jsonNode.get("modelVersion").asText();
    List<BanditFlagVariation> flagVariations = new ArrayList<>();
    JsonNode flagVariationsNode = jsonNode.get("flagVariations");
    if (flagVariationsNode != null && flagVariationsNode.isArray()) {
      for (JsonNode flagVariationNode : flagVariationsNode) {
        String banditKey = flagVariationNode.get("key").asText();
        String flagKey = flagVariationNode.get("flagKey").asText();
        String allocationKey = flagVariationNode.get("allocationKey").asText();
        String variationKey = flagVariationNode.get("variationKey").asText();
        String variationValue = flagVariationNode.get("variationValue").asText();
        BanditFlagVariation flagVariation =
            new BanditFlagVariation(
                banditKey, flagKey, allocationKey, variationKey, variationValue);
        flagVariations.add(flagVariation);
      }
    }
    return new BanditReference(modelVersion, flagVariations);
  }
}
