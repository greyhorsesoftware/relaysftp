package com.gideonsoftworks.gcore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Persistence round-trips for the FTP-firewall + transport proxy config, plus data-driven firewall types. */
class GProxyConfigTest
{
    @AfterEach
    void reset()
    {
        GProxyConfig.setType( GFtpFirewallType.NONE );
        GProxyConfig.setHost( "" );
        GProxyConfig.setPort( 0 );
        GProxyConfig.setUser( "" );
        GProxyConfig.setPassword( "" );
        GProxyConfig.setSocksEnabled( false );
        GProxyConfig.setSocksHost( "" );
        GProxyConfig.setSocksPort( 0 );
        GProxyConfig.setSocksUser( "" );
        GProxyConfig.setSocksPassword( "" );
    }

    @Test
    void ftpFirewallConfig_roundTrips()
    {
        GProxyConfig.setType( GFtpFirewallTypes.byId( "FTP_2" ) );
        GProxyConfig.setHost( "firewall.example.com" );
        GProxyConfig.setPort( 21 );
        GProxyConfig.setUser( "fwuser" );
        GProxyConfig.setPassword( "fwpass" );

        assertEquals( "FTP_2", GProxyConfig.getType().id() );
        assertTrue( GProxyConfig.isEnabled() );
        assertEquals( "firewall.example.com", GProxyConfig.getHost() );
        assertEquals( 21, GProxyConfig.getPort() );
        assertEquals( "fwuser", GProxyConfig.getUser() );
        assertTrue( GProxyConfig.hasPassword() );
        assertEquals( "fwpass", GProxyConfig.getPassword() );
    }

    @Test
    void socksConfig_roundTrips()
    {
        GProxyConfig.setSocksEnabled( true );
        GProxyConfig.setSocksHost( "socks.example.com" );
        GProxyConfig.setSocksPort( 1080 );
        GProxyConfig.setSocksUser( "puser" );
        GProxyConfig.setSocksPassword( "ppass" );

        assertTrue( GProxyConfig.isSocksEnabled() );
        assertEquals( "socks.example.com", GProxyConfig.getSocksHost() );
        assertEquals( 1080, GProxyConfig.getSocksPort() );
        assertEquals( "puser", GProxyConfig.getSocksUser() );
        assertTrue( GProxyConfig.hasSocksPassword() );
        assertEquals( "ppass", GProxyConfig.getSocksPassword() );
    }

    @Test
    void hasPasswordFlag_tracksWritesWithoutQueryingKeychainToCheck()
    {
        // The has-password flag is what the UI reads to show bubbles — it must mirror set/clear of the secret.
        assertFalse( GProxyConfig.hasPassword() );
        GProxyConfig.setPassword( "secret" );
        assertTrue( GProxyConfig.hasPassword() );
        GProxyConfig.setPassword( "" );
        assertFalse( GProxyConfig.hasPassword() );

        assertFalse( GProxyConfig.hasSocksPassword() );
        GProxyConfig.setSocksPassword( "x" );
        assertTrue( GProxyConfig.hasSocksPassword() );
        GProxyConfig.setSocksPassword( null );
        assertFalse( GProxyConfig.hasSocksPassword() );
    }

    @Test
    void clearedConfig_isNoneAndEmpty()
    {
        reset();   // prefs are global/persistent — clear first, then assert the documented defaults
        assertTrue( GProxyConfig.getType().isNone() );
        assertFalse( GProxyConfig.isEnabled() );
        assertFalse( GProxyConfig.isSocksEnabled() );
        assertEquals( "", GProxyConfig.getHost() );
        assertEquals( 0, GProxyConfig.getPort() );
    }

    @Test
    void registry_loadsNonePlusTenTypes_byIdRoundTrips()
    {
        assertEquals( 11, GFtpFirewallTypes.list().size() );   // None + Type 1..10
        for( GFtpFirewallType t : GFtpFirewallTypes.list() )
            assertEquals( t, GFtpFirewallTypes.byId( t.id() ) );
        assertTrue( GFtpFirewallTypes.byId( "nonsense" ).isNone() );
        assertTrue( GFtpFirewallTypes.byId( null ).isNone() );
    }

    @Test
    void firewallTypes_fieldUsageFlags_matchProxyTxt()
    {
        assertFalse( GFtpFirewallType.NONE.usesServer() );
        assertTrue ( GFtpFirewallTypes.byId( "FTP_1" ).usesServer() );
        assertFalse( GFtpFirewallTypes.byId( "FTP_1" ).usesUser() );
        assertFalse( GFtpFirewallTypes.byId( "FTP_1" ).usesPassword() );
        assertTrue ( GFtpFirewallTypes.byId( "FTP_2" ).usesUser() );
        assertTrue ( GFtpFirewallTypes.byId( "FTP_2" ).usesPassword() );
        assertTrue ( GFtpFirewallTypes.byId( "FTP_10" ).usesUser() );
        assertFalse( GFtpFirewallTypes.byId( "FTP_10" ).usesPassword() );
        assertTrue ( GFtpFirewallTypes.byId( "FTP_7" ).description().contains( "ACCT fwpass" ) );
    }
}
