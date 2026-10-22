package cloud.eppo.android.helpers;

import cloud.eppo.api.dto.VariationType;
import java.util.List;

public class AssignmentTestCase {
  private final String flag;
  private final VariationType variationType;
  private final TestCaseValue defaultValue;
  private final List<SubjectAssignment> subjects;

  public AssignmentTestCase(
      String flag,
      VariationType variationType,
      TestCaseValue defaultValue,
      List<SubjectAssignment> subjects) {
    this.flag = flag;
    this.variationType = variationType;
    this.defaultValue = defaultValue;
    this.subjects = subjects;
  }

  public String getFlag() {
    return flag;
  }

  public VariationType getVariationType() {
    return variationType;
  }

  public TestCaseValue getDefaultValue() {
    return defaultValue;
  }

  public List<SubjectAssignment> getSubjects() {
    return subjects;
  }
}
