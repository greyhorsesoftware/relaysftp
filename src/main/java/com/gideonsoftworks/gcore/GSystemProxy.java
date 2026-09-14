package com.gideonsoftworks.gcore;

import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;

import javax.net.SocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Resolves the platform proxy (via {@link ProxySelector}, which honours the OS proxy settings because the
 * app launches with {@code -Djava.net.useSystemProxies=true}) and exposes it to the transfer libraries:
 *
 * <ul>
 *   <li>FTP / SFTP — a {@link SocketFactory} for {@code commons-net}/{@code sshj}: SOCKS proxies are used
 *       natively; HTTP proxies are tunnelled with an HTTP {@code CONNECT} handshake.</li>
 *   <li>S3 — {@code apacheClientFor}/{@code nettyClientFor} build proxied AWS HTTP clients.</li>
 * </ul>
 *
 * Authenticated proxies are supported via {@link #setCredentials}: HTTP CONNECT sends a preemptive Basic
 * {@code Proxy-Authorization}, S3 sets them on the {@code ProxyConfiguration}, and SOCKS uses the installed
 * {@link Authenticator}. Credentials also fall back to the {@code http.proxyUser}/{@code http.proxyPassword}
 * system properties.
 */
public final class GSystemProxy
{
    private GSystemProxy() {}

    private static volatile String credUser;
    private static volatile String credPass;

    /**
     * Set credentials for authenticated proxies. SOCKS and HTTP server-style challenges are answered by an
     * installed {@link Authenticator} (the JDK SOCKS client requests via scheme "SOCKS5", not RequestorType.PROXY,
     * so we match both); HTTP CONNECT and S3 read {@link #credUser()}/{@link #credPass()} directly. Pass null to clear.
     */
    public static void setCredentials( String user, String pass )
    {
        credUser = user;
        credPass = pass;
        Authenticator.setDefault( user == null ? null : new Authenticator() {
            @Override protected PasswordAuthentication getPasswordAuthentication()
            {
                boolean proxyAuth = getRequestorType() == RequestorType.PROXY
                    || ( getRequestingScheme() != null && getRequestingScheme().toUpperCase().startsWith( "SOCKS" ) );
                return proxyAuth && credUser != null
                    ? new PasswordAuthentication( credUser, ( credPass != null ? credPass : "" ).toCharArray() )
                    : null;
            }
        } );
    }

    private static String credUser() { return credUser != null ? credUser : System.getProperty( "http.proxyUser" ); }
    private static String credPass() { return credPass != null ? credPass : System.getProperty( "http.proxyPassword" ); }

    /** First non-DIRECT proxy the OS would use to reach host:port over {@code scheme}, or {@link Proxy#NO_PROXY}. */
    public static Proxy resolve( String scheme, String host, int port )
    {
        try {
            List<Proxy> proxies = ProxySelector.getDefault().select( new URI( scheme, null, host, port, null, null, null ) );
            for( Proxy p : proxies ) if( p.type() != Proxy.Type.DIRECT ) return p;
        } catch( Exception ignore ) {}
        return Proxy.NO_PROXY;
    }

    /** Proxy for a raw FTP/SFTP socket to host:port — tries the FTP/HTTPS proxy then SOCKS. */
    public static Proxy forSocket( String scheme, String host, int port )
    {
        Proxy p = resolve( scheme, host, port );
        if( p.type() != Proxy.Type.DIRECT ) return p;
        return resolve( "socket", host, port );   // SOCKS fallback
    }

    /** Short label for a resolved proxy, for audit logging. */
    public static String describe( Proxy p )
    {
        return switch( p.type() ) { case SOCKS -> "SOCKS"; case HTTP -> "HTTP-CONNECT"; default -> "DIRECT"; };
    }

    /** {@code host:port} of a resolved proxy, for audit logging. */
    public static String via( Proxy p )
    {
        return p.address() instanceof InetSocketAddress a ? a.getHostString() + ":" + a.getPort() : "";
    }

    /** HTTP proxy URI the OS would use for the given https service URL, or {@code null} (incl. SOCKS, which the
     *  AWS SDK proxy config doesn't take here — S3 over HTTP proxies is the supported case). */
    private static URI httpProxyFor( String httpsServiceUrl )
    {
        try {
            Proxy p = resolve( "https", new URI( httpsServiceUrl ).getHost(), 443 );
            if( p.type() == Proxy.Type.HTTP && p.address() instanceof InetSocketAddress a )
                return URI.create( "http://" + a.getHostString() + ":" + a.getPort() );
        } catch( Exception ignore ) {}
        return null;
    }

    /** {@code host:port} of the system HTTP proxy for an https service URL, or {@code null} when direct. For audit logging. */
    public static String httpViaFor( String httpsServiceUrl )
    {
        URI u = httpProxyFor( httpsServiceUrl );
        return u != null ? u.getHost() + ":" + u.getPort() : null;
    }

    /** Apache (sync) S3 HTTP client builder routed through the system proxy, or {@code null} for a direct connection. */
    public static ApacheHttpClient.Builder apacheClientFor( String serviceUrl )
    {
        URI proxy = httpProxyFor( serviceUrl );
        if( proxy == null ) return null;
        ProxyConfiguration.Builder cfg = ProxyConfiguration.builder().endpoint( proxy );
        if( credUser() != null ) cfg.username( credUser() ).password( credPass() );
        return ApacheHttpClient.builder().proxyConfiguration( cfg.build() );
    }

    /** Netty (async) S3 HTTP client builder routed through the system proxy, or {@code null} for a direct connection. */
    public static NettyNioAsyncHttpClient.Builder nettyClientFor( String serviceUrl )
    {
        URI proxy = httpProxyFor( serviceUrl );
        if( proxy == null ) return null;
        // FQN: netty's ProxyConfiguration class name clashes with the apache one imported above.
        var cfg = software.amazon.awssdk.http.nio.netty.ProxyConfiguration.builder()
            .scheme( proxy.getScheme() ).host( proxy.getHost() ).port( proxy.getPort() );
        if( credUser() != null ) cfg.username( credUser() ).password( credPass() );
        return NettyNioAsyncHttpClient.builder().proxyConfiguration( cfg.build() );
    }

    /**
     * A SocketFactory that routes new sockets through {@code proxy}. {@code commons-net}/{@code sshj} call the
     * no-arg {@code createSocket()} and then {@code socket.connect(target)} themselves, so SOCKS works via the
     * proxy-bound Socket and HTTP is tunnelled by {@link HttpConnectSocket} when {@code connect()} is invoked.
     */
    public static SocketFactory socketFactory( final Proxy proxy )
    {
        return new SocketFactory() {
            @Override public Socket createSocket() {
                if( proxy.type() == Proxy.Type.SOCKS ) return new Socket( proxy );
                if( proxy.type() == Proxy.Type.HTTP )  return new HttpConnectSocket( (InetSocketAddress) proxy.address() );
                return new Socket();
            }
            @Override public Socket createSocket( String h, int p ) throws IOException { Socket s = createSocket(); s.connect( new InetSocketAddress( h, p ) ); return s; }
            @Override public Socket createSocket( InetAddress h, int p ) throws IOException { Socket s = createSocket(); s.connect( new InetSocketAddress( h, p ) ); return s; }
            @Override public Socket createSocket( String h, int p, InetAddress lh, int lp ) throws IOException { return createSocket( h, p ); }
            @Override public Socket createSocket( InetAddress h, int p, InetAddress lh, int lp ) throws IOException { return createSocket( h, p ); }
        };
    }

    /**
     * A SocketFactory for the manual SOCKS5 proxy (Preferences → SOCKS5), with optional RFC 1929 user/pass
     * auth. Unlike {@link #socketFactory}, this drives the SOCKS5 handshake ourselves so authentication is
     * reliable (the JDK's {@code Socket(socksProxy)} + Authenticator path is not).
     */
    public static SocketFactory socks5Factory( String proxyHost, int proxyPort, String user, String pass )
    {
        final InetSocketAddress addr = new InetSocketAddress( proxyHost, proxyPort );
        return new SocketFactory() {
            @Override public Socket createSocket() { return new Socks5Socket( addr, user, pass ); }
            @Override public Socket createSocket( String h, int p ) throws IOException { Socket s = createSocket(); s.connect( new InetSocketAddress( h, p ) ); return s; }
            @Override public Socket createSocket( InetAddress h, int p ) throws IOException { Socket s = createSocket(); s.connect( new InetSocketAddress( h, p ) ); return s; }
            @Override public Socket createSocket( String h, int p, InetAddress lh, int lp ) throws IOException { return createSocket( h, p ); }
            @Override public Socket createSocket( InetAddress h, int p, InetAddress lh, int lp ) throws IOException { return createSocket( h, p ); }
        };
    }

    /** A Socket that, on connect(target), performs a SOCKS5 handshake (with optional user/pass) to the proxy, then a CONNECT to the target. */
    private static final class Socks5Socket extends Socket
    {
        private final InetSocketAddress proxyAddr;
        private final String user;
        private final String pass;
        Socks5Socket( InetSocketAddress proxyAddr, String user, String pass ) { this.proxyAddr = proxyAddr; this.user = user; this.pass = pass; }

        @Override public void connect( SocketAddress endpoint, int timeout ) throws IOException
        {
            InetSocketAddress target = (InetSocketAddress) endpoint;
            super.connect( proxyAddr, timeout );
            OutputStream out = getOutputStream();
            InputStream  in  = getInputStream();
            boolean auth = user != null && !user.isEmpty();

            // greeting: offer no-auth, plus user/pass when we have credentials
            out.write( auth ? new byte[]{ 0x05, 0x02, 0x00, 0x02 } : new byte[]{ 0x05, 0x01, 0x00 } );
            out.flush();
            if( read( in ) != 0x05 ) throw new IOException( "SOCKS5: bad version from proxy" );
            int method = read( in );
            if( method == 0x02 )
            {
                if( !auth ) throw new IOException( "SOCKS5: proxy requires authentication but none configured" );
                byte[] u = user.getBytes( StandardCharsets.UTF_8 );
                byte[] p = ( pass != null ? pass : "" ).getBytes( StandardCharsets.UTF_8 );
                ByteArrayOutputStream a = new ByteArrayOutputStream();
                a.write( 0x01 ); a.write( u.length ); a.write( u, 0, u.length ); a.write( p.length ); a.write( p, 0, p.length );
                out.write( a.toByteArray() ); out.flush();
                read( in );                                   // auth version
                if( read( in ) != 0x00 ) throw new IOException( "SOCKS5: authentication failed" );
            }
            else if( method != 0x00 )
            {
                throw new IOException( "SOCKS5: no acceptable authentication method (" + method + ")" );
            }

            // CONNECT request with the target as a domain name (proxy resolves it)
            byte[] h = target.getHostString().getBytes( StandardCharsets.US_ASCII );
            ByteArrayOutputStream req = new ByteArrayOutputStream();
            req.write( 0x05 ); req.write( 0x01 ); req.write( 0x00 ); req.write( 0x03 );
            req.write( h.length ); req.write( h, 0, h.length );
            req.write( ( target.getPort() >> 8 ) & 0xFF ); req.write( target.getPort() & 0xFF );
            out.write( req.toByteArray() ); out.flush();

            if( read( in ) != 0x05 ) throw new IOException( "SOCKS5: bad reply version" );
            int rep = read( in );
            if( rep != 0x00 ) throw new IOException( "SOCKS5: connect refused (code " + rep + ")" );
            read( in );                                       // reserved
            int atyp = read( in );
            int addrLen = switch( atyp ) { case 0x01 -> 4; case 0x04 -> 16; case 0x03 -> read( in ); default -> 0; };
            for( int i = 0; i < addrLen + 2; i++ ) read( in );   // bound addr + port (ignored)
        }
        @Override public void connect( SocketAddress endpoint ) throws IOException { connect( endpoint, 0 ); }

        private static int read( InputStream in ) throws IOException
        {
            int b = in.read();
            if( b < 0 ) throw new EOFException( "SOCKS5: unexpected end of stream" );
            return b;
        }
    }

    /** A Socket that, on connect(target), opens an HTTP CONNECT tunnel to the target through an HTTP proxy. */
    private static final class HttpConnectSocket extends Socket
    {
        private final InetSocketAddress proxyAddr;
        HttpConnectSocket( InetSocketAddress proxyAddr ) { this.proxyAddr = proxyAddr; }

        @Override public void connect( SocketAddress endpoint, int timeout ) throws IOException
        {
            InetSocketAddress target = (InetSocketAddress) endpoint;
            super.connect( proxyAddr, timeout );   // TCP to the proxy itself

            String hostPort = target.getHostString() + ":" + target.getPort();
            String connect = "CONNECT " + hostPort + " HTTP/1.1\r\nHost: " + hostPort + "\r\n" + proxyAuthHeader() + "\r\n";
            OutputStream out = getOutputStream();
            out.write( connect.getBytes( StandardCharsets.US_ASCII ) );
            out.flush();

            String statusLine = readLine( getInputStream() );
            boolean ok = statusLine != null && statusLine.contains( " 200" );
            GAuditLog.proxy( target.getHostString(), "HTTP-CONNECT",
                proxyAddr.getHostString() + ":" + proxyAddr.getPort(), hostPort, ok ? "ok" : "failed: " + statusLine );
            if( !ok )
                throw new IOException( "Proxy CONNECT to " + hostPort + " failed: " + statusLine );
            while( true ) { String l = readLine( getInputStream() ); if( l == null || l.isEmpty() ) break; }   // drain headers
        }
        @Override public void connect( SocketAddress endpoint ) throws IOException { connect( endpoint, 0 ); }

        /** Preemptive Basic {@code Proxy-Authorization} header line (with trailing CRLF), or "" when no credentials. */
        private static String proxyAuthHeader()
        {
            String u = credUser();
            if( u == null ) return "";
            String token = Base64.getEncoder().encodeToString( ( u + ":" + ( credPass() != null ? credPass() : "" ) ).getBytes( StandardCharsets.UTF_8 ) );
            return "Proxy-Authorization: Basic " + token + "\r\n";
        }

        private static String readLine( InputStream in ) throws IOException
        {
            StringBuilder sb = new StringBuilder();
            int c, prev = -1;
            while( ( c = in.read() ) != -1 ) {
                if( c == '\n' ) { if( prev == '\r' ) sb.setLength( sb.length() - 1 ); return sb.toString(); }
                sb.append( (char) c ); prev = c;
            }
            return sb.length() == 0 ? null : sb.toString();
        }
    }
}
