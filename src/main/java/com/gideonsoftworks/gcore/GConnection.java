//
//  GConnection.java
//  Gideon
//
//  Created by David on Mon Jul 19 2004.
//  Copyright (c) 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gcore;

import java.util.WeakHashMap;
import java.util.Set;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import  com.gideonsoftworks.gideon.preferences.GSFTPPreferences;
import  java.io.*;

public class GConnection extends GListenableItem
{

public interface FolderChangeListener {
    void localFolderChanged(GLocalFileEvent event);
}

private GProtocolPlugin gcProtocol=null;        // protocol plugin
private GLog            gcLog=null;             // log instance
private FolderChangeListener folderChangeListener = null;
private GBookmark       gcBookmark=null;        // Connection Bookmark
private GListParser     gcParser=null;          // listing parser for this connection
private GFileItem       gcCurrentFileItem=null; // CWD
private GFileItem       gcRootFileItem=null;    // Root Folder
private boolean         gcRemoteConnection=false;
private boolean         gcForcedToDisconnect=false;
private GConnectionStats gcStats = new GConnectionStats();
private final GListingPool gcListingPool = new GListingPool();   // shares owner/group/size/perms across every listing on this connection

/**
 * Every constructed connection, weakly, so an orderly shutdown can log them all out without a
 * registry per owner (panes, per-job clones, preprocessing clones, the reaper). Keyed by identity:
 * GConnection does not override equals — keep it that way.
 */
private static final Set<GConnection> OPEN_CONNECTIONS =
    Collections.synchronizedSet( Collections.newSetFromMap( new WeakHashMap<>() ) );

/** Snapshot of the constructed-and-still-connected connections. */
public static List<GConnection> openConnections()
{
    List<GConnection> out = new ArrayList<>();
    synchronized( OPEN_CONNECTIONS )
    {
        for( GConnection c : OPEN_CONNECTIONS )
        {
            if( c == null ) continue;
            try { if( c.getIsConnected() ) out.add( c ); } catch( GConnectionException e ) { /* not connected */ }
        }
    }
    return out;
}

public  GConnection( final GBookmark b ) throws GConnectionException
{
    OPEN_CONNECTIONS.add( this );
    GLog        log;
    GFileItem   theCWD;
    
    if( b == null )
    {
        throw new NullPointerException("GConnection : null Bookmark passed in to constructor");
    }
    
    this.setBookmark(b);
    
    // create the protocol plugin subclass
    if( b.getProtocol() == GBookmark.G_FTP_PROTOCOL )
    {
        gcProtocol = new GProtocolPluginFTP(this);
        gcRemoteConnection = true;
    }else if( b.getProtocol() == GBookmark.G_FTPSSL_PROTOCOL )
    {
        gcProtocol = new GProtocolPluginFTP(this, true);
        gcRemoteConnection = true;
    }else if( b.getProtocol() == GBookmark.G_SFTP_PROTOCOL )
    {
        gcProtocol = new GProtocolPluginSFTP(this);
        gcRemoteConnection = true;
    }else if( b.getProtocol() == GBookmark.G_S3_PROTOCOL )
    {
        gcProtocol = new GProtocolPluginS3(this);
        gcRemoteConnection = true;
    }else if( b.getProtocol() == GBookmark.G_FILE_PROTOCOL )
    {
        gcProtocol = new GProtocolPluginFILE(this);
        gcRemoteConnection = false;
    }else{
        throw new IllegalArgumentException("GConnection->GConnection : unknown protocol : " + b.getProtocol() );
    }
  
    // create a log with protocol-qualified name to avoid tab collisions
    String protocolName;
    switch (b.getProtocol()) {
        case GBookmark.G_FTP_PROTOCOL: protocolName = "FTP"; break;
        case GBookmark.G_FTPSSL_PROTOCOL: protocolName = "FTPS"; break;
        case GBookmark.G_SFTP_PROTOCOL: protocolName = "SFTP"; break;
        case GBookmark.G_S3_PROTOCOL: protocolName = "S3"; break;
        case GBookmark.G_FILE_PROTOCOL: protocolName = "Local"; break;
        default: protocolName = ""; break;
    }
    String logName = b.getHostname() + " (" + protocolName + ")";
    log = new GLog(logName);
    this.setLog(log);
    
}

public  GConnection( final String host, final String un, final String pw, final int port, final int protocol ) throws GConnectionException
{

    this(new GBookmark(host, null, un, pw, port, protocol));

}

/**
    Get this connections bookmark
    @return Bookmark for connection
*/
public  boolean   getIsRemote()
{

    return gcRemoteConnection;

}

/**
    Returns yes if this protocol is secure
*/
public  boolean   getIsSecure()
{

    return gcProtocol.getIsSecure();

}

/**
    Get this connections bookmark
    @return Bookmark for connection
*/
public  GBookmark   getBookmark()
{

    return gcBookmark;

}

/**
    Set this connections bookmark
    @param b new bookmark for connection
*/
public  void   setBookmark( final GBookmark b )
{
    
    if( b == null )
    {
        throw new NullPointerException();
    }
    
    gcBookmark = b;

}

/*
*
* List Parser
*
*/

/**
    Get this connections list parser
    @return List parser for the connection
*/
public  GListParser   getListParser()
{

    return gcParser;

}

/**
    Set this connections list parser
    @param lp List parser for the connection
*/
public  void   setListParser( final GListParser lp )
{
    if( lp == null )
    {
        throw new NullPointerException();
    }
    
    gcParser = lp;

}

/*
*
* File Item Management
*
*
*       Folder handling in GConnection/GProtocolPlugin
*           - UI calls getCurrentFileItem() ( defaults to "/" for FTP/SFTP )
*           - UI will then be returned the GFileItem. UI can then issue a "list" and the item is "filled in".
*           
*       Bookmark Home Location
*
*           Bookmark home locations are automatically processed and all necessary GFileItems are created.
*           For instance, /home/david would result in a GConnection with the following items :
*
*                            /       STATE : NOT_LISTED ( root file item )
*                            home    STATE : NOT_LISTED
*                            david   STATE : LISTED
*/

/**
    Get the current file item
    @return Current working directory as a file item - can be not listed.
*/
public  GFileItem   getCurrentWorkingDirectory()
{

    return gcCurrentFileItem;

}

/**
    Update the current file item pointer
    @param cFI  New CWD
*/
private  void   setCurrentWorkingDirectory( GFileItem cFI )
{

    if( cFI == null )
    {
        throw new NullPointerException();
    }
    
    gcCurrentFileItem = cFI;

}


/*
*
* Log
*
*/

/**
    Get this connections log
    @return Log
*/
public  GLog   getLog()
{

    return gcLog;

}

/**
    Set this connections log
    @param log Log for the connection.
*/
public  void   setLog( final GLog log )
{
    if( log == null )
    {
        throw new NullPointerException();
    }
    
    gcLog = log;

}

/*
*
* commands
*
*/

/**
    Returns true if the connection is connected.
    @throws GConnectionException
*/
public  boolean  getIsConnected() throws GConnectionException
{
    return gcProtocol.getIsConnected();
}


public  boolean  userDisconnectedConnection()
{
    return gcForcedToDisconnect;
}

/**
    Login the user in using the pw/un in the connections bookmark. Also handles creating a list parser instance.
    @throws GConnectionException
*/
public  void    login() throws GConnectionException
{

    final   String      results;
    final   GBookmark   bm;
    
    bm  =   this.getBookmark();

    final long    startMs = System.currentTimeMillis();
    final String  host    = bm.getHostname() != null ? bm.getHostname() : "";
    final String  proto   = protocolLabel(bm.getProtocol());
    // Local-filesystem "logins" are instant and not meaningful — only audit remote ones.
    final boolean auditLogin = bm.getProtocol() != GBookmark.G_FILE_PROTOCOL;

    try {
        gcProtocol.gpp_connect( bm );
    } catch( GConnectionException e ) {
        if( auditLogin ) GAuditLog.login(host, proto, System.currentTimeMillis() - startMs, false);
        throw e;
    }

    this.setListParser(gcProtocol.gpp_parser_instance());

    // Probe server capabilities. mtime works over plain SFTP; exec-based features
    // (warp-list) are gated on the Use-Exec preference inside the probe itself.
    gcProtocol.gpp_probeCapabilities();

    // Populate connection stats
    gcStats.setHostname(bm.getHostname() != null ? bm.getHostname() : "");
    gcStats.setPort(bm.getPort());
    gcStats.setConnectedSince(System.currentTimeMillis());
    switch (bm.getProtocol()) {
        case GBookmark.G_FTP_PROTOCOL: gcStats.setProtocol("FTP"); break;
        case GBookmark.G_FTPSSL_PROTOCOL: gcStats.setProtocol("FTPS"); break;
        case GBookmark.G_SFTP_PROTOCOL: gcStats.setProtocol("SFTP"); break;
        case GBookmark.G_S3_PROTOCOL: gcStats.setProtocol("S3"); break;
        case GBookmark.G_FILE_PROTOCOL: gcStats.setProtocol("Local"); break;
        default: gcStats.setProtocol("Unknown"); break;
    }
    gcStats.setWarpListSupported(gcProtocol.gpp_supportsFastList());

    if( auditLogin ) GAuditLog.login(host, proto, System.currentTimeMillis() - startMs, true);

}

private static String protocolLabel( int p )
{
    switch( p )
    {
        case GBookmark.G_FTP_PROTOCOL:    return "FTP";
        case GBookmark.G_FTPSSL_PROTOCOL: return "FTPS";
        case GBookmark.G_SFTP_PROTOCOL:   return "SFTP";
        case GBookmark.G_S3_PROTOCOL:     return "S3";
        case GBookmark.G_FILE_PROTOCOL:   return "Local";
        default:                          return "Unknown";
    }
}

/**
    Logs the user out of the server. Also releases the parser context.
    @throws GConnectionException
*/
public  void    logout() throws GConnectionException
{
    try
    {
        gcProtocol.gpp_stopWatching();
        gcProtocol.gpp_disconnect( this );
    }
    finally
    {
        OPEN_CONNECTIONS.remove( this );
    }
    gcParser=null;
    gcCurrentFileItem=null;
}

public GConnectionStats getStats() { return gcStats; }

public boolean hasCapability(String capability) {
    // Only exec-channel capabilities depend on the Use-Exec preference. mtime uses
    // native SFTP stat and must not be masked by it.
    if (GConnectionStats.CAP_WARP_LIST.equals(capability) && !GSFTPPreferences.useExec()) return false;
    return gcStats.hasCapability(capability);
}

public void setFolderChangeListener(FolderChangeListener l) { folderChangeListener = l; }
public FolderChangeListener getFolderChangeListener() { return folderChangeListener; }

/**
 * Start watching a local folder for changes. Posts events via FolderChangeListener.
 */
public void watchFolder(GFileItem folderItem) {
    gcProtocol.gpp_watchFolder(folderItem);
}

/**
 * Stop watching for folder changes.
 */
public void stopWatching() {
    gcProtocol.gpp_stopWatching();
}

/**
 * Fast recursive delete via exec. Returns true if successful.
 */
public boolean fastDelete(String path) throws GConnectionException {
    return gcProtocol.gpp_fastDelete(path);
}

/**
 * Get the remote modification time of a folder. Returns -1 if not supported.
 * Logs the result to the connection log.
 */
public long getRemoteMtime(String path) {
    long mtime = gcProtocol.gpp_getRemoteMtime(path);
    if (gcLog != null) {
        if (mtime == -1) {
            gcLog.logMessage(new GLogMessage(GLogMessage.GLOG_INFORMATIONAL,
                "MTIME-CHECK " + path + " — not supported"));
        } else {
            gcLog.logMessage(new GLogMessage(GLogMessage.GLOG_INFORMATIONAL,
                "MTIME-CHECK " + path + " — " + mtime));
        }
    }
    return mtime;
}


public String  pathForWorkingDirectory() throws GConnectionException
{

    return gcProtocol.gpp_get_pwd();

}

public GFileItem    getMyComputerFileItem()
{

    return gcProtocol.gpp_getMyComputerFileItem();

}

public String getSeparator()
{
    
    return gcProtocol.gpp_getSeparator();


}

/**
    Create a new folder in a GFileItem
    @param  f  Parent folder to create folder in.
    @param name Folder name to create in GFileItem
    @throws GConnectionException, NullPointerException
*/
public  void newfolder( final GFileItem f, final String name ) throws GConnectionException
{
    final       String[]  results;
    final       String   completePath;
    GFileItem            newFolder;
    
	// check to make sure f is a folder
	if( !f.getIsDir() )
	{
		throw new IllegalArgumentException("Parent file item is not a folder.");
	}
	
    // error checks
    if( f == null )
    {
        throw new NullPointerException("null parent file item");
    }
    
    if( name == null )
    {
        throw new NullPointerException("Null name for new folder");
    }
    
    // generate complete path for folder
    String comParentPath = f.getCompletePath();
    
    StringBuilder sb = new StringBuilder( comParentPath );
    if( comParentPath.compareToIgnoreCase("/" ) == 0 )
    {
        sb.append(name);
    }else{
        sb.append("/" + name );
    }
    String newFolderCompletePath = sb.toString();
    
    // create it
    gcProtocol.gpp_mkdir( newFolderCompletePath );
       
    // create a new "folder" file item and insert it into the
    // parent file item
    newFolder = new GFileItem( name, newFolderCompletePath, this );
    newFolder.setFileType(GFileItem.G_FOLDER);
    f.addChild(newFolder);
	
	if( f.getIsFileLocal() )
	{
		newFolder.setFileIsLocal(true);
	}
                      
    // handle some GFileItem housekeeping
    // notify listeners file has been listed
    //f.notifyListenersContentsChanged();
        
}

public  void newfolder( final GFileItem f ) throws GConnectionException
{
    final       String   newFolderCompletePath;

    // error checks
    if( f == null )
    {
        throw new NullPointerException("GConnection->newfolder (GFileItem) : user passed in null parent file item");
    }

    // generate complete path for folder
    newFolderCompletePath = f.getCompletePath();

    // create it
    gcProtocol.gpp_mkdir( newFolderCompletePath );

}

/**
  Create a folder at an absolute path — path-based mkdir, no parent GFileItem required.
  Used by the mirror engine, which operates in path space. Thin delegate to the protocol's
  gpp_mkdir (same call newfolder() makes).
  @param completePath absolute path of the folder to create.
*/
public  void makeFolderAtPath( final String completePath ) throws GConnectionException
{
    if( completePath == null )
    {
        throw new NullPointerException("GConnection->makeFolderAtPath : null folder path");
    }
    gcProtocol.gpp_mkdir( completePath );
}

/** Set a file's last-modified time (epoch millis) — used by the mirror engine to preserve the source mtime. */
public  void setModifiedTime( final String path, final long epochMillis ) throws GConnectionException
{
    if( path == null ) throw new NullPointerException("GConnection->setModifiedTime : null path");
    gcProtocol.gpp_setModifiedTime( path, epochMillis );
}

/**
    Test to see if a folder exists.
    @param  f GFileItem to test for existance.
    @throws GConnectionException, NullPointerException
*/
public  void list( final GFileItem f ) throws GConnectionException
{
    final   Object   results;
    final   String   completePath;
    
    // error checks
    if( f == null )
    {
        throw new NullPointerException();
    }
    
    completePath = f.getCompletePath();

    results = gcProtocol.gpp_list( completePath );

    // clear only after the fetch succeeded - the previous listing stays visible
    // (and intact for mirror/find) during the network round-trip or a failed list
    if( f.getFileState() == GFileItem.STATE_LISTED )
    {
        f.removeAllChildren();
        f.setFileState(GFileItem.STATE_NOT_LISTED);
    }

    getListParser().parseList(results, this, f);
    
    // handle some GFileItem housekeeping
    // notify listeners file has been listed
    //f.notifyListenersContentsChanged();
    // set the state to listed
    f.setFileState(GFileItem.STATE_LISTED);
        
}

/**
 * Returns true if this connection supports fast recursive listing.
 */
/** The per-connection pool that de-duplicates repeated listing strings (owner, group, size, permissions). */
public GListingPool getListingPool()
{
    return gcListingPool;
}

public boolean supportsFastList()
{
    if (!GSFTPPreferences.useExec()) return false;
    return gcProtocol.gpp_supportsFastList();
}

/**
 * Recursively list all files/folders under rootItem in a single operation.
 * Populates the GFileItem tree with all children, marking all folders as STATE_LISTED.
 * Returns true if successful, false if not supported (caller should fall back).
 */
public boolean fastListRecursive(GFileItem rootItem) throws GConnectionException
{
    if (!supportsFastList()) return false;

    String rootPath = rootItem.getCompletePath();
    Object result = gcProtocol.gpp_fastListRecursive(rootPath);
    if (result == null) return false;

    String[] lines = (String[]) result;

    // Parse each ls -ld line and build the tree
    for (String line : lines) {
        if (line == null || line.trim().isEmpty()) continue;

        // Parse the ls -ld line to extract: type, size, date, name (full path)
        GFileItem parsed = GListParserUNIX.parseOneLine(line, this);
        if (parsed == null) continue;
        gcListingPool.canonicalize(parsed);

        String fullPath = parsed.getCompletePath();
        if (fullPath == null) continue;

        // Skip the root itself
        if (fullPath.equals(rootPath)) {
            rootItem.setFileState(GFileItem.STATE_LISTED);
            continue;
        }

        // Compute relative path from root
        String relativePath;
        if (fullPath.startsWith(rootPath + "/")) {
            relativePath = fullPath.substring(rootPath.length() + 1);
        } else if (fullPath.startsWith(rootPath)) {
            relativePath = fullPath.substring(rootPath.length());
            if (relativePath.startsWith("/")) relativePath = relativePath.substring(1);
        } else {
            continue; // outside our tree
        }

        if (relativePath.isEmpty()) continue;

        // Walk/create the path in the tree
        String[] parts = relativePath.split("/");
        GFileItem parent = rootItem;

        for (int i = 0; i < parts.length - 1; i++) {
            GFileItem child = parent.getChildNamed(parts[i]);
            if (child == null) {
                // Create intermediate folder
                child = new GFileItem(parts[i], parent.getCompletePath() + "/" + parts[i], this);
                child.setFileType(GFileItem.G_FOLDER);
                child.setFileState(GFileItem.STATE_LISTED);
                child.setParentFileItem(parent);
                parent.addChild(child);
            }
            parent = child;
        }

        // Add the leaf item
        String leafName = parts[parts.length - 1];
        GFileItem existing = parent.getChildNamed(leafName);
        if (existing == null) {
            parsed.setParentFileItem(parent);
            parent.addChild(parsed);
            if (parsed.getIsDir()) {
                parsed.setFileState(GFileItem.STATE_LISTED);
            }
        }

        // Mark parent as listed
        parent.setFileState(GFileItem.STATE_LISTED);
    }

    rootItem.setFileState(GFileItem.STATE_LISTED);
    return true;
}

/**
    Lists a folder GFileItem, then assigns it as our working directory.
    @param  f GFileItem to list
    @throws GConnectionException, NullPointerException
*/
public  void changeWorkingDirectoryAndList( final GFileItem f ) throws GConnectionException
{

    gcProtocol.gpp_cwd( f.getCompletePath() );
    
    // list the item
    this.list(f);

    // update the working directory pointer
    this.setCurrentWorkingDirectory(f);   
    
}


/**
    Called to "ping" the server - ie keep the connection alive
    @throws GConnectionException, NullPointerException
*/
public  void ping() throws GConnectionException
{

    // list the item
    gcProtocol.gpp_ping();

}

/**
    Called to see if this protocol requires pinging to keep the connection
    from timing out.
*/
public  boolean needsKeepAlive()
{

    // list the item
    return gcProtocol.gpp_needsKeepAlive();

}


/**
    Rename a GFileItem.
    @param  f GFileItem to rename
    @param  name new name for f
    @throws GConnectionException, NullPointerException, IllegalArgumentException
*/
public  void rename( final GFileItem f, final String name ) throws GConnectionException
{
    String  thePath = null;
    String  renameToPath = null;
            
    // error checks
    if( (f == null) || ( name == null ) )
    {
        throw new NullPointerException("GConnection : rename() file or name was null.");
    }
        
    if( name.length() == 0 )
    {
        throw new IllegalArgumentException("GConnection : rename() zero length name.");
    }
    
    // actual work
    thePath = f.getCompletePath();
    
    // the protocol needs a complete path specified as the rename to target
    if( gcProtocol.gpp_needsCompletePathForRename() )
    {
        // generate a complete path from the new file name n
        GFileItem pFI = f.getParentFileItem();
    
        // file/folder exists at the / of the file system. ie /etc
        if( pFI == null )
        {
            renameToPath = getSeparator() + name;
        }else{
            renameToPath = pFI.getCompletePath() + getSeparator() + name;
        }
    }else{
        renameToPath = name;
    }
    
    gcProtocol.gpp_rename( thePath, renameToPath );

    // update the file name
    f.setName( name );

    // update the complete path for our target item
    GFileItem   pFI = f.getParentFileItem();
    if( pFI != null )
    {
        f.setCompletePath( pFI.getCompletePath() + getSeparator() + name );
    }

    // a renamed folder's cached children still hold paths under the OLD name -
    // drop them and force a re-list on the next drill-in
    if( f.getIsDir() && f.getFileState() == GFileItem.STATE_LISTED )
    {
        f.removeAllChildren();
        f.setFileState( GFileItem.STATE_NOT_LISTED );
    }

}

/**
    Move a GFileItem to path
    @param  f GFileItem to move
    @param  nP New Parent folder for f
    @throws GConnectionException, NullPointerException, IllegalArgumentException
*/
public  void move( final GFileItem f, final GFileItem nP ) throws GConnectionException
{
    final   String  thePath;
    final   String  newPath;

    // error checks
    if( (f == null))
    {
        throw new NullPointerException();
    }
    
    // actual work
    thePath = f.getCompletePath();
    newPath = nP.getCompletePath();
    
    gcProtocol.gpp_move( thePath, newPath );

    // update the files parent
    f.setParentFileItem( nP );

}

/**
    Delete a file/folder GFileItem
    @param  f GFileItem to remove
    @throws GConnectionException, NullPointerException
*/
public  void delete( final GFileItem f ) throws GConnectionException
{
    final   String  thePath;
    
    // error checks
    if( f == null )
    {
        throw new NullPointerException();
    }

    // actual work
    thePath = f.getCompletePath();

    gcProtocol.gpp_rm( thePath, f.getIsDir() );
    
    // remove it from the parent fso
    if (f.getParentFileItem() != null) {
        f.getParentFileItem().removeChild(f);
    }
    
}

/**
    Tests for the existence of a GFileItem
    @param  f GFileItem to change permissions on
	@return	GFileItem representing file
    @throws GConnectionException, NullPointerException, IllegalArgumentException
*/
public GFileItem	exists( GFileItem f ) throws GConnectionException
{

	// error checks
    if( f == null )
    {
        throw new NullPointerException();
    }

    return	gcProtocol.gpp_exists( f.getCompletePath(), f.getName(), f.getIsDir() );

}

/**
    chmod a GFileItem
    @param  f GFileItem to change permissions on
    @param  pmO new GPermissions object
    @throws GConnectionException, NullPointerException, IllegalArgumentException
*/
public  void chmod( final GFileItem f, final GPermissions pmO ) throws GConnectionException
{
    final   String  thePath;
    
    // error checks
    if( f == null )
    {
        throw new NullPointerException();
    }
    
    // actual work
    thePath = f.getCompletePath();

    gcProtocol.gpp_chmod( thePath, pmO );

    // update the file item
    f.setPermissions(pmO);
    
}

/**
    Send an command to the server.
    @param  c Command string to send.
    @throws GConnectionException, NullPointerException, IllegalArgumentException
*/
public  String sendCommand( final String c ) throws GConnectionException
{

    // error checks
    if( c == null )
    {
        throw new NullPointerException();
    }
    
    if( c.length()==0 )
    {
        throw new IllegalArgumentException();
    }
    
    return gcProtocol.gpp_send_command( c );

}

public  void    setConnectMode( int flag ) throws GConnectionException
{

    gcProtocol.gpp_setConnectMode( flag );

}

/**
*
*
*   Transfer API
*
*
*/


public void upload( final String c, FileInputStream srcStream, File localFile, GTransferProgressListener l ) throws GConnectionException
{

    if( ( c == null ) ||
        ( srcStream == null ) )
    {
        throw new NullPointerException();
    }

    if( c.length()==0 )
    {
        throw new IllegalArgumentException();
    }

    gcProtocol.gpp_upload( c, srcStream, localFile, l );

}



public  void    prepareToTransferFile( GFileItem file, int flag ) throws GConnectionException
{

    gcProtocol.gpp_prepareToTransferFile( file, flag );

}

/**
  Generate a temporary, expiring download link for a remote object (S3 only).
  @param path remote path of the object
  @param expiry how long the link stays valid
  @return a presigned URL string
*/
public  String  presignGet( String path, java.time.Duration expiry ) throws GConnectionException
{
    return gcProtocol.gpp_presignGet( path, expiry );
}

public  void    cancelTransfer()
{

    gcProtocol.gpp_cancelTransfer();

}

public boolean  requiresLoginAfterCancellation()
{

    return gcProtocol.gpp_requiresLoginAfterCancellation();

}

public void download( final String c, FileOutputStream dstStream, File localFile, GTransferProgressListener l ) throws GConnectionException
{

    if( ( c == null ) ||
        ( dstStream == null ) )
    {
        throw new NullPointerException();
    }

    if( c.length()==0 )
    {
        throw new IllegalArgumentException();
    }

    gcProtocol.gpp_download( c, dstStream, localFile, l );

}

}
