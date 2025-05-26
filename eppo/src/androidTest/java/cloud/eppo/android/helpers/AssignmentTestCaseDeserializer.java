package cloud.eppo.android.helpers;

import android.util.Log;
import cloud.eppo.android.adapters.EppoValueDeserializer;
import cloud.eppo.android.util.AndroidUtils;
import cloud.eppo.api.Attributes;
import cloud.eppo.api.EppoValue;
import cloud.eppo.ufc.dto.VariationType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class AssignmentTestCaseDeserializer {
  private static final String TAG = AndroidUtils.logTag(AssignmentTestCaseDeserializer.class);
  private final EppoValueDeserializer eppoValueDeserializer = new EppoValueDeserializer();

  public AssignmentTestCase deserialize(String jsonString) throws JSONException {
    JSONObject rootNode = new JSONObject(jsonString);

    String flag = rootNode.getString("flag");
    VariationType variationType = VariationType.fromString(rootNode.getString("variationType"));
    TestCaseValue defaultValue = deserializeTestCaseValue(rootNode.get("defaultValue"));
    List<SubjectAssignment> subjects =
        deserializeSubjectAssignments(rootNode.getJSONArray("subjects"));

    return new AssignmentTestCase(flag, variationType, defaultValue, subjects);
  }

  private List<SubjectAssignment> deserializeSubjectAssignments(JSONArray jsonArray) {
    List<SubjectAssignment> subjectAssignments = new ArrayList<>();
    if (jsonArray == null) {
      return subjectAssignments;
    }

    for (int i = 0; i < jsonArray.length(); i++) {
      try {
        JSONObject subjectAssignmentNode = jsonArray.getJSONObject(i);
        String subjectKey = subjectAssignmentNode.getString("subjectKey");

        Attributes attributes = new Attributes();
        if (subjectAssignmentNode.has("subjectAttributes")) {
          JSONObject attributesNode = subjectAssignmentNode.getJSONObject("subjectAttributes");
          Iterator<String> keys = attributesNode.keys();

          while (keys.hasNext()) {
            String attributeName = keys.next();
            EppoValue attributeValue =
                eppoValueDeserializer.deserialize(attributesNode.opt(attributeName));
            attributes.put(attributeName, attributeValue);
          }
        }

        TestCaseValue assignment =
            deserializeTestCaseValue(subjectAssignmentNode.get("assignment"));
        subjectAssignments.add(new SubjectAssignment(subjectKey, attributes, assignment));
      } catch (JSONException e) {
        Log.w(TAG, "Error deserializing subject assignment at index " + i, e);
      }
    }

    return subjectAssignments;
  }

  private TestCaseValue deserializeTestCaseValue(Object jsonValue) throws JSONException {
    if (jsonValue instanceof JSONObject) {
      return TestCaseValue.valueOf((JSONObject) jsonValue);
    } else {
      return TestCaseValue.copyOf(eppoValueDeserializer.deserialize(jsonValue));
    }
  }
}
