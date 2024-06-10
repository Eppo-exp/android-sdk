package cloud.eppo.android.helpers;

import java.util.List;

import cloud.eppo.ufc.dto.VariationType;

public class AssignmentTestCase {
    String flag;
    VariationType variationType;
    TestCaseValue defaultValue;
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

    public TestCaseValue getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(TestCaseValue defaultValue) {
        this.defaultValue = defaultValue;
    }

    public List<SubjectAssignment> getSubjects() {
        return subjects;
    }

    public void setSubjects(List<SubjectAssignment> subjects) {
        this.subjects = subjects;
    }
}

