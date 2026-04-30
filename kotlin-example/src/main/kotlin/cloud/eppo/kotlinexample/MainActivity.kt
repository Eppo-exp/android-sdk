package cloud.eppo.kotlinexample

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import cloud.eppo.api.Attributes

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)
        val assignmentText = findViewById<TextView>(R.id.assignmentText)

        statusText.text = getString(R.string.status_not_initialized)

        EppoApplication.initFuture
            .thenAccept { client ->
                if (!isDestroyed) {
                    runOnUiThread {
                        val assignment = client.getStringAssignment(
                            "my-example-flag",
                            "user-123",
                            Attributes(),
                            "control"
                        )
                        statusText.text = getString(R.string.status_ready)
                        assignmentText.text = getString(R.string.assignment_result, assignment)
                    }
                }
            }
            .exceptionally { ex ->
                if (!isDestroyed) {
                    runOnUiThread {
                        statusText.text = "Initialization failed: ${ex.message}"
                        Log.e(TAG, "Eppo init failed", ex)
                    }
                }
                null
            }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
