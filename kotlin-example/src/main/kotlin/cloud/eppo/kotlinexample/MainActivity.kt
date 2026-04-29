package cloud.eppo.kotlinexample

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import cloud.eppo.android.framework.BaseAndroidClient
import cloud.eppo.android.framework.exceptions.NotInitializedException
import cloud.eppo.api.Attributes
import kotlinx.serialization.json.JsonElement

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)
        val assignmentText = findViewById<TextView>(R.id.assignmentText)

        try {
            val client = BaseAndroidClient.getInstance<JsonElement>()
            val assignment = client.getStringAssignment(
                "my-example-flag",
                "user-123",
                Attributes(),
                "control"
            )
            statusText.text = getString(R.string.status_ready)
            assignmentText.text = getString(R.string.assignment_result, assignment)
        } catch (e: NotInitializedException) {
            statusText.text = getString(R.string.status_not_initialized)
            assignmentText.text = ""
        }
    }
}
