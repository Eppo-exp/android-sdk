package cloud.eppo.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.ArrayList;
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
        Map<String, Variation> variations = new HashMap<>();
        Variation variation = new Variation();
        variation.setKey("variation");
        variations.put(variation.getKey(), variation);

        Range range = new Range();
        range.setStart(0);
        range.setEnd(10);
        Set<Range> ranges = new HashSet<>();
        ranges.add(range);

        Shard shard = new Shard();
        shard.setSalt("salt");
        shard.setRanges(ranges);
        Set<Shard> shards = new HashSet<>();
        shards.add(shard);

        Split split = new Split();
        split.setVariationKey("variation");
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
        Map<String, Variation> variations = new HashMap<>();
        Variation variation = new Variation();
        variation.setKey("variation");
        variations.put(variation.getKey(), variation);

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
        Map<String, Variation> variations = new HashMap<>();
        Variation variation = new Variation();
        variation.setKey("control");
        variation.setValue(EppoValue.valueOf("control-value"));
        variations.put(variation.getKey(), variation);

        Range range = new Range();
        range.setStart(0);
        range.setEnd(10000);
        Set<Range> ranges = new HashSet<>();
        ranges.add(range);

        Shard shard = new Shard();
        shard.setSalt("salt");
        shard.setRanges(ranges);
        Set<Shard> shards = new HashSet<>();
        shards.add(shard);

        Split split = new Split();
        split.setVariationKey("control");
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
        assertEquals(variation, result.getVariation());
        assertTrue(result.doLog());
    }

    @Test
    public void testSubjectKeyIDTargetingCondition() {
        Map<String, Variation> variations = new HashMap<>();
        Variation variation = new Variation();
        variation.setKey("control");
        variation.setValue(EppoValue.valueOf("control-value"));
        variations.put(variation.getKey(), variation);

        Split split = new Split();
        split.setVariationKey("control");
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

        assertEquals(variation, result.getVariation());

        result = FlagEvaluator.evaluateFlag(
                flag,
                "bob",
                new SubjectAttributes(),
                false
        );

        assertEquals(variation, result.getVariation());

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
        Map<String, Variation> variations = new HashMap<>();
        Variation variation = new Variation();
        variation.setKey("control");
        variation.setValue(EppoValue.valueOf("control-value"));
        variations.put(variation.getKey(), variation);

        Split split = new Split();
        split.setVariationKey("control");
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

        assertEquals(variation, result.getVariation());
    }

    @Test
    public void testCatchAllAllocation() {
        Variation variationA = new Variation();
        variationA.setKey("a");
        variationA.setValue(EppoValue.valueOf("A"));
        Variation variationB = new Variation();
        variationB.setKey("b");
        variationB.setValue(EppoValue.valueOf("B"));

        Map<String, Variation> variations = new HashMap<>();
        variations.put(variationA.getKey(), variationA);
        variations.put(variationB.getKey(), variationB);

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
        assertEquals(variationA, result.getVariation());
        assertTrue(result.doLog());
    }

    @Test
    public void testMultipleAllocations() {
        Variation variationA = new Variation();
        variationA.setKey("a");
        variationA.setValue(EppoValue.valueOf("A"));
        Variation variationB = new Variation();
        variationB.setKey("b");
        variationB.setValue(EppoValue.valueOf("B"));

        Map<String, Variation> variations = new HashMap<>();
        variations.put(variationA.getKey(), variationA);
        variations.put(variationB.getKey(), variationB);

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
        assertEquals(variationB, result.getVariation());

        SubjectAttributes unknownEmailAttributes = new SubjectAttributes();
        unknownEmailAttributes.put("email", "eppo@test.com");
        result = FlagEvaluator.evaluateFlag(
                flag,
                "subjectKey",
                unknownEmailAttributes,
                false
        );
        assertEquals(variationA, result.getVariation());

        result = FlagEvaluator.evaluateFlag(
                flag,
                "subjectKey",
                new SubjectAttributes(),
                false
        );
        assertEquals(variationA, result.getVariation());
    }

    @Test
    public void testVariationShardRanges() {
        Variation variationA = new Variation();
        variationA.setKey("a");
        variationA.setValue(EppoValue.valueOf("A"));
        Variation variationB = new Variation();
        variationB.setKey("b");
        variationB.setValue(EppoValue.valueOf("B"));
        Variation variationC = new Variation();
        variationC.setKey("c");
        variationC.setValue(EppoValue.valueOf("C"));

        Map<String, Variation> variations = new HashMap<>();
        variations.put(variationA.getKey(), variationA);
        variations.put(variationB.getKey(), variationB);
        variations.put(variationC.getKey(), variationC);

        Range trafficRange = new Range();
        trafficRange.setStart(0);
        trafficRange.setEnd(5);
        Set<Range> trafficRanges = new HashSet<>();
        trafficRanges.add(trafficRange);

        Shard trafficShard = new Shard();
        trafficShard.setSalt("traffic");
        trafficShard.setRanges(trafficRanges);

        Range splitRangeA = new Range();
        splitRangeA.setStart(0);
        splitRangeA.setEnd(3);
        Set<Range> splitRangesA = new HashSet<>();
        splitRangesA.add(splitRangeA);

        Shard splitShardA = new Shard();
        splitShardA.setSalt("split");
        splitShardA.setRanges(splitRangesA);

        Split splitA = new Split();
        splitA.setVariationKey("a");
        Set<Shard> shardsA = new HashSet<>();
        shardsA.add(trafficShard);
        shardsA.add(splitShardA);
        splitA.setShards(shardsA);

        Range splitRangeB = new Range();
        splitRangeB.setStart(3);
        splitRangeB.setEnd(6);
        Set<Range> splitRangesB = new HashSet<>();
        splitRangesB.add(splitRangeB);

        Shard splitShardB = new Shard();
        splitShardB.setSalt("split");
        splitShardB.setRanges(splitRangesB);

        Split splitB = new Split();
        splitB.setVariationKey("b");
        Set<Shard> shardsB = new HashSet<>();
        shardsB.add(trafficShard);
        shardsB.add(splitShardB);
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

        for (int i = 0; i < 50; i +=1) {
            FlagEvaluationResult result = FlagEvaluator.evaluateFlag(
                    flag,
                    "subject"+i,
                    new SubjectAttributes(),
                    false
            );
            System.out.println("subject"+i+": "+(result.getVariation() != null ? result.getVariation().getValue() : "null"));
        }

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
}
