package cloud.eppo.kotlinexample

import android.app.Application
import android.util.Log
import cloud.eppo.OkHttpEppoClient
import cloud.eppo.android.framework.BaseAndroidClient

class EppoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initEppoClient()
    }

    private fun initEppoClient() {
        BaseAndroidClient.Builder(
            BuildConfig.EPPO_API_KEY,
            this,
            KotlinxConfigurationParser(),
            OkHttpEppoClient()
        )
            .isGracefulMode(true)
            .buildAndInitAsync()
            .handle { _, ex ->
                if (ex != null) {
                    Log.e(TAG, "Eppo initialization failed", ex)
                } else {
                    Log.i(TAG, "Eppo client initialized")
                }
                null
            }
    }

    companion object {
        private const val TAG = "EppoApplication"
    }
}
