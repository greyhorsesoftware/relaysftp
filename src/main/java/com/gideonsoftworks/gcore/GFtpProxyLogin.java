package com.gideonsoftworks.gcore;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.IOException;

/**
 * Runs an FTP firewall login by executing the {@link GFtpFirewallType}'s data-driven {@code steps}: each step
 * is a raw FTP command template (from {@code ftp_firewall_types.json}) whose tokens are substituted and then
 * sent on the control connection (already connected to the firewall). Returns true once finally logged in (2xx).
 *
 * <p>Tokens: {@code %host %port} (the destination from the bookmark), {@code %user %pass} (the account creds),
 * {@code %fwuser %fwpass} (the firewall creds from {@link GProxyConfig}).
 */
final class GFtpProxyLogin
{
    private GFtpProxyLogin() {}

    static boolean run( FTPClient c, GFtpFirewallType type, String realHost, int realPort,
                        String user, String pass, String fwUser, String fwPass ) throws IOException
    {
        for( String step : type.steps() )
            c.sendCommand( subst( step, realHost, realPort, user, pass, fwUser, fwPass ) );
        return FTPReply.isPositiveCompletion( c.getReplyCode() );
    }

    static String subst( String template, String host, int port, String user, String pass, String fwUser, String fwPass )
    {
        return template
            .replace( "%fwuser", nz( fwUser ) )
            .replace( "%fwpass", nz( fwPass ) )
            .replace( "%host", nz( host ) )
            .replace( "%port", String.valueOf( port ) )
            .replace( "%user", nz( user ) )
            .replace( "%pass", nz( pass ) );
    }

    private static String nz( String s ) { return s != null ? s : ""; }
}
