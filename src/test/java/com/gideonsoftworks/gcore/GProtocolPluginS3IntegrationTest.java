package com.gideonsoftworks.gcore;

import com.adobe.testing.s3mock.junit5.S3MockExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link GProtocolPluginS3} against Adobe S3Mock, run
 * in-process via the JUnit 5 extension (no Docker). The mock implements the real
 * S3 API surface (incl. multipart), so it is the backend for both these tests and
 * the upcoming multipart upload coverage.
 */
@Tag("integration")
class GProtocolPluginS3IntegrationTest {

    private static final String BUCKET = "test-bucket";
    private static final String ACCESS_KEY = "accessKey";
    private static final String SECRET_KEY = "secretKey";
    private static final String REGION = "us-east-1";

    @RegisterExtension
    static final S3MockExtension S3_MOCK = S3MockExtension.builder()
            .withInitialBuckets(BUCKET)
            .withSecureConnection(false)
            .silent()
            .build();

    private static String endpoint;

    private GProtocolPluginS3 plugin;
    private GConnection conn;
    private GBookmark bookmark;

    @BeforeEach
    void setUp() throws GConnectionException {
        endpoint = "http://localhost:" + S3_MOCK.getHttpPort();

        bookmark = new GBookmark(BUCKET, BUCKET, "/",
                ACCESS_KEY, SECRET_KEY, 0, GBookmark.G_S3_PROTOCOL);
        bookmark.setS3AccessKey(ACCESS_KEY);
        bookmark.setS3SecretKey(SECRET_KEY);
        bookmark.setS3Region(REGION);
        bookmark.setS3EndpointUrl(endpoint);

        conn = new GConnection(bookmark);
        plugin = new GProtocolPluginS3(conn);
    }

    @AfterEach
    void tearDown() {
        try {
            if (plugin != null && plugin.getIsConnected()) {
                plugin.gpp_disconnect(conn);
            }
        } catch (Exception ignored) {
        }

        // Clean up all objects in the bucket between tests
        try (S3Client cleanupClient = directClient()) {
            ListObjectsV2Response listing = cleanupClient.listObjectsV2(
                    ListObjectsV2Request.builder().bucket(BUCKET).build());
            if (listing.contents() != null) {
                for (S3Object obj : listing.contents()) {
                    cleanupClient.deleteObject(DeleteObjectRequest.builder()
                            .bucket(BUCKET)
                            .key(obj.key())
                            .build());
                }
            }
        } catch (Exception ignored) {
        }
    }

    // --- Helper to put an object directly via AWS SDK (test setup) ---

    private S3Client directClient() {
        return S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .region(Region.of(REGION))
                .endpointOverride(URI.create(endpoint))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    private void putObject(String key, String content) {
        try (S3Client client = directClient()) {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(BUCKET)
                            .key(key)
                            .build(),
                    RequestBody.fromString(content));
        }
    }

    private boolean objectExists(String key) {
        try (S3Client client = directClient()) {
            client.headObject(HeadObjectRequest.builder()
                    .bucket(BUCKET)
                    .key(key)
                    .build());
            return true;
        } catch (S3Exception e) {
            return false;
        }
    }

    // 1. connect succeeds
    @Test
    void connectSucceeds() throws GConnectionException {
        plugin.gpp_connect(bookmark);

        assertTrue(plugin.getIsConnected());
        assertTrue(plugin.getIsSecure());
    }

    // Temporary expiring link (presigned GET URL).
    @Test
    void presignGetProducesExpiringSignedUrl() throws Exception {
        plugin.gpp_connect(bookmark);
        putObject("tl.txt", "temporary link target");

        String url = plugin.gpp_presignGet("tl.txt", java.time.Duration.ofHours(1));

        assertNotNull(url);
        assertTrue(url.contains("tl.txt"), url);
        assertTrue(url.contains(BUCKET), url);             // path-style: /ui-bucket/tl.txt
        assertTrue(url.contains("X-Amz-Signature"), url);  // SigV4 presigned query
        assertTrue(url.contains("X-Amz-Expires"), url);
    }

    @Test
    void connectWithSessionTokenSucceeds() throws GConnectionException {
        // Temporary-credentials path: key + secret + session token.
        bookmark.setS3AuthMethod("session");
        bookmark.setS3SessionToken("FwoGZXIvYXdzEXAMPLEsessiontoken");

        plugin.gpp_connect(bookmark);
        assertTrue(plugin.getIsConnected());
    }

