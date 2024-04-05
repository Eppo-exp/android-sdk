package cloud.eppo.android.dto;

public class TargetingCondition {
    @SerializedName("operator")
    private String operator;

    @SerializedName("attribute")
    private String attribute;

    @SerializedName("value")
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

    public void setOperator(OperatorType operatorType) {
        this.operator = operatorType.value;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    public void setValue(EppoValue value) {
        this.value = value;
    }
}
