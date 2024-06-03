package cloud.eppo.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

import static cloud.eppo.android.util.Utils.base64Encode;
import static cloud.eppo.android.util.Utils.getMD5Hex;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cloud.eppo.android.dto.Allocation;
import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.FlagConfig;
import cloud.eppo.android.dto.OperatorType;
import cloud.eppo.android.dto.Range;
import cloud.eppo.android.dto.Shard;
import cloud.eppo.android.dto.Split;
import cloud.eppo.android.dto.SubjectAttributes;
import cloud.eppo.android.dto.TargetingCondition;
import cloud.eppo.android.dto.TargetingRule;
import cloud.eppo.android.dto.Variation;
import cloud.eppo.android.dto.VariationType;

@RunWith(RobolectricTestRunner.class) // Needed for anything that relies on Base64
public class FlagEvaluatorTest {

    @Test
    public void testDisabledFlag() {
        Map<String, Variation> variations = createVariations("a");
        Set<Shard> shards = createShards("salt");
        List<Split> splits = createSplits("a", shards);
        List<Allocation> allocations = createAllocations("allocation", splits);

        FlagConfig flag = createFlag(variations, allocations);
        flag.setEnabled(false);

        FlagEvaluationResult result = FlagEvaluator.evaluateFlag(
                flag,
                "flag",
                "subjectKey",
                new SubjectAttributes(),
                false
        );

        assertEquals(flag.getKey(), result.getFlagKey());
        assertEquals("subjectKey", result.getSubjectKey());
        assertEquals(new SubjectAttributes(), result.getSubjectAttributes());
        assertNull(result.getAllocationKey());
        assertNull(result.getVariation());
        assertFalse(result.doLog());
    }

    @Test
    public void testNoAllocations() {
        Map<String, Variation> variations = createVariations("a");

        FlagConfig flag = createFlag(variations, null);

        FlagEvaluationResult result = FlagEvaluator.evaluateFlag(
                flag,
                "flag",
                "subjectKey",
                new SubjectAttributes(),
                false
        );

        assertEquals(flag.getKey(), result.getFlagKey());
        assertEquals("subjectKey", result.getSubjectKey());
        assertEquals(new SubjectAttributes(), result.getSubjectAttributes());
        assertNull(result.getAllocationKey());
        assertNull(result.getVariation());
        assertFalse(result.doLog());
    }

    @Test
    public void testSimpleFlag() {
        Map<String, Variation> variations = createVariations("a");
        Set<Shard> shards = createShards("salt", 0, 10);
        List<Split> splits = createSplits("a", shards);
        List<Allocation> allocations = createAllocations("allocation", splits);
        FlagConfig flag = createFlag(variations, allocations);

        FlagEvaluationResult result = FlagEvaluator.evaluateFlag(
                flag,
                "flag",
                "subjectKey",
                new SubjectAttributes(),
                false
        );

        assertEquals(flag.getKey(), result.getFlagKey());
        assertEquals("subjectKey", result.getSubjectKey());
        assertEquals(new SubjectAttributes(), result.getSubjectAttributes());
        assertEquals("allocation", result.getAllocationKey());
        assertEquals("A", result.getVariation().getValue().stringValue());
        assertTrue(result.doLog());
    }

    @Test
    public void testIDTargetingCondition() {
        Map<String, Variation> variations = createVariations("a");
        List<Split> splits = createSplits("a");

        List<String> values = new LinkedList<>();
        values.add("alice");
        values.add("bob");
        EppoValue value = EppoValue.valueOf(values);
        Set<TargetingRule> rules = createRules("id", OperatorType.ONE_OF, value);

        List<Allocation> allocations = createAllocations("allocation", splits, rules);
        FlagConfig flag = createFlag(variations, allocations);

        // Check that subjectKey is evaluated as the "id" attribute

        FlagEvaluationResult result = FlagEvaluator.evaluateFlag(
                flag,
                "flag",
                "alice",
                new SubjectAttributes(),
                false
        );

        assertEquals("A", result.getVariation().getValue().stringValue());

        result = FlagEvaluator.evaluateFlag(
                flag,
                "flag",
                "bob",
                new SubjectAttributes(),
                false
        );

        assertEquals("A", result.getVariation().getValue().stringValue());

        result = FlagEvaluator.evaluateFlag(
                flag,
                "flag",
                "charlie",
                new SubjectAttributes(),
                false
        );

        assertNull(result.getVariation());

        // Check that an explicitly passed-in "id" attribute takes precedence

        SubjectAttributes aliceAttributes = new SubjectAttributes();
        aliceAttributes.put("id", "charlie");
        result = FlagEvaluator.evaluateFlag(
                flag,
                "flag",
                "alice",
                aliceAttributes,
                false
        );

        assertNull(result.getVariation());

        SubjectAttributes charlieAttributes = new SubjectAttributes();
        charlieAttributes.put("id", "alice");

        result = FlagEvaluator.evaluateFlag(
                flag,
                "flag",
                "charlie",
                charlieAttributes,
                false
        );

        assertEquals("A", result.getVariation().getValue().stringValue());
    }

