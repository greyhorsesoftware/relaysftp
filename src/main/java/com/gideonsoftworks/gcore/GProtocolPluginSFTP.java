//
//  GProtocolPluginSFTP.java
//  Gideon
//
//  Created by David on 10/29/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//
//  Rewritten 2024: replaced abandoned j2ssh library with sshj (com.hierynomus:sshj)
//

package com.gideonsoftworks.gcore;

import java.net.Proxy;

import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.core.GideonConfiguration;
import com.gideonsoftworks.gideon.preferences.GSFTPPreferences;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.userauth.UserAuthException;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import net.schmizz.sshj.xfer.InMemoryDestFile;
import net.schmizz.sshj.xfer.InMemorySourceFile;
import net.schmizz.sshj.connection.channel.direct.Session;

import java.io.*;
import java.util.Date;
import java.util.List;

class GProtocolPluginSFTP extends GProtocolPlugin
{

private SSHClient   sshClient       = null;
private SFTPClient  sftpClient      = null;
// transfer channel: used by the job worker inside put/get while cancel closes it
// from another thread (that close IS the abort mechanism) - volatile + transferLock
// keep create/close/reassign atomic without serializing the transfer itself
private volatile SFTPClient  transSftpClient = null;
private volatile GTransferProgressListener dataListener = null;
private final Object transferLock = new Object();
private String      currentDir      = "/";

public GProtocolPluginSFTP( final GConnection c ) throws GConnectionException
{
    super(c);
    setIsSecure(true);
}

public GFileItem gpp_exists( String fPath, String fName, boolean isDir ) throws GConnectionException
{
    try {
        FileAttributes attrs = sftpClient.stat(fPath);
        Date mDate  = new Date(attrs.getMtime() * 1000L);
        long fSize  = attrs.getSize();

        if( GideonConfiguration.TESTFORFILEDEBUG )
        {
            System.out.println("sftp->gpp_exists()");
            System.out.println("\tFile Size : " + fSize);
            System.out.println("\tFile Mod Date : " + mDate);
        }

        GFileItem nFI = new GFileItem(null, fName, fSize, mDate);
        if( isDir )
        {
            nFI.setFileType(GFileItem.G_FOLDER);
        } else {
            nFI.setFileType(GFileItem.G_FILE);
        }
        return nFI;
    } catch( IOException e ) {
        return null;
    }
}

public void gpp_connect( GBookmark b ) throws GConnectionException
{
    if( b == null )
    {
        throw new NullPointerException();
    }

    sshClient = new SSHClient();
    // #1.1: verify the server's host key against known_hosts (trust-on-first-use),
    // instead of the old PromiscuousVerifier that accepted any key (MITM-able).
    try {
        sshClient.addHostKeyVerifier(GKnownHostsVerifier.create());
    } catch( IOException e ) {
        throw new GConnectionException("Could not initialize host key verification: " + e.getMessage(),
            GConnectionException.GCE_PROTOCOL_ERROR);
    }

    int port = b.getPort();

    // Route through a proxy: the manual SOCKS5 config takes precedence; otherwise fall back to the system
    // proxy (which covers an OS HTTP CONNECT proxy). S3 is unaffected (it uses the system proxy directly).
    String  target     = b.getHostname() + ":" + ( port != 0 ? port : 22 );
    String  proxyLabel = null;          // non-null once a proxy is in use (drives the failure audit below)
    String  proxyVia   = null;
    if( GProxyConfig.isSocksEnabled() )
    {
        proxyLabel = "SOCKS5";
        proxyVia   = GProxyConfig.getSocksHost() + ":" + GProxyConfig.getSocksPort();
        sshClient.setSocketFactory( GSystemProxy.socks5Factory(
            GProxyConfig.getSocksHost(), GProxyConfig.getSocksPort(), GProxyConfig.getSocksUser(), GProxyConfig.getSocksPassword() ) );
        GAuditLog.proxy( b.getHostname(), proxyLabel, proxyVia, target, "configured" );
    }
    else
    {
        Proxy sshProxy = GSystemProxy.forSocket( "https", b.getHostname(), port != 0 ? port : 22 );
        if( sshProxy.type() != Proxy.Type.DIRECT )
        {
            proxyLabel = GSystemProxy.describe( sshProxy );
            proxyVia   = GSystemProxy.via( sshProxy );
            sshClient.setSocketFactory( GSystemProxy.socketFactory( sshProxy ) );
            GAuditLog.proxy( b.getHostname(), proxyLabel, proxyVia, target, "configured" );
        }
    }

    boolean ok = false;
    try {
        try {
            if( port != 0 )
            {
                this.getConnection().getLog().logMessage(
                    new GLogMessage(GLogMessage.GLOG_INFORMATIONAL, GLocalized.USING_PORT + port));
                sshClient.connect(b.getHostname(), port);
            } else {
                sshClient.connect(b.getHostname());
            }
        } catch( IOException ce ) {
            // SOCKS transport failures surface here (HTTP CONNECT self-reports the proxy's status at the source).
            if( proxyLabel != null && !"HTTP-CONNECT".equals( proxyLabel ) )
                GAuditLog.proxy( b.getHostname(), proxyLabel, proxyVia, target, "failed: " + ce.getMessage() );
            throw ce;
        }

        // Enable SSH keepalive after connection is established
        sshClient.getConnection().getKeepAlive().setKeepAliveInterval(30);

        if( sshClient.isConnected() )
        {
            String banner = sshClient.getUserAuth().getBanner();
            if( banner != null && banner.length() > 0 )
            {
                this.getConnection().getLog().logMessage(
                    new GLogMessage(GLogMessage.GLOG_INFORMATIONAL, banner));
            }
        }

        // Prefer public-key auth if a key file is found; fall back to password
        String bmPassword = GBookmarkManager.getPassword(b);
        KeyProvider keys = getPrivateKeyProvider(bmPassword);
        boolean authenticated = false;
        if( keys != null )
        {
            this.logMessage(GLogMessage.GLOG_INFORMATIONAL, GLocalized.PK_AUTH + " Authentication.");
            try {
                sshClient.authPublickey(b.getUsername(), keys);
                authenticated = sshClient.isAuthenticated();
            } catch( UserAuthException e ) {
                this.logMessage(GLogMessage.GLOG_INFORMATIONAL, "Public key auth failed, trying password.");
            }
        }
        if( !authenticated )
        {
            this.logMessage(GLogMessage.GLOG_INFORMATIONAL, GLocalized.TRYING_PW_AUTH);
            String pw = bmPassword;
            if( pw == null ) pw = "";
            try {
                sshClient.authPassword(b.getUsername(), pw);
            } catch( UserAuthException e ) {
                throw new GConnectionException("Authentication failed", GConnectionException.GCE_BAD_LOGIN);
            }
        }

        if( !sshClient.isAuthenticated() )
        {
            throw new GConnectionException("Authentication failed", GConnectionException.GCE_BAD_LOGIN);
        }

        setIsConnected(true);
        sftpClient = sshClient.newSFTPClient();
        currentDir = sftpClient.canonicalize(".");
        ok = true;

        this.logMessage(GLogMessage.GLOG_INFORMATIONAL, "Authentication Complete. Logged in.");

    } catch( IOException e ) {
        throw new GConnectionException("IO Exception while connecting: " + e.getMessage(),
            GConnectionException.GCE_PROTOCOL_ERROR);
    } finally {
        // #3: any failure (auth, SFTP-client creation, IO) must not leak the open SSH socket + transport thread.
        if( !ok ) closeSshQuietly();
    }
}

/** Disconnect the SSH/SFTP client without throwing (used on connect failure). */
private void closeSshQuietly()
{
    try { if( sftpClient != null ) sftpClient.close(); } catch( Exception ignore ) {}
    try { if( sshClient  != null ) sshClient.disconnect(); } catch( Exception ignore ) {}
    sftpClient = null;
    sshClient  = null;
    setIsConnected(false);
}

@Override
public void gpp_probeCapabilities() {
    GConnectionStats stats = getConnection().getStats();

    // Warp-list (fast recursive listing) rides on the SSH exec channel, so it is only
    // probed when the Use-Exec preference is on. mtime below needs no exec.
    if (GSFTPPreferences.useExec()) {
        // Test warp-list: try actual find command on current directory
        try {
            Session session = sshClient.startSession();
            try {
                String escapedPath = currentDir.replace("'", "'\\''");
                String output;
                int exitCode;
                try (var cmd = session.exec("find '" + escapedPath + "' -maxdepth 0 -exec ls -ld '{}' +")) {
                    output = new String(cmd.getInputStream().readAllBytes()).trim();
                    cmd.join(5, java.util.concurrent.TimeUnit.SECONDS);
                    exitCode = cmd.getExitStatus() != null ? cmd.getExitStatus() : -1;
                }

                if (exitCode == 0 && !output.isEmpty()) {
                    stats.setWarpListSupported(true);
                    logMessage(GLogMessage.GLOG_INFORMATIONAL, "Warp List: supported");
                } else {
                    stats.setWarpListSupported(false);
                    logMessage(GLogMessage.GLOG_INFORMATIONAL, "Warp List: not supported (exit " + exitCode + ")");
                }
            } finally {
                session.close();
            }
        } catch (Exception e) {
            stats.setWarpListSupported(false);
            logMessage(GLogMessage.GLOG_INFORMATIONAL, "Warp List: not available (" + e.getMessage() + ")");
        }
    }

    // Test mtime (native SFTP stat, no exec needed) on current directory
    try {
        long mtime = gpp_getRemoteMtime(currentDir);
        if (mtime != -1) {
            stats.setMtimeSupported(true);
            logMessage(GLogMessage.GLOG_INFORMATIONAL, "Server supports mtime checking");
        } else {
            stats.setMtimeSupported(false);
            logMessage(GLogMessage.GLOG_INFORMATIONAL, "Server does not support mtime checking");
        }
    } catch (Exception e) {
        stats.setMtimeSupported(false);
        logMessage(GLogMessage.GLOG_INFORMATIONAL, "Mtime checking not available: " + e.getMessage());
    }
}

private KeyProvider getPrivateKeyProvider( String passphrase )
{
    String[] keyNames = { "id_ed25519", "id_rsa", "id_ecdsa", "id_dsa", "identity" };
    String sshDir = System.getProperty("user.home") + File.separator + ".ssh" + File.separator;

    for( String name : keyNames )
    {
        File keyFile = new File(sshDir + name);
        if( keyFile.exists() )
        {
            this.logMessage(GLogMessage.GLOG_INFORMATIONAL,
                GLocalized.USING_SSH_KEY + keyFile.getAbsolutePath());
            try {
                if( passphrase != null && !passphrase.isEmpty() )
                {
                    return sshClient.loadKeys(keyFile.getAbsolutePath(), passphrase.toCharArray());
                } else {
                    return sshClient.loadKeys(keyFile.getAbsolutePath());
                }
            } catch( IOException e ) {
                return null;
            }
        }
    }
    return null;
}

public void gpp_disconnect( GConnection c ) throws GConnectionException
{
    if( c == null )
    {
        throw new NullPointerException();
    }
    logMessage(GLogMessage.GLOG_CLIENT_COMMAND, "DISCONNECT");
    // #9: guard each close independently so one failure can't skip the SSH socket disconnect.
    synchronized( transferLock )
    {
        try { if( transSftpClient != null ) transSftpClient.close(); } catch( Exception ignore ) {}
        transSftpClient = null;
    }
    try { if( sftpClient      != null ) sftpClient.close();      } catch( Exception ignore ) {}
    try { if( sshClient       != null ) sshClient.disconnect();  } catch( Exception ignore ) {}
    sftpClient = null;
    sshClient = null;
    this.setIsConnected(false);
}

public GListParser gpp_parser_instance() throws GConnectionException
{
    return new GListParserSFTP();
}

public Object gpp_list( String p ) throws GConnectionException
{
    logMessage(GLogMessage.GLOG_CLIENT_COMMAND, "LIST " + p);
    try {
        List<RemoteResourceInfo> listing = sftpClient.ls(p);
        logMessage(GLogMessage.GLOG_SERVER_REPLY, listing.size() + " entries");
        return listing.toArray();
    } catch( IOException e ) {
        throw new GConnectionException("IO Exception while listing " + p,
            GConnectionException.GCE_PROTOCOL_ERROR);
    }
}

public String gpp_get_pwd() throws GConnectionException
{
    return currentDir;
}

@Override
public boolean gpp_supportsFastList()
{
    return true;
}

/**
 * Uses SSH exec to run 'find' + 'ls -ld' to get the entire directory tree
 * in a single round-trip. Falls back gracefully if exec is not available.
 */
@Override
public Object gpp_fastListRecursive(String path) throws GConnectionException
{
    logMessage(GLogMessage.GLOG_INFORMATIONAL, "WARP-LIST " + path + " (via SSH exec)");
    long startTime = System.currentTimeMillis();

    try {
        Session session = sshClient.startSession();
        try {
            // Use find + xargs ls -ld for portable output that GListParserUNIX can parse
            // Escape single quotes in path
            String escapedPath = path.replace("'", "'\\''");
            String cmd = "find '" + escapedPath + "' -exec ls -ld '{}' +";
            Session.Command command = session.exec(cmd);
            java.util.ArrayList<String> lines = new java.util.ArrayList<>();
            int exitCode;

            // Read stdout. try-with-resources closes the reader, then the command, on every path
            // (#10: previously only the session was closed, in the outer finally).
            try (command;
                 BufferedReader reader = new BufferedReader(new InputStreamReader(command.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
                command.join(30, java.util.concurrent.TimeUnit.SECONDS);
                exitCode = command.getExitStatus() != null ? command.getExitStatus() : -1;
            }

            long elapsed = System.currentTimeMillis() - startTime;
            logMessage(GLogMessage.GLOG_INFORMATIONAL,
                "WARP-LIST completed: " + lines.size() + " entries in " + elapsed + "ms (exit " + exitCode + ")");

            if (exitCode != 0 || lines.isEmpty()) {
                logMessage(GLogMessage.GLOG_INFORMATIONAL, "WARP-LIST failed or empty — will fall back to SFTP LIST");
                getConnection().getStats().setWarpListSupported(false);
                return null;
            }

            getConnection().getStats().setWarpListSupported(true);
            return lines.toArray(new String[0]);

        } finally {
            session.close();
        }
    } catch (Exception e) {
        long elapsed = System.currentTimeMillis() - startTime;
        logMessage(GLogMessage.GLOG_INFORMATIONAL,
            "WARP-LIST not available (" + e.getMessage() + ", " + elapsed + "ms) — will fall back to SFTP LIST");
        return null;
    }
}

private boolean mtimeCheckFailed = false;

@Override
public long gpp_getRemoteMtime(String path) {
    if (mtimeCheckFailed || sftpClient == null || sshClient == null || !sshClient.isConnected()) return -1;

    GConnectionStats stats = getConnection().getStats();

    try {
        // Native SFTP stat carries the mtime (seconds) — no shell/exec needed, so this
        // works on SFTP-only servers too. Directories report their own mtime, which bumps
        // when an entry is added/removed, which is exactly what the change-poll wants.
        FileAttributes attrs = sftpClient.stat(path);
        long mtime = attrs.getMtime();
        stats.setLastMtimeResponse(String.valueOf(mtime));
        stats.setMtimeSupported(true);
        return mtime;
    } catch (Exception e) {
        mtimeCheckFailed = true;
        stats.setMtimeSupported(false);
        stats.setLastMtimeResponse("Error: " + e.getMessage());
        return -1;
    }
}

@Override
public boolean gpp_fastDelete(String path) throws GConnectionException {
    if (sshClient == null || !sshClient.isConnected()) return false;

    try {
        Session session = sshClient.startSession();
        try {
            String escapedPath = path.replace("'", "'\\''");
            String cmd = "rm -rf '" + escapedPath + "'";
            logMessage(GLogMessage.GLOG_CLIENT_COMMAND, "EXEC " + cmd);
            Session.Command command = session.exec(cmd);
            // Drain stdout/stderr to prevent channel stall
            command.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            command.getErrorStream().transferTo(java.io.OutputStream.nullOutputStream());
            command.join(60, java.util.concurrent.TimeUnit.SECONDS);
            command.close();
            int exitCode = command.getExitStatus() != null ? command.getExitStatus() : -1;

            if (exitCode == 0) {
                logMessage(GLogMessage.GLOG_SERVER_REPLY, "FAST-DELETE OK");
                return true;
            } else {
                logMessage(GLogMessage.GLOG_SERVER_REPLY, "FAST-DELETE failed (exit " + exitCode + ")");
                return false;
            }
        } finally {
            session.close();
        }
    } catch (Exception e) {
        logMessage(GLogMessage.GLOG_SERVER_REPLY, "FAST-DELETE unavailable: " + e.getMessage());
        return false;
    }
}

@Override
public void gpp_ping() throws GConnectionException
{
    try {
        sftpClient.canonicalize(".");
    } catch( Exception e ) {
        throw new GConnectionException("Connection test failed: " + e.getMessage(),
            GConnectionException.GCE_PROTOCOL_ERROR);
    }
}

public void gpp_cwd( String p ) throws GConnectionException
{
    logMessage(GLogMessage.GLOG_CLIENT_COMMAND, "CWD " + p);
    try {
        String target = p.startsWith("/") ? p : currentDir + "/" + p;
        currentDir = sftpClient.canonicalize(target);
        logMessage(GLogMessage.GLOG_SERVER_REPLY, "CWD OK " + currentDir);
    } catch( IOException e ) {
        throw new GConnectionException("IO Exception while changing working directory to " + p,
            GConnectionException.GCE_BAD_FOLDER);
    }
}

@Override
public boolean gpp_needsCompletePathForRename() {
    return true;
}

public void gpp_rename( String p, String n ) throws GConnectionException
{
    logMessage(GLogMessage.GLOG_CLIENT_COMMAND, "RENAME " + p + " -> " + n);
    try {
        sftpClient.rename(p, n);
        logMessage(GLogMessage.GLOG_SERVER_REPLY, "RENAME OK");
    } catch( IOException e ) {
        throw new GConnectionException("IO Exception while renaming " + p + " to " + n,
            GConnectionException.GCE_PROTOCOL_ERROR);
    }
}

public void gpp_move( String p, String n ) throws GConnectionException
{
    this.gpp_rename(p, n);
}

public void gpp_rm( String p, boolean isDir ) throws GConnectionException
{
    logMessage(GLogMessage.GLOG_CLIENT_COMMAND, (isDir ? "RMDIR " : "RM ") + p);
    try {
        if( isDir )
        {
            sftpClient.rmdir(p);
        } else {
            sftpClient.rm(p);
        }
        logMessage(GLogMessage.GLOG_SERVER_REPLY, "DELETE OK");
    } catch( IOException e ) {
        throw new GConnectionException("IO Exception while removing " + p,
            GConnectionException.GCE_PROTOCOL_ERROR);
    }
}

public void gpp_chmod( final String p, final GPermissions pmO ) throws GConnectionException
{
    logMessage(GLogMessage.GLOG_CLIENT_COMMAND, "CHMOD " + pmO.getNumericValueAsString() + " " + p);
    try {
        // GPermissions.getNumericValue() returns decimal digits representing chmod octal (e.g. 755).
        // Convert to actual Unix permissions mask (e.g. 755 decimal-digits -> 0755 octal = 493).
        int unixPerms = Integer.parseInt(String.valueOf(pmO.getNumericValue()), 8);
        sftpClient.chmod(p, unixPerms);
        logMessage(GLogMessage.GLOG_SERVER_REPLY, "CHMOD OK");
    } catch( IOException e ) {
        throw new GConnectionException("IO Exception while changing permissions of " + p,
            GConnectionException.GCE_PROTOCOL_ERROR);
    }
}

@Override
public void gpp_setModifiedTime( String path, long epochMillis ) throws GConnectionException
{
    int secs = (int) ( epochMillis / 1000L );   // SFTP mtime is epoch seconds
    logMessage(GLogMessage.GLOG_CLIENT_COMMAND, "SETSTAT mtime=" + secs + " " + path);
    try {
        FileAttributes attrs = new FileAttributes.Builder().withAtimeMtime(secs, secs).build();
        sftpClient.setattr(path, attrs);
        logMessage(GLogMessage.GLOG_SERVER_REPLY, "SETSTAT OK");
    } catch( IOException e ) {
        throw new GConnectionException("IO Exception while setting mtime of " + path,
            GConnectionException.GCE_PROTOCOL_ERROR);
    }
}

public void gpp_mkdir( String dP ) throws GConnectionException
{
    logMessage(GLogMessage.GLOG_CLIENT_COMMAND, "MKDIR " + dP);
    try {
        sftpClient.mkdir(dP);
        logMessage(GLogMessage.GLOG_SERVER_REPLY, "MKDIR OK");
    } catch( IOException e ) {
        throw new GConnectionException("IO Exception while creating directory " + dP,
            GConnectionException.GCE_PROTOCOL_ERROR);
    }
}

public String gpp_send_command( String c ) throws GConnectionException
{
    return null;
}

/**
*
*   Transfer API
*
*/

public void gpp_prepareToTransferFile( GFileItem f, int flag ) throws GConnectionException
{
    try {
        synchronized( transferLock )
        {
            // Reuse existing transfer client if still open
            if( transSftpClient != null )
            {
                return;
            }
            transSftpClient = sshClient.newSFTPClient();
        }
    } catch( IOException e ) {
        throw new GConnectionException("IO Exception while preparing transfer: " + e.getMessage(),
            GConnectionException.GCE_PROTOCOL_ERROR);
    }
}

public void gpp_upload( final String c, FileInputStream srcStream, File localFile, GTransferProgressListener l ) throws GConnectionException
{
    logMessage(GLogMessage.GLOG_CLIENT_COMMAND, "PUT " + c);
    dataListener = l;
    final long[] totalSent = { 0 };

    // read the volatile once - a concurrent cancel can null the field between a
    // check and a use; the captured ref stays valid and close() aborts the put
    SFTPClient t = transSftpClient;
    if( t == null )
    {
        throw new GConnectionException("Transfer cancelled",
            GConnectionException.GCE_PROTOCOL_ERROR);
    }

    try {
        t.put(new InMemorySourceFile() {
            public String getName() { return c; }
            public long getLength() { return -1; }
            public InputStream getInputStream() throws IOException
            {
                return new FilterInputStream(srcStream) {
                    public int read( byte[] b, int off, int len ) throws IOException
                    {
                        int n = super.read(b, off, len);
                        if( n > 0 )
                        {
                            totalSent[0] += n;
                            if( dataListener != null ) dataListener.updateTransferStats(totalSent[0]);
                        }
                        return n;
                    }
                };
            }
        }, c);
        logMessage(GLogMessage.GLOG_SERVER_REPLY, "PUT OK " + totalSent[0] + " bytes");
    } catch( IOException e ) {
        // keep the server's reason (Permission denied, No such file, …): the mirror skip row shows it
        throw new GConnectionException("IO Exception while uploading to " + c + " : " + e.getMessage(),
            GConnectionException.GCE_PROTOCOL_ERROR);
    }
}

public void gpp_download( final String c, FileOutputStream dstStream, File localFile, GTransferProgressListener l ) throws GConnectionException
{
    logMessage(GLogMessage.GLOG_CLIENT_COMMAND, "GET " + c);
    dataListener = l;
    final long[] totalReceived = { 0 };

    // read the volatile once - see gpp_upload
    SFTPClient t = transSftpClient;
    if( t == null )
    {
        throw new GConnectionException("Transfer cancelled",
            GConnectionException.GCE_PROTOCOL_ERROR);
    }

    try {
        t.get(c, new InMemoryDestFile() {
            public long getLength() { return -1; }
            public OutputStream getOutputStream() throws IOException
            {
                return new FilterOutputStream(dstStream) {
                    public void write( byte[] b, int off, int len ) throws IOException
                    {
                        super.write(b, off, len);
                        totalReceived[0] += len;
                        if( dataListener != null ) dataListener.updateTransferStats(totalReceived[0]);
                    }
                };
            }
            public OutputStream getOutputStream( boolean append ) throws IOException
            {
                return getOutputStream();
            }
        });
        logMessage(GLogMessage.GLOG_SERVER_REPLY, "GET OK " + totalReceived[0] + " bytes");
    } catch( IOException e ) {
        throw new GConnectionException("IO Exception while downloading " + c + " : " + e.getMessage(),
            GConnectionException.GCE_PROTOCOL_ERROR);
    }
}

public void gpp_cancelTransfer()
{
    synchronized( transferLock )
    {
        if( transSftpClient != null )
        {
            try {
                transSftpClient.close();
            } catch( IOException e ) {
                // best-effort
            }
            transSftpClient = null;
        }
    }
}

}
