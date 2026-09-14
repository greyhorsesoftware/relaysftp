package com.gideonsoftworks.gcore;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class GConnectionTest {

    // ---- FILE protocol tests (no network needed) ----

    @Test
    void constructorWithFileProtocol() throws GConnectionException {
        GConnection conn = fileConnection();
        assertFalse(conn.getIsRemote());
    }

    @Test
    void loginAndLogout_FILE() throws GConnectionException {
        GConnection conn = fileConnection();

        conn.login();
        assertTrue(conn.getIsConnected());

        conn.logout();
        assertFalse(conn.getIsConnected());
    }

    @Test
    void getListParser_afterLogin_FILE() throws GConnectionException {
        GConnection conn = fileConnection();
        conn.login();

        GListParser parser = conn.getListParser();
        assertNotNull(parser);
        assertInstanceOf(GListParserFILE.class, parser);

        conn.logout();
    }

    @Test
    void getBookmark() throws GConnectionException {
        GBookmark bm = fileBookmark();
        GConnection conn = new GConnection(bm);
        assertSame(bm, conn.getBookmark());
    }

    @Test
    void getSeparator_FILE() throws GConnectionException {
        GConnection conn = fileConnection();
        assertEquals(File.separator, conn.getSeparator());
    }

    @Test
    void needsKeepAlive_FILE() throws GConnectionException {
        GConnection conn = fileConnection();
        assertFalse(conn.needsKeepAlive());
    }

    @Test
    void getIsSecure_FILE() throws GConnectionException {
        GConnection conn = fileConnection();
        assertFalse(conn.getIsSecure());
    }

    // ---- Remote protocol constructor-only tests (no login) ----

    @Test
    void constructorWithFTPProtocol() throws GConnectionException {
        GConnection conn = connectionForProtocol(GBookmark.G_FTP_PROTOCOL);
        assertTrue(conn.getIsRemote());
    }

    @Test
    void constructorWithSFTPProtocol() throws GConnectionException {
        GConnection conn = connectionForProtocol(GBookmark.G_SFTP_PROTOCOL);
        assertTrue(conn.getIsRemote());
        assertTrue(conn.getIsSecure());
    }

    @Test
    void constructorWithS3Protocol() throws GConnectionException {
        GConnection conn = connectionForProtocol(GBookmark.G_S3_PROTOCOL);
        assertTrue(conn.getIsRemote());
        assertTrue(conn.getIsSecure());
    }

    @Test
    void constructorWithFTPSSLProtocol() throws GConnectionException {
        GConnection conn = connectionForProtocol(GBookmark.G_FTPSSL_PROTOCOL);
        assertTrue(conn.getIsRemote());
    }

    @Test
    void getIsSecure_SFTP() throws GConnectionException {
        GConnection conn = connectionForProtocol(GBookmark.G_SFTP_PROTOCOL);
        assertTrue(conn.getIsSecure());
    }

    // ---- helpers ----

    private static GBookmark fileBookmark() {
        return new GBookmark("localhost", System.getProperty("user.home"),
                "", "", 0, GBookmark.G_FILE_PROTOCOL);
    }

    private static GConnection fileConnection() throws GConnectionException {
        return new GConnection(fileBookmark());
    }

    private static GConnection connectionForProtocol(int protocol) throws GConnectionException {
        return new GConnection(new GBookmark("test.example.com", "/home",
                "user", "pass", 22, protocol));
    }
}
