package cloud.eppo.android.dto.adapters;

import static cloud.eppo.Utils.base64Decode;

import cloud.eppo.api.EppoValue;
import cloud.eppo.api.dto.Allocation;
import cloud.eppo.api.dto.BanditFlagVariation;
import cloud.eppo.api.dto.BanditReference;
import cloud.eppo.api.dto.FlagConfig;
import cloud.eppo.api.dto.FlagConfigResponse;
import cloud.eppo.api.dto.OperatorType;
import cloud.eppo.api.dto.Shard;
import cloud.eppo.api.dto.Split;
import cloud.eppo.api.dto.TargetingCondition;
import cloud.eppo.api.dto.TargetingRule;
import cloud.eppo.api.dto.Variation;
import cloud.eppo.api.dto.VariationType;
import cloud.eppo.model.ShardRange;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jackson deserializer for {@link FlagConfigResponse}.
 *
 * <p>Hand-rolled deserializer so that we don't rely on annotations and method names, which can be
 * unreliable when ProGuard minification is in-use and not configured to protect
 * JSON-deserialization-related classes and annotations.
 */
public class FlagConfigResponseDeserializer extends StdDeserializer<FlagConfigResponse> {
  private static final Logger log = LoggerFactory.getLogger(FlagConfigResponseDeserializer.class);
  private static final ThreadLocal<SimpleDateFormat> UTC_ISO_DATE_FORMAT =
      ThreadLocal.withInitial(
          () -> {
            SimpleDateFormat dateFormat =
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            return dateFormat;
          });

  private final EppoValueDeserializer eppoValueDeserializer = new EppoValueDeserializer();

  protected FlagConfigResponseDeserializer(Class<?> vc) {
    super(vc);
  }

  public FlagConfigResponseDeserializer() {
    this(null);
  }

  @Override
  public FlagConfigResponse deserialize(JsonParser jp, DeserializationContext ctxt)
      throws IOException {
    JsonNode rootNode = jp.getCodec().readTree(jp);

    if (rootNode == null || !rootNode.isObject()) {
      log.warn("no top-level JSON object");
      return new FlagConfigResponse.Default();
    }
    JsonNode flagsNode = rootNode.get("flags");
    if (flagsNode == null || !flagsNode.isObject()) {
      log.warn("no root-level flags object");
      return new FlagConfigResponse.Default();
    }

    // Default is to assume that the config is not obfuscated.
    JsonNode formatNode = rootNode.get("format");
    FlagConfigResponse.Format dataFormat =
        formatNode == null
            ? FlagConfigResponse.Format.SERVER
            : FlagConfigResponse.Format.valueOf(formatNode.asText());

    // Parse environment name from environment object
    String environmentName = null;
    JsonNode environmentNode = rootNode.get("environment");
    if (environmentNode != null && environmentNode.isObject()) {
      JsonNode nameNode = environmentNode.get("name");
      if (nameNode != null) {
        environmentName = nameNode.asText();
      }
    }

    // Parse createdAt
    Date createdAt = parseUtcISODateNode(rootNode.get("createdAt"));

    Map<String, FlagConfig> flags = new ConcurrentHashMap<>();

    flagsNode
        .fields()
        .forEachRemaining(
            field -> {
              FlagConfig flagConfig = deserializeFlag(field.getValue());
              flags.put(field.getKey(), flagConfig);
            });

    Map<String, BanditReference> banditReferences = new ConcurrentHashMap<>();
    if (rootNode.has("banditReferences")) {
      JsonNode banditReferencesNode = rootNode.get("banditReferences");
      if (!banditReferencesNode.isObject()) {
        log.warn("root-level banditReferences property is present but not a JSON object");
      } else {
        banditReferencesNode
            .fields()
            .forEachRemaining(
                field -> {
                  BanditReference banditReference = deserializeBanditReference(field.getValue());
                  banditReferences.put(field.getKey(), banditReference);
                });
      }
    }

    return new FlagConfigResponse.Default(
        flags, banditReferences, dataFormat, environmentName, createdAt);
  }

