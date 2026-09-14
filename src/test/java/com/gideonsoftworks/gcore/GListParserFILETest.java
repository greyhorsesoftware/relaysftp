package com.gideonsoftworks.gcore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;

import static org.junit.jupiter.api.Assertions.*;

class GListParserFILETest {

    private GListParserFILE parser;

    @BeforeEach
    void setUp() {
        parser = new GListParserFILE();
    }

    @Test
    void parseTempFile_returnsFileItem(@TempDir File tempDir) throws IOException, ParseException {
        File tempFile = new File(tempDir, "example.txt");
        assertTrue(tempFile.createNewFile());

        GFileItem item = parser.parseLine(tempFile);

        assertNotNull(item);
        assertEquals("example.txt", item.getName());
        assertEquals(GFileItem.G_FILE, item.getFileType());
    }

    @Test
    void parseTempDirectory_returnsFolderItem(@TempDir File tempDir) throws ParseException {
        File subDir = new File(tempDir, "myFolder");
        assertTrue(subDir.mkdir());

        GFileItem item = parser.parseLine(subDir);

        assertNotNull(item);
        assertEquals("myFolder", item.getName());
        assertEquals(GFileItem.G_FOLDER, item.getFileType());
    }

    @Test
    void getName_returnsExpectedString() {
        assertEquals("FILE:// Listing Parser", parser.getName());
    }
}
