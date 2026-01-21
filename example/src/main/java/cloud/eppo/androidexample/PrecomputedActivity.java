package cloud.eppo.androidexample;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import cloud.eppo.android.EppoPrecomputedClient;
import cloud.eppo.api.Attributes;
import cloud.eppo.api.EppoValue;
import com.geteppo.androidexample.BuildConfig;
import com.geteppo.androidexample.R;

/**
 * Example activity demonstrating the EppoPrecomputedClient. The precomputed client computes all
 * flag assignments server-side for a specific subject, providing instant lookups.
 */
public class PrecomputedActivity extends AppCompatActivity {
  private static final String TAG = PrecomputedActivity.class.getSimpleName();
  private static final String API_KEY = BuildConfig.API_KEY;

  private EditText subjectInput;
  private EditText flagKeyInput;
  private RadioGroup flagTypeGroup;
  private TextView assignmentLog;
  private ScrollView assignmentLogScrollView;
  private TextView statusText;

  private EppoPrecomputedClient precomputedClient;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_precomputed);

    subjectInput = findViewById(R.id.precomputed_subject);
    flagKeyInput = findViewById(R.id.precomputed_flag_key);
    flagTypeGroup = findViewById(R.id.flag_type_group);
    assignmentLog = findViewById(R.id.precomputed_assignment_log);
    assignmentLogScrollView = findViewById(R.id.precomputed_assignment_log_scrollview);
    statusText = findViewById(R.id.precomputed_status);

    findViewById(R.id.btn_initialize).setOnClickListener(view -> initializeClient());
    findViewById(R.id.btn_get_assignment).setOnClickListener(view -> getAssignment());
  }

  private void initializeClient() {
    String subjectKey = subjectInput.getText().toString();
    if (TextUtils.isEmpty(subjectKey)) {
      appendToLog("Subject ID is required");
      return;
    }

    statusText.setText("Initializing...");
    appendToLog("Initializing precomputed client for subject: " + subjectKey);

    // Create subject attributes (example)
    Attributes subjectAttributes = new Attributes();
    subjectAttributes.put("platform", EppoValue.valueOf("android"));
    subjectAttributes.put("appVersion", EppoValue.valueOf(BuildConfig.VERSION_NAME));

    new EppoPrecomputedClient.Builder(API_KEY, getApplication())
        .subjectKey(subjectKey)
        .subjectAttributes(subjectAttributes)
        .isGracefulMode(true)
        .forceReinitialize(true)
        .assignmentLogger(
            assignment -> {
              Log.d(
                  TAG,
                  "Assignment logged: "
                      + assignment.getFeatureFlag()
                      + " -> "
                      + assignment.getVariation());
            })
        .buildAndInitAsync()
        .thenAccept(
            client -> {
              precomputedClient = client;
              runOnUiThread(
                  () -> {
                    statusText.setText("Initialized for: " + subjectKey);
                    appendToLog("Client initialized successfully!");
                  });
            })
        .exceptionally(
            error -> {
              Log.e(TAG, "Failed to initialize", error);
              runOnUiThread(
                  () -> {
                    statusText.setText("Initialization failed");
                    appendToLog("Error: " + error.getMessage());
                  });
              return null;
            });
  }

  private void getAssignment() {
    if (precomputedClient == null) {
      appendToLog("Client not initialized. Click 'Initialize Client' first.");
      return;
    }

    String flagKey = flagKeyInput.getText().toString();
    if (TextUtils.isEmpty(flagKey)) {
      appendToLog("Flag key is required");
      return;
    }

    int selectedTypeId = flagTypeGroup.getCheckedRadioButtonId();
    String result;

    try {
      if (selectedTypeId == R.id.type_string) {
        result = precomputedClient.getStringAssignment(flagKey, "(default)");
        appendToLog("String assignment for '" + flagKey + "': " + result);
      } else if (selectedTypeId == R.id.type_boolean) {
        boolean boolResult = precomputedClient.getBooleanAssignment(flagKey, false);
        appendToLog("Boolean assignment for '" + flagKey + "': " + boolResult);
      } else if (selectedTypeId == R.id.type_integer) {
        int intResult = precomputedClient.getIntegerAssignment(flagKey, 0);
        appendToLog("Integer assignment for '" + flagKey + "': " + intResult);
      } else if (selectedTypeId == R.id.type_numeric) {
        double numericResult = precomputedClient.getNumericAssignment(flagKey, 0.0);
        appendToLog("Numeric assignment for '" + flagKey + "': " + numericResult);
      } else if (selectedTypeId == R.id.type_json) {
        // JSON assignments return JsonNode - for simplicity, we show as string
        appendToLog("JSON assignment for '" + flagKey + "': (use getJSONAssignment() API)");
      } else {
        appendToLog("Please select a flag type");
      }
    } catch (Exception e) {
      appendToLog("Error getting assignment: " + e.getMessage());
    }
  }

  private void appendToLog(String message) {
    assignmentLog.append(message + "\n\n");
    assignmentLogScrollView.post(() -> assignmentLogScrollView.fullScroll(View.FOCUS_DOWN));
  }

  @Override
  public void onPause() {
    super.onPause();
    if (precomputedClient != null) {
      precomputedClient.pausePolling();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if (precomputedClient != null) {
      precomputedClient.resumePolling();
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (precomputedClient != null) {
      precomputedClient.stopPolling();
    }
  }
}
