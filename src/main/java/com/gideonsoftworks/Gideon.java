//
//  Gideon.java
//  GSFTP FTP/SSL/SFTP client
//
//  Created by David on Mon Jul 19 2004.
//  Copyright (c) 2004 Grey Horse Software All rights reserved.
//
package com.gideonsoftworks;

import com.gideonsoftworks.gideon.preferences.GSFTPPreferences;
import com.gideonsoftworks.gideon.core.GDockMenu;
import com.gideonsoftworks.gideon.core.GFrameManager;
import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.core.GCommonPartsFactory;
import com.gideonsoftworks.gideon.core.GAppLifecycle;
import com.gideonsoftworks.gideon.core.GAppLifecycleHandler;
import com.gideonsoftworks.gideon.core.GCommonEvents;
import com.gideonsoftworks.gideon.core.GOSXAdapter;
import com.gideonsoftworks.gideon.actions.GUIAction;
import com.gideonsoftworks.gideon.browser.GBrowserWindow;
import com.gideonsoftworks.gideon.bookmarks.GBookmarkManagerWindow;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import com.gideonsoftworks.gcore.*;
import com.gideonsoftworks.gja.*;
import java.io.IOException;
import java.io.*;
import java.util.prefs.Preferences;
import java.awt.Desktop;
import java.lang.reflect.*;

public class Gideon
{

        private GFrameManager           fm                  = new GFrameManager();
        private GBookmarkManagerWindow  bm                  = null; 
		
