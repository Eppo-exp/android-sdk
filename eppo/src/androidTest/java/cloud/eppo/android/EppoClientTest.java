package cloud.eppo.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import static cloud.eppo.android.ConfigCacheFile.CACHE_FILE_NAME;

import android.content.res.AssetManager;

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
import cloud.eppo.android.dto.adapters.EppoValueAdapter;

public class EppoClientTest {
    private static final String TEST_HOST = "https://us-central1-eppo-qa.cloudfunctions.net/serveGitHubRacTestFile";
    private static final String INVALID_HOST = "https://thisisabaddomainforthistest.com";
    private Gson gson = new GsonBuilder()
            .registerTypeAdapter(EppoValue.class, new EppoValueAdapter())
            .registerTypeAdapter(AssignmentValueType.class, new AssignmentValueTypeAdapter(AssignmentValueType.STRING))
            .create();
    private CountDownLatch lock = new CountDownLatch(1);

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

    private void deleteFileIfExists(String fileName) {
        File file = new File(ApplicationProvider.getApplicationContext().getFilesDir(), fileName);
        if (file.exists()) {
            file.delete();
        }
    }

    private void deleteCacheFiles() {
        deleteFileIfExists(CACHE_FILE_NAME);
    }

    private void initClient(String host, boolean throwOnCallackError, boolean shouldDeleteCacheFiles, boolean isGracefulMode)
            throws InterruptedException {
        if (shouldDeleteCacheFiles) {
            deleteCacheFiles();
        }

        new EppoClient.Builder()
                .application(ApplicationProvider.getApplicationContext())
                .apiKey("mock-api-key")
                .isGracefulMode(isGracefulMode)
                .host(host)
                .callback(new InitializationCallback() {
                    @Override
                    public void onCompleted() {
                        lock.countDown();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        if (throwOnCallackError) {
                            throw new RuntimeException("Unable to initialize: "+errorMessage);
                        }
                        lock.countDown();
                    }
                })
                .buildAndInit();

        if(!lock.await(10000, TimeUnit.MILLISECONDS)) {
            throw new RuntimeException("Request for RAC did not complete within timeout");
        }
    }

    @After
    public void teardown() {
        deleteCacheFiles();
    }

    @Test
    public void testAssignments() {
        try {
            initClient(TEST_HOST, true, true, false);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        runTestCases();
    }

    @Test
    public void testErrorGracefulModeOn() {
        try {
            initClient(TEST_HOST, false, true, true);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

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
        try {
            initClient(TEST_HOST, false, true, false);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

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
        try {
            // First initialize successfully
            initClient(TEST_HOST, false, true, false); // ensure cache is populated

            // wait for a bit since cache file is loaded asynchronously
            waitForPopulatedCache();

            // Then reinitialize with a bad host so we know it's using the cached RAC built from the first initialization
            initClient(INVALID_HOST, false, false, false); // invalid port to force to use cache
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
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


            initClient(TEST_HOST, true, true, false);
        } catch (InterruptedException | NoSuchFieldException | IllegalAccessException e) {
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
    public void testCachedBadResponseAllowsLaterFetching() {
        // Populate the cache with a bad response
        ConfigCacheFile cacheFile = new ConfigCacheFile(ApplicationProvider.getApplicationContext());
        cacheFile.delete();
        try {
            cacheFile.getOutputWriter().write("{}");
            cacheFile.getOutputWriter().close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            initClient(TEST_HOST, false, false, false);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        };

        String result = EppoClient.getInstance().getStringAssignment("dummy subject", "dummy flag");
        assertNull(result);
        // Failure callback will have fired from cache read error, but configuration request will still be fired off on init
        // Wait for the configuration request to load the configuration
        waitForNonNullAssignment();
        String assignment = EppoClient.getInstance().getStringAssignment("6255e1a7fc33a9c050ce9508", "randomization_algo");
        assertEquals("control", assignment);
    }

    private void waitForPopulatedCache() {
        long waitStart = System.currentTimeMillis();
        long waitEnd = waitStart + 10 * 1000; // allow up to 10 seconds
        boolean cachePopulated = false;
        try {
            File file = new File(ApplicationProvider.getApplicationContext().getFilesDir(), CACHE_FILE_NAME);
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
