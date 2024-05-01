package cloud.eppo.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import static cloud.eppo.android.ConfigCacheFile.CACHE_FILE_NAME;
import static cloud.eppo.android.util.Utils.logTag;

import android.content.Context;
import android.content.SharedPreferences;
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

import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.SubjectAttributes;
import cloud.eppo.android.helpers.AssignmentTestCase;
import cloud.eppo.android.helpers.AssignmentTestCaseDeserializer;
import cloud.eppo.android.helpers.SubjectAssignment;

public class EppoClientTest {
    private static final String TAG = logTag(EppoClient.class);
    private static final String TEST_HOST = "https://us-central1-eppo-qa.cloudfunctions.net/serveGitHubRacTestFile";
    private static final String INVALID_HOST = "https://thisisabaddomainforthistest.com";
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(AssignmentTestCase.class, new AssignmentTestCaseDeserializer())
            .create();

    private void deleteFileIfExists(String fileName) {
        File file = new File(ApplicationProvider.getApplicationContext().getFilesDir(), fileName);
        if (file.exists()) {
            file.delete();
        }
    }

    private void deleteCacheFiles() {
        deleteFileIfExists(CACHE_FILE_NAME);
        SharedPreferences sharedPreferences = ApplicationProvider.getApplicationContext().getSharedPreferences("eppo", Context.MODE_PRIVATE);
        sharedPreferences.edit().clear().commit();
    }

    private void initClient(String host, boolean throwOnCallackError, boolean shouldDeleteCacheFiles, boolean isGracefulMode) {
        if (shouldDeleteCacheFiles) {
            deleteCacheFiles();
        }

        CountDownLatch lock = new CountDownLatch(1);

        new EppoClient.Builder()
                .application(ApplicationProvider.getApplicationContext())
                .apiKey("mock-api-key")
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
    public void teardown() {
        deleteCacheFiles();
    }

    @Test
    public void testAssignments() {
        initClient(TEST_HOST, true, true, false);
        runTestCases();
    }

    @Test
    public void testErrorGracefulModeOn() {
        initClient(TEST_HOST, false, true, true);

        EppoClient realClient = EppoClient.getInstance();
        EppoClient spyClient = spy(realClient);
        doThrow(new RuntimeException("Exception thrown by mock"))
            .when(spyClient)
            .getTypedAssignment(anyString(), anyString(), any(SubjectAttributes.class), any(EppoValue.class));

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
        initClient(TEST_HOST, false, true, false);

        EppoClient realClient = EppoClient.getInstance();
        EppoClient spyClient = spy(realClient);
        doThrow(new RuntimeException("Exception thrown by mock"))
            .when(spyClient)
            .getTypedAssignment(anyString(), anyString(), any(SubjectAttributes.class), any(EppoValue.class));

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
            for (String path : assets.list("tests")) {
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
        initClient(TEST_HOST, false, true, false); // ensure cache is populated

        // wait for a bit since cache file is loaded asynchronously
        waitForPopulatedCache();

        // Then reinitialize with a bad host so we know it's using the cached RAC built from the first initialization
        initClient(INVALID_HOST, false, false, false); // invalid port to force to use cache

        runTestCases();
    }

    private int runTestCaseFileStream(InputStream testCaseStream) throws IOException {
        String json = IOUtils.toString(testCaseStream, Charsets.toCharset("UTF8"));
        AssignmentTestCase testCase = gson.fromJson(json, AssignmentTestCase.class);
        String flagKey = testCase.getFlag();
        EppoValue defaultValue = testCase.getDefaultValue();
        EppoClient eppoClient = EppoClient.getInstance();

        for (SubjectAssignment subjectAssignment : testCase.getSubjects()) {
            String subjectKey = subjectAssignment.getSubjectKey();
            SubjectAttributes subjectAttributes = subjectAssignment.getSubjectAttributes();

            // Depending on the variation type, we will need to change which assignment method we call and how we get the default value
            switch (testCase.getVariationType()) {
                case BOOLEAN:
                    boolean boolAssignment = eppoClient.getBooleanAssignment(flagKey, subjectKey, subjectAttributes, defaultValue.boolValue());
                    assertAssignment(flagKey, subjectAssignment, boolAssignment);
                case INTEGER:
                    int intAssignment = eppoClient.getIntegerAssignment(flagKey, subjectKey, subjectAttributes, Double.valueOf(defaultValue.doubleValue()).intValue());
                    assertAssignment(flagKey, subjectAssignment, intAssignment);
                case NUMERIC:
                    double doubleAssignment = eppoClient.getDoubleAssignment(flagKey, subjectKey, subjectAttributes, defaultValue.doubleValue());
                    assertAssignment(flagKey, subjectAssignment, doubleAssignment);
                case STRING:
                    String stringAssignment = eppoClient.getStringAssignment(flagKey, subjectKey, subjectAttributes, defaultValue.stringValue());
                    assertAssignment(flagKey, subjectAssignment, stringAssignment);
                case JSON:
                    JsonElement jsonAssignment = eppoClient.getJSONAssignment(flagKey, subjectKey, subjectAttributes, defaultValue.jsonValue());
                    assertAssignment(flagKey, subjectAssignment, jsonAssignment);
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

        String failureMessage = "Incorrect "+flagKey+" assignment for subject "+expectedSubjectAssignment.getSubjectKey()+
                "\n  Expected: "+expectedSubjectAssignment.getAssignment().toString()+
                "\n  Received: "+assignment.toString();

        if (assignment instanceof Boolean) {
            assertEquals(failureMessage, (Boolean)assignment, expectedSubjectAssignment.getAssignment().boolValue());
        } else if (assignment instanceof Integer) {
            assertEquals(failureMessage, ((Integer)assignment).intValue(), Double.valueOf(expectedSubjectAssignment.getAssignment().doubleValue()).intValue());
        } else if (assignment instanceof Double) {
            assertEquals(failureMessage, (Double)assignment, expectedSubjectAssignment.getAssignment().doubleValue(), 0.000001);
        } else if (assignment instanceof String) {
            assertEquals(failureMessage, assignment, expectedSubjectAssignment.getAssignment().stringValue());
        } else if (assignment instanceof JsonElement) {
            assertEquals(failureMessage, assignment, expectedSubjectAssignment.getAssignment().jsonValue());
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

        Field httpClientOverrideField = null;
        try {
            // Use reflection to set the httpClientOverride field
            httpClientOverrideField = EppoClient.class.getDeclaredField("httpClientOverride");
            httpClientOverrideField.setAccessible(true);
            httpClientOverrideField.set(null, mockHttpClient);


            initClient(TEST_HOST, true, true, false);
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

        String result = EppoClient.getInstance().getStringAssignment("dummy subject", "dummy flag", "not-populated");
        assertEquals("not-populated", result);
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

        initClient(TEST_HOST, false, false, false);

        String result = EppoClient.getInstance().getStringAssignment("dummy subject", "dummy flag", "not-populated");
        assertEquals("not-populated", result);
        // Failure callback will have fired from cache read error, but configuration request will still be fired off on init
        // Wait for the configuration request to load the configuration
        waitForNonNullAssignment();
        String assignment = EppoClient.getInstance().getStringAssignment("6255e1a7fc33a9c050ce9508", "randomization_algo", "");
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
                // TODO: update
                // Uses third subject in test-case-0
                assignment = EppoClient.getInstance().getStringAssignment("6255e1a7fc33a9c050ce9508", "randomization_algo", "");
                if (assignment.equals("")) {
                    Thread.sleep(100);
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
