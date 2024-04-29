package cloud.eppo.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.List;

import cloud.eppo.android.dto.Allocation;
import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.FlagConfig;
import cloud.eppo.android.dto.OperatorType;
import cloud.eppo.android.dto.RandomizationConfigResponse;
import cloud.eppo.android.dto.Range;
import cloud.eppo.android.dto.TargetingCondition;
import cloud.eppo.android.dto.TargetingRule;
import cloud.eppo.android.dto.Variation;
import cloud.eppo.android.dto.deserializers.EppoValueAdapter;
import cloud.eppo.android.dto.deserializers.RandomizationConfigResponseDeserializer;

public class RandomizationConfigResponseDeserializerTest {

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(RandomizationConfigResponse.class, new RandomizationConfigResponseDeserializer())
            .registerTypeAdapter(EppoValue.class, new EppoValueAdapter())
            .serializeNulls()
            .create();

    @Test
    public void testDeserialize() throws IOException {
        File testRac = new File("src/androidTest/assets/rac-experiments-v3.json");
        FileReader fileReader = new FileReader(testRac);
        RandomizationConfigResponse configResponse = gson.fromJson(fileReader, RandomizationConfigResponse.class);

        assertEquals(10, configResponse.getFlags().size());
        assertTrue(configResponse.getFlags().containsKey("randomization_algo"));
        assertTrue(configResponse.getFlags().containsKey("new_user_onboarding"));
        assertTrue(configResponse.getFlags().containsKey("disabled_experiment_with_overrides"));
        assertTrue(configResponse.getFlags().containsKey("targeting_rules_experiment"));
        assertTrue(configResponse.getFlags().containsKey("experiment_with_numeric_variations"));
        assertTrue(configResponse.getFlags().containsKey("experiment_with_json_variations"));
        assertTrue(configResponse.getFlags().containsKey("test_bandit_1"));
        assertTrue(configResponse.getFlags().containsKey("experiment_with_holdout"));
        assertTrue(configResponse.getFlags().containsKey("rollout_with_holdout"));

        FlagConfig flagConfig = configResponse.getFlags().get("disabled_experiment_with_overrides");
        assertNotNull(flagConfig);
        assertFalse(flagConfig.isEnabled());
        assertEquals(10000, flagConfig.getTotalShards());
        Map<String, String> typedOverrides = flagConfig.getTypedOverrides();
        assertEquals("treatment", typedOverrides.get("0bcbfc2660c78c549b0fbf870e3dc3ea"));
        assertEquals("control", typedOverrides.get("50a681dcd4046400e5c675e85b69b4ac"));

        List<TargetingRule> targetingRules = flagConfig.getRules();
        assertEquals(1, targetingRules.size());
        assertEquals("allocation-experiment-3", targetingRules.get(0).getAllocationKey());
        assertTrue(targetingRules.get(0).getConditions().isEmpty());

        Map<String, Allocation> allocations = flagConfig.getAllocations();
        assertEquals(1, allocations.size());
        Allocation allocation = allocations.get("allocation-experiment-3");
        assertNotNull(allocation);
        assertEquals(1.0, allocation.getPercentExposure(), 0.001);

        List<Variation> variations = allocation.getVariations();
        assertEquals(2, variations.size());

        Variation controlVariation = variations.get(0);
        assertEquals("control", controlVariation.getTypedValue().stringValue());
        Range controlShardRange = controlVariation.getShardRange();
        assertEquals(0, controlShardRange.getStart());
        assertEquals(5000, controlShardRange.getEnd());

        Variation testVariation = variations.get(1);
        assertEquals("treatment", testVariation.getTypedValue().stringValue());
        Range testShardRange = testVariation.getShardRange();
        assertEquals(5000, testShardRange.getStart());
        assertEquals(10000, testShardRange.getEnd());

        // Need another flag to check targeting rules
        flagConfig = configResponse.getFlags().get("targeting_rules_experiment");
        assertNotNull(flagConfig);
        assertTrue(flagConfig.isEnabled());
        assertTrue(flagConfig.getTypedOverrides().isEmpty());
        targetingRules = flagConfig.getRules();
        assertEquals(3, targetingRules.size());

        TargetingRule rule = targetingRules.get(0);
        assertEquals("allocation-experiment-4", rule.getAllocationKey());
        List<TargetingCondition> conditions = rule.getConditions();
        assertEquals(2, conditions.size());

        TargetingCondition condition = conditions.get(0);
        assertEquals("device", condition.getAttribute());
        assertEquals(OperatorType.OneOf,  condition.getOperator());
        assertEquals("iOS", condition.getValue().arrayValue().get(0));
        assertEquals("Android", condition.getValue().arrayValue().get(1));

        condition = conditions.get(1);
        assertEquals("version", condition.getAttribute());
        assertEquals(OperatorType.GreaterThan,  condition.getOperator());
        assertEquals(1.0, condition.getValue().doubleValue(), 0.001);

        rule = targetingRules.get(1);
        assertEquals("allocation-experiment-4", rule.getAllocationKey());
        conditions = rule.getConditions();
        assertEquals(1, conditions.size());

        condition = conditions.get(0);
        assertEquals("country", condition.getAttribute());
        assertEquals(OperatorType.NotOneOf,  condition.getOperator());
        assertEquals(1, condition.getValue().arrayValue().size());
        assertEquals("China", condition.getValue().arrayValue().get(0));

        rule = targetingRules.get(2);
        assertEquals("allocation-experiment-4", rule.getAllocationKey());
        conditions = rule.getConditions();
        assertEquals(1, conditions.size());

        condition = conditions.get(0);
        assertEquals("email", condition.getAttribute());
        assertEquals(OperatorType.Matches,  condition.getOperator());
        assertEquals(".*geteppo.com", condition.getValue().stringValue());
    }

}