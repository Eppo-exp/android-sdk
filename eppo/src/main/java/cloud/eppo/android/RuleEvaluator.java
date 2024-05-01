package cloud.eppo.android;

import com.github.zafarkhaja.semver.Version;

import java.util.Set;
import java.util.regex.Pattern;

import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.OperatorType;
import cloud.eppo.android.dto.SubjectAttributes;
import cloud.eppo.android.dto.TargetingCondition;
import cloud.eppo.android.dto.TargetingRule;

public class RuleEvaluator {

    public static TargetingRule findMatchingRule(SubjectAttributes subjectAttributes, Set<TargetingRule> rules) {
        for (TargetingRule rule : rules) {
            if (allConditionsMatch(subjectAttributes, rule.getConditions())) {
                return rule;
            }
        }
        return null;
    }

    private static boolean allConditionsMatch(SubjectAttributes subjectAttributes, Set<TargetingCondition> conditions) {
        for (TargetingCondition condition : conditions) {
            if (!evaluateCondition(subjectAttributes, condition)) {
                return false;
            }
        }
        return true;
    }

    private static boolean evaluateCondition(SubjectAttributes subjectAttributes, TargetingCondition condition) {
        EppoValue conditionValue = condition.getValue();
        EppoValue attributeValue = subjectAttributes.get(condition.getAttribute());

        // First we do any NULL check
        boolean attributeValueIsNull = attributeValue == null || attributeValue.isNull();
        if (condition.getOperator() == OperatorType.IS_NULL) {
            boolean expectNull = conditionValue.booleanValue();
            return expectNull && attributeValueIsNull || !expectNull && !attributeValueIsNull;
        } else if (attributeValueIsNull) {
            // Any check other than IS NULL should fail if the attribute value is null
            return false;
        }

        boolean numericComparison = attributeValue.isNumeric() && conditionValue.isNumeric();

        // Android API version 21 does not have access to the java.util.Optional class.
        // Version.tryParse returns a Optional<Version> would be ideal.
        // Instead use Version.parse which throws an exception if the string is not a valid SemVer.
        // We front-load the parsing here so many evaluation of gte, gt, lte, lt operations
        // more straight-forward.
        Version valueSemVer = null;
        Version conditionSemVer = null;
        try {
            valueSemVer = Version.parse(attributeValue.stringValue());
            conditionSemVer = Version.parse(condition.getValue().stringValue());
        } catch (Exception e) {
            // no-op
        }

        // Performing this check satisfies the compiler that the possibly
        // null value can be safely accessed later.
        boolean semVerComparison = valueSemVer != null && conditionSemVer != null;

        switch (condition.getOperator()) {
            case GREATER_THAN_OR_EQUAL_TO:
                if (numericComparison) {
                    return attributeValue.doubleValue() >= conditionValue.doubleValue();
                }

                if (semVerComparison) {
                    return valueSemVer.isHigherThanOrEquivalentTo(conditionSemVer);
                }

                return false;
            case GREATER_THAN:
                if (numericComparison) {
                    return attributeValue.doubleValue() > conditionValue.doubleValue();
                }

                if (semVerComparison) {
                    return valueSemVer.isHigherThan(conditionSemVer);
                }

                return false;
            case LESS_THAN_OR_EQUAL_TO:
                if (numericComparison) {
                    return attributeValue.doubleValue() <= conditionValue.doubleValue();
                }

                if (semVerComparison) {
                    return valueSemVer.isLowerThanOrEquivalentTo(conditionSemVer);
                }

                return false;
            case LESS_THAN:
                if (numericComparison) {
                    return attributeValue.doubleValue() < conditionValue.doubleValue();
                }

                if (semVerComparison) {
                    return valueSemVer.isLowerThan(conditionSemVer);
                }

                return false;
            case MATCHES:
                return Pattern.compile(condition.getValue().stringValue()).matcher(attributeValue.stringValue()).matches();
            case ONE_OF:
                return conditionValue.stringArrayValue().contains(attributeValue.stringValue());
            case NOT_ONE_OF:
                return !conditionValue.stringArrayValue().contains(attributeValue.stringValue());
            default:
                throw new IllegalStateException("Unexpected value: " + condition.getOperator());
        }
    }
}