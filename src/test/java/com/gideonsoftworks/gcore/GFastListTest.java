package com.gideonsoftworks.gcore;

import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.shell.ProcessShellCommandFactory;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests fast recursive listing (SSH exec) vs standard SFTP LIST,
 * and verifies fallback behavior. Outputs timing for comparison.
 */
@Tag("integration")
class GFastListTest {

    private static SshServer sshdWithExec;
    private static SshServer sshdSftpOnly;
    private static int portWithExec;
    private static int portSftpOnly;

    @TempDir
    static Path tempDir;
    private static Path workDir;

    // Number of directories and files to create for the test
    private static final int DIR_DEPTH = 4;
    private static final int DIRS_PER_LEVEL = 3;
    private static final int FILES_PER_DIR = 5;
    // 3^1 + 3^2 + 3^3 + 3^4 = 3 + 9 + 27 + 81 = 120 dirs
    // 120 dirs × 5 files = 600 files
    private static final int EXPECTED_DIRS = 120;
    private static final int EXPECTED_FILES = 600;

    @BeforeAll
    static void startServers() throws IOException {
        workDir = Files.createTempDirectory(tempDir, "fastlist-").toRealPath();
        createDeepStructure(workDir, DIR_DEPTH, DIRS_PER_LEVEL, FILES_PER_DIR);

        int[] counts = countStructure(workDir);
        System.out.println("=== Fast List Test Setup ===");
        System.out.println("Test directory: " + workDir);
        System.out.println("Directories: " + counts[0] + ", Files: " + counts[1]);
        System.out.println();

        // Server 1: SFTP + shell exec (supports fast list)
        sshdWithExec = SshServer.setUpDefaultServer();
        sshdWithExec.setPort(0);
        sshdWithExec.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(tempDir.resolve("hostkey1.ser")));
        sshdWithExec.setPasswordAuthenticator((u, p, s) -> "test".equals(u) && "test".equals(p));
        sshdWithExec.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
        sshdWithExec.setCommandFactory(ProcessShellCommandFactory.INSTANCE);
        sshdWithExec.start();
        portWithExec = sshdWithExec.getPort();

