package cloud.eppo.android.helpers;

import cloud.eppo.api.Attributes;

public class SubjectAssignment {
  private final String subjectKey;
  private final Attributes attributes;
  private final TestCaseValue assignment;

  public SubjectAssignment(String subjectKey, Attributes attributes, TestCaseValue assignment) {
    this.subjectKey = subjectKey;
    this.attributes = attributes;
    this.assignment = assignment;
  }

  public String getSubjectKey() {
    return subjectKey;
  }

  public Attributes getAttributes() {
    return attributes;
  }

  public TestCaseValue getAssignment() {
    return assignment;
  }
}
