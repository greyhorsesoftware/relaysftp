package com.gideonsoftworks.ui;

import org.junit.jupiter.api.*;

import javax.swing.*;
import java.awt.Window;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UI tests driving the real {@link com.gideonsoftworks.gideon.mirroring.GMirrorPanel} against an
 * SFTP backend (the {@link UITestBase} MINA SSHD server). Covers both directions and the
 * size-based comparators, asserting both the Review plan and the post-execute destination state.
 *
 * The "remote" is the SFTP serverRoot; the "local" master/replica is localRoot. One login for the
 * class (PER_CLASS); each test seeds its own files and cleans up after.
 */
@Tag("ui")
class MirrorSftpUITest extends UITestBase {

    @BeforeAll
    void connect() throws Exception {
        loginToServer();
    }

    @AfterEach
    void cleanup() throws Exception {
        exitMirrorView();
        emptyDir(serverRoot);
        emptyDir(localRoot);
    }

    // ---- Upload (local -> remote) ----

    @Test
    void upload_allNew_copiesEveryFile() throws Exception {
        createLocalFile("a.txt", "aaa");
        createLocalFile("b.txt", "bbbb");

        enterMirrorView();
        mirrorSetDirection(true);
        mirrorSelectDetector(DETECTOR_AUTOMATIC);   // SFTP -> size+mtime
        mirrorPreflightAndWaitForReview();

        assertEquals(2, mirrorCountOp("copy (new)"));
        assertEquals(0, mirrorCountOp("delete"));
        assertTrue(mirrorSummary().startsWith("2 copy · 0 delete"), mirrorSummary());

        String msg = mirrorExecuteAndWaitForCompletion();
        assertTrue(msg.contains("2 copied"), msg);
        assertEquals("aaa", Files.readString(serverRoot.resolve("a.txt")));
        assertEquals("bbbb", Files.readString(serverRoot.resolve("b.txt")));
    }

    @Test
    void upload_changedAndUnchanged_copiesOnlyChanged() throws Exception {
        // identical on both sides (same size + same mtime -> unchanged)
        createLocalFile("same.txt", "X");
        createServerFile("same.txt", "X");
        setEqualMtime("same.txt");
        // different size -> changed
        createLocalFile("diff.txt", "new-content");
        createServerFile("diff.txt", "old");

        enterMirrorView();
        mirrorSetDirection(true);
        mirrorSelectDetector(DETECTOR_AUTOMATIC);
        mirrorPreflightAndWaitForReview();

        assertEquals(1, mirrorCountOp("copy (changed)"));
        assertEquals(1, mirrorCountOp("unchanged"));
        assertEquals(0, mirrorCountOp("copy (new)"));

        mirrorExecuteAndWaitForCompletion();
        assertEquals("new-content", Files.readString(serverRoot.resolve("diff.txt")));
        assertEquals("X", Files.readString(serverRoot.resolve("same.txt")));
    }

    @Test
    void upload_deleteExtras_removesReplicaOnlyFiles() throws Exception {
        createLocalFile("keep1.txt", "k");
        createLocalFile("keep2.txt", "k");
        createServerFile("keep1.txt", "k");
        createServerFile("keep2.txt", "k");
        setEqualMtime("keep1.txt");
        setEqualMtime("keep2.txt");
        createServerFile("extra.txt", "gone");   // only on the replica

        enterMirrorView();
        mirrorSetDirection(true);
        mirrorSelectDetector(DETECTOR_AUTOMATIC);
        mirrorSetDeleteExtras(true);
        mirrorPreflightAndWaitForReview();

        assertEquals(1, mirrorCountOp("delete"));
        assertTrue(mirrorSummary().contains("1 delete"), mirrorSummary());

        mirrorExecuteAndWaitForCompletion();
        assertFalse(Files.exists(serverRoot.resolve("extra.txt")));
        assertTrue(Files.exists(serverRoot.resolve("keep1.txt")));
    }

    @Test
    void upload_deleteExtrasOff_leavesReplicaOnlyFiles() throws Exception {
        createLocalFile("new.txt", "fresh");
        createServerFile("extra.txt", "stays");   // replica-only, must survive

        enterMirrorView();
        mirrorSetDirection(true);
        mirrorSelectDetector(DETECTOR_AUTOMATIC);
        mirrorSetDeleteExtras(false);
        mirrorPreflightAndWaitForReview();

        assertEquals(1, mirrorCountOp("copy (new)"));
        assertEquals(0, mirrorCountOp("delete"));

        mirrorExecuteAndWaitForCompletion();
        assertEquals("fresh", Files.readString(serverRoot.resolve("new.txt")));
        assertTrue(Files.exists(serverRoot.resolve("extra.txt")), "extra must survive with delete off");
    }

    // ---- Download (remote -> local) ----

    @Test
    void download_allNew_copiesEveryFile() throws Exception {
        createServerFile("d1.txt", "server-one");
        createServerFile("d2.txt", "server-two");

        enterMirrorView();
        mirrorSetDirection(false);
        mirrorSelectDetector(DETECTOR_AUTOMATIC);   // SFTP download -> size+mtime
        mirrorPreflightAndWaitForReview();

        assertEquals(2, mirrorCountOp("copy (new)"));

        mirrorExecuteAndWaitForCompletion();
        assertEquals("server-one", Files.readString(localRoot.resolve("d1.txt")));
        assertEquals("server-two", Files.readString(localRoot.resolve("d2.txt")));
    }

