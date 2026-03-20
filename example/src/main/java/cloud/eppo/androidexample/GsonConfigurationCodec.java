package cloud.eppo.androidexample;

import android.util.Log;
import cloud.eppo.android.framework.storage.ConfigurationCodec;
import cloud.eppo.api.Configuration;
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
import cloud.eppo.model.ShardRange;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A GSON-based {@link ConfigurationCodec} for {@link Configuration}.
 *
 * <p>Serializes to UTF-8 JSON rather than Java's binary serialization format. Cached configurations
 * are human-readable and immune to {@code serialVersionUID} drift between SDK versions. This is
 * useful for development and debugging in the example app.
 *
 * <p>Because {@link Configuration} does not expose its internal maps, this codec uses reflection to
 * read the {@code flags}, {@code banditReferences}, and {@code bandits} fields.
 */
public class GsonConfigurationCodec implements ConfigurationCodec<Configuration> {

  private static final String TAG = GsonConfigurationCodec.class.getSimpleName();
  private static final int FORMAT_VERSION = 1;

  private static final ThreadLocal<SimpleDateFormat> UTC_DATE_FORMAT =
      ThreadLocal.withInitial(
          () -> {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
            return fmt;
          });

  private static final Field FLAGS_FIELD;
  private static final Field BANDIT_REFS_FIELD;
  private static final Field BANDITS_FIELD;

