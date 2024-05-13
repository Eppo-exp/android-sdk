package cloud.eppo.android.helpers;

import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.SubjectAttributes;

public class SubjectAssignment {
    String subjectKey;
    SubjectAttributes subjectAttributes;
    TestCaseValue assignment;

    public String getSubjectKey() {
        return subjectKey;
    }

    public void setSubjectKey(String subjectKey) {
        this.subjectKey = subjectKey;
    }

    public SubjectAttributes getSubjectAttributes() {
        return subjectAttributes;
    }

    public void setSubjectAttributes(SubjectAttributes subjectAttributes) {
        this.subjectAttributes = subjectAttributes;
    }

    public TestCaseValue getAssignment() {
        return assignment;
    }

    public void setAssignment(TestCaseValue assignment) {
        this.assignment = assignment;
    }
}
