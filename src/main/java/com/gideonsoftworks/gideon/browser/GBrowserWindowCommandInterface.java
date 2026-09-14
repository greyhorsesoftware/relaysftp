//
//  GBrowserWindowCommandInterface.java
//  Gideon
//
//  Created by David on 11/8/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.browser;

import  com.gideonsoftworks.gcore.*;
import  com.gideonsoftworks.gja.*;
import  javax.swing.*;

public interface GBrowserWindowCommandInterface 
{
    
    public  void                            setTitleTo( String title );
    public  GBrowserPanelCommandInterface   getRemoteBrowser();
    public  GBrowserPanelCommandInterface   getLocalBrowser();
    public  GBrowserPanelCommandInterface   getKeyBrowser();
   //     public  void                            closeKeyTab();
    public  void                            newTab();
    public  boolean                         getSideBarVisible();
    public  void                            setSideBarVisible( boolean  isVisible );
    public  boolean                         getLocalFilesVisible();
    public  void                            setLocalFilesVisible( boolean  isVisible );
    public  GJobManager                     getJobManager();
    public void                             disconnectBrowser( GBrowserPanelCommandInterface b );
    // actually part of GFrame - its here for continuity purposes
    public  void                            validateToolbar();
    public  void                            addBrowserWindowListener( GBrowserWindowListenerInterface l );
    public  void                            removeBrowserWindowListener( GBrowserWindowListenerInterface l );
    // call backs for browsers -> browser window - should this use the event center ?
    public  void                            userDoubleClickedFile(GFileItem f);
    
}
