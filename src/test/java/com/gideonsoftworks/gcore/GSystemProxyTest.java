package com.gideonsoftworks.gcore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link GSystemProxy#socketFactory} end-to-end against in-process HTTP CONNECT and SOCKS5
 * proxies ({@link TestProxies}) — including authenticated proxies. No real proxy or network needed.
 */
class GSystemProxyTest
{
    @AfterEach
    void clearCredentials()
    {
        GSystemProxy.setCredentials( null, null );
        Authenticator.setDefault( null );
    }

    @Test
    void httpConnectProxy_tunnelsBidirectionally() throws Exception
    {
        try( TestProxies.Echo echo = new TestProxies.Echo(); TestProxies.HttpConnect proxy = new TestProxies.HttpConnect() )
        {
            Socket s = GSystemProxy.socketFactory( proxy.proxy() ).createSocket();   // -> HttpConnectSocket
            s.connect( new InetSocketAddress( "127.0.0.1", echo.port() ), 3000 );    // -> CONNECT on connect()
            roundTrip( s, "ping" );
            assertEquals( "127.0.0.1:" + echo.port(), proxy.targets.get( 0 ), "proxy received CONNECT to the real target" );
            s.close();
        }
    }

    @Test
    void httpConnectProxy_authenticated() throws Exception
    {
        try( TestProxies.Echo echo = new TestProxies.Echo();
             TestProxies.HttpConnect proxy = new TestProxies.HttpConnect().requireAuth( "user", "secret" ) )
        {
            // no credentials -> proxy answers 407 -> connect fails (never silently goes direct), and the
            // proxy's own error reply is surfaced in the exception (the same status the audit log records).
            Socket bad = GSystemProxy.socketFactory( proxy.proxy() ).createSocket();
            IOException err = assertThrows( IOException.class, () -> bad.connect( new InetSocketAddress( "127.0.0.1", echo.port() ), 3000 ) );
            assertTrue( err.getMessage().contains( "407" ), "proxy's error reply surfaces: " + err.getMessage() );

            // with credentials -> preemptive Proxy-Authorization -> tunnels
            GSystemProxy.setCredentials( "user", "secret" );
            Socket ok = GSystemProxy.socketFactory( proxy.proxy() ).createSocket();
            ok.connect( new InetSocketAddress( "127.0.0.1", echo.port() ), 3000 );
            roundTrip( ok, "auth" );
            ok.close();
        }
    }

    @Test
    void socksProxy_tunnelsBidirectionally() throws Exception
    {
        try( TestProxies.Echo echo = new TestProxies.Echo(); TestProxies.Socks5 proxy = new TestProxies.Socks5() )
        {
            Socket s = GSystemProxy.socketFactory( proxy.proxy() ).createSocket();   // -> new Socket(socksProxy)
            s.connect( new InetSocketAddress( "127.0.0.1", echo.port() ), 3000 );     // -> SOCKS5 by the JDK
            roundTrip( s, "sock" );
            assertEquals( "127.0.0.1:" + echo.port(), proxy.targets.get( 0 ), "SOCKS proxy received the real target" );
            s.close();
        }
    }

    @Test
    void socks5Factory_tunnelsWithoutAuth() throws Exception
    {
        try( TestProxies.Echo echo = new TestProxies.Echo(); TestProxies.Socks5 proxy = new TestProxies.Socks5() )
        {
            Socket s = GSystemProxy.socks5Factory( "127.0.0.1", proxy.port(), null, null ).createSocket();
            s.connect( new InetSocketAddress( "127.0.0.1", echo.port() ), 3000 );
            roundTrip( s, "sok5" );
            assertEquals( "127.0.0.1:" + echo.port(), proxy.targets.get( 0 ) );
            s.close();
        }
    }

    @Test
    void socks5Factory_authenticatedTunnel() throws Exception
    {
        try( TestProxies.Echo echo = new TestProxies.Echo();
             TestProxies.Socks5 proxy = new TestProxies.Socks5().requireAuth( "u", "p" ) )
        {
            // our own RFC 1929 handshake — no JDK Authenticator involved
            Socket s = GSystemProxy.socks5Factory( "127.0.0.1", proxy.port(), "u", "p" ).createSocket();
            s.connect( new InetSocketAddress( "127.0.0.1", echo.port() ), 3000 );
            roundTrip( s, "auth" );
            assertEquals( "127.0.0.1:" + echo.port(), proxy.targets.get( 0 ) );
            s.close();
        }
    }

    @Test
    void socks5Factory_wrongPasswordFails() throws Exception
    {
        try( TestProxies.Echo echo = new TestProxies.Echo();
             TestProxies.Socks5 proxy = new TestProxies.Socks5().requireAuth( "u", "p" ) )
        {
            Socket s = GSystemProxy.socks5Factory( "127.0.0.1", proxy.port(), "u", "wrong" ).createSocket();
            IOException err = assertThrows( IOException.class,
                () -> s.connect( new InetSocketAddress( "127.0.0.1", echo.port() ), 3000 ) );
            assertTrue( err.getMessage().toLowerCase().contains( "auth" ), "surfaces auth failure: " + err.getMessage() );
        }
    }

    @Test
    void noProxy_connectsDirectly() throws Exception
    {
        try( TestProxies.Echo echo = new TestProxies.Echo() )
        {
            Socket s = GSystemProxy.socketFactory( Proxy.NO_PROXY ).createSocket();
            s.connect( new InetSocketAddress( "127.0.0.1", echo.port() ), 3000 );
            roundTrip( s, "hey!" );
            s.close();
        }
    }

    private static void roundTrip( Socket s, String msg ) throws IOException
    {
        s.getOutputStream().write( msg.getBytes( StandardCharsets.US_ASCII ) );
        s.getOutputStream().flush();
        byte[] buf = new byte[msg.length()];
        int off = 0;
        while( off < buf.length )
        {
            int r = s.getInputStream().read( buf, off, buf.length - off );
            if( r < 0 ) throw new EOFException();
            off += r;
        }
        assertEquals( msg, new String( buf, StandardCharsets.US_ASCII ), "data tunnelled round-trip" );
    }
}
