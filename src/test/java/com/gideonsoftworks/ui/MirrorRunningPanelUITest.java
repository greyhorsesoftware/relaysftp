package com.gideonsoftworks.ui;

import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * The mirror Running card (docs/1.8.0/MIRROR_RUNNING_PANEL_PLAN.md §5.1): one row per action with a
 * live Status, the stats and footer labels, bounded parallel copies, cancel, and the Activity
 * popover's mirror row. Same harness as {@link MirrorSftpUITest}: the SFTP serverRoot is the
 * remote, localRoot the master; one login per class; every case awaits its job before cleanup.
 *
 * Cases that must observe rows in flight set {@code relaysftp.mirror.copyDelayMs}; the engine reads
 * it once per run, so it is set before Mirror is clicked and restored afterwards.
 */
@Tag("ui")
class MirrorRunningPanelUITest extends UITestBase {

    private static final String DELAY_PROP = "relaysftp.mirror.copyDelayMs";
    private String previousDelay;

    @BeforeAll
    void connect() throws Exception {
        loginToServer();
    }

    @AfterEach
    void cleanup() throws Exception {
        if (previousDelay == null) System.clearProperty(DELAY_PROP); else System.setProperty(DELAY_PROP, previousDelay);
        previousDelay = null;
        exitMirrorView();
        emptyDir(serverRoot);
        emptyDir(localRoot);
    }

    private void throttle(long ms) {
        previousDelay = System.getProperty(DELAY_PROP);
        System.setProperty(DELAY_PROP, Long.toString(ms));
    }

    /** Configure an upload mirror and land on Review. */
    private void reviewUpload() throws Exception {
        enterMirrorView();
        mirrorSetDirection(true);
        mirrorSelectDetector(DETECTOR_AUTOMATIC);
        mirrorPreflightAndWaitForReview();
    }

    private long countStatus(String prefix) {
        long n = 0;
        for (String[] r : mirrorRunRows()) if (r[3].startsWith(prefix)) n++;
        return n;
    }

    // 1
    @Test
    void allNew_everyRowEndsDone() throws Exception {
        createLocalFile("a.txt", "aaa");
        createLocalFile("b.txt", "bb");
        Files.createDirectories(localRoot.resolve("sub"));
        Files.writeString(localRoot.resolve("sub/c.txt"), "c");
        reviewUpload();

        String detail = mirrorExecuteAndWaitForCompletion();

        String[][] rows = mirrorRunRows();
        assertEquals(4, rows.length, "mkdir sub + 3 copies, no SKIP rows: " + java.util.Arrays.deepToString(rows));
        int subRow = -1, cRow = -1;
        for (int i = 0; i < rows.length; i++) {
            assertEquals("done", rows[i][3], rows[i][1]);
            if (rows[i][1].equals("sub")) subRow = i;
            if (rows[i][1].equals("sub/c.txt")) cRow = i;
        }
        assertTrue(subRow >= 0 && cRow > subRow, "mkdir before its child copy");
        assertTrue(mirrorRunStats().startsWith("4 of 4 items"), mirrorRunStats());
        assertTrue(mirrorRunFooter().startsWith("0 skipped so far"), mirrorRunFooter());
        assertTrue(detail.startsWith("3 copied · 0 deleted"), detail);
        assertEquals("aaa", Files.readString(serverRoot.resolve("a.txt")));
        assertEquals("c", Files.readString(serverRoot.resolve("sub/c.txt")));
    }

    // 2
    @Test
    void reviewDeselection_dropsRowFromRunTable() throws Exception {
        createLocalFile("one.txt", "1");
        createLocalFile("two.txt", "2");
        createLocalFile("three.txt", "3");
        reviewUpload();

        JTable review = findByName(JTable.class, "mirrorReviewTable");
        String dropped = (String) review.getValueAt(1, 2);
        SwingUtilities.invokeAndWait(() -> review.setValueAt(Boolean.FALSE, 1, 0));

        mirrorExecuteAndWaitForCompletion();

        String[][] rows = mirrorRunRows();
        assertEquals(2, rows.length, java.util.Arrays.deepToString(rows));
        assertNull(mirrorRunStatus(dropped), "deselected path is not in the run table");
        assertFalse(Files.exists(serverRoot.resolve(dropped)), "deselected file was not copied");
    }

