package cloud.eppo.android.framework.storage;

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

/** Base class for disk cache files. */
public class BaseCacheFile {
  private final File cacheFile;

  protected BaseCacheFile(Application application, String fileName) {
    File filesDir = application.getFilesDir();
    cacheFile = new File(filesDir, fileName);
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

  /** Useful for passing in as a writer for JSON serialization. */
  public BufferedWriter getWriter() throws IOException {
    return new BufferedWriter(new FileWriter(cacheFile));
  }

  public OutputStream getOutputStream() throws FileNotFoundException {
    return new FileOutputStream(cacheFile);
  }

  /**
   * Atomically writes bytes by writing to a temp file then renaming.
   *
   * @param bytes the bytes to write
   * @throws IOException if writing or renaming fails
   */
  public void atomicWrite(byte[] bytes) throws IOException {
    File tmpFile = new File(cacheFile.getParentFile(), cacheFile.getName() + ".tmp");
    try (OutputStream out = new FileOutputStream(tmpFile)) {
      out.write(bytes);
      out.flush();
    }
    if (!tmpFile.renameTo(cacheFile)) {
      tmpFile.delete();
      throw new IOException("Failed to rename temp file to " + cacheFile.getName());
    }
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
    try (BufferedWriter writer = getWriter()) {
      writer.write(contents);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }
}
