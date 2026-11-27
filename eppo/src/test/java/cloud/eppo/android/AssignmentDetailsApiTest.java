package cloud.eppo.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cloud.eppo.api.AllocationEvaluationCode;
import cloud.eppo.api.AssignmentDetails;
import cloud.eppo.api.Attributes;
import cloud.eppo.api.BanditActions;
import cloud.eppo.api.Configuration;
import cloud.eppo.api.EppoValue;
import cloud.eppo.api.EvaluationDetails;
import cloud.eppo.api.FlagEvaluationCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class AssignmentDetailsApiTest {
  private static final String UFC =
      "{\"format\":\"SERVER\",\"createdAt\":\"2026-10-14T00:00:00.000Z\","
          + "\"environment\":{\"name\":\"Test\"},\"banditReferences\":{},\"flags\":{"
          + "\"str_flag\":{\"key\":\"str_flag\",\"enabled\":true,\"variationType\":\"STRING\","
          + "\"totalShards\":10000,\"variations\":{\"us\":{\"key\":\"us\",\"value\":\"us-value\"},"
          + "\"other\":{\"key\":\"other\",\"value\":\"other-value\"}},\"allocations\":["
          + "{\"key\":\"us-only\",\"doLog\":true,\"rules\":[{\"conditions\":[{\"attribute\":"
          + "\"country\",\"operator\":\"ONE_OF\",\"value\":[\"US\"]}]}],"
          + "\"splits\":[{\"variationKey\":\"us\",\"shards\":[]}]},"
          + "{\"key\":\"everyone\",\"doLog\":true,"
          + "\"splits\":[{\"variationKey\":\"other\",\"shards\":[]}]}]},"
          + "\"json_flag\":{\"key\":\"json_flag\",\"enabled\":true,\"variationType\":\"JSON\","
          + "\"totalShards\":10000,\"variations\":{\"j\":{\"key\":\"j\",\"value\":\"{\\\"a\\\":1}\"}},"
          + "\"allocations\":[{\"key\":\"all\",\"doLog\":true,"
          + "\"splits\":[{\"variationKey\":\"j\",\"shards\":[]}]}]}}}";

  private EppoClient client;

  @Before
  public void setUp() throws Exception {
    JacksonConfigurationParser parser = new JacksonConfigurationParser();
    Configuration config =
        parser.buildConfig(
            parser.parseFlagConfig(UFC.getBytes(StandardCharsets.UTF_8)), null, null, null);
    client =
        new EppoClient.Builder("mock-api-key", RuntimeEnvironment.getApplication())
            .forceReinitialize(true)
            .offlineMode(true)
            .isGracefulMode(false)
            .assignmentCache(null)
            .initialConfiguration(CompletableFuture.completedFuture(config))
            .buildAndInitAsync()
            .get(10, TimeUnit.SECONDS);
  }

  private static Attributes country(String value) {
    Attributes attributes = new Attributes();
    attributes.put("country", EppoValue.valueOf(value));
    return attributes;
  }

  @Test
  public void stringDetailsReportMatchedAndUnmatchedAllocations() {
    AssignmentDetails<String> us =
        client.getStringAssignmentDetails("str_flag", "alice", country("US"), "default");
    EvaluationDetails details = us.getEvaluationDetails();
    assertEquals("us-value", us.getVariation());
    assertEquals(FlagEvaluationCode.MATCH, details.getFlagEvaluationCode());
    assertEquals("us", details.getVariationKey());
    assertEquals("us-only", details.getMatchedAllocation().getKey());
    assertEquals("everyone", details.getUnevaluatedAllocations().get(0).getKey());

    EvaluationDetails canada =
        client
            .getStringAssignmentDetails("str_flag", "bob", country("CA"), "default")
            .getEvaluationDetails();
    assertEquals("everyone", canada.getMatchedAllocation().getKey());
    assertEquals(
        AllocationEvaluationCode.FAILING_RULE,
        canada.getUnmatchedAllocations().get(0).getAllocationEvaluationCode());
  }

  @Test
  public void detailsReportErrorCodesJsonValuesAndBanditFallback() throws Exception {
    AssignmentDetails<Boolean> missing = client.getBooleanAssignmentDetails("nope", "alice", true);
    assertTrue(missing.getVariation());
    assertEquals(
        FlagEvaluationCode.FLAG_UNRECOGNIZED_OR_DISABLED,
        missing.getEvaluationDetails().getFlagEvaluationCode());

    AssignmentDetails<Integer> wrongType =
        client.getIntegerAssignmentDetails("str_flag", "alice", 7);
    assertEquals(Integer.valueOf(7), wrongType.getVariation());
    assertEquals(
        FlagEvaluationCode.TYPE_MISMATCH, wrongType.getEvaluationDetails().getFlagEvaluationCode());

    assertEquals(
        1,
        client
            .getJSONAssignmentDetails("json_flag", "alice", new ObjectMapper().readTree("{}"))
            .getVariation()
            .get("a")
            .asInt());

    AssignmentDetails<String> bandit =
        client.getBanditActionDetails(
            "str_flag",
            "alice",
            country("US"),
            new BanditActions(Collections.singleton("a1")),
            "default");
    assertEquals("us-value", bandit.getVariation());
    assertNull(bandit.getAction());
    assertEquals(FlagEvaluationCode.MATCH, bandit.getEvaluationDetails().getFlagEvaluationCode());
  }

  @Test
  public void gracefulModeAppliesToDetails() {
    assertThrows(RuntimeException.class, () -> client.getStringAssignment("str_flag", "", "d"));
    assertThrows(
        RuntimeException.class, () -> client.getStringAssignmentDetails("str_flag", "", "d"));
    client.setIsGracefulFailureMode(true);
    assertEquals("d", client.getStringAssignment("str_flag", "", "d"));
    assertEquals(
        FlagEvaluationCode.ASSIGNMENT_ERROR,
        client
            .getStringAssignmentDetails("str_flag", "", "d")
            .getEvaluationDetails()
            .getFlagEvaluationCode());
  }
}