        // Server 2: SFTP only (no exec — fast list should fail and fall back)
        sshdSftpOnly = SshServer.setUpDefaultServer();
        sshdSftpOnly.setPort(0);
        sshdSftpOnly.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(tempDir.resolve("hostkey2.ser")));
        sshdSftpOnly.setPasswordAuthenticator((u, p, s) -> "test".equals(u) && "test".equals(p));
        sshdSftpOnly.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
        // No setCommandFactory — exec will fail
        sshdSftpOnly.start();
        portSftpOnly = sshdSftpOnly.getPort();
    }

    @AfterAll
    static void stopServers() throws IOException {
        if (sshdWithExec != null) sshdWithExec.stop(true);
        if (sshdSftpOnly != null) sshdSftpOnly.stop(true);
    }

    private GConnection connect(int port) throws GConnectionException {
        GBookmark bm = new GBookmark("test", "localhost",
            workDir.toAbsolutePath().toString(),
            "test", "test", port, GBookmark.G_SFTP_PROTOCOL);
        GConnection conn = new GConnection(bm);
        conn.login();
        return conn;
    }

    // --- Test: Fast list succeeds with exec support ---

    @Test
    void fastList_withExecSupport_populatesTree() throws Exception {
        GConnection conn = connect(portWithExec);
        try {
            GFileItem root = new GFileItem(workDir.getFileName().toString(),
                workDir.toAbsolutePath().toString(), conn);
            root.setFileType(GFileItem.G_FOLDER);

            long start = System.currentTimeMillis();
            boolean result = conn.fastListRecursive(root);
            long elapsed = System.currentTimeMillis() - start;

            System.out.println("=== Fast List (SSH exec) ===");
            System.out.println("Result: " + (result ? "SUCCESS" : "FAILED"));
            System.out.println("Time: " + elapsed + "ms");

            assertTrue(result, "Fast list should succeed with exec support");
            int[] counts = countTree(root);
            System.out.println("Directories found: " + counts[0]);
            System.out.println("Files found: " + counts[1]);
            assertEquals(EXPECTED_DIRS, counts[0], "Directory count");
            assertEquals(EXPECTED_FILES, counts[1], "File count");
            assertEquals(GFileItem.STATE_LISTED, root.getFileState());
            System.out.println();
        } finally {
            conn.logout();
        }
    }

    // --- Test: repeated WARP-LISTs close their exec channel (leak audit #10) ---

    @Test
    void repeatedWarpList_doesNotAccumulateServerChannels() throws Exception {
        GConnection conn = connect(portWithExec);
        try {
            // warm up once so the SFTP subsystem channel (which stays open by design) is counted
            GFileItem warm = new GFileItem(workDir.getFileName().toString(),
                workDir.toAbsolutePath().toString(), conn);
            warm.setFileType(GFileItem.G_FOLDER);
            assertTrue(conn.fastListRecursive(warm));
            int channelsBefore = openServerChannels(sshdWithExec);

            for (int i = 0; i < 25; i++) {
                GFileItem root = new GFileItem(workDir.getFileName().toString(),
                    workDir.toAbsolutePath().toString(), conn);
                root.setFileType(GFileItem.G_FOLDER);
                assertTrue(conn.fastListRecursive(root), "warp-list #" + i);
                int[] counts = countTree(root);
                assertEquals(EXPECTED_DIRS, counts[0]);
                assertEquals(EXPECTED_FILES, counts[1]);
            }

            // exec channels are closed per call; only the persistent SFTP channel(s) remain
            int channelsAfter = openServerChannels(sshdWithExec);
            assertTrue(channelsAfter <= channelsBefore,
                "server channels grew from " + channelsBefore + " to " + channelsAfter + " over 25 warp-lists");
        } finally {
            conn.logout();
        }
    }

    /** Sum of open channels over every server session (the SFTP subsystem channel is one of them). */
    private static int openServerChannels(SshServer server) throws Exception {
        // give the server a moment to process the client's channel-close messages
        for (int attempt = 0; attempt < 20; attempt++) {
            int total = 0;
            for (org.apache.sshd.common.session.helpers.AbstractSession s : server.getActiveSessions()) {
                org.apache.sshd.common.session.ConnectionService cs =
                    s.getService(org.apache.sshd.common.session.ConnectionService.class);
                if (cs instanceof org.apache.sshd.common.session.helpers.AbstractConnectionService acs)
                    total += acs.getChannels().size();
            }
            if (attempt > 0 && total <= 1) return total;
            Thread.sleep(50);
            if (attempt == 19) return total;
        }
        return -1;
    }

    // --- Test: Standard SFTP LIST for comparison ---

    @Test
    void standardList_timing() throws Exception {
        GConnection conn = connect(portWithExec);
        try {
            GFileItem root = new GFileItem(workDir.getFileName().toString(),
                workDir.toAbsolutePath().toString(), conn);
            root.setFileType(GFileItem.G_FOLDER);

            long start = System.currentTimeMillis();
            listRecursiveStandard(conn, root);
            long elapsed = System.currentTimeMillis() - start;

            int[] counts = countTree(root);
            System.out.println("=== Standard List (SFTP per-directory) ===");
            System.out.println("Time: " + elapsed + "ms");
            System.out.println("Directories found: " + counts[0]);
            System.out.println("Files found: " + counts[1]);
            System.out.println();

            assertEquals(EXPECTED_DIRS, counts[0], "Directory count");
            assertEquals(EXPECTED_FILES, counts[1], "File count");
        } finally {
            conn.logout();
        }
    }

    // --- Test: Fallback when exec is not available ---

    @Test
    void fastList_withoutExecSupport_fallsBack() throws Exception {
        GConnection conn = connect(portSftpOnly);
        try {
            GFileItem root = new GFileItem(workDir.getFileName().toString(),
                workDir.toAbsolutePath().toString(), conn);
            root.setFileType(GFileItem.G_FOLDER);

            long start = System.currentTimeMillis();
            boolean result = conn.fastListRecursive(root);
            long elapsed = System.currentTimeMillis() - start;

            System.out.println("=== Fast List Fallback (no exec) ===");
            System.out.println("Result: " + (result ? "SUCCESS (unexpected)" : "FAILED (expected — fallback)"));
            System.out.println("Time: " + elapsed + "ms");
            System.out.println();

            assertFalse(result, "Should fail on server without exec support");

            // Now do standard list to prove fallback works
            long start2 = System.currentTimeMillis();
            listRecursiveStandard(conn, root);
            long elapsed2 = System.currentTimeMillis() - start2;

            int[] counts = countTree(root);
            System.out.println("=== Fallback Standard List ===");
            System.out.println("Time: " + elapsed2 + "ms");
            System.out.println("Directories found: " + counts[0]);
            System.out.println("Files found: " + counts[1]);
            System.out.println();

            assertEquals(EXPECTED_DIRS, counts[0], "Fallback directory count");
            assertEquals(EXPECTED_FILES, counts[1], "Fallback file count");
        } finally {
            conn.logout();
        }
    }

    // --- Test: Both methods find the same files ---

    @Test
    void fastAndStandard_findSameFiles() throws Exception {
        // Fast list
        GConnection conn1 = connect(portWithExec);
        GFileItem root1 = new GFileItem(workDir.getFileName().toString(),
            workDir.toAbsolutePath().toString(), conn1);
        root1.setFileType(GFileItem.G_FOLDER);

        long t1 = System.currentTimeMillis();
        boolean fastResult = conn1.fastListRecursive(root1);
        long fastTime = System.currentTimeMillis() - t1;
        conn1.logout();

        // Standard list
        GConnection conn2 = connect(portWithExec);
        GFileItem root2 = new GFileItem(workDir.getFileName().toString(),
            workDir.toAbsolutePath().toString(), conn2);
        root2.setFileType(GFileItem.G_FOLDER);

        long t2 = System.currentTimeMillis();
        listRecursiveStandard(conn2, root2);
        long stdTime = System.currentTimeMillis() - t2;
        conn2.logout();

        int[] fastCounts = countTree(root1);
        int[] stdCounts = countTree(root2);

        System.out.println("=== Comparison ===");
        System.out.println("Fast list:     " + fastTime + "ms — " + fastCounts[0] + " dirs, " + fastCounts[1] + " files");
        System.out.println("Standard list: " + stdTime + "ms — " + stdCounts[0] + " dirs, " + stdCounts[1] + " files");
        if (stdTime > 0) {
            System.out.println("Speedup: " + String.format("%.1fx", (double) stdTime / Math.max(fastTime, 1)));
        }
        System.out.println();

        assertTrue(fastResult, "Fast list should succeed");
        assertEquals(EXPECTED_DIRS, fastCounts[0], "Fast list directory count");
        assertEquals(EXPECTED_FILES, fastCounts[1], "Fast list file count");
        assertEquals(EXPECTED_DIRS, stdCounts[0], "Standard list directory count");
        assertEquals(EXPECTED_FILES, stdCounts[1], "Standard list file count");
        assertEquals(stdCounts[0], fastCounts[0], "Directory counts should match");
        assertEquals(stdCounts[1], fastCounts[1], "File counts should match");
    }

    // --- Helpers ---

    /** Standard per-directory SFTP listing (what the app normally does) */
    private void listRecursiveStandard(GConnection conn, GFileItem folder) throws GConnectionException {
        conn.list(folder);
        for (int i = 0; i < folder.getChildCount(); i++) {
            GFileItem child = folder.getChild(i);
            if (child.getIsDir() && !child.getName().equals(".") && !child.getName().equals("..")) {
                listRecursiveStandard(conn, child);
            }
        }
    }

    /** Count dirs and files in a GFileItem tree */
    private static int[] countTree(GFileItem item) {
        int dirs = 0, files = 0;
        for (int i = 0; i < item.getChildCount(); i++) {
            GFileItem child = item.getChild(i);
            if (child.getName().equals(".") || child.getName().equals("..")) continue;
            if (child.getIsDir()) {
                dirs++;
                int[] sub = countTree(child);
                dirs += sub[0];
                files += sub[1];
            } else {
                files++;
            }
        }
        return new int[]{ dirs, files };
    }

    /** Count dirs and files on the actual filesystem */
    private static int[] countStructure(Path root) throws IOException {
        int[] counts = { 0, 0 };
        Files.walk(root).forEach(p -> {
            if (p.equals(root)) return;
            if (Files.isDirectory(p)) counts[0]++;
            else counts[1]++;
        });
        return counts;
    }

    /** Create a nested directory structure for testing */
    private static void createDeepStructure(Path root, int depth, int dirsPerLevel, int filesPerDir) throws IOException {
        if (depth <= 0) return;
        for (int d = 0; d < dirsPerLevel; d++) {
            Path dir = root.resolve("dir_" + depth + "_" + d);
            Files.createDirectories(dir);
            for (int f = 0; f < filesPerDir; f++) {
                Files.writeString(dir.resolve("file_" + f + ".txt"), "content " + f);
            }
            createDeepStructure(dir, depth - 1, dirsPerLevel, filesPerDir);
        }
    }
}
