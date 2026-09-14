//
//  GProtocolPluginFTP.java
//  Gideon
//
//  Created by David on 9/18/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gcore;

import  com.gideonsoftworks.gideon.core.GLocalized;
import  com.gideonsoftworks.gideon.core.GideonConfiguration;
import  com.gideonsoftworks.gideon.preferences.GSFTPPreferences;
import  com.gideonsoftworks.gja.*;
import  com.gideonsoftworks.gcore.*;
import  org.apache.commons.net.ftp.*;
import  org.apache.commons.net.io.CopyStreamAdapter;
import  org.apache.commons.net.ProtocolCommandEvent;
import  org.apache.commons.net.ProtocolCommandListener;
import java.text.SimpleDateFormat;
import java.text.ParsePosition;
import  java.io.*;
import  java.net.*;
import  java.util.*;

class GProtocolPluginFTP extends GProtocolPlugin
{

public static  final int    FTP_AUTO_MODE = 0;
public static  final int    FTP_ASCII_MODE = 1;
public static  final int    FTP_BINARY_MODE = 2;

protected	FTPClient			ftpClient   = null;
private		GTransferProgressListener	tListener   = null;
private		SimpleDateFormat	mlstFormatter = new SimpleDateFormat("yyyyMMddHHmmss");
private		boolean				useSsl		= false;

public  void          gpp_setConnectMode( int flag ) throws GConnectionException
{
        // 1 - is active / 0 is passive

        if( flag == 1 )
        {
            ftpClient.enterLocalActiveMode();
        }else{
            ftpClient.enterLocalPassiveMode();
        }

}

public  GProtocolPluginFTP( final GConnection c )  throws GConnectionException
{

    super(c);
    this.useSsl = false;

}

public  GProtocolPluginFTP( final GConnection c, boolean ssl )  throws GConnectionException
{

    super(c);
    this.useSsl = ssl;

}

public  void    gpp_disconnect(GConnection c) throws GConnectionException
{
    if( c == null )
    {
        throw new NullPointerException();
    }

    try{
        // #9: logout can fail on a dropped link — still disconnect the socket, always mark disconnected.
        try { ftpClient.logout(); } catch( IOException ignore ) {}
        ftpClient.disconnect();
    }catch( IOException e ){
        throw new GConnectionException(e.toString(), GConnectionException.GCE_NETWORK_ERROR );
    }finally{
        this.setIsConnected(false);
    }
}

public  void    gpp_connect( GBookmark b ) throws GConnectionException
{
    String  hn = null;
    String  proxyLabel = null, proxyVia = null, connectTarget = null;   // proxy info, visible to the catch

    if( b == null )
    {
        throw new NullPointerException();
    }

    // if the bookmark port is 0 - use 21
    int port = b.getPort();
    if(port==0)
    {
        port = 21;
    }

    try{
        this.getConnection().getLog().logMessage(new GLogMessage(GLogMessage.GLOG_INFORMATIONAL, GLocalized.USING_PORT + " " + port ));

        hn = b.getHostname();

        // create a new client instance
        if( useSsl )
        {
            FTPSClient ftpsClient = new FTPSClient(port == 990);
            // commons-net leaves TLS endpoint identity checking off by default; enable it so a cert
            // valid for a different host is rejected. Must be set before connect.
            ftpsClient.setEndpointCheckingEnabled(true);
            ftpClient = ftpsClient;
        }else{
            ftpClient = new FTPClient();
        }

        // add protocol command listener for logging
        ftpClient.addProtocolCommandListener(new ProtocolCommandListener() {
            public void protocolCommandSent(ProtocolCommandEvent event) {
                String msg = event.getMessage();
                if( msg != null ) {
                    GLogMessage cMSG = new GLogMessage( GLogMessage.GLOG_CLIENT_COMMAND, msg.trim() );
                    getConnection().getLog().logMessage(cMSG);
                }
            }
            public void protocolReplyReceived(ProtocolCommandEvent event) {
                String msg = event.getMessage();
                if( msg != null ) {
                    GLogMessage rMSG = new GLogMessage( GLogMessage.GLOG_SERVER_REPLY, msg.trim() );
                    getConnection().getLog().logMessage(rMSG);
                }
            }
        });

        // With an FTP firewall proxy, the control socket connects to the FIREWALL; the real host travels in
        // the login commands (see GFtpProxyLogin). Without one, we connect straight to the real host.
        boolean useFirewall = GProxyConfig.isEnabled();
        String  connectHost = useFirewall ? GProxyConfig.getHost() : hn;
        int     connectPort = useFirewall ? ( GProxyConfig.getPort() != 0 ? GProxyConfig.getPort() : 21 ) : port;
        connectTarget = connectHost + ":" + connectPort;

        // Transport proxy: the manual SOCKS5 config takes precedence; otherwise fall back to the system proxy
        // (which covers an OS HTTP CONNECT proxy). Implicit FTPS (990) needs its own SSL sockets, so a SOCKS
        // proxy is applied there via setProxy, which preserves the TLS socket handling.
        boolean implicitFtps = useSsl && port == 990;
        if( GProxyConfig.isSocksEnabled() )
        {
            proxyLabel = "SOCKS5";
            proxyVia   = GProxyConfig.getSocksHost() + ":" + GProxyConfig.getSocksPort();
            if( implicitFtps )
                ftpClient.setProxy( new Proxy( Proxy.Type.SOCKS, new InetSocketAddress( GProxyConfig.getSocksHost(), GProxyConfig.getSocksPort() ) ) );
            else
                ftpClient.setSocketFactory( GSystemProxy.socks5Factory(
                    GProxyConfig.getSocksHost(), GProxyConfig.getSocksPort(), GProxyConfig.getSocksUser(), GProxyConfig.getSocksPassword() ) );
            GAuditLog.proxy( hn, proxyLabel, proxyVia, connectTarget, "configured" );
        }
        else
        {
            Proxy ftpProxy = GSystemProxy.forSocket( "ftp", connectHost, connectPort );
            if( ftpProxy.type() != Proxy.Type.DIRECT )
            {
                proxyLabel = GSystemProxy.describe( ftpProxy );
                proxyVia   = GSystemProxy.via( ftpProxy );
                if( implicitFtps )
                {
                    if( ftpProxy.type() == Proxy.Type.SOCKS ) ftpClient.setProxy( ftpProxy );
                }
                else
                {
                    ftpClient.setSocketFactory( GSystemProxy.socketFactory( ftpProxy ) );
                }
                GAuditLog.proxy( hn, proxyLabel, proxyVia, connectTarget, "configured" );
            }
        }

        // connect (to the firewall when one is configured, else the real host)
        ftpClient.connect(connectHost, connectPort);

        int reply = ftpClient.getReplyCode();
        if( !FTPReply.isPositiveCompletion(reply) )
        {
            // A proxy/firewall in front rejects with a negative FTP reply — record exactly what it said.
            if( proxyLabel != null )
                GAuditLog.proxy( hn, proxyLabel, proxyVia, connectTarget, "failed: " + ftpClient.getReplyString().trim() );
            ftpClient.disconnect();
            throw new GConnectionException(ftpClient.getReplyString(), GConnectionException.GCE_PROTOCOL_ERROR);
        }

    }catch( IOException e ){
        // SOCKS transport failures surface here (HTTP CONNECT self-reports the proxy's status at the source).
        if( proxyLabel != null && !"HTTP-CONNECT".equals( proxyLabel ) )
            GAuditLog.proxy( hn, proxyLabel, proxyVia, connectTarget != null ? connectTarget : hn, "failed: " + e.getMessage() );
        throw new GConnectionException(e.toString(), GConnectionException.GCE_NETWORK_ERROR );
    }

    boolean ok = false;
    try{

        // FTPS: negotiate TLS before login
        if( useSsl )
        {
            FTPSClient ftpsClient = (FTPSClient) ftpClient;
            ftpsClient.execPBSZ(0);
            ftpsClient.execPROT("P");
            setIsSecure(true);
        }

        // handle the case where the password/username is null
        String username = b.getUsername();
        String password = GBookmarkManager.getPassword(b);

        if( username == null )
        {
            username = "anonymous";

            if( password == null )
            {
                password = "relaysftp@greyhorsesoftware.com";
            }
        }

        if( password == null ) password = "";

        boolean loginOk;
        if( GProxyConfig.isEnabled() )   // FTP firewall proxy: run the type-specific login sequence
        {
            GFtpFirewallType fwType = GProxyConfig.getType();
            loginOk = GFtpProxyLogin.run( ftpClient, fwType, hn, port, username, password,
                                          GProxyConfig.getUser(), GProxyConfig.getPassword() );
            int fwPort = GProxyConfig.getPort() != 0 ? GProxyConfig.getPort() : 21;
            GAuditLog.proxy( hn, "FTP-firewall " + fwType.label(), GProxyConfig.getHost() + ":" + fwPort,
                hn + ":" + port, loginOk ? "login-ok" : "failed: " + ftpClient.getReplyString().trim() );
        }
        else
        {
            loginOk = ftpClient.login(username, password);
        }

        if( !loginOk )
        {
            throw new GConnectionException(ftpClient.getReplyString(), GConnectionException.GCE_BAD_LOGIN);
        }

        setIsConnected(true);

        // handle configuring for pasv/port mode.
        if( GSFTPPreferences.dontUsePASV() )
        {
            ftpClient.enterLocalActiveMode();
        }else{
            ftpClient.enterLocalPassiveMode();
        }

        // set up progress monitoring
        ftpClient.setCopyStreamListener(new CopyStreamAdapter() {
            public void bytesTransferred(long totalBytesTransferred, int bytesTransferred, long streamSize) {
                if( tListener != null )
                {
                    tListener.updateTransferStats(totalBytesTransferred);
                }
            }
        });

        ok = true;

    }catch( IOException e ){
        throw new GConnectionException(e.toString(), GConnectionException.GCE_BAD_LOGIN );
    }finally{
        // #9: a failed TLS negotiation/login must not leave the connected control socket open.
        if( !ok ){
            try { if( ftpClient != null ) ftpClient.disconnect(); } catch( IOException ignore ) {}
            setIsConnected(false);
        }
    }

}

public  GFileItem	gpp_exists( String fPath, String fName, boolean isDir ) throws GConnectionException
{

	GFileItem	nFI					= null;
	long		fSize				= 0;
	Date		modDate				= null;
	boolean		fFolderDoesNotExist	= false;

	try{

		// handle checking for the existence of a file
		if( !isDir )
		{

			if( GideonConfiguration.TESTFORFILEDEBUG ) System.out.println("ftp->gpp_exists() - Testing for presence of file.");

			try{

				int rc = ftpClient.sendCommand("MLST " + fPath);
				String replyStr = ftpClient.getReplyString();

				if( GideonConfiguration.TESTFORFILEDEBUG ) System.out.println( "Reply MSG : " + replyStr );

				if( FTPReply.isPositiveCompletion(rc) )
				{
					String size = getMLSTValue("Size=", replyStr);

					if( size != null )
					{
						if( GideonConfiguration.TESTFORFILEDEBUG ) System.out.println( "File Size : " + size );
						fSize = Long.parseLong(size);
					}

					String mdString = getMLSTValue("Modify=", replyStr);
					if( GideonConfiguration.TESTFORFILEDEBUG ) System.out.println( "Modify : >" + mdString + "<" );

					if( mdString != null )
					{
						modDate = mlstFormatter.parse(mdString, new ParsePosition(0));
					}
				}else if( rc == 550 )
				{
					return null;
				}else if( rc == 500 || rc == 502 )
				{
					// server doesn't support MLST, try SIZE
					if( GideonConfiguration.TESTFORFILEDEBUG ) System.out.println( "MLST Unsupported - trying SIZE" );
					int sizeRc = ftpClient.sendCommand("SIZE " + fPath);
					if( FTPReply.isPositiveCompletion(sizeRc) )
					{
						String sizeReply = ftpClient.getReplyString().trim();
						// Reply format: "213 <size>"
						String[] parts = sizeReply.split("\\s+");
						if( parts.length >= 2 )
						{
							fSize = Long.parseLong(parts[1]);
						}
					}else if( ftpClient.getReplyCode() == 550 )
					{
						return null;
					}
				}

			}catch( NumberFormatException e )
			{
				// size parse failure, continue with size=0
			}

		}else{

			if( GideonConfiguration.TESTFORFILEDEBUG ) System.out.println("ftp->gpp_exists() - Testing for presence of folder.");

			if( GideonConfiguration.TESTFORFILEDEBUG ) System.out.println("\tftp->gpp_exists() - Getting current folder");
			String	lastCWD = ftpClient.printWorkingDirectory();
			if( GideonConfiguration.TESTFORFILEDEBUG ) System.out.println("\tftp->gpp_exists() - Current Folder : " + lastCWD);

			boolean changed = ftpClient.changeWorkingDirectory(fPath);

			if( !changed )
			{
				if( GideonConfiguration.TESTFORFILEDEBUG )
				{
					System.out.println("\tChange Failed " + fPath);
					System.out.println("\tReply Code : " + ftpClient.getReplyCode() );
				}

				if( ftpClient.getReplyCode() == 550 )
				{
					System.out.println("\tFolder Does Not Exist");
					fFolderDoesNotExist=true;
				}
			}

			gpp_cwd( lastCWD );

			if( fFolderDoesNotExist )
			{
				return null;
			}

			fSize=0;
			modDate=null;

		}

		if( GideonConfiguration.TESTFORFILEDEBUG )
		{
			System.out.println("ftp->gpp_exists()");
			System.out.println("\tFile Name : " + fName );
			System.out.println("\tFile Size : " + fSize );
			System.out.println("\tFile Mod Date : " + modDate );
			System.out.println("ftp->gpp_exists()");
		}

		// create a new file item
		nFI = new GFileItem( null, fName, fSize, modDate );
		nFI.setCompletePath(fPath);

		if( isDir )
		{
			nFI.setFileType(GFileItem.G_FOLDER);
		}else{
			nFI.setFileType(GFileItem.G_FILE);
		}

    }catch( IOException e ){
        throw new GConnectionException(e.toString(), GConnectionException.GCE_NETWORK_ERROR );
    }

	return nFI;

}


private	String	getMLSTValue( String key, String text )
{
		StringBuilder	sb = new StringBuilder();
		int				index = text.indexOf(key);

		// key does not exist
		if( index == -1 ) return null;

		char			c;

		index+=key.length();

		while( index < text.length() && ( c = text.charAt(index++) ) != ';' )
		{
			sb.append(c);
		}

		return sb.toString();
}

public  GListParser gpp_parser_instance()
{

    return new GListParserFTP();

}

public  Object    gpp_list( String p ) throws GConnectionException
{

    FTPFile[]   list=null;

    if( ftpClient == null ) System.out.println("FTP Client is null");

    try{
        list = ftpClient.listFiles(p);
    }catch( IOException e ){
        throw new GConnectionException(e.toString(), GConnectionException.GCE_NETWORK_ERROR );
    }

    return (Object)list;

}

public String gpp_get_pwd() throws GConnectionException
{
    String  pwd;

    try{
        pwd = ftpClient.printWorkingDirectory();
        if( pwd == null )
        {
            throw new GConnectionException(ftpClient.getReplyString(), GConnectionException.GCE_PROTOCOL_ERROR);
        }
    }catch( IOException e ){
        throw new GConnectionException(e.toString(), GConnectionException.GCE_NETWORK_ERROR );
    }

    return pwd;

}

public  void        gpp_cwd( String p ) throws GConnectionException
{

    try{
        boolean ok = ftpClient.changeWorkingDirectory(p);
        if( !ok )
        {
            throw new GConnectionException(ftpClient.getReplyCode() + " " + ftpClient.getReplyString(), GConnectionException.GCE_BAD_FOLDER);
        }
    }catch( IOException e ){
        throw new GConnectionException(e.toString(), GConnectionException.GCE_NETWORK_ERROR );
    }

}

private	String	generateErrorString()
{
    return ftpClient.getReplyCode() + " " + ftpClient.getReplyString();
}

public  boolean     gpp_needsCompletePathForRename()
{
        return true;
}

public  void        gpp_rename( String p, String n ) throws GConnectionException
{

    try{
        boolean ok = ftpClient.rename(p, n);
        if( !ok )
        {
            throw new GConnectionException(generateErrorString(), GConnectionException.GCE_PROTOCOL_ERROR);
        }
    }catch( IOException e ){
        throw new GConnectionException(e.toString(), GConnectionException.GCE_NETWORK_ERROR );
    }

}

public  void        gpp_move( String p, String n ) throws GConnectionException
{
    this.gpp_rename(p, n);
}

public  void        gpp_rm( String p, boolean isDir ) throws GConnectionException
{
    try{
        boolean ok;
        if( isDir )
        {
            ok = ftpClient.removeDirectory(p);
        }else{
            ok = ftpClient.deleteFile(p);
        }
        if( !ok )
        {
            throw new GConnectionException(generateErrorString(), GConnectionException.GCE_PROTOCOL_ERROR);
        }
    }catch( IOException e ){
        throw new GConnectionException(e.toString(), GConnectionException.GCE_NETWORK_ERROR );
    }
}

public  void   gpp_chmod( final String p, final GPermissions pmO ) throws GConnectionException
{
    // fake it ;)
    this.gpp_send_command("SITE CHMOD " + pmO.getNumericValueAsString() + " " + p );
}

public  void   gpp_mkdir( String dP ) throws GConnectionException
{

    try{
        boolean ok = ftpClient.makeDirectory(dP);
        if( !ok )
        {
            throw new GConnectionException(generateErrorString(), GConnectionException.GCE_PROTOCOL_ERROR);
        }
    }catch( IOException e ){
        throw new GConnectionException(e.toString(), GConnectionException.GCE_NETWORK_ERROR );
    }

}

public  String        gpp_send_command( String c ) throws GConnectionException
{

    try{
        int rc = ftpClient.sendCommand(c);
        String reply = ftpClient.getReplyString();
        if( !FTPReply.isPositiveCompletion(rc) )
        {
            throw new GConnectionException(rc + " " + reply, GConnectionException.GCE_PROTOCOL_ERROR);
        }
        return reply;
    }catch( IOException e ){
        throw new GConnectionException(e.toString(), GConnectionException.GCE_NETWORK_ERROR );
    }

}

public  void    gpp_ping() throws GConnectionException
{

    try{

        boolean ok = ftpClient.sendNoOp();
        if( !ok )
        {
            setIsConnected(false);
            throw new GConnectionException("NOOP failed",
                GConnectionException.GCE_PROTOCOL_ERROR);
        }

    }catch( IOException e )
    {

        setIsConnected(false);
        throw new GConnectionException("Connection test failed: " + e.getMessage(),
            GConnectionException.GCE_PROTOCOL_ERROR);

    }

}

public boolean  gpp_needsKeepAlive()
{

    return true;

}
/**
*
*
*   Transfer API
*
*
*/

public void gpp_upload( final String c, FileInputStream srcStream, File localFile, GTransferProgressListener l ) throws GConnectionException
{
    tListener = l;
    try{
        boolean ok = ftpClient.storeFile( c, srcStream );
        if( !ok )
        {
            throw new GConnectionException(generateErrorString(), GConnectionException.GCE_PROTOCOL_ERROR);
        }
    }catch( IOException e ){
        throw new GConnectionException(e.toString(), GConnectionException.GCE_NETWORK_ERROR );
    }finally{
        tListener = null;   // don't keep a finished transfer's listener alive until the next one
    }
}


public void gpp_download( final String c, FileOutputStream dstStream, File localFile, GTransferProgressListener l ) throws GConnectionException
{
    tListener = l;
    try{
        boolean ok = ftpClient.retrieveFile( c, dstStream );
        if( !ok )
        {
            throw new GConnectionException(generateErrorString(), GConnectionException.GCE_PROTOCOL_ERROR);
        }
    }catch( IOException e ){
        throw new GConnectionException(e.toString(), GConnectionException.GCE_NETWORK_ERROR );
    }finally{
        tListener = null;   // don't keep a finished transfer's listener alive until the next one
    }
}

public void gpp_prepareToTransferFile( GFileItem f, int flag ) throws GConnectionException
{
    int tType;

    if( flag == 0 )
    {
        // auto mode
        if( f.isASCIIFile())
        {
            tType = FTP.ASCII_FILE_TYPE;
        }else{
            tType = FTP.BINARY_FILE_TYPE;
        }

    }else if( flag == 1 ){

        tType = FTP.BINARY_FILE_TYPE;

    }else{

        tType = FTP.ASCII_FILE_TYPE;

    }

    try{
        boolean ok = ftpClient.setFileType(tType);
        if( !ok )
        {
            throw new GConnectionException(generateErrorString(), GConnectionException.GCE_PROTOCOL_ERROR);
        }
    }catch( IOException e ){
        throw new GConnectionException(e.toString(), GConnectionException.GCE_NETWORK_ERROR );
    }

}

public void gpp_cancelTransfer()
{

    try{
        ftpClient.abort();
    }catch( IOException e ){
        // best effort
    }

}

public  boolean gpp_requiresLoginAfterCancellation()
{

    return true;

}

}
