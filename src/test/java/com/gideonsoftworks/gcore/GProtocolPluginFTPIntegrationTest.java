package com.gideonsoftworks.gcore;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for GProtocolPluginFTP using MockFtpServer.
 *
 * These tests exercise the FTP plugin against a fake FTP server to verify
 * connect, disconnect, directory operations, file transfer, and metadata queries.
 */
@Tag("integration")
class GProtocolPluginFTPIntegrationTest {

    private static FakeFtpServer server;
    private static int port;

    private GConnection connection;
    private GProtocolPluginFTP plugin;

    @BeforeAll
    static void startServer() {
        server = new FakeFtpServer();
        server.setServerControlPort(0);
        server.addUserAccount(new UserAccount("testuser", "testpass", "/home/testuser"));

        FileSystem fs = new UnixFakeFileSystem();
        fs.add(new DirectoryEntry("/"));
        fs.add(new DirectoryEntry("/home"));
        fs.add(new DirectoryEntry("/home/testuser"));
        fs.add(new FileEntry("/home/testuser/test.txt", "hello world"));
        fs.add(new DirectoryEntry("/home/testuser/subdir"));
        fs.add(new FileEntry("/home/testuser/subdir/nested.txt", "nested content"));
        server.setFileSystem(fs);

        server.start();
        port = server.getServerControlPort();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @BeforeEach
    void connect() throws GConnectionException {
        GBookmark bm = new GBookmark("test", "localhost", "/home/testuser",
                "testuser", "testpass", port, GBookmark.G_FTP_PROTOCOL);
        connection = new GConnection(bm);
        plugin = (GProtocolPluginFTP) getProtocolPlugin(connection);
        connection.login();
    }

    @AfterEach
    void disconnect() {
        try {
            if (connection != null && connection.getIsConnected()) {
                connection.logout();
            }
        } catch (GConnectionException ignored) {
        }
    }

    /**
     * Access the package-private gcProtocol field via a helper in the same package.
     */
    private static GProtocolPlugin getProtocolPlugin(GConnection conn) {
        // GConnection stores gcProtocol as a private field. Since we are in the
        // same package we can use reflection as a fallback, but first try the
        // fact that GConnection creates the plugin in its constructor and we can
        // just cast after constructing a new plugin. Actually, we can use
        // reflection since we are testing.
        try {
            java.lang.reflect.Field f = GConnection.class.getDeclaredField("gcProtocol");
            f.setAccessible(true);
            return (GProtocolPlugin) f.get(conn);
        } catch (Exception e) {
            throw new RuntimeException("Could not access gcProtocol field", e);
        }
    }

    // ---------------------------------------------------------------
    // Connection tests
    // ---------------------------------------------------------------

    @Test
    void connectSucceeds() throws GConnectionException {
        assertTrue(connection.getIsConnected());
    }

    @Test
    void connectWithBadPasswordThrows() {
        GBookmark bad = new GBookmark("bad", "localhost", "/home/testuser",
                "testuser", "wrongpass", port, GBookmark.G_FTP_PROTOCOL);
        GConnectionException ex = assertThrows(GConnectionException.class, () -> {
            GConnection badConn = new GConnection(bad);
            badConn.login();
        });
        assertEquals(GConnectionException.GCE_BAD_LOGIN, ex.getType());
    }

    @Test
    void disconnectSetsNotConnected() throws GConnectionException {
        assertTrue(connection.getIsConnected());
        connection.logout();
        assertFalse(connection.getIsConnected());
    }

    // ---------------------------------------------------------------
    // Directory listing
    // ---------------------------------------------------------------

    @Test
    void listDirectory() throws GConnectionException {
        Object result = plugin.gpp_list("/home/testuser");
        assertNotNull(result);
        assertTrue(result instanceof org.apache.commons.net.ftp.FTPFile[]);
        org.apache.commons.net.ftp.FTPFile[] files = (org.apache.commons.net.ftp.FTPFile[]) result;
        // Should contain at least test.txt and subdir
        assertTrue(files.length >= 2, "Expected at least 2 entries, got " + files.length);
    }

    // ---------------------------------------------------------------
    // CWD / PWD
    // ---------------------------------------------------------------

    @Test
    void cwdToValidPath() throws GConnectionException {
        plugin.gpp_cwd("/home/testuser/subdir");
        String pwd = plugin.gpp_get_pwd();
        assertEquals("/home/testuser/subdir", pwd);
    }

    @Test
    void cwdToInvalidPathThrows() {
        GConnectionException ex = assertThrows(GConnectionException.class, () -> {
            plugin.gpp_cwd("/nonexistent/path");
        });
        assertEquals(GConnectionException.GCE_BAD_FOLDER, ex.getType());
    }

    @Test
    void getPwd() throws GConnectionException {
        String pwd = plugin.gpp_get_pwd();
        assertNotNull(pwd);
        assertEquals("/home/testuser", pwd);
    }

    // ---------------------------------------------------------------
    // mkdir
    // ---------------------------------------------------------------

    @Test
    void mkdir() throws GConnectionException {
        plugin.gpp_mkdir("/home/testuser/newdir");
        // Verify by cwd into it
        plugin.gpp_cwd("/home/testuser/newdir");
        assertEquals("/home/testuser/newdir", plugin.gpp_get_pwd());
    }

    // ---------------------------------------------------------------
    // rename
    // ---------------------------------------------------------------

    @Test
    void rename() throws GConnectionException {
        // Create a file to rename by uploading
        server.getFileSystem().add(new FileEntry("/home/testuser/renameme.txt", "rename test"));

        plugin.gpp_rename("/home/testuser/renameme.txt", "/home/testuser/renamed.txt");

        // The old name should no longer exist, the new one should
        GFileItem found = plugin.gpp_exists("/home/testuser/renamed.txt", "renamed.txt", false);
        assertNotNull(found, "Renamed file should exist");
    }

    // ---------------------------------------------------------------
    // rm
    // ---------------------------------------------------------------

    @Test
    void rmFile() throws GConnectionException {
        server.getFileSystem().add(new FileEntry("/home/testuser/deleteme.txt", "delete me"));

        plugin.gpp_rm("/home/testuser/deleteme.txt", false);

        assertFalse(server.getFileSystem().exists("/home/testuser/deleteme.txt"),
                "File should have been deleted from the server filesystem");
    }

    @Test
    void rmDirectory() throws GConnectionException {
        server.getFileSystem().add(new DirectoryEntry("/home/testuser/rmdir"));

        plugin.gpp_rm("/home/testuser/rmdir", true);

        assertFalse(server.getFileSystem().exists("/home/testuser/rmdir"),
                "Directory should have been deleted from the server filesystem");
    }

    // ---------------------------------------------------------------
    // exists
    // ---------------------------------------------------------------

    @Test
    void existsFindsFile() throws GConnectionException {
        GFileItem item = plugin.gpp_exists("/home/testuser/test.txt", "test.txt", false);
        assertNotNull(item, "test.txt should exist");
        assertEquals("test.txt", item.getName());
    }

    @Test
    void existsReturnNullForMissingDirectory() throws GConnectionException {
        // Use directory existence check (CWD-based) which works reliably with MockFtpServer.
        // File existence uses MLST/SIZE commands which MockFtpServer may not fully support.
        GFileItem item = plugin.gpp_exists("/home/testuser/nosuchdir", "nosuchdir", true);
        assertNull(item, "Non-existent directory should return null");
    }

    @Test
    void existsFindsDirectory() throws GConnectionException {
        GFileItem item = plugin.gpp_exists("/home/testuser/subdir", "subdir", true);
        assertNotNull(item, "subdir directory should exist");
    }

    // ---------------------------------------------------------------
    // Upload and download round-trip
    // ---------------------------------------------------------------

    @Test
    void uploadAndDownloadRoundTrip(@TempDir Path tempDir) throws Exception {
        String content = "Round trip test content with special chars: @#$%";

        // Create a temp file to upload
        Path srcFile = tempDir.resolve("upload.txt");
        Files.writeString(srcFile, content);

        // Upload
        try (FileInputStream fis = new FileInputStream(srcFile.toFile())) {
            plugin.gpp_upload("/home/testuser/roundtrip.txt", fis, srcFile.toFile(), null);
        }

        // Download
        Path dstFile = tempDir.resolve("download.txt");
        try (FileOutputStream fos = new FileOutputStream(dstFile.toFile())) {
            plugin.gpp_download("/home/testuser/roundtrip.txt", fos, dstFile.toFile(), null);
        }

        String downloaded = Files.readString(dstFile);
        assertEquals(content, downloaded);
    }

    @Test
    void transferListener_receivesCumulativeMonotonicBytes(@TempDir Path tempDir) throws Exception {
        java.util.List<Long> seen = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        byte[] payload = new byte[200000];
        Path srcFile = tempDir.resolve("progress.bin");
        Files.write(srcFile, payload);
        try (FileInputStream fis = new FileInputStream(srcFile.toFile())) {
            plugin.gpp_upload("/home/testuser/progress.bin", fis, srcFile.toFile(), seen::add);
        }
        assertFalse(seen.isEmpty(), "listener must be called");
        for (int i = 1; i < seen.size(); i++) assertTrue(seen.get(i) >= seen.get(i - 1), "cumulative, never decreasing");
        assertEquals(payload.length, seen.get(seen.size() - 1).longValue(), "last value is the file size");
    }

    // ---------------------------------------------------------------
    // Capability queries
    // ---------------------------------------------------------------

    @Test
    void needsKeepAliveReturnsTrue() {
        assertTrue(plugin.gpp_needsKeepAlive());
    }

    @Test
    void needsCompletePathForRenameReturnsTrue() {
        assertTrue(plugin.gpp_needsCompletePathForRename());
    }
}
