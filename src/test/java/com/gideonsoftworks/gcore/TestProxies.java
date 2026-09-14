package com.gideonsoftworks.gcore;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-process proxy servers + a {@link ProxySelector} helper for testing the proxy wiring without a real proxy. */
final class TestProxies
{
    private TestProxies() {}

    /** A ProxySelector that returns {@code proxy} for every request (install via ProxySelector.setDefault). */
    static ProxySelector selectorReturning( Proxy proxy )
    {
        return new ProxySelector() {
            @Override public List<Proxy> select( URI uri ) { return List.of( proxy ); }
            @Override public void connectFailed( URI uri, SocketAddress sa, IOException ioe ) {}
        };
    }

    /** Minimal HTTP CONNECT proxy. Optionally requires Basic auth; records the CONNECT targets it served. */
    static final class HttpConnect implements AutoCloseable
    {
        final ServerSocket server = new ServerSocket( 0 );
        final List<String> targets = new CopyOnWriteArrayList<>();
        volatile String requiredAuth;   // null = no auth; else "user:pass"

        HttpConnect() throws IOException { accept(); }
        HttpConnect requireAuth( String user, String pass ) { requiredAuth = user + ":" + pass; return this; }
        int port() { return server.getLocalPort(); }
        Proxy proxy() { return new Proxy( Proxy.Type.HTTP, new InetSocketAddress( "127.0.0.1", port() ) ); }

        private void accept()
        {
            daemon( () -> { try { while( !server.isClosed() ) handle( server.accept() ); } catch( IOException ignore ) {} } );
        }

        private void handle( Socket client )
        {
            daemon( () -> {
                try {
                    String headers = readHeaders( client.getInputStream() );
                    String[] lines = headers.split( "\r\n" );
                    String target = lines[0].split( " " )[1];   // CONNECT host:port HTTP/1.1

                    if( requiredAuth != null && !hasValidAuth( lines ) )
                    {
                        write( client, "HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=\"t\"\r\n\r\n" );
                        client.close();
                        return;
                    }
                    targets.add( target );
                    String[] hp = target.split( ":" );
                    Socket upstream = new Socket( hp[0], Integer.parseInt( hp[1] ) );
                    write( client, "HTTP/1.1 200 Connection established\r\n\r\n" );
                    pump( client, upstream );
                    pump( upstream, client );
                } catch( IOException ignore ) {}
            } );
        }

        private boolean hasValidAuth( String[] headerLines )
        {
            String want = "Basic " + Base64.getEncoder().encodeToString( requiredAuth.getBytes( StandardCharsets.UTF_8 ) );
            for( String l : headerLines )
                if( l.regionMatches( true, 0, "Proxy-Authorization:", 0, 20 ) && l.substring( 20 ).trim().equals( want ) )
                    return true;
            return false;
        }

        @Override public void close() throws IOException { server.close(); }
    }

    /** Minimal SOCKS5 proxy (no-auth or username/password). Records the targets it served. */
    static final class Socks5 implements AutoCloseable
    {
        final ServerSocket server = new ServerSocket( 0 );
        final List<String> targets = new CopyOnWriteArrayList<>();
        volatile String requiredUser, requiredPass;

        Socks5() throws IOException { accept(); }
        Socks5 requireAuth( String user, String pass ) { requiredUser = user; requiredPass = pass; return this; }
        int port() { return server.getLocalPort(); }
        Proxy proxy() { return new Proxy( Proxy.Type.SOCKS, new InetSocketAddress( "127.0.0.1", port() ) ); }

        private void accept()
        {
            daemon( () -> { try { while( !server.isClosed() ) handle( server.accept() ); } catch( IOException ignore ) {} } );
        }

        private void handle( Socket client )
        {
            daemon( () -> {
                try {
                    DataInputStream in = new DataInputStream( client.getInputStream() );
                    OutputStream out = client.getOutputStream();

                    in.readUnsignedByte();                       // ver 5
                    int nMethods = in.readUnsignedByte();
                    byte[] methods = new byte[nMethods];
                    in.readFully( methods );
                    boolean needAuth = requiredUser != null;
                    out.write( new byte[]{ 0x05, (byte) ( needAuth ? 0x02 : 0x00 ) } );   // method: 02 user/pass, 00 none
                    out.flush();

                    if( needAuth )
                    {
                        in.readUnsignedByte();                   // auth ver 1
                        byte[] u = new byte[in.readUnsignedByte()]; in.readFully( u );
                        byte[] p = new byte[in.readUnsignedByte()]; in.readFully( p );
                        boolean ok = new String( u, StandardCharsets.UTF_8 ).equals( requiredUser )
                                  && new String( p, StandardCharsets.UTF_8 ).equals( requiredPass );
                        out.write( new byte[]{ 0x01, (byte) ( ok ? 0x00 : 0x01 ) } );
                        out.flush();
                        if( !ok ) { client.close(); return; }
                    }

                    in.readUnsignedByte();                       // ver 5
                    in.readUnsignedByte();                       // cmd 1 = connect
                    in.readUnsignedByte();                       // rsv
                    int atyp = in.readUnsignedByte();
                    String host;
                    if( atyp == 1 )      { byte[] a = new byte[4];  in.readFully( a ); host = InetAddress.getByAddress( a ).getHostAddress(); }
                    else if( atyp == 3 ) { byte[] a = new byte[in.readUnsignedByte()]; in.readFully( a ); host = new String( a, StandardCharsets.US_ASCII ); }
                    else                 { byte[] a = new byte[16]; in.readFully( a ); host = InetAddress.getByAddress( a ).getHostAddress(); }
                    int port = in.readUnsignedShort();
                    targets.add( host + ":" + port );

                    Socket upstream = new Socket( host, port );
                    out.write( new byte[]{ 0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0 } );   // succeeded
                    out.flush();
                    pump( client, upstream );
                    pump( upstream, client );
                } catch( IOException ignore ) {}
            } );
        }

        @Override public void close() throws IOException { server.close(); }
    }

    /** TCP echo server: writes back whatever it receives. */
    static final class Echo implements AutoCloseable
    {
        final ServerSocket server = new ServerSocket( 0 );
        Echo() throws IOException { daemon( () -> { try { while( !server.isClosed() ) { Socket c = server.accept(); pump( c, c ); } } catch( IOException ignore ) {} } ); }
        int port() { return server.getLocalPort(); }
        @Override public void close() throws IOException { server.close(); }
    }

    // ---- shared plumbing ----

    static void daemon( Runnable r ) { Thread t = new Thread( r ); t.setDaemon( true ); t.start(); }

    static void write( Socket s, String text ) throws IOException
    {
        s.getOutputStream().write( text.getBytes( StandardCharsets.US_ASCII ) );
        s.getOutputStream().flush();
    }

    static String readHeaders( InputStream in ) throws IOException
    {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        String term = "\r\n\r\n";
        int state = 0, c;
        while( ( c = in.read() ) != -1 )
        {
            b.write( c );
            if( c == term.charAt( state ) ) { if( ++state == 4 ) break; }
            else state = ( c == '\r' ) ? 1 : 0;
        }
        return b.toString( StandardCharsets.US_ASCII );
    }

    static void pump( Socket from, Socket to )
    {
        daemon( () -> {
            try {
                InputStream in = from.getInputStream();
                OutputStream out = to.getOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while( ( n = in.read( buf ) ) != -1 ) { out.write( buf, 0, n ); out.flush(); }
            } catch( IOException ignore ) {}
        } );
    }
}