    // ---- Comparator divergence: size-only vs size+mtime ----
    //
    // Same size, different content, different mtime: size-only sees no change; size+mtime does.
    @Test
    void comparators_sizeOnlyVsSizeMtime_disagreeOnSameSizeEdit() throws Exception {
        createLocalFile("c.txt", "12345");          // 5 bytes
        createServerFile("c.txt", "ABCDE");          // 5 bytes, different content
        // make the mtimes differ well beyond the 2s tolerance
        Files.setLastModifiedTime(localRoot.resolve("c.txt"), FileTime.fromMillis(2_000_000_000L));
        Files.setLastModifiedTime(serverRoot.resolve("c.txt"), FileTime.fromMillis(1_000_000_000L));

        enterMirrorView();
        mirrorSetDirection(true);

        // Size only: equal size -> unchanged, nothing to copy.
        mirrorSelectDetector(DETECTOR_SIZE_ONLY);
        mirrorPreflightAndWaitForReview();
        assertEquals(0, mirrorCountOp("copy (changed)"), "size-only must treat a same-size edit as unchanged");
        assertEquals(1, mirrorCountOp("unchanged"));

        // Size + modification date: mtimes differ -> changed.
        mirrorBackToConfigure();
        mirrorSelectDetector(DETECTOR_SIZE_MTIME);
        mirrorPreflightAndWaitForReview();
        assertEquals(1, mirrorCountOp("copy (changed)"), "size+mtime must flag the differing mtime");
        assertEquals(0, mirrorCountOp("unchanged"));
    }

    // ---- Feature: "only mirror files modified in the last N" ----

    @Test
    void modifiedWithin_skipsOldFiles() throws Exception {
        createLocalFile("recent.txt", "fresh");                      // mtime = now
        createLocalFile("old.txt", "stale");
        Files.setLastModifiedTime(localRoot.resolve("old.txt"),
            FileTime.fromMillis(1_000_000_000_000L));                // year 2001 — well outside any window

        enterMirrorView();
        mirrorSetDirection(true);
        mirrorSelectDetector(DETECTOR_AUTOMATIC);
        mirrorSetOnlyRecent(1, "Days");                              // only files modified in the last day
        mirrorPreflightAndWaitForReview();

        // empty replica → both would be "new", but the window drops the old one (not copied, not deleted)
        assertEquals(1, mirrorCountOp("copy (new)"), "only the recent file is copied");
        assertEquals(1, mirrorCountOp("unchanged"), "the old file is skipped, not copied");

        mirrorExecuteAndWaitForCompletion();
        assertTrue(Files.exists(serverRoot.resolve("recent.txt")));
        assertFalse(Files.exists(serverRoot.resolve("old.txt")), "old file must not have been mirrored");
    }

    // ---- Feature: auto clock-offset (timezone skew correction) ----

    @Test
    void autoClockOffset_absorbsConsistentTimezoneSkew() throws Exception {
        long serverMtime = 1_500_000_000_000L;                       // a round-second epoch
        long localMtime  = serverMtime + 3 * 3_600_000L;             // local clock 3h ahead of the server

        // 3 identical files (same size) → enough samples for the auto-offset to engage
        for (String n : new String[]{ "a.txt", "b.txt", "c.txt" }) {
            createLocalFile(n, "same-content");
            createServerFile(n, "same-content");
            Files.setLastModifiedTime(localRoot.resolve(n),  FileTime.fromMillis(localMtime));
            Files.setLastModifiedTime(serverRoot.resolve(n), FileTime.fromMillis(serverMtime));
        }

        enterMirrorView();
        mirrorSetDirection(true);
        mirrorSelectDetector(DETECTOR_SIZE_MTIME);                   // mtime-sensitive → offset matters here
        mirrorPreflightAndWaitForReview();

        // scan auto-detects the systematic +3h skew across the 3 pairs → none flagged as changed
        assertEquals(0, mirrorCountOp("copy (changed)"), "auto clock-offset should absorb the 3h skew");
        assertEquals(3, mirrorCountOp("unchanged"));
    }

    // ---- Feature: detector info popup ----

    @Test
    void detectorInfo_buttonOpensBreakdownPopup() throws Exception {
        enterMirrorView();
        JButton info = findByName(JButton.class, "mirrorDetectorInfo");
        assertNotNull(info, "the ⓘ button next to the detector dropdown should exist");

        SwingUtilities.invokeAndWait(info::doClick);
        robot.waitForIdle();

        JComponent pop = findByName(JComponent.class, "mirrorDetectorInfoPopup");
        assertNotNull(pop, "clicking ⓘ should open the breakdown popover");

        Window w = SwingUtilities.getWindowAncestor(pop);
        assertNotNull(w, "popover should live in its own window");
        SwingUtilities.invokeAndWait(w::dispose);   // don't bleed into the next test
        robot.waitForIdle();
    }

    // ---- helpers ----

    /** Pin a file present on both sides to the same mtime so size+mtime reads it as unchanged. */
    private void setEqualMtime(String name) throws IOException {
        FileTime t = FileTime.fromMillis(1_500_000_000_000L);
        Files.setLastModifiedTime(localRoot.resolve(name), t);
        Files.setLastModifiedTime(serverRoot.resolve(name), t);
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
