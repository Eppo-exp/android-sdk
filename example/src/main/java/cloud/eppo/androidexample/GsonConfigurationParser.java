package cloud.eppo.androidexample;

import static cloud.eppo.Utils.base64Decode;

import android.util.Log;
import cloud.eppo.api.EppoValue;
import cloud.eppo.api.dto.Allocation;
import cloud.eppo.api.dto.BanditCategoricalAttributeCoefficients;
import cloud.eppo.api.dto.BanditCoefficients;
import cloud.eppo.api.dto.BanditFlagVariation;
import cloud.eppo.api.dto.BanditModelData;
import cloud.eppo.api.dto.BanditNumericAttributeCoefficients;
import cloud.eppo.api.dto.BanditParameters;
import cloud.eppo.api.dto.BanditParametersResponse;
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
import cloud.eppo.api.dto.ShardRange;
import cloud.eppo.parser.ConfigurationParseException;
import cloud.eppo.parser.ConfigurationParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
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

/**
 * A GSON-based implementation of {@link ConfigurationParser}.
 *
 * <p>This is an alternative to the default {@link cloud.eppo.android.JacksonConfigurationParser}
 * for apps that already include GSON and want to avoid the Jackson dependency. The hand-rolled
 * deserialization avoids reliance on field names or annotations that ProGuard minification could
 * strip.
 *
 * <p>Because this parser uses {@link JsonElement} as its JSON value type, it is not directly
 * compatible with {@link cloud.eppo.android.EppoClient.Builder#configurationParser} (which is typed
 * to {@code ConfigurationParser<JsonNode>}). It is provided here as a reference implementation and
 * for use with framework clients that are parameterised over {@link JsonElement}.
 */
public class GsonConfigurationParser implements ConfigurationParser<JsonElement> {
  private static final String TAG = GsonConfigurationParser.class.getSimpleName();

  public GsonConfigurationParser() {}

  private static final ThreadLocal<SimpleDateFormat> UTC_ISO_DATE_FORMAT =
      ThreadLocal.withInitial(
          () -> {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
            return fmt;
          });

  // ===== ConfigurationParser interface =====

  @Override
  public FlagConfigResponse parseFlagConfig(byte[] flagConfigJson)
      throws ConfigurationParseException {
    try {
      Log.d(TAG, "Parsing flag configuration, " + flagConfigJson.length + " bytes");
      JsonElement root = JsonParser.parseString(new String(flagConfigJson, StandardCharsets.UTF_8));
      return deserializeFlagConfigResponse(root);
    } catch (Exception e) {
      throw new ConfigurationParseException("Failed to parse flag configuration", e);
    }
  }

  @Override
  public BanditParametersResponse parseBanditParams(byte[] banditParamsJson)
      throws ConfigurationParseException {
    try {
      Log.d(TAG, "Parsing bandit parameters, " + banditParamsJson.length + " bytes");
      JsonElement root =
          JsonParser.parseString(new String(banditParamsJson, StandardCharsets.UTF_8));
      return deserializeBanditParametersResponse(root);
    } catch (Exception e) {
      throw new ConfigurationParseException("Failed to parse bandit parameters", e);
    }
  }

  @Override
  public JsonElement parseJsonValue(String jsonValue) throws ConfigurationParseException {
    try {
      return JsonParser.parseString(jsonValue);
    } catch (Exception e) {
      throw new ConfigurationParseException("Failed to parse JSON value", e);
    }
  }

  // ===== Flag configuration =====

  private FlagConfigResponse deserializeFlagConfigResponse(JsonElement element) {
    if (element == null || !element.isJsonObject()) {
      Log.w(TAG, "no top-level JSON object");
      return new FlagConfigResponse.Default();
    }
    JsonObject root = element.getAsJsonObject();

    JsonElement flagsElement = root.get("flags");
    if (flagsElement == null || !flagsElement.isJsonObject()) {
      Log.w(TAG, "no root-level flags object");
      return new FlagConfigResponse.Default();
    }

    JsonElement formatElement = root.get("format");
    FlagConfigResponse.Format dataFormat =
        formatElement == null
            ? FlagConfigResponse.Format.SERVER
            : FlagConfigResponse.Format.valueOf(formatElement.getAsString());

    String environmentName = null;
    JsonElement envElement = root.get("environment");
    if (envElement != null && envElement.isJsonObject()) {
      JsonElement nameElement = envElement.getAsJsonObject().get("name");
      if (nameElement != null && !nameElement.isJsonNull()) {
        environmentName = nameElement.getAsString();
      }
    }

    Date createdAt = parseDateElement(root.get("createdAt"));

    Map<String, FlagConfig> flags = new ConcurrentHashMap<>();
    for (Map.Entry<String, JsonElement> entry : flagsElement.getAsJsonObject().entrySet()) {
      flags.put(entry.getKey(), deserializeFlag(entry.getValue().getAsJsonObject()));
    }

    Map<String, BanditReference> banditReferences = new ConcurrentHashMap<>();
    JsonElement banditRefsElement = root.get("banditReferences");
    if (banditRefsElement != null) {
      if (!banditRefsElement.isJsonObject()) {
        Log.w(TAG, "root-level banditReferences is present but not a JSON object");
      } else {
        for (Map.Entry<String, JsonElement> entry :
            banditRefsElement.getAsJsonObject().entrySet()) {
          banditReferences.put(
              entry.getKey(), deserializeBanditReference(entry.getValue().getAsJsonObject()));
        }
      }
    }

    return new FlagConfigResponse.Default(
        flags, banditReferences, dataFormat, environmentName, createdAt);
  }

