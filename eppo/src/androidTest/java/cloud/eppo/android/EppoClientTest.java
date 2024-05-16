package cloud.eppo.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import static cloud.eppo.android.ConfigCacheFile.cacheFileName;
import static cloud.eppo.android.util.Utils.logTag;
import static cloud.eppo.android.util.Utils.safeCacheKey;

import android.content.res.AssetManager;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;

import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.SubjectAttributes;
import cloud.eppo.android.dto.deserializers.EppoValueAdapter;

public class EppoClientTest {
    private static final String TAG = logTag(EppoClient.class);
    private static final String DUMMY_API_KEY = "mock-api-key";
    private static final String DUMMY_OTHER_API_KEY = "another-mock-api-key";
    private static final String TEST_HOST = "https://us-central1-eppo-qa.cloudfunctions.net/serveGitHubRacTestFile";
    private static final String INVALID_HOST = "https://thisisabaddomainforthistest.com";
    private Gson gson = new GsonBuilder()
            .registerTypeAdapter(EppoValue.class, new EppoValueAdapter())
            .registerTypeAdapter(AssignmentValueType.class, new AssignmentValueTypeAdapter(AssignmentValueType.STRING))
            .create();

    static class SubjectWithAttributes {
        String subjectKey;
        SubjectAttributes subjectAttributes;
    }

    static enum AssignmentValueType {
        STRING("string"),
        BOOLEAN("boolean"),
        JSON("json"),
        NUMERIC("numeric");

        private String strValue;

        AssignmentValueType(String value) {
            this.strValue = value;
        }

        String value() {
            return this.strValue;
        }

        static AssignmentValueType getByString(String str) {
            for (AssignmentValueType valueType : AssignmentValueType.values()) {
                if (valueType.value().compareTo(str) == 0) {
                    return valueType;
                }
            }
            return null;
        }
    }

    static class AssignmentValueTypeAdapter implements JsonDeserializer<AssignmentValueType> {
        private AssignmentValueType defaultValue = null;

        AssignmentValueTypeAdapter(AssignmentValueType defaultValue) {
            this.defaultValue = defaultValue;
        }

        AssignmentValueTypeAdapter() {
        }

        @Override
        public AssignmentValueType deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            if (json.isJsonNull()) {
                return this.defaultValue;
            }

            AssignmentValueType value = AssignmentValueType.getByString(json.getAsString());
            if (value == null) {
                throw new RuntimeException("Invalid assignment value type");
            }

