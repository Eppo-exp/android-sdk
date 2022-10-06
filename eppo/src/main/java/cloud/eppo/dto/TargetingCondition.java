package cloud.eppo.dto;

public class TargetingCondition {
    private String operator;
    private String attribute;
    private EppoValue value;

    public OperatorType getOperator() {
        return OperatorType.fromString(operator);
    }

    public String getAttribute() {
        return attribute;
    }

    public EppoValue getValue() {
        return value;
    }
}

