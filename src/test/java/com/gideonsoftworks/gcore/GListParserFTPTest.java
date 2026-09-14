package com.gideonsoftworks.gcore;

import org.apache.commons.net.ftp.FTPFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.*;

class GListParserFTPTest
{

    private GListParserFTP parser;

    @BeforeEach
    void setUp()
    {
        parser = new GListParserFTP();
    }

    @Test
    void parseRegularFile() throws ParseException
    {
        FTPFile ftpFile = new FTPFile();
        ftpFile.setType(FTPFile.FILE_TYPE);
        ftpFile.setName("report.txt");
        ftpFile.setSize(4096L);
        ftpFile.setUser("alice");
        ftpFile.setGroup("staff");
        ftpFile.setHardLinkCount(1);
        ftpFile.setTimestamp(Calendar.getInstance());

        GFileItem item = parser.parseLine(ftpFile);

        assertNotNull(item);
        assertEquals("report.txt", item.getName());
        assertEquals(GFileItem.G_FILE, item.getFileType());
        assertEquals(4096L, item.getRawSize());
        assertEquals("alice", item.getOwner());
        assertEquals("staff", item.getGroup());
    }

    @Test
    void parseDirectory() throws ParseException
    {
        FTPFile ftpFile = new FTPFile();
        ftpFile.setType(FTPFile.DIRECTORY_TYPE);
        ftpFile.setName("documents");
        ftpFile.setSize(0L);
        ftpFile.setUser("bob");
        ftpFile.setGroup("users");

        GFileItem item = parser.parseLine(ftpFile);

        assertNotNull(item);
        assertEquals("documents", item.getName());
        assertEquals(GFileItem.G_FOLDER, item.getFileType());
    }

    @Test
    void parseSymbolicLink() throws ParseException
    {
        FTPFile ftpFile = new FTPFile();
        ftpFile.setType(FTPFile.SYMBOLIC_LINK_TYPE);
        ftpFile.setName("latest");
        ftpFile.setSize(0L);
        ftpFile.setUser("root");
        ftpFile.setGroup("root");
        ftpFile.setLink("/var/data/current");

        GFileItem item = parser.parseLine(ftpFile);

        assertNotNull(item);
        assertEquals("latest", item.getName());
        assertEquals(GFileItem.G_LINK, item.getFileType());
    }

    @Test
    void parseNullFTPFileReturnsNull() throws ParseException
    {
        GFileItem item = parser.parseLine(null);

        assertNull(item);
    }

    @Test
    void parseFTPFileWithNullNameReturnsNull() throws ParseException
    {
        FTPFile ftpFile = new FTPFile();
        ftpFile.setType(FTPFile.FILE_TYPE);
        // name defaults to null on a fresh FTPFile

        GFileItem item = parser.parseLine(ftpFile);

        assertNull(item);
    }

    @Test
    void parseFileWithPermissions() throws ParseException
    {
        FTPFile ftpFile = new FTPFile();
        ftpFile.setType(FTPFile.FILE_TYPE);
        ftpFile.setName("script.sh");
        ftpFile.setSize(512L);
        ftpFile.setUser("deploy");
        ftpFile.setGroup("www");

        // Set rwxr-xr-- (754)
        ftpFile.setPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION, true);
        ftpFile.setPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION, true);
        ftpFile.setPermission(FTPFile.USER_ACCESS, FTPFile.EXECUTE_PERMISSION, true);
        ftpFile.setPermission(FTPFile.GROUP_ACCESS, FTPFile.READ_PERMISSION, true);
        ftpFile.setPermission(FTPFile.GROUP_ACCESS, FTPFile.WRITE_PERMISSION, false);
        ftpFile.setPermission(FTPFile.GROUP_ACCESS, FTPFile.EXECUTE_PERMISSION, true);
        ftpFile.setPermission(FTPFile.WORLD_ACCESS, FTPFile.READ_PERMISSION, true);
        ftpFile.setPermission(FTPFile.WORLD_ACCESS, FTPFile.WRITE_PERMISSION, false);
        ftpFile.setPermission(FTPFile.WORLD_ACCESS, FTPFile.EXECUTE_PERMISSION, false);

        GFileItem item = parser.parseLine(ftpFile);

        assertNotNull(item);
        GPermissions perms = item.getPermissions();
        assertNotNull(perms);
        assertEquals(754, perms.getNumericValue());
        assertEquals("-", perms.getStartChar());

        assertTrue(perms.getOwnerHasRead());
        assertTrue(perms.getOwnerHasWrite());
        assertTrue(perms.getOwnerHasExecute());
        assertTrue(perms.getGroupHasRead());
        assertFalse(perms.getGroupHasWrite());
        assertTrue(perms.getGroupHasExecute());
        assertTrue(perms.getWorldHasRead());
        assertFalse(perms.getWorldHasWrite());
        assertFalse(perms.getWorldHasExecute());
    }
}
