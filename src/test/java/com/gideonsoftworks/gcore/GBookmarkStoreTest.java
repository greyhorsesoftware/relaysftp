package com.gideonsoftworks.gcore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GBookmarkStore} — the pure (static-state-free) bookmark persistence layer.
 * No app static init / DATA_FOLDER_PATH coupling: everything runs against a temp dir.
 */
class GBookmarkStoreTest
{
    private static GBookmark ftp(String name, String host, String user, int port)
    {
        return new GBookmark(name, host, "/home", user, "pw", port, GBookmark.G_FTP_PROTOCOL);
    }

    @Test
    void save_afterHomePathChange_rewritesSameFile(@TempDir File dir) throws Exception
    {
        // "Set as Home Folder" edits the saved bookmark's remote path and saves immediately.
        GBookmark a = ftp("Alpha", "a.example.com", "alice", 21);
        GBookmarkStore.save(dir, List.of(a));
        a.setHomePath("/srv/www");
        GBookmarkStore.save(dir, List.of(a));

        File[] relay = dir.listFiles((d, n) -> n.endsWith(".relay"));
        assertNotNull(relay);
        assertEquals(1, relay.length, "re-saving the same bookmark must not leave a second .relay file");
        assertEquals("/srv/www", GBookmarkStore.load(dir).get(0).getHomePath());
    }

    @Test
    void roundTrip_preservesFields(@TempDir File dir) throws Exception
    {
        GBookmark a = ftp("Alpha", "a.example.com", "alice", 21);
        a.setLocalHomePath("/local/a");

        GBookmark b = new GBookmark("S3", "my-bucket", null, "AKIA", "secret", 0, GBookmark.G_S3_PROTOCOL);
        b.setS3AccessKey("AKIA");
        b.setS3Region("us-west-2");
        b.setS3EndpointUrl("https://s3.example");
        b.setS3AuthMethod("basic");

        GBookmarkStore.save(dir, List.of(a, b));
        List<GBookmark> loaded = GBookmarkStore.load(dir);   // name-sorted: "Alpha", "S3"

        assertEquals(2, loaded.size());

        GBookmark la = loaded.get(0);
        assertEquals("Alpha", la.getName());
        assertEquals("a.example.com", la.getHostname());
        assertEquals("alice", la.getUsername());
        assertEquals(21, la.getPort());
        assertEquals(GBookmark.G_FTP_PROTOCOL, la.getProtocol());
        assertEquals("/home", la.getHomePath());
        assertEquals("/local/a", la.getLocalHomePath());
        assertNull(la.getPassword(), "password is keyring-only, never in the .relay file");

        GBookmark lb = loaded.get(1);
        assertEquals("S3", lb.getName());
        assertEquals(GBookmark.G_S3_PROTOCOL, lb.getProtocol());
        assertEquals("AKIA", lb.getS3AccessKey());
        assertEquals("us-west-2", lb.getS3Region());
        assertEquals("https://s3.example", lb.getS3EndpointUrl());
        assertEquals("basic", lb.getS3AuthMethod());
    }

    @Test
    void files_areNamedAfterBookmark_withRelayExtension(@TempDir File dir) throws Exception
    {
        GBookmarkStore.save(dir, List.of(ftp("My Server", "h", "u", 21)));
        assertTrue(new File(dir, "My Server.relay").isFile());
    }

    @Test
    void fileName_sanitizesIllegalChars()
    {
        String fn = GBookmarkStore.fileNameFor(ftp("a/b:c*?", "h", "u", 21), new HashSet<>());
        assertFalse(fn.contains("/"));
        assertFalse(fn.contains(":"));
        assertFalse(fn.contains("*"));
        assertTrue(fn.endsWith(".relay"));
    }

    @Test
    void fileName_dedupesCollisions()
    {
        Set<String> used = new HashSet<>();
        GBookmark x = ftp("Server", "h", "u", 21);
        assertEquals("Server.relay",   GBookmarkStore.fileNameFor(x, used));
        assertEquals("Server 2.relay", GBookmarkStore.fileNameFor(x, used));
        assertEquals("Server 3.relay", GBookmarkStore.fileNameFor(x, used));
    }

