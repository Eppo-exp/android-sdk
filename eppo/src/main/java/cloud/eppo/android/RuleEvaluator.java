package cloud.eppo.android;

import com.github.zafarkhaja.semver.Version;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.SubjectAttributes;
import cloud.eppo.android.dto.TargetingCondition;
import cloud.eppo.android.dto.TargetingRule;

interface IConditionFunc<T> {
    boolean check(T a, T b);
}

class Compare {
    public static boolean compareRegex(String a, Pattern pattern) {
        return pattern.matcher(a).matches();
    }

    public static boolean isOneOf(String a, List<String> values) {
        for (String value : values) {
            if (value.equalsIgnoreCase(a)) {
                return true;
            }
        }
        return false;
    }
}

public class RuleEvaluator {
    public static TargetingRule findMatchingRule(SubjectAttributes subjectAttributes, List<TargetingRule> rules) {
        for (TargetingRule rule : rules) {
            if (matchesRule(subjectAttributes, rule)) {
                return rule;
            }
        }
        return null;
    }

    private static boolean matchesRule(SubjectAttributes subjectAttributes, TargetingRule rule) {
        List<Boolean> conditionEvaluations = evaluateRuleConditions(subjectAttributes, rule.getConditions());
        return !conditionEvaluations.contains(false);
    }



    private static boolean evaluateCondition(SubjectAttributes subjectAttributes, TargetingCondition condition
    ) {
        if (subjectAttributes.containsKey(condition.getAttribute())) {
            EppoValue value = subjectAttributes.get(condition.getAttribute());
            Boolean isValueSemVer = Version.isValid(value.stringValue());
            Boolean isConditionSemVer = Version.isValid(condition.getValue().stringValue());

            try {
                switch (condition.getOperator()) {
                    case GreaterThanEqualTo:
                        if (value.isNumeric() && condition.getValue().isNumeric()) {
                            return value.doubleValue() >= condition.getValue().doubleValue();
                        }

                        if (isValueSemVer && isConditionSemVer) {
                            return Version.parse(value.stringValue()).isHigherThanOrEquivalentTo(Version.parse(condition.getValue().stringValue()));
                        }

                        return false;
                    case GreaterThan:
                        if (value.isNumeric() && condition.getValue().isNumeric()) {
                            return value.doubleValue() > condition.getValue().doubleValue();
                        }

                        if (isValueSemVer && isConditionSemVer) {
                            return Version.parse(value.stringValue()).isHigherThan(Version.parse(condition.getValue().stringValue()));
                        }

                        return false;
                    case LessThanEqualTo:
                        if (value.isNumeric() && condition.getValue().isNumeric()) {
                            return value.doubleValue() <= condition.getValue().doubleValue();
                        }

                        if (isValueSemVer && isConditionSemVer) {
                            return Version.parse(value.stringValue()).isLowerThanOrEquivalentTo(Version.parse(condition.getValue().stringValue()));
                        }

                        return false;
                    case LessThan:
                        if (value.isNumeric() && condition.getValue().isNumeric()) {
                            return value.doubleValue() < condition.getValue().doubleValue();
                        }

                        if (isValueSemVer && isConditionSemVer) {
                            return Version.parse(value.stringValue()).isLowerThan(Version.parse(condition.getValue().stringValue()));
                        }

                        return false;
                    case Matches:
                        return Compare.compareRegex(value.stringValue(), Pattern.compile(condition.getValue().stringValue()));
                    case OneOf:
                        return Compare.isOneOf(value.stringValue(), condition.getValue().arrayValue());
                    case NotOneOf:
                        return !Compare.isOneOf(value.stringValue(), condition.getValue().arrayValue());
                    default:
                        throw new IllegalStateException("Unexpected value: " + condition.getOperator());
                }
            } catch (Exception e) {
                return false;
            }

        }
        return false;
    }

    private static List<Boolean> evaluateRuleConditions(SubjectAttributes subjectAttributes, List<TargetingCondition> conditions) {
        List<Boolean> evaluations = new ArrayList<>();
        for (TargetingCondition condition : conditions) {
            evaluations.add(evaluateCondition(subjectAttributes, condition));
        }
        return evaluations;
    }
}