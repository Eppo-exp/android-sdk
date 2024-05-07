package cloud.eppo.android;

import static cloud.eppo.android.util.Utils.base64Decode;
import static cloud.eppo.android.util.Utils.getMD5Hex;

import com.github.zafarkhaja.semver.Version;

import java.util.Set;
import java.util.regex.Pattern;

import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.OperatorType;
import cloud.eppo.android.dto.SubjectAttributes;
import cloud.eppo.android.dto.TargetingCondition;
import cloud.eppo.android.dto.TargetingRule;

public class RuleEvaluator {

    public static TargetingRule findMatchingRule(SubjectAttributes subjectAttributes, Set<TargetingRule> rules, boolean isObfuscated) {
        for (TargetingRule rule : rules) {
            if (allConditionsMatch(subjectAttributes, rule.getConditions(), isObfuscated)) {
                return rule;
            }
        }
        return null;
    }

    private static boolean allConditionsMatch(SubjectAttributes subjectAttributes, Set<TargetingCondition> conditions, boolean isObfuscated) {
        for (TargetingCondition condition : conditions) {
            if (!evaluateCondition(subjectAttributes, condition, isObfuscated)) {
                return false;
            }
        }
        return true;
    }

    private static boolean evaluateCondition(SubjectAttributes subjectAttributes, TargetingCondition condition, boolean isObfuscated) {
        EppoValue conditionValue = condition.getValue();
        EppoValue attributeValue = subjectAttributes.get(condition.getAttribute());

        // First we do any NULL check
        boolean attributeValueIsNull = attributeValue == null || attributeValue.isNull();
        if (condition.getOperator() == OperatorType.IS_NULL) {
            boolean expectNull = isObfuscated
                    ? conditionValue.booleanValue()
                    : getMD5Hex("true").equals(conditionValue.stringValue());
            return expectNull && attributeValueIsNull || !expectNull && !attributeValueIsNull;
        } else if (attributeValueIsNull) {
            // Any check other than IS NULL should fail if the attribute value is null
            return false;
        }

        boolean numericComparison = attributeValue.isNumeric() && conditionValue.isNumeric();
        double conditionNumber;
        if (isObfuscated && conditionValue.isString()) {
            // it may be an encoded number
            try {
                conditionNumber = Double.parseDouble(base64Decode(conditionValue.stringValue()))
            } catch(Exception e) {
                // not a number
            }
        } else if (numericComparison) {
            conditionNumber = conditionValue.doubleValue();
        }

        // Android API version 21 does not have access to the java.util.Optional class.
        // Version.tryParse returns a Optional<Version> would be ideal.
        // Instead use Version.parse which throws an exception if the string is not a valid SemVer.
        // We front-load the parsing here so many evaluation of gte, gt, lte, lt operations
        // more straight-forward.
        Version valueSemVer = null;
        Version conditionSemVer = null;
        numericComparison = false;
        try {
            valueSemVer = Version.parse(attributeValue.stringValue());
            String conditionSemVerString = condition.getValue().stringValue();
            if (isObfuscated) {
                conditionSemVerString = base64Decode(conditionSemVerString);
            }
            conditionSemVer = Version.parse(conditionSemVerString);
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