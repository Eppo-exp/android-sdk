package cloud.eppo.androidexample;

import android.app.Application;
import android.util.Log;

import cloud.eppo.android.EppoClient;
import cloud.eppo.android.InitializationCallback;
import com.geteppo.androidexample.BuildConfig;

public class EppoApplication extends Application {
    private static final String TAG = EppoApplication.class.getSimpleName();
    private static final String API_KEY = BuildConfig.API_KEY; // Set in root-level local.properties

    @Override
    public void onCreate() {
        super.onCreate();
        new EppoClient.Builder()
            .application(this)
            .apiKey(API_KEY)
            .isGracefulMode(true)
            .assignmentLogger(assignment -> {
                Log.d(TAG, assignment.getExperiment() + "-> subject: " + assignment.getSubject() + " assigned to " + assignment.getExperiment());
            })
            .callback(new InitializationCallback() {
                @Override
                public void onCompleted() {
                    Log.d(TAG, "Eppo SDK initialized");
                }

                @Override
                public void onError(String errorMessage) {
                    throw new RuntimeException("Unable to initialize. Ensure you API key is set correctly in EppoApplication.java");
                }
            })
            .buildAndInit();
    }
}