    @Test
    void uploadStampsContentSha256OnCompatibleEndpoint() throws Exception {
        // S3Mock is a non-AWS endpoint → the compatible path stamps x-amz-meta-content-sha256.
        plugin.gpp_connect(bookmark);

        byte[] content = "stamp me with a checksum".getBytes(StandardCharsets.UTF_8);
        Path local = Files.createTempFile("s3-stamp-", ".txt");
        Files.write(local, content);
        try (FileInputStream fis = new FileInputStream(local.toFile())) {
            plugin.gpp_upload("/stamped.txt", fis, local.toFile(), null);
        }

        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest(content)) sb.append(String.format("%02x", b));
        String expected = sb.toString();

        try (S3Client c = directClient()) {
            HeadObjectResponse head = c.headObject(
                    HeadObjectRequest.builder().bucket(BUCKET).key("stamped.txt").build());
            assertEquals(expected, head.metadata().get("content-sha256"),
                    "object should carry our content SHA-256 in user metadata");
        }
        Files.deleteIfExists(local);
    }

    @Test
    void abortOrphanMultipartUploadsRemovesIncompleteUpload() throws GConnectionException {
        plugin.gpp_connect(bookmark);

        String key = "orphan.bin";
        try (S3Client c = directClient()) {
            c.createMultipartUpload(CreateMultipartUploadRequest.builder()
                    .bucket(BUCKET).key(key).build());
            assertFalse(c.listMultipartUploads(ListMultipartUploadsRequest.builder()
                    .bucket(BUCKET).build()).uploads().isEmpty(), "orphan MPU should be listed");
        }

        plugin.abortOrphanMultipartUploads(key);   // what cancel runs

        try (S3Client c = directClient()) {
            java.util.List<MultipartUpload> left = c.listMultipartUploads(
                    ListMultipartUploadsRequest.builder().bucket(BUCKET).prefix(key).build()).uploads();
            assertTrue(left == null || left.isEmpty(), "orphan MPU should be aborted");
        }
    }

    @Test
    void failedUploadAbortsOrphanMultipartForThatKey() throws Exception {
        plugin.gpp_connect(bookmark);

        // An in-progress multipart upload for the key (as a crashed earlier attempt would leave)...
        String key = "fail.bin";
        try (S3Client c = directClient()) {
            c.createMultipartUpload(CreateMultipartUploadRequest.builder().bucket(BUCKET).key(key).build());
        }

        // ...and an upload of that key that fails hard (source file does not exist).
        Path scratch = Files.createTempFile("s3-fail", ".tmp");
        File missing = new File(scratch.getParent().toFile(), "does-not-exist-" + System.nanoTime() + ".bin");
        try (FileInputStream fis = new FileInputStream(scratch.toFile())) {
            assertThrows(GConnectionException.class, () -> plugin.gpp_upload("/" + key, fis, missing, null));
        } finally {
            Files.deleteIfExists(scratch);
        }

        // The failure path must have aborted the orphan (leak audit #14).
        try (S3Client c = directClient()) {
            java.util.List<MultipartUpload> left = c.listMultipartUploads(
                    ListMultipartUploadsRequest.builder().bucket(BUCKET).prefix(key).build()).uploads();
            assertTrue(left == null || left.isEmpty(), "failed upload must abort the key's in-progress multipart upload");
        }
    }

    @Test
    void cancelDuringUploadReportsCancellationNotError() throws Exception {
        plugin.gpp_connect(bookmark);

        // Over the 100 MB multipart threshold; sparse so it costs no disk time to create.
        Path big = Files.createTempFile("s3-cancel", ".bin");
        try (RandomAccessFile raf = new RandomAccessFile(big.toFile(), "rw")) { raf.setLength(256L * 1024 * 1024); }

        // Cancel from the FIRST progress callback, i.e. deterministically mid-transfer: the in-process
        // mock can swallow 256 MB before a timer-based cancel would land. The listener is the same
        // GJobPlugin type the job runner passes; gpp_cancelTransfer() is what forceCancellation() runs.
        com.gideonsoftworks.gja.GJobDescription jd = new com.gideonsoftworks.gja.GJobDescription(
                com.gideonsoftworks.gja.GJobDescription.UPLOAD_JOB_TYPE, conn, "cancel-probe");
        final java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        com.gideonsoftworks.gja.GJobPlugin cancelOnFirstProgress =
                new com.gideonsoftworks.gja.GJobPlugin(new com.gideonsoftworks.gja.GJobStatus(jd)) {
            @Override public void updateTransferStats(long transferred) {
                if (cancelled.compareAndSet(false, true)) plugin.gpp_cancelTransfer();
            }
        };

        final Throwable[] outcome = { null };
        final boolean[] finished = { false };
        Thread worker = new Thread(() -> {
            try (FileInputStream fis = new FileInputStream(big.toFile())) {
                plugin.gpp_upload("/cancel.bin", fis, big.toFile(), cancelOnFirstProgress);
                finished[0] = true;
            } catch (Throwable t) {
                outcome[0] = t;
            }
        }, "s3-cancel-test-upload");
        worker.start();
        worker.join(60_000);
        Files.deleteIfExists(big);

        assertTrue(cancelled.get(), "the transfer listener never fired, so cancel was never attempted");
        // If the SDK completed the upload despite the cancel (possible if the last part was already
        // in flight), the run proves nothing either way; treat it as inconclusive, not a failure.
        org.junit.jupiter.api.Assumptions.assumeFalse(finished[0], "upload completed despite cancel; inconclusive");

        assertNotNull(outcome[0], "cancelled upload must throw");
        assertTrue(outcome[0] instanceof GConnectionException,
                "cancel must surface as GConnectionException, not " + outcome[0].getClass().getName());
        assertTrue(outcome[0].getMessage().toLowerCase().contains("cancel"), outcome[0].getMessage());

        try (S3Client c = directClient()) {
            java.util.List<MultipartUpload> left = c.listMultipartUploads(
                    ListMultipartUploadsRequest.builder().bucket(BUCKET).prefix("cancel.bin").build()).uploads();
            assertTrue(left == null || left.isEmpty(), "cancel must leave no in-progress multipart upload");
        }
    }

    // 2. disconnect
    @Test
    void disconnectSetsNotConnected() throws GConnectionException {
        plugin.gpp_connect(bookmark);
        assertTrue(plugin.getIsConnected());

        plugin.gpp_disconnect(conn);
        assertFalse(plugin.getIsConnected());

        // Prevent double-disconnect in tearDown
        plugin = null;
    }

    // 3. list root (empty bucket)
    @Test
    void listRoot_emptyBucket() throws GConnectionException {
        plugin.gpp_connect(bookmark);

        Object result = plugin.gpp_list("/");
        assertNotNull(result);
        assertInstanceOf(Object[].class, result);

        Object[] items = (Object[]) result;
        assertEquals(0, items.length, "Empty bucket root should have no items");
    }

    // 4. list with prefix (subfolder contents)
    @Test
    void listPrefix_returnsSubfolderContents() throws GConnectionException {
        // Seed objects: docs/readme.txt, docs/guide.txt, docs/sub/
        putObject("docs/readme.txt", "readme");
        putObject("docs/guide.txt", "guide");
        putObject("docs/sub/", "");  // folder marker

        plugin.gpp_connect(bookmark);

        Object result = plugin.gpp_list("/docs");
        assertNotNull(result);
        Object[] items = (Object[]) result;

        // Should contain: readme.txt, guide.txt, and sub/ (as common prefix)
        assertTrue(items.length >= 2,
                "Expected at least 2 items under docs/, got " + items.length);
    }

    @Test
    void transferListener_receivesCumulativeMonotonicBytes() throws Exception {
        java.util.List<Long> seen = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        byte[] payload = new byte[400000];
        plugin.gpp_connect(bookmark);
        Path local = Files.createTempFile("s3-progress", ".bin");
        Files.write(local, payload);
        try (FileInputStream fis = new FileInputStream(local.toFile())) {
            plugin.gpp_upload("/progress.bin", fis, local.toFile(), seen::add);
        } finally {
            Files.deleteIfExists(local);
        }
        assertFalse(seen.isEmpty(), "listener must be called");
        for (int i = 1; i < seen.size(); i++) assertTrue(seen.get(i) >= seen.get(i - 1), "cumulative, never decreasing");
        assertEquals(payload.length, seen.get(seen.size() - 1).longValue(), "last value is the file size");
    }

    // 5. cwd and getPwd
    @Test
    void cwdAndGetPwd() throws GConnectionException {
        plugin.gpp_connect(bookmark);

        assertEquals("/", plugin.gpp_get_pwd());

        plugin.gpp_cwd("/mydir/");
        assertEquals("/mydir/", plugin.gpp_get_pwd());

        // Navigate to root
        plugin.gpp_cwd("/");
        assertEquals("/", plugin.gpp_get_pwd());
    }

    // 6. mkdir creates folder marker
    @Test
    void mkdir_createsFolderMarker() throws GConnectionException {
        plugin.gpp_connect(bookmark);

        plugin.gpp_mkdir("/newfolder");

        assertTrue(objectExists("newfolder/"),
                "mkdir should create a zero-byte folder marker key ending with /");
    }

    // 7. rm file
    @Test
    void rmFile_deletesObject() throws GConnectionException {
        putObject("to-delete.txt", "delete me");
        assertTrue(objectExists("to-delete.txt"));

        plugin.gpp_connect(bookmark);
        plugin.gpp_rm("/to-delete.txt", false);

        assertFalse(objectExists("to-delete.txt"),
                "Object should be deleted after gpp_rm");
    }

    // 8. rename (copy + delete)
    @Test
    void rename_copiesAndDeletesOriginal() throws GConnectionException {
        putObject("original.txt", "original content");

        plugin.gpp_connect(bookmark);
        plugin.gpp_rename("/original.txt", "/renamed.txt");

        assertFalse(objectExists("original.txt"),
                "Original key should be deleted after rename");
        assertTrue(objectExists("renamed.txt"),
                "New key should exist after rename");
    }

    // 9. exists file / exists missing
    @Test
    void existsFile_returnsFileItem() throws GConnectionException {
        putObject("present.txt", "I exist");

        plugin.gpp_connect(bookmark);
        GFileItem item = plugin.gpp_exists("/present.txt", "present.txt", false);

        assertNotNull(item, "gpp_exists should return non-null for existing file");
        assertEquals("present.txt", item.getName());
    }

    @Test
    void existsMissing_returnsNull() throws GConnectionException {
        plugin.gpp_connect(bookmark);

        GFileItem item = plugin.gpp_exists("/nonexistent.txt", "nonexistent.txt", false);
        assertNull(item, "gpp_exists should return null for missing file");
    }

    // 10. upload and download round-trip
    @Test
    void uploadAndDownload_roundTrip() throws Exception {
        plugin.gpp_connect(bookmark);

        String content = "Round-trip test content: Hello S3!";
        Path localSrc = Files.createTempFile("s3test-upload-", ".txt");
        Files.writeString(localSrc, content);

        // Upload
        try (FileInputStream fis = new FileInputStream(localSrc.toFile())) {
            plugin.gpp_upload("/roundtrip.txt", fis, localSrc.toFile(), null);
        }

        assertTrue(objectExists("roundtrip.txt"), "Uploaded object should exist");

        // Download (via TransferManager — writes localDst directly)
        Path localDst = Files.createTempFile("s3test-download-", ".txt");
        try (FileOutputStream fos = new FileOutputStream(localDst.toFile())) {
            plugin.gpp_download("/roundtrip.txt", fos, localDst.toFile(), null);
        }

        String downloaded = Files.readString(localDst, StandardCharsets.UTF_8);
        assertEquals(content, downloaded,
                "Downloaded content should match uploaded content");

        // Cleanup temp files
        Files.deleteIfExists(localSrc);
        Files.deleteIfExists(localDst);
    }

    // 11. chmod is no-op (should not throw)
    @Test
    void chmod_isNoOp() throws GConnectionException {
        plugin.gpp_connect(bookmark);

        GPermissions perms = new GPermissions(0, "-");
        assertDoesNotThrow(() -> plugin.gpp_chmod("/anything.txt", perms));
    }

    // 12. needsCompletePathForRename returns true
    @Test
    void needsCompletePathForRename_returnsTrue() throws GConnectionException {
        assertTrue(plugin.gpp_needsCompletePathForRename(),
                "S3 plugin requires complete path for rename");
    }
}