    @Test
    public void testCatchAllAllocation() {
        Map<String, Variation> variations = createVariations("a", "b");
        List<Split> splits = createSplits("a");
        List<Allocation> allocations = createAllocations("default", splits);
        FlagConfig flag = createFlag(variations, allocations);

        FlagEvaluationResult result = FlagEvaluator.evaluateFlag(
                flag,
                "flag",
                "subjectKey",
                new SubjectAttributes(),
                false
        );

        assertEquals("default", result.getAllocationKey());
        assertEquals("A", result.getVariation().getValue().stringValue());
        assertTrue(result.doLog());
    }

    @Test
    public void testMultipleAllocations() {
        Map<String, Variation> variations = createVariations("a", "b");
        List<Split> firstAllocationSplits = createSplits("b");
        Set<TargetingRule> rules = createRules("email", OperatorType.MATCHES, EppoValue.valueOf(".*example\\.com$"));
        List<Allocation> allocations = createAllocations("first", firstAllocationSplits, rules);

        List<Split> defaultSplits = createSplits("a");
        allocations.addAll(createAllocations("default", defaultSplits));
        FlagConfig flag = createFlag(variations, allocations);

        SubjectAttributes matchingEmailAttributes = new SubjectAttributes();
        matchingEmailAttributes.put("email", "eppo@example.com");
        FlagEvaluationResult result = FlagEvaluator.evaluateFlag(
                flag,
                "flag",
                "subjectKey",
                matchingEmailAttributes,
                false
        );
        assertEquals("B", result.getVariation().getValue().stringValue());

        SubjectAttributes unknownEmailAttributes = new SubjectAttributes();
        unknownEmailAttributes.put("email", "eppo@test.com");
        result = FlagEvaluator.evaluateFlag(
                flag,
                "flag",
                "subjectKey",
                unknownEmailAttributes,
                false
        );
        assertEquals("A", result.getVariation().getValue().stringValue());

        result = FlagEvaluator.evaluateFlag(
                flag,
                "flag",
                "subjectKey",
                new SubjectAttributes(),
                false
        );
        assertEquals("A", result.getVariation().getValue().stringValue());
    }

    @Test
    public void testVariationShardRanges() {
        Map<String, Variation> variations = createVariations("a", "b", "c");
        Set<Shard> trafficShards = createShards("traffic", 0, 5);

        Set<Shard> shardsA = createShards("split", 0, 3);
        shardsA.addAll(trafficShards); // both splits include the same traffic shard
        List<Split> firstAllocationSplits = createSplits("a", shardsA);

        Set<Shard> shardsB = createShards("split", 3, 6);
        shardsB.addAll(trafficShards); // both splits include the same traffic shard
        firstAllocationSplits.addAll(createSplits("b", shardsB));

        List<Allocation> allocations = createAllocations("first", firstAllocationSplits);

        List<Split> defaultSplits = createSplits("c");
        allocations.addAll(createAllocations("default", defaultSplits));

        FlagConfig flag = createFlag(variations, allocations);

        FlagEvaluationResult result = FlagEvaluator.evaluateFlag(
                flag,
                "flag",
                "subject4",
                new SubjectAttributes(),
                false
        );

        assertEquals("A", result.getVariation().getValue().stringValue());

        result = FlagEvaluator.evaluateFlag(
                flag,
                "flag",
                "subject13",
                new SubjectAttributes(),
                false
        );

        assertEquals("B", result.getVariation().getValue().stringValue());

        result = FlagEvaluator.evaluateFlag(
                flag,
                "flag",
                "subject14",
                new SubjectAttributes(),
                false
        );

        assertEquals("C", result.getVariation().getValue().stringValue());
    }

