package com.gideonsoftworks.gcore;

import net.schmizz.sshj.sftp.RemoteResourceInfo;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class GProtocolPluginSFTPIntegrationTest {

    private static SshServer sshd;
    private static int port;

    @TempDir
    static Path tempDir;

    private GProtocolPluginSFTP plugin;
    private GConnection conn;
    private GBookmark bookmark;

    @BeforeAll
    static void startServer() throws IOException {
        sshd = SshServer.setUpDefaultServer();
        sshd.setPort(0);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(tempDir.resolve("hostkey.ser")));
        sshd.setPasswordAuthenticator((username, password, session) ->
                "testuser".equals(username) && "testpass".equals(password));
        sshd.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
        sshd.start();
        port = sshd.getPort();
    }

    @AfterAll
    static void stopServer() throws IOException {
        if (sshd != null) {
            sshd.stop(true);
        }
    }

    private Path workDir;

    @BeforeEach
    void setUp() throws Exception {
        // Create a per-test working directory for isolation.
        // Use toRealPath() to resolve symlinks (macOS: /var -> /private/var)
        // which avoids mismatches between sshj and Apache SSHD path resolution.
        workDir = Files.createTempDirectory(tempDir, "test-").toRealPath();

        // Create seed files in workDir
        Files.writeString(workDir.resolve("hello.txt"), "Hello, World!");
        Files.createDirectories(workDir.resolve("subdir"));
        Files.writeString(workDir.resolve("subdir").resolve("nested.txt"), "nested content");

        bookmark = new GBookmark("test", "localhost",
                workDir.toAbsolutePath().toString(),
                "testuser", "testpass", port, GBookmark.G_SFTP_PROTOCOL);
        conn = new GConnection(bookmark);
        plugin = new GProtocolPluginSFTP(conn);
    }

    @AfterEach
    void tearDown() {
        try {
            if (plugin != null) {
                plugin.gpp_disconnect(conn);
            }
        } catch (Exception ignored) {
        }
    }

    // 1. connect succeeds, isSecure = true
    @Test
    void connectSucceeds_isSecureTrue() throws GConnectionException {
        plugin.gpp_connect(bookmark);
        assertTrue(plugin.getIsConnected());
        assertTrue(plugin.getIsSecure());
    }

    // 2. connect with bad password throws GConnectionException
    @Test
    void connectWithBadPassword_throwsGConnectionException() throws GConnectionException {
        GBookmark badBm = new GBookmark("test", "localhost",
                workDir.toAbsolutePath().toString(),
                "testuser", "wrongpass", port, GBookmark.G_SFTP_PROTOCOL);
        GConnection badConn = new GConnection(badBm);
        GProtocolPluginSFTP badPlugin = new GProtocolPluginSFTP(badConn);

        GConnectionException ex = assertThrows(GConnectionException.class, () ->
                badPlugin.gpp_connect(badBm));
        assertEquals(GConnectionException.GCE_BAD_LOGIN, ex.getType());
    }

    // 3. disconnect
    @Test
    void disconnect_setsNotConnected() throws GConnectionException {
        plugin.gpp_connect(bookmark);
        assertTrue(plugin.getIsConnected());
        plugin.gpp_disconnect(conn);
        assertFalse(plugin.getIsConnected());
        // Prevent double-disconnect in tearDown
        plugin = null;
    }

    // 4. list directory
    @Test
    void listDirectory_returnsEntries() throws GConnectionException {
        plugin.gpp_connect(bookmark);
        Object result = plugin.gpp_list(workDir.toAbsolutePath().toString());
        assertNotNull(result);
        assertTrue(result instanceof Object[]);
        Object[] entries = (Object[]) result;
        // Should contain at least hello.txt, subdir, and possibly hostkey.ser
        assertTrue(entries.length >= 2, "Expected at least 2 entries, got " + entries.length);

        boolean foundHello = false;
        boolean foundSubdir = false;
        for (Object entry : entries) {
            RemoteResourceInfo info = (RemoteResourceInfo) entry;
            if ("hello.txt".equals(info.getName())) foundHello = true;
            if ("subdir".equals(info.getName())) foundSubdir = true;
        }
        assertTrue(foundHello, "hello.txt not found in listing");
        assertTrue(foundSubdir, "subdir not found in listing");
    }

    // 5. cwd to valid path
    @Test
    void cwdToValidPath() throws GConnectionException {
        plugin.gpp_connect(bookmark);
        String subdirPath = workDir.resolve("subdir").toAbsolutePath().toString();
        plugin.gpp_cwd(subdirPath);
        assertEquals(subdirPath, plugin.gpp_get_pwd());
    }

    // 6. getPwd
    @Test
    void getPwd_returnsCurrentDirectory() throws GConnectionException {
        plugin.gpp_connect(bookmark);
        String pwd = plugin.gpp_get_pwd();
        assertNotNull(pwd);
        // After connect, pwd should be the canonicalized "." which is typically user home or /
        assertFalse(pwd.isEmpty());
    }

    // 7. mkdir
    @Test
    void mkdir_createsDirectory() throws GConnectionException {
        plugin.gpp_connect(bookmark);
        String newDir = workDir.resolve("newdir").toAbsolutePath().toString();
        plugin.gpp_mkdir(newDir);
        assertTrue(Files.isDirectory(workDir.resolve("newdir")));
    }

    // 8. rename file
    @Test
    void renameFile() throws GConnectionException {
        plugin.gpp_connect(bookmark);
        String oldPath = workDir.resolve("hello.txt").toAbsolutePath().toString();
        String newPath = workDir.resolve("renamed.txt").toAbsolutePath().toString();
        plugin.gpp_rename(oldPath, newPath);
        assertTrue(Files.exists(workDir.resolve("renamed.txt")));
        assertFalse(Files.exists(workDir.resolve("hello.txt")));
    }

    // 9. rm file
    @Test
    void rmFile_deletesFile() throws Exception {
        plugin.gpp_connect(bookmark);
        // Create a dedicated file for this test
        Path toDelete = workDir.resolve("deleteme.txt");
        Files.writeString(toDelete, "delete me");
        assertTrue(Files.exists(toDelete));
        plugin.gpp_rm(toDelete.toAbsolutePath().toString(), false);
        assertFalse(Files.exists(toDelete));
    }

    // 10. exists file / exists missing
    @Test
    void existsFile_returnsFileItem() throws GConnectionException {
        plugin.gpp_connect(bookmark);
        String filePath = workDir.resolve("hello.txt").toAbsolutePath().toString();
        GFileItem item = plugin.gpp_exists(filePath, "hello.txt", false);
        assertNotNull(item, "gpp_exists should return non-null for existing file");
        assertEquals("hello.txt", item.getName());
    }

    @Test
    void existsMissing_returnsNull() throws GConnectionException {
        plugin.gpp_connect(bookmark);
        String missingPath = workDir.resolve("nonexistent.txt").toAbsolutePath().toString();
        GFileItem item = plugin.gpp_exists(missingPath, "nonexistent.txt", false);
        assertNull(item, "gpp_exists should return null for missing file");
    }

    // 11. upload and download round-trip
    @Test
    void uploadAndDownload_roundTrip() throws Exception {
        plugin.gpp_connect(bookmark);

        // Prepare a local file to upload
        Path localSrc = workDir.resolve("upload_src.txt");
        String content = "Round-trip test content with special chars: \u00e9\u00e0\u00fc";
        Files.writeString(localSrc, content);

        String remotePath = workDir.resolve("uploaded.txt").toAbsolutePath().toString();

        // Upload
        plugin.gpp_prepareToTransferFile(null, 0);
        try (FileInputStream fis = new FileInputStream(localSrc.toFile())) {
            plugin.gpp_upload(remotePath, fis, localSrc.toFile(), null);
        }

        assertTrue(Files.exists(workDir.resolve("uploaded.txt")));

        // Download
        Path localDst = workDir.resolve("downloaded.txt");
        plugin.gpp_prepareToTransferFile(null, 0);
        try (FileOutputStream fos = new FileOutputStream(localDst.toFile())) {
            plugin.gpp_download(remotePath, fos, localDst.toFile(), null);
        }

        String downloaded = Files.readString(localDst);
        assertEquals(content, downloaded, "Downloaded content should match uploaded content");
    }

    @Test
    void transferListener_receivesCumulativeMonotonicBytes() throws Exception {
        java.util.List<Long> seen = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        byte[] payload = new byte[300000];
        plugin.gpp_connect(bookmark);
        Path localSrc = workDir.resolve("progress_src.bin");
        Files.write(localSrc, payload);
        plugin.gpp_prepareToTransferFile(null, 0);
        try (FileInputStream fis = new FileInputStream(localSrc.toFile())) {
            plugin.gpp_upload(workDir.resolve("progress_up.bin").toAbsolutePath().toString(), fis, localSrc.toFile(), seen::add);
        }
        assertFalse(seen.isEmpty(), "listener must be called");
        for (int i = 1; i < seen.size(); i++) assertTrue(seen.get(i) >= seen.get(i - 1), "cumulative, never decreasing");
        assertEquals(payload.length, seen.get(seen.size() - 1).longValue(), "last value is the file size");
    }

    // 12. chmod changes permissions
    @Test
    void chmod_changesPermissions() throws Exception {
        // This test only works on POSIX-compliant file systems
        plugin.gpp_connect(bookmark);

        String filePath = workDir.resolve("hello.txt").toAbsolutePath().toString();

        // Set permissions to 644 (rw-r--r--)
        // GPermissions bitfield: OWNER_READ|OWNER_WRITE|GROUP_READ|OTHER_READ
        // Numeric value would be 644
        GPermissions perms644 = new GPermissions("-rw-r--r--");
        plugin.gpp_chmod(filePath, perms644);

        Set<PosixFilePermission> actual = Files.getPosixFilePermissions(workDir.resolve("hello.txt"));
        assertTrue(actual.contains(PosixFilePermission.OWNER_READ));
        assertTrue(actual.contains(PosixFilePermission.OWNER_WRITE));
        assertFalse(actual.contains(PosixFilePermission.OWNER_EXECUTE));
        assertTrue(actual.contains(PosixFilePermission.GROUP_READ));
        assertFalse(actual.contains(PosixFilePermission.GROUP_WRITE));
        assertTrue(actual.contains(PosixFilePermission.OTHERS_READ));
        assertFalse(actual.contains(PosixFilePermission.OTHERS_WRITE));

        // Now chmod to 755 (rwxr-xr-x)
        GPermissions perms755 = new GPermissions("-rwxr-xr-x");
        plugin.gpp_chmod(filePath, perms755);

        actual = Files.getPosixFilePermissions(workDir.resolve("hello.txt"));
        assertTrue(actual.contains(PosixFilePermission.OWNER_EXECUTE));
        assertTrue(actual.contains(PosixFilePermission.GROUP_EXECUTE));
        assertTrue(actual.contains(PosixFilePermission.OTHERS_EXECUTE));
    }
}