  private FlagConfig deserializeFlag(JsonNode jsonNode) {
    String key = jsonNode.get("key").asText();
    boolean enabled = jsonNode.get("enabled").asBoolean();
    int totalShards = jsonNode.get("totalShards").asInt();
    VariationType variationType = VariationType.fromString(jsonNode.get("variationType").asText());
    Map<String, Variation> variations = deserializeVariations(jsonNode.get("variations"));
    List<Allocation> allocations = deserializeAllocations(jsonNode.get("allocations"));

    return new FlagConfig.Default(
        key, enabled, totalShards, variationType, variations, allocations);
  }

  private Map<String, Variation> deserializeVariations(JsonNode jsonNode) {
    Map<String, Variation> variations = new HashMap<>();
    if (jsonNode == null) {
      return variations;
    }
    for (Iterator<Map.Entry<String, JsonNode>> it = jsonNode.fields(); it.hasNext(); ) {
      Map.Entry<String, JsonNode> entry = it.next();
      String key = entry.getValue().get("key").asText();
      EppoValue value = eppoValueDeserializer.deserializeNode(entry.getValue().get("value"));
      variations.put(entry.getKey(), new Variation.Default(key, value));
    }
    return variations;
  }

  private List<Allocation> deserializeAllocations(JsonNode jsonNode) {
    List<Allocation> allocations = new ArrayList<>();
    if (jsonNode == null) {
      return allocations;
    }
    for (JsonNode allocationNode : jsonNode) {
      String key = allocationNode.get("key").asText();
      Set<TargetingRule> rules = deserializeTargetingRules(allocationNode.get("rules"));
      Date startAt = parseUtcISODateNode(allocationNode.get("startAt"));
      Date endAt = parseUtcISODateNode(allocationNode.get("endAt"));
      List<Split> splits = deserializeSplits(allocationNode.get("splits"));
      boolean doLog = allocationNode.get("doLog").asBoolean();
      allocations.add(new Allocation.Default(key, rules, startAt, endAt, splits, doLog));
    }
    return allocations;
  }

  private Set<TargetingRule> deserializeTargetingRules(JsonNode jsonNode) {
    Set<TargetingRule> targetingRules = new HashSet<>();
    if (jsonNode == null || !jsonNode.isArray()) {
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
        conditions.add(new TargetingCondition.Default(operator, attribute, value));
      }
      targetingRules.add(new TargetingRule.Default(conditions));
    }

    return targetingRules;
  }

  private List<Split> deserializeSplits(JsonNode jsonNode) {
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
      splits.add(new Split.Default(variationKey, shards, extraLogging));
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
      shards.add(new Shard.Default(salt, ranges));
    }
    return shards;
  }

  private BanditReference deserializeBanditReference(JsonNode jsonNode) {
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
            new BanditFlagVariation.Default(
                banditKey, flagKey, allocationKey, variationKey, variationValue);
        flagVariations.add(flagVariation);
      }
    }
    return new BanditReference.Default(modelVersion, flagVariations);
  }

  // ===== Date Parsing Helpers =====

  private static Date parseUtcISODateNode(JsonNode isoDateStringElement) {
    if (isoDateStringElement == null || isoDateStringElement.isNull()) {
      return null;
    }
    String isoDateString = isoDateStringElement.asText();
    Date result = null;
    try {
      result = UTC_ISO_DATE_FORMAT.get().parse(isoDateString);
    } catch (ParseException e) {
      // We expect to fail parsing if the date is base 64 encoded
      // Thus we'll leave the result null for now and try again with the decoded value
    }

    if (result == null) {
      // Date may be encoded
      String decodedIsoDateString = base64Decode(isoDateString);
      try {
        result = UTC_ISO_DATE_FORMAT.get().parse(decodedIsoDateString);
      } catch (ParseException e) {
        log.warn("Date \"{}\" not in ISO date format", isoDateString);
      }
    }

    return result;
  }
}