    @Test
    void save_prunesOrphansFromDeletedOrRenamedBookmarks(@TempDir File dir) throws Exception
    {
        GBookmarkStore.save(dir, List.of(ftp("Keep", "h", "u", 21), ftp("Gone", "h", "u", 21)));
        assertTrue(new File(dir, "Gone.relay").isFile());

        // re-save without "Gone" → its file is pruned (rewrite-all + prune)
        GBookmarkStore.save(dir, List.of(ftp("Keep", "h", "u", 21)));
        assertFalse(new File(dir, "Gone.relay").isFile());
        assertTrue(new File(dir, "Keep.relay").isFile());
        assertEquals(1, GBookmarkStore.load(dir).size());
    }

    @Test
    void load_skipsMalformedFiles(@TempDir File dir) throws Exception
    {
        GBookmarkStore.save(dir, List.of(ftp("Good", "h", "u", 21)));
        Files.writeString(new File(dir, "Broken.relay").toPath(), "{ not valid json");

        List<GBookmark> loaded = GBookmarkStore.load(dir);
        assertEquals(1, loaded.size());
        assertEquals("Good", loaded.get(0).getName());
    }

    @Test
    void roundTrip_preservesNonAsciiName(@TempDir File dir) throws Exception
    {
        String name = "Åland Serveur café 日本語";
        GBookmarkStore.save(dir, List.of(ftp(name, "h.example.com", "user", 21)));

        List<GBookmark> loaded = GBookmarkStore.load(dir);
        assertEquals(1, loaded.size());
        assertEquals(name, loaded.get(0).getName(),
            "non-ASCII bookmark name must survive the UTF-8 save/load round-trip");
    }

    @Test
    void load_skipsRelayMissingName_withoutException(@TempDir File dir) throws Exception
    {
        GBookmarkStore.save(dir, List.of(ftp("Good", "h", "u", 21)));
        // valid JSON object, but no "name" field
        Files.writeString(new File(dir, "NoName.relay").toPath(),
            "{ \"hostname\": \"h\", \"port\": 21, \"protocol\": 0 }");

        List<GBookmark> loaded = assertDoesNotThrow(() -> GBookmarkStore.load(dir));
        assertEquals(1, loaded.size());
        assertEquals("Good", loaded.get(0).getName());
    }

    @Test
    void save_writesSchemaVersion(@TempDir File dir) throws Exception
    {
        GBookmarkStore.save(dir, List.of(ftp("Versioned", "h", "u", 21)));
        String json = Files.readString(new File(dir, "Versioned.relay").toPath());
        assertTrue(json.contains("\"schemaVersion\""), "saved .relay must carry a schemaVersion");
        assertTrue(json.contains("\"schemaVersion\": 1"), "current schemaVersion is 1");
    }

    @Test
    void load_toleratesFileWithoutSchemaVersion(@TempDir File dir) throws Exception
    {
        // an old .relay written before schemaVersion existed must still load
        Files.writeString(new File(dir, "Legacy.relay").toPath(),
            "{ \"name\": \"Legacy\", \"hostname\": \"h\", \"port\": 21, \"protocol\": 0 }");

        List<GBookmark> loaded = GBookmarkStore.load(dir);
        assertEquals(1, loaded.size());
        assertEquals("Legacy", loaded.get(0).getName());
    }

    @Test
    void save_leavesNoTmpFileBehind(@TempDir File dir) throws Exception
    {
        GBookmarkStore.save(dir, List.of(ftp("Alpha", "h", "u", 21), ftp("Beta", "h", "u", 21)));
        File[] tmp = dir.listFiles((d, n) -> n.endsWith(".tmp"));
        assertNotNull(tmp);
        assertEquals(0, tmp.length, "atomic write must not leave a .tmp file after a successful save");
    }

    @Test
    void passwordKey_isHostPortUser()
    {
        GBookmark bm = new GBookmark("n", "h.example.com", null, "bob", "p", 22, GBookmark.G_SFTP_PROTOCOL);
        assertEquals("pw:h.example.com:22:bob", GBookmarkStore.passwordKey(bm));
    }

    @Test
    void isAnonymous_trueForBlankOrNullUser()
    {
        assertTrue( GBookmarkStore.isAnonymous(new GBookmark("n", "h", null, null,   null, 21, GBookmark.G_FTP_PROTOCOL)));
        assertTrue( GBookmarkStore.isAnonymous(new GBookmark("n", "h", null, "  ",   null, 21, GBookmark.G_FTP_PROTOCOL)));
        assertFalse(GBookmarkStore.isAnonymous(new GBookmark("n", "h", null, "alice",null, 21, GBookmark.G_FTP_PROTOCOL)));
    }
}
