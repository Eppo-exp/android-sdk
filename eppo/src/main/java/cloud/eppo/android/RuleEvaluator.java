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
        String attributeKey = condition.getAttribute();
        if (isObfuscated) {
            attributeKey = base64Decode(attributeKey);
        }
        EppoValue attributeValue = subjectAttributes.get(attributeKey);

        // First we do any NULL check
        boolean attributeValueIsNull = attributeValue == null || attributeValue.isNull();
        if (condition.getOperator() == OperatorType.IS_NULL) {
            boolean expectNull = isObfuscated
                    ? getMD5Hex("true").equals(conditionValue.stringValue())
                    : conditionValue.booleanValue();
            return expectNull && attributeValueIsNull || !expectNull && !attributeValueIsNull;
        } else if (attributeValueIsNull) {
            // Any check other than IS NULL should fail if the attribute value is null
            return false;
        }

        if (condition.getOperator().isInequalityComparison()) {
            Double conditionNumber = null;
            if (isObfuscated && conditionValue.isString()) {
                // it may be an encoded number
                try {
                    conditionNumber = Double.parseDouble(base64Decode(conditionValue.stringValue()));
                } catch (Exception e) {
                    // not a number
                }
            } else if (conditionValue.isNumeric()) {
                conditionNumber = conditionValue.doubleValue();
            }

            boolean numericComparison = attributeValue.isNumeric() && conditionNumber != null;

            // Android API version 21 does not have access to the java.util.Optional class.
            // Version.tryParse returns a Optional<Version> would be ideal.
            // Instead use Version.parse which throws an exception if the string is not a valid SemVer.
            // We front-load the parsing here so many evaluation of gte, gt, lte, lt operations
            // more straight-forward.
            Version valueSemVer = null;
            Version conditionSemVer = null;
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
                default:
                    throw new IllegalStateException("Unexpected inequality operator: " + condition.getOperator());
            }
        }

        if (condition.getOperator().isListComparison()) {
            boolean expectMatch = condition.getOperator() == OperatorType.ONE_OF;
            boolean matchFound = false;
            for (String arrayString : conditionValue.stringArrayValue()) {
                String comparisonString = attributeValue.stringValue();
                if (isObfuscated) {
                    // List comparisons use hashes for checking exact match
                    comparisonString = getMD5Hex(comparisonString);
                }
                if (arrayString.equals(comparisonString)) {
                    matchFound = true;
                    break;
                }
            }
            return expectMatch && matchFound || !expectMatch && !matchFound;
        }

        if (condition.getOperator() == OperatorType.MATCHES) {
            // Regexes require decoding
            String patternString = condition.getValue().stringValue();
            if (isObfuscated) {
                patternString = base64Decode(patternString);
            }

            return Pattern.compile(patternString).matcher(attributeValue.stringValue()).matches();
        }

        throw new IllegalStateException("Unexpected rule operator: " + condition.getOperator());
    }
}