//
//  GCommonEvents.java
//  Gideon
//
//  Created by David on 11/18/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.core;

import com.gideonsoftworks.gideon.widgets.GAboutBox;
import com.gideonsoftworks.gideon.preferences.GPreferencesWindow;
import com.gideonsoftworks.gideon.actions.GUIAction;
import com.gideonsoftworks.gideon.browser.GBrowserPanelController;
import com.gideonsoftworks.gideon.browser.GBrowserWindow;
import com.gideonsoftworks.gcore.*;
import com.gideonsoftworks.gja.*;
import java.util.*;
import javax.swing.*;
import java.io.File;
import java.io.FileFilter;
import java.lang.reflect.Array;
 
/**
    This class contains methods that are used to do common application actions on all supported platforms. These are called
	from different places depending on platform ie Exit in the file menu calls doQuit()...
*/
public class GCommonEvents 
{

private static  GAboutBox					gcwAboutWindow			= null;
private static  GPreferencesWindow			gcwPreferenceWindow		= null;
private static  GTransferFilterListPanel	gcwTransferFilterWindow = null;

/**
    This method is called to show the about panel.
*/
public static   void doAbout()
{

    if( gcwAboutWindow == null )
    {
        gcwAboutWindow = new GAboutBox(GUIAction.getSharedGFrameManager()); 
    }
    
    gcwAboutWindow.show();
    
}

/**
*
*   Global error handling dialog
*   
*   Used througout the code to inform the user of hte bad people. 
*
*   @param  ex Exception
*
*
*/

public static void handleError( final Exception ex )
{

    if( ex == null )
    {
        throw new NullPointerException("exception passed to handleError was null");
    }

    String errorMsg = (ex != null && ex.getMessage() != null) ? ex.getMessage() : "An error occurred.";
    System.err.println("ERROR DIALOG: " + errorMsg);
    ex.printStackTrace(System.err);
    javax.swing.JOptionPane.showMessageDialog(null, errorMsg, "Error", javax.swing.JOptionPane.ERROR_MESSAGE);

}



/**
    This method is called by the OS to open a file with GSFTP
    @param  f File Local file to open
*/
public static void doOpenFile( final File f )
{
	// The legacy "mirror-ease" auto-mirror feature was removed; opening a file with the
	// app currently has no associated action.
}

/**
    This method is called to open the Transfer Filter Manager
*/
public static   void doTransferFilter()
{

    if( gcwTransferFilterWindow == null )
    {           
        gcwTransferFilterWindow = new GTransferFilterListPanel(GUIAction.getSharedGFrameManager());  
    }
    
    gcwTransferFilterWindow.show();

}

/**
    This method is called to open the Preference Window
*/
public static   void doPreferences()
{

    if( gcwPreferenceWindow == null )
    {           
        gcwPreferenceWindow = new GPreferencesWindow(GUIAction.getSharedGFrameManager());  
    }
    
    gcwPreferenceWindow.show();

}

/**
    Global Quit method that is executed on all platforms.
*/
public static   boolean doQuit()
{
    performShutdown( () -> System.exit(0) );
    return false;
}

/**
    The orderly quit (docs/1.8.0/GRACEFUL_SHUTDOWN_PLAN.md), every step bounded, on the calling (EDT) thread:
    flag + watchdog -> save state -> stop pollers -> stop and join all jobs -> log out every open
    connection in parallel -> delete temp files -> {@code exit}. Workers that reach the EDT hand-off
    while this runs see the flag and cancel themselves instead of waiting on the busy EDT.
    @param exit what to do at the end (System.exit in the app; a hook in tests)
*/
public static   void performShutdown( final Runnable exit )
{
    GAppShutdown.begin();
    GAppShutdown.startWatchdog( GAppShutdown.WATCHDOG_MS, () -> {
        GAuditLog.lifecycle("shutdown deadline exceeded - halting");
        Runtime.getRuntime().halt(0);
    } );

    // 1. save state first: it must survive even if a later step stalls
    GAppLifecycle.fireAppWillQuit();

    // 2. stop everything that could still enqueue work on a connection
    GFrameManager fm = GUIAction.getSharedGFrameManager();
    List<GJobManager> managers = new ArrayList<>();
    if( fm != null )
    {
        for( GBrowserWindow bw : fm.getBrowserWindows() )
        {
            for( Object pane : new Object[]{ bw.getRemoteBrowser(), bw.getLocalBrowser() } )
                if( pane instanceof GBrowserPanelController p ) p.stopPollers();
            if( bw.getJobManager() != null ) managers.add( bw.getJobManager() );
        }
    }
    managers.add( GConnectionReaper.getSharedReaper().getJobManager() );

    // 3. stop every active job everywhere and wait (bounded) for the workers
    GAppShutdown.stopManagers( managers, GAppShutdown.JOIN_BUDGET_MS );

    // 4. log out every open connection, in parallel, bounded
    int left = GAppShutdown.disconnectAll( GAppShutdown.DISCONNECT_BUDGET_MS );
    if( left > 0 ) GAuditLog.lifecycle("shutdown: " + left + " connection(s) did not log out in time");

    // 5. temp files - now that no worker is still writing one
    cleanTempFolder();

    // 6. out
    exit.run();
}

private static void cleanTempFolder()
{
/*
    *
    * Clean out temp folder
    *
    */
        
    if( GideonConfiguration.TEMP_CLEANUP_DEBUG ) System.out.println("Clearing : " + GLocalized.TEMP_FILE_PATH );
    
    File tempFolder = new File(GLocalized.TEMP_FILE_PATH);
    
    // clean up temp folder
    if (tempFolder.exists()) 
    {

        // It is also possible to filter the list of returned files.
        // This example does not return any files that start with `.'.
        FileFilter fileFilter = new FileFilter() {
            public boolean accept(File file) {
                return !file.getName().startsWith(".");
            }
            public String getDescription()
            {
                return "filter . files";
            }
        };
        
        File[] children = tempFolder.listFiles((FileFilter)fileFilter);

        // delete all files in this folder
        if( children == null ) children = new File[0];
        for( int i = 0 ; i < Array.getLength(children) ; i++ )
        {
            File cItem = (File)Array.get(children, i);
            
            if(cItem != null)
            {
                if( GideonConfiguration.TEMP_CLEANUP_DEBUG ) System.out.println("Deleting : " + cItem.getName() );
                cItem.delete();
            }
        
        }
        
    }
}

}
