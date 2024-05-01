package cloud.eppo.android.helpers;

import java.util.List;

import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.VariationType;

public class AssignmentTestCase {
    String flag;
    VariationType variationType;
    EppoValue defaultValue;
    List<SubjectAssignment> subjects;

    public String getFlag() {
        return flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public VariationType getVariationType() {
        return variationType;
    }

    public void setVariationType(VariationType variationType) {
        this.variationType = variationType;
    }

    public EppoValue getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(EppoValue defaultValue) {
        this.defaultValue = defaultValue;
    }

    public List<SubjectAssignment> getSubjects() {
        return subjects;
    }

    public void setSubjects(List<SubjectAssignment> subjects) {
        this.subjects = subjects;
    }
}

