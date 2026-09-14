//
//  GBookmarkUIAction.java
//  Gideon
//
//  Created by David on 10/12/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.bookmarks;

import com.gideonsoftworks.gideon.core.GFrame;
import com.gideonsoftworks.gideon.core.GFrameManager;
import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.core.GValidationInterface;
import com.gideonsoftworks.gideon.actions.GUIAction;
import com.gideonsoftworks.gideon.browser.GBrowserWindowCommandInterface;
import com.gideonsoftworks.gideon.browser.GBrowserWindow;
import com.gideonsoftworks.gideon.browser.GBrowserPanelCommandInterface;
import com.gideonsoftworks.editor.*;
import com.gideonsoftworks.gcore.*;
import javax.swing.*;
import  java.awt.event.ActionEvent;

public class GBookmarkUIAction extends GUIAction implements GValidationInterface
{
    

    public  GBookmarkUIAction( String name, Icon icon, int gaCode )
    {
    
        super( name, icon, gaCode );
    
    }
    
    /**
     * Open a bookmark the way the menu-bar Bookmarks item does: in the key browser window (a new
     * tab first if that window already has a live connection), or in a new browser window when
     * none is open. Shared by the Swing menu and the macOS Dock menu ({@code GDockMenu}).
     */
    public static void openBookmark( GBookmark bm )
    {
        if( bm == null )
        {
            throw new NullPointerException("GBookmarkUIAction : openBookmark - Bookmark was null");
        }

        GFrameManager   sharedFrameManager = GUIAction.getSharedGFrameManager();
        JFrame bw = sharedFrameManager.getKeyWindow(); 

        // if we have an open browser window - open the bookmark in the browser
        if( bw instanceof GBrowserWindow )
        {
            // a connected remote in the current tab: open the bookmark in a new tab instead
            if( ((GBrowserWindow)bw).isCurrentConnectionActive() )
            {
                ((GBrowserWindow)bw).newTab();
            }
            ((GBrowserWindow)bw).openBookmark( bm );
        }else{
            bw = new GBrowserWindow(sharedFrameManager, null);
            ((GBrowserWindow)bw).show();
            ((GBrowserWindow)bw).openBookmark(bm);
        }
    }

    public void  actionPerformed( ActionEvent e )
    {
    
        if( this.getGideonActionCode() == GA_BOOKMARK_MANAGE )
        { 
            GFrame qmWindow = GUIAction.getSharedGBookmarkManager();                
            qmWindow.show();
        }else if( this.getGideonActionCode() == GA_BOOKMARK_OPEN )
        { 
            Action bmmi = ((GBookmarkMenuItem)e.getSource()).getAction();
            GBookmark bm = (GBookmark)((GBookmarkUIAction)bmmi).getRepresentedObject();
            openBookmark( bm );
                    
        }else if( this.getGideonActionCode() == GA_BOOKMARK_ADD )
        { 
            // get the key window and open this bookmark in it.
            GFrameManager   sharedFrameManager = GUIAction.getSharedGFrameManager();
            
            GBrowserWindowCommandInterface bw = (GBrowserWindowCommandInterface)sharedFrameManager.getKeyWindow(); 
            
            // if we have an open browser window - open the bookmark in the browser
            if( bw instanceof GBrowserWindow )
            {
                
                GBrowserPanelCommandInterface   bR = bw.getRemoteBrowser();
                GBrowserPanelCommandInterface   bL = bw.getLocalBrowser();
                
                if( bR == null )
                {
                    throw new NullPointerException("GBookmarkUIAction->actionPerformed - Error retreiving remote or local browser");
                }
                
                GBookmark remoteBookmark = bR.getBookmark();
                
                if( remoteBookmark == null )
                {
                    throw new NullPointerException("GBookmarkUIAction->actionPerformed - Bookmark from remote browser was null");
                }
                
                // create a new bookmark from this one and add it to the bookmark manager
                // 091705 - we now use the bookmark from the view
                //          this is so the favorite folder functionality works
                //          making a copy resulted in the add favorite folder shit adding
                //          to the original bookmark.
                //GBookmark nBM = new GBookmark( remoteBookmark );
                
                // ask the user for a name for the bookmark
                String name=JOptionPane.showInputDialog(null, GLocalized.PLEASEENTERBOOKMARKNAME, remoteBookmark.getHostname() ); 

                if( name != null ) 
                {                
                    // set to the user specified name
                    remoteBookmark.setName(name);
                }else{
                    // default to setting the bookmark name to the host name
                    remoteBookmark.setName(remoteBookmark.getHostname());
                }
				
				if( bL != null )
				{
					// synch our local home path
					GBookmark localBookmark = bL.getBookmark();
					
					// if we have a local bookmark assign its home path as the local home
					// path for our new bookmark
					if( localBookmark != null )
					{
						remoteBookmark.setLocalHomePath(localBookmark.getHomePath());
					}
                }
				
                // add it to the bookmark manager
                GBookmarkManager.addBookmark(remoteBookmark);

            }
                    
        }


    }
    
    public boolean validateItem(GFrameManager sharedFrameManager,
                                    GFrame  keyWindow,
                                        boolean keyWindowIsBrowser,
                                        GBrowserPanelCommandInterface keyBrowser, 
                                            GBrowserPanelCommandInterface localBrowser, 
                                                GBrowserPanelCommandInterface remoteBrowser,   
                                                    int gideonActionCode )
    {

        if( gideonActionCode == GA_BOOKMARK_ADD )
        { 

            if( !(keyWindow instanceof GBrowserWindowCommandInterface) )
            {	
                    // we don't have a browser compatible thing - return
                    return false;
            }
            
            // XXX - deactivate this for this release - the editor is only for in place editing
            if( keyWindow instanceof GEditorWindow ) return false;
			
            GBrowserWindowCommandInterface bw = (GBrowserWindowCommandInterface)keyWindow; 

            // if we have an open browser window - enable the add bookmark menu item
            if( keyWindowIsBrowser )
            {
            
                GBrowserPanelCommandInterface   bR = bw.getRemoteBrowser();
                
                if( bR == null )
                {
                    return false;
                }
                
                GBookmark remoteBookmark = bR.getBookmark();
                
                if( remoteBookmark != null ) return true;

            }
            
        }else if( ( gideonActionCode == GA_BOOKMARK_MANAGE ) ||
            ( gideonActionCode == GA_BOOKMARK_OPEN ) )
        { 
            return true;
        }
        
        return false;
    
    }
    
    // return false if this action does not require a browser on screen
    public boolean needsBrowser()
    {
        return false;
    }
    
}
