package com.gideonsoftworks.gideon.browser;

import com.gideonsoftworks.gcore.GBookmark;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The login panel reuses a pane's existing connection only when the form still points at the same server. */
class GBrowserPanelSameServerTest {

    private static GBookmark bm( String host, String user, int port, int protocol ) {
        return new GBookmark( host, null, user, null, port, protocol );
    }

    @Test
    void sameServerIgnoresHostCaseAndPassword() {
        GBookmark a = bm( "Example.com", "dave", 22, GBookmark.G_SFTP_PROTOCOL );
        GBookmark b = bm( "example.com", "dave", 22, GBookmark.G_SFTP_PROTOCOL );
        b.setPassword( "secret" );
        assertTrue( GBrowserPanelController.isSameServer( a, b ) );
    }

    @Test
    void protocolChangeIsADifferentServer() {
        // the reported bug: SFTP bookmark failed, user switched to FTP and the SFTP connection was reused
        GBookmark sftp = bm( "ftp.gnu.org", null, 0, GBookmark.G_SFTP_PROTOCOL );
        GBookmark ftp  = bm( "ftp.gnu.org", null, 0, GBookmark.G_FTP_PROTOCOL );
        assertFalse( GBrowserPanelController.isSameServer( sftp, ftp ) );
    }

    @Test
    void hostPortOrUserChangeIsADifferentServer() {
        GBookmark base = bm( "localhost", "test", 58554, GBookmark.G_SFTP_PROTOCOL );
        assertFalse( GBrowserPanelController.isSameServer( base, bm( "ftp.gnu.org", "test", 58554, GBookmark.G_SFTP_PROTOCOL ) ) );
        assertFalse( GBrowserPanelController.isSameServer( base, bm( "localhost", "test", 22, GBookmark.G_SFTP_PROTOCOL ) ) );
        assertFalse( GBrowserPanelController.isSameServer( base, bm( "localhost", null, 58554, GBookmark.G_SFTP_PROTOCOL ) ) );
    }
}
