package com.gideonsoftworks.gcore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GProtocolPluginFILETest {

    @TempDir
    Path tempDir;

    private GConnection conn;
    private GProtocolPluginFILE plugin;

    @BeforeEach
    void setUp() throws GConnectionException {
        GBookmark bm = new GBookmark("local", "/", null, null, 0, GBookmark.G_FILE_PROTOCOL);
        conn = new GConnection(bm);
        plugin = new GProtocolPluginFILE(conn);
    }

    @Test
    void connectAndDisconnectSucceed() throws GConnectionException {
        GBookmark bm = conn.getBookmark();

        plugin.gpp_connect(bm);
        assertTrue(plugin.getIsConnected());

        plugin.gpp_disconnect(conn);
        assertFalse(plugin.getIsConnected());
    }

    @Test
    void listDirectoryWithFiles() throws GConnectionException, IOException {
        Files.createFile(tempDir.resolve("alpha.txt"));
        Files.createFile(tempDir.resolve("beta.txt"));
        Files.createDirectory(tempDir.resolve("subdir"));

        Object result = plugin.gpp_list(tempDir.toString());
        assertInstanceOf(File[].class, result);

        File[] files = (File[]) result;
        assertEquals(3, files.length);
    }

    @Test
    void listEmptyDirectory() throws GConnectionException {
        Object result = plugin.gpp_list(tempDir.toString());
        assertInstanceOf(File[].class, result);

        File[] files = (File[]) result;
        assertEquals(0, files.length);
    }

    @Test
    void mkdirCreatesDirectory() throws GConnectionException {
        String newDir = tempDir.resolve("newFolder").toString();

        plugin.gpp_mkdir(newDir);

        assertTrue(new File(newDir).exists());
        assertTrue(new File(newDir).isDirectory());
    }

    @Test
    void rmFile() throws GConnectionException, IOException {
        Path file = Files.createFile(tempDir.resolve("toDelete.txt"));
        assertTrue(file.toFile().exists());

        plugin.gpp_rm(file.toString(), false);

        assertFalse(file.toFile().exists());
    }

    @Test
    void rmDirectory() throws GConnectionException, IOException {
        Path dir = Files.createDirectory(tempDir.resolve("toDeleteDir"));
        assertTrue(dir.toFile().exists());

        plugin.gpp_rm(dir.toString(), true);

        assertFalse(dir.toFile().exists());
    }

    @Test
    void renameFile() throws GConnectionException, IOException {
        Path original = Files.createFile(tempDir.resolve("original.txt"));
        String renamed = tempDir.resolve("renamed.txt").toString();

        plugin.gpp_rename(original.toString(), renamed);

        assertFalse(original.toFile().exists());
        assertTrue(new File(renamed).exists());
    }

    @Test
    void cwdToValidPath() throws GConnectionException {
        // Should not throw for an existing directory
        assertDoesNotThrow(() -> plugin.gpp_cwd(tempDir.toString()));
    }

    @Test
    void cwdToInvalidPathThrowsGConnectionException() {
        String bogus = tempDir.resolve("nonexistent").toString();

        GConnectionException ex = assertThrows(GConnectionException.class,
                () -> plugin.gpp_cwd(bogus));

        assertEquals(GConnectionException.GCE_BAD_FOLDER, ex.getType());
    }

    @Test
    void existsForExistingFileReturnsNonNull() throws GConnectionException, IOException {
        Path file = Files.createFile(tempDir.resolve("present.txt"));

        GFileItem item = plugin.gpp_exists(file.toString(), "present.txt", false);

        assertNotNull(item);
    }

    @Test
    void existsForMissingFileReturnsNull() throws GConnectionException {
        GFileItem item = plugin.gpp_exists(
                tempDir.resolve("ghost.txt").toString(), "ghost.txt", false);

        assertNull(item);
    }

    @Test
    void getPwdReturnsPath() throws GConnectionException {
        String pwd = plugin.gpp_get_pwd();

        assertNotNull(pwd);
        assertEquals(System.getProperty("user.home"), pwd);
    }

    @Test
    void getSeparatorReturnsFileSeparator() {
        assertEquals(File.separator, plugin.gpp_getSeparator());
    }

    @Test
    void chmodIsNoOp() throws GConnectionException, IOException {
        Path file = Files.createFile(tempDir.resolve("chmodTarget.txt"));
        GPermissions perms = new GPermissions(0, "-");

        // Should complete without throwing
        assertDoesNotThrow(() -> plugin.gpp_chmod(file.toString(), perms));
    }

    @Test
    void parserInstanceReturnsGListParserFILE() throws GConnectionException {
        GListParser parser = plugin.gpp_parser_instance();

        assertNotNull(parser);
        assertInstanceOf(GListParserFILE.class, parser);
    }

    @Test
    void needsCompletePathForRenameReturnsTrue() {
        assertTrue(plugin.gpp_needsCompletePathForRename());
    }

    @Test
    void sendCommandReturnsNull() throws GConnectionException {
        assertNull(plugin.gpp_send_command("any command"));
    }

    @Test
    void moveFile() throws GConnectionException, IOException {
        Path source = Files.createFile(tempDir.resolve("moveMe.txt"));
        String destination = tempDir.resolve("moved.txt").toString();

        plugin.gpp_move(source.toString(), destination);

        assertFalse(source.toFile().exists());
        assertTrue(new File(destination).exists());
    }
}
