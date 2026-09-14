package com.gideonsoftworks.ui;

import org.junit.jupiter.api.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI tests driving the real {@link com.gideonsoftworks.gideon.mirroring.GMirrorPanel} against an
 * S3 backend (Adobe S3Mock, via {@link S3UITestBase}). Covers both directions and the S3-ETag
 * comparator, which is content-based (md5) and so deterministic — no mtime sensitivity.
 *
 * "remote" is the mock bucket (seed/inspect via the SDK helpers in the base); "local" is localRoot.
 */
@org.junit.jupiter.api.Tag("ui")
class MirrorS3UITest extends S3UITestBase {

    @BeforeAll
    void connect() throws Exception {
        loginToServer();
    }

    @AfterEach
    void cleanup() throws Exception {
        exitMirrorView();
        emptyBucket();
        emptyDir(localRoot);
    }

    // ---- Upload (local -> bucket) ----

    @Test
    void upload_allNew_copiesEveryFile() throws Exception {
        createLocalFile("a.txt", "aaa");
        createLocalFile("b.txt", "bbbb");

        enterMirrorView();
        mirrorSetDirection(true);
        mirrorSelectDetector(DETECTOR_AUTOMATIC);   // S3 -> ETag
        mirrorPreflightAndWaitForReview();

        assertEquals(2, mirrorCountOp("copy (new)"));

        String msg = mirrorExecuteAndWaitForCompletion();
        assertTrue(msg.contains("2 copied"), msg);
        assertTrue(objectExists("a.txt"));
        assertTrue(objectExists("b.txt"));
    }

    @Test
    void upload_deleteExtras_removesBucketOnlyObjects() throws Exception {
        createLocalFile("keep.txt", "k");
        seedObject("keep.txt", "k");        // identical content -> ETag unchanged
        seedObject("extra.txt", "gone");    // bucket-only

        enterMirrorView();
        mirrorSetDirection(true);
        mirrorSelectDetector(DETECTOR_AUTOMATIC);
        mirrorSetDeleteExtras(true);
        mirrorPreflightAndWaitForReview();

        assertEquals(1, mirrorCountOp("delete"));
        assertEquals(1, mirrorCountOp("unchanged"));

        mirrorExecuteAndWaitForCompletion();
        assertFalse(objectExists("extra.txt"));
        assertTrue(objectExists("keep.txt"));
    }

    // ---- Download (bucket -> local) ----

    @Test
    void download_allNew_copiesEveryObject() throws Exception {
        seedObject("d1.txt", "server-one");
        seedObject("d2.txt", "server-two");

        enterMirrorView();
        mirrorSetDirection(false);
        mirrorSelectDetector(DETECTOR_AUTOMATIC);   // S3 master -> ETag
        mirrorPreflightAndWaitForReview();

        assertEquals(2, mirrorCountOp("copy (new)"));

        mirrorExecuteAndWaitForCompletion();
        assertEquals("server-one", Files.readString(localRoot.resolve("d1.txt")));
        assertEquals("server-two", Files.readString(localRoot.resolve("d2.txt")));
    }

    // ---- Comparator: S3 ETag is content-based ----
    //
    // Identical content, different mtimes: ETag (md5) says unchanged; size+mtime says changed.
    // This both verifies the ETag comparator and proves Automatic resolves to it for S3.
    @Test
    void comparators_etagIgnoresMtime_sizeMtimeDoesNot() throws Exception {
        createLocalFile("c.txt", "identical");
        seedObject("c.txt", "identical");   // same content -> same md5
        Files.setLastModifiedTime(localRoot.resolve("c.txt"), FileTime.fromMillis(1_000_000_000L));

        enterMirrorView();
        mirrorSetDirection(true);

        // S3 ETag: md5 matches -> unchanged regardless of mtime.
        mirrorSelectDetector(DETECTOR_S3_ETAG);
        mirrorPreflightAndWaitForReview();
        assertEquals(0, mirrorCountOp("copy (changed)"), "ETag must ignore mtime when content matches");
        assertEquals(1, mirrorCountOp("unchanged"));

        // Automatic must resolve to ETag for S3 -> same verdict.
        mirrorBackToConfigure();
        mirrorSelectDetector(DETECTOR_AUTOMATIC);
        mirrorPreflightAndWaitForReview();
        assertEquals(0, mirrorCountOp("copy (changed)"), "Automatic must pick ETag for S3");
        assertEquals(1, mirrorCountOp("unchanged"));

        // Size + modification date: mtimes differ -> changed (the comparator that ETag overrides).
        mirrorBackToConfigure();
        mirrorSelectDetector(DETECTOR_SIZE_MTIME);
        mirrorPreflightAndWaitForReview();
        assertEquals(1, mirrorCountOp("copy (changed)"), "size+mtime must flag the differing mtime");
    }

    @Test
    void comparators_etagDetectsSameSizeEdit_sizeOnlyMisses() throws Exception {
        createLocalFile("e.txt", "12345");   // 5 bytes
        seedObject("e.txt", "ABCDE");          // 5 bytes, different content -> different md5

        enterMirrorView();
        mirrorSetDirection(true);

        // Size only: equal size -> unchanged (misses the edit).
        mirrorSelectDetector(DETECTOR_SIZE_ONLY);
        mirrorPreflightAndWaitForReview();
        assertEquals(0, mirrorCountOp("copy (changed)"));
        assertEquals(1, mirrorCountOp("unchanged"));

        // S3 ETag: md5 differs -> changed.
        mirrorBackToConfigure();
        mirrorSelectDetector(DETECTOR_S3_ETAG);
        mirrorPreflightAndWaitForReview();
        assertEquals(1, mirrorCountOp("copy (changed)"), "ETag must catch a same-size content edit");
    }

    // ---- helpers ----

    private void emptyBucket() {
        try (S3Client c = directClient()) {
            ListObjectsV2Response list = c.listObjectsV2(
                    ListObjectsV2Request.builder().bucket(BUCKET).build());
            for (S3Object o : list.contents()) {
                c.deleteObject(DeleteObjectRequest.builder().bucket(BUCKET).key(o.key()).build());
            }
        }
    }

    private static void emptyDir(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                  .filter(p -> !p.equals(dir))
                  .forEach(p -> { try { Files.delete(p); } catch (IOException ignore) {} });
        }
    }
}
