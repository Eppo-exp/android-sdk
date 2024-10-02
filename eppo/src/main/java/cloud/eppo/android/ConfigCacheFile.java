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

public class ConfigCacheFile {
  private final File cacheFile;

  public ConfigCacheFile(Application application, String fileNameSuffix) {
    File filesDir = application.getFilesDir();
    cacheFile = new File(filesDir, cacheFileName(fileNameSuffix));
  }

  public static String cacheFileName(String suffix) {
    return "eppo-sdk-config-v4-" + suffix + ".ser";
  }

  public boolean exists() {
    return cacheFile.exists();
  }

  /**
   * @noinspection ResultOfMethodCallIgnored
   */
  public void delete() {
    if (cacheFile.exists()) {
      cacheFile.delete();
    }
  }

  public String getFileContents() {
    StringBuilder text = new StringBuilder();
    // on below line creating and initializing buffer reader.
    BufferedReader br = null;
    try {
      br = getReader();
      // on below line creating a string variable/
      String line;
      // on below line setting the data to text
      while ((line = br.readLine()) != null) {
        text.append(line);
      }
      br.close();

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return text.toString();
  }

  /** Useful for passing in as a writer for gson serialization */
  public BufferedWriter getWriter() throws IOException {
    return new BufferedWriter(new FileWriter(cacheFile));
  }

  public OutputStream getOutputStream() throws FileNotFoundException {
    return new FileOutputStream(cacheFile);
  }

  public InputStream getInputStream() throws FileNotFoundException {
    return new FileInputStream(cacheFile);
  }

  /** Useful for passing in as a reader for gson deserialization */
  public BufferedReader getReader() throws IOException {
    return new BufferedReader(new FileReader(cacheFile));
  }

  /** Useful for mocking caches in automated tests */
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
