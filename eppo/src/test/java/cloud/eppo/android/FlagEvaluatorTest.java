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
        allocation.setRules(new HashSet<>());
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
        flag.setAllocations(new LinkedList<>());
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
        allocation.setRules(new HashSet<>());
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
        split.setShards(new HashSet<>());
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
        split.setShards(new HashSet<>());
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
}
