package com.gideonsoftworks.gdo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Persistence tests for {@link GDataArray} — the versioned JSON backing store for local favorite paths.
 * Runs against a temp dir with no app static-init coupling.
 */
class GDataArrayTest
{
    private static final String CLASS = GBookmarkFavoritePath.class.getName();

    @Test
    void emptyList_writesEmptyArray_andRoundTrips(@TempDir File dir) throws Exception
    {
        File out = new File(dir, "LocalFavoritePaths.json");

        GDataArray empty = new GDataArray();
        assertTrue(empty.writeToJsonFile(out), "writing an empty list should succeed");
        assertTrue(out.exists(), "empty list must write a file, not delete it");

        String json = Files.readString(out.toPath());
        assertTrue(json.contains("\"schemaVersion\""), "file must carry a schemaVersion");
        assertTrue(json.contains("\"items\""), "file must carry an items array");

        GDataArray reloaded = new GDataArray(out, CLASS);
        assertEquals(0, reloaded.getItemCount(), "empty array must round-trip to an empty list");
    }

    @Test
    void nonEmptyList_roundTrips_withSchemaVersion(@TempDir File dir) throws Exception
    {
        File out = new File(dir, "LocalFavoritePaths.json");

        GDataArray a = new GDataArray();
        a.addItem(new GBookmarkFavoritePath("Café", "/Users/me/Café"));
        a.addItem(new GBookmarkFavoritePath("docs", "/Users/me/docs"));
        assertTrue(a.writeToJsonFile(out));

        String json = Files.readString(out.toPath());
        assertTrue(json.contains("\"schemaVersion\": 1"));

        GDataArray reloaded = new GDataArray(out, CLASS);
        assertEquals(2, reloaded.getItemCount());
        GBookmarkFavoritePath f0 = (GBookmarkFavoritePath) reloaded.getItemAtIndex(0);
        assertEquals("Café",           f0.getObjectForKey(GBookmarkFavoritePath.FAVORITE_NAME));
        assertEquals("/Users/me/Café", f0.getObjectForKey(GBookmarkFavoritePath.FAVORITE_PATH));
    }

    @Test
    void loads_legacyBareArrayFile(@TempDir File dir) throws Exception
    {
        // pre-schemaVersion format: a bare JSON array
        File out = new File(dir, "LocalFavoritePaths.json");
        Files.writeString(out.toPath(),
            "[ { \"FAVORITE_NAME\": \"docs\", \"FAVORITE_PATH\": \"/Users/me/docs\" } ]");

        GDataArray loaded = new GDataArray(out, CLASS);
        assertEquals(1, loaded.getItemCount());
        GBookmarkFavoritePath f0 = (GBookmarkFavoritePath) loaded.getItemAtIndex(0);
        assertEquals("/Users/me/docs", f0.getObjectForKey(GBookmarkFavoritePath.FAVORITE_PATH));
    }

    @Test
    void writeToJsonFile_leavesNoTmpFileBehind(@TempDir File dir) throws Exception
    {
        File out = new File(dir, "LocalFavoritePaths.json");
        GDataArray a = new GDataArray();
        a.addItem(new GBookmarkFavoritePath("docs", "/Users/me/docs"));
        a.writeToJsonFile(out);

        File[] tmp = dir.listFiles((d, n) -> n.endsWith(".tmp"));
        assertNotNull(tmp);
        assertEquals(0, tmp.length, "atomic write must not leave a .tmp file after a successful save");
    }
}
