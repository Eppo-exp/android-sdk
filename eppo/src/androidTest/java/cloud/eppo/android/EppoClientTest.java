package cloud.eppo.android;

import static cloud.eppo.android.util.Utils.logTag;
import static cloud.eppo.android.util.Utils.safeCacheKey;
import static java.lang.Thread.sleep;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.res.AssetManager;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import cloud.eppo.BaseEppoClient;
import cloud.eppo.EppoHttpClient;
import cloud.eppo.IEppoHttpClient;
import cloud.eppo.android.helpers.AssignmentTestCase;
import cloud.eppo.android.helpers.AssignmentTestCaseDeserializer;
import cloud.eppo.android.helpers.SubjectAssignment;
import cloud.eppo.android.helpers.TestCaseValue;
import cloud.eppo.android.helpers.TestUtils;
import cloud.eppo.api.Attributes;
import cloud.eppo.api.Configuration;
import cloud.eppo.api.EppoActionCallback;
import cloud.eppo.api.EppoValue;
import cloud.eppo.api.IAssignmentCache;
import cloud.eppo.logging.AssignmentLogger;
import cloud.eppo.ufc.dto.VariationType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class EppoClientTest {
  private static final String TAG = logTag(EppoClient.class);
  private static final String DUMMY_API_KEY = "mock-api-key";
  private static final String DUMMY_OTHER_API_KEY = "another-mock-api-key";

  // Use branch if specified by env variable `TEST_DATA_BRANCH`.
  private static final String TEST_BRANCH =
      InstrumentationRegistry.getArguments().getString("TEST_DATA_BRANCH");
  private static final String TEST_URL_BASE =
      "https://us-central1-eppo-qa.cloudfunctions.net/serveGitHubRacTestFile";
  private static final String TEST_API_BASE_URL =
      TEST_URL_BASE + (TEST_BRANCH != null ? "/b/" + TEST_BRANCH : "");

  private static final String INVALID_HOST = "https://thisisabaddomainforthistest.com";
  private static final byte[] EMPTY_CONFIG = "{\"flags\":{}}".getBytes();
  private final ObjectMapper mapper = new ObjectMapper().registerModule(module());
  @Mock AssignmentLogger mockAssignmentLogger;
  @Mock EppoHttpClient mockHttpClient;

  private void initClient(
      String baseUrl,
      boolean throwOnCallbackError,
      boolean shouldDeleteCacheFiles,
      boolean isGracefulMode,
      @Nullable EppoHttpClient httpClientOverride,
      @Nullable ConfigurationStore configurationStoreOverride,
      String apiKey,
      boolean offlineMode,
      IAssignmentCache assignmentCache,
      boolean ignoreConfigCacheFile) {
    if (shouldDeleteCacheFiles) {
      clearCacheFile(apiKey);
    }

    setBaseClientHttpClientOverrideField(httpClientOverride);

    // Replace CompletableFuture with CountDownLatch
    final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
    final Throwable[] initError = new Throwable[1];

    EppoClient.Builder builder =
        new EppoClient.Builder(apiKey, ApplicationProvider.getApplicationContext())
            .isGracefulMode(isGracefulMode)
            .apiBaseUrl(baseUrl)
            .assignmentLogger(mockAssignmentLogger)
            .forceReinitialize(true)
            .offlineMode(offlineMode)
            .configStore(configurationStoreOverride)
            .assignmentCache(assignmentCache);

    builder.buildAndInitAsync(
        new EppoActionCallback<EppoClient>() {
          @Override
          public void onSuccess(EppoClient data) {
            Log.i(TAG, "Test client async buildAndInit completed.");
            latch.countDown();
          }

          @Override
          public void onFailure(Throwable error) {
            Log.e(TAG, "Test client async buildAndInit error" + error.getMessage(), error);
            initError[0] = error;
            if (throwOnCallbackError) {
              throw new RuntimeException("Unable to initialize: " + error.getMessage(), error);
            }
            latch.countDown();
          }
        });

    // Wait for initialization to succeed or fail, up to 10 seconds, before continuing
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new RuntimeException("Client initialization timed out");
      }
      if (initError[0] != null && throwOnCallbackError) {
        throw new RuntimeException(
            "Unable to initialize: " + initError[0].getMessage(), initError[0]);
      }
      Log.d(TAG, "Test client initialized");
    } catch (InterruptedException e) {
      throw new RuntimeException("Client initialization interrupted", e);
    }
  }

  @Before
  public void cleanUp() {
    MockitoAnnotations.openMocks(this);
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
    initClient(TEST_API_BASE_URL, true, true, false, null, null, DUMMY_API_KEY, false, null, false);
    runTestCases();
  }

  @Test
  public void testAssignments() {
    initClient(TEST_API_BASE_URL, true, true, false, null, null, DUMMY_API_KEY, false, null, false);
    runTestCases();
  }

  @Test
  public void testErrorGracefulModeOn() throws JSONException, JsonProcessingException {
    initClient(TEST_API_BASE_URL, false, true, true, null, null, DUMMY_API_KEY, false, null, false);

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
    initClient(
        TEST_API_BASE_URL, false, true, false, null, null, DUMMY_API_KEY, false, null, false);

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

  private static IEppoHttpClient mockHttpError() {
    // Create a mock instance of EppoHttpClient tha throws
    IEppoHttpClient mockHttpClient = new TestUtils.ThrowingHttpClient();

    return mockHttpClient;
  }

  @Test
  public void testGracefulInitializationFailure() {
    // Set up bad HTTP response
    IEppoHttpClient http = mockHttpError();
    setBaseClientHttpClientOverrideField(http);

    // Use CountDownLatch instead of CompletableFuture
    final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
    final boolean[] success = new boolean[1];

    EppoClient.Builder clientBuilder =
        new EppoClient.Builder(DUMMY_API_KEY, ApplicationProvider.getApplicationContext())
            .forceReinitialize(true)
            .isGracefulMode(true);

    // Initialize and no exception should be thrown.
    clientBuilder.buildAndInitAsync(
        new EppoActionCallback<EppoClient>() {
          @Override
          public void onSuccess(EppoClient data) {
            success[0] = true;
            latch.countDown();
          }

          @Override
          public void onFailure(Throwable error) {
            success[0] = false;
            latch.countDown();
          }
        });

    try {
      latch.await(5, TimeUnit.SECONDS);
      assertTrue("Client should have initialized successfully in graceful mode", success[0]);
    } catch (InterruptedException e) {
      fail("Test was interrupted");
    }
  }

  @Test
  public void testFetchAndActivateConfiguration() throws ExecutionException, InterruptedException {
    testFetchAndActivateConfigurationHelper(false);
  }

  @Test
  public void testFetchAndActivateConfigurationAsync()
      throws ExecutionException, InterruptedException {
    testFetchAndActivateConfigurationHelper(true);
  }

  private static class LatchedCallback<T> implements EppoActionCallback<T> {
    public final AtomicReference<T> result = new AtomicReference();
    public final AtomicReference<Throwable> failure = new AtomicReference();
    private final CountDownLatch latch = new CountDownLatch(1);

    public boolean await(long duration, TimeUnit timeUnit) throws InterruptedException {
      return latch.await(duration, timeUnit);
    }

    @Override
    public void onSuccess(T data) {
      result.set(data);
      latch.countDown();
    }

    @Override
    public void onFailure(Throwable error) {
      failure.set(error);
      latch.countDown();
    }
  }

  private void testFetchAndActivateConfigurationHelper(boolean loadAsync)
      throws ExecutionException, InterruptedException {
    // Set up a changing response from the "server"
    TestUtils.MockHttpClient mockHttpClient = getMockHttpClient();

    EppoClient.Builder clientBuilder =
        new EppoClient.Builder(DUMMY_API_KEY, ApplicationProvider.getApplicationContext())
            .forceReinitialize(true)
            .isGracefulMode(false);

    // Initialize and no exception should be thrown.
    EppoClient eppoClient = clientBuilder.buildAndInit();

    verify(mockHttpClient, times(1)).getAsync(anyString(), any(IEppoHttpClient.Callback.class));
    assertFalse(eppoClient.getBooleanAssignment("bool_flag", "subject1", false));

    // Now, return the boolean flag config (bool_flag = true)
    mockHttpClient.changeResponse(BOOL_FLAG_CONFIG);

    // Trigger a reload of the client
    if (loadAsync) {
      LatchedCallback<Configuration> latch = new LatchedCallback<>();
      eppoClient.fetchAndActivateConfigurationAsync(latch);
      assertTrue(
          "Client did not initialize asynchronously within 5 seconds",
          latch.await(5, TimeUnit.SECONDS));
    } else {
      eppoClient.fetchAndActivateConfiguration();
    }

    assertTrue(eppoClient.getBooleanAssignment("bool_flag", "subject1", false));
  }

  @Test
  public void testConfigurationChangeListener() {
    List<Configuration> received = new ArrayList<>();

    // Set up a changing response from the "server"
    TestUtils.MockHttpClient mockHttpClient = getMockHttpClient();

    EppoClient.Builder clientBuilder =
        new EppoClient.Builder(DUMMY_API_KEY, ApplicationProvider.getApplicationContext())
            .forceReinitialize(true)
            .onConfigurationChange(received::add)
            .isGracefulMode(false);

    // Initialize and no exception should be thrown.
    EppoClient eppoClient = clientBuilder.buildAndInit();

    verify(mockHttpClient, times(1)).getAsync(anyString(), any(IEppoHttpClient.Callback.class));
    assertEquals(1, received.size());

    // Now, return the boolean flag config so that the config has changed.
    mockHttpClient.changeResponse(BOOL_FLAG_CONFIG);

    // Trigger a reload of the client
    eppoClient.fetchAndActivateConfiguration();

    assertEquals(2, received.size());

    // Reload the client again; the config hasn't changed, but Java doesn't check eTag (yet)
    eppoClient.fetchAndActivateConfiguration();

    assertEquals(3, received.size());
  }

  private static TestUtils.MockHttpClient getMockHttpClient() {
    TestUtils.MockHttpClient mockHttpClient = spy(new TestUtils.MockHttpClient(EMPTY_CONFIG));
    setBaseClientHttpClientOverrideField(mockHttpClient);
    return mockHttpClient;
  }

  @Test
  public void testPollingClient() throws InterruptedException {
    TestUtils.MockHttpClient mockHttpClient = spy(new TestUtils.MockHttpClient(EMPTY_CONFIG));

    CountDownLatch pollLatch = new CountDownLatch(1);
    CountDownLatch configActivatedLatch = new CountDownLatch(1);

    setBaseClientHttpClientOverrideField(mockHttpClient);

    long pollingIntervalMs = 50;

    EppoClient.Builder clientBuilder =
        new EppoClient.Builder(DUMMY_API_KEY, ApplicationProvider.getApplicationContext())
            .forceReinitialize(true)
            .pollingEnabled(true)
            .pollingIntervalMs(pollingIntervalMs)
            .onConfigurationChange(
                (config) -> {
                  configActivatedLatch.countDown();
                })
            .isGracefulMode(false);

    EppoClient eppoClient = clientBuilder.buildAndInit();

    // Empty config on initialization
    verify(mockHttpClient, times(1)).getAsync(anyString(), any(IEppoHttpClient.Callback.class));
    assertFalse(eppoClient.getBooleanAssignment("bool_flag", "subject1", false));

    // Change the served config to the "boolean flag config"
    mockHttpClient.changeResponse(BOOL_FLAG_CONFIG);

    // Wait for the client to send the "fetch"
    //      assertTrue("Polling did not occur within timeout", pollLatch.await(5,
    // TimeUnit.SECONDS));
    sleep(pollingIntervalMs * 12 / 10);

    // Wait for the client to apply the fetch and notify of config change.

    verify(mockHttpClient, times(1)).get(anyString());
    assertTrue(
        "Configuration not activated within timeout",
        configActivatedLatch.await(250, TimeUnit.MILLISECONDS));

    // Assignment is now true.
    assertTrue(eppoClient.getBooleanAssignment("bool_flag", "subject1", false));

    eppoClient.stopPolling();
  }

  @Test
  public void testClientMakesDefaultAssignmentsAfterFailingToInitialize()
      throws ExecutionException, InterruptedException {
    // Set up bad HTTP response
    setBaseClientHttpClientOverrideField(mockHttpError());

    EppoClient.Builder clientBuilder =
        new EppoClient.Builder(DUMMY_API_KEY, ApplicationProvider.getApplicationContext())
            .forceReinitialize(true)
            .isGracefulMode(true);

    // Initialize and no exception should be thrown.
    EppoClient eppoClient = clientBuilder.buildAndInit();

    assertEquals("default", eppoClient.getStringAssignment("experiment1", "subject1", "default"));
  }

  @Test
  public void testClientMakesDefaultAssignmentsAfterFailingToInitializeNonGracefulMode() {
    // Set up bad HTTP response
    setBaseClientHttpClientOverrideField(mockHttpError());

    EppoClient.Builder clientBuilder =
        new EppoClient.Builder(DUMMY_API_KEY, ApplicationProvider.getApplicationContext())
            .forceReinitialize(true)
            .isGracefulMode(false);

    // Initialize, expect the exception and then verify that the client can still complete an
    // assignment.
    try {
      clientBuilder.buildAndInit();
      fail("Expected exception");
    } catch (RuntimeException e) {
      // Expected
      assertNotNull(e.getCause());

      assertEquals(
          "default",
          EppoClient.getInstance().getStringAssignment("experiment1", "subject1", "default"));
    }
  }

  @Test
  public void testNonGracefulInitializationFailure() {
    // Set up bad HTTP response
    setBaseClientHttpClientOverrideField(mockHttpError());

    final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
    final Throwable[] error = new Throwable[1];

    EppoClient.Builder clientBuilder =
        new EppoClient.Builder(DUMMY_API_KEY, ApplicationProvider.getApplicationContext())
            .forceReinitialize(true)
            .isGracefulMode(false);

    // Initialize and expect an exception.
    clientBuilder.buildAndInitAsync(
        new EppoActionCallback<EppoClient>() {
          @Override
          public void onSuccess(EppoClient data) {
            latch.countDown();
          }

          @Override
          public void onFailure(Throwable e) {
            error[0] = e;
            latch.countDown();
          }
        });

    try {
      latch.await(5, TimeUnit.SECONDS);
      assertNotNull("Expected an error", error[0]);
    } catch (InterruptedException e) {
      fail("Test was interrupted");
    }
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

  private static SimpleModule module() {
    SimpleModule module = new SimpleModule();
    module.addDeserializer(AssignmentTestCase.class, new AssignmentTestCaseDeserializer());
    return module;
  }

  private static void setBaseClientHttpClientOverrideField(IEppoHttpClient httpClient) {
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

  private static final byte[] BOOL_FLAG_CONFIG =
      ("{\n"
              + "  \"createdAt\": \"2024-04-17T19:40:53.716Z\",\n"
              + "  \"format\": \"CLIENT\",\n"
              + "  \"environment\": {\n"
              + "    \"name\": \"Test\"\n"
              + "  },\n"
              + "  \"flags\": {\n"
              + "    \"9a2025738dde19ff44cd30b9d2967000\": {\n"
              + "      \"key\": \"9a2025738dde19ff44cd30b9d2967000\",\n"
              + "      \"enabled\": true,\n"
              + "      \"variationType\": \"BOOLEAN\",\n"
              + "      \"variations\": {\n"
              + "        \"b24=\": {\n"
              + "          \"key\": \"b24=\",\n"
              + "          \"value\": \"dHJ1ZQ==\"\n"
              + "        }\n"
              + "      },\n"
              + "      \"allocations\": [\n"
              + "        {\n"
              + "          \"key\": \"b24=\",\n"
              + "          \"doLog\": true,\n"
              + "          \"splits\": [\n"
              + "            {\n"
              + "              \"variationKey\": \"b24=\",\n"
              + "              \"shards\": []\n"
              + "            }\n"
              + "          ]\n"
              + "        }\n"
              + "      ],\n"
              + "      \"totalShards\": 10000\n"
              + "    }\n"
              + "  }\n"
              + "}")
          .getBytes();
}
