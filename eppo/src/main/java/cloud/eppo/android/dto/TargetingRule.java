package cloud.eppo.android.dto;

import java.util.List;

public class TargetingRule {
    private String allocationKey;
    private List<TargetingCondition> conditions;

    public String getAllocationKey() {
        return allocationKey;
    }

    public List<TargetingCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<TargetingCondition> conditions) {
        this.conditions = conditions;
    }
}