    @Test
    public void testAllocationStartAndEndAt() {
        Map<String, Variation> variations = createVariations("a");
        List<Split> splits = createSplits("a");
        List<Allocation> allocations = createAllocations("allocation", splits);
        FlagConfig flag = createFlag(variations, allocations);

        // Start off with today being between startAt and endAt
        Date now = new Date();
        long oneDayInMilliseconds = 1000L * 60 * 60 * 24;
        Date startAt = new Date(now.getTime() - oneDayInMilliseconds);
        Date endAt = new Date(now.getTime() + oneDayInMilliseconds);

        Allocation allocation = allocations.get(0);
        allocation.setStartAt(startAt);
        allocation.setEndAt(endAt);

        FlagEvaluationResult result = FlagEvaluator.evaluateFlag(
                flag,
                "flag",
                "subject",
                new SubjectAttributes(),
                false
        );

        assertEquals("A", result.getVariation().getValue().stringValue());
        assertTrue(result.doLog());

        // Make both start startAt and endAt in the future
        allocation.setStartAt(new Date(now.getTime() + oneDayInMilliseconds));
        allocation.setEndAt(new Date(now.getTime() + 2 * oneDayInMilliseconds));

        result = FlagEvaluator.evaluateFlag(
                flag,
                "flag",
                "subject",
                new SubjectAttributes(),
                false
        );

        assertNull(result.getVariation());
        assertFalse(result.doLog());

        // Make both startAt and endAt in the past
        allocation.setStartAt(new Date(now.getTime() - 2 * oneDayInMilliseconds));
        allocation.setEndAt(new Date(now.getTime() - oneDayInMilliseconds));

        result = FlagEvaluator.evaluateFlag(
                flag,
                "flag",
                "subject",
                new SubjectAttributes(),
                false
        );

        assertNull(result.getVariation());
        assertFalse(result.doLog());
    }

    @Test
    public void testObfuscated() {
        // Note: this is NOT a comprehensive test of obfuscation (many operators and value types are
        // excluded, as are startAt and endAt)
        // Much more is covered by EppoClientTest

        Map<String, Variation> variations = createVariations("a", "b");
        List<Split> firstAllocationSplits = createSplits("b");
        Set<TargetingRule> rules = createRules("email", OperatorType.MATCHES, EppoValue.valueOf(".*example\\.com$"));
        List<Allocation> allocations = createAllocations("first", firstAllocationSplits, rules);

        List<Split> defaultSplits = createSplits("a");
        allocations.addAll(createAllocations("default", defaultSplits));
        FlagConfig flag = createFlag(variations, allocations);

        // Obfuscate the config

        // Hash the flag key (done in-place)
        flag.setKey(getMD5Hex(flag.getKey()));

        // Encode the variations (done by creating new map as keys change)
        Map<String, Variation> encodedVariations = new HashMap<>();
        for (Map.Entry<String, Variation> variationEntry : variations.entrySet()) {
            String encodedVariationKey = base64Encode(variationEntry.getKey());
            Variation variationToEncode = variationEntry.getValue();
            variationToEncode.setKey(encodedVariationKey);
            variationToEncode.setValue(EppoValue.valueOf(base64Encode(variationToEncode.getValue().stringValue())));
            encodedVariations.put(encodedVariationKey, variationToEncode);
        }
        flag.setVariations(encodedVariations);

        // Encode the allocations (done in-place)
        for (Allocation allocationToEncode : allocations) {
            allocationToEncode.setKey(base64Encode(allocationToEncode.getKey()));
            if (allocationToEncode.getRules() != null) {
                // assume just a single rule with a single string-valued condition
                TargetingCondition conditionToEncode = allocationToEncode.getRules().iterator().next().getConditions().iterator().next();
                conditionToEncode.setAttribute(getMD5Hex(conditionToEncode.getAttribute()));
                conditionToEncode.setValue(EppoValue.valueOf(base64Encode(conditionToEncode.getValue().stringValue())));
            }
            for (Split splitToEncode : allocationToEncode.getSplits()) {
                splitToEncode.setVariationKey(base64Encode(splitToEncode.getVariationKey()));
            }
        }

        SubjectAttributes matchingEmailAttributes = new SubjectAttributes();
        matchingEmailAttributes.put("email", "eppo@example.com");
        FlagEvaluationResult result = FlagEvaluator.evaluateFlag(
                flag,
                "flag",
                "subjectKey",
                matchingEmailAttributes,
                true
        );

        // Expect an unobfuscated evaluation result
        assertEquals("flag", result.getFlagKey());
        assertEquals("subjectKey", result.getSubjectKey());
        assertEquals(matchingEmailAttributes, result.getSubjectAttributes());
        assertEquals("first", result.getAllocationKey());
        assertEquals("B", result.getVariation().getValue().stringValue());
        assertTrue(result.doLog());

        SubjectAttributes unknownEmailAttributes = new SubjectAttributes();
        unknownEmailAttributes.put("email", "eppo@test.com");
        result = FlagEvaluator.evaluateFlag(
                flag,
                "flag",
                "subjectKey",
                unknownEmailAttributes,
                true
        );
        assertEquals("A", result.getVariation().getValue().stringValue());

        result = FlagEvaluator.evaluateFlag(
                flag,
                "flag",
                "subjectKey",
                new SubjectAttributes(),
                true
        );
        assertEquals("A", result.getVariation().getValue().stringValue());
    }

