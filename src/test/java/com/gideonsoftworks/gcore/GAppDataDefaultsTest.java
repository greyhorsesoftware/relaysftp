package com.gideonsoftworks.gcore;

import com.gideonsoftworks.gja.GTransferFilter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the first-run application-support defaults written by {@link GAppDataDefaults}
 * (which {@code Gideon.createDataFolder()} delegates to). Runs entirely against a temp dir —
 * no real {@code ~/Library/Application Support} writes, no {@code System.exit}/Swing coupling.
 */
class GAppDataDefaultsTest
{
    @TempDir Path tmp;

    @Test
    void firstRun_createsDirsAndDefaultFiles() throws Exception
    {
        File data = tmp.resolve("AppSupport").toFile();
        File temp = tmp.resolve("Temp").toFile();

        GAppDataDefaults.ensureDefaults(data, temp, "/Users/tester");

        assertTrue(data.isDirectory(), "data folder created");
        assertTrue(temp.isDirectory(), "temp folder created");

        File favFile  = new File(data, "LocalFavoritePaths.json");
        File filtFile = new File(data, "TransferFilters.json");
        assertTrue(favFile.exists(),  "LocalFavoritePaths.json written");
        assertTrue(filtFile.exists(), "TransferFilters.json written");

        // Local favorites: Home / Desktop / Documents under the given home path.
        JsonArray fav = JsonParser.parseString(Files.readString(favFile.toPath())).getAsJsonArray();
        assertEquals(3, fav.size());
        assertEquals("Home",                  fav.get(0).getAsJsonObject().get("FAVORITE_NAME").getAsString());
        assertEquals("/Users/tester",         fav.get(0).getAsJsonObject().get("FAVORITE_PATH").getAsString());
        assertEquals("Desktop",               fav.get(1).getAsJsonObject().get("FAVORITE_NAME").getAsString());
        assertEquals("/Users/tester/Desktop", fav.get(1).getAsJsonObject().get("FAVORITE_PATH").getAsString());
        assertEquals("/Users/tester/Documents", fav.get(2).getAsJsonObject().get("FAVORITE_PATH").getAsString());

        // Transfer filters: dotfile skip (starts-with ".", type 3) + ".DS_Store" (matches, type 2), both enabled.
        JsonArray filt = JsonParser.parseString(Files.readString(filtFile.toPath())).getAsJsonArray();
        assertEquals(2, filt.size());
        JsonObject dot = filt.get(0).getAsJsonObject();
        assertTrue(dot.get("enabled").getAsBoolean());
        assertEquals(3,   dot.get("type").getAsInt());
        assertEquals(".", dot.get("value").getAsString());
        JsonObject dsStore = filt.get(1).getAsJsonObject();
        assertTrue(dsStore.get("enabled").getAsBoolean());
        assertEquals(2,          dsStore.get("type").getAsInt());
        assertEquals(".DS_Store", dsStore.get("value").getAsString());
    }

    @Test
    void existingFiles_areNotOverwritten() throws Exception
    {
        File data = tmp.resolve("AppSupport").toFile();
        assertTrue(data.mkdirs());
        File filt = new File(data, "TransferFilters.json");
        Files.writeString(filt.toPath(), "CUSTOM-USER-RULES");

        GAppDataDefaults.ensureDefaults(data, tmp.resolve("Temp").toFile(), "/home/x");

        assertEquals("CUSTOM-USER-RULES", Files.readString(filt.toPath()), "existing file preserved");
        // the absent favorites file is still created
        assertTrue(new File(data, "LocalFavoritePaths.json").exists());
    }

    @Test
    void filterTypeConstants_matchGTransferFilter()
    {
        // Guard the magic numbers in the default JSON against GTransferFilter's enum.
        assertEquals(GTransferFilter.TF_STARTSWITH, 3);
        assertEquals(GTransferFilter.TF_MATCHES,    2);
    }
}