    // 3
    @Test
    void permissionDenied_rowSkippedWithReason_runContinues() throws Exception {
        assumeFalse("root".equals(System.getProperty("user.name")), "root ignores mode bits");
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        createServerDir("locked");
        Files.createDirectories(localRoot.resolve("locked"));
        Files.writeString(localRoot.resolve("locked/x.txt"), "x");
        createLocalFile("ok.txt", "ok");
        Path locked = serverRoot.resolve("locked");
        Files.setPosixFilePermissions(locked, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
        try {
            reviewUpload();
            String detail = mirrorExecuteAndWaitForCompletion();

            assertTrue(mirrorRunStatus("locked/x.txt").startsWith("skipped · permission denied"), mirrorRunStatus("locked/x.txt"));
            assertEquals("done", mirrorRunStatus("ok.txt"));
            assertTrue(mirrorRunFooter().startsWith("1 skipped so far"), mirrorRunFooter());
            assertTrue(detail.contains("1 skipped"), detail);
            assertEquals("Mirror complete", mirrorRunTitle());
        } finally {
            Files.setPosixFilePermissions(locked, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        }
    }

    // 4
    @Test
    void inFlight_rowsShowProgressThenDone() throws Exception {
        throttle(400);
        for (int i = 0; i < 6; i++) createLocalFile("f" + i + ".txt", "file " + i);
        reviewUpload();

        JobWaiter waiter = mirrorExecuteWithoutWaiting();
        waitForRunRow(null, "running", 3_000);
        assertTrue(countStatus("queued") >= 1, "some rows still queued: " + java.util.Arrays.deepToString(mirrorRunRows()));
        assertTrue(Pattern.compile("^\\d+ of 6 items").matcher(mirrorRunStats()).find(), mirrorRunStats());

        waiter.await(30);
        robot.waitForIdle();
        assertEquals(6, countStatus("done"), java.util.Arrays.deepToString(mirrorRunRows()));
    }

    // 5
    @Test
    void parallel_upToThreeRowsRunningAtOnce() throws Exception {
        throttle(400);
        for (int i = 0; i < 9; i++) createLocalFile("p" + i + ".txt", "parallel " + i);
        reviewUpload();

        JobWaiter waiter = mirrorExecuteWithoutWaiting();
        int maxRunning = 0, maxSessions = 0;
        long deadline = System.currentTimeMillis() + 30_000;
        while (countStatus("done") < 9 && System.currentTimeMillis() < deadline) {
            maxRunning = Math.max(maxRunning, (int) countStatus("running"));
            maxSessions = Math.max(maxSessions, sshd.getActiveSessions().size());
            Thread.sleep(50);
        }
        waiter.await(30);
        robot.waitForIdle();

        assertTrue(maxRunning >= 2 && maxRunning <= 3, "max running at once: " + maxRunning);
        assertTrue(maxSessions >= 2, "clone sessions were used: " + maxSessions);
        assertEquals(1, sessionsSettled(1), "only the browser's session remains");
        assertEquals(9, countStatus("done"));
    }

    // 6
    @Test
    void cancel_midRun_marksRestCancelledAndRefreshesPanes() throws Exception {
        throttle(300);
        for (int i = 0; i < 10; i++) createLocalFile("c" + i + ".txt", "cancel " + i);
        reviewUpload();

        JobWaiter waiter = mirrorExecuteWithoutWaiting();
        waitForRunRow(null, "done", 5_000);
        mirrorCancelRunning();
        waiter.await(30);
        robot.waitForIdle();

        assertTrue(waiter.wasStopped(), "job ended by STOPPED");
        assertEquals("Mirror cancelled", mirrorRunTitle());
        assertTrue(countStatus("done") >= 1, java.util.Arrays.deepToString(mirrorRunRows()));
        assertTrue(countStatus("cancelled") >= 1, java.util.Arrays.deepToString(mirrorRunRows()));
        assertEquals(0, countStatus("running"));
        Thread.sleep(200);
        for (Thread t : Thread.getAllStackTraces().keySet())
            assertFalse(t.isAlive() && t.getName().startsWith("mirror-copy-"), "worker still alive: " + t.getName());
        assertEquals(1, sessionsSettled(1));

        // the panes were re-listed: a completed file shows in the remote pane
        String firstDone = null;
        for (String[] r : mirrorRunRows()) if (r[3].equals("done")) { firstDone = r[1]; break; }
        assertNotNull(firstDone);
        exitMirrorView();
        waitForRowPresent(new JTableFixture(robot, remoteBrowser().getFileTable()), firstDone, 10);
    }

    // 7
    @Test
    void deleteExtras_deleteRowsRenderAndEndDone() throws Exception {
        createServerFile("extra.txt", "old");
        createLocalFile("keep.txt", "k");
        enterMirrorView();
        mirrorSetDirection(true);
        mirrorSelectDetector(DETECTOR_AUTOMATIC);
        mirrorSetDeleteExtras(true);
        mirrorPreflightAndWaitForReview();

        mirrorExecuteAndWaitForCompletion();

        String[] deleteRow = null;
        for (String[] r : mirrorRunRows()) if (r[1].equals("extra.txt")) deleteRow = r;
        assertNotNull(deleteRow, java.util.Arrays.deepToString(mirrorRunRows()));
        assertEquals("✕ delete", deleteRow[0]);
        assertEquals("done", deleteRow[3]);
        assertFalse(Files.exists(serverRoot.resolve("extra.txt")));
        assertEquals("k", Files.readString(serverRoot.resolve("keep.txt")));
    }

    // 8
    @Test
    void backgrounded_activityPopoverShowsMirrorRow() throws Exception {
        throttle(300);
        for (int i = 0; i < 6; i++) createLocalFile("bg" + i + ".txt", "background " + i);
        reviewUpload();

        JobWaiter waiter = mirrorExecuteWithoutWaiting();
        waitForRunRow(null, "running", 3_000);
        exitMirrorView();
        openActivityPopover();

        JLabel status = findInPopover(JLabel.class, "activityStatusLabel");
        JLabel header = findInPopover(JLabel.class, "activityHeaderLabel");
        assertNotNull(status, "popover status label; owned windows: " + java.util.Arrays.toString(browserWindow.getOwnedWindows()));
        assertNotNull(header, "popover header label");
        long deadline = System.currentTimeMillis() + 3_000;
        Pattern p = Pattern.compile("^\\d+ of 6 items · .* of .* · ");
        while (!p.matcher(status.getText()).find() && System.currentTimeMillis() < deadline) Thread.sleep(50);
        assertTrue(p.matcher(status.getText()).find(), status.getText());
        assertTrue(header.getText().matches("bg\\d\\.txt"), header.getText());

        waiter.await(30);
        robot.waitForIdle();
        for (int i = 0; i < 6; i++)   // the mirror view is closed; the run itself completed in full
            assertEquals("background " + i, Files.readString(serverRoot.resolve("bg" + i + ".txt")));
    }

    // ---- helpers ----

    private int sessionsSettled(int expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (sshd.getActiveSessions().size() != expected && System.currentTimeMillis() < deadline) Thread.sleep(50);
        return sshd.getActiveSessions().size();
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
