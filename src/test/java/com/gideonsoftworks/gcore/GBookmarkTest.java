package com.gideonsoftworks.gcore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GBookmarkTest {

    @Test
    void fullConstructorSetsAllFields() {
        GBookmark bm = new GBookmark("My Server", "ftp.example.com", "/home/user",
                "alice", "secret", 22, GBookmark.G_SFTP_PROTOCOL);

        assertEquals("My Server", bm.getName());
        assertEquals("ftp.example.com", bm.getHostname());
        assertEquals("/home/user", bm.getHomePath());
        assertEquals("alice", bm.getUsername());
        assertEquals("secret", bm.getPassword());
        assertEquals(22, bm.getPort());
        assertEquals(GBookmark.G_SFTP_PROTOCOL, bm.getProtocol());
    }

    @Test
    void shortConstructorDefaultsNameToHostname() {
        GBookmark bm = new GBookmark("ftp.example.com", "/pub", "anon", "anon@", 21,
                GBookmark.G_FTP_PROTOCOL);

        assertEquals("ftp.example.com", bm.getName());
        assertEquals("ftp.example.com", bm.getHostname());
    }

    @Test
    void copyConstructorCreatesIndependentCopy() {
        GBookmark original = new GBookmark("Original", "host.com", "/root",
                "user", "pass", 21, GBookmark.G_FTP_PROTOCOL);

        GBookmark copy = new GBookmark(original);

        assertEquals(original.getName(), copy.getName());
        assertEquals(original.getHostname(), copy.getHostname());
        assertEquals(original.getHomePath(), copy.getHomePath());
        assertEquals(original.getUsername(), copy.getUsername());
        assertEquals(original.getPassword(), copy.getPassword());
        assertEquals(original.getPort(), copy.getPort());
        assertEquals(original.getProtocol(), copy.getProtocol());

        // Mutating the copy must not affect the original
        copy.setHostname("other.com");
        copy.setUsername("changed");
        assertEquals("host.com", original.getHostname());
        assertEquals("user", original.getUsername());
    }

    @Test
    void isLocalReturnsTrueOnlyForFileProtocol() {
        assertTrue(bookmark(GBookmark.G_FILE_PROTOCOL).isLocal());

        assertFalse(bookmark(GBookmark.G_FTP_PROTOCOL).isLocal());
        assertFalse(bookmark(GBookmark.G_SFTP_PROTOCOL).isLocal());
        assertFalse(bookmark(GBookmark.G_FTPSSL_PROTOCOL).isLocal());
        assertFalse(bookmark(GBookmark.G_S3_PROTOCOL).isLocal());
        assertFalse(bookmark(GBookmark.G_UNKNOWN_PROTOCOL).isLocal());
    }

    @Test
    void isSecureReturnsTrueForSftpAndS3Only() {
        assertTrue(bookmark(GBookmark.G_SFTP_PROTOCOL).isSecure());
        assertTrue(bookmark(GBookmark.G_S3_PROTOCOL).isSecure());

        assertFalse(bookmark(GBookmark.G_FTP_PROTOCOL).isSecure());
        assertFalse(bookmark(GBookmark.G_FILE_PROTOCOL).isSecure());
        assertFalse(bookmark(GBookmark.G_FTPSSL_PROTOCOL).isSecure());
        assertFalse(bookmark(GBookmark.G_UNKNOWN_PROTOCOL).isSecure());
    }

    @Test
    void getProtocolStringReturnsCorrectScheme() {
        assertEquals("sftp://", bookmark(GBookmark.G_SFTP_PROTOCOL).getProtocolString());
        assertEquals("ftp://", bookmark(GBookmark.G_FTP_PROTOCOL).getProtocolString());
        assertEquals("s3://", bookmark(GBookmark.G_S3_PROTOCOL).getProtocolString());
        assertEquals("", bookmark(GBookmark.G_FILE_PROTOCOL).getProtocolString());
        assertEquals("", bookmark(GBookmark.G_FTPSSL_PROTOCOL).getProtocolString());
        assertEquals("", bookmark(GBookmark.G_UNKNOWN_PROTOCOL).getProtocolString());
    }

    @Test
    void s3PropertiesRoundTrip() {
        GBookmark bm = new GBookmark("s3test", "s3.amazonaws.com", null, null, null, 0, GBookmark.G_S3_PROTOCOL);

        assertNull(bm.getS3AccessKey());
        assertNull(bm.getS3SecretKey());
        assertNull(bm.getS3Region());
        assertNull(bm.getS3EndpointUrl());

        bm.setS3AccessKey("AKIAIOSFODNN7EXAMPLE");
        bm.setS3SecretKey("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");
        bm.setS3Region("us-west-2");
        bm.setS3EndpointUrl("https://s3.us-west-2.amazonaws.com");

        assertEquals("AKIAIOSFODNN7EXAMPLE", bm.getS3AccessKey());
        assertEquals("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", bm.getS3SecretKey());
        assertEquals("us-west-2", bm.getS3Region());
        assertEquals("https://s3.us-west-2.amazonaws.com", bm.getS3EndpointUrl());
    }

    @Test
    void protocolConstantsHaveExpectedValues() {
        assertEquals(-1, GBookmark.G_UNKNOWN_PROTOCOL);
        assertEquals(0, GBookmark.G_FTP_PROTOCOL);
        assertEquals(1, GBookmark.G_SFTP_PROTOCOL);
        assertEquals(2, GBookmark.G_FILE_PROTOCOL);
        assertEquals(3, GBookmark.G_FTPSSL_PROTOCOL);
        assertEquals(4, GBookmark.G_S3_PROTOCOL);
    }

    @Test
    void setterGetterRoundTrips() {
        GBookmark bm = bookmark(GBookmark.G_FTP_PROTOCOL);

        bm.setName("NewName");
        assertEquals("NewName", bm.getName());

        bm.setHostname("new.host.com");
        assertEquals("new.host.com", bm.getHostname());

        bm.setUsername("bob");
        assertEquals("bob", bm.getUsername());

        bm.setPassword("hunter2");
        assertEquals("hunter2", bm.getPassword());

        bm.setPort(2222);
        assertEquals(2222, bm.getPort());

        bm.setProtocol(GBookmark.G_SFTP_PROTOCOL);
        assertEquals(GBookmark.G_SFTP_PROTOCOL, bm.getProtocol());

        bm.setHomePath("/new/home");
        assertEquals("/new/home", bm.getHomePath());

        bm.setLocalHomePath("/local/path");
        assertEquals("/local/path", bm.getLocalHomePath());
    }

    // ---- helpers ----

    /** Create a minimal bookmark with the given protocol. */
    private static GBookmark bookmark(int protocol) {
        return new GBookmark("test", "host.example.com", "/home", "user", "pass", 21, protocol);
    }
}
