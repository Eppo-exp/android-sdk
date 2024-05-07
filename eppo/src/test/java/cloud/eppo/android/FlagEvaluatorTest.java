package cloud.eppo.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

import org.junit.Test;

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

public class FlagEvaluatorTest {

    @Test
    public void testDisabledFlag() {
        Map<String, Variation> variations = createVariations("a");
        Set<Shard> shards = createShards("salt");

        Split split = new Split();
        split.setVariationKey("a");
        split.setShards(shards);
        List<Split> splits = new ArrayList<>();
        splits.add(split);

        Allocation allocation = new Allocation();
        allocation.setKey("allocation");
        allocation.setSplits(splits);
        List<Allocation> allocations = new ArrayList<>();
        allocations.add(allocation);

        FlagConfig flag = new FlagConfig();
        flag.setKey("flag");
        flag.setVariations(variations);
        flag.setAllocations(allocations);
        flag.setTotalShards(10);
        flag.setEnabled(false);

        FlagEvaluationResult result = FlagEvaluator.evaluateFlag(
                flag,
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

        FlagConfig flag = new FlagConfig();
        flag.setKey("flag");
        flag.setVariations(variations);
        flag.setTotalShards(10);
        flag.setEnabled(false);

        FlagEvaluationResult result = FlagEvaluator.evaluateFlag(
                flag,
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
        Set<Shard> shards = createShards("salt", 0, 10000);

        Split split = new Split();
        split.setVariationKey("a");
        split.setShards(shards);
        List<Split> splits = new ArrayList<>();
        splits.add(split);

        Allocation allocation = new Allocation();
        allocation.setKey("allocation");
        allocation.setSplits(splits);
        allocation.setDoLog(true);
        List<Allocation> allocations = new ArrayList<>();
        allocations.add(allocation);

        FlagConfig flag = new FlagConfig();
        flag.setKey("flag");
        flag.setVariations(variations);
        flag.setAllocations(allocations);
        flag.setTotalShards(10);
        flag.setEnabled(true);

        FlagEvaluationResult result = FlagEvaluator.evaluateFlag(
                flag,
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
    public void testSubjectKeyIDTargetingCondition() {
        Map<String, Variation> variations = createVariations("a");

        Split split = new Split();
        split.setVariationKey("a");
        List<Split> splits = new ArrayList<>();
        splits.add(split);

        TargetingCondition condition = new TargetingCondition();
        condition.setAttribute("id");
        condition.setOperator(OperatorType.ONE_OF);
        List<String> values = new LinkedList<>();
        values.add("alice");
        values.add("bob");
        condition.setValue(EppoValue.valueOf(values));
        Set<TargetingCondition> conditions = new HashSet<>();
        conditions.add(condition);

        TargetingRule rule = new TargetingRule();
        rule.setConditions(conditions);

        Set<TargetingRule> rules = new HashSet<>();
        rules.add(rule);

        Allocation allocation = new Allocation();
        allocation.setKey("allocation");
        allocation.setRules(rules);
        allocation.setSplits(splits);
        allocation.setDoLog(true);
        List<Allocation> allocations = new ArrayList<>();
        allocations.add(allocation);

        FlagConfig flag = new FlagConfig();
        flag.setKey("flag");
        flag.setVariations(variations);
        flag.setAllocations(allocations);
        flag.setTotalShards(10);
        flag.setEnabled(true);

        FlagEvaluationResult result = FlagEvaluator.evaluateFlag(
                flag,
                "alice",
                new SubjectAttributes(),
                false
        );

        assertEquals("A", result.getVariation().getValue().stringValue());

        result = FlagEvaluator.evaluateFlag(
                flag,
                "bob",
                new SubjectAttributes(),
                false
        );

        assertEquals("A", result.getVariation().getValue().stringValue());

        result = FlagEvaluator.evaluateFlag(
                flag,
                "charlie",
                new SubjectAttributes(),
                false
        );

        assertNull(result.getVariation());
    }

    @Test
    public void testOverriddenIDTargetingCondition() {
        Map<String, Variation> variations = createVariations("a");

        Split split = new Split();
        split.setVariationKey("a");
        List<Split> splits = new ArrayList<>();
        splits.add(split);

        TargetingCondition condition = new TargetingCondition();
        condition.setAttribute("id");
        condition.setOperator(OperatorType.ONE_OF);
        List<String> values = new LinkedList<>();
        values.add("alice");
        values.add("bob");
        condition.setValue(EppoValue.valueOf(values));
        Set<TargetingCondition> conditions = new HashSet<>();
        conditions.add(condition);

        TargetingRule rule = new TargetingRule();
        rule.setConditions(conditions);

        Set<TargetingRule> rules = new HashSet<>();
        rules.add(rule);

        Allocation allocation = new Allocation();
        allocation.setKey("allocation");
        allocation.setRules(rules);
        allocation.setSplits(splits);
        allocation.setDoLog(true);
        List<Allocation> allocations = new ArrayList<>();
        allocations.add(allocation);

        FlagConfig flag = new FlagConfig();
        flag.setKey("flag");
        flag.setVariations(variations);
        flag.setAllocations(allocations);
        flag.setTotalShards(10);
        flag.setEnabled(true);

        SubjectAttributes aliceAttributes = new SubjectAttributes();
        aliceAttributes.put("id", "charlie");
        FlagEvaluationResult result = FlagEvaluator.evaluateFlag(
                flag,
                "alice",
                aliceAttributes,
                false
        );

        assertNull(result.getVariation());

        SubjectAttributes charlieAttributes = new SubjectAttributes();
        charlieAttributes.put("id", "alice");

        result = FlagEvaluator.evaluateFlag(
                flag,
                "charlie",
                charlieAttributes,
                false
        );

        assertEquals("A", result.getVariation().getValue().stringValue());
    }

    @Test
    public void testCatchAllAllocation() {
        Map<String, Variation> variations = createVariations("a", "b");

        Split split = new Split();
        split.setVariationKey("a");
        List<Split> splits = new ArrayList<>();
        splits.add(split);

        Allocation allocation = new Allocation();
        allocation.setKey("default");
        allocation.setSplits(splits);
        allocation.setDoLog(true);
        List<Allocation> allocations = new ArrayList<>();
        allocations.add(allocation);

        FlagConfig flag = new FlagConfig();
        flag.setKey("flag");
        flag.setVariations(variations);
        flag.setAllocations(allocations);
        flag.setTotalShards(10);
        flag.setEnabled(true);

        FlagEvaluationResult result = FlagEvaluator.evaluateFlag(
                flag,
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

        Split firstAllocationSplit = new Split();
        firstAllocationSplit.setVariationKey("b");
        List<Split> firstAllocationSplits = new ArrayList<>();
        firstAllocationSplits.add(firstAllocationSplit);

        TargetingCondition condition = new TargetingCondition();
        condition.setAttribute("email");
        condition.setOperator(OperatorType.MATCHES);
        condition.setValue(EppoValue.valueOf(".*example\\.com$"));
        Set<TargetingCondition> conditions = new HashSet<>();
        conditions.add(condition);

        TargetingRule rule = new TargetingRule();
        rule.setConditions(conditions);

        Set<TargetingRule> rules = new HashSet<>();
        rules.add(rule);

        Allocation firstAllocation = new Allocation();
        firstAllocation.setKey("first");
        firstAllocation.setRules(rules);
        firstAllocation.setSplits(firstAllocationSplits);
        firstAllocation.setDoLog(true);

        Split defaultSplit = new Split();
        defaultSplit.setVariationKey("a");
        List<Split> defaultSplits = new ArrayList<>();
        defaultSplits.add(defaultSplit);

        Allocation defaultAllocation = new Allocation();
        defaultAllocation.setKey("default");
        defaultAllocation.setSplits(defaultSplits);
        defaultAllocation.setDoLog(true);

        List<Allocation> allocations = new ArrayList<>();
        allocations.add(firstAllocation);
        allocations.add(defaultAllocation);

        FlagConfig flag = new FlagConfig();
        flag.setKey("flag");
        flag.setVariations(variations);
        flag.setAllocations(allocations);
        flag.setTotalShards(10);
        flag.setEnabled(true);

        SubjectAttributes matchingEmailAttributes = new SubjectAttributes();
        matchingEmailAttributes.put("email", "eppo@example.com");
        FlagEvaluationResult result = FlagEvaluator.evaluateFlag(
                flag,
                "subjectKey",
                matchingEmailAttributes,
                false
        );
        assertEquals("B", result.getVariation().getValue().stringValue());

        SubjectAttributes unknownEmailAttributes = new SubjectAttributes();
        unknownEmailAttributes.put("email", "eppo@test.com");
        result = FlagEvaluator.evaluateFlag(
                flag,
                "subjectKey",
                unknownEmailAttributes,
                false
        );
        assertEquals("A", result.getVariation().getValue().stringValue());

        result = FlagEvaluator.evaluateFlag(
                flag,
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

        Range splitRangeA = new Range();
        splitRangeA.setStart(0);
        splitRangeA.setEnd(3);
        Set<Range> splitRangesA = new HashSet<>();
        splitRangesA.add(splitRangeA);

        Set<Shard> shardsA = createShards("split", 0, 3);
        shardsA.addAll(trafficShards);

        Split splitA = new Split();
        splitA.setVariationKey("a");
        splitA.setShards(shardsA);

        Set<Shard> shardsB = createShards("split", 3, 6);
        shardsB.addAll(trafficShards);

        Split splitB = new Split();
        splitB.setVariationKey("b");
        splitB.setShards(shardsB);

        List<Split> firstSplits = new ArrayList<>();
        firstSplits.add(splitA);
        firstSplits.add(splitB);

        Allocation allocation = new Allocation();
        allocation.setKey("first");
        allocation.setSplits(firstSplits);
        allocation.setDoLog(true);

        Split defaultSplit = new Split();
        defaultSplit.setVariationKey("c");
        List<Split> defaultSplits = new ArrayList<>();
        defaultSplits.add(defaultSplit);

        Allocation defaultAllocation = new Allocation();
        defaultAllocation.setKey("default");
        defaultAllocation.setSplits(defaultSplits);
        defaultAllocation.setDoLog(true);

        List<Allocation> allocations = new ArrayList<>();
        allocations.add(allocation);
        allocations.add(defaultAllocation);

        FlagConfig flag = new FlagConfig();
        flag.setKey("flag");
        flag.setVariations(variations);
        flag.setAllocations(allocations);
        flag.setTotalShards(10);
        flag.setEnabled(true);

        FlagEvaluationResult result = FlagEvaluator.evaluateFlag(
                flag,
                "subject4",
                new SubjectAttributes(),
                false
        );

        assertEquals("A", result.getVariation().getValue().stringValue());

        result = FlagEvaluator.evaluateFlag(
                flag,
                "subject13",
                new SubjectAttributes(),
                false
        );

        assertEquals("B", result.getVariation().getValue().stringValue());

        result = FlagEvaluator.evaluateFlag(
                flag,
                "subject14",
                new SubjectAttributes(),
                false
        );

        assertEquals("C", result.getVariation().getValue().stringValue());
    }

    @Test
    public void testAllocationStartAndEndAt() {
        Map<String, Variation> variations = createVariations("a");

        Split split = new Split();
        split.setVariationKey("a");
        List<Split> splits = new ArrayList<>();
        splits.add(split);

        Allocation allocation = new Allocation();
        allocation.setKey("allocation");
        allocation.setSplits(splits);
        allocation.setDoLog(true);

        // Start off with today being between startAt and endAt
        Date now = new Date();
        long oneDayInMilliseconds = 1000L * 60 * 60 * 24;
        Date startAt = new Date(now.getTime() - oneDayInMilliseconds);
        Date endAt = new Date(now.getTime() + oneDayInMilliseconds);

        allocation.setStartAt(startAt);
        allocation.setEndAt(endAt);

        List<Allocation> allocations = new LinkedList<>();
        allocations.add(allocation);

        FlagConfig flag = new FlagConfig();
        flag.setKey("flag");
        flag.setVariations(variations);
        flag.setAllocations(allocations);
        flag.setTotalShards(10);
        flag.setEnabled(true);

        FlagEvaluationResult result = FlagEvaluator.evaluateFlag(
                flag,
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
                "subject",
                new SubjectAttributes(),
                false
        );

        assertNull(result.getVariation());
        assertFalse(result.doLog());
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
}
