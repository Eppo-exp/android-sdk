package cloud.eppo.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import static cloud.eppo.android.ConfigCacheFile.ENC_CACHE_FILE_NAME;
import static cloud.eppo.android.ConfigCacheFile.PT_CACHE_FILE_NAME;

import android.content.res.AssetManager;
import android.util.Log;

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

    private void initClient(String host, boolean throwOnCallackError, boolean cacheToEncryptedFile, boolean deleteCacheFiles) throws InterruptedException {
        if (deleteCacheFiles) {
            deleteFileIfExists(ENC_CACHE_FILE_NAME);
            deleteFileIfExists(PT_CACHE_FILE_NAME);
        }

        new EppoClient.Builder()
            .application(ApplicationProvider.getApplicationContext())
            .apiKey("mock-api-key")
            .host(host)
            .useEncryptedCacheFile(cacheToEncryptedFile)
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
            initClient(HOST, true, true, true);
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail();
        }
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
    public void testAssignments()  {
        try {
            AssetManager assets = ApplicationProvider.getApplicationContext().getAssets();
            for (String path : assets.list("assignment-v2")) {
                runTestCaseFileStream(assets.open("assignment-v2/" + path));
            }
        } catch (Exception e) {
            fail();
        }
    }

    private void testCachedAssignments(boolean useEncryptedFile) {
        try {
            initClient(HOST, false, useEncryptedFile, true); // ensure cache is populated
            initClient(INVALID_HOST, false, useEncryptedFile, false); // invalid port to force to use cache

            AssetManager assets = ApplicationProvider.getApplicationContext().getAssets();
            for (String path : assets.list("assignment-v2")) {
                runTestCaseFileStream(assets.open("assignment-v2/" + path));
            }
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void testCachedAssignmentsPlaintextFile() {
        testCachedAssignments(false);
    }

    @Test
    public void testCachedAssignmentsEncryptedFile() {
        testCachedAssignments(true);
    }

    private void runTestCaseFileStream(InputStream testCaseStream) throws IOException {
        String json = IOUtils.toString(testCaseStream, Charsets.toCharset("UTF8"));
        AssignmentTestCase testCase = gson.fromJson(json, AssignmentTestCase.class);
        List<String> assignments = getAssignments(testCase);
        assertEquals(testCase.expectedAssignments, assignments);
        Log.i(TAG, "Evaluated " + assignments.size() + " assignments");
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
        try {
            InputStream in = ApplicationProvider.getApplicationContext().getAssets().open("rac-experiments-v2.json");
            return IOUtils.toString(in, Charsets.toCharset("UTF8"));
        } catch (IOException e) {
            throw new RuntimeException("Error reading mock RAC data", e);
        }
    }
}