package cloud.eppo.android;

import static cloud.eppo.android.ConfigCacheFile.cacheFileName;
import static cloud.eppo.android.util.Utils.logTag;
import static cloud.eppo.android.util.Utils.safeCacheKey;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.res.AssetManager;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import cloud.eppo.BaseEppoClient;
import cloud.eppo.EppoHttpClient;
import cloud.eppo.android.helpers.AssignmentTestCase;
import cloud.eppo.android.helpers.AssignmentTestCaseDeserializer;
import cloud.eppo.android.helpers.SubjectAssignment;
import cloud.eppo.android.helpers.TestCaseValue;
import cloud.eppo.api.Attributes;
import cloud.eppo.api.Configuration;
import cloud.eppo.api.EppoValue;
import cloud.eppo.logging.Assignment;
import cloud.eppo.logging.AssignmentLogger;
import cloud.eppo.ufc.dto.FlagConfig;
import cloud.eppo.ufc.dto.FlagConfigResponse;
import cloud.eppo.ufc.dto.VariationType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class EppoClientTest {
  private static final String TAG = logTag(EppoClient.class);
  private static final String DUMMY_API_KEY = "mock-api-key";
  private static final String DUMMY_OTHER_API_KEY = "another-mock-api-key";
  private static final String TEST_HOST =
      "https://us-central1-eppo-qa.cloudfunctions.net/serveGitHubRacTestFile";
  private static final String INVALID_HOST = "https://thisisabaddomainforthistest.com";
  private final ObjectMapper mapper = new ObjectMapper().registerModule(module());
  private AssignmentLogger mockAssignmentLogger;

  private void initClient(
      String host,
      boolean throwOnCallbackError,
      boolean shouldDeleteCacheFiles,
      boolean isGracefulMode,
      boolean obfuscateConfig,
      @Nullable EppoHttpClient httpClientOverride,
      @Nullable ConfigurationStore configurationStoreOverride,
      String apiKey,
      boolean offlineMode) {
    if (shouldDeleteCacheFiles) {
      clearCacheFile(apiKey);
    }
    mockAssignmentLogger = mock(AssignmentLogger.class);

    setBaseClientHttpClientOverrideField(httpClientOverride);

    CompletableFuture<Void> futureClient =
        new EppoClient.Builder(apiKey, ApplicationProvider.getApplicationContext())
            .isGracefulMode(isGracefulMode)
            .host(host)
            .assignmentLogger(mockAssignmentLogger)
            .obfuscateConfig(obfuscateConfig)
            .forceReinitialize(true)
            .offlineMode(offlineMode)
            .configStore(configurationStoreOverride)
            .buildAndInitAsync()
            .thenAccept(
                client -> {
                  Log.i(TAG, "Test client async buildAndInit completed.");
                })
            .exceptionally(
                error -> {
                  Log.e(TAG, "Test client async buildAndInit error" + error.getMessage(), error);
                  if (throwOnCallbackError) {
                    throw new RuntimeException(
                        "Unable to initialize: " + error.getMessage(), error);
                  }
                  return null;
                });

    // Wait for initialization to succeed or fail, up to 10 seconds, before continuing
    try {
      futureClient.get(10, TimeUnit.SECONDS);
      Log.d(TAG, "Test client initialized");
    } catch (ExecutionException | TimeoutException | InterruptedException e) {

      throw new RuntimeException("Client initialization not complete within timeout", e);
    }
  }

  @Before
  public void cleanUp() {
    // Clear any caches
    String[] apiKeys = {DUMMY_API_KEY, DUMMY_OTHER_API_KEY};
    for (String apiKey : apiKeys) {
      clearCacheFile(apiKey);
    }
    setBaseClientHttpClientOverrideField(null);
  }

  private void clearCacheFile(String apiKey) {
    String cacheFileNameSuffix = safeCacheKey(apiKey);
    ConfigCacheFile cacheFile =
        new ConfigCacheFile(ApplicationProvider.getApplicationContext(), cacheFileNameSuffix);
    cacheFile.delete();
  }

  @Test
  public void testUnobfuscatedAssignments() {
    initClient(TEST_HOST, true, true, false, false, null, null, DUMMY_API_KEY, false);
    runTestCases();
  }

  @Test
  public void testAssignments() {
    initClient(TEST_HOST, true, true, false, true, null, null, DUMMY_API_KEY, false);
    runTestCases();
  }

  @Test
  public void testErrorGracefulModeOn() throws JSONException, JsonProcessingException {
    initClient(TEST_HOST, false, true, true, true, null, null, DUMMY_API_KEY, false);

    EppoClient realClient = EppoClient.getInstance();
    EppoClient spyClient = spy(realClient);
    doThrow(new RuntimeException("Exception thrown by mock"))
        .when(spyClient)
        .getTypedAssignment(
            anyString(),
            anyString(),
            any(Attributes.class),
            any(EppoValue.class),
            any(VariationType.class));

    assertTrue(spyClient.getBooleanAssignment("experiment1", "subject1", true));
    assertFalse(spyClient.getBooleanAssignment("experiment1", "subject1", new Attributes(), false));

    assertEquals(10, spyClient.getIntegerAssignment("experiment1", "subject1", 10));
    assertEquals(0, spyClient.getIntegerAssignment("experiment1", "subject1", new Attributes(), 0));

    assertEquals(1.2345, spyClient.getDoubleAssignment("experiment1", "subject1", 1.2345), 0.0001);
    assertEquals(
        0.0,
        spyClient.getDoubleAssignment("experiment1", "subject1", new Attributes(), 0.0),
        0.0001);

    assertEquals("default", spyClient.getStringAssignment("experiment1", "subject1", "default"));
    assertEquals(
        "", spyClient.getStringAssignment("experiment1", "subject1", new Attributes(), ""));

    assertEquals(
        mapper.readTree("{\"a\": 1, \"b\": false}").toString(),
        spyClient
            .getJSONAssignment(
                "subject1", "experiment1", mapper.readTree("{\"a\": 1, \"b\": false}"))
            .toString());

    assertEquals(
        "{\"a\": 1, \"b\": false}",
        spyClient.getJSONStringAssignment("subject1", "experiment1", "{\"a\": 1, \"b\": false}"));

    assertEquals(
        mapper.readTree("{}").toString(),
        spyClient
            .getJSONAssignment("subject1", "experiment1", new Attributes(), mapper.readTree("{}"))
            .toString());
  }

  @Test
  public void testErrorGracefulModeOff() {
    initClient(TEST_HOST, false, true, false, true, null, null, DUMMY_API_KEY, false);

    EppoClient realClient = EppoClient.getInstance();
    EppoClient spyClient = spy(realClient);
    doThrow(new RuntimeException("Exception thrown by mock"))
        .when(spyClient)
        .getTypedAssignment(
            anyString(),
            anyString(),
            any(Attributes.class),
            any(EppoValue.class),
            any(VariationType.class));

    assertThrows(
        RuntimeException.class,
        () -> spyClient.getBooleanAssignment("experiment1", "subject1", true));
    assertThrows(
        RuntimeException.class,
        () -> spyClient.getBooleanAssignment("experiment1", "subject1", new Attributes(), false));

    assertThrows(
        RuntimeException.class,
        () -> spyClient.getIntegerAssignment("experiment1", "subject1", 10));
    assertThrows(
        RuntimeException.class,
        () -> spyClient.getIntegerAssignment("experiment1", "subject1", new Attributes(), 0));

    assertThrows(
        RuntimeException.class,
        () -> spyClient.getDoubleAssignment("experiment1", "subject1", 1.2345));
    assertThrows(
        RuntimeException.class,
        () -> spyClient.getDoubleAssignment("experiment1", "subject1", new Attributes(), 0.0));

    assertThrows(
        RuntimeException.class,
        () -> spyClient.getStringAssignment("experiment1", "subject1", "default"));
    assertThrows(
        RuntimeException.class,
        () -> spyClient.getStringAssignment("experiment1", "subject1", new Attributes(), ""));

    assertThrows(
        RuntimeException.class,
        () ->
            spyClient.getJSONAssignment(
                "subject1", "experiment1", mapper.readTree("{\"a\": 1, \"b\": false}")));
    assertThrows(
        RuntimeException.class,
        () ->
            spyClient.getJSONAssignment(
                "subject1", "experiment1", new Attributes(), mapper.readTree("{}")));
  }

  private void runTestCases() {
    try {
      int testsRan = 0;
      AssetManager assets = ApplicationProvider.getApplicationContext().getAssets();
      String[] testFiles = assets.list("tests");
      assertNotNull(testFiles);
      for (String path : testFiles) {
        testsRan += runTestCaseFileStream(assets.open("tests/" + path));
      }
      System.out.println("We ran this many tests: " + testsRan);
      assertTrue("Did not run any test cases", testsRan > 0);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testCachedConfigurations() {
    // First initialize successfully
    initClient(
        TEST_HOST,
        true,
        true,
        false,
        false,
        null,
        null,
        DUMMY_API_KEY,
        false); // ensure cache is populated

    // wait for a bit since cache file is written asynchronously
    waitForPopulatedCache();

    // Then reinitialize with a bad host so we know it's using the cached UFC built from the first
    // initialization
    initClient(
        INVALID_HOST,
        false,
        false,
        false,
        false,
        null,
        null,
        DUMMY_API_KEY,
        false); // invalid host to force to use cache

    runTestCases();
  }

  private int runTestCaseFileStream(InputStream testCaseStream) throws IOException, JSONException {
    String json = IOUtils.toString(testCaseStream, Charsets.toCharset("UTF8"));
    AssignmentTestCase testCase = mapper.readValue(json, AssignmentTestCase.class);
    String flagKey = testCase.getFlag();
    TestCaseValue defaultValue = testCase.getDefaultValue();
    EppoClient eppoClient = EppoClient.getInstance();

    for (SubjectAssignment subjectAssignment : testCase.getSubjects()) {
      String subjectKey = subjectAssignment.getSubjectKey();
      Attributes subjectAttributes = subjectAssignment.getAttributes();

      // Depending on the variation type, we will need to change which assignment method we call and
      // how we get the default value
      switch (testCase.getVariationType()) {
        case BOOLEAN:
          boolean boolAssignment =
              eppoClient.getBooleanAssignment(
                  flagKey, subjectKey, subjectAttributes, defaultValue.booleanValue());
          assertAssignment(flagKey, subjectAssignment, boolAssignment);
          break;
        case INTEGER:
          int intAssignment =
              eppoClient.getIntegerAssignment(
                  flagKey,
                  subjectKey,
                  subjectAttributes,
                  Double.valueOf(defaultValue.doubleValue()).intValue());
          assertAssignment(flagKey, subjectAssignment, intAssignment);
          break;
        case NUMERIC:
          double doubleAssignment =
              eppoClient.getDoubleAssignment(
                  flagKey, subjectKey, subjectAttributes, defaultValue.doubleValue());
          assertAssignment(flagKey, subjectAssignment, doubleAssignment);
          break;
        case STRING:
          String stringAssignment =
              eppoClient.getStringAssignment(
                  flagKey, subjectKey, subjectAttributes, defaultValue.stringValue());
          assertAssignment(flagKey, subjectAssignment, stringAssignment);
          break;
        case JSON:
          JsonNode jsonAssignment =
              eppoClient.getJSONAssignment(
                  flagKey,
                  subjectKey,
                  subjectAttributes,
                  mapper.readTree(defaultValue.jsonValue().toString()));
          assertAssignment(flagKey, subjectAssignment, jsonAssignment);
          break;
        default:
          throw new UnsupportedOperationException(
              "Unexpected variation type "
                  + testCase.getVariationType()
                  + " for "
                  + flagKey
                  + " test case");
      }
    }

    return testCase.getSubjects().size();
  }

  /** Helper method for asserting a subject assignment with a useful failure message. */
  private <T> void assertAssignment(
      String flagKey, SubjectAssignment expectedSubjectAssignment, T assignment) {

    if (assignment == null) {
      fail(
          "Unexpected null "
              + flagKey
              + " assignment for subject "
              + expectedSubjectAssignment.getSubjectKey());
    }

    String failureMessage =
        "Incorrect "
            + flagKey
            + " assignment for subject "
            + expectedSubjectAssignment.getSubjectKey();

    if (assignment instanceof Boolean) {
      assertEquals(
          failureMessage, expectedSubjectAssignment.getAssignment().booleanValue(), assignment);
    } else if (assignment instanceof Integer) {
      assertEquals(
          failureMessage,
          Double.valueOf(expectedSubjectAssignment.getAssignment().doubleValue()).intValue(),
          assignment);
    } else if (assignment instanceof Double) {
      assertEquals(
          failureMessage,
          expectedSubjectAssignment.getAssignment().doubleValue(),
          (Double) assignment,
          0.000001);
    } else if (assignment instanceof String) {
      assertEquals(
          failureMessage, expectedSubjectAssignment.getAssignment().stringValue(), assignment);
    } else if (assignment instanceof JsonNode) {
      assertEquals(
          failureMessage,
          expectedSubjectAssignment.getAssignment().jsonValue().toString(),
          assignment.toString());
    } else {
      throw new IllegalArgumentException(
          "Unexpected assignment type " + assignment.getClass().getCanonicalName());
    }
  }

  @Test
  public void testInvalidConfigJSON() {

    // Create a mock instance of EppoHttpClient
    EppoHttpClient mockHttpClient = mock(EppoHttpClient.class);

    when(mockHttpClient.getAsync(anyString()))
        .thenReturn(CompletableFuture.completedFuture("{}".getBytes()));

    initClient(TEST_HOST, true, true, false, false, mockHttpClient, null, DUMMY_API_KEY, false);

    String result =
        EppoClient.getInstance()
            .getStringAssignment("dummy subject", "dummy flag", "not-populated");
    assertEquals("not-populated", result);
  }

  @Test
  public void testInvalidConfigJSONAsync() {

    // Create a mock instance of EppoHttpClient
    EppoHttpClient mockHttpClient = mock(EppoHttpClient.class);
    CompletableFuture<byte[]> httpResponse = CompletableFuture.completedFuture("{}".getBytes());

    when(mockHttpClient.getAsync(anyString())).thenReturn(httpResponse);

    initClient(TEST_HOST, true, true, false, false, mockHttpClient, null, DUMMY_API_KEY, false);

    String result =
        EppoClient.getInstance()
            .getStringAssignment("dummy subject", "dummy flag", "not-populated");
    assertEquals("not-populated", result);
  }

  @Test
  public void testCachedBadResponseRequiresFetch() {
    // Populate the cache with a bad response
    ConfigCacheFile cacheFile =
        new ConfigCacheFile(
            ApplicationProvider.getApplicationContext(), safeCacheKey(DUMMY_API_KEY));
    cacheFile.setContents("NEEDS TO BE A VALID JSON TREE");

    initClient(TEST_HOST, true, false, false, true, null, null, DUMMY_API_KEY, false);

    double assignment = EppoClient.getInstance().getDoubleAssignment("numeric_flag", "alice", 0.0);
    assertEquals(3.1415926, assignment, 0.0000001);
  }

  @Test
  public void testEmptyFlagsResponseRequiresFetch() throws IOException {
    // Populate the cache with a bad response
    ConfigCacheFile cacheFile =
        new ConfigCacheFile(
            ApplicationProvider.getApplicationContext(), safeCacheKey(DUMMY_API_KEY));
    Configuration config = Configuration.emptyConfig();
    cacheFile.getOutputStream().write(config.serializeFlagConfigToBytes());

    initClient(TEST_HOST, true, false, false, true, null, null, DUMMY_API_KEY, false);
    double assignment = EppoClient.getInstance().getDoubleAssignment("numeric_flag", "alice", 0.0);
    assertEquals(3.1415926, assignment, 0.0000001);
  }

  @Test
  public void testDifferentCacheFilesPerKey() throws IOException {
    initClient(TEST_HOST, true, true, false, true, null, null, DUMMY_API_KEY, false);
    // API Key 1 will fetch and then populate its cache with the usual test data
    double apiKey1Assignment =
        EppoClient.getInstance().getDoubleAssignment("numeric_flag", "alice", 0.0);
    assertEquals(3.1415926, apiKey1Assignment, 0.0000001);

    // Pre-seed a different flag configuration for the other API Key
    ConfigCacheFile cacheFile2 =
        new ConfigCacheFile(
            ApplicationProvider.getApplicationContext(), safeCacheKey(DUMMY_OTHER_API_KEY));
    // Set the experiment_with_boolean_variations flag to always return true
    byte[] jsonBytes =
        ("{\n"
                + "  \"createdAt\": \"2024-04-17T19:40:53.716Z\",\n"
                + "  \"flags\": {\n"
                + "    \"2c27190d8645fe3bc3c1d63b31f0e4ee\": {\n"
                + "      \"key\": \"2c27190d8645fe3bc3c1d63b31f0e4ee\",\n"
                + "      \"enabled\": true,\n"
                + "      \"variationType\": \"NUMERIC\",\n"
                + "      \"totalShards\": 10000,\n"
                + "      \"variations\": {\n"
                + "        \"cGk=\": {\n"
                + "          \"key\": \"cGk=\",\n"
                + "          \"value\": \"MS4yMzQ1\"\n"
                + // Changed to be 1.2345 encoded
                "        }\n"
                + "      },\n"
                + "      \"allocations\": [\n"
                + "        {\n"
                + "          \"key\": \"cm9sbG91dA==\",\n"
                + "          \"doLog\": true,\n"
                + "          \"splits\": [\n"
                + "            {\n"
                + "              \"variationKey\": \"cGk=\",\n"
                + "              \"shards\": []\n"
                + "            }\n"
                + "          ]\n"
                + "        }\n"
                + "      ]\n"
                + "    }\n"
                + "  }\n"
                + "}")
            .getBytes();
    cacheFile2
        .getOutputStream()
        .write(Configuration.builder(jsonBytes, true).build().serializeFlagConfigToBytes());

    // Initialize with offline mode to prevent instance2 from pulling config via fetch.
    initClient(TEST_HOST, true, false, false, true, null, null, DUMMY_OTHER_API_KEY, true);

    // Ensure API key 2 uses its cache
    double apiKey2Assignment =
        EppoClient.getInstance().getDoubleAssignment("numeric_flag", "alice", 0.0);
    assertEquals(1.2345, apiKey2Assignment, 0.0000001);

    // Reinitialize API key 1 to be sure it used its cache
    initClient(TEST_HOST, true, false, false, true, null, null, DUMMY_API_KEY, false);
    // API Key 1 will fetch and then populate its cache with the usual test data
    apiKey1Assignment = EppoClient.getInstance().getDoubleAssignment("numeric_flag", "alice", 0.0);
    assertEquals(3.1415926, apiKey1Assignment, 0.0000001);
  }

  @Test
  public void testFetchCompletesBeforeCacheLoad() {
    ConfigurationStore slowStore =
        new ConfigurationStore(
            ApplicationProvider.getApplicationContext(), safeCacheKey(DUMMY_API_KEY)) {
          @Override
          protected Configuration readCacheFile() {
            Log.d(TAG, "Simulating slow cache read start");
            try {
              Thread.sleep(2000);
            } catch (InterruptedException ex) {
              throw new RuntimeException(ex);
            }
            Map<String, FlagConfig> mockFlags = new HashMap<>();
            // make the map non-empty so it's not ignored
            mockFlags.put("dummy", new FlagConfig(null, false, 0, null, null, null));

            Log.d(TAG, "Simulating slow cache read end");
            byte[] flagConfig = null;
            try {
              flagConfig =
                  mapper.writeValueAsBytes(new FlagConfigResponse(mockFlags, new HashMap<>()));
            } catch (JsonProcessingException e) {
              throw new RuntimeException(e);
            }
            return Configuration.builder(flagConfig, false).build();
          }
        };

    initClient(TEST_HOST, true, false, false, true, null, slowStore, DUMMY_API_KEY, false);

    EppoClient client = EppoClient.getInstance();
    // Give time for async slow cache read to finish
    try {
      Thread.sleep(2500);
    } catch (InterruptedException ex) {
      throw new RuntimeException(ex);
    }

    double assignment = EppoClient.getInstance().getDoubleAssignment("numeric_flag", "alice", 0.0);
    assertEquals(3.1415926, assignment, 0.0000001);
  }

  private void waitForPopulatedCache() {
    long waitStart = System.currentTimeMillis();
    long waitEnd = waitStart + 10 * 1000; // allow up to 10 seconds
    boolean cachePopulated = false;
    try {
      File file =
          new File(
              ApplicationProvider.getApplicationContext().getFilesDir(),
              cacheFileName(safeCacheKey(DUMMY_API_KEY)));
      while (!cachePopulated) {
        if (System.currentTimeMillis() > waitEnd) {
          throw new InterruptedException(
              "Cache file never populated; assuming configuration error");
        }
        long expectedMinimumSizeInBytes =
            8000; // Last time this test was updated, cache size was 11,506 bytes
        cachePopulated = file.exists() && file.length() > expectedMinimumSizeInBytes;
        if (!cachePopulated) {
          Thread.sleep(8000);
        }
      }
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testAssignmentEventCorrectlyCreated() {
    Date testStart = new Date();
    initClient(TEST_HOST, true, true, false, true, null, null, DUMMY_API_KEY, false);
    Attributes subjectAttributes = new Attributes();
    subjectAttributes.put("age", EppoValue.valueOf(30));
    subjectAttributes.put("employer", EppoValue.valueOf("Eppo"));
    double assignment =
        EppoClient.getInstance()
            .getDoubleAssignment("numeric_flag", "alice", subjectAttributes, 0.0);

    assertEquals(3.1415926, assignment, 0.0000001);

    ArgumentCaptor<Assignment> assignmentLogCaptor = ArgumentCaptor.forClass(Assignment.class);
    verify(mockAssignmentLogger, times(1)).logAssignment(assignmentLogCaptor.capture());
    Assignment capturedAssignment = assignmentLogCaptor.getValue();
    assertEquals("numeric_flag-rollout", capturedAssignment.getExperiment());
    assertEquals("numeric_flag", capturedAssignment.getFeatureFlag());
    assertEquals("rollout", capturedAssignment.getAllocation());
    assertEquals(
        "pi",
        capturedAssignment
            .getVariation()); // Note: unlike this test, typically variation keys will just be the
    // value for everything not JSON
    assertEquals("alice", capturedAssignment.getSubject());
    assertEquals(subjectAttributes, capturedAssignment.getSubjectAttributes());
    assertEquals(new HashMap<>(), capturedAssignment.getExtraLogging());

    Date assertionDate = new Date();
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
    dateFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
    Date parsedTimestamp = capturedAssignment.getTimestamp();
    assertNotNull(parsedTimestamp);
    assertTrue(parsedTimestamp.after(testStart));
    assertTrue(parsedTimestamp.before(assertionDate));

    Map<String, String> expectedMeta = new HashMap<>();
    expectedMeta.put("obfuscated", "true");
    expectedMeta.put("sdkLanguage", "android");
    expectedMeta.put("sdkLibVersion", BuildConfig.EPPO_VERSION);

    assertEquals(expectedMeta, capturedAssignment.getMetaData());
  }

  private static SimpleModule module() {
    SimpleModule module = new SimpleModule();
    module.addDeserializer(AssignmentTestCase.class, new AssignmentTestCaseDeserializer());
    return module;
  }

  private static void setBaseClientHttpClientOverrideField(EppoHttpClient httpClient) {
    setBaseClientOverrideField("httpClientOverride", httpClient);
  }

  /** Uses reflection to set a static override field used for tests (e.g., httpClientOverride) */
  @SuppressWarnings("SameParameterValue")
  public static <T> void setBaseClientOverrideField(String fieldName, T override) {
    try {
      Field httpClientOverrideField = BaseEppoClient.class.getDeclaredField(fieldName);
      httpClientOverrideField.setAccessible(true);
      httpClientOverrideField.set(null, override);
      httpClientOverrideField.setAccessible(false);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
