package cloud.eppo.android;

import android.app.Application;
import android.util.Log;

import androidx.security.crypto.EncryptedFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.security.GeneralSecurityException;

import cloud.eppo.android.util.Utils;

public class ConfigCacheFile {
    private static final String TAG = ConfigCacheFile.class.getSimpleName();
    static final String ENC_CACHE_FILE_NAME = "eppo-sdk-config.enc";
    static final String PT_CACHE_FILE_NAME = "eppo-sdk-config.json";

    private final File filesDir;

    private EncryptedFile encryptedFile;
    private File plaintextFile;

    public ConfigCacheFile(Application application, boolean useEncryptedFileIfPossible) {
        filesDir = application.getFilesDir();

        if (useEncryptedFileIfPossible) {
            try {
                encryptedFile = Utils.getSecureFile(filesDir, ENC_CACHE_FILE_NAME, application);
            } catch (Exception e) {
                Log.e(TAG, "Unable to create secure cache file", e);
            }
        }

        if (encryptedFile == null) {
            plaintextFile = new File(filesDir, PT_CACHE_FILE_NAME);
        }
    }

    private File getFile() {
        if (encryptedFile != null) {
            return new File(filesDir, ENC_CACHE_FILE_NAME);
        } else {
            return plaintextFile;
        }
    }

    public boolean exists() {
        return getFile().exists();
    }

    public void delete() {
        File existingFile = getFile();
        if (existingFile.exists()) {
            existingFile.delete();
        }
    }

    public OutputStreamWriter getOutputWriter() throws GeneralSecurityException, IOException {
        if (encryptedFile != null) {
            // EncryptedFile requires deleting old versions if one exists
            delete();
            return new OutputStreamWriter(encryptedFile.openFileOutput());
        } else {
            FileOutputStream fos = new FileOutputStream(getFile());
            return new OutputStreamWriter(fos);
        }
    }

    public InputStreamReader getInputReader() throws GeneralSecurityException, IOException {
        if (encryptedFile != null) {
            return new InputStreamReader(encryptedFile.openFileInput());
        } else {
            FileInputStream fis = new FileInputStream(getFile());
            return new InputStreamReader(fis);
        }
    }
}
