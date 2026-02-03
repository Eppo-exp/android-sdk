package cloud.eppo.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Application;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import cloud.eppo.android.cache.LRUAssignmentCache;
import cloud.eppo.android.dto.BanditResult;
import cloud.eppo.android.exceptions.MissingApiKeyException;
import cloud.eppo.android.exceptions.MissingSubjectKeyException;
import cloud.eppo.android.util.ObfuscationUtils;
import cloud.eppo.api.Attributes;
import cloud.eppo.api.EppoValue;
import cloud.eppo.api.IAssignmentCache;
import cloud.eppo.logging.Assignment;
import cloud.eppo.logging.AssignmentLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class EppoPrecomputedClientTest {

  private static final String TEST_API_KEY = "test-api-key";
  private static final String TEST_SUBJECT_KEY = "test-subject-123";
  private static final String TEST_SALT = "test-salt";
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private Application application;

  @Mock AssignmentLogger mockAssignmentLogger;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    application = ApplicationProvider.getApplicationContext();
  }

  /**
   * Creates a mock precomputed configuration response. The MD5 hashes are computed as: MD5(salt +
   * flagKey)
   */
  private String getMockPrecomputedResponse() {
    // Compute MD5 hashes for the flag keys with the salt
    String stringFlagHash = ObfuscationUtils.md5Hex("string_flag", TEST_SALT);
    String boolFlagHash = ObfuscationUtils.md5Hex("bool_flag", TEST_SALT);
    String intFlagHash = ObfuscationUtils.md5Hex("int_flag", TEST_SALT);
    String numericFlagHash = ObfuscationUtils.md5Hex("numeric_flag", TEST_SALT);
    String jsonFlagHash = ObfuscationUtils.md5Hex("json_flag", TEST_SALT);

    // Base64 encoded values:
    // "test-string" = dGVzdC1zdHJpbmc=
    // "true" = dHJ1ZQ==
    // "42" = NDI=
    // "3.14159" = My4xNDE1OQ==
    // {"key":"value"} = eyJrZXkiOiJ2YWx1ZSJ9
    // "allocation-1" = YWxsb2NhdGlvbi0x
    // "variant-a" = dmFyaWFudC1h

    return "{\n"
        + "  \"format\": \"PRECOMPUTED\",\n"
        + "  \"obfuscated\": true,\n"
        + "  \"createdAt\": \"2024-01-20T12:00:00.000Z\",\n"
        + "  \"environment\": { \"name\": \"Test\" },\n"
        + "  \"salt\": \""
        + TEST_SALT
        + "\",\n"
        + "  \"flags\": {\n"
        + "    \""
        + stringFlagHash
        + "\": {\n"
        + "      \"allocationKey\": \"YWxsb2NhdGlvbi0x\",\n"
        + "      \"variationKey\": \"dmFyaWFudC1h\",\n"
        + "      \"variationType\": \"STRING\",\n"
        + "      \"variationValue\": \"dGVzdC1zdHJpbmc=\",\n"
        + "      \"doLog\": true,\n"
        + "      \"extraLogging\": {}\n"
        + "    },\n"
        + "    \""
        + boolFlagHash
        + "\": {\n"
        + "      \"allocationKey\": \"YWxsb2NhdGlvbi0x\",\n"
        + "      \"variationKey\": \"dmFyaWFudC1h\",\n"
        + "      \"variationType\": \"BOOLEAN\",\n"
        + "      \"variationValue\": \"dHJ1ZQ==\",\n"
        + "      \"doLog\": true,\n"
        + "      \"extraLogging\": {}\n"
        + "    },\n"
        + "    \""
        + intFlagHash
        + "\": {\n"
        + "      \"allocationKey\": \"YWxsb2NhdGlvbi0x\",\n"
        + "      \"variationKey\": \"dmFyaWFudC1h\",\n"
        + "      \"variationType\": \"INTEGER\",\n"
        + "      \"variationValue\": \"NDI=\",\n"
        + "      \"doLog\": true,\n"
        + "      \"extraLogging\": {}\n"
        + "    },\n"
        + "    \""
        + numericFlagHash
        + "\": {\n"
        + "      \"allocationKey\": \"YWxsb2NhdGlvbi0x\",\n"
        + "      \"variationKey\": \"dmFyaWFudC1h\",\n"
        + "      \"variationType\": \"NUMERIC\",\n"
        + "      \"variationValue\": \"My4xNDE1OQ==\",\n"
        + "      \"doLog\": true,\n"
        + "      \"extraLogging\": {}\n"
        + "    },\n"
        + "    \""
        + jsonFlagHash
        + "\": {\n"
        + "      \"allocationKey\": \"YWxsb2NhdGlvbi0x\",\n"
        + "      \"variationKey\": \"dmFyaWFudC1h\",\n"
        + "      \"variationType\": \"JSON\",\n"
        + "      \"variationValue\": \"eyJrZXkiOiJ2YWx1ZSJ9\",\n"
        + "      \"doLog\": true,\n"
        + "      \"extraLogging\": {}\n"
        + "    }\n"
        + "  },\n"
        + "  \"bandits\": {}\n"
        + "}";
  }

  private EppoPrecomputedClient initializeClientOffline(
      AssignmentLogger assignmentLogger, IAssignmentCache cache) {
    byte[] configBytes = getMockPrecomputedResponse().getBytes(StandardCharsets.UTF_8);

    return new EppoPrecomputedClient.Builder(TEST_API_KEY, application)
        .subjectKey(TEST_SUBJECT_KEY)
        .offlineMode(true)
        .initialConfiguration(configBytes)
        .assignmentLogger(assignmentLogger)
        .assignmentCache(cache)
        .forceReinitialize(true)
        .buildAndInit();
  }

  @Test
  public void testBuilderRequiresApiKey() {
    assertThrows(
        MissingApiKeyException.class,
        () ->
            new EppoPrecomputedClient.Builder("", application)
                .subjectKey(TEST_SUBJECT_KEY)
                .buildAndInit());
  }

  @Test
  public void testBuilderRequiresSubjectKey() {
    assertThrows(
        MissingSubjectKeyException.class,
        () ->
            new EppoPrecomputedClient.Builder(TEST_API_KEY, application)
                .offlineMode(true)
                .buildAndInit());
  }

  @Test
  public void testOfflineModeWithInitialConfiguration() {
    EppoPrecomputedClient client = initializeClientOffline(null, null);
    assertNotNull(client);
  }

  @Test
  public void testEmptyFlagKeyReturnsDefault() {
    EppoPrecomputedClient client = initializeClientOffline(null, null);

    assertEquals("default", client.getStringAssignment("", "default"));
    assertEquals("default", client.getStringAssignment(null, "default"));
  }

  @Test
  public void testTypeMismatchReturnsDefault() {
    EppoPrecomputedClient client = initializeClientOffline(null, null);

    // string_flag is STRING type, requesting boolean should return default
    assertFalse(client.getBooleanAssignment("string_flag", false));

    // bool_flag is BOOLEAN type, requesting string should return default
    assertEquals("default", client.getStringAssignment("bool_flag", "default"));
  }

  @Test
  public void testAssignmentLogging() {
    AssignmentLogger mockLogger = mock(AssignmentLogger.class);
    EppoPrecomputedClient client = initializeClientOffline(mockLogger, null);

    // Make an assignment request
    client.getStringAssignment("string_flag", "default");

    // Verify logger was called
    ArgumentCaptor<Assignment> captor = ArgumentCaptor.forClass(Assignment.class);
    verify(mockLogger, times(1)).logAssignment(captor.capture());

    Assignment logged = captor.getValue();
    assertEquals(TEST_SUBJECT_KEY, logged.getSubject());
    assertEquals("string_flag", logged.getFeatureFlag());
    assertEquals("allocation-1", logged.getAllocation());
    assertEquals("variant-a", logged.getVariation());
    assertNotNull(logged.getMetaData());
    assertEquals("true", logged.getMetaData().get("obfuscated"));
    assertEquals("android", logged.getMetaData().get("sdkLanguage"));
  }

  @Test
  public void testAssignmentDeduplicationWithCache() {
    AssignmentLogger mockLogger = mock(AssignmentLogger.class);
    IAssignmentCache cache = new LRUAssignmentCache(100);

    EppoPrecomputedClient client = initializeClientOffline(mockLogger, cache);

    // Make the same assignment request twice
    client.getStringAssignment("string_flag", "default");
    client.getStringAssignment("string_flag", "default");

    // Logger should only be called once due to cache deduplication
    verify(mockLogger, times(1)).logAssignment(org.mockito.ArgumentMatchers.any());
  }

  @Test
  public void testBanditResultDefaultValue() {
    EppoPrecomputedClient client = initializeClientOffline(null, null);

    BanditResult result = client.getBanditAction("unknown_bandit", "default_variation");
    assertEquals("default_variation", result.getVariation());
    assertNull(result.getAction());
  }

  @Test
  public void testSubjectAttributes() {
    Attributes attributes = new Attributes();
    attributes.put("age", EppoValue.valueOf(25));
    attributes.put("country", EppoValue.valueOf("US"));

    byte[] configBytes = getMockPrecomputedResponse().getBytes(StandardCharsets.UTF_8);

    EppoPrecomputedClient client =
        new EppoPrecomputedClient.Builder(TEST_API_KEY, application)
            .subjectKey(TEST_SUBJECT_KEY)
            .subjectAttributes(attributes)
            .offlineMode(true)
            .initialConfiguration(configBytes)
            .forceReinitialize(true)
            .buildAndInit();

    assertNotNull(client);
    // Assignments should still work
    assertEquals("test-string", client.getStringAssignment("string_flag", "default"));
  }

  @Test
  public void testPollingCanBePausedAndResumed() {
    EppoPrecomputedClient client = initializeClientOffline(null, null);

    // Start polling manually
    client.startPolling(60000, 6000);

    // Pause, resume, and stop should not throw
    client.pausePolling();
    client.resumePolling();
    client.stopPolling();
  }

  @Test
  public void testGracefulModeReturnsDefaultsWithNoConfig() {
    // Initialize without any configuration
    EppoPrecomputedClient client =
        new EppoPrecomputedClient.Builder(TEST_API_KEY, application)
            .subjectKey(TEST_SUBJECT_KEY)
            .offlineMode(true)
            .isGracefulMode(true)
            .forceReinitialize(true)
            .buildAndInit();

    // Should return defaults when no configuration is available
    assertEquals("default", client.getStringAssignment("any_flag", "default"));
    assertFalse(client.getBooleanAssignment("any_flag", false));
    assertEquals(0, client.getIntegerAssignment("any_flag", 0));
    assertEquals(0.0, client.getNumericAssignment("any_flag", 0.0), 0.001);
  }

  // =============================================
  // Tests using sdk-test-data precomputed files
  // =============================================

  /**
   * Loads the precomputed test data from sdk-test-data and extracts the response JSON. The file
   * format is: { "version": 1, "precomputed": { "response": "escaped-json-string", ... } }
   */
  private byte[] loadPrecomputedTestData() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    InputStream inputStream = context.getAssets().open("precomputed-v1.json");
    byte[] bytes = new byte[inputStream.available()];
    inputStream.read(bytes);
    inputStream.close();

    // Parse the wrapper and extract the response string
    JsonNode wrapper = objectMapper.readTree(bytes);
    String responseJson = wrapper.get("precomputed").get("response").asText();
    return responseJson.getBytes(StandardCharsets.UTF_8);
  }

  private EppoPrecomputedClient initializeClientWithTestData(AssignmentLogger logger)
      throws Exception {
    byte[] configBytes = loadPrecomputedTestData();

    return new EppoPrecomputedClient.Builder(TEST_API_KEY, application)
        .subjectKey("test-subject-key")
        .offlineMode(true)
        .initialConfiguration(configBytes)
        .assignmentLogger(logger)
        .forceReinitialize(true)
        .buildAndInit();
  }

  @Test
  public void testSdkTestData_StringFlag() throws Exception {
    EppoPrecomputedClient client = initializeClientWithTestData(null);

    String result = client.getStringAssignment("string-flag", "default");
    assertEquals("red", result);
  }

  @Test
  public void testSdkTestData_BooleanFlag() throws Exception {
    EppoPrecomputedClient client = initializeClientWithTestData(null);

    boolean result = client.getBooleanAssignment("boolean-flag", false);
    assertTrue(result);
  }

  @Test
  public void testSdkTestData_IntegerFlag() throws Exception {
    EppoPrecomputedClient client = initializeClientWithTestData(null);

    int result = client.getIntegerAssignment("integer-flag", 0);
    assertEquals(42, result);
  }

  @Test
  public void testSdkTestData_NumericFlag() throws Exception {
    EppoPrecomputedClient client = initializeClientWithTestData(null);

    double result = client.getNumericAssignment("numeric-flag", 0.0);
    assertEquals(3.14, result, 0.001);
  }

  @Test
  public void testSdkTestData_JsonFlag() throws Exception {
    EppoPrecomputedClient client = initializeClientWithTestData(null);

    JsonNode defaultValue = objectMapper.readTree("{}");
    JsonNode result = client.getJSONAssignment("json-flag", defaultValue);

    assertNotNull(result);
    assertTrue(result.has("key"));
    assertEquals("value", result.get("key").asText());
    assertTrue(result.has("number"));
    assertEquals(123, result.get("number").asInt());
  }

  @Test
  public void testSdkTestData_StringFlagWithExtraLogging() throws Exception {
    AssignmentLogger mockLogger = mock(AssignmentLogger.class);
    EppoPrecomputedClient client = initializeClientWithTestData(mockLogger);

    String result = client.getStringAssignment("string-flag-with-extra-logging", "default");
    assertEquals("red", result);

    // Verify the assignment was logged with extra logging info
    ArgumentCaptor<Assignment> captor = ArgumentCaptor.forClass(Assignment.class);
    verify(mockLogger, times(1)).logAssignment(captor.capture());

    Assignment logged = captor.getValue();
    assertEquals("string-flag-with-extra-logging", logged.getFeatureFlag());
    // Extra logging should include holdout information
    assertNotNull(logged.getMetaData());
  }

  @Test
  public void testSdkTestData_NotABanditFlag() throws Exception {
    EppoPrecomputedClient client = initializeClientWithTestData(null);

    String result = client.getStringAssignment("not-a-bandit-flag", "default");
    assertEquals("control", result);
  }

  @Test
  public void testSdkTestData_BanditAction() throws Exception {
    EppoPrecomputedClient client = initializeClientWithTestData(null);

    // string-flag has a bandit associated with it
    BanditResult result = client.getBanditAction("string-flag", "default");

    assertNotNull(result);
    assertEquals("red", result.getVariation());
    assertEquals("show_red_button", result.getAction());
  }

  @Test
  public void testSdkTestData_BanditActionWithExtraLogging() throws Exception {
    EppoPrecomputedClient client = initializeClientWithTestData(null);

    // string-flag-with-extra-logging has a bandit
    BanditResult result = client.getBanditAction("string-flag-with-extra-logging", "default");

    assertNotNull(result);
    assertEquals("red", result.getVariation());
    assertEquals("featured_content", result.getAction());
  }

  @Test
  public void testSdkTestData_UnknownFlagReturnsDefault() throws Exception {
    EppoPrecomputedClient client = initializeClientWithTestData(null);

    assertEquals("default", client.getStringAssignment("non-existent-flag", "default"));
    assertFalse(client.getBooleanAssignment("non-existent-flag", false));
    assertEquals(99, client.getIntegerAssignment("non-existent-flag", 99));
    assertEquals(1.5, client.getNumericAssignment("non-existent-flag", 1.5), 0.001);
  }

  @Test
  public void testResumePollingAfterStop() {
    EppoPrecomputedClient client = initializeClientOffline(null, null);

    // Start polling, then stop (which shuts down the executor)
    client.startPolling(60000, 6000);
    client.stopPolling();

    // Resume should recreate the executor and not throw
    client.resumePolling();
    client.stopPolling();
  }

  @Test
  public void testNonGracefulModeCanBeConfigured() {
    // Initialize without configuration and with graceful mode disabled
    EppoPrecomputedClient client =
        new EppoPrecomputedClient.Builder(TEST_API_KEY, application)
            .subjectKey(TEST_SUBJECT_KEY)
            .offlineMode(true)
            .isGracefulMode(false)
            .forceReinitialize(true)
            .buildAndInit();

    // Client should be created successfully even in non-graceful mode
    // When no config is loaded, assignments return defaults (salt is null)
    assertNotNull(client);
    assertEquals("default", client.getStringAssignment("any_flag", "default"));
  }
}
