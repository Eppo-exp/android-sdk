package cloud.eppo.android;

import static org.junit.Assert.assertEquals;

import cloud.eppo.BaseEppoClient;
import cloud.eppo.EppoHttpClient;
import cloud.eppo.android.util.ObfuscationUtils;
import cloud.eppo.logging.Assignment;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class AssignmentLoggingCacheTest {
  private static final String FLAG = "test-flag";
  private static final String SUBJECT = "test-subject";
  private static final String SALT = "test-salt";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  public void standardClientLogsChangedAssignmentsAfterConfigurationReload() throws Exception {
    AtomicReference<byte[]> response = new AtomicReference<>(standardConfig("allocation-1", "A"));
    EppoHttpClient httpClient =
        new EppoHttpClient("https://example.invalid", "test-api-key", "android", "test") {
          @Override
          public CompletableFuture<byte[]> getAsync(String path) {
            return CompletableFuture.completedFuture(response.get());
          }
        };
    Field override = BaseEppoClient.class.getDeclaredField("httpClientOverride");
    override.setAccessible(true);
    Object previous = override.get(null);
    override.set(null, httpClient);
    try {
      List<Assignment> assignments = new ArrayList<>();
      EppoClient client =
          new EppoClient.Builder("test-api-key", RuntimeEnvironment.getApplication())
              .forceReinitialize(true)
              .ignoreCachedConfiguration(true)
              .isGracefulMode(false)
              .assignmentLogger(assignments::add)
              .buildAndInitAsync()
              .get(10, TimeUnit.SECONDS);

      String[] variations = {"A", "B", "A", "A"};
      String[] allocations = {"allocation-1", "allocation-1", "allocation-1", "allocation-2"};
      for (int i = 0; i < variations.length; i++) {
        response.set(standardConfig(allocations[i], variations[i]));
        client.loadConfigurationAsync().get(10, TimeUnit.SECONDS);
        for (int repeat = 0; repeat < 2; repeat++) {
          assertEquals(variations[i], client.getStringAssignment(FLAG, SUBJECT, "default"));
        }
        assertAssignment(assignments, i, allocations[i], variations[i]);
        client.loadConfigurationAsync().get(10, TimeUnit.SECONDS);
        assertEquals(variations[i], client.getStringAssignment(FLAG, SUBJECT, "default"));
        assertAssignment(assignments, i, allocations[i], variations[i]);
      }
    } finally {
      override.set(null, previous);
    }
  }

  @Test
  public void precomputedClientLogsChangedAssignmentsAfterConfigurationReload() throws Exception {
    AtomicReference<byte[]> response =
        new AtomicReference<>(precomputedConfig("allocation-1", "A"));
    OkHttpClient httpClient =
        new OkHttpClient.Builder()
            .addInterceptor(
                chain ->
                    new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(
                            ResponseBody.create(response.get(), MediaType.get("application/json")))
                        .build())
            .build();
    try {
      List<Assignment> assignments = new ArrayList<>();
      EppoPrecomputedClient client =
          new EppoPrecomputedClient.Builder("test-api-key", RuntimeEnvironment.getApplication())
              .subjectKey(SUBJECT)
              .forceReinitialize(true)
              .ignoreCachedConfiguration(true)
              .isGracefulMode(false)
              .httpClient(httpClient)
              .assignmentLogger(assignments::add)
              .buildAndInitAsync()
              .get(10, TimeUnit.SECONDS);

      String[] variations = {"A", "B", "A", "A"};
      String[] allocations = {"allocation-1", "allocation-1", "allocation-1", "allocation-2"};
      for (int i = 0; i < variations.length; i++) {
        response.set(precomputedConfig(allocations[i], variations[i]));
        client.fetchPrecomputedFlagsAsync().get(10, TimeUnit.SECONDS);
        for (int repeat = 0; repeat < 2; repeat++) {
          assertEquals(variations[i], client.getStringAssignment(FLAG, "default"));
        }
        assertAssignment(assignments, i, allocations[i], variations[i]);
        client.fetchPrecomputedFlagsAsync().get(10, TimeUnit.SECONDS);
        assertEquals(variations[i], client.getStringAssignment(FLAG, "default"));
        assertAssignment(assignments, i, allocations[i], variations[i]);
      }
    } finally {
      httpClient.dispatcher().executorService().shutdownNow();
      httpClient.connectionPool().evictAll();
    }
  }

  private static void assertAssignment(
      List<Assignment> assignments, int index, String allocation, String variation) {
    assertEquals(index + 1, assignments.size());
    Assignment assignment = assignments.get(index);
    assertEquals(SUBJECT, assignment.getSubject());
    assertEquals(FLAG, assignment.getFeatureFlag());
    assertEquals(allocation, assignment.getAllocation());
    assertEquals(variation, assignment.getVariation());
  }

  private static ObjectNode configuration(String format) {
    ObjectNode config = MAPPER.createObjectNode();
    config.put("format", format);
    config.put("createdAt", "2026-10-14T00:00:00.000Z");
    config.putObject("environment").put("name", "Test");
    return config;
  }

  private static byte[] standardConfig(String allocation, String variation) throws Exception {
    ObjectNode config = configuration("CLIENT");
    String flagHash = ObfuscationUtils.md5Hex(FLAG, null);
    ObjectNode flag = config.putObject("flags").putObject(flagHash);
    flag.put("key", flagHash);
    flag.put("enabled", true);
    flag.put("variationType", "STRING");
    flag.put("totalShards", 10000);
    flag.putObject("variations")
        .putObject(encode(variation))
        .put("key", encode(variation))
        .put("value", encode(variation));
    ObjectNode allocationNode = flag.putArray("allocations").addObject();
    allocationNode.put("key", encode(allocation));
    allocationNode.put("doLog", true);
    allocationNode
        .putArray("splits")
        .addObject()
        .put("variationKey", encode(variation))
        .putArray("shards");
    return MAPPER.writeValueAsBytes(config);
  }

  private static byte[] precomputedConfig(String allocation, String variation) throws Exception {
    ObjectNode config = configuration("PRECOMPUTED");
    config.put("obfuscated", true);
    config.put("salt", SALT);
    ObjectNode flag = config.putObject("flags").putObject(ObfuscationUtils.md5Hex(FLAG, SALT));
    flag.put("allocationKey", encode(allocation));
    flag.put("variationKey", encode(variation));
    flag.put("variationType", "STRING");
    flag.put("variationValue", encode(variation));
    flag.put("doLog", true);
    return MAPPER.writeValueAsBytes(config);
  }

  private static String encode(String value) {
    return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }
}
