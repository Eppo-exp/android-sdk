package cloud.eppo.androidexample;

import static cloud.eppo.android.Constants.LoggingTag;

import android.app.Application;
import android.util.Log;

import cloud.eppo.android.EppoClient;
import cloud.eppo.android.InitializationCallback;

public class EppoApplication extends Application {
    private static final String API_KEY = "REPLACE WITH YOUR API KEY";

    @Override
    public void onCreate() {
        super.onCreate();
        new EppoClient.Builder()
            .application(this)
            .apiKey(API_KEY)
            .assignmentLogger(assignment -> {
                Log.d(EppoApplication.class.getSimpleName(), assignment.getExperiment() + "-> subject: " + assignment.getSubject() + " assigned to " + assignment.getExperiment());
            })
            .callback(new InitializationCallback() {
                @Override
                public void onCompleted() {
                    Log.d(LoggingTag, "Eppo SDK initialized");
                }

                @Override
                public void onError(String errorMessage) {
                    throw new RuntimeException("Unable to initialize. Ensure you API key is set correctly in EppoApplication.java");
                }
            })
            .buildAndInit();
    }
}
