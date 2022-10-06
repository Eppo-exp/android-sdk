package cloud.eppo;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import cloud.eppo.dto.EppoValue;
import cloud.eppo.dto.SubjectAttributes;
import cloud.eppo.dto.TargetingCondition;
import cloud.eppo.dto.TargetingRule;

interface IConditionFunc<T> {
    boolean check(T a, T b);
}

class Compare {
    public static boolean compareNumber(long a, long b, @NonNull IConditionFunc<Long> conditionFunc) {
        return conditionFunc.check(a, b);
    }

    public static boolean compareRegex(String a, @NonNull Pattern pattern) {
        return pattern.matcher(a).matches();
    }

    public static boolean isOneOf(@NonNull String a, @NonNull List<String> values) {
        return values.stream()
                .map(value -> value.toLowerCase())
                .collect(Collectors.toList())
                .indexOf(a.toLowerCase()) >= 0;
    }
}

public class RuleEvaluator {
    public static TargetingRule findMatchingRule(
            SubjectAttributes subjectAttributes,
            @NonNull List<TargetingRule> rules
    ) {
        for (TargetingRule rule : rules) {
            if (matchesRule(subjectAttributes, rule)) {
                return rule;
            }
        }
        return null;
    }

    private static boolean matchesRule(
            SubjectAttributes subjectAttributes,
            @NonNull TargetingRule rule
    ) {
        List<Boolean> conditionEvaluations = evaluateRuleConditions(subjectAttributes, rule.getConditions());
        return !conditionEvaluations.contains(false);
    }

    private static boolean evaluateCondition(
            @NonNull SubjectAttributes subjectAttributes,
            @NonNull TargetingCondition condition
    ) {
        if (subjectAttributes.containsKey(condition.getAttribute())) {
            EppoValue value = subjectAttributes.get(condition.getAttribute());
            try {
                switch (condition.getOperator()) {
                    case GreaterThanEqualTo:
                        return Compare.compareNumber(value.longValue(), condition.getValue().longValue()
                                , (a, b) -> a >= b);
                    case GreaterThan:
                        return Compare.compareNumber(value.longValue(), condition.getValue().longValue(), (a, b) -> a > b);
                    case LessThanEqualTo:
                        return Compare.compareNumber(value.longValue(), condition.getValue().longValue(), (a, b) -> a <= b);
                    case LessThan:
                        return Compare.compareNumber(value.longValue(), condition.getValue().longValue(), (a, b) -> a < b);
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

    @NonNull
    private static List<Boolean> evaluateRuleConditions(
            SubjectAttributes subjectAttributes,
            @NonNull List<TargetingCondition> conditions
    ) {
        List<Boolean> evaluations = new ArrayList<>();
        for (TargetingCondition condition : conditions) {
            evaluations.add(evaluateCondition(subjectAttributes, condition));
        }
        return evaluations;
    }
}