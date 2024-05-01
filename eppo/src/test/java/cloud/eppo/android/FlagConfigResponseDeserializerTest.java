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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import cloud.eppo.android.dto.Allocation;
import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.FlagConfig;
import cloud.eppo.android.dto.FlagConfigResponse;
import cloud.eppo.android.dto.OperatorType;
import cloud.eppo.android.dto.Range;
import cloud.eppo.android.dto.Shard;
import cloud.eppo.android.dto.Split;
import cloud.eppo.android.dto.TargetingCondition;
import cloud.eppo.android.dto.Variation;
import cloud.eppo.android.dto.VariationType;
import cloud.eppo.android.dto.deserializers.EppoValueDeserializer;
import cloud.eppo.android.dto.deserializers.FlagConfigResponseDeserializer;

public class FlagConfigResponseDeserializerTest {

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(FlagConfigResponse.class, new FlagConfigResponseDeserializer())
            .registerTypeAdapter(EppoValue.class, new EppoValueDeserializer())
            .serializeNulls()
            .create();

    @Test
    public void testDeserializePlainText() throws IOException {
        File testRac = new File("src/androidTest/assets/flags-v1.json");
        FileReader fileReader = new FileReader(testRac);
        FlagConfigResponse configResponse = gson.fromJson(fileReader, FlagConfigResponse.class);

        assertTrue(configResponse.getFlags().size() >= 13);
        assertTrue(configResponse.getFlags().containsKey("empty_flag"));
        assertTrue(configResponse.getFlags().containsKey("disabled_flag"));
        assertTrue(configResponse.getFlags().containsKey("no_allocations_flag"));
        assertTrue(configResponse.getFlags().containsKey("numeric_flag"));
        assertTrue(configResponse.getFlags().containsKey("invalid-value-flag"));
        assertTrue(configResponse.getFlags().containsKey("kill-switch"));
        assertTrue(configResponse.getFlags().containsKey("semver-test"));
        assertTrue(configResponse.getFlags().containsKey("comparator-operator-test"));
        assertTrue(configResponse.getFlags().containsKey("start-and-end-date-test"));
        assertTrue(configResponse.getFlags().containsKey("null-operator-test"));
        assertTrue(configResponse.getFlags().containsKey("new-user-onboarding"));
        assertTrue(configResponse.getFlags().containsKey("integer-flag"));
        assertTrue(configResponse.getFlags().containsKey("json-config-flag"));

        FlagConfig flagConfig = configResponse.getFlags().get("kill-switch");
        assertNotNull(flagConfig);
        assertEquals(flagConfig.getKey(), "kill-switch");
        assertTrue(flagConfig.isEnabled());
        assertEquals(VariationType.Boolean, flagConfig.getVariationType());

        Map<String, Variation> variations = flagConfig.getVariations();
        assertEquals(2, variations.size());
        Variation onVariation = variations.get("on");
        assertNotNull(onVariation);
        assertEquals("on", onVariation.getKey());
        assertTrue(onVariation.getValue().boolValue());
        Variation offVariation = variations.get("off");
        assertNotNull(offVariation);
        assertEquals("off", offVariation.getKey());
        assertFalse(offVariation.getValue().boolValue());

        List<Allocation> allocations = flagConfig.getAllocations();
        assertEquals(3, allocations.size());

        Allocation northAmericaAllocation = allocations.get(0);
        assertEquals("on-for-NA", northAmericaAllocation.getKey());
        assertTrue(northAmericaAllocation.doLog());
        assertEquals(1, northAmericaAllocation.getRules().size());
        TargetingCondition northAmericaCondition = northAmericaAllocation.getRules()
                .iterator()
                .next()
                .getConditions()
                .iterator()
                .next();
        assertEquals("country", northAmericaCondition.getAttribute());
        assertEquals(OperatorType.OneOf, northAmericaCondition.getOperator());
        List<String> expectedValues = new ArrayList<>();
        expectedValues.add("US");
        expectedValues.add("Canada");
        expectedValues.add("Mexico");
        assertEquals(expectedValues, northAmericaCondition.getValue().stringArrayValue());

        assertEquals(1, northAmericaAllocation.getSplits().size());
        Split northAmericaSplit = northAmericaAllocation.getSplits().iterator().next();
        assertEquals("on", northAmericaSplit.getVariationKey());

        Shard northAmericaShard = northAmericaSplit.getShards().iterator().next();
        assertEquals("some-salt", northAmericaShard.getSalt());

        Range northAmericaRange = northAmericaShard.getRanges().iterator().next();
        assertEquals(0, northAmericaRange.getStart());
        assertEquals(10000, northAmericaRange.getEnd());

        Allocation fiftyPlusAllocation = allocations.get(1);
        assertEquals("on-for-age-50+", fiftyPlusAllocation.getKey());
        assertTrue(fiftyPlusAllocation.doLog());
        assertEquals(1, fiftyPlusAllocation.getRules().size());
        TargetingCondition fiftyPlusCondition = fiftyPlusAllocation.getRules()
                .iterator()
                .next()
                .getConditions()
                .iterator()
                .next();
        assertEquals("age", fiftyPlusCondition.getAttribute());
        assertEquals(OperatorType.GreaterThanEqualTo, fiftyPlusCondition.getOperator());
        assertEquals(50, fiftyPlusCondition.getValue().doubleValue(), 0.0);

        assertEquals(1, fiftyPlusAllocation.getSplits().size());
        Split fiftyPlusSplit = fiftyPlusAllocation.getSplits().iterator().next();
        assertEquals("on", fiftyPlusSplit.getVariationKey());

        Shard fiftyPlusShard = fiftyPlusSplit.getShards().iterator().next();
        assertEquals("some-salt", fiftyPlusShard.getSalt());

        Range fiftyPlusRange = fiftyPlusShard.getRanges().iterator().next();
        assertEquals(0, fiftyPlusRange.getStart());
        assertEquals(10000, fiftyPlusRange.getEnd());

        Allocation offForAll = allocations.get(2);
        assertEquals("off-for-all", offForAll.getKey());
        assertTrue(offForAll.doLog());
        assertEquals(0, offForAll.getRules().size());

        assertEquals(1, offForAll.getSplits().size());
        Split offForAllSplit = offForAll.getSplits().iterator().next();
        assertEquals("off", offForAllSplit.getVariationKey());
        assertEquals(0, offForAllSplit.getShards().size());
    }
}
