package cloud.eppo.androidexample;

import static cloud.eppo.androidexample.Constants.INITIAL_FLAG_KEY;
import static cloud.eppo.androidexample.Constants.INITIAL_SUBJECT_ID;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.geteppo.androidexample.R;

import cloud.eppo.android.EppoClient;
import cloud.eppo.ufc.dto.SubjectAttributes;

public class SecondActivity extends AppCompatActivity {
    private EditText experiment;
    private EditText subject;
    private TextView assignmentLog;
    private ScrollView assignmentLogScrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_assigner);

        experiment = findViewById(R.id.experiment);
        subject = findViewById(R.id.subject);
        assignmentLog = findViewById(R.id.assignment_log);
        assignmentLogScrollView = findViewById(R.id.assignment_log_scrollview);

        experiment.setText(INITIAL_FLAG_KEY);
        subject.setText(INITIAL_SUBJECT_ID);

        findViewById(R.id.btn_assign).setOnClickListener(view -> handleAssignment());
    }

    private void handleAssignment() {
        String subjectId = subject.getText().toString();
        if (TextUtils.isEmpty(subjectId)) {
            appendToAssignmentLogView("Subject must not be empty");
            return;
        }

        String experimentKey = experiment.getText().toString();
        if (TextUtils.isEmpty(experimentKey)) {
            appendToAssignmentLogView("Experiment key must not be empty");
            return;
        }

        String assignedVariation = EppoClient.getInstance().getStringAssignment(
            experimentKey, subjectId, new SubjectAttributes(), "");
        appendToAssignmentLogView("Assigned variation: " + assignedVariation);
    }

    private void appendToAssignmentLogView(String message) {
        assignmentLog.append(message + "\n\n");
        assignmentLogScrollView.post(() -> assignmentLogScrollView.fullScroll(View.FOCUS_DOWN));
    }
}
