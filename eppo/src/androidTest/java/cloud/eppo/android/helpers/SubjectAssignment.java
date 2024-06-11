package cloud.eppo.android.helpers;

import cloud.eppo.ufc.dto.SubjectAttributes;

public class SubjectAssignment {
  private final String subjectKey;
  private final SubjectAttributes subjectAttributes;
  private final TestCaseValue assignment;

  public SubjectAssignment(
      String subjectKey, SubjectAttributes subjectAttributes, TestCaseValue assignment) {
    this.subjectKey = subjectKey;
    this.subjectAttributes = subjectAttributes;
    this.assignment = assignment;
  }

  public String getSubjectKey() {
    return subjectKey;
  }

  public SubjectAttributes getSubjectAttributes() {
    return subjectAttributes;
  }

  public TestCaseValue getAssignment() {
    return assignment;
  }
}