            return value;
        }
    }

    static class AssignmentTestCase {
        String experiment;
        AssignmentValueType valueType = AssignmentValueType.STRING;
        List<SubjectWithAttributes> subjectsWithAttributes;
        List<String> subjects;
        List<String> expectedAssignments;
    }

    private void initClient(String host, boolean throwOnCallackError, boolean shouldDeleteCacheFiles, boolean isGracefulMode, String apiKey) {
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
                        if (throwOnCallackError) {
                            throw new RuntimeException("Unable to initialize: "+errorMessage);
                        }
                        lock.countDown();
                    }
                })
                .buildAndInit();

        // Wait for initialization to succeed or fail, up to 10 seconds, before continuing
        try {
            if (!lock.await(10000, TimeUnit.MILLISECONDS)) {
                throw new InterruptedException("Request for RAC did not complete within timeout");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @After
    public void clearCaches() {
        String[] apiKeys = { DUMMY_API_KEY, DUMMY_OTHER_API_KEY };
        for (String apiKey : apiKeys) {
            clearCacheFile(apiKey);
        }
    }

    private void clearCacheFile(String apiKey) {
        ConfigCacheFile cacheFile = new ConfigCacheFile(ApplicationProvider.getApplicationContext(), apiKey);
        cacheFile.delete();
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
            .getTypedAssignment(anyString(), anyString(), any(SubjectAttributes.class));

        assertNull(spyClient.getAssignment("subject1", "experiment1"));
        assertNull(spyClient.getAssignment("subject1", "experiment1", new SubjectAttributes()));

        assertNull(spyClient.getBooleanAssignment("subject1", "experiment1"));
        assertNull(spyClient.getBooleanAssignment("subject1", "experiment1", new SubjectAttributes()));

        assertNull(spyClient.getDoubleAssignment("subject1", "experiment1"));
        assertNull(spyClient.getDoubleAssignment("subject1", "experiment1", new SubjectAttributes()));

        assertNull(spyClient.getStringAssignment("subject1", "experiment1"));
        assertNull(spyClient.getStringAssignment("subject1", "experiment1", new SubjectAttributes()));

        assertNull(spyClient.getParsedJSONAssignment("subject1", "experiment1"));
        assertNull(spyClient.getParsedJSONAssignment("subject1", "experiment1", new SubjectAttributes()));

        assertNull(spyClient.getJSONStringAssignment("subject1", "experiment1"));
        assertNull(spyClient.getJSONStringAssignment("subject1", "experiment1", new SubjectAttributes()));
    }

    @Test
    public void testErrorGracefulModeOff() {
        initClient(TEST_HOST, false, true, false, DUMMY_API_KEY);

        EppoClient realClient = EppoClient.getInstance();
        EppoClient spyClient = spy(realClient);
        doThrow(new RuntimeException("Exception thrown by mock"))
            .when(spyClient)
            .getTypedAssignment(anyString(), anyString(), any(SubjectAttributes.class));

        assertThrows(RuntimeException.class, () -> spyClient.getAssignment("subject1", "experiment1"));
        assertThrows(RuntimeException.class, () -> spyClient.getAssignment("subject1", "experiment1", new SubjectAttributes()));

        assertThrows(RuntimeException.class, () -> spyClient.getBooleanAssignment("subject1", "experiment1"));
        assertThrows(RuntimeException.class, () -> spyClient.getBooleanAssignment("subject1", "experiment1", new SubjectAttributes()));

        assertThrows(RuntimeException.class, () -> spyClient.getDoubleAssignment("subject1", "experiment1"));
        assertThrows(RuntimeException.class, () -> spyClient.getDoubleAssignment("subject1", "experiment1", new SubjectAttributes()));

        assertThrows(RuntimeException.class, () -> spyClient.getStringAssignment("subject1", "experiment1"));
        assertThrows(RuntimeException.class, () -> spyClient.getStringAssignment("subject1", "experiment1", new SubjectAttributes()));

        assertThrows(RuntimeException.class, () -> spyClient.getParsedJSONAssignment("subject1", "experiment1"));
        assertThrows(RuntimeException.class, () -> spyClient.getParsedJSONAssignment("subject1", "experiment1", new SubjectAttributes()));

        assertThrows(RuntimeException.class, () -> spyClient.getJSONStringAssignment("subject1", "experiment1"));
        assertThrows(RuntimeException.class, () -> spyClient.getJSONStringAssignment("subject1", "experiment1", new SubjectAttributes()));
    }

    private void runTestCases() {
        try {
            int testsRan = 0;
            AssetManager assets = ApplicationProvider.getApplicationContext().getAssets();
            for (String path : assets.list("assignment-v2")) {
                testsRan += runTestCaseFileStream(assets.open("assignment-v2/" + path));
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

        // wait for a bit since cache file is loaded asynchronously
        waitForPopulatedCache();

        // Then reinitialize with a bad host so we know it's using the cached RAC built from the first initialization
        initClient(INVALID_HOST, true, false, false, DUMMY_API_KEY); // invalid port to force to use cache

        runTestCases();
    }

    private int runTestCaseFileStream(InputStream testCaseStream) throws IOException {
        String json = IOUtils.toString(testCaseStream, Charsets.toCharset("UTF8"));
        AssignmentTestCase testCase = gson.fromJson(json, AssignmentTestCase.class);
        switch (testCase.valueType) {
            case NUMERIC:
                List<Double> expectedDoubleAssignments = Converter.convertToDouble(testCase.expectedAssignments);
                List<Double> actualDoubleAssignments = this.getDoubleAssignments(testCase);
                assertEquals(expectedDoubleAssignments, actualDoubleAssignments);
                return actualDoubleAssignments.size();
            case BOOLEAN:
                List<Boolean> expectedBooleanAssignments = Converter.convertToBoolean(testCase.expectedAssignments);
                List<Boolean> actualBooleanAssignments = this.getBooleanAssignments(testCase);
                assertEquals(expectedBooleanAssignments, actualBooleanAssignments);
                return actualBooleanAssignments.size();
            case JSON:
                // test parsed json
                List<JsonElement> actualParsedJSONAssignments = this.getJSONAssignments(testCase);
                List<String> actualJSONStringAssignments = actualParsedJSONAssignments.stream().map(x -> x.toString()).collect(Collectors.toList());

                assertEquals(testCase.expectedAssignments, actualJSONStringAssignments);
                return actualParsedJSONAssignments.size();
            default:
                List<String> actualStringAssignments = this.getStringAssignments(testCase);
                assertEquals(testCase.expectedAssignments, actualStringAssignments);
                return actualStringAssignments.size();
        }
    }

    private List<?> getAssignments(AssignmentTestCase testCase, AssignmentValueType valueType) {
        EppoClient client = EppoClient.getInstance();
        if (testCase.subjectsWithAttributes != null) {
            return testCase.subjectsWithAttributes.stream()
                    .map(subject -> {
                        try {
                            switch (valueType) {
                                case NUMERIC:
                                    return client.getDoubleAssignment(subject.subjectKey, testCase.experiment,
                                            subject.subjectAttributes);
                                case BOOLEAN:
                                    return client.getBooleanAssignment(subject.subjectKey, testCase.experiment,
                                            subject.subjectAttributes);
                                case JSON:
                                    return client.getParsedJSONAssignment(subject.subjectKey, testCase.experiment,
                                            subject.subjectAttributes);
                                default:
                                    return client.getStringAssignment(subject.subjectKey, testCase.experiment,
                                            subject.subjectAttributes);
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }).collect(Collectors.toList());
        }
        return testCase.subjects.stream()
                .map(subject -> {
                    try {
                        switch (valueType) {
                            case NUMERIC:
                                return client.getDoubleAssignment(subject, testCase.experiment);
                            case BOOLEAN:
                                return client.getBooleanAssignment(subject, testCase.experiment);
                            case JSON:
                                return client.getParsedJSONAssignment(subject, testCase.experiment);
                            default:
                                return client.getStringAssignment(subject, testCase.experiment);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toList());
    }

    private List<String> getStringAssignments(AssignmentTestCase testCase) {
        return (List<String>) this.getAssignments(testCase, AssignmentValueType.STRING);
    }

    private List<Double> getDoubleAssignments(AssignmentTestCase testCase) {
        return (List<Double>) this.getAssignments(testCase, AssignmentValueType.NUMERIC);
    }

    private List<Boolean> getBooleanAssignments(AssignmentTestCase testCase) {
        return (List<Boolean>) this.getAssignments(testCase, AssignmentValueType.BOOLEAN);
    }

    private List<JsonElement> getJSONAssignments(AssignmentTestCase testCase) {
        return (List<JsonElement>) this.getAssignments(testCase, AssignmentValueType.JSON);
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

        Field httpClientOverrideField = null;
        try {
            // Use reflection to set the httpClientOverride field
            httpClientOverrideField = EppoClient.class.getDeclaredField("httpClientOverride");
            httpClientOverrideField.setAccessible(true);
            httpClientOverrideField.set(null, mockHttpClient);


            initClient(TEST_HOST, true, true, false, DUMMY_API_KEY);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        } finally {
            if (httpClientOverrideField != null) {
                try {
                    httpClientOverrideField.set(null, null);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
                httpClientOverrideField.setAccessible(false);
            }
        }

        String result = EppoClient.getInstance().getStringAssignment("dummy subject", "dummy flag");
        assertNull(result);
    }

    @Test
    public void testCachedBadResponseRequiresFetch() {
        // Populate the cache with a bad response
        ConfigCacheFile cacheFile = new ConfigCacheFile(ApplicationProvider.getApplicationContext(), safeCacheKey(DUMMY_API_KEY));
        cacheFile.setContents("{ invalid }");

        initClient(TEST_HOST, true, false, false, DUMMY_API_KEY);

        String assignment = EppoClient.getInstance().getStringAssignment("6255e1a7d1a3025a26078b95", "randomization_algo");
        assertEquals("green", assignment);
    }

    @Test
    public void testEmptyFlagsResponseRequiresFetch() {
        // Populate the cache with a bad response
        ConfigCacheFile cacheFile = new ConfigCacheFile(ApplicationProvider.getApplicationContext(), safeCacheKey(DUMMY_API_KEY));
        cacheFile.setContents("{\"flags\": {}}");

        initClient(TEST_HOST, true, false, false, DUMMY_API_KEY);
        String assignment = EppoClient.getInstance().getStringAssignment("6255e1a7d1a3025a26078b95", "randomization_algo");
        assertEquals("green", assignment);
    }

    @Test
    public void testDifferentCacheFilesPerKey() {
        initClient(TEST_HOST, true, true, false, DUMMY_API_KEY);
        // API Key 1 will fetch and then populate its cache with the usual test data
        Boolean apiKey1Assignment = EppoClient.getInstance().getBooleanAssignment("subject-2", "experiment_with_boolean_variations");
        assertFalse(apiKey1Assignment);

        // Pre-seed a different flag configuration for the other API Key
        ConfigCacheFile cacheFile2 = new ConfigCacheFile(ApplicationProvider.getApplicationContext(), safeCacheKey(DUMMY_OTHER_API_KEY));
        cacheFile2.setContents("{\n" +
                "  \"flags\": {\n" +
                "    \"8fc1fb33379d78c8a9edbf43afd6703a\": {\n" +
                "      \"subjectShards\": 10000,\n" +
                "      \"enabled\": true,\n" +
                "      \"rules\": [\n" +
                "        {\n" +
                "          \"allocationKey\": \"mock-allocation\",\n" +
                "          \"conditions\": []\n" +
                "        }\n" +
                "      ],\n" +
                "      \"allocations\": {\n" +
                "        \"mock-allocation\": {\n" +
                "          \"percentExposure\": 1,\n" +
                "          \"statusQuoVariationKey\": null,\n" +
                "          \"shippedVariationKey\": null,\n" +
                "          \"holdouts\": [],\n" +
                "          \"variations\": [\n" +
                "            {\n" +
                "              \"name\": \"on\",\n" +
                "              \"value\": \"true\",\n" +
                "              \"typedValue\": true,\n" +
                "              \"shardRange\": {\n" +
                "                \"start\": 0,\n" +
                "                \"end\": 10000\n" +
                "              }\n" +
                "            }" +
                "          ]\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}");

        initClient(TEST_HOST, true, false, false, DUMMY_OTHER_API_KEY);

        // Ensure API key 2 uses its cache
        Boolean apiKey2Assignment = EppoClient.getInstance().getBooleanAssignment("subject-2", "experiment_with_boolean_variations");
        assertTrue(apiKey2Assignment);

        // Reinitialize API key 1 to be sure it used its cache
        initClient(TEST_HOST, true, false, false, DUMMY_API_KEY);
        // API Key 1 will fetch and then populate its cache with the usual test data
        apiKey1Assignment = EppoClient.getInstance().getBooleanAssignment("subject-2", "experiment_with_boolean_variations");
        assertFalse(apiKey1Assignment);
    }

    private void waitForPopulatedCache() {
        long waitStart = System.currentTimeMillis();
        long waitEnd = waitStart + 10 * 1000; // allow up to 10 seconds
        boolean cachePopulated = false;
        try {
            File file = new File(ApplicationProvider.getApplicationContext().getFilesDir(), cacheFileName(safeCacheKey(DUMMY_API_KEY)));
            while (!cachePopulated) {
                if (System.currentTimeMillis() > waitEnd) {
                    throw new InterruptedException("Cache file never populated; assuming configuration error");
                }
                long expectedMinimumSizeInBytes = 4000; // At time of writing cache size is 4354
                cachePopulated = file.exists() && file.length() > expectedMinimumSizeInBytes;
                if (!cachePopulated) {
                    Thread.sleep(100);
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void waitForNonNullAssignment() {
        long waitStart = System.currentTimeMillis();
        long waitEnd = waitStart + 15 * 1000; // allow up to 15 seconds
        String assignment = null;
        try {
            while (assignment == null) {
                if (System.currentTimeMillis() > waitEnd) {
                    throw new InterruptedException("Non-null assignment never received; assuming configuration not loaded");
                }
                // Uses third subject in test-case-0
                assignment = EppoClient.getInstance().getStringAssignment("6255e1a7fc33a9c050ce9508", "randomization_algo");
                if (assignment == null) {
                    Thread.sleep(100);
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
