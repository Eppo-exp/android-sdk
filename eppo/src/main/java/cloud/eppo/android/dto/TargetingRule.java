package cloud.eppo.android.dto;

import java.util.Set;

public class TargetingRule {

    private Set<TargetingCondition> conditions;

    public Set<TargetingCondition> getConditions() {
        return conditions;
    }

    public void setConditions(Set<TargetingCondition> conditions) {
        this.conditions = conditions;
    }
}
