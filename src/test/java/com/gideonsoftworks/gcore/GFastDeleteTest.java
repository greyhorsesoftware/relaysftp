package com.gideonsoftworks.gcore;

import com.gideonsoftworks.gideon.preferences.GSFTPPreferences;
import com.gideonsoftworks.gja.GDeleteJobPlugin;
import com.gideonsoftworks.gja.GJobDescription;
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
 * Tests fast delete (rm -rf via SSH exec) vs standard SFTP per-file delete,
 * including preference gating via GSFTPPreferences.useExec().
 */
@Tag("integration")
class GFastDeleteTest {

    private static SshServer sshdWithExec;
    private static SshServer sshdSftpOnly;
    private static int portWithExec;
    private static int portSftpOnly;

    @TempDir
    static Path tempDir;

    private static final int DIR_DEPTH = 3;
    private static final int DIRS_PER_LEVEL = 3;
    private static final int FILES_PER_DIR = 5;
    // 3 + 9 + 27 = 39 dirs, 39 * 5 = 195 files
    private static final int EXPECTED_DIRS = 39;
    private static final int EXPECTED_FILES = 195;

    @BeforeAll
    static void startServers() throws IOException {
        sshdWithExec = SshServer.setUpDefaultServer();
        sshdWithExec.setPort(0);
        sshdWithExec.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(tempDir.resolve("hostkey1.ser")));
        sshdWithExec.setPasswordAuthenticator((u, p, s) -> "test".equals(u) && "test".equals(p));
        sshdWithExec.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
        sshdWithExec.setCommandFactory(ProcessShellCommandFactory.INSTANCE);
        sshdWithExec.start();
        portWithExec = sshdWithExec.getPort();

