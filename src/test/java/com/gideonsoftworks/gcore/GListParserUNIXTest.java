package com.gideonsoftworks.gcore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.text.ParseException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GListParserUNIX#parseLine(Object)}.
 *
 * Each test calls parseLine directly with a raw Unix FTP listing string
 * and verifies the resulting GFileItem fields.
 */
class GListParserUNIXTest {

    private GListParserUNIX parser;

    @BeforeEach
    void setUp() {
        parser = new GListParserUNIX();
    }

    @Test
    void parseRegularFile() throws ParseException {
        String line = "-rw-r--r--   1 user  group      1234 Jan 15 10:30 readme.txt";
        GFileItem item = parser.parseLine(line);

        assertNotNull(item);
        assertEquals("readme.txt", item.getName());
        assertEquals(GFileItem.G_FILE, item.getFileType());
        assertEquals(1234L, item.getRawSize());
    }

    @Test
    void parseDirectory() throws ParseException {
        String line = "drwxr-xr-x   2 user  group      4096 Mar 10  2023 subdir";
        GFileItem item = parser.parseLine(line);

        assertNotNull(item);
        assertEquals("subdir", item.getName());
        assertEquals(GFileItem.G_FOLDER, item.getFileType());
        assertNotNull(item.getPermissions());
        assertEquals("d", item.getPermissions().getStartChar());
    }

    @Test
    void parseSymlink() throws ParseException {
        String line = "lrwxrwxrwx   1 wuftpd   wuftpd         14 Jul 22  2002 MIRRORS -> README";
        GFileItem item = parser.parseLine(line);

        assertNotNull(item);
        assertEquals(GFileItem.G_LINK, item.getFileType());
        assertEquals("MIRRORS", item.getName());
    }

    @Test
    void totalLineReturnsNull() throws ParseException {
        String line = "total 12345";
        GFileItem item = parser.parseLine(line);

        assertNull(item);
    }

    @Test
    void parseDateWithYearFormat() throws ParseException {
        String line = "drwxr-xr-x   2 user  group      4096 Mar 10  2023 archive";
        GFileItem item = parser.parseLine(line);

        assertNotNull(item);
        assertNotNull(item.getDateModified());
        // The date should have been parsed -- verify it is non-null and represents March 2023
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(item.getDateModified());
        assertEquals(java.util.Calendar.MARCH, cal.get(java.util.Calendar.MONTH));
        assertEquals(2023, cal.get(java.util.Calendar.YEAR));
        assertEquals(10, cal.get(java.util.Calendar.DAY_OF_MONTH));
    }

    @Test
    void parseDateWithTimeFormat() throws ParseException {
        String line = "-rw-r--r--   1 user  group       512 Jan 15 10:30 notes.txt";
        GFileItem item = parser.parseLine(line);

        assertNotNull(item);
        assertNotNull(item.getDateModified());
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(item.getDateModified());
        assertEquals(java.util.Calendar.JANUARY, cal.get(java.util.Calendar.MONTH));
        assertEquals(15, cal.get(java.util.Calendar.DAY_OF_MONTH));
        // Time format includes hours and minutes
        assertEquals(10, cal.get(java.util.Calendar.HOUR_OF_DAY));
        assertEquals(30, cal.get(java.util.Calendar.MINUTE));
    }

    @Test
    void parsePermissionsNumericValue() throws ParseException {
        // -rw-r--r-- should be 644
        String line = "-rw-r--r--   1 user  group      1234 Jan 15 10:30 readme.txt";
        GFileItem item = parser.parseLine(line);

        assertNotNull(item);
        GPermissions perms = item.getPermissions();
        assertNotNull(perms);
        assertEquals(644, perms.getNumericValue());
        assertEquals("-", perms.getStartChar());
    }

    @Test
    void parseOwnerAndGroup() throws ParseException {
        String line = "-rw-r--r--   1 alice  developers      999 Feb 28 14:00 config.yml";
        GFileItem item = parser.parseLine(line);

        assertNotNull(item);
        assertEquals("alice", item.getOwner());
        assertEquals("developers", item.getGroup());
    }
}
