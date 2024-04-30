package cloud.eppo.android;

import com.github.zafarkhaja.semver.Version;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.SubjectAttributes;
import cloud.eppo.android.dto.TargetingCondition;
import cloud.eppo.android.dto.TargetingRule;

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
            if (value == null) {
                return false;
            }

            boolean numericComparison = value.isNumeric() && condition.getValue().isNumeric();

            // Android API version 21 does not have access to the java.util.Optional class.
            // Version.tryParse returns a Optional<Version> would be ideal.
            // Instead use Version.parse which throws an exception if the string is not a valid SemVer.
            // We front-load the parsing here so many evaluation of gte, gt, lte, lt operations
            // more straight-forward.
            Version valueSemVer = null;
            Version conditionSemVer = null;
            try {
                valueSemVer =  Version.parse(value.stringValue());
                conditionSemVer = Version.parse(condition.getValue().stringValue());
            } catch (Exception e) {
                // no-op
            }

            // Performing this check satisfies the compiler that the possibly
            // null value can be safely accessed later.
            boolean semVerComparison = valueSemVer != null && conditionSemVer != null;

            try {
                switch (condition.getOperator()) {
                    case GreaterThanEqualTo:
                        if (numericComparison) {
                            return value.doubleValue() >= condition.getValue().doubleValue();
                        }

                        if (semVerComparison) {
                            return valueSemVer.isHigherThanOrEquivalentTo(conditionSemVer);
                        }

                        return false;
                    case GreaterThan:
                        if (numericComparison) {
                            return value.doubleValue() > condition.getValue().doubleValue();
                        }

                        if (semVerComparison) {
                            return valueSemVer.isHigherThan(conditionSemVer);
                        }

                        return false;
                    case LessThanEqualTo:
                        if (numericComparison) {
                            return value.doubleValue() <= condition.getValue().doubleValue();
                        }

                        if (semVerComparison) {
                            return valueSemVer.isLowerThanOrEquivalentTo(conditionSemVer);
                        }

                        return false;
                    case LessThan:
                        if (numericComparison) {
                            return value.doubleValue() < condition.getValue().doubleValue();
                        }

                        if (semVerComparison) {
                            return valueSemVer.isLowerThan(conditionSemVer);
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

    private static List<Boolean> evaluateRuleConditions(SubjectAttributes subjectAttributes, Set<TargetingCondition> conditions) {
        List<Boolean> evaluations = new ArrayList<>();
        for (TargetingCondition condition : conditions) {
            evaluations.add(evaluateCondition(subjectAttributes, condition));
        }
        return evaluations;
    }
}