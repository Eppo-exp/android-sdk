package cloud.eppo.android;

import android.app.Application;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Disk cache file for precomputed configuration. */
public class PrecomputedCacheFile {
  private final File cacheFile;

  public PrecomputedCacheFile(Application application, String fileNameSuffix) {
    File filesDir = application.getFilesDir();
    cacheFile = new File(filesDir, cacheFileName(fileNameSuffix));
  }

  public static String cacheFileName(String suffix) {
    return "eppo-sdk-precomputed-" + suffix + ".json";
  }

  public boolean exists() {
    return cacheFile.exists();
  }

  /** @noinspection ResultOfMethodCallIgnored */
  public void delete() {
    if (cacheFile.exists()) {
      cacheFile.delete();
    }
  }

  /** Useful for passing in as a writer for JSON serialization. */
  public BufferedWriter getWriter() throws IOException {
    return new BufferedWriter(new FileWriter(cacheFile));
  }

  public OutputStream getOutputStream() throws FileNotFoundException {
    return new FileOutputStream(cacheFile);
  }

  public InputStream getInputStream() throws FileNotFoundException {
    return new FileInputStream(cacheFile);
  }

  /** Useful for passing in as a reader for JSON deserialization. */
  public BufferedReader getReader() throws IOException {
    return new BufferedReader(new FileReader(cacheFile));
  }

  /** Useful for mocking caches in automated tests. */
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
