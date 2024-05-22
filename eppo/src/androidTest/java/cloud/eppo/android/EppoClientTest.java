package cloud.eppo.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import static cloud.eppo.android.util.Utils.logTag;
import static cloud.eppo.android.util.Utils.safeCacheKey;

import android.content.res.AssetManager;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.FlagConfig;
import cloud.eppo.android.dto.FlagConfigResponse;
import cloud.eppo.android.dto.SubjectAttributes;
import cloud.eppo.android.dto.VariationType;
import cloud.eppo.android.helpers.AssignmentTestCase;
import cloud.eppo.android.helpers.AssignmentTestCaseDeserializer;
import cloud.eppo.android.helpers.SubjectAssignment;
import cloud.eppo.android.helpers.TestCaseValue;

public class EppoClientTest {
    private static final String TAG = logTag(EppoClient.class);
    private static final String DUMMY_API_KEY = "mock-api-key";
    private static final String DUMMY_OTHER_API_KEY = "another-mock-api-key";
    private static final String TEST_HOST = "https://us-central1-eppo-qa.cloudfunctions.net/serveGitHubRacTestFile";
    private static final String INVALID_HOST = "https://thisisabaddomainforthistest.com";
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(AssignmentTestCase.class, new AssignmentTestCaseDeserializer())
            .create();

