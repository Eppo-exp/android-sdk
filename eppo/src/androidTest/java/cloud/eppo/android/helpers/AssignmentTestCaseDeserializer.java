package cloud.eppo.android.helpers;

import cloud.eppo.ufc.dto.EppoValue;
import cloud.eppo.ufc.dto.SubjectAttributes;
import cloud.eppo.ufc.dto.VariationType;
import cloud.eppo.ufc.dto.adapters.EppoValueDeserializer;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

public class AssignmentTestCaseDeserializer extends StdDeserializer<AssignmentTestCase> {
  private final EppoValueDeserializer eppoValueDeserializer = new EppoValueDeserializer();

  public AssignmentTestCaseDeserializer() {
    super(AssignmentTestCase.class);
  }

  @Override
  public AssignmentTestCase deserialize(JsonParser parser, DeserializationContext ctxt)
      throws IOException {
    JsonNode rootNode = parser.getCodec().readTree(parser);
    String flag = rootNode.get("flag").asText();
    try {
      VariationType variationType =
          VariationType.fromString(rootNode.get("variationType").asText());
      TestCaseValue defaultValue = deserializeTestCaseValue(rootNode.get("defaultValue"));
      List<SubjectAssignment> subjects = deserializeSubjectAssignments(rootNode.get("subjects"));
      return new AssignmentTestCase(flag, variationType, defaultValue, subjects);
    } catch (JSONException e) {
      throw new IOException(e);
    }
  }

  private List<SubjectAssignment> deserializeSubjectAssignments(JsonNode jsonNode)
      throws JSONException {
    List<SubjectAssignment> subjectAssignments = new ArrayList<>();
    if (jsonNode != null && jsonNode.isArray()) {
      for (JsonNode subjectAssignmentNode : jsonNode) {
        String subjectKey = subjectAssignmentNode.get("subjectKey").asText();

        SubjectAttributes subjectAttributes = new SubjectAttributes();
        JsonNode attributesNode = subjectAssignmentNode.get("subjectAttributes");
        if (attributesNode != null && attributesNode.isObject()) {
          for (Iterator<Map.Entry<String, JsonNode>> it = attributesNode.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            String attributeName = entry.getKey();
            EppoValue attributeValue = eppoValueDeserializer.deserializeNode(entry.getValue());
            subjectAttributes.put(attributeName, attributeValue);
          }
        }

        TestCaseValue assignment =
            deserializeTestCaseValue(subjectAssignmentNode.get("assignment"));

        subjectAssignments.add(new SubjectAssignment(subjectKey, subjectAttributes, assignment));
      }
    }

    return subjectAssignments;
  }

  private TestCaseValue deserializeTestCaseValue(JsonNode jsonNode) throws JSONException {
    if (jsonNode != null && jsonNode.isObject()) {
      return TestCaseValue.valueOf(new JSONObject(jsonNode.toString()));
    } else {
      return TestCaseValue.copyOf(eppoValueDeserializer.deserializeNode(jsonNode));
    }
  }
}
