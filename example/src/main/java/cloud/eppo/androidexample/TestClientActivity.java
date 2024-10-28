package cloud.eppo.androidexample;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import cloud.eppo.android.EppoClient;
import cloud.eppo.api.Attributes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geteppo.androidexample.BuildConfig;
import com.geteppo.androidexample.R;
import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import io.socket.engineio.client.EngineIOException;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class TestClientActivity extends AppCompatActivity {
  private static final String TAG = TestClientActivity.class.getSimpleName();
  private static final String API_KEY = BuildConfig.API_KEY; // Set in root-level local.properties
  private static final String READY_PACKET =
      "{\"sdkName\":\"example\", \"supportsBandits\" : false, \"sdkType\":\"client\"}";

  // UI Controls
  private TextView mStatus;
  private TextView mSocketLog;
  private ScrollView mSocketLogScroll;
  private TextView mAssignmentLog;
  private ScrollView mAssignmentLogScroll;

  // Services
  private Socket mSocket;
  ObjectMapper objectMapper = new ObjectMapper();

  {
    try {
      mSocket = IO.socket("http://10.0.2.2:3000");
    } catch (URISyntaxException e) {
    }
  }

  private static class AssignmentRequest {
    public String flag;
    public String subjectKey;
    public String assignmentType;
    public Map<String, Object> subjectAttributes;
    public Object defaultValue;
  }

  private static class TestResponse {
    public final Object result;

    public TestResponse(Object result) {
      this.result = result;
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Socket listeners
    mSocket.on("connect_error", onConnectError);
    mSocket.on("connect", onConnect);
    mSocket.on("disconnect", onRunnerDisconnect);
    mSocket.on("/sdk/reset", onSdkReset);
    mSocket.on("/flags/v1/assignment", onNewAssignment);

    // Set the view and grab UI handles
    setContentView(R.layout.activity_socket);
    mStatus = findViewById(R.id.status_textview);
    mSocketLog = findViewById(R.id.socket_log_textview);
    mAssignmentLog = findViewById(R.id.assignment_log_textview);
    mSocketLogScroll = findViewById(R.id.socket_log_scrollview);
    mAssignmentLogScroll = findViewById(R.id.assignment_log_scrollview);

    updateStatus("pending");

    // Initialize the SDK
    reInitializeEppoClient()
        .thenAccept(
            client -> {
              Log.d(TAG, "Eppo SDK initialized");

              // Now connect to the test runner
              mSocket.connect();
            })
        .exceptionally(
            error -> {
              throw new RuntimeException("Unable to initialize.", error);
            });
  }

  private CompletableFuture<EppoClient> reInitializeEppoClient() {
    // Most of the settings used here are intended for debugging only.
    return new EppoClient.Builder(API_KEY, getApplication())
        .forceReinitialize(true) // Debug: create a new instance every time
        .ignoreCache(true) // Debug: don't preload data from the device
        .host("http://10.0.2.2:5000") // Debug: for local API serving
        .isGracefulMode(false) // Debug: surface exceptions
        .assignmentLogger(
            assignment -> {
              String msg =
                  assignment.getExperiment()
                      + "-> subject: "
                      + assignment.getSubject()
                      + " assigned to "
                      + assignment.getExperiment();
              Log.d(TAG, msg);

              addAssignmentMessage(msg);
            })
        .buildAndInitAsync();
  }

  private void sendReady() {
    mSocket.emit(
        "READY",
        new String[] {READY_PACKET},
        (ackArgs) -> {
          Log.d(TAG, "Ready message ack'd");
        });
  }

  private void addSocketMessage(String payload) {
    mSocketLog.append(payload + "\n");
    mSocketLogScroll.post(() -> mSocketLogScroll.fullScroll(View.FOCUS_DOWN));
  }

  private void addAssignmentMessage(String payload) {
    mAssignmentLog.append(payload + "\n");
    mAssignmentLogScroll.post(() -> mAssignmentLogScroll.fullScroll(View.FOCUS_DOWN));
  }

  private void updateStatus(String status) {
    String fullStr = getString(R.string.label_status_prefix, status);
    mStatus.setText(fullStr);
  }

  // Event listener lambdas

  private final Emitter.Listener onConnect =
      (args) -> {
        Log.d(TAG, "(Re)connected");
        updateStatus("(Re)connected");

        sendReady();
      };

  private final Emitter.Listener onConnectError =
      (args) -> {
        EngineIOException exception = (EngineIOException) args[0];
        if (exception != null && exception.getMessage() != null) {
          Log.e("Connection error", exception.getMessage());
        }
      };

  private final Emitter.Listener onSdkReset =
      args -> {
        Ack ack = (Ack) args[args.length - 1];
        reInitializeEppoClient()
            .thenAccept(
                client -> {
                  ack.call(true);
                });
      };

  private final Emitter.Listener onRunnerDisconnect =
      args -> {
        // close down the socket and pop a notification.
        this.mSocket.close();
        runOnUiThread(
            () -> {
              updateStatus("Complete");
              Toast.makeText(
                      TestClientActivity.this, "Test runner disconnected", Toast.LENGTH_SHORT)
                  .show();
            });
      };

  private final Emitter.Listener onNewAssignment =
      args ->
          runOnUiThread(
              () -> {
                Log.d(TAG, "Assignment Request received");
                updateStatus("Assigning Flag");

                addSocketMessage("flags/v1/assignment:");
                addSocketMessage(args[0].toString());

                // Ack function for responding to the server.
                Ack ack = (Ack) args[args.length - 1];

                AssignmentRequest assignmentRequest;
                try {
                  assignmentRequest =
                      objectMapper.readValue(args[0].toString(), AssignmentRequest.class);
                } catch (JsonProcessingException e) {
                  ack.call(genericErrorResponse());
                  throw new RuntimeException(e);
                }

                EppoClient client = EppoClient.getInstance();

                try {
                  // Convert the subject attributes map to an `Attributes` instance.
                  Attributes subject = convertAttributesMapToAttributes(assignmentRequest);
                  Object result = getResult(assignmentRequest, client, subject);

                  TestResponse testResponse = new TestResponse(result);

                  // "return" the result.
                  ack.call(objectMapper.writeValueAsString(testResponse));
                } catch (JsonProcessingException e) {
                  ack.call(genericErrorResponse());
                  throw new RuntimeException(e);
                }
              });

  @NonNull private Object getResult(
      AssignmentRequest assignmentRequest, EppoClient client, Attributes subject)
      throws JsonProcessingException {
    switch (assignmentRequest.assignmentType) {
      case "STRING":
        return client.getStringAssignment(
            assignmentRequest.flag,
            assignmentRequest.subjectKey,
            subject,
            (String) assignmentRequest.defaultValue);

      case "INTEGER":
        return client.getIntegerAssignment(
            assignmentRequest.flag,
            assignmentRequest.subjectKey,
            subject,
            (Integer) assignmentRequest.defaultValue);

      case "BOOLEAN":
        return client.getBooleanAssignment(
            assignmentRequest.flag,
            assignmentRequest.subjectKey,
            subject,
            (Boolean) assignmentRequest.defaultValue);

      case "NUMERIC":
        double defaultNumericValue =
            (assignmentRequest.defaultValue instanceof Integer)
                ? (Integer) assignmentRequest.defaultValue
                : (Double) assignmentRequest.defaultValue;
        return client.getDoubleAssignment(
            assignmentRequest.flag, assignmentRequest.subjectKey, subject, defaultNumericValue);

      case "JSON":
        JsonNode defaultValue =
            objectMapper.convertValue(assignmentRequest.defaultValue, JsonNode.class);

        return client.getJSONAssignment(
            assignmentRequest.flag, assignmentRequest.subjectKey, subject, defaultValue);
    }
    return "NO RESULT";
  }

  @NonNull private static Attributes convertAttributesMapToAttributes(AssignmentRequest assignmentRequest) {
    Attributes subject = new Attributes();
    assignmentRequest.subjectAttributes.forEach(
        (key, value) -> {
          if (value instanceof String) {
            subject.put(key, (String) value);
          } else if (value instanceof Integer) {
            subject.put(key, (int) value);
          } else if (value instanceof Number) {
            subject.put(key, (double) value);
          } else if (value instanceof Boolean) {
            subject.put(key, (Boolean) value);
          } else {
            // Handle other types (potentially throw an exception or log a warning)
            Log.e(TAG, "Unsupported attribute value type for key: " + key);
          }
        });
    return subject;
  }

  @NonNull private String genericErrorResponse() {
    return "{\"error\": \"JSON processing error\"}";
  }

  @Override
  public void onDestroy() {
    super.onDestroy();

    mSocket.disconnect();

    // Clear all listeners
    mSocket.off();
  }
}
