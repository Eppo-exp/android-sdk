package cloud.eppo.android.helpers;

import cloud.eppo.ufc.dto.EppoValue;
import cloud.eppo.ufc.dto.SubjectAttributes;
import cloud.eppo.ufc.dto.VariationType;
import cloud.eppo.ufc.dto.adapters.EppoValueAdapter;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AssignmentTestCaseDeserializer implements JsonDeserializer<AssignmentTestCase> {

  private final EppoValueAdapter eppoValueDeserializer = new EppoValueAdapter();

  @Override
  public AssignmentTestCase deserialize(
      JsonElement rootElement, Type type, JsonDeserializationContext context)
      throws JsonParseException {
    JsonObject testCaseObject = rootElement.getAsJsonObject();
    String flag = testCaseObject.get("flag").getAsString();
    VariationType variationType =
        VariationType.fromString(testCaseObject.get("variationType").getAsString());
    TestCaseValue defaultValue =
        deserializeTestCaseValue(testCaseObject.get("defaultValue"), type, context);
    List<SubjectAssignment> subjects =
        deserializeSubjectAssignments(testCaseObject.get("subjects"), type, context);

    AssignmentTestCase assignmentTestCase = new AssignmentTestCase();
    assignmentTestCase.setFlag(flag);
    assignmentTestCase.setVariationType(variationType);
    assignmentTestCase.setDefaultValue(defaultValue);
    assignmentTestCase.setSubjects(subjects);
    return assignmentTestCase;
  }

  private List<SubjectAssignment> deserializeSubjectAssignments(
      JsonElement jsonElement, Type type, JsonDeserializationContext context) {
    List<SubjectAssignment> subjectAssignments = new ArrayList<>();
    for (JsonElement subjectAssignmentElement : jsonElement.getAsJsonArray()) {
      JsonObject subjectAssignmentObject = subjectAssignmentElement.getAsJsonObject();

      String subjectKey = subjectAssignmentObject.get("subjectKey").getAsString();

      SubjectAttributes subjectAttributes = new SubjectAttributes();
      for (Map.Entry<String, JsonElement> attributeEntry :
          subjectAssignmentObject.get("subjectAttributes").getAsJsonObject().entrySet()) {
        String attributeName = attributeEntry.getKey();
        EppoValue attributeValue =
            eppoValueDeserializer.deserialize(attributeEntry.getValue(), type, context);
        subjectAttributes.put(attributeName, attributeValue);
      }

      TestCaseValue assignment =
          deserializeTestCaseValue(subjectAssignmentObject.get("assignment"), type, context);

      SubjectAssignment subjectAssignment = new SubjectAssignment();
      subjectAssignment.setSubjectKey(subjectKey);
      subjectAssignment.setSubjectAttributes(subjectAttributes);
      subjectAssignment.setAssignment(assignment);
      subjectAssignments.add(subjectAssignment);
    }

    return subjectAssignments;
  }

  /** Test cases can also have raw JSON (i.e. not encoded into a string) */
  private TestCaseValue deserializeTestCaseValue(
      JsonElement jsonElement, Type type, JsonDeserializationContext context) {
    return jsonElement != null && jsonElement.isJsonObject()
        ? TestCaseValue.valueOf(jsonElement)
        : TestCaseValue.copyOf(eppoValueDeserializer.deserialize(jsonElement, type, context));
  }
}
