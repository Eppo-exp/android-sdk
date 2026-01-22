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
import cloud.eppo.android.ConfigCacheFile;
import cloud.eppo.android.EppoClient;
import com.geteppo.androidexample.BuildConfig;
import com.geteppo.androidexample.R;
import java.io.File;

public class HomeActivity extends AppCompatActivity {
  private static final String API_KEY = BuildConfig.API_KEY; // Set in root-level local.properties

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_home);
    Button button = findViewById(R.id.button_start_assigner);
    Intent launchAssigner = new Intent(HomeActivity.this, StandardClientActivity.class);

    button.setOnClickListener(view -> startActivity(launchAssigner));

    Button offlineButton = findViewById(R.id.button_start_offline_assigner);
    offlineButton.setOnClickListener(
        view ->
            startActivity(launchAssigner.putExtra(this.getPackageName() + ".offlineMode", false)));

    Button precomputedButton = findViewById(R.id.button_start_precomputed);
    Intent launchPrecomputed = new Intent(HomeActivity.this, PrecomputedActivity.class);
    precomputedButton.setOnClickListener(view -> startActivity(launchPrecomputed));

    Button clearCacheButton = findViewById(R.id.button_clear_cache);
    clearCacheButton.setOnClickListener(view -> clearCacheFile());
  }

  private void clearCacheFile() {
    // Clear standard client cache
    String cacheFileNameSuffix = safeCacheKey(API_KEY);
    ConfigCacheFile cacheFile = new ConfigCacheFile(getApplication(), cacheFileNameSuffix);
    cacheFile.delete();

    // Clear all precomputed client caches (they include subject key hash in filename)
    File filesDir = getApplication().getFilesDir();
    File[] precomputedCaches =
        filesDir.listFiles(
            (dir, name) -> name.startsWith("eppo-sdk-precomputed-") && name.endsWith(".json"));
    int precomputedCount = 0;
    if (precomputedCaches != null) {
      for (File file : precomputedCaches) {
        if (file.delete()) {
          precomputedCount++;
        }
      }
    }

    String message =
        precomputedCount > 0
            ? "Cache Cleared (" + precomputedCount + " precomputed)"
            : "Cache Cleared";
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
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
