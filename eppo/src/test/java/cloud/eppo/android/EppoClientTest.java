package cloud.eppo.android;

import static org.junit.Assert.assertEquals;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import cloud.eppo.android.dto.EppoValue;
import cloud.eppo.android.dto.SubjectAttributes;
import cloud.eppo.android.dto.adapters.EppoValueAdapter;

public class EppoClientTest {
    private static final int TEST_PORT = 4001;
    private WireMockServer mockServer;
    private Gson gson = new GsonBuilder().registerTypeAdapter(EppoValue.class, new EppoValueAdapter()).create();
    private CountDownLatch lock = new CountDownLatch(1);

    static class SubjectWithAttributes {
        String subjectKey;
        SubjectAttributes subjectAttributes;
    }


    static class AssignmentTestCase {
        String experiment;
        List<SubjectWithAttributes> subjectsWithAttributes;
        List<String> subjects;
        List<String> expectedAssignments;
    }

    @Before
    public void init() throws InterruptedException {
        setupMockRacServer();

        new EppoClient.Builder()
                .apiKey("mock-api-key")
                .host("http://localhost:4001")
                .callback(new InitializationCallback() {
                    @Override
                    public void onCompleted() {
                        lock.countDown();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        throw new RuntimeException("Unable to initialize");
                    }
                })
                .buildAndInit();

        lock.await(2000, TimeUnit.MILLISECONDS);
    }

    @After
    public void teardown() {
        this.mockServer.stop();
    }

    private void setupMockRacServer() {
        this.mockServer = new WireMockServer(TEST_PORT);
        this.mockServer.start();
        String racResponseJson = getMockRandomizedAssignmentResponse();
        this.mockServer.stubFor(WireMock.get(WireMock.urlMatching(".*randomized_assignment.*")).willReturn(WireMock.okJson(racResponseJson)));
    }

    @Test
    public void testAssignments() throws IOException {
        File testCaseFolder = new File("src/test/resources/assignment-v2/");
        File[] testCaseFiles = testCaseFolder.listFiles();
        for (File testCaseFile : testCaseFiles) {
            runTestCaseFile(testCaseFile);
        }
    }

    private void runTestCaseFile(File testCaseFile) throws IOException {
        String json = FileUtils.readFileToString(testCaseFile, "UTF8");
        AssignmentTestCase testCase = gson.fromJson(json, AssignmentTestCase.class);
        List<String> assignments = getAssignments(testCase);
        assertEquals(testCase.expectedAssignments, assignments);
    }

    private List<String> getAssignments(AssignmentTestCase testCase) {
        EppoClient client = EppoClient.getInstance();
        if (testCase.subjectsWithAttributes != null) {
            return testCase.subjectsWithAttributes.stream()
                    .map(subject -> {
                        try {
                            return client.getAssignment(subject.subjectKey, testCase.experiment, subject.subjectAttributes);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }).collect(Collectors.toList());
        }
        return testCase.subjects.stream()
                .map(subject -> {
                    try {
                        return client.getAssignment(subject, testCase.experiment);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toList());
    }

    private static String getMockRandomizedAssignmentResponse() {
        File mockRacResponse = new File("src/test/resources/rac-experiments-v2.json");
        try {
            return FileUtils.readFileToString(mockRacResponse, "UTF8");
        } catch (Exception e) {
            throw new RuntimeException("Error reading mock RAC data", e);
        }
    }
}