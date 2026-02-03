package cloud.eppo.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import cloud.eppo.android.dto.PrecomputedBandit;
import cloud.eppo.android.dto.PrecomputedConfigurationResponse;
import cloud.eppo.android.dto.PrecomputedFlag;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class PrecomputedConfigurationResponseTest {

  @Test
  public void testDeserializeBasicResponse() {
    String json =
        "{\n"
            + "  \"format\": \"PRECOMPUTED\",\n"
            + "  \"obfuscated\": true,\n"
            + "  \"createdAt\": \"2024-01-20T12:00:00.000Z\",\n"
            + "  \"environment\": { \"name\": \"Production\" },\n"
            + "  \"salt\": \"random-salt-value\",\n"
            + "  \"flags\": {},\n"
            + "  \"bandits\": {}\n"
            + "}";

    PrecomputedConfigurationResponse response =
        PrecomputedConfigurationResponse.fromBytes(json.getBytes(StandardCharsets.UTF_8));

    assertEquals("PRECOMPUTED", response.getFormat());
    assertTrue(response.isObfuscated());
    assertEquals("2024-01-20T12:00:00.000Z", response.getCreatedAt());
    assertEquals("Production", response.getEnvironmentName());
    assertEquals("random-salt-value", response.getSalt());
    assertTrue(response.getFlags().isEmpty());
    assertTrue(response.getBandits().isEmpty());
  }

  @Test
  public void testDeserializeWithFlags() {
    String json =
        "{\n"
            + "  \"format\": \"PRECOMPUTED\",\n"
            + "  \"obfuscated\": true,\n"
            + "  \"createdAt\": \"2024-01-20T12:00:00.000Z\",\n"
            + "  \"salt\": \"test-salt\",\n"
            + "  \"flags\": {\n"
            + "    \"a1b2c3d4\": {\n"
            + "      \"allocationKey\": \"YWxsb2NhdGlvbi0x\",\n"
            + "      \"variationKey\": \"dmFyaWFudC1h\",\n"
            + "      \"variationType\": \"STRING\",\n"
            + "      \"variationValue\": \"dGVzdC12YWx1ZQ==\",\n"
            + "      \"doLog\": true,\n"
            + "      \"extraLogging\": {\"key\": \"dmFsdWU=\"}\n"
            + "    }\n"
            + "  },\n"
            + "  \"bandits\": {}\n"
            + "}";

    PrecomputedConfigurationResponse response =
        PrecomputedConfigurationResponse.fromBytes(json.getBytes(StandardCharsets.UTF_8));

    assertEquals(1, response.getFlags().size());
    PrecomputedFlag flag = response.getFlags().get("a1b2c3d4");
    assertNotNull(flag);
    assertEquals("YWxsb2NhdGlvbi0x", flag.getAllocationKey());
    assertEquals("dmFyaWFudC1h", flag.getVariationKey());
    assertEquals("STRING", flag.getVariationType());
    assertEquals("dGVzdC12YWx1ZQ==", flag.getVariationValue());
    assertTrue(flag.isDoLog());
    assertNotNull(flag.getExtraLogging());
    assertEquals("dmFsdWU=", flag.getExtraLogging().get("key"));
  }

  @Test
  public void testDeserializeWithBandits() {
    String json =
        "{\n"
            + "  \"format\": \"PRECOMPUTED\",\n"
            + "  \"obfuscated\": true,\n"
            + "  \"createdAt\": \"2024-01-20T12:00:00.000Z\",\n"
            + "  \"salt\": \"test-salt\",\n"
            + "  \"flags\": {},\n"
            + "  \"bandits\": {\n"
            + "    \"b1c2d3e4\": {\n"
            + "      \"banditKey\": \"YmFuZGl0LTE=\",\n"
            + "      \"action\": \"YWN0aW9uLTE=\",\n"
            + "      \"modelVersion\": \"djEuMA==\",\n"
            + "      \"actionNumericAttributes\": {\"score\": \"MC41\"},\n"
            + "      \"actionCategoricalAttributes\": {\"category\": \"Y2F0LWE=\"},\n"
            + "      \"actionProbability\": 0.75,\n"
            + "      \"optimalityGap\": 0.05\n"
            + "    }\n"
            + "  }\n"
            + "}";

    PrecomputedConfigurationResponse response =
        PrecomputedConfigurationResponse.fromBytes(json.getBytes(StandardCharsets.UTF_8));

    assertEquals(1, response.getBandits().size());
    PrecomputedBandit bandit = response.getBandits().get("b1c2d3e4");
    assertNotNull(bandit);
    assertEquals("YmFuZGl0LTE=", bandit.getBanditKey());
    assertEquals("YWN0aW9uLTE=", bandit.getAction());
    assertEquals("djEuMA==", bandit.getModelVersion());
    assertEquals(0.75, bandit.getActionProbability(), 0.001);
    assertEquals(0.05, bandit.getOptimalityGap(), 0.001);
    assertEquals("MC41", bandit.getActionNumericAttributes().get("score"));
    assertEquals("Y2F0LWE=", bandit.getActionCategoricalAttributes().get("category"));
  }

  @Test
  public void testDeserializeWithNullEnvironment() {
    String json =
        "{\n"
            + "  \"format\": \"PRECOMPUTED\",\n"
            + "  \"obfuscated\": true,\n"
            + "  \"createdAt\": \"2024-01-20T12:00:00.000Z\",\n"
            + "  \"salt\": \"test-salt\",\n"
            + "  \"flags\": {},\n"
            + "  \"bandits\": {}\n"
            + "}";

    PrecomputedConfigurationResponse response =
        PrecomputedConfigurationResponse.fromBytes(json.getBytes(StandardCharsets.UTF_8));

    assertNull(response.getEnvironmentName());
  }

  @Test
  public void testDeserializeWithStringEnvironment() {
    String json =
        "{\n"
            + "  \"format\": \"PRECOMPUTED\",\n"
            + "  \"obfuscated\": true,\n"
            + "  \"createdAt\": \"2024-01-20T12:00:00.000Z\",\n"
            + "  \"environment\": \"Production\",\n"
            + "  \"salt\": \"test-salt\",\n"
            + "  \"flags\": {},\n"
            + "  \"bandits\": {}\n"
            + "}";

    PrecomputedConfigurationResponse response =
        PrecomputedConfigurationResponse.fromBytes(json.getBytes(StandardCharsets.UTF_8));

    assertEquals("Production", response.getEnvironmentName());
  }

  @Test
  public void testEmptyConfiguration() {
    PrecomputedConfigurationResponse empty = PrecomputedConfigurationResponse.empty();

    assertEquals("PRECOMPUTED", empty.getFormat());
    assertTrue(empty.isObfuscated());
    assertEquals("", empty.getCreatedAt());
    assertEquals("", empty.getSalt());
    assertTrue(empty.getFlags().isEmpty());
    assertTrue(empty.getBandits().isEmpty());
  }

  @Test
  public void testRoundTripSerialization() {
    String json =
        "{\n"
            + "  \"format\": \"PRECOMPUTED\",\n"
            + "  \"obfuscated\": true,\n"
            + "  \"createdAt\": \"2024-01-20T12:00:00.000Z\",\n"
            + "  \"salt\": \"test-salt\",\n"
            + "  \"flags\": {\n"
            + "    \"flag1\": {\n"
            + "      \"variationType\": \"STRING\",\n"
            + "      \"variationValue\": \"dGVzdA==\",\n"
            + "      \"doLog\": false\n"
            + "    }\n"
            + "  },\n"
            + "  \"bandits\": {}\n"
            + "}";

    PrecomputedConfigurationResponse original =
        PrecomputedConfigurationResponse.fromBytes(json.getBytes(StandardCharsets.UTF_8));

    // Serialize and deserialize
    byte[] serialized = original.toBytes();
    PrecomputedConfigurationResponse deserialized =
        PrecomputedConfigurationResponse.fromBytes(serialized);

    assertEquals(original.getFormat(), deserialized.getFormat());
    assertEquals(original.isObfuscated(), deserialized.isObfuscated());
    assertEquals(original.getSalt(), deserialized.getSalt());
    assertEquals(original.getFlags().size(), deserialized.getFlags().size());

    PrecomputedFlag originalFlag = original.getFlags().get("flag1");
    PrecomputedFlag deserializedFlag = deserialized.getFlags().get("flag1");
    assertEquals(originalFlag.getVariationType(), deserializedFlag.getVariationType());
    assertEquals(originalFlag.getVariationValue(), deserializedFlag.getVariationValue());
    assertEquals(originalFlag.isDoLog(), deserializedFlag.isDoLog());
  }

  @Test
  public void testFlagWithNullOptionalFields() {
    String json =
        "{\n"
            + "  \"format\": \"PRECOMPUTED\",\n"
            + "  \"obfuscated\": true,\n"
            + "  \"createdAt\": \"2024-01-20T12:00:00.000Z\",\n"
            + "  \"salt\": \"test-salt\",\n"
            + "  \"flags\": {\n"
            + "    \"flag1\": {\n"
            + "      \"variationType\": \"STRING\",\n"
            + "      \"variationValue\": \"dGVzdA==\",\n"
            + "      \"doLog\": false\n"
            + "    }\n"
            + "  },\n"
            + "  \"bandits\": {}\n"
            + "}";

    PrecomputedConfigurationResponse response =
        PrecomputedConfigurationResponse.fromBytes(json.getBytes(StandardCharsets.UTF_8));

    PrecomputedFlag flag = response.getFlags().get("flag1");
    assertNotNull(flag);
    assertNull(flag.getAllocationKey());
    assertNull(flag.getVariationKey());
    assertNull(flag.getExtraLogging());
    assertFalse(flag.isDoLog());
  }
}
