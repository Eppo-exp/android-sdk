package cloud.eppo.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static cloud.eppo.android.ConfigCacheFile.CACHE_FILE_NAME;

import android.content.res.AssetManager;
import androidx.test.core.app.ApplicationProvider;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.SubjectAttributes;
import cloud.eppo.android.dto.adapters.EppoValueAdapter;

public class EppoClientTest {
    private static final String TAG = EppoClientTest.class.getSimpleName();
    private static final int TEST_PORT = 4001;
    private static final String HOST = "http://localhost:" + TEST_PORT;
    private static final String INVALID_HOST = "http://localhost:" + (TEST_PORT + 1);
    private WireMockServer mockServer;
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

    private void initClient(String host, boolean throwOnCallackError, boolean shouldDeleteCacheFiles)
            throws InterruptedException {
        if (shouldDeleteCacheFiles) {
            deleteCacheFiles();
        }

        new EppoClient.Builder()
                .application(ApplicationProvider.getApplicationContext())
                .apiKey("mock-api-key")
                .host(host)
                .callback(new InitializationCallback() {
                    @Override
                    public void onCompleted() {
                        lock.countDown();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        if (throwOnCallackError) {
                            throw new RuntimeException("Unable to initialize");
                        }
                        lock.countDown();
                    }
                })
                .buildAndInit();

        lock.await(2000, TimeUnit.MILLISECONDS);
    }

    @Before
    public void init() {
        setupMockRacServer();

        try {
            initClient(HOST, true, true);
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail();
        }
    }

    @After
    public void teardown() {
        this.mockServer.stop();
        deleteCacheFiles();
    }

    private void setupMockRacServer() {
        this.mockServer = new WireMockServer(TEST_PORT);
        this.mockServer.start();
        String racResponseJson = getMockRandomizedAssignmentResponse();
        this.mockServer.stubFor(WireMock.get(WireMock.urlMatching(".*randomized_assignment.*"))
                .willReturn(WireMock.okJson(racResponseJson)));
    }

    @Test
    public void testAssignments() {
        runTestCases();
    }

//    @Test
//    public void testCachedAssignments() {
//        try {
//            initClient(HOST, false, true); // ensure cache is populated
//            initClient(INVALID_HOST, false, false); // invalid port to force to use cache
//
//            // wait for a bit since file is loaded asynchronously
//            System.out.println("Sleeping for a bit to wait for cache population to complete");
//            Thread.sleep(1000);
//        } catch (Exception e) {
//            fail();
//        }
//        runTestCases();
//    }

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
            fail();
        }
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

    private static String getMockRandomizedAssignmentResponse() {
        try {
            InputStream in = ApplicationProvider.getApplicationContext().getAssets()
                    .open("rac-experiments-v3-hashed-keys.json");
            return IOUtils.toString(in, Charsets.toCharset("UTF8"));
        } catch (IOException e) {
            throw new RuntimeException("Error reading mock RAC data", e);
        }
    }
}