  private FlagConfig deserializeFlag(JsonObject obj) {
    String key = obj.get("key").getAsString();
    boolean enabled = obj.get("enabled").getAsBoolean();
    int totalShards = obj.get("totalShards").getAsInt();
    VariationType variationType = VariationType.fromString(obj.get("variationType").getAsString());
    Map<String, Variation> variations = deserializeVariations(obj.get("variations"));
    List<Allocation> allocations = deserializeAllocations(obj.get("allocations"));
    return new FlagConfig.Default(
        key, enabled, totalShards, variationType, variations, allocations);
  }

  private Map<String, Variation> deserializeVariations(JsonElement element) {
    Map<String, Variation> variations = new HashMap<>();
    if (element == null || !element.isJsonObject()) {
      return variations;
    }
    for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
      JsonObject varObj = entry.getValue().getAsJsonObject();
      String varKey = varObj.get("key").getAsString();
      EppoValue value = deserializeEppoValue(varObj.get("value"));
      variations.put(entry.getKey(), new Variation.Default(varKey, value));
    }
    return variations;
  }

  private List<Allocation> deserializeAllocations(JsonElement element) {
    List<Allocation> allocations = new ArrayList<>();
    if (element == null || !element.isJsonArray()) {
      return allocations;
    }
    for (JsonElement allocationElement : element.getAsJsonArray()) {
      JsonObject alloc = allocationElement.getAsJsonObject();
      String key = alloc.get("key").getAsString();
      Set<TargetingRule> rules = deserializeTargetingRules(alloc.get("rules"));
      Date startAt = parseDateElement(alloc.get("startAt"));
      Date endAt = parseDateElement(alloc.get("endAt"));
      List<Split> splits = deserializeSplits(alloc.get("splits"));
      boolean doLog = alloc.get("doLog").getAsBoolean();
      allocations.add(new Allocation.Default(key, rules, startAt, endAt, splits, doLog));
    }
    return allocations;
  }

  private Set<TargetingRule> deserializeTargetingRules(JsonElement element) {
    Set<TargetingRule> rules = new HashSet<>();
    if (element == null || !element.isJsonArray()) {
      return rules;
    }
    for (JsonElement ruleElement : element.getAsJsonArray()) {
      JsonObject ruleObj = ruleElement.getAsJsonObject();
      Set<TargetingCondition> conditions = new HashSet<>();
      JsonElement conditionsElement = ruleObj.get("conditions");
      if (conditionsElement != null && conditionsElement.isJsonArray()) {
        for (JsonElement condElement : conditionsElement.getAsJsonArray()) {
          JsonObject cond = condElement.getAsJsonObject();
          String attribute = cond.get("attribute").getAsString();
          String operatorKey = cond.get("operator").getAsString();
          OperatorType operator = OperatorType.fromString(operatorKey);
          if (operator == null) {
            Log.w(TAG, "Unknown operator \"" + operatorKey + "\"");
            continue;
          }
          EppoValue value = deserializeEppoValue(cond.get("value"));
          conditions.add(new TargetingCondition.Default(operator, attribute, value));
        }
      }
      rules.add(new TargetingRule.Default(conditions));
    }
    return rules;
  }

  private List<Split> deserializeSplits(JsonElement element) {
    List<Split> splits = new ArrayList<>();
    if (element == null || !element.isJsonArray()) {
      return splits;
    }
    for (JsonElement splitElement : element.getAsJsonArray()) {
      JsonObject splitObj = splitElement.getAsJsonObject();
      String variationKey = splitObj.get("variationKey").getAsString();
      Set<Shard> shards = deserializeShards(splitObj.get("shards"));
      Map<String, String> extraLogging = new HashMap<>();
      JsonElement extraLoggingElement = splitObj.get("extraLogging");
      if (extraLoggingElement != null && extraLoggingElement.isJsonObject()) {
        for (Map.Entry<String, JsonElement> entry :
            extraLoggingElement.getAsJsonObject().entrySet()) {
          extraLogging.put(entry.getKey(), entry.getValue().getAsString());
        }
      }
      splits.add(new Split.Default(variationKey, shards, extraLogging));
    }
    return splits;
  }

  private Set<Shard> deserializeShards(JsonElement element) {
    Set<Shard> shards = new HashSet<>();
    if (element == null || !element.isJsonArray()) {
      return shards;
    }
    for (JsonElement shardElement : element.getAsJsonArray()) {
      JsonObject shardObj = shardElement.getAsJsonObject();
      String salt = shardObj.get("salt").getAsString();
      Set<ShardRange> ranges = new HashSet<>();
      JsonArray rangesArray = shardObj.getAsJsonArray("ranges");
      if (rangesArray != null) {
        for (JsonElement rangeElement : rangesArray) {
          JsonObject range = rangeElement.getAsJsonObject();
          int start = range.get("start").getAsInt();
          int end = range.get("end").getAsInt();
          ranges.add(new ShardRange.Default(start, end));
        }
      }
      shards.add(new Shard.Default(salt, ranges));
    }
    return shards;
  }

  private BanditReference deserializeBanditReference(JsonObject obj) {
    String modelVersion = obj.get("modelVersion").getAsString();
    List<BanditFlagVariation> flagVariations = new ArrayList<>();
    JsonElement flagVariationsElement = obj.get("flagVariations");
    if (flagVariationsElement != null && flagVariationsElement.isJsonArray()) {
      for (JsonElement fvElement : flagVariationsElement.getAsJsonArray()) {
        JsonObject fv = fvElement.getAsJsonObject();
        String banditKey = fv.get("key").getAsString();
        String flagKey = fv.get("flagKey").getAsString();
        String allocationKey = fv.get("allocationKey").getAsString();
        String variationKey = fv.get("variationKey").getAsString();
        String variationValue = fv.get("variationValue").getAsString();
        flagVariations.add(
            new BanditFlagVariation.Default(
                banditKey, flagKey, allocationKey, variationKey, variationValue));
      }
    }
    return new BanditReference.Default(modelVersion, flagVariations);
  }

  // ===== Bandit parameters =====

  private BanditParametersResponse deserializeBanditParametersResponse(JsonElement element) {
    if (element == null || !element.isJsonObject()) {
      Log.w(TAG, "no top-level JSON object");
      return new BanditParametersResponse.Default();
    }
    JsonElement banditsElement = element.getAsJsonObject().get("bandits");
    if (banditsElement == null || !banditsElement.isJsonObject()) {
      Log.w(TAG, "no root-level bandits object");
      return new BanditParametersResponse.Default();
    }

    Map<String, BanditParameters> bandits = new HashMap<>();
    for (Map.Entry<String, JsonElement> entry : banditsElement.getAsJsonObject().entrySet()) {
      JsonObject banditObj = entry.getValue().getAsJsonObject();
      String banditKey = banditObj.get("banditKey").getAsString();
      Date updatedAt = Date.from(Instant.parse(banditObj.get("updatedAt").getAsString()));
      String modelName = banditObj.get("modelName").getAsString();
      String modelVersion = banditObj.get("modelVersion").getAsString();
      BanditModelData modelData =
          deserializeBanditModelData(banditObj.get("modelData").getAsJsonObject());
      bandits.put(
          banditKey,
          new BanditParameters.Default(banditKey, updatedAt, modelName, modelVersion, modelData));
    }
    return new BanditParametersResponse.Default(bandits);
  }

  private BanditModelData deserializeBanditModelData(JsonObject obj) {
    double gamma = obj.get("gamma").getAsDouble();
    double defaultActionScore = obj.get("defaultActionScore").getAsDouble();
    double actionProbabilityFloor = obj.get("actionProbabilityFloor").getAsDouble();
    Map<String, BanditCoefficients> coefficients = new HashMap<>();
    JsonElement coeffsElement = obj.get("coefficients");
    if (coeffsElement != null && coeffsElement.isJsonObject()) {
      for (Map.Entry<String, JsonElement> entry : coeffsElement.getAsJsonObject().entrySet()) {
        coefficients.put(
            entry.getKey(), deserializeBanditCoefficients(entry.getValue().getAsJsonObject()));
      }
    }
    return new BanditModelData.Default(
        gamma, defaultActionScore, actionProbabilityFloor, coefficients);
  }

  private BanditCoefficients deserializeBanditCoefficients(JsonObject obj) {
    String actionKey = obj.get("actionKey").getAsString();
    double intercept = obj.get("intercept").getAsDouble();
    Map<String, BanditNumericAttributeCoefficients> subjectNumeric =
        deserializeNumericCoefficients(obj.get("subjectNumericCoefficients"));
    Map<String, BanditCategoricalAttributeCoefficients> subjectCategorical =
        deserializeCategoricalCoefficients(obj.get("subjectCategoricalCoefficients"));
    Map<String, BanditNumericAttributeCoefficients> actionNumeric =
        deserializeNumericCoefficients(obj.get("actionNumericCoefficients"));
    Map<String, BanditCategoricalAttributeCoefficients> actionCategorical =
        deserializeCategoricalCoefficients(obj.get("actionCategoricalCoefficients"));
    return new BanditCoefficients.Default(
        actionKey, intercept, subjectNumeric, subjectCategorical, actionNumeric, actionCategorical);
  }

  private Map<String, BanditNumericAttributeCoefficients> deserializeNumericCoefficients(
      JsonElement element) {
    Map<String, BanditNumericAttributeCoefficients> result = new HashMap<>();
    if (element == null || !element.isJsonArray()) {
      return result;
    }
    for (JsonElement item : element.getAsJsonArray()) {
      JsonObject obj = item.getAsJsonObject();
      String attributeKey = obj.get("attributeKey").getAsString();
      double coefficient = obj.get("coefficient").getAsDouble();
      double missingValueCoefficient = obj.get("missingValueCoefficient").getAsDouble();
      result.put(
          attributeKey,
          new BanditNumericAttributeCoefficients.Default(
              attributeKey, coefficient, missingValueCoefficient));
    }
    return result;
  }

  private Map<String, BanditCategoricalAttributeCoefficients> deserializeCategoricalCoefficients(
      JsonElement element) {
    Map<String, BanditCategoricalAttributeCoefficients> result = new HashMap<>();
    if (element == null || !element.isJsonArray()) {
      return result;
    }
    for (JsonElement item : element.getAsJsonArray()) {
      JsonObject obj = item.getAsJsonObject();
      String attributeKey = obj.get("attributeKey").getAsString();
      double missingValueCoefficient = obj.get("missingValueCoefficient").getAsDouble();
      Map<String, Double> valueCoefficients = new HashMap<>();
      JsonElement valuesElement = obj.get("valueCoefficients");
      if (valuesElement != null && valuesElement.isJsonObject()) {
        for (Map.Entry<String, JsonElement> entry : valuesElement.getAsJsonObject().entrySet()) {
          valueCoefficients.put(entry.getKey(), entry.getValue().getAsDouble());
        }
      }
      result.put(
          attributeKey,
          new BanditCategoricalAttributeCoefficients.Default(
              attributeKey, missingValueCoefficient, valueCoefficients));
    }
    return result;
  }

  // ===== EppoValue =====

  private EppoValue deserializeEppoValue(JsonElement element) {
    if (element == null || element.isJsonNull()) {
      return EppoValue.nullValue();
    }
    if (element.isJsonArray()) {
      List<String> stringArray = new ArrayList<>();
      for (JsonElement item : element.getAsJsonArray()) {
        if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
          stringArray.add(item.getAsString());
        } else {
          Log.w(TAG, "only Strings are supported for array-valued values; received: " + item);
        }
      }
      return EppoValue.valueOf(stringArray);
    }
    if (element.isJsonPrimitive()) {
      if (element.getAsJsonPrimitive().isBoolean()) {
        return EppoValue.valueOf(element.getAsBoolean());
      } else if (element.getAsJsonPrimitive().isNumber()) {
        return EppoValue.valueOf(element.getAsDouble());
      } else {
        return EppoValue.valueOf(element.getAsString());
      }
    }
    Log.w(TAG, "Unexpected JSON for parsing a value: " + element);
    return EppoValue.nullValue();
  }

  // ===== Date helpers =====

  private static Date parseDateElement(JsonElement element) {
    if (element == null || element.isJsonNull()) {
      return null;
    }
    String isoDateString = element.getAsString();
    Date result = null;
    try {
      result = UTC_ISO_DATE_FORMAT.get().parse(isoDateString);
    } catch (ParseException ignored) {
      // May be base64-encoded; fall through and try decoded form
    }
    if (result == null) {
      String decoded = base64Decode(isoDateString);
      try {
        result = UTC_ISO_DATE_FORMAT.get().parse(decoded);
      } catch (ParseException e) {
        Log.w(TAG, "Date \"" + isoDateString + "\" not in ISO date format");
      }
    }
    return result;
  }
}
