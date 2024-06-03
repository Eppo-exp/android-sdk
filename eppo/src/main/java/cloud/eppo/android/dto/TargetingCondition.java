package cloud.eppo.android.dto;

public class TargetingCondition {
    private OperatorType operator;

    private String attribute;

    private EppoValue value;

    public OperatorType getOperator() {
        return operator;
    }

    public void setOperator(OperatorType operatorType) {
        this.operator = operatorType;
    }

    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    public EppoValue getValue() {
        return value;
    }

    public void setValue(EppoValue value) {
        this.value = value;
    }
}
