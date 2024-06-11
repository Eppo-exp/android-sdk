package cloud.eppo.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import cloud.eppo.ufc.dto.EppoValue;
import cloud.eppo.ufc.dto.OperatorType;
import cloud.eppo.ufc.dto.SubjectAttributes;
import cloud.eppo.ufc.dto.TargetingCondition;
import cloud.eppo.ufc.dto.TargetingRule;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class RuleEvaluatorTest {

  public TargetingRule createRule(Set<TargetingCondition> conditions) {
    return new TargetingRule(conditions);
  }

  public void addConditionToRule(TargetingRule TargetingRule, TargetingCondition condition) {
    TargetingRule.getConditions().add(condition);
  }

  public void addNumericConditionToRule(TargetingRule TargetingRule) {
    TargetingCondition condition1 =
        new TargetingCondition(
            OperatorType.GREATER_THAN_OR_EQUAL_TO, "price", EppoValue.valueOf(10));
    TargetingCondition condition2 =
        new TargetingCondition(OperatorType.LESS_THAN_OR_EQUAL_TO, "price", EppoValue.valueOf(20));

    addConditionToRule(TargetingRule, condition1);
    addConditionToRule(TargetingRule, condition2);
  }

  public void addSemVerConditionToRule(TargetingRule TargetingRule) {
    TargetingCondition condition1 =
        new TargetingCondition(
            OperatorType.GREATER_THAN_OR_EQUAL_TO, "appVersion", EppoValue.valueOf("1.5.0"));
    TargetingCondition condition2 =
        new TargetingCondition(OperatorType.LESS_THAN, "appVersion", EppoValue.valueOf("2.2.0"));

    addConditionToRule(TargetingRule, condition1);
    addConditionToRule(TargetingRule, condition2);
  }

  public void addRegexConditionToRule(TargetingRule TargetingRule) {
    TargetingCondition condition =
        new TargetingCondition(
            OperatorType.MATCHES, "match", EppoValue.valueOf("example\\.(com|org)"));
    addConditionToRule(TargetingRule, condition);
  }

  public void addOneOfConditionWithStrings(TargetingRule rule) {
    List<String> values = Arrays.asList("value1", "value2");
    TargetingCondition condition =
        new TargetingCondition(OperatorType.ONE_OF, "oneOf", EppoValue.valueOf(values));
    addConditionToRule(rule, condition);
  }

  public void addOneOfConditionWithIntegers(TargetingRule rule) {
    List<String> values = Arrays.asList("1", "2");
    TargetingCondition condition =
        new TargetingCondition(OperatorType.ONE_OF, "oneOf", EppoValue.valueOf(values));
    addConditionToRule(rule, condition);
  }

  public void addOneOfConditionWithDoubles(TargetingRule rule) {
    List<String> values = Arrays.asList("1.5", "2.7");
    TargetingCondition condition =
        new TargetingCondition(OperatorType.ONE_OF, "oneOf", EppoValue.valueOf(values));
    addConditionToRule(rule, condition);
  }

  public void addOneOfConditionWithBoolean(TargetingRule rule) {
    List<String> values = Collections.singletonList("true");
    TargetingCondition condition =
        new TargetingCondition(OperatorType.ONE_OF, "oneOf", EppoValue.valueOf(values));
    addConditionToRule(rule, condition);
  }

  public void addNotOneOfTargetingCondition(TargetingRule TargetingRule) {
    List<String> values = Arrays.asList("value1", "value2");
    TargetingCondition condition =
        new TargetingCondition(OperatorType.NOT_ONE_OF, "oneOf", EppoValue.valueOf(values));
    addConditionToRule(TargetingRule, condition);
  }

  public void addNameToSubjectAttribute(SubjectAttributes subjectAttributes) {
    subjectAttributes.put("name", "test");
  }

  public void addPriceToSubjectAttribute(SubjectAttributes subjectAttributes) {
    subjectAttributes.put("price", "30");
  }

  @Test
  public void testMatchesAnyRuleWithEmptyConditions() {
    Set<TargetingRule> targetingRules = new HashSet<>();
    final TargetingRule targetingRuleWithEmptyConditions = createRule(new HashSet<>());
    targetingRules.add(targetingRuleWithEmptyConditions);
    SubjectAttributes subjectAttributes = new SubjectAttributes();
    addNameToSubjectAttribute(subjectAttributes);

    assertEquals(
        targetingRuleWithEmptyConditions,
        RuleEvaluator.findMatchingRule(subjectAttributes, targetingRules, false));
  }

  @Test
  public void testMatchesAnyRuleWithEmptyRules() {
    Set<TargetingRule> targetingRules = new HashSet<>();
    SubjectAttributes subjectAttributes = new SubjectAttributes();
    addNameToSubjectAttribute(subjectAttributes);

    assertNull(RuleEvaluator.findMatchingRule(subjectAttributes, targetingRules, false));
  }

  @Test
  public void testMatchesAnyRuleWhenNoRuleMatches() {
    Set<TargetingRule> targetingRules = new HashSet<>();
    TargetingRule targetingRule = createRule(new HashSet<>());
    addNumericConditionToRule(targetingRule);
    targetingRules.add(targetingRule);

    SubjectAttributes subjectAttributes = new SubjectAttributes();
    addPriceToSubjectAttribute(subjectAttributes);

    assertNull(RuleEvaluator.findMatchingRule(subjectAttributes, targetingRules, false));
  }

  @Test
  public void testMatchesAnyRuleWhenRuleMatches() {
    Set<TargetingRule> targetingRules = new HashSet<>();
    TargetingRule targetingRule = createRule(new HashSet<>());
    addNumericConditionToRule(targetingRule);
    targetingRules.add(targetingRule);

    SubjectAttributes subjectAttributes = new SubjectAttributes();
    subjectAttributes.put("price", 15);

    assertEquals(
        targetingRule, RuleEvaluator.findMatchingRule(subjectAttributes, targetingRules, false));
  }

  @Test
  public void testMatchesAnyRuleWhenRuleMatchesWithSemVer() {
    Set<TargetingRule> targetingRules = new HashSet<>();
    TargetingRule targetingRule = createRule(new HashSet<>());
    addSemVerConditionToRule(targetingRule);
    targetingRules.add(targetingRule);

    SubjectAttributes subjectAttributes = new SubjectAttributes();
    subjectAttributes.put("appVersion", "1.15.5");

    assertEquals(
        targetingRule, RuleEvaluator.findMatchingRule(subjectAttributes, targetingRules, false));
  }

  @Test
  public void testMatchesAnyRuleWhenThrowInvalidSubjectAttribute() {
    Set<TargetingRule> targetingRules = new HashSet<>();
    TargetingRule targetingRule = createRule(new HashSet<>());
    addNumericConditionToRule(targetingRule);
    targetingRules.add(targetingRule);

    SubjectAttributes subjectAttributes = new SubjectAttributes();
    subjectAttributes.put("price", EppoValue.valueOf("abcd"));

    assertNull(RuleEvaluator.findMatchingRule(subjectAttributes, targetingRules, false));
  }

  @Test
  public void testMatchesAnyRuleWithRegexCondition() {
    Set<TargetingRule> targetingRules = new HashSet<>();
    TargetingRule targetingRule = createRule(new HashSet<>());
    addRegexConditionToRule(targetingRule);
    targetingRules.add(targetingRule);

    SubjectAttributes subjectAttributes = new SubjectAttributes();
    subjectAttributes.put("match", EppoValue.valueOf("test@example.com"));

    assertEquals(
        targetingRule, RuleEvaluator.findMatchingRule(subjectAttributes, targetingRules, false));
  }

  @Test
  public void testMatchesAnyRuleWithRegexConditionNotMatched() {
    Set<TargetingRule> targetingRules = new HashSet<>();
    TargetingRule targetingRule = createRule(new HashSet<>());
    addRegexConditionToRule(targetingRule);
    targetingRules.add(targetingRule);

    SubjectAttributes subjectAttributes = new SubjectAttributes();
    subjectAttributes.put("match", EppoValue.valueOf("123"));

    assertNull(RuleEvaluator.findMatchingRule(subjectAttributes, targetingRules, false));
  }

  @Test
  public void testMatchesAnyRuleWithNotOneOfRule() {
    Set<TargetingRule> targetingRules = new HashSet<>();
    TargetingRule targetingRule = createRule(new HashSet<>());
    addNotOneOfTargetingCondition(targetingRule);
    targetingRules.add(targetingRule);

    SubjectAttributes subjectAttributes = new SubjectAttributes();
    subjectAttributes.put("oneOf", EppoValue.valueOf("value3"));

    assertEquals(
        targetingRule, RuleEvaluator.findMatchingRule(subjectAttributes, targetingRules, false));
  }

  @Test
  public void testMatchesAnyRuleWithNotOneOfRuleNotPassed() {
    Set<TargetingRule> targetingRules = new HashSet<>();
    TargetingRule targetingRule = createRule(new HashSet<>());
    addNotOneOfTargetingCondition(targetingRule);
    targetingRules.add(targetingRule);

    SubjectAttributes subjectAttributes = new SubjectAttributes();
    subjectAttributes.put("oneOf", EppoValue.valueOf("value1"));

    assertNull(RuleEvaluator.findMatchingRule(subjectAttributes, targetingRules, false));
  }

  @Test
  public void testMatchesAnyRuleWithOneOfRuleOnString() {
    Set<TargetingRule> targetingRules = new HashSet<>();
    TargetingRule targetingRule = createRule(new HashSet<>());
    addOneOfConditionWithStrings(targetingRule);
    targetingRules.add(targetingRule);

    SubjectAttributes subjectAttributes = new SubjectAttributes();
    subjectAttributes.put("oneOf", EppoValue.valueOf("value1"));

    assertEquals(
        targetingRule, RuleEvaluator.findMatchingRule(subjectAttributes, targetingRules, false));
  }

  @Test
  public void testMatchesAnyRuleWithOneOfRuleOnInteger() {
    Set<TargetingRule> targetingRules = new HashSet<>();
    TargetingRule targetingRule = createRule(new HashSet<>());
    addOneOfConditionWithIntegers(targetingRule);
    targetingRules.add(targetingRule);

    SubjectAttributes subjectAttributes = new SubjectAttributes();
    subjectAttributes.put("oneOf", EppoValue.valueOf(2));

    assertEquals(
        targetingRule, RuleEvaluator.findMatchingRule(subjectAttributes, targetingRules, false));
  }

  @Test
  public void testMatchesAnyRuleWithOneOfRuleOnDouble() {
    Set<TargetingRule> targetingRules = new HashSet<>();
    TargetingRule targetingRule = createRule(new HashSet<>());
    addOneOfConditionWithDoubles(targetingRule);
    targetingRules.add(targetingRule);

    SubjectAttributes subjectAttributes = new SubjectAttributes();
    subjectAttributes.put("oneOf", EppoValue.valueOf(1.5));

    assertEquals(
        targetingRule, RuleEvaluator.findMatchingRule(subjectAttributes, targetingRules, false));
  }

  @Test
  public void testMatchesAnyRuleWithOneOfRuleOnBoolean() {
    Set<TargetingRule> targetingRules = new HashSet<>();
    TargetingRule targetingRule = createRule(new HashSet<>());
    addOneOfConditionWithBoolean(targetingRule);
    targetingRules.add(targetingRule);

    SubjectAttributes subjectAttributes = new SubjectAttributes();
    subjectAttributes.put("oneOf", EppoValue.valueOf(true));

    assertEquals(
        targetingRule, RuleEvaluator.findMatchingRule(subjectAttributes, targetingRules, false));
  }
}
