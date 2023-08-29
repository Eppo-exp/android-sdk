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

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.SubjectAttributes;
import cloud.eppo.android.dto.adapters.EppoValueAdapter;
import cloud.eppo.android.util.Converter;

public class EppoClientTest {
    private static final String TAG = EppoClientTest.class.getSimpleName();
    private static final int TEST_PORT = 4001;
    private static final String HOST = "http://localhost:" + TEST_PORT;
    private static final String INVALID_HOST = "http://localhost:" + (TEST_PORT + 1);
    private WireMockServer mockServer;
    private Gson gson = new GsonBuilder().registerTypeAdapter(EppoValue.class, new EppoValueAdapter()).create();
    private CountDownLatch lock = new CountDownLatch(1);

    static class SubjectWithAttributes {
        String subjectKey;
        SubjectAttributes subjectAttributes;
    }

    static class AssignmentTestCase {
        String experiment;
        String valueType = "string";
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

    @Test
    public void testCachedAssignments() {
        try {
            initClient(HOST, false, true); // ensure cache is populated
            initClient(INVALID_HOST, false, false); // invalid port to force to use cache

            // wait for a bit since file is loaded asynchronously
            System.out.println("Sleeping for a bit to wait for cache population to complete");
            Thread.sleep(1000);
        } catch (Exception e) {
            fail();
        }
        runTestCases();
    }

    private int runTestCaseFileStream(InputStream testCaseStream) throws IOException {
        String json = IOUtils.toString(testCaseStream, Charsets.toCharset("UTF8"));
        AssignmentTestCase testCase = gson.fromJson(json, AssignmentTestCase.class);
        switch (testCase.valueType) {
            case "numeric":
                List<Double> expectedDoubleAssignments = Converter.convertToDecimal(testCase.expectedAssignments);
                List<Double> actualDoubleAssignments = this.getDoubleAssignments(testCase);
                assertEquals(expectedDoubleAssignments, actualDoubleAssignments);
                return actualDoubleAssignments.size();
            case "boolean":
                List<Boolean> expectedBooleanAssignments = Converter.convertToBoolean(testCase.expectedAssignments);
                List<Boolean> actualBooleanAssignments = this.getBooleanAssignments(testCase);
                assertEquals(expectedBooleanAssignments, actualBooleanAssignments);
                return actualBooleanAssignments.size();
            case "json":
                List<String> actualJSONAssignments = this.getJSONAssignments(testCase);
                assertEquals(testCase.expectedAssignments, actualJSONAssignments);
                return actualJSONAssignments.size();
            default:
                List<String> actualStringAssignments = this.getStringAssignments(testCase);
                assertEquals(testCase.expectedAssignments, actualStringAssignments);
                return actualStringAssignments.size();
        }
    }

    private List<?> getAssignments(AssignmentTestCase testCase, String valueType) {
        EppoClient client = EppoClient.getInstance();
        if (testCase.subjectsWithAttributes != null) {
            return testCase.subjectsWithAttributes.stream()
                    .map(subject -> {
                        try {
                            switch (valueType) {
                                case "numeric":
                                    return client.getDoubleAssignment(subject.subjectKey, testCase.experiment,
                                            subject.subjectAttributes);
                                case "boolean":
                                    return client.getBooleanAssignment(subject.subjectKey, testCase.experiment,
                                            subject.subjectAttributes);
                                case "json":
                                    return client.getJSONAssignment(subject.subjectKey, testCase.experiment,
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
                            case "numeric":
                                return client.getDoubleAssignment(subject, testCase.experiment);
                            case "boolean":
                                return client.getBooleanAssignment(subject, testCase.experiment);
                            case "json":
                                return client.getJSONAssignment(subject, testCase.experiment);
                            default:
                                return client.getStringAssignment(subject, testCase.experiment);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toList());
    }

    private List<String> getStringAssignments(AssignmentTestCase testCase) {
        return (List<String>) this.getAssignments(testCase, "string");
    }

    private List<Double> getDoubleAssignments(AssignmentTestCase testCase) {
        return (List<Double>) this.getAssignments(testCase, "numeric");
    }

    private List<Boolean> getBooleanAssignments(AssignmentTestCase testCase) {
        return (List<Boolean>) this.getAssignments(testCase, "boolean");
    }

    private List<String> getJSONAssignments(AssignmentTestCase testCase) {
        return (List<String>) this.getAssignments(testCase, "json");
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
