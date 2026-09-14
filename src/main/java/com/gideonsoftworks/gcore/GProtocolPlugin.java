//
//  GProtocolPlugin.java
//  Gideon
//
//  Created by David on Mon Jul 19 2004.
//  Copyright (c) 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gcore;

import com.gideonsoftworks.gcore.*;
import java.util.*;
import java.io.*;

abstract class GProtocolPlugin
{

private     GConnection gppParentConnection;   // for access to log and bookmark info
private     boolean     gppIsConnected = false;
private     boolean     gppIsSecure = false;

public  GProtocolPlugin( final GConnection c )
{
    setConnection( c );
}

public void logMessage( int msgType, String string )
{
    
    getConnection().getLog().logMessage(new GLogMessage(msgType, string ) );

}

/**
    Set connection state
    @param isConnected true/false depending if connection is up.
*/
public  void    setIsConnected( final boolean isConnected )
{
    gppIsConnected = isConnected;
}

/**
    Returns current connected state. For use of subclassers.
    @return true if connected
*/
public boolean   getIsConnected()
{
    return gppIsConnected;
    
}

/**
    Set this connection as secure
    @return  true if connection is secure
*/
public  boolean  getIsSecure()
{

    return gppIsSecure;

}

/**
    Set this connection as secure
    @param  isSecure   secure state
*/
public void setIsSecure( boolean isSecure ) 
{

    gppIsSecure = isSecure;

}

/**
    Set connection info for the protocol plugin.
    @param c The connection.
*/
public  void    setConnection( final GConnection c )
{
    
    if( c == null )
    {
        throw new NullPointerException();
    }

    gppParentConnection = c;

}

/**
    Get context info for the protocol plugin. For use of subclassers.
    @return Context object
*/
public GConnection   getConnection()
{

    return gppParentConnection;
    
}

/*
*
* Plugin methods - subclasses must implement these methods
*
*/

/**
    Returns true if the file/folder exists remotely.
    @param  fPath   Parent folder path
	@param  fName   File name
	@param  isDir   is item a directory
	@throws GConnectionException
*/
abstract	public  GFileItem	gpp_exists( final String fPath, final String fName, final boolean isDir ) throws GConnectionException;

/**
    Login using the information found in the bookmark
    @param  b   Bookmark to fetch connection setting info from.
*/
abstract    public  void        gpp_connect(GBookmark b) throws GConnectionException;

/**
    Disconnect plugin.
    @param  c   Connection plugin is assigned too.
*/
abstract    public  void        gpp_disconnect(GConnection c) throws GConnectionException;

/**
    Return a parser for the server listings.
*/
abstract    public  GListParser gpp_parser_instance() throws GConnectionException;

/**
    List a folder at a path
    @param  p   Path of folder to list.
    @return Server file listing as text
*/
abstract    public  Object  gpp_list( String p ) throws GConnectionException;

/**
    Rename item at path p to name n
    @param  p   Target file/folder path.
    @param  n   New name for item
*/
abstract    public  void        gpp_rename( String p, String n ) throws GConnectionException;

/**
    Create a new directory at path/location
    @param  dP   Target file/folder path/name.
*/
abstract    public  void   gpp_mkdir( String dP ) throws GConnectionException;

/**
    Move item p to path n
    @param  p   Target file/folder path.
    @param  n   New path for item
*/
abstract    public  void        gpp_move( String p, String n ) throws GConnectionException;

/**
    Remove item at p
    @param  p   Target file/folder path.
    @param  isDir   true if item is a folder.
*/
abstract    public  void        gpp_rm( String p, boolean isDir ) throws GConnectionException;

/**
    Change permissions of p to perm
    @param  p    Target file/folder path.
    @param  pmO	 New permissions object
*/
abstract    public  void        gpp_chmod( final String p, final GPermissions pmO ) throws GConnectionException;

/**
    Set a file's last-modified time (epoch millis). Used by the mirror engine to preserve the source mtime on
    the copied file so size+mtime change-detection stays idempotent. Default is a no-op for protocols that
    can't set it (FTP upload, S3) — those use a mtime-independent detector. SFTP overrides this.
    @param  path          Target file path.
    @param  epochMillis   Last-modified time to set, in epoch milliseconds.
*/
public void gpp_setModifiedTime( String path, long epochMillis ) throws GConnectionException
{
    // no-op by default
}

/**
    Change working directory
    @param  p    Target file/folder path.
*/
abstract    public  void        gpp_cwd( String p ) throws GConnectionException;

/**
    Send a custom command to the server
    @param  c    Custom command to send.
    @return Server response to command.
*/
abstract    public  String        gpp_send_command( String c ) throws GConnectionException;

/**
    Specify a connection mode - FTP only
    @param  flag Mode to set
*/
public  void          gpp_setConnectMode( int flag ) throws GConnectionException
{

}

/**
    Generate a temporary, expiring download link for an object. Only S3 supports this; other
    protocols throw "operation not supported".
    @param  fPath  Remote path of the object.
    @param  expiry How long the link stays valid.
    @return A presigned URL string.
*/
public  String        gpp_presignGet( String fPath, java.time.Duration expiry ) throws GConnectionException
{
    throw new GConnectionException( "This protocol does not support temporary links",
        GConnectionException.GCE_OPERATION_NOT_SUPPORTED );
}

public  String  gpp_getSeparator()
{
    return "/";
}

public  boolean     gpp_needsCompletePathForRename()
{
        return false;
}

public  GFileItem   gpp_getMyComputerFileItem() 
{

    GFileItem   fItem = new GFileItem( "Computer", "Computer", this.getConnection() );
    fItem.setIsMyComputer(true);
    GFileItem   nFI = new GFileItem( "/", "/", this.getConnection() );
    nFI.setFileType(GFileItem.G_FOLDER);
    nFI.setIsRoot(true);
    fItem.addChild( nFI );
    return fItem;
}

abstract    public  String    gpp_get_pwd() throws GConnectionException;

public  void    gpp_ping() throws GConnectionException
{



}

public boolean  gpp_needsKeepAlive()
{

    return false;

}

/**
 * Fast recursive delete via exec (e.g. rm -rf). Returns true if successful.
 * Only supported on SFTP when exec is available.
 */
public boolean gpp_fastDelete(String path) throws GConnectionException {
    return false;
}

/**
 * Probe server capabilities after login (e.g. exec support, mtime, warp-list).
 * Results should be recorded in the connection's GConnectionStats.
 * Override in subclasses. Default is no-op.
 */
public void gpp_probeCapabilities() {
}


/**
 * Get the remote modification time of a folder. Returns -1 if not supported.
 * Used to detect remote folder changes without a full LIST.
 */
public long gpp_getRemoteMtime(String path) {
    return -1;
}

/**
 * Returns true if this protocol supports fast recursive listing
 * (e.g. via SSH exec). Override in subclasses.
 */
/**
 * Start watching a folder for changes. Only supported by local plugins.
 */
public void gpp_watchFolder(GFileItem folderItem) {
    // Default: no-op.
}

/**
 * Stop watching for folder changes.
 */
public void gpp_stopWatching() {
    // Default: no-op.
}

public boolean gpp_supportsFastList()
{
    return false;
}

/**
 * Recursively list all files and folders under the given path
 * in a single operation. Returns a flat list of lines parseable
 * by GListParserUNIX, or null if not supported/failed.
 */
public Object gpp_fastListRecursive(String path) throws GConnectionException
{
    return null;
}

/**
*
*
*   Transfer API
*
*
*/
abstract    public void gpp_cancelTransfer();
abstract    public void gpp_upload( final String c, FileInputStream srcStream, File localFile, GTransferProgressListener l ) throws GConnectionException;
abstract    public void gpp_download( final String c, FileOutputStream dstStream, File localFile, GTransferProgressListener l ) throws GConnectionException;
abstract    public void gpp_prepareToTransferFile( GFileItem f, int flag ) throws GConnectionException;

public  boolean gpp_requiresLoginAfterCancellation()
{

    return false;

}

}
