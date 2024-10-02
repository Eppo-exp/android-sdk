package cloud.eppo.androidexample;

import android.app.Application;
import android.util.Log;

import cloud.eppo.android.EppoClient;

import com.geteppo.androidexample.BuildConfig;

public class EppoApplication extends Application {
    private static final String TAG = EppoApplication.class.getSimpleName();
    private static final String API_KEY = BuildConfig.API_KEY; // Set in root-level local.properties

    @Override
    public void onCreate() {
        super.onCreate();

    }
}
