package cloud.eppo.android.helpers;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.SubjectAttributes;
import cloud.eppo.android.dto.VariationType;
import cloud.eppo.android.dto.deserializers.EppoValueDeserializer;

public class AssignmentTestCaseDeserializer implements JsonDeserializer<AssignmentTestCase> {

    private final EppoValueDeserializer eppoValueDeserializer = new EppoValueDeserializer();

    @Override
    public AssignmentTestCase deserialize(JsonElement rootElement, Type type, JsonDeserializationContext context) throws JsonParseException {
        JsonObject testCaseObject = rootElement.getAsJsonObject();
        String flag = testCaseObject.get("flag").getAsString();
        VariationType variationType = VariationType.fromString(testCaseObject.get("variationType").getAsString());
        EppoValue defaultValue = eppoValueDeserializer.deserialize(testCaseObject.get("defaultValue"), type, context);
        List<SubjectAssignment> subjects = deserializeSubjectAssignments(testCaseObject.get("subjects"), type, context);

        AssignmentTestCase assignmentTestCase = new AssignmentTestCase();
        assignmentTestCase.setFlag(flag);
        assignmentTestCase.setVariationType(variationType);
        assignmentTestCase.setDefaultValue(defaultValue);
        assignmentTestCase.setSubjects(subjects);
        return assignmentTestCase;
    }

    private List<SubjectAssignment> deserializeSubjectAssignments(JsonElement jsonElement, Type type, JsonDeserializationContext context) {
        List<SubjectAssignment> subjectAssignments = new ArrayList<>();
        for (JsonElement subjectAssignmentElement : jsonElement.getAsJsonArray()) {
            JsonObject subjectAssignmentObject = subjectAssignmentElement.getAsJsonObject();

            String subjectKey = subjectAssignmentObject.get("subjectKey").getAsString();

            SubjectAttributes subjectAttributes = new SubjectAttributes();
            for (Map.Entry<String, JsonElement> attributeEntry : subjectAssignmentObject.get("subjectAttributes").getAsJsonObject().entrySet()) {
                String attributeName = attributeEntry.getKey();
                EppoValue attributeValue = eppoValueDeserializer.deserialize(attributeEntry.getValue(), type, context);
                subjectAttributes.put(attributeName, attributeValue);
            }

            EppoValue assignment = eppoValueDeserializer.deserialize(subjectAssignmentObject.get("assignment"), type, context);

            SubjectAssignment subjectAssignment = new SubjectAssignment();
            subjectAssignment.setSubjectKey(subjectKey);
            subjectAssignment.setSubjectAttributes(subjectAttributes);
            subjectAssignment.setAssignment(assignment);
            subjectAssignments.add(subjectAssignment);
        }

        return subjectAssignments;
    }
}

