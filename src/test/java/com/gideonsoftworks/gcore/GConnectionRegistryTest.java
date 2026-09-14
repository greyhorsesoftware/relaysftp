package com.gideonsoftworks.gcore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** {@link GConnection#openConnections()}: connected connections are listed; logged-out ones are not. */
class GConnectionRegistryTest
{
    @Test
    void connectedConnectionIsListed_untilLogout( @TempDir Path dir ) throws Exception
    {
        GBookmark bm = new GBookmark( "local", dir.toAbsolutePath().toString(), "u", "p", 0, GBookmark.G_FILE_PROTOCOL );
        GConnection c = new GConnection( bm );
        assertFalse( GConnection.openConnections().contains( c ), "constructed but not logged in: not open" );

        c.login();
        assertTrue( GConnection.openConnections().contains( c ), "logged in: open" );

        c.logout();
        assertFalse( GConnection.openConnections().contains( c ), "logged out: gone" );
    }
}
