package cloud.eppo.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import cloud.eppo.ufc.dto.EppoValue;
import cloud.eppo.ufc.dto.OperatorType;
import cloud.eppo.ufc.dto.SubjectAttributes;
import cloud.eppo.ufc.dto.TargetingCondition;
import cloud.eppo.ufc.dto.TargetingRule;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class RuleEvaluatorTest {

  public TargetingRule createRule(Set<TargetingCondition> conditions) {
    final TargetingRule targetingRule = new TargetingRule();
    targetingRule.setConditions(conditions);
    return targetingRule;
  }

  public void addConditionToRule(TargetingRule TargetingRule, TargetingCondition condition) {
    TargetingRule.getConditions().add(condition);
  }

  public void addNumericConditionToRule(TargetingRule TargetingRule) {
    TargetingCondition condition1 = new TargetingCondition();
    condition1.setValue(EppoValue.valueOf(10));
    condition1.setAttribute("price");
    condition1.setOperator(OperatorType.GREATER_THAN_OR_EQUAL_TO);

    TargetingCondition condition2 = new TargetingCondition();
    condition2.setValue(EppoValue.valueOf(20));
    condition2.setAttribute("price");
    condition2.setOperator(OperatorType.LESS_THAN_OR_EQUAL_TO);

    addConditionToRule(TargetingRule, condition1);
    addConditionToRule(TargetingRule, condition2);
  }

  public void addSemVerConditionToRule(TargetingRule TargetingRule) {
    TargetingCondition condition1 = new TargetingCondition();
    condition1.setValue(EppoValue.valueOf("1.5.0"));
    condition1.setAttribute("appVersion");
    condition1.setOperator(OperatorType.GREATER_THAN_OR_EQUAL_TO);

    TargetingCondition condition2 = new TargetingCondition();
    condition2.setValue(EppoValue.valueOf("2.2.0"));
    condition2.setAttribute("appVersion");
    condition2.setOperator(OperatorType.LESS_THAN);

    addConditionToRule(TargetingRule, condition1);
    addConditionToRule(TargetingRule, condition2);
  }

  public void addRegexConditionToRule(TargetingRule TargetingRule) {
    TargetingCondition condition = new TargetingCondition();
    condition.setValue(EppoValue.valueOf("example\\.(com|org)"));
    condition.setAttribute("match");
    condition.setOperator(OperatorType.MATCHES);

    addConditionToRule(TargetingRule, condition);
  }

  public void addOneOfConditionWithStrings(TargetingRule rule) {
    TargetingCondition condition = new TargetingCondition();
    List<String> values = new ArrayList<>();
    values.add("value1");
    values.add("value2");

    condition.setValue(EppoValue.valueOf(values));
    condition.setAttribute("oneOf");
    condition.setOperator(OperatorType.ONE_OF);

    addConditionToRule(rule, condition);
  }

  public void addOneOfConditionWithIntegers(TargetingRule rule) {
    TargetingCondition condition = new TargetingCondition();
    List<String> values = new ArrayList<>();
    values.add("1");
    values.add("2");

    condition.setValue(EppoValue.valueOf(values));
    condition.setAttribute("oneOf");
    condition.setOperator(OperatorType.ONE_OF);

    addConditionToRule(rule, condition);
  }

  public void addOneOfConditionWithDoubles(TargetingRule rule) {
    TargetingCondition condition = new TargetingCondition();
    List<String> values = new ArrayList<>();
    values.add("1.5");
    values.add("2.7");

    condition.setValue(EppoValue.valueOf(values));
    condition.setAttribute("oneOf");
    condition.setOperator(OperatorType.ONE_OF);

    addConditionToRule(rule, condition);
  }

  public void addOneOfConditionWithBoolean(TargetingRule rule) {
    TargetingCondition condition = new TargetingCondition();
    List<String> values = new ArrayList<>();
    values.add("true");

    condition.setValue(EppoValue.valueOf(values));
    condition.setAttribute("oneOf");
    condition.setOperator(OperatorType.ONE_OF);

    addConditionToRule(rule, condition);
  }

  public void addNotOneOfTargetingCondition(TargetingRule TargetingRule) {
    TargetingCondition condition = new TargetingCondition();
    List<String> values = new ArrayList<>();
    values.add("value1");
    values.add("value2");
    condition.setValue(EppoValue.valueOf(values));
    condition.setAttribute("oneOf");
    condition.setOperator(OperatorType.NOT_ONE_OF);

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
