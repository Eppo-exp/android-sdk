package cloud.eppo.androidexample;

import static cloud.eppo.androidexample.Constants.INITIAL_FLAG_KEY;
import static cloud.eppo.androidexample.Constants.INITIAL_SUBJECT_ID;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import cloud.eppo.android.EppoClient;
import cloud.eppo.api.Attributes;
import com.geteppo.androidexample.BuildConfig;
import com.geteppo.androidexample.R;

public class StandardClientActivity extends AppCompatActivity {
  private static final String TAG = StandardClientActivity.class.getSimpleName();
  private static final String API_KEY = BuildConfig.API_KEY; // Set in root-level local.properties
  private EditText experiment;
  private EditText subject;
  private TextView assignmentLog;
  private ScrollView assignmentLogScrollView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Bundle extras = getIntent().getExtras();
    boolean offlineMode =
        extras != null && extras.getBoolean(this.getPackageName() + ".offlineMode", false);

    new EppoClient.Builder(API_KEY, getApplication())
        .isGracefulMode(
            false) // Note: This is for debugging--stick to default of "true" in production
        .offlineMode(offlineMode)
        .assignmentLogger(
            assignment -> {
              Log.d(
                  TAG,
                  assignment.getExperiment()
                      + "-> subject: "
                      + assignment.getSubject()
                      + " assigned to "
                      + assignment.getExperiment());
            })
        .buildAndInitAsync()
        .thenAccept(
            client -> {
              Log.d(TAG, "Eppo SDK initialized");
            })
        .exceptionally(
            error -> {
              throw new RuntimeException(
                  "Unable to initialize. Ensure you API key is set correctly in EppoApplication.java",
                  error);
            });

    setContentView(R.layout.activity_assigner);

    // Enable the action bar back button
    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setTitle("Standard Client");
    }

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

    String assignedVariation =
        EppoClient.getInstance()
            .getStringAssignment(experimentKey, subjectId, new Attributes(), "");
    appendToAssignmentLogView("Assigned variation: " + assignedVariation);
  }

  private void appendToAssignmentLogView(String message) {
    assignmentLog.append(message + "\n\n");
    assignmentLogScrollView.post(() -> assignmentLogScrollView.fullScroll(View.FOCUS_DOWN));
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  // Tie into the activity's lifecycle and pause/resume polling where appropriate.

  @Override
  public void onPause() {
    super.onPause();
    EppoClient.getInstance().pausePolling();
  }

  @Override
  public void onResume() {
    super.onResume();
    EppoClient.getInstance().resumePolling();
  }
}
