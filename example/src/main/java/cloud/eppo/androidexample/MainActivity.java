package cloud.eppo.androidexample;

import static cloud.eppo.android.util.Utils.safeCacheKey;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import cloud.eppo.android.framework.storage.ConfigCacheFile;
import com.geteppo.androidexample.BuildConfig;
import com.geteppo.androidexample.R;

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
            startActivity(launchAssigner.putExtra(this.getPackageName() + ".offlineMode", true)));

    Button gsonButton = findViewById(R.id.button_start_gson_assigner);
    Intent launchGsonAssigner = new Intent(MainActivity.this, CustomClientActivity.class);
    gsonButton.setOnClickListener(view -> startActivity(launchGsonAssigner));

    Button clearCacheButton = findViewById(R.id.button_clear_cache);
    clearCacheButton.setOnClickListener(view -> clearCacheFile());
  }

  private void clearCacheFile() {
    String cacheFileNameSuffix = safeCacheKey(API_KEY);
    ConfigCacheFile cacheFile =
        new ConfigCacheFile(
            getApplication(), cacheFileNameSuffix, "application/x-java-serialized-object");
    if (cacheFile.exists()) {

      cacheFile.delete();
      Toast.makeText(this, "Cache Cleared", Toast.LENGTH_SHORT).show();
    } else {
      Toast.makeText(this, "Cache file did not exist", Toast.LENGTH_SHORT).show();
    }
  }

}
