package cloud.eppo.kotlinexample

import android.app.Application
import android.util.Log
import cloud.eppo.OkHttpEppoClient
import cloud.eppo.android.framework.BaseAndroidClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.serialization.json.JsonElement

class EppoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val raw = BaseAndroidClient.Builder(
            BuildConfig.EPPO_API_KEY,
            this,
            KotlinxConfigurationParser(),
            OkHttpEppoClient()
        )
            .isGracefulMode(true)
            .buildAndInitAsync()

        // orTimeout() requires API 31; manual fallback for minSdk 26.
        val result = CompletableFuture<BaseAndroidClient<JsonElement>>()
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val timeoutTask = scheduler.schedule({
            result.completeExceptionally(TimeoutException("Eppo initialization timed out"))
        }, INIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        raw.handle { client, ex ->
            timeoutTask.cancel(false)
            scheduler.shutdown()
            if (ex != null) result.completeExceptionally(ex) else result.complete(client)
            null
        }

        initFuture = result.whenComplete { _, ex ->
            if (ex != null) {
                Log.e(TAG, "Eppo initialization failed", ex)
            } else {
                Log.i(TAG, "Eppo client initialized")
            }
        }
    }

    companion object {
        private const val TAG = "EppoApplication"
        private const val INIT_TIMEOUT_SECONDS = 15L

        lateinit var initFuture: CompletableFuture<BaseAndroidClient<JsonElement>>
            private set
    }
}
