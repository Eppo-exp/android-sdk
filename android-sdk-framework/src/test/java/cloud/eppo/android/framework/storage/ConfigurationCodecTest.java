package cloud.eppo.android.framework.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import cloud.eppo.api.Configuration;
import cloud.eppo.api.SerializableEppoConfiguration;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link ConfigurationCodec} and {@link ConfigurationCodec.Default}. */
@RunWith(RobolectricTestRunner.class)
public class ConfigurationCodecTest {

  private ConfigurationCodec<SerializableEppoConfiguration> codec;

  @Before
  public void setUp() {
    codec = new ConfigurationCodec.Default<>(SerializableEppoConfiguration.class);
  }

  @Test
  public void getContentType_returnsJavaSerializedObject() {
    assertEquals("application/x-java-serialized-object", codec.getContentType());
  }

  @Test
  public void toBytes_nullConfiguration_throwsIllegalArgumentException() {
    try {
      codec.toBytes(null);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(
          "Exception should mention null constraint", e.getMessage().contains("must not be null"));
    }
  }

  @Test
  public void fromBytes_nullBytes_throwsIllegalArgumentException() {
    try {
      codec.fromBytes(null);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(
          "Exception should mention null constraint", e.getMessage().contains("must not be null"));
    }
  }

  @Test
  public void fromBytes_invalidData_throwsRuntimeException() {
    byte[] invalid = "not java serialized data".getBytes(StandardCharsets.UTF_8);
    try {
      codec.fromBytes(invalid);
      fail("Expected RuntimeException");
    } catch (RuntimeException e) {
      assertTrue(
          "Exception should mention deserialization failure",
          e.getMessage().contains("deserialize"));
    }
  }

  @Test
  public void fromBytes_emptyArray_throwsRuntimeException() {
    try {
      codec.fromBytes(new byte[0]);
      fail("Expected RuntimeException");
    } catch (RuntimeException e) {
      assertNotNull("Exception message should not be null", e.getMessage());
      assertTrue(
          "Exception should mention deserialization failure",
          e.getMessage().contains("deserialize"));
    }
  }

  @Test
  public void fromBytes_javaSerializedWrongType_throwsRuntimeException() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
      oos.writeObject("not a Configuration");
    }
    byte[] bytes = baos.toByteArray();
    try {
      codec.fromBytes(bytes);
      fail("Expected RuntimeException (deserialized object is not correct type)");
    } catch (RuntimeException e) {
      assertTrue(
          "Exception should mention type mismatch",
          e.getMessage().contains("not a SerializableEppoConfiguration"));
    }
  }

  @Test
  public void roundTrip_serializeAndDeserialize_succeeds() {
    SerializableEppoConfiguration original =
        (SerializableEppoConfiguration) Configuration.emptyConfig();
    byte[] bytes = codec.toBytes(original);
    assertNotNull(bytes);
    assertTrue(bytes.length > 0);

    SerializableEppoConfiguration deserialized = codec.fromBytes(bytes);
    assertNotNull(deserialized);
    assertEquals("Deserialized should equal original", original, deserialized);
  }
}