  static {
    try {
      FLAGS_FIELD = Configuration.class.getDeclaredField("flags");
      FLAGS_FIELD.setAccessible(true);
      BANDIT_REFS_FIELD = Configuration.class.getDeclaredField("banditReferences");
      BANDIT_REFS_FIELD.setAccessible(true);
      BANDITS_FIELD = Configuration.class.getDeclaredField("bandits");
      BANDITS_FIELD.setAccessible(true);
    } catch (NoSuchFieldException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @Override
  public byte[] toBytes(@NotNull Configuration configuration) {
    return serializeConfiguration(configuration).toString().getBytes(StandardCharsets.UTF_8);
  }

  @Override
  @NotNull public Configuration fromBytes(byte[] bytes) {
    String json = new String(bytes, StandardCharsets.UTF_8);
    return deserializeConfiguration(JsonParser.parseString(json).getAsJsonObject());
  }

  @Override
  @NotNull public String getContentType() {
    return "application/json";
  }

  // ===== Serialization =====

  @SuppressWarnings("unchecked")
  private JsonObject serializeConfiguration(Configuration config) {
    JsonObject root = new JsonObject();
    root.addProperty("v", FORMAT_VERSION);
    root.addProperty("isObfuscated", config.isConfigObfuscated());

    String environmentName = config.getEnvironmentName();
    if (environmentName != null) {
      root.addProperty("environmentName", environmentName);
    }

    Date publishedAt = config.getConfigPublishedAt();
    if (publishedAt != null) {
      root.addProperty("publishedAt", UTC_DATE_FORMAT.get().format(publishedAt));
    }

    String snapshotId = config.getFlagsSnapshotId();
    if (snapshotId != null) {
      root.addProperty("snapshotId", snapshotId);
    }

    try {
      Map<String, FlagConfig> flags = (Map<String, FlagConfig>) FLAGS_FIELD.get(config);
      Map<String, BanditReference> banditRefs =
          (Map<String, BanditReference>) BANDIT_REFS_FIELD.get(config);
      Map<String, BanditParameters> bandits =
          (Map<String, BanditParameters>) BANDITS_FIELD.get(config);

      if (flags != null) {
        root.add("flags", serializeFlags(flags));
      }
      if (banditRefs != null && !banditRefs.isEmpty()) {
        root.add("banditReferences", serializeBanditReferences(banditRefs));
      }
      if (bandits != null && !bandits.isEmpty()) {
        root.add("bandits", serializeBandits(bandits));
      }
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Failed to access Configuration fields via reflection", e);
    }

    return root;
  }

  private JsonObject serializeFlags(Map<String, FlagConfig> flags) {
    JsonObject obj = new JsonObject();
    for (Map.Entry<String, FlagConfig> entry : flags.entrySet()) {
      obj.add(entry.getKey(), serializeFlag(entry.getValue()));
    }
    return obj;
  }

  private JsonObject serializeFlag(FlagConfig flag) {
    JsonObject obj = new JsonObject();
    obj.addProperty("key", flag.getKey());
    obj.addProperty("enabled", flag.isEnabled());
    obj.addProperty("totalShards", flag.getTotalShards());
    obj.addProperty("variationType", flag.getVariationType().value);
    obj.add("variations", serializeVariations(flag.getVariations()));
    obj.add("allocations", serializeAllocations(flag.getAllocations()));
    return obj;
  }

  private JsonObject serializeVariations(Map<String, Variation> variations) {
    JsonObject obj = new JsonObject();
    for (Map.Entry<String, Variation> entry : variations.entrySet()) {
      JsonObject varObj = new JsonObject();
      varObj.addProperty("key", entry.getValue().getKey());
      varObj.add("value", serializeEppoValue(entry.getValue().getValue()));
      obj.add(entry.getKey(), varObj);
    }
    return obj;
  }

  private JsonArray serializeAllocations(List<Allocation> allocations) {
    JsonArray arr = new JsonArray();
    for (Allocation alloc : allocations) {
      JsonObject obj = new JsonObject();
      obj.addProperty("key", alloc.getKey());
      obj.addProperty("doLog", alloc.doLog());

      Date startAt = alloc.getStartAt();
      if (startAt != null) {
        obj.addProperty("startAt", UTC_DATE_FORMAT.get().format(startAt));
      }

      Date endAt = alloc.getEndAt();
      if (endAt != null) {
        obj.addProperty("endAt", UTC_DATE_FORMAT.get().format(endAt));
      }

      Set<TargetingRule> rules = alloc.getRules();
      obj.add("rules", rules != null ? serializeTargetingRules(rules) : new JsonArray());
      obj.add("splits", serializeSplits(alloc.getSplits()));
      arr.add(obj);
    }
    return arr;
  }

  private JsonArray serializeTargetingRules(Set<TargetingRule> rules) {
    JsonArray arr = new JsonArray();
    for (TargetingRule rule : rules) {
      JsonObject ruleObj = new JsonObject();
      JsonArray conditions = new JsonArray();
      for (TargetingCondition cond : rule.getConditions()) {
        JsonObject condObj = new JsonObject();
        condObj.addProperty("operator", cond.getOperator().value);
        condObj.addProperty("attribute", cond.getAttribute());
        condObj.add("value", serializeEppoValue(cond.getValue()));
        conditions.add(condObj);
      }
      ruleObj.add("conditions", conditions);
      arr.add(ruleObj);
    }
    return arr;
  }

  private JsonArray serializeSplits(List<Split> splits) {
    JsonArray arr = new JsonArray();
    for (Split split : splits) {
      JsonObject obj = new JsonObject();
      obj.addProperty("variationKey", split.getVariationKey());
      obj.add("shards", serializeShards(split.getShards()));
      Map<String, String> extraLogging = split.getExtraLogging();
      if (!extraLogging.isEmpty()) {
        JsonObject extraObj = new JsonObject();
        for (Map.Entry<String, String> entry : extraLogging.entrySet()) {
          extraObj.addProperty(entry.getKey(), entry.getValue());
        }
        obj.add("extraLogging", extraObj);
      }
      arr.add(obj);
    }
    return arr;
  }

  private JsonArray serializeShards(Set<Shard> shards) {
    JsonArray arr = new JsonArray();
    for (Shard shard : shards) {
      JsonObject obj = new JsonObject();
      obj.addProperty("salt", shard.getSalt());
      JsonArray ranges = new JsonArray();
      for (ShardRange range : shard.getRanges()) {
        JsonObject rangeObj = new JsonObject();
        rangeObj.addProperty("start", range.getStart());
        rangeObj.addProperty("end", range.getEnd());
        ranges.add(rangeObj);
      }
      obj.add("ranges", ranges);
      arr.add(obj);
    }
    return arr;
  }

  private JsonElement serializeEppoValue(@Nullable EppoValue value) {
    if (value == null || value.isNull()) {
      return JsonNull.INSTANCE;
    }
    if (value.isBoolean()) {
      return new JsonPrimitive(value.booleanValue());
    }
    if (value.isNumeric()) {
      return new JsonPrimitive(value.doubleValue());
    }
    if (value.isStringArray()) {
      JsonArray arr = new JsonArray();
      for (String s : value.stringArrayValue()) {
        arr.add(s);
      }
      return arr;
    }
    return new JsonPrimitive(value.stringValue());
  }

  private JsonObject serializeBanditReferences(Map<String, BanditReference> banditRefs) {
    JsonObject obj = new JsonObject();
    for (Map.Entry<String, BanditReference> entry : banditRefs.entrySet()) {
      BanditReference ref = entry.getValue();
      JsonObject refObj = new JsonObject();
      refObj.addProperty("modelVersion", ref.getModelVersion());
      JsonArray variations = new JsonArray();
      for (BanditFlagVariation fv : ref.getFlagVariations()) {
        JsonObject fvObj = new JsonObject();
        fvObj.addProperty("key", fv.getBanditKey());
        fvObj.addProperty("flagKey", fv.getFlagKey());
        fvObj.addProperty("allocationKey", fv.getAllocationKey());
        fvObj.addProperty("variationKey", fv.getVariationKey());
        fvObj.addProperty("variationValue", fv.getVariationValue());
        variations.add(fvObj);
      }
      refObj.add("flagVariations", variations);
      obj.add(entry.getKey(), refObj);
    }
    return obj;
  }

  private JsonObject serializeBandits(Map<String, BanditParameters> bandits) {
    JsonObject obj = new JsonObject();
    for (Map.Entry<String, BanditParameters> entry : bandits.entrySet()) {
      BanditParameters bp = entry.getValue();
      JsonObject bpObj = new JsonObject();
      bpObj.addProperty("banditKey", bp.getBanditKey());
      Date updatedAt = bp.getUpdatedAt();
      if (updatedAt != null) {
        bpObj.addProperty("updatedAt", UTC_DATE_FORMAT.get().format(updatedAt));
      }
      bpObj.addProperty("modelName", bp.getModelName());
      bpObj.addProperty("modelVersion", bp.getModelVersion());
      bpObj.add("modelData", serializeBanditModelData(bp.getModelData()));
      obj.add(entry.getKey(), bpObj);
    }
    return obj;
  }

  private JsonObject serializeBanditModelData(BanditModelData modelData) {
    JsonObject obj = new JsonObject();
    obj.addProperty("gamma", modelData.getGamma());
    obj.addProperty("defaultActionScore", modelData.getDefaultActionScore());
    obj.addProperty("actionProbabilityFloor", modelData.getActionProbabilityFloor());
    JsonObject coefficients = new JsonObject();
    for (Map.Entry<String, BanditCoefficients> entry : modelData.getCoefficients().entrySet()) {
      coefficients.add(entry.getKey(), serializeBanditCoefficients(entry.getValue()));
    }
    obj.add("coefficients", coefficients);
    return obj;
  }

  private JsonObject serializeBanditCoefficients(BanditCoefficients bc) {
    JsonObject obj = new JsonObject();
    obj.addProperty("actionKey", bc.getActionKey());
    obj.addProperty("intercept", bc.getIntercept());
    obj.add(
        "subjectNumericCoefficients",
        serializeNumericCoefficients(bc.getSubjectNumericCoefficients()));
    obj.add(
        "subjectCategoricalCoefficients",
        serializeCategoricalCoefficients(bc.getSubjectCategoricalCoefficients()));
    obj.add(
        "actionNumericCoefficients",
        serializeNumericCoefficients(bc.getActionNumericCoefficients()));
    obj.add(
        "actionCategoricalCoefficients",
        serializeCategoricalCoefficients(bc.getActionCategoricalCoefficients()));
    return obj;
  }

  private JsonArray serializeNumericCoefficients(
      Map<String, BanditNumericAttributeCoefficients> coefs) {
    JsonArray arr = new JsonArray();
    for (BanditNumericAttributeCoefficients c : coefs.values()) {
      JsonObject obj = new JsonObject();
      obj.addProperty("attributeKey", c.getAttributeKey());
      obj.addProperty("coefficient", c.getCoefficient());
      obj.addProperty("missingValueCoefficient", c.getMissingValueCoefficient());
      arr.add(obj);
    }
    return arr;
  }

  private JsonArray serializeCategoricalCoefficients(
      Map<String, BanditCategoricalAttributeCoefficients> coefs) {
    JsonArray arr = new JsonArray();
    for (BanditCategoricalAttributeCoefficients c : coefs.values()) {
      JsonObject obj = new JsonObject();
      obj.addProperty("attributeKey", c.getAttributeKey());
      obj.addProperty("missingValueCoefficient", c.getMissingValueCoefficient());
      JsonObject valueCoefficients = new JsonObject();
      for (Map.Entry<String, Double> entry : c.getValueCoefficients().entrySet()) {
        valueCoefficients.addProperty(entry.getKey(), entry.getValue());
      }
      obj.add("valueCoefficients", valueCoefficients);
      arr.add(obj);
    }
    return arr;
  }

  // ===== Deserialization =====

  private Configuration deserializeConfiguration(JsonObject root) {
    int version = root.has("v") ? root.get("v").getAsInt() : 1;
    if (version != FORMAT_VERSION) {
      Log.w(TAG, "Unknown cache format version " + version + "; attempting deserialization anyway");
    }

    boolean isObfuscated =
        root.has("isObfuscated")
            && !root.get("isObfuscated").isJsonNull()
            && root.get("isObfuscated").getAsBoolean();

    String environmentName = stringOrNull(root, "environmentName");
    Date publishedAt = parseDateElement(root.get("publishedAt"));
    String snapshotId = stringOrNull(root, "snapshotId");

    Map<String, FlagConfig> flags = new HashMap<>();
    JsonElement flagsEl = root.get("flags");
    if (flagsEl != null && flagsEl.isJsonObject()) {
      for (Map.Entry<String, JsonElement> e : flagsEl.getAsJsonObject().entrySet()) {
        flags.put(e.getKey(), deserializeFlag(e.getValue().getAsJsonObject()));
      }
    }

    Map<String, BanditReference> banditRefs = new HashMap<>();
    JsonElement banditRefsEl = root.get("banditReferences");
    if (banditRefsEl != null && banditRefsEl.isJsonObject()) {
      for (Map.Entry<String, JsonElement> e : banditRefsEl.getAsJsonObject().entrySet()) {
        banditRefs.put(e.getKey(), deserializeBanditReference(e.getValue().getAsJsonObject()));
      }
    }

    Map<String, BanditParameters> bandits = new HashMap<>();
    JsonElement banditsEl = root.get("bandits");
    if (banditsEl != null && banditsEl.isJsonObject()) {
      for (Map.Entry<String, JsonElement> e : banditsEl.getAsJsonObject().entrySet()) {
        bandits.put(e.getKey(), deserializeBanditParameters(e.getValue().getAsJsonObject()));
      }
    }

    FlagConfigResponse.Format format =
        isObfuscated ? FlagConfigResponse.Format.CLIENT : FlagConfigResponse.Format.SERVER;
    FlagConfigResponse flagConfigResponse =
        new FlagConfigResponse.Default(flags, banditRefs, format, environmentName, publishedAt);

    Configuration.Builder builder = new Configuration.Builder(flagConfigResponse);
    if (!bandits.isEmpty()) {
      builder.banditParameters(new BanditParametersResponse.Default(bandits));
    }
    if (snapshotId != null) {
      builder.flagsSnapshotId(snapshotId);
    }

    return builder.build();
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
      variations.put(
          entry.getKey(),
          new Variation.Default(
              varObj.get("key").getAsString(), deserializeEppoValue(varObj.get("value"))));
    }
    return variations;
  }

  private List<Allocation> deserializeAllocations(JsonElement element) {
    List<Allocation> allocations = new ArrayList<>();
    if (element == null || !element.isJsonArray()) {
      return allocations;
    }
    for (JsonElement allocationEl : element.getAsJsonArray()) {
      JsonObject obj = allocationEl.getAsJsonObject();
      String key = obj.get("key").getAsString();
      Set<TargetingRule> rules = deserializeTargetingRules(obj.get("rules"));
      Date startAt = parseDateElement(obj.get("startAt"));
      Date endAt = parseDateElement(obj.get("endAt"));
      List<Split> splits = deserializeSplits(obj.get("splits"));
      boolean doLog = obj.get("doLog").getAsBoolean();
      allocations.add(new Allocation.Default(key, rules, startAt, endAt, splits, doLog));
    }
    return allocations;
  }

  private Set<TargetingRule> deserializeTargetingRules(JsonElement element) {
    Set<TargetingRule> rules = new HashSet<>();
    if (element == null || !element.isJsonArray()) {
      return rules;
    }
    for (JsonElement ruleEl : element.getAsJsonArray()) {
      JsonObject ruleObj = ruleEl.getAsJsonObject();
      Set<TargetingCondition> conditions = new HashSet<>();
      JsonElement conditionsEl = ruleObj.get("conditions");
      if (conditionsEl != null && conditionsEl.isJsonArray()) {
        for (JsonElement condEl : conditionsEl.getAsJsonArray()) {
          JsonObject cond = condEl.getAsJsonObject();
          OperatorType operator = OperatorType.fromString(cond.get("operator").getAsString());
          if (operator == null) {
            Log.w(TAG, "Unknown operator: " + cond.get("operator").getAsString());
            continue;
          }
          conditions.add(
              new TargetingCondition.Default(
                  operator,
                  cond.get("attribute").getAsString(),
                  deserializeEppoValue(cond.get("value"))));
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
    for (JsonElement splitEl : element.getAsJsonArray()) {
      JsonObject obj = splitEl.getAsJsonObject();
      String variationKey = obj.get("variationKey").getAsString();
      Set<Shard> shards = deserializeShards(obj.get("shards"));
      Map<String, String> extraLogging = new HashMap<>();
      JsonElement extraEl = obj.get("extraLogging");
      if (extraEl != null && extraEl.isJsonObject()) {
        for (Map.Entry<String, JsonElement> entry : extraEl.getAsJsonObject().entrySet()) {
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
    for (JsonElement shardEl : element.getAsJsonArray()) {
      JsonObject obj = shardEl.getAsJsonObject();
      String salt = obj.get("salt").getAsString();
      Set<ShardRange> ranges = new HashSet<>();
      JsonElement rangesEl = obj.get("ranges");
      if (rangesEl != null && rangesEl.isJsonArray()) {
        for (JsonElement rangeEl : rangesEl.getAsJsonArray()) {
          JsonObject range = rangeEl.getAsJsonObject();
          ranges.add(new ShardRange(range.get("start").getAsInt(), range.get("end").getAsInt()));
        }
      }
      shards.add(new Shard.Default(salt, ranges));
    }
    return shards;
  }

  private BanditReference deserializeBanditReference(JsonObject obj) {
    String modelVersion = obj.get("modelVersion").getAsString();
    List<BanditFlagVariation> flagVariations = new ArrayList<>();
    JsonElement fvsEl = obj.get("flagVariations");
    if (fvsEl != null && fvsEl.isJsonArray()) {
      for (JsonElement fvEl : fvsEl.getAsJsonArray()) {
        JsonObject fv = fvEl.getAsJsonObject();
        flagVariations.add(
            new BanditFlagVariation.Default(
                fv.get("key").getAsString(),
                fv.get("flagKey").getAsString(),
                fv.get("allocationKey").getAsString(),
                fv.get("variationKey").getAsString(),
                fv.get("variationValue").getAsString()));
      }
    }
    return new BanditReference.Default(modelVersion, flagVariations);
  }

  private BanditParameters deserializeBanditParameters(JsonObject obj) {
    String banditKey = obj.get("banditKey").getAsString();
    Date updatedAt = parseDateElement(obj.get("updatedAt"));
    String modelName = obj.get("modelName").getAsString();
    String modelVersion = obj.get("modelVersion").getAsString();
    BanditModelData modelData = deserializeBanditModelData(obj.get("modelData").getAsJsonObject());
    return new BanditParameters.Default(banditKey, updatedAt, modelName, modelVersion, modelData);
  }

  private BanditModelData deserializeBanditModelData(JsonObject obj) {
    double gamma = obj.get("gamma").getAsDouble();
    double defaultActionScore = obj.get("defaultActionScore").getAsDouble();
    double actionProbabilityFloor = obj.get("actionProbabilityFloor").getAsDouble();
    Map<String, BanditCoefficients> coefficients = new HashMap<>();
    JsonElement coefsEl = obj.get("coefficients");
    if (coefsEl != null && coefsEl.isJsonObject()) {
      for (Map.Entry<String, JsonElement> entry : coefsEl.getAsJsonObject().entrySet()) {
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
      result.put(
          attributeKey,
          new BanditNumericAttributeCoefficients.Default(
              attributeKey,
              obj.get("coefficient").getAsDouble(),
              obj.get("missingValueCoefficient").getAsDouble()));
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
      Map<String, Double> valueCoefficients = new HashMap<>();
      JsonElement valuesEl = obj.get("valueCoefficients");
      if (valuesEl != null && valuesEl.isJsonObject()) {
        for (Map.Entry<String, JsonElement> entry : valuesEl.getAsJsonObject().entrySet()) {
          valueCoefficients.put(entry.getKey(), entry.getValue().getAsDouble());
        }
      }
      result.put(
          attributeKey,
          new BanditCategoricalAttributeCoefficients.Default(
              attributeKey, obj.get("missingValueCoefficient").getAsDouble(), valueCoefficients));
    }
    return result;
  }

  // ===== Helpers =====

  private EppoValue deserializeEppoValue(JsonElement element) {
    if (element == null || element.isJsonNull()) {
      return EppoValue.nullValue();
    }
    if (element.isJsonArray()) {
      List<String> arr = new ArrayList<>();
      for (JsonElement item : element.getAsJsonArray()) {
        arr.add(item.getAsString());
      }
      return EppoValue.valueOf(arr);
    }
    if (element.isJsonPrimitive()) {
      if (element.getAsJsonPrimitive().isBoolean()) {
        return EppoValue.valueOf(element.getAsBoolean());
      }
      if (element.getAsJsonPrimitive().isNumber()) {
        return EppoValue.valueOf(element.getAsDouble());
      }
      return EppoValue.valueOf(element.getAsString());
    }
    Log.w(TAG, "Unexpected JSON element for EppoValue: " + element);
    return EppoValue.nullValue();
  }

  @Nullable private static Date parseDateElement(JsonElement element) {
    if (element == null || element.isJsonNull()) {
      return null;
    }
    try {
      return UTC_DATE_FORMAT.get().parse(element.getAsString());
    } catch (ParseException e) {
      Log.w(TAG, "Failed to parse date: " + element.getAsString());
      return null;
    }
  }

  @Nullable private static String stringOrNull(JsonObject obj, String key) {
    JsonElement el = obj.get(key);
    return (el != null && !el.isJsonNull()) ? el.getAsString() : null;
  }
}
