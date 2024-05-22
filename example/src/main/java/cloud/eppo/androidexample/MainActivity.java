package cloud.eppo.androidexample;

import static cloud.eppo.androidexample.Constants.INITIAL_FLAG_KEY;
import static cloud.eppo.androidexample.Constants.INITIAL_SUBJECT_ID;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.geteppo.androidexample.R;

import cloud.eppo.android.EppoClient;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button button = findViewById(R.id.button_start_assigner);
        button.setOnClickListener(view -> startActivity(new Intent(MainActivity.this, SecondActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();

        // for testing assignments on application/main activity start
        if (!TextUtils.isEmpty(INITIAL_FLAG_KEY)) {
            EppoClient.getInstance().getStringAssignment(INITIAL_FLAG_KEY, INITIAL_SUBJECT_ID, "");
        }
    }
}
