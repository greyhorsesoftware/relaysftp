//
//  GProtocolPluginFILE.java
//  Gideon
//
//  Created by David on 10/29/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gcore;

import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gcore.*;
import java.io.*;
import java.lang.reflect.*;

public class GProtocolPluginFILE extends GProtocolPlugin
{

private GLocalFolderWatcher folderWatcher = null;

public  GProtocolPluginFILE( final GConnection c )
{
    super(c);
}
    
/**
    Login using the information found in the bookmark
    @param  b   Bookmark to fetch connection setting info from.
*/
public  void        gpp_connect(GBookmark b) throws GConnectionException
{

    this.setIsConnected(true);

}

/**
    Disconnect plugin.
    @param  c   Connection plugin is assigned too.
*/
public  void        gpp_disconnect(GConnection c) throws GConnectionException
{
    gpp_stopWatching();
    this.setIsConnected(false);

}

/**
    Return a parser for the server listings.
*/
public  GListParser gpp_parser_instance() throws GConnectionException
{
    return new GListParserFILE();
}

/**
    List a folder at a path
    @param  p   Path of folder to list.
    @return Server file listing as text
*/
public  Object  gpp_list( String p ) throws GConnectionException
{ 
    File    fItem = new File(p);
    return fItem.listFiles();
}

/**
	Return true if file/folder exists
*/
public  GFileItem	gpp_exists( String fPath, String fName, boolean isDir ) throws GConnectionException
{
	File	f = new File(fPath);
	
	if( !f.exists() ) return null;
	
	return new GFileItem(f);
	
}

/**
    Change working directory
    @param  p    Target file/folder path.
*/
public  void        gpp_cwd( String p ) throws GConnectionException
{

    File    fItem = new File(p);
    
    if( !fItem.exists() &&
        !fItem.isDirectory() )
    {
        throw new GConnectionException("Folder does not exist :" + p, GConnectionException.GCE_BAD_FOLDER );
    }

}

public String gpp_get_pwd() throws GConnectionException
{   
    return System.getProperty("user.home");
}

public  String gpp_getSeparator()
{

    return System.getProperty("file.separator");

}

public  GFileItem   gpp_getMyComputerFileItem() 
{
    GFileItem      fItem = null;

    if ( GLocalized.onWindows )
    {
        fItem = new GFileItem( GLocalized.MYCOMPUTER, GLocalized.MYCOMPUTER, this.getConnection() );
        fItem.setIsMyComputer(true);
        fItem.setFileIsLocal(true);
        
        File[]  roots = File.listRoots();
        
        for ( int i = 0; i < Array.getLength(roots) ; i++ )
        {
            File             f  = (File)Array.get(roots, i);
            GFileItem      nFI  = null;
            
            // A:\ is always the floppy - haha       
            if( f.getAbsolutePath().compareTo("A:\\") == 0 )
            {   
                nFI = new GFileItem( "A:\\", "A:\\", this.getConnection() );
                nFI.setFileType(GFileItem.G_FLOPPY);
                nFI.setCompletePath(f.getAbsolutePath());
            // ASSuME that D:\ is the cd - on most winblows boxes it is
            }else{
            
                nFI = new GFileItem(f);
            
                if( f.getAbsolutePath().compareTo("D:\\") == 0 )
                {
                    nFI.setFileType(GFileItem.G_REMOVABLE_MEDIA);
                    nFI.setCompletePath(f.getAbsolutePath());
                // every other root we'll call a HDD
                }else{
                    nFI.setFileType(GFileItem.G_HARD_DRIVE);
                }
            
            }
            
            // the name and path are the same
            nFI.setName(f.getAbsolutePath());
            nFI.setConnection(getConnection());
            nFI.setFileIsLocal(true);
            // flag this as the root for any child items
            nFI.setIsRoot(true);
            fItem.addChild( nFI );
            //fItem.setPlaceHolder(nFI);
        }
    }else{
        fItem = new GFileItem( "Computer", "Computer", this.getConnection() );
        fItem.setIsMyComputer(true);
        fItem.setFileIsLocal(true);
        GFileItem   nFItem = new GFileItem( new File("/") );
        // flag this as the root for any child items - used in path generation
        nFItem.setIsRoot(true);
        fItem.addChild( nFItem );
         
    }
    
    fItem.setFileState(GFileItem.STATE_LISTED);
    fItem.setIsRoot(true);

    return fItem;

}

public  boolean     gpp_needsCompletePathForRename()
{
        return true;
}


/**
    Rename item at path p to name n
    @param  p   Target file/folder path.
    @param  n   New name for item
*/
public  void        gpp_rename( String p, String n ) throws GConnectionException
{
	
	File    f = new File(p);
    
    if( !f.exists() )
    {
        throw new GConnectionException("Could not rename ( original doesn't exist ) : " + p, GConnectionException.GCE_PROTOCOL_ERROR );
    }
	
	File	dest = new File(n);
	
	if( dest.exists() )
    {
        throw new GConnectionException("Rename target exists : " + n, GConnectionException.GCE_PROTOCOL_ERROR );
    }
	
	if( !f.renameTo(dest) )
	{
		throw new GConnectionException("Could not rename : " + p, GConnectionException.GCE_PROTOCOL_ERROR );
	}

}

/**
    Create a new directory at path/location
    @param  dP   Target file/folder path/name.
*/
public  void   gpp_mkdir( String dP ) throws GConnectionException
{

    File    f = new File(dP);
    
    if( !f.mkdir() )
    {
        throw new GConnectionException("Could not mkdir :" + dP, GConnectionException.GCE_PROTOCOL_ERROR );
    }

}

/**
    Move item p to path n
    @param  p   Target file/folder path.
    @param  n   New path for item
*/
public  void        gpp_move( String p, String n ) throws GConnectionException
{
    
    File    f = new File(p);
    File    tF = new File(n);
    
    if( !f.renameTo(tF) )
    {
        throw new GConnectionException("Could not mkdir :" + p, GConnectionException.GCE_PROTOCOL_ERROR );
    }
    
}

/**
    Remove item at p
    @param  p   Target file/folder path.
    @param  isDir   true if item is a folder.
*/
public  void        gpp_rm( String p, boolean isDir ) throws GConnectionException
{

    File    f = new File(p);
    
    if( !f.delete() )
    {
        throw new GConnectionException("Could not delete :" + p, GConnectionException.GCE_PROTOCOL_ERROR );
    }
    
}

/**
    Change permissions of p to perm
    @param  p   Target file/folder path.
    @param  pmO New octal permission value.
	@throws GConnectionException
*/
public  void        gpp_chmod( final String p, final GPermissions pmO ) throws GConnectionException
{



}

/**
    Send a custom command to the server
    @param  c    Custom command to send.
    @return Server response to command.
*/
public  String        gpp_send_command( String c ) throws GConnectionException{ return null; }

/**
*
*
*   Transfer API
*
*
*/

public void gpp_upload( final String c, FileInputStream srcStream, File localFile, GTransferProgressListener l ) throws GConnectionException
{
    // On the local filesystem "upload" is a copy of the source file to the destination path.
    // We copy by path with COPY_ATTRIBUTES so the modification time is preserved — otherwise a
    // re-mirror would see every file as changed. (The streams exist for the generic transfer
    // API used by real remote protocols; the local copy doesn't need them.)
    localCopy( localFile, new File( c ) );
}


public void gpp_download( final String c, FileOutputStream dstStream, File localFile, GTransferProgressListener l ) throws GConnectionException
{
    localCopy( new File( c ), localFile );
}

private void localCopy( File src, File dst ) throws GConnectionException
{
    try {
        java.nio.file.Files.copy( src.toPath(), dst.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            java.nio.file.StandardCopyOption.COPY_ATTRIBUTES );
    } catch( IOException e ) {
        // NIO's message is just the path; keep the exception kind (AccessDenied, NoSuchFile, …) so callers can tell why.
        throw new GConnectionException( "Local copy failed: " + src + " -> " + dst + " : " + e.getClass().getSimpleName() + ": " + e.getMessage(),
            GConnectionException.GCE_NETWORK_ERROR );
    }
}

public void gpp_prepareToTransferFile( GFileItem f, int flag )
{
}

public void gpp_cancelTransfer()
{
}

@Override
public void gpp_watchFolder(GFileItem folderItem)
{
    gpp_stopWatching();

    if (folderItem == null || folderItem.getCompletePath() == null) return;

    folderWatcher = new GLocalFolderWatcher(changedNames -> {
        // Mark any changed subfolders as dirty so they re-list on next navigation
        for (String name : changedNames) {
            GFileItem child = folderItem.getChildNamed(name);
            if (child != null && child.getIsDir()
                && child.getFileState() == GFileItem.STATE_LISTED) {
                child.setFileState(GFileItem.STATE_NOT_LISTED);
            }
        }

        // Notify listener that folder contents changed
        GConnection conn = getConnection();
        if (conn != null && conn.getFolderChangeListener() != null) {
            GLocalFileEvent event = new GLocalFileEvent(
                GLocalFileEvent.LOCAL_FOLDER_CHANGED, folderItem.getCompletePath());
            conn.getFolderChangeListener().localFolderChanged(event);
        }
    });
    folderWatcher.watch(folderItem);
}

@Override
public void gpp_stopWatching()
{
    if (folderWatcher != null) {
        folderWatcher.stop();
        folderWatcher = null;
    }
}

}
