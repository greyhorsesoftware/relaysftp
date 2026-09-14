//
//  GBrowserPanelCommandInterface.java
//  Gideon
//
//  Created by David on 10/19/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.browser;

import com.gideonsoftworks.gideon.core.GGideonPanelController;
import  com.gideonsoftworks.gja.*;
import  com.gideonsoftworks.gcore.*;
import  com.gideonsoftworks.gdo.*;
import  java.util.*;
import  javax.swing.*;

public interface GBrowserPanelCommandInterface 
{

/**
*
* Commands
*
*/

public boolean		isConnected();
public void             download( final GFileItem[] sourceItems, final Object listener );
public void             upload( final GFileItem[] sourceItems, final Object listener );
public void             downloadTo( final GFileItem[] sourceItems, final GFileItem targetFile, final Object listener );
public void             uploadTo( final GFileItem[] sourceItems, final GFileItem targetFile, final Object listener );
public void             setPermissions( final GFileItem[] fileItems, final GPermissions permissions, final boolean recrusive, final Object listener );
public void             deleteItems( final GFileItem[] fileItems, final Object listener);
public void             disconnect( final Object listener );
public void             refresh( final Object listener );
public void             newFolder(  final String folderName, final Object listener );
public void             setName( final String name, final GFileItem fItem, final Object listener );
public GFileItem[]      getSelection();
public boolean          hasSelection();
public boolean          hasMultipleSelection();
public GFileItem	getWorkingFolder();
public JPopupMenu	getPopupMenuForSelection();
public boolean          getPopupShowing();
public void             setPopupShowing(boolean popupShowing);
public  void            setBuddy( GGideonPanelController buddy );
public  GGideonPanelController getBuddy();
public GFileItem    getItemAt( int row, int column );
public void         setConnection(GConnection c );
public GConnection  getConnection();
public GBookmark    getBookmark();
public void         showLoginPanel(GBookmark b);

/**
*
* Data Objects
*
*/
public GDataArray       getFavoriteFolderList();
public void             addBrowserPanelListener( GBrowserPanelListenerInterface l );
public void             removeBrowserPanelListener( GBrowserPanelListenerInterface l );

/**
* 
* 
*
*/
public  void    connectionToConnection( GConnection c );
public  void    connectForBookmark( GBookmark bm );
//public  void    browserWindowIsClosing();

/**
* Returns true or false depending on whether the current FileItem is a local/remote item
*
*/
public boolean      hasLocalSelection();
public GFileItem    getVirtualCWD();
public void         changeToFolder( GFileItem folder );
}