	public Gideon(String args[]) {

		super();

                // if on mac os x - show the menu in the system menu bar
                // MUST be set before any AWT/Swing class loading
                String osName = System.getProperty("os.name", "");
                if( osName.toLowerCase().contains("mac") )
                {
                    System.setProperty("apple.laf.useScreenMenuBar", "true");
                    System.setProperty("apple.awt.application.name", "RelaySFTP");
                }

                // Set WM_CLASS on Linux (XWayland) so taskbar shows correct name/icon
                if( !osName.toLowerCase().contains("mac") && !osName.toLowerCase().contains("win") )
                {
                    try {
                        var toolkit = java.awt.Toolkit.getDefaultToolkit();
                        var awtAppClassNameField = toolkit.getClass().getDeclaredField("awtAppClassName");
                        awtAppClassNameField.setAccessible(true);
                        awtAppClassNameField.set(toolkit, "RelaySFTP");
                    } catch (Exception e) {
                        // Fallback: try XToolkit programName field
                        try {
                            var toolkit = java.awt.Toolkit.getDefaultToolkit();
                            var programNameField = toolkit.getClass().getDeclaredField("programName");
                            programNameField.setAccessible(true);
                            programNameField.set(toolkit, "RelaySFTP");
                        } catch (Exception ignored) {}
                    }
                }

                JMenuBar        mBar;

                // use system proxy settings (macOS, Windows, Linux)
                System.setProperty("java.net.useSystemProxies", "true");

                // do the mac os x specific hookups
                macOSXRegistration();
                    
                createDataFolder();

                // Diagnostics: record startup and route uncaught exceptions to the audit log.
                String version = getClass().getPackage().getImplementationVersion();
                GAuditLog.lifecycle("start"
                    + " version=" + (version != null ? version : "dev")
                    + " os=" + System.getProperty("os.name", "?")
                    + " java=" + System.getProperty("java.version", "?"));
                Thread.setDefaultUncaughtExceptionHandler(GAuditLog.uncaughtHandler());

                Preferences prefs = Preferences.userNodeForPackage(GSFTPPreferences.class);
                
                // this resets back to auto mode on launch
                prefs.putInt(GJobDescription.TRANSFERMODE_JOB_PROP, 0 );
                
                // defaults for first launch
                if( !prefs.getBoolean(GSFTPPreferences.GPREF_FIRST_SETUP, false ) )
                {
                    prefs.putBoolean(GSFTPPreferences.GPREF_ONLY_SHOW_REMOTE, false );
                    prefs.putBoolean(GSFTPPreferences.GPREF_SFTP_IS_DEFAULT, true );
                    prefs.put(GSFTPPreferences.GPREF_DEFAULT_LHOME, GLocalized.HOME_FOLDER );
                    prefs.putBoolean(GSFTPPreferences.GPREF_FIRST_SETUP, true );
                }

                // create our shared job manager
                // on 11/09/04 - each window now has its own job manager
                //GJobManager.setSharedJobManager( new GJobManager() );

                /*
                *
                *
                * activate FlatLaf look and feel
                *
                *
                */
                com.gideonsoftworks.gideon.core.GTheme.applyStartup();   // apply the user's saved theme (default Light)
 
                /*
                *
                *
                * configure the GUIAction class with a ref to our frame manager
                * this is necessary so the action clases can discover active views/windows
                *
                *
                */
                GUIAction.setSharedGFrameManager(fm);

                /*
                *
                * Start the magical window creator
                *
                */
                	
                /*
                *
                *
                * Create our shared bookmark manager
                *
                *
                */
                bm = new GBookmarkManagerWindow(fm);

                // configure the GUIAction class with a ref to our shared bookmark manager
                GUIAction.setSharedGBookmarkManager(bm);

                // register shutdown listeners (typed app-lifecycle registry)
                GAppLifecycle.addListener( new GAppLifecycleHandler() );

                /*
                *
                * if we are under X - setup a globalm menu bar.
                * if not, under windows the browsers use the same menu 
                * in each window
                *
                */
                if( GLocalized.onMacOSX )
                {
                    // on mac os x - just launch and show the menu bar
                    Desktop.getDesktop().setDefaultMenuBar( GCommonPartsFactory.generateMenuBar(fm) );
                    
                }
                
                // Launch the browser window
                GBrowserWindow oBW = new GBrowserWindow(fm, null);
                oBW.show();

                // macOS Dock icon menu with the user's bookmarks. Installed only now: the data
                // folder exists (bookmarks load on first touch), a key window exists for
                // openBookmark().
                if( GLocalized.onMacOSX )
                {
                    boolean dock = GDockMenu.install();
                    GAuditLog.lifecycle("dock-menu installed=" + dock);
                }
				
  
                /*
                *
                * Handle open events for unix and windows
                *
                */
                
                if( !GLocalized.onMacOSX )
                {
                        int	i = 0;
                        int count = Array.getLength(args);
                        if( count > 0 ) 
                        {
                                for( i = 0 ; i < count ; i++ )
                                {
                                        String	cPathString = (String)Array.get( args, i ); 
                                        GCommonEvents.doOpenFile( new File(cPathString) );
                                }
                        }
                }
			
				
	}
     
	// Generic registration with the Mac OS X application menu.  Checks the platform, then attempts
	// to register with the Apple EAWT.
	// This method calls OSXAdapter.registerMacOSXApplication() and OSXAdapter.enablePrefs().
	// See OSXAdapter.java for the signatures of these methods.
	public void macOSXRegistration() {
		if (GLocalized.onMacOSX) {
			try {
				GOSXAdapter.registerMacOSXApplication(this, fm);
				GOSXAdapter.enablePrefs(true);
			} catch (Exception e) {
				System.err.println("Exception while registering macOS application handlers:");
				e.printStackTrace();
			}
		}
	}


	public static void main(String args[]) {
                Gideon  theGideon;
                theGideon = new Gideon(args);
	 }
        
        public void createDataFolder()
        {
            try
            {
                com.gideonsoftworks.gcore.GAppDataDefaults.ensureDefaults(
                    new File( GLocalized.DATA_FOLDER_PATH ),
                    new File( GLocalized.TEMP_FILE_PATH ),
                    System.getProperty( "user.home" ) );
            }
            catch( IOException e )
            {
                JOptionPane.showMessageDialog( null, GLocalized.DATA_STORE_INACCESSIBLE + e.getMessage() );
                System.exit( 0 );
            }
        }
        
}