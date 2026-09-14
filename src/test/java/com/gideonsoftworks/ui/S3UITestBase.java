package com.gideonsoftworks.ui;

import com.adobe.testing.s3mock.junit5.S3MockExtension;
import com.gideonsoftworks.gcore.GBookmark;
import com.gideonsoftworks.gja.GJobDescription;

import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import javax.swing.*;
import java.net.URI;

/**
 * UI-test harness for the S3 protocol. Reuses the window/robot/job/menu machinery
 * from {@link UITestBase}, swapping the SFTP server for an in-process Adobe S3Mock
 * (via the JUnit 5 extension — no Docker). Auth is access key + secret (the v1
 * default). Each concrete test seeds the bucket, calls {@link #loginToServer()},
 * then drives the browser.
 */
abstract class S3UITestBase extends UITestBase {

    static final String BUCKET = "ui-bucket";
    static final String ACCESS_KEY = "accessKey";
    static final String SECRET_KEY = "secretKey";
    static final String REGION = "us-east-1";

    @RegisterExtension
    static final S3MockExtension S3_MOCK = S3MockExtension.builder()
            .withInitialBuckets(BUCKET)
            .withSecureConnection(false)
            .silent()
            .build();

    // Subclasses may set these before loginToServer() to use temporary credentials.
    String authMethod = null;     // "session" for temporary creds
    String sessionToken = null;

    String endpoint() {
        return "http://localhost:" + S3_MOCK.getHttpPort();
    }

    /** No SFTP server — Adobe S3Mock is managed by the @RegisterExtension above. */
    @Override
    void startBackend() {
        // intentionally empty
    }

    /** Connect the remote browser to the mock bucket via access key + secret. */
    @Override
    void loginToServer() throws Exception {
        // connectForBookmark() itself starts the login job, so register the waiter first.
        JobWaiter waiter = waitForJob(GJobDescription.LOGIN_JOB_TYPE);
        SwingUtilities.invokeAndWait(() -> {
            GBookmark bm = new GBookmark(BUCKET, BUCKET, "/",
                    ACCESS_KEY, SECRET_KEY, 0, GBookmark.G_S3_PROTOCOL);
            bm.setS3AccessKey(ACCESS_KEY);
            bm.setS3SecretKey(SECRET_KEY);
            bm.setS3Region(REGION);
            bm.setS3EndpointUrl(endpoint());
            bm.setS3AuthMethod(authMethod);
            bm.setS3SessionToken(sessionToken);
            bm.setNeverSavePassword(true);
            bm.setLocalHomePath(localRoot.toAbsolutePath().toString());
            remoteBrowser().connectForBookmark(bm);
        });
        waiter.await(20);
        robot.waitForIdle();

        if (localBrowser() != null) {
            navigateLocalTo(localRoot);
        }
    }

    // ---- S3 seeding / inspection helpers (direct SDK, bypassing the UI) ----

    S3Client directClient() {
        return S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .region(Region.of(REGION))
                .endpointOverride(URI.create(endpoint()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true).build())
                .build();
    }

    void seedObject(String key, String content) {
        try (S3Client c = directClient()) {
            c.putObject(PutObjectRequest.builder().bucket(BUCKET).key(key).build(),
                    RequestBody.fromString(content));
        }
    }

    boolean objectExists(String key) {
        try (S3Client c = directClient()) {
            c.headObject(HeadObjectRequest.builder().bucket(BUCKET).key(key).build());
            return true;
        } catch (S3Exception e) {
            return false;
        }
    }

    long countObjects(String prefix) {
        try (S3Client c = directClient()) {
            return c.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(BUCKET).prefix(prefix).build()).keyCount();
        }
    }
}
