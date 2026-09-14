package com.gideonsoftworks.gcore;

import com.gideonsoftworks.gideon.preferences.GSFTPPreferences;

/**
 * The app-wide manual proxy configuration. Two independent proxies:
 * <ul>
 *   <li><b>FTP firewall</b> (Preferences → FTP) — a {@link GFtpFirewallType} (Types 1–10, data-driven), for FTP only.</li>
 *   <li><b>SOCKS5</b> (Preferences → SOCKS5) — transport proxy, for FTP + SFTP.</li>
 * </ul>
 * HTTP proxying is handled by the system proxy (used for S3), not configured here.
 * Non-secret fields persist in
 * {@link GSFTPPreferences}; passwords live in the keyring (via {@link GKeyringManager}). A {@code hasXxxPassword}
 * flag (in prefs) lets the UI know a secret exists <i>without reading the keychain</i>; the keychain is read
 * only at connect time and written only when the user changes a password. S3 ignores all of this (system proxy).
 */
public final class GProxyConfig
{
    private GProxyConfig() {}

    private static final String FTP_PW_KEY   = "proxy:password";
    private static final String SOCKS_PW_KEY = "socks:password";

    // --- FTP firewall proxy ---

    public static GFtpFirewallType getType()              { return GFtpFirewallTypes.byId( GSFTPPreferences.getProxyType() ); }
    public static void       setType( GFtpFirewallType t ){ GSFTPPreferences.setProxyType( ( t != null ? t : GFtpFirewallType.NONE ).id() ); }
    public static String getHost()           { return GSFTPPreferences.getProxyHost(); }
    public static void   setHost( String h ) { GSFTPPreferences.setProxyHost( h ); }
    public static int    getPort()           { return GSFTPPreferences.getProxyPort(); }
    public static void   setPort( int p )    { GSFTPPreferences.setProxyPort( p ); }
    public static String getUser()           { return GSFTPPreferences.getProxyUser(); }
    public static void   setUser( String u ) { GSFTPPreferences.setProxyUser( u ); }

    public static boolean hasPassword()            { return GSFTPPreferences.proxyHasPassword(); }
    public static String  getPassword()            { return GKeyringManager.getPassword( FTP_PW_KEY ); }
    public static void    setPassword( String pw ) { writeSecret( FTP_PW_KEY, pw, GSFTPPreferences::setProxyHasPassword ); }

    /** True when an FTP firewall proxy is configured (type != None). */
    public static boolean isEnabled() { return !getType().isNone(); }

    // --- SOCKS5 proxy (used by FTP + SFTP). HTTP proxying is handled by the system proxy, not here. ---

    public static boolean isSocksEnabled()            { return GSFTPPreferences.socksEnabled(); }
    public static void    setSocksEnabled( boolean b ){ GSFTPPreferences.setSocksEnabled( b ); }
    public static String getSocksHost()           { return GSFTPPreferences.getSocksHost(); }
    public static void   setSocksHost( String h ) { GSFTPPreferences.setSocksHost( h ); }
    public static int    getSocksPort()           { return GSFTPPreferences.getSocksPort(); }
    public static void   setSocksPort( int p )    { GSFTPPreferences.setSocksPort( p ); }
    public static String getSocksUser()           { return GSFTPPreferences.getSocksUser(); }
    public static void   setSocksUser( String u ) { GSFTPPreferences.setSocksUser( u ); }

    public static boolean hasSocksPassword()            { return GSFTPPreferences.socksHasPassword(); }
    public static String  getSocksPassword()            { return GKeyringManager.getPassword( SOCKS_PW_KEY ); }
    public static void    setSocksPassword( String pw ) { writeSecret( SOCKS_PW_KEY, pw, GSFTPPreferences::setSocksHasPassword ); }

    /** Write (or clear) a keyring secret and keep its has-password flag in sync. */
    private static void writeSecret( String key, String pw, java.util.function.Consumer<Boolean> flag )
    {
        GKeyringManager.setPassword( key, pw );
        flag.accept( pw != null && !pw.isEmpty() );
    }
}
