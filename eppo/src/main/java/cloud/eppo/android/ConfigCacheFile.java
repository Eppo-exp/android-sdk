package cloud.eppo.android;

import android.app.Application;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class ConfigCacheFile {
    private static final String TAG = ConfigCacheFile.class.getSimpleName();
    static final String CACHE_FILE_NAME = "eppo-sdk-config-v2.json";
    private final File filesDir;
    private final File cacheFile;

    public ConfigCacheFile(Application application) {
        filesDir = application.getFilesDir();
        cacheFile = new File(filesDir, CACHE_FILE_NAME);
    }

    public boolean exists() {
        return cacheFile.exists();
    }

    public void delete() {
        if (cacheFile.exists()) {
            cacheFile.delete();
        }
    }

    public OutputStreamWriter getOutputWriter() throws IOException {
        FileOutputStream fos = new FileOutputStream(cacheFile);
        return new OutputStreamWriter(fos);
    }

    public InputStreamReader getInputReader() throws IOException {
        FileInputStream fis = new FileInputStream(cacheFile);
        return new InputStreamReader(fis);
    }
}
