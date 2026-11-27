package cloud.eppo.android;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** 4.x appended "/api" to the host passed to init()/initAsync() (sdk-common 3.13.1). */
@RunWith(RobolectricTestRunner.class)
public class EppoClientStaticInitTest {
  private static final String UFC =
      "{\"format\":\"SERVER\",\"createdAt\":\"2026-10-14T00:00:00.000Z\","
          + "\"environment\":{\"name\":\"Test\"},\"flags\":{\"str_flag\":{\"key\":\"str_flag\","
          + "\"enabled\":true,\"variationType\":\"STRING\",\"totalShards\":10000,"
          + "\"variations\":{\"on\":{\"key\":\"on\",\"value\":\"served\"}},"
          + "\"allocations\":[{\"key\":\"all\",\"doLog\":false,"
          + "\"splits\":[{\"variationKey\":\"on\",\"shards\":[]}]}]}}}";

  private final List<String> paths = new CopyOnWriteArrayList<>();
  private ServerSocket server;
  private String host;

  @Before
  public void setUp() throws Exception {
    resetSingleton();
    server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
    Thread serverThread = new Thread(this::serve);
    serverThread.setDaemon(true);
    serverThread.start();
    host = "http://127.0.0.1:" + server.getLocalPort();
  }

  // Serves the UFC only at {host}/api/flag-config/v1/config, like the Eppo CDN.
  private void serve() {
    while (!server.isClosed()) {
      try (Socket socket = server.accept()) {
        BufferedReader in =
            new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        String path = in.readLine().split(" ")[1].split("\\?")[0];
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {}
        paths.add(path);
        byte[] body =
            path.equals("/api/flag-config/v1/config")
                ? UFC.getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        String status = body.length > 0 ? "200 OK" : "404 Not Found";
        OutputStream out = socket.getOutputStream();
        out.write(
            ("HTTP/1.1 "
                    + status
                    + "\r\nContent-Length: "
                    + body.length
                    + "\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
      } catch (Exception ignored) {
        // Socket closed by tearDown.
      }
    }
  }

  @After
  public void tearDown() throws Exception {
    server.close();
    resetSingleton();
  }

  @Test
  public void initAsyncWithHostFetchesFromHostApiPath() throws Exception {
    EppoClient client =
        EppoClient.initAsync(RuntimeEnvironment.getApplication(), "mock-api-key", host, null, true)
            .get(10, TimeUnit.SECONDS);

    assertEquals("/api/flag-config/v1/config", paths.get(0));
    assertEquals("served", client.getStringAssignment("str_flag", "subject", "default"));
  }

  @Test
  public void initWithHostFetchesFromHostApiPath() {
    EppoClient client =
        EppoClient.init(
            RuntimeEnvironment.getApplication(), "mock-api-key", host, null, null, true);

    assertEquals("/api/flag-config/v1/config", paths.get(0));
    assertEquals("served", client.getStringAssignment("str_flag", "subject", "default"));
  }

  @Test
  public void initUsesApiBaseUrlUnchangedWhenProvided() {
    EppoClient.init(
        RuntimeEnvironment.getApplication(),
        "mock-api-key",
        "http://127.0.0.1:1",
        host + "/api",
        null,
        true);

    assertEquals("/api/flag-config/v1/config", paths.get(0));
  }

  private static void resetSingleton() throws Exception {
    Field instance = EppoClient.class.getDeclaredField("instance");
    instance.setAccessible(true);
    instance.set(null, null);
  }
}
