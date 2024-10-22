package cloud.eppo.androidexample;

import static cloud.eppo.android.util.Utils.safeCacheKey;
import static cloud.eppo.androidexample.Constants.INITIAL_FLAG_KEY;
import static cloud.eppo.androidexample.Constants.INITIAL_SUBJECT_ID;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.geteppo.androidexample.BuildConfig;
import com.geteppo.androidexample.R;

import cloud.eppo.android.ConfigCacheFile;
import cloud.eppo.android.EppoClient;

public class MainActivity extends AppCompatActivity {
  private static final String API_KEY = BuildConfig.API_KEY; // Set in root-level local.properties

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    Button button = findViewById(R.id.button_start_assigner);
    Intent launchAssigner = new Intent(MainActivity.this, SecondActivity.class);

    button.setOnClickListener(view -> startActivity(launchAssigner));

    Button offlineButton = findViewById(R.id.button_start_offline_assigner);
    offlineButton.setOnClickListener(
        view ->
            startActivity(launchAssigner.putExtra(this.getPackageName() + ".offlineMode", false)));

    Button clearCacheButton = findViewById(R.id.button_clear_cache);
    clearCacheButton.setOnClickListener(view -> clearCacheFile());
  }

  private void clearCacheFile() {
    String cacheFileNameSuffix = safeCacheKey(API_KEY);
    ConfigCacheFile cacheFile = new ConfigCacheFile(getApplication(), cacheFileNameSuffix);
    cacheFile.delete();
    Toast.makeText(this, "Cache Cleared", Toast.LENGTH_SHORT).show();
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
