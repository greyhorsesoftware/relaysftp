package com.gideonsoftworks.gcore;

import org.apache.commons.net.ftp.FTPClient;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the exact command sequence {@link GFtpProxyLogin} sends for each firewall type (proxy.txt Types
 * 1–10), driving a real commons-net {@link FTPClient} against an in-process fake firewall that records every
 * command and always replies positively.
 */
class GFtpProxyLoginTest
{
    @Test void type1_userAtHost()         throws Exception { assertSeq( "FTP_1",  "USER alice@real.host", "PASS pw" ); }
    @Test void type2_firewallThenUser()   throws Exception { assertSeq( "FTP_2",  "USER fwuser", "PASS fwpw", "USER alice@real.host", "PASS pw" ); }
    @Test void type3_site()               throws Exception { assertSeq( "FTP_3",  "SITE real.host", "USER alice", "PASS pw" ); }
    @Test void type4_open()               throws Exception { assertSeq( "FTP_4",  "OPEN real.host", "USER alice", "PASS pw" ); }
    @Test void type5_combinedUserPass()   throws Exception { assertSeq( "FTP_5",  "USER alice@fwuser@real.host", "PASS pw@fwpw" ); }
    @Test void type6_fwUserHostThenUser() throws Exception { assertSeq( "FTP_6",  "USER fwuser@real.host", "PASS fwpw", "USER alice", "PASS pw" ); }
    @Test void type7_acct()               throws Exception { assertSeq( "FTP_7",  "USER alice@real.host fwuser", "PASS pw", "ACCT fwpw" ); }
    @Test void type8_hostColonPort()      throws Exception { assertSeq( "FTP_8",  "USER alice@real.host:21", "PASS pw" ); }
    @Test void type9_hostSpacePort()      throws Exception { assertSeq( "FTP_9",  "USER alice@real.host 21", "PASS pw" ); }
    @Test void type10_userAtFwAtHost()    throws Exception { assertSeq( "FTP_10", "USER alice@fwuser@real.host", "PASS pw" ); }

    private void assertSeq( String typeId, String... expected ) throws Exception
    {
        try( FakeFirewall fw = new FakeFirewall() )
        {
            FTPClient c = new FTPClient();
            c.setConnectTimeout( 10000 );
            c.connect( "127.0.0.1", fw.port() );
            boolean ok = GFtpProxyLogin.run( c, GFtpFirewallTypes.byId( typeId ), "real.host", 21, "alice", "pw", "fwuser", "fwpw" );
            assertTrue( ok, "should finish logged in (server always 2xx)" );
            // the server records commands on its own thread — wait for the full sequence to land before asserting
            long deadline = System.currentTimeMillis() + 2000;
            while( fw.commands.size() < expected.length && System.currentTimeMillis() < deadline ) Thread.sleep( 5 );
            assertEquals( List.of( expected ), fw.commands, "command sequence for " + typeId );
            try { c.disconnect(); } catch( IOException ignore ) {}
        }
    }

    /** Minimal FTP server: greets 220, records each command, replies 331 to USER and 2xx to everything else. */
    private static final class FakeFirewall implements AutoCloseable
    {
        final ServerSocket server = new ServerSocket( 0, 50, java.net.InetAddress.getLoopbackAddress() );
        final List<String> commands = new CopyOnWriteArrayList<>();

        FakeFirewall() throws IOException
        {
            Thread t = new Thread( () -> { try { handle( server.accept() ); } catch( IOException ignore ) {} } );
            t.setDaemon( true );
            t.start();
        }

        int port() { return server.getLocalPort(); }

        private void handle( Socket s ) throws IOException
        {
            BufferedReader in = new BufferedReader( new InputStreamReader( s.getInputStream(), StandardCharsets.US_ASCII ) );
            OutputStream out = s.getOutputStream();
            write( out, "220 fake firewall ready" );
            String line;
            while( ( line = in.readLine() ) != null )
            {
                commands.add( line.trim() );
                String cmd = line.split( " ", 2 )[0].toUpperCase();
                if( cmd.equals( "QUIT" ) ) { write( out, "221 bye" ); break; }
                else if( cmd.equals( "USER" ) ) write( out, "331 need password" );
                else write( out, "230 ok" );
            }
            s.close();
        }

        private static void write( OutputStream out, String reply ) throws IOException
        {
            out.write( ( reply + "\r\n" ).getBytes( StandardCharsets.US_ASCII ) );
            out.flush();
        }

        @Override public void close() throws IOException { server.close(); }
    }
}
