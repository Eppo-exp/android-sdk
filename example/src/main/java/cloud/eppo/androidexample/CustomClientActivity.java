package cloud.eppo.androidexample;

import static cloud.eppo.androidexample.Constants.INITIAL_FLAG_KEY;
import static cloud.eppo.androidexample.Constants.INITIAL_SUBJECT_ID;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import cloud.eppo.android.framework.AndroidBaseClient;
import cloud.eppo.android.framework.storage.ConfigurationCodec;
import cloud.eppo.android.framework.storage.FileBackedConfigStore;
import cloud.eppo.android.framework.util.Utils;
import cloud.eppo.api.AllocationDetails;
import cloud.eppo.api.AssignmentDetails;
import cloud.eppo.api.Attributes;
import cloud.eppo.api.Configuration;
import cloud.eppo.api.EvaluationDetails;
import com.geteppo.androidexample.BuildConfig;
import com.geteppo.androidexample.R;
import com.google.gson.JsonElement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Demonstrates supplying custom implementations of both {@link
 * cloud.eppo.http.EppoConfigurationClient} and {@link cloud.eppo.parser.ConfigurationParser} to
 * {@link AndroidBaseClient.Builder}.
 *
 * <ul>
 *   <li>{@link GsonConfigurationParser} — GSON-based parser instead of the default Jackson one
 *   <li>{@link HeaderInjectingEppoClient} — custom HTTP client that attaches headers to every
 *       configuration request
 * </ul>
 */
public class CustomClientActivity extends AppCompatActivity {
  private static final String TAG = CustomClientActivity.class.getSimpleName();
  private static final String API_KEY = BuildConfig.API_KEY;

  private EditText experiment;
  private EditText subject;
  private TextView assignmentLog;
  private ScrollView assignmentLogScrollView;

  private AndroidBaseClient<JsonElement> client;

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

    appendToLog("Initializing with GsonConfigurationParser + HeaderInjectingEppoClient…");

    Map<String, String> customHeaders = new LinkedHashMap<>();
    customHeaders.put("X-App-Name", "EppoExampleApp");
    customHeaders.put("X-App-Version", BuildConfig.VERSION_NAME);

    FileBackedConfigStore customStore =
        new FileBackedConfigStore(
            getApplication(),
            Utils.safeCacheKey(API_KEY),
            new ConfigurationCodec.Default<>(Configuration.class));

    new AndroidBaseClient.Builder<>(
            API_KEY,
            getApplication(),
            new GsonConfigurationParser(),
            new HeaderInjectingEppoClient(customHeaders))
        .forceReinitialize(true)
        .isGracefulMode(false)
        .configStore(customStore)
        .assignmentLogger(
            assignment ->
                Log.d(
                    TAG,
                    assignment.getExperiment()
                        + " -> subject: "
                        + assignment.getSubject()
                        + " assigned to "
                        + assignment.getVariation()))
        .buildAndInitAsync()
        .thenAccept(
            initialized -> {
              this.client = initialized;
              runOnUiThread(
                  () ->
                      appendToLog(
                          "Ready — GSON parser + header-injecting HTTP client.\n"
                              + "Injected headers: "
                              + customHeaders));
            })
        .exceptionally(
            error -> {
              runOnUiThread(() -> appendToLog("Initialization failed: " + error.getMessage()));
              Log.e(TAG, "Initialization failed", error);
              return null;
            });

    findViewById(R.id.btn_assign).setOnClickListener(view -> handleAssignment());
  }

  private void handleAssignment() {
    if (client == null) {
      appendToLog("Client not ready yet");
      return;
    }

    String subjectId = subject.getText().toString();
    if (TextUtils.isEmpty(subjectId)) {
      appendToLog("Subject must not be empty");
      return;
    }

    String experimentKey = experiment.getText().toString();
    if (TextUtils.isEmpty(experimentKey)) {
      appendToLog("Experiment key must not be empty");
      return;
    }

    AssignmentDetails<String> details =
        client.getStringAssignmentDetails(experimentKey, subjectId, new Attributes(), "DEFAULT");
    Log.i(TAG, "Assignment: " + details.getVariation());
    appendToLog(formatAssignmentDetails(details));
  }

  private String formatAssignmentDetails(AssignmentDetails<String> details) {
    EvaluationDetails eval = details.getEvaluationDetails();
    StringBuilder sb = new StringBuilder();

    sb.append("Variation: \"").append(details.getVariation()).append("\"\n");
    sb.append("  Code:        ").append(eval.getFlagEvaluationCode()).append("\n");
    sb.append("  Description: ").append(eval.getFlagEvaluationDescription()).append("\n");
    sb.append("  Variation key: ").append(nullOr(eval.getVariationKey())).append("\n");
    sb.append("  Environment: ").append(nullOr(eval.getEnvironmentName())).append("\n");
    sb.append("  Config fetched: ").append(formatDate(eval.getConfigFetchedAt())).append("\n");

    AllocationDetails matched = eval.getMatchedAllocation();
    if (matched != null) {
      sb.append("  Matched allocation: ")
          .append(matched.getKey())
          .append(" (")
          .append(matched.getAllocationEvaluationCode())
          .append(")\n");
    } else {
      sb.append("  Matched allocation: none\n");
    }

    List<AllocationDetails> unmatched = eval.getUnmatchedAllocations();
    if (unmatched != null && !unmatched.isEmpty()) {
      sb.append("  Unmatched (").append(unmatched.size()).append("):");
      for (AllocationDetails a : unmatched) {
        sb.append("\n    - ")
            .append(a.getKey())
            .append(" (")
            .append(a.getAllocationEvaluationCode())
            .append(")");
      }
      sb.append("\n");
    }

    List<AllocationDetails> unevaluated = eval.getUnevaluatedAllocations();
    if (unevaluated != null && !unevaluated.isEmpty()) {
      sb.append("  Unevaluated (").append(unevaluated.size()).append("):");
      for (AllocationDetails a : unevaluated) {
        sb.append("\n    - ").append(a.getKey());
      }
      sb.append("\n");
    }

    return sb.toString();
  }

  private static String nullOr(String value) {
    return value != null ? value : "(null)";
  }

  private static String formatDate(Date date) {
    if (date == null) return "(null)";
    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(date);
  }

  private void appendToLog(String message) {
    assignmentLog.append(message + "\n\n");
    assignmentLogScrollView.post(() -> assignmentLogScrollView.fullScroll(View.FOCUS_DOWN));
  }

  @Override
  public void onPause() {
    super.onPause();
    if (client != null) {
      client.pausePolling();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if (client != null) {
      client.resumePolling();
    }
  }
}
