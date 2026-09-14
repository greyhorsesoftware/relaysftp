package com.gideonsoftworks.gcore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.text.ParseException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class GListParserS3Test {

    private GListParserS3 parser;

    @BeforeEach
    void setUp() {
        parser = new GListParserS3();
    }

    @Test
    void parseS3Object_returnsFileWithCorrectNameAndSize() throws ParseException {
        S3Object obj = S3Object.builder()
                .key("folder/file.txt")
                .size(1234L)
                .lastModified(Instant.now())
                .build();

        GFileItem item = parser.parseLine(obj);

        assertNotNull(item);
        assertEquals(GFileItem.G_FILE, item.getFileType());
        assertEquals("file.txt", item.getName());
        assertEquals(1234L, item.getRawSize());
    }

    @Test
    void parseS3Object_rootKey_nameIsFullKey() throws ParseException {
        S3Object obj = S3Object.builder()
                .key("readme.md")
                .size(42L)
                .lastModified(Instant.now())
                .build();

        GFileItem item = parser.parseLine(obj);

        assertNotNull(item);
        assertEquals(GFileItem.G_FILE, item.getFileType());
        assertEquals("readme.md", item.getName());
    }

    @Test
    void parseCommonPrefix_returnsFolderWithCorrectName() throws ParseException {
        CommonPrefix cp = CommonPrefix.builder()
                .prefix("photos/")
                .build();

        GFileItem item = parser.parseLine(cp);

        assertNotNull(item);
        assertEquals(GFileItem.G_FOLDER, item.getFileType());
        assertEquals("photos", item.getName());
    }

    @Test
    void parseNestedCommonPrefix_returnsLeafFolderName() throws ParseException {
        CommonPrefix cp = CommonPrefix.builder()
                .prefix("a/b/c/")
                .build();

        GFileItem item = parser.parseLine(cp);

        assertNotNull(item);
        assertEquals(GFileItem.G_FOLDER, item.getFileType());
        assertEquals("c", item.getName());
    }

    @Test
    void parseUnknownObjectType_returnsNull() throws ParseException {
        GFileItem item = parser.parseLine("not an S3 type");

        assertNull(item);
    }

    @Test
    void parseS3Object_withLastModified_dateIsSet() throws ParseException {
        Instant now = Instant.now();
        S3Object obj = S3Object.builder()
                .key("data/report.csv")
                .size(500L)
                .lastModified(now)
                .build();

        GFileItem item = parser.parseLine(obj);

        assertNotNull(item);
        assertNotNull(item.getDateModified());
        assertEquals(now.toEpochMilli(), item.getDateModified().getTime());
    }
}
