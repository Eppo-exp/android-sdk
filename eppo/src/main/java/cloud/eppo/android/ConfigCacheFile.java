package cloud.eppo.android;

import android.app.Application;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ConfigCacheFile {
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

    /**
     * Useful for passing in as a writer for gson serialization
     */
    public BufferedWriter getWriter() throws IOException {
        return new BufferedWriter(new FileWriter(cacheFile));
    }

    /**
     * Useful for passing in as a reader for gson deserialization
     */
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new FileReader(cacheFile));
    }

    /**
     * Useful for mocking caches in automated tests
     */
    public void setContents(String contents) {
        delete();
        try {
            BufferedWriter writer = getWriter();
            writer.write(contents);
            writer.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