    private Map<String, Variation> createVariations(String key) {
        return createVariations(key, null, null);
    }

    private Map<String, Variation> createVariations(String key1, String key2) {
        return createVariations(key1, key2, null);
    }

    private Map<String, Variation> createVariations(String key1, String key2, String key3) {
        String[] keys = { key1, key2, key3 };
        Map<String, Variation> variations = new HashMap<>();
        for (String key : keys) {
            if (key != null) {
                Variation variation = new Variation();
                variation.setKey(key);
                // Use the uppercase key as the dummy value
                variation.setValue(EppoValue.valueOf(key.toUpperCase()));
                variations.put(variation.getKey(), variation);
            }
        }
        return variations;
    }

    private Set<Shard> createShards(String salt) {
        return createShards(salt, null, null);
    }

    private Set<Shard> createShards(String salt, Integer rangeStart, Integer rangeEnd) {
        Shard shard = new Shard();
        shard.setSalt(salt);
        if (rangeStart != null) {
            Range range = new Range();
            range.setStart(rangeStart);
            range.setEnd(rangeEnd);
            Set<Range> ranges = new HashSet<>();
            ranges.add(range);
            shard.setRanges(ranges);
        }
        Set<Shard> shards = new HashSet<>();
        shards.add(shard);
        return shards;
    }

    private List<Split> createSplits(String variationKey) {
        return createSplits(variationKey, null);
    }

    private List<Split> createSplits(String variationKey, Set<Shard> shards) {
        Split split = new Split();
        split.setVariationKey(variationKey);
        split.setShards(shards);
        List<Split> splits = new ArrayList<>();
        splits.add(split);
        return splits;
    }

    private Set<TargetingRule> createRules(String attribute, OperatorType operator, EppoValue value) {
        TargetingCondition condition = new TargetingCondition();
        condition.setAttribute(attribute);
        condition.setOperator(operator);
        condition.setValue(value);
        Set<TargetingCondition> conditions = new HashSet<>();
        conditions.add(condition);
        TargetingRule rule = new TargetingRule();
        rule.setConditions(conditions);
        Set<TargetingRule> rules = new HashSet<>();
        rules.add(rule);
        return rules;
    }

    private List<Allocation> createAllocations(String allocationKey, List<Split> splits) {
        return createAllocations(allocationKey, splits, null);
    }

    private List<Allocation> createAllocations(String allocationKey, List<Split> splits, Set<TargetingRule> rules) {
        Allocation allocation = new Allocation();
        allocation.setKey(allocationKey);
        allocation.setSplits(splits);
        allocation.setRules(rules);
        allocation.setDoLog(true);
        List<Allocation> allocations = new ArrayList<>();
        allocations.add(allocation);
        return allocations;
    }

    private FlagConfig createFlag(Map<String, Variation> variations, List<Allocation> allocations) {
        FlagConfig flag = new FlagConfig();
        flag.setKey("flag");
        flag.setTotalShards(10);
        flag.setEnabled(true);
        flag.setVariationType(VariationType.STRING);
        flag.setVariations(variations);
        flag.setAllocations(allocations);
        return flag;
    }
}