        sshdSftpOnly = SshServer.setUpDefaultServer();
        sshdSftpOnly.setPort(0);
        sshdSftpOnly.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(tempDir.resolve("hostkey2.ser")));
        sshdSftpOnly.setPasswordAuthenticator((u, p, s) -> "test".equals(u) && "test".equals(p));
        sshdSftpOnly.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
        sshdSftpOnly.start();
        portSftpOnly = sshdSftpOnly.getPort();
    }

    @AfterAll
    static void stopServers() throws IOException {
        if (sshdWithExec != null) sshdWithExec.stop(true);
        if (sshdSftpOnly != null) sshdSftpOnly.stop(true);
    }

    @BeforeEach
    void enableExec() {
        // Default: exec enabled for most tests
        GSFTPPreferences.setUseExec(true);
    }

    @AfterEach
    void restoreExec() {
        GSFTPPreferences.setUseExec(true);
    }

    private GConnection connect(int port) throws GConnectionException {
        GBookmark bm = new GBookmark("test", "localhost",
            "/", "test", "test", port, GBookmark.G_SFTP_PROTOCOL);
        GConnection conn = new GConnection(bm);
        conn.login();
        return conn;
    }

    private Path createTestStructure(String name) throws IOException {
        Path root = Files.createDirectory(tempDir.resolve(name)).toRealPath();
        createDeepStructure(root, DIR_DEPTH, DIRS_PER_LEVEL, FILES_PER_DIR);
        return root;
    }

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

    private static int[] countStructure(Path root) throws IOException {
        int[] counts = { 0, 0 };
        if (!Files.exists(root)) return counts;
        Files.walk(root).forEach(p -> {
            if (p.equals(root)) return;
            if (Files.isDirectory(p)) counts[0]++;
            else counts[1]++;
        });
        return counts;
    }

    // --- Test: Fast delete removes folder ---

    @Test
    void fastDelete_removesEntireFolder() throws Exception {
        Path testDir = createTestStructure("fast_delete_test");

        // Verify structure exists
        int[] before = countStructure(testDir);
        assertEquals(EXPECTED_DIRS, before[0], "Dirs before delete");
        assertEquals(EXPECTED_FILES, before[1], "Files before delete");

        GConnection conn = connect(portWithExec);
        try {
            long start = System.currentTimeMillis();
            boolean result = conn.fastDelete(testDir.toAbsolutePath().toString());
            long elapsed = System.currentTimeMillis() - start;

            System.out.println("=== Fast Delete (SSH exec rm -rf) ===");
            System.out.println("Result: " + (result ? "SUCCESS" : "FAILED"));
            System.out.println("Time: " + elapsed + "ms");
            System.out.println("Dirs: " + EXPECTED_DIRS + ", Files: " + EXPECTED_FILES);

            assertTrue(result, "Fast delete should succeed");
            assertFalse(Files.exists(testDir), "Folder should be gone");
        } finally {
            conn.logout();
        }
    }

    // --- Test: Standard SFTP delete via framework GDeleteJobPlugin ---

    @Test
    void standardDelete_removesEntireFolder() throws Exception {
        Path testDir = createTestStructure("std_delete_test");

        GConnection conn = connect(portWithExec);
        try {
            GFileItem target = new GFileItem(testDir.getFileName().toString(),
                testDir.toAbsolutePath().toString(), conn);
            target.setFileType(GFileItem.G_FOLDER);

            GJobDescription jd = new GJobDescription(
                GJobDescription.DELETE_JOB_TYPE, conn, "std delete test");
            jd.setTargetFile(target);

            GDeleteJobPlugin plugin = new GDeleteJobPlugin(jd.getJobStatus());

            // Preprocessing: lists tree and builds prerequisites (deepest-first)
            plugin.preProcessJobDescription(jd, conn);

            long start = System.currentTimeMillis();

            // Process prerequisites in reverse order (deepest items first)
            int pCount = jd.getPrerequisiteCount();
            for (int i = pCount - 1; i >= 0; i--) {
                plugin.processJobDescription(jd.getPrerequisite(i), conn);
            }

            long elapsed = System.currentTimeMillis() - start;

            System.out.println("=== Standard Delete (GDeleteJobPlugin) ===");
            System.out.println("Time: " + elapsed + "ms");
            System.out.println("Dirs: " + EXPECTED_DIRS + ", Files: " + EXPECTED_FILES);
            System.out.println("Prerequisites: " + pCount);

            assertFalse(Files.exists(testDir), "Folder should be gone");
        } finally {
            conn.logout();
        }
    }

    // --- Test: Fast delete not available falls back ---

    @Test
    void fastDelete_withoutExec_returnsFalse() throws Exception {
        Path testDir = createTestStructure("no_exec_delete_test");

        GConnection conn = connect(portSftpOnly);
        try {
            boolean result = conn.fastDelete(testDir.toAbsolutePath().toString());

            System.out.println("=== Fast Delete (no exec) ===");
            System.out.println("Result: " + (result ? "SUCCESS (unexpected)" : "FAILED (expected)"));

            assertFalse(result, "Should fail without exec support");
            assertTrue(Files.exists(testDir), "Folder should still exist");
        } finally {
            conn.logout();
            deleteLocalDir(testDir);
        }
    }

    // --- Test: Preference disables fast methods on exec-capable server ---

    @Test
    void prefDisabled_disablesFastDelete() throws Exception {
        Path testDir = createTestStructure("pref_off_delete_test");

        // Connect with exec enabled (capabilities probed)
        GConnection conn = connect(portWithExec);
        try {
            // Verify capabilities were probed
            assertTrue(conn.hasCapability(GConnectionStats.CAP_WARP_LIST),
                "Should have warp-list when exec enabled");

            // Now disable the preference
            GSFTPPreferences.setUseExec(false);

            // hasCapability should now return false
            assertFalse(conn.hasCapability(GConnectionStats.CAP_WARP_LIST),
                "Should not report warp-list when preference disabled");
            assertFalse(conn.supportsFastList(),
                "Should not support fast list when preference disabled");

            // GDeleteJobPlugin should require preprocessing (standard path)
            GFileItem target = new GFileItem(testDir.getFileName().toString(),
                testDir.toAbsolutePath().toString(), conn);
            target.setFileType(GFileItem.G_FOLDER);

            GJobDescription jd = new GJobDescription(
                GJobDescription.DELETE_JOB_TYPE, conn, "pref off delete");
            jd.setTargetFile(target);

            GDeleteJobPlugin plugin = new GDeleteJobPlugin(jd.getJobStatus());
            assertTrue(plugin.requiresPreprocessing(jd),
                "Should require preprocessing when exec preference disabled");

            System.out.println("=== Preference Disabled ===");
            System.out.println("hasCapability(WARP_LIST): " + conn.hasCapability(GConnectionStats.CAP_WARP_LIST));
            System.out.println("supportsFastList(): " + conn.supportsFastList());
            System.out.println("requiresPreprocessing(): " + plugin.requiresPreprocessing(jd));
        } finally {
            conn.logout();
            deleteLocalDir(testDir);
        }
    }

    // --- Test: Preference enabled allows fast methods ---

    @Test
    void prefEnabled_allowsFastDelete() throws Exception {
        Path testDir = createTestStructure("pref_on_delete_test");

        GConnection conn = connect(portWithExec);
        try {
            assertTrue(conn.hasCapability(GConnectionStats.CAP_WARP_LIST),
                "Should have warp-list when exec enabled");

            GFileItem target = new GFileItem(testDir.getFileName().toString(),
                testDir.toAbsolutePath().toString(), conn);
            target.setFileType(GFileItem.G_FOLDER);

            GJobDescription jd = new GJobDescription(
                GJobDescription.DELETE_JOB_TYPE, conn, "pref on delete");
            jd.setTargetFile(target);

            GDeleteJobPlugin plugin = new GDeleteJobPlugin(jd.getJobStatus());
            assertFalse(plugin.requiresPreprocessing(jd),
                "Should skip preprocessing when exec available");

            // Fast delete should work
            boolean result = conn.fastDelete(testDir.toAbsolutePath().toString());

            System.out.println("=== Preference Enabled ===");
            System.out.println("hasCapability(WARP_LIST): true");
            System.out.println("requiresPreprocessing(): false");
            System.out.println("fastDelete: " + (result ? "SUCCESS" : "FAILED"));

            assertTrue(result, "Fast delete should succeed with preference enabled");
            assertFalse(Files.exists(testDir), "Folder should be gone");
        } finally {
            conn.logout();
        }
    }

    // --- Test: Comparison timing ---

    @Test
    void fastVsStandard_timing() throws Exception {
        Path fastDir = createTestStructure("timing_fast");
        Path stdDir = createTestStructure("timing_std");

        // Fast delete
        GConnection conn1 = connect(portWithExec);
        long t1 = System.currentTimeMillis();
        boolean fastResult = conn1.fastDelete(fastDir.toAbsolutePath().toString());
        long fastTime = System.currentTimeMillis() - t1;
        conn1.logout();

        // Standard delete via framework
        GConnection conn2 = connect(portWithExec);
        GFileItem target = new GFileItem(stdDir.getFileName().toString(),
            stdDir.toAbsolutePath().toString(), conn2);
        target.setFileType(GFileItem.G_FOLDER);

        GJobDescription jd = new GJobDescription(
            GJobDescription.DELETE_JOB_TYPE, conn2, "timing std delete");
        jd.setTargetFile(target);

        GDeleteJobPlugin plugin = new GDeleteJobPlugin(jd.getJobStatus());
        plugin.preProcessJobDescription(jd, conn2);

        long t2 = System.currentTimeMillis();
        int pCount = jd.getPrerequisiteCount();
        for (int i = pCount - 1; i >= 0; i--) {
            plugin.processJobDescription(jd.getPrerequisite(i), conn2);
        }
        long stdTime = System.currentTimeMillis() - t2;
        conn2.logout();

        System.out.println("=== Delete Comparison ===");
        System.out.println("Fast delete:     " + fastTime + "ms");
        System.out.println("Standard delete: " + stdTime + "ms");
        System.out.println("Structure: " + EXPECTED_DIRS + " dirs, " + EXPECTED_FILES + " files");
        if (stdTime > 0) {
            System.out.println("Speedup: " + String.format("%.1fx", (double) stdTime / Math.max(fastTime, 1)));
        }

        assertTrue(fastResult, "Fast delete should succeed");
        assertFalse(Files.exists(fastDir), "Fast dir should be gone");
        assertFalse(Files.exists(stdDir), "Standard dir should be gone");
    }

    // --- Helpers ---

    private static void deleteLocalDir(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
        }
    }
}
