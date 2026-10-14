package cloud.eppo.android;

import static org.junit.Assert.assertEquals;

import cloud.eppo.android.dto.PrecomputedConfigurationResponse;
import cloud.eppo.android.util.ObfuscationUtils;
import cloud.eppo.api.Configuration;
import cloud.eppo.logging.Assignment;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
  public void standardClientLogsChangedAssignmentsAfterConfigurationChanges() throws Exception {
    ConfigurationStore store =
        new ConfigurationStore(RuntimeEnvironment.getApplication(), "assignment-logging-test");
    List<Assignment> assignments = new ArrayList<>();
    EppoClient client =
        new EppoClient.Builder("test-api-key", RuntimeEnvironment.getApplication())
            .forceReinitialize(true)
            .offlineMode(true)
            .pollingEnabled(false)
            .isGracefulMode(false)
            .configStore(store)
            .initialConfiguration(standardConfig("allocation-1", "A"))
            .assignmentLogger(assignments::add)
            .buildAndInitAsync()
            .get(10, TimeUnit.SECONDS);

    String[] variations = {"A", "B", "A", "A"};
    String[] allocations = {"allocation-1", "allocation-1", "allocation-1", "allocation-2"};
    for (int i = 0; i < variations.length; i++) {
      for (int repeat = 0; repeat < 2; repeat++) {
        store
            .saveConfiguration(
                new Configuration.Builder(standardConfig(allocations[i], variations[i])).build())
            .get(10, TimeUnit.SECONDS);
        assertEquals(variations[i], client.getStringAssignment(FLAG, SUBJECT, "default"));
        assertAssignment(assignments, i, allocations[i], variations[i]);
      }
    }
  }

  @Test
  public void precomputedClientLogsChangedAssignmentsAfterConfigurationChanges() throws Exception {
    PrecomputedConfigurationStore store =
        new PrecomputedConfigurationStore(
            RuntimeEnvironment.getApplication(), "assignment-logging-test");
    List<Assignment> assignments = new ArrayList<>();
    EppoPrecomputedClient client =
        new EppoPrecomputedClient.Builder("test-api-key", RuntimeEnvironment.getApplication())
            .subjectKey(SUBJECT)
            .forceReinitialize(true)
            .offlineMode(true)
            .pollingEnabled(false)
            .isGracefulMode(false)
            .configStore(store)
            .initialConfiguration(precomputedConfig("allocation-1", "A"))
            .assignmentLogger(assignments::add)
            .buildAndInitAsync()
            .get(10, TimeUnit.SECONDS);

    String[] variations = {"A", "B", "A", "A"};
    String[] allocations = {"allocation-1", "allocation-1", "allocation-1", "allocation-2"};
    for (int i = 0; i < variations.length; i++) {
      for (int repeat = 0; repeat < 2; repeat++) {
        store.setConfiguration(
            PrecomputedConfigurationResponse.fromBytes(
                precomputedConfig(allocations[i], variations[i])));
        assertEquals(variations[i], client.getStringAssignment(FLAG, "default"));
        assertAssignment(assignments, i, allocations[i], variations[i]);
      }
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