    private void initClient(String host, boolean throwOnCallbackError, boolean shouldDeleteCacheFiles, boolean isGracefulMode, String apiKey) {
        if (shouldDeleteCacheFiles) {
            clearCacheFile(apiKey);
        }

        CountDownLatch lock = new CountDownLatch(1);

        new EppoClient.Builder()
                .application(ApplicationProvider.getApplicationContext())
                .apiKey(apiKey)
                .isGracefulMode(isGracefulMode)
                .host(host)
                .callback(new InitializationCallback() {
                    @Override
                    public void onCompleted() {
                        Log.w(TAG, "Test client onCompleted callback");
                        lock.countDown();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Log.w(TAG, "Test client onError callback");
                        if (throwOnCallbackError) {
                            throw new RuntimeException("Unable to initialize: "+errorMessage);
                        }
                        lock.countDown();
                    }
                })
                .buildAndInit();

        // Wait for initialization to succeed or fail, up to 10 seconds, before continuing
        try {
            if (!lock.await(10000, TimeUnit.MILLISECONDS)) {
                throw new InterruptedException("Client initialization not complete within timeout");
            }
            Log.d(TAG, "Test client initialized");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Before
    public void cleanUp() {
        // Clear any caches
        String[] apiKeys = { DUMMY_API_KEY, DUMMY_OTHER_API_KEY };
        for (String apiKey : apiKeys) {
            clearCacheFile(apiKey);
        }
        // Reset any development overrides
        setIsConfigObfuscatedField(true);
        setHttpClientOverrideField(null);
        setConfigurationStoreOverrideField(null);
    }

    private void clearCacheFile(String apiKey) {
        String cacheFileNameSuffix = safeCacheKey(apiKey);
        ConfigCacheFile cacheFile = new ConfigCacheFile(ApplicationProvider.getApplicationContext(), cacheFileNameSuffix);
        cacheFile.delete();
    }

    @Test
    public void testUnobfuscatedAssignments() {
        setIsConfigObfuscatedField(false);
        initClient(TEST_HOST, true, true, false, DUMMY_API_KEY);
        runTestCases();
    }

    @Test
    public void testAssignments() {
        initClient(TEST_HOST, true, true, false, DUMMY_API_KEY);
        runTestCases();
    }

    @Test
    public void testErrorGracefulModeOn() {
        initClient(TEST_HOST, false, true, true, DUMMY_API_KEY);

        EppoClient realClient = EppoClient.getInstance();
        EppoClient spyClient = spy(realClient);
        doThrow(new RuntimeException("Exception thrown by mock"))
            .when(spyClient)
            .getTypedAssignment(anyString(), anyString(), any(SubjectAttributes.class), any(EppoValue.class), any(VariationType.class));

        assertTrue(spyClient.getBooleanAssignment("experiment1", "subject1", true));
        assertFalse(spyClient.getBooleanAssignment("experiment1", "subject1", new SubjectAttributes(), false));

        assertEquals(10, spyClient.getIntegerAssignment("experiment1", "subject1", 10));
        assertEquals(0, spyClient.getIntegerAssignment("experiment1", "subject1", new SubjectAttributes(), 0));

        assertEquals(1.2345, spyClient.getDoubleAssignment("experiment1", "subject1", 1.2345), 0.0001);
        assertEquals(0.0, spyClient.getDoubleAssignment("experiment1", "subject1", new SubjectAttributes(), 0.0), 0.0001);

        assertEquals("default", spyClient.getStringAssignment("experiment1", "subject1", "default"));
        assertEquals("", spyClient.getStringAssignment("experiment1", "subject1", new SubjectAttributes(), ""));

        assertEquals(JsonParser.parseString("{\"a\": 1, \"b\": false}"),
                spyClient.getJSONAssignment("subject1", "experiment1", JsonParser.parseString("{\"a\": 1, \"b\": false}"))
        );

        assertEquals(JsonParser.parseString("{}"),
                spyClient.getJSONAssignment("subject1", "experiment1", new SubjectAttributes(), JsonParser.parseString("{}"))
        );
    }

    @Test
    public void testErrorGracefulModeOff() {
        initClient(TEST_HOST, false, true, false, DUMMY_API_KEY);

        EppoClient realClient = EppoClient.getInstance();
        EppoClient spyClient = spy(realClient);
        doThrow(new RuntimeException("Exception thrown by mock"))
            .when(spyClient)
            .getTypedAssignment(anyString(), anyString(), any(SubjectAttributes.class), any(EppoValue.class), any(VariationType.class));

        assertThrows(RuntimeException.class, () -> spyClient.getBooleanAssignment("experiment1", "subject1", true));
        assertThrows(RuntimeException.class, () -> spyClient.getBooleanAssignment("experiment1", "subject1", new SubjectAttributes(), false));

        assertThrows(RuntimeException.class, () -> spyClient.getIntegerAssignment("experiment1", "subject1", 10));
        assertThrows(RuntimeException.class, () -> spyClient.getIntegerAssignment("experiment1", "subject1", new SubjectAttributes(), 0));

        assertThrows(RuntimeException.class, () -> spyClient.getDoubleAssignment("experiment1", "subject1", 1.2345));
        assertThrows(RuntimeException.class, () -> spyClient.getDoubleAssignment("experiment1", "subject1", new SubjectAttributes(), 0.0));

        assertThrows(RuntimeException.class, () -> spyClient.getStringAssignment("experiment1", "subject1", "default"));
        assertThrows(RuntimeException.class, () -> spyClient.getStringAssignment("experiment1", "subject1", new SubjectAttributes(), ""));

        assertThrows(RuntimeException.class, () -> spyClient.getJSONAssignment("subject1", "experiment1", JsonParser.parseString("{\"a\": 1, \"b\": false}")));
        assertThrows(RuntimeException.class, () -> spyClient.getJSONAssignment("subject1", "experiment1", new SubjectAttributes(), JsonParser.parseString("{}")));
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
    public void testCachedAssignments() {
        // First initialize successfully
        initClient(TEST_HOST, true, true, false, DUMMY_API_KEY); // ensure cache is populated

        // Then reinitialize with a bad host so we know it's using the cached UFC built from the first initialization
        initClient(INVALID_HOST, false, false, false, DUMMY_API_KEY); // invalid host to force to use cache

        runTestCases();
    }

    private int runTestCaseFileStream(InputStream testCaseStream) throws IOException {
        String json = IOUtils.toString(testCaseStream, Charsets.toCharset("UTF8"));
        AssignmentTestCase testCase = gson.fromJson(json, AssignmentTestCase.class);
        String flagKey = testCase.getFlag();
        TestCaseValue defaultValue = testCase.getDefaultValue();
        EppoClient eppoClient = EppoClient.getInstance();

        for (SubjectAssignment subjectAssignment : testCase.getSubjects()) {
            String subjectKey = subjectAssignment.getSubjectKey();
            SubjectAttributes subjectAttributes = subjectAssignment.getSubjectAttributes();

            // Depending on the variation type, we will need to change which assignment method we call and how we get the default value
            switch (testCase.getVariationType()) {
                case BOOLEAN:
                    boolean boolAssignment = eppoClient.getBooleanAssignment(flagKey, subjectKey, subjectAttributes, defaultValue.booleanValue());
                    assertAssignment(flagKey, subjectAssignment, boolAssignment);
                    break;
                case INTEGER:
                    int intAssignment = eppoClient.getIntegerAssignment(flagKey, subjectKey, subjectAttributes, Double.valueOf(defaultValue.doubleValue()).intValue());
                    assertAssignment(flagKey, subjectAssignment, intAssignment);
                    break;
                case NUMERIC:
                    double doubleAssignment = eppoClient.getDoubleAssignment(flagKey, subjectKey, subjectAttributes, defaultValue.doubleValue());
                    assertAssignment(flagKey, subjectAssignment, doubleAssignment);
                    break;
                case STRING:
                    String stringAssignment = eppoClient.getStringAssignment(flagKey, subjectKey, subjectAttributes, defaultValue.stringValue());
                    assertAssignment(flagKey, subjectAssignment, stringAssignment);
                    break;
                case JSON:
                    JsonElement jsonAssignment = eppoClient.getJSONAssignment(flagKey, subjectKey, subjectAttributes, defaultValue.jsonValue());
                    assertAssignment(flagKey, subjectAssignment, jsonAssignment);
                    break;
                default:
                    throw new UnsupportedOperationException("Unexpected variation type "+testCase.getVariationType()+" for "+flagKey+" test case");
            }
        }

        return testCase.getSubjects().size();
    }

    /**
     * Helper method for asserting a subject assignment with a useful failure message.
     */
    private <T> void assertAssignment(String flagKey, SubjectAssignment expectedSubjectAssignment, T assignment) {

        if (assignment == null) {
            fail("Unexpected null "+flagKey+" assignment for subject "+expectedSubjectAssignment.getSubjectKey());
        }

        String failureMessage = "Incorrect "+flagKey+" assignment for subject "+expectedSubjectAssignment.getSubjectKey();

        if (assignment instanceof Boolean) {
            assertEquals(failureMessage, expectedSubjectAssignment.getAssignment().booleanValue(), assignment);
        } else if (assignment instanceof Integer) {
            assertEquals(failureMessage, Double.valueOf(expectedSubjectAssignment.getAssignment().doubleValue()).intValue(), assignment);
        } else if (assignment instanceof Double) {
            assertEquals(failureMessage, expectedSubjectAssignment.getAssignment().doubleValue(), (Double)assignment, 0.000001);
        } else if (assignment instanceof String) {
            assertEquals(failureMessage, expectedSubjectAssignment.getAssignment().stringValue(), assignment);
        } else if (assignment instanceof JsonElement) {
            assertEquals(failureMessage, expectedSubjectAssignment.getAssignment().jsonValue(), assignment);
        } else {
            throw new IllegalArgumentException("Unexpected assignment type "+assignment.getClass().getCanonicalName());
        }
    }

    @Test
    public void testInvalidConfigJSON() {

        // Create a mock instance of EppoHttpClient
        EppoHttpClient mockHttpClient = mock(EppoHttpClient.class);

        doAnswer(invocation -> {
            RequestCallback callback = invocation.getArgument(1);
            callback.onSuccess(new StringReader("{}"));
            return null; // doAnswer doesn't require a return value
        }).when(mockHttpClient).get(anyString(), any(RequestCallback.class));

        setHttpClientOverrideField(mockHttpClient);
        initClient(TEST_HOST, true, true, false, DUMMY_API_KEY);

        String result = EppoClient.getInstance().getStringAssignment("dummy subject", "dummy flag", "not-populated");
        assertEquals("not-populated", result);
    }

    @Test
    public void testCachedBadResponseRequiresFetch() {
        // Populate the cache with a bad response
        ConfigCacheFile cacheFile = new ConfigCacheFile(ApplicationProvider.getApplicationContext(), safeCacheKey(DUMMY_API_KEY));
        cacheFile.setContents("{ invalid }");

        initClient(TEST_HOST, true, false, false, DUMMY_API_KEY);

        double assignment = EppoClient.getInstance().getDoubleAssignment("numeric_flag", "alice", 0.0);
        assertEquals(3.1415926, assignment, 0.0000001);
    }

    @Test
    public void testEmptyFlagsResponseRequiresFetch() {
        // Populate the cache with a bad response
        ConfigCacheFile cacheFile = new ConfigCacheFile(ApplicationProvider.getApplicationContext(), safeCacheKey(DUMMY_API_KEY));
        cacheFile.setContents("{\"flags\": {}}");

        initClient(TEST_HOST, true, false, false, DUMMY_API_KEY);
        double assignment = EppoClient.getInstance().getDoubleAssignment("numeric_flag", "alice", 0.0);
        assertEquals(3.1415926, assignment, 0.0000001);
    }

    @Test
    public void testDifferentCacheFilesPerKey() {
        initClient(TEST_HOST, true, true, false, DUMMY_API_KEY);
        // API Key 1 will fetch and then populate its cache with the usual test data
        double apiKey1Assignment = EppoClient.getInstance().getDoubleAssignment("numeric_flag", "alice", 0.0);
        assertEquals(3.1415926, apiKey1Assignment, 0.0000001);

        // Pre-seed a different flag configuration for the other API Key
        ConfigCacheFile cacheFile2 = new ConfigCacheFile(ApplicationProvider.getApplicationContext(), safeCacheKey(DUMMY_OTHER_API_KEY));
        // Set the experiment_with_boolean_variations flag to always return true
        cacheFile2.setContents("{\n" +
                "  \"createdAt\": \"2024-04-17T19:40:53.716Z\",\n" +
                "  \"flags\": {\n" +
                "    \"2c27190d8645fe3bc3c1d63b31f0e4ee\": {\n" +
                "      \"key\": \"2c27190d8645fe3bc3c1d63b31f0e4ee\",\n" +
                "      \"enabled\": true,\n" +
                "      \"variationType\": \"NUMERIC\",\n" +
                "      \"totalShards\": 10000,\n" +
                "      \"variations\": {\n" +
                "        \"cGk=\": {\n" +
                "          \"key\": \"cGk=\",\n" +
                "          \"value\": \"MS4yMzQ1\"\n" + //Changed to be 1.2345 encoded
                "        }\n" +
                "      },\n" +
                "      \"allocations\": [\n" +
                "        {\n" +
                "          \"key\": \"cm9sbG91dA==\",\n" +
                "          \"doLog\": true,\n" +
                "          \"splits\": [\n" +
                "            {\n" +
                "              \"variationKey\": \"cGk=\",\n" +
                "              \"shards\": []\n" +
                "            }\n" +
                "          ]\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  }\n" +
                "}");

        initClient(TEST_HOST, true, false, false, DUMMY_OTHER_API_KEY);

        // Ensure API key 2 uses its cache
        double apiKey2Assignment = EppoClient.getInstance().getDoubleAssignment("numeric_flag", "alice", 0.0);
        assertEquals(1.2345, apiKey2Assignment, 0.0000001);

        // Reinitialize API key 1 to be sure it used its cache
        initClient(TEST_HOST, true, false, false, DUMMY_API_KEY);
        // API Key 1 will fetch and then populate its cache with the usual test data
        apiKey1Assignment = EppoClient.getInstance().getDoubleAssignment("numeric_flag", "alice", 0.0);
        assertEquals(3.1415926, apiKey1Assignment, 0.0000001);
    }

    @Test
    public void testFetchCompletesBeforeCacheLoad() {
        ConfigurationStore slowStore = new ConfigurationStore(ApplicationProvider.getApplicationContext(), safeCacheKey(DUMMY_API_KEY)) {
          @Override
          protected FlagConfigResponse readCacheFile() {
              Log.d(TAG, "Simulating slow cache read start");
              try {
                  Thread.sleep(2000);
              } catch (InterruptedException ex) {
                  throw new RuntimeException(ex);
              }
              FlagConfigResponse response = new FlagConfigResponse();
              Map<String, FlagConfig> mockFlags = new HashMap<>();
              mockFlags.put("dummy", new FlagConfig()); // make the map non-empty so it's not ignored
              response.setFlags(mockFlags);

              Log.d(TAG, "Simulating slow cache read end");
              return response;
          }
        };

        setConfigurationStoreOverrideField(slowStore);
        initClient(TEST_HOST, true, false, false, DUMMY_API_KEY);

        // Give time for async slow cache read to finish
        try {
            Thread.sleep(2500);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }

        double assignment = EppoClient.getInstance().getDoubleAssignment("numeric_flag", "alice", 0.0);
        assertEquals(3.1415926, assignment, 0.0000001);
    }

    private void setHttpClientOverrideField(EppoHttpClient httpClient) {
        setOverrideField("httpClientOverride", httpClient);
    }

    private void setConfigurationStoreOverrideField(ConfigurationStore configurationStore) {
        setOverrideField("configurationStoreOverride", configurationStore);
    }

    /** @noinspection SameParameterValue*/
    private void setIsConfigObfuscatedField(boolean isConfigObfuscated) {
        setOverrideField("isConfigObfuscated", isConfigObfuscated);
    }

    private <T> void setOverrideField(String fieldName, T override) {
        try {
            // Use reflection to set the httpClientOverride field
            Field httpClientOverrideField = EppoClient.class.getDeclaredField(fieldName);
            httpClientOverrideField.setAccessible(true);
            httpClientOverrideField.set(null, override);
            httpClientOverrideField.setAccessible(false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
