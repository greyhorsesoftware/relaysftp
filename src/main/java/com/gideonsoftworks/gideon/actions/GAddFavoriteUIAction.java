//
//  GSetPermissionsUIAction.java
//  Gideon
//
//  Created by David on 10/28/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.actions;

import com.gideonsoftworks.gideon.core.GFrame;
import com.gideonsoftworks.gideon.core.GFrameManager;
import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.actions.GUIAction;
import com.gideonsoftworks.gideon.browser.GBrowserPanelController;
import com.gideonsoftworks.gideon.browser.GBrowserWindow;
import com.gideonsoftworks.gideon.browser.GBrowserPanelCommandInterface;
import com.gideonsoftworks.gdo.*;
import com.gideonsoftworks.gcore.*;
import java.lang.reflect.*;
import javax.swing.*;
import java.awt.event.ActionEvent;

public class GAddFavoriteUIAction extends GUIAction 
{

public  GAddFavoriteUIAction( String name, Icon icon )
{

     super( name, icon, GUIAction.GA_ADD_FAVORITE );

}

public  void actionPerformed( ActionEvent e )
{

    GFrameManager           theManager = GUIAction.getSharedGFrameManager();
    //GQueueManagerWindow     qmWindow = null;
    GBrowserWindow          keyWindow = ((GBrowserWindow)theManager.getKeyWindow());
    GBrowserPanelController brw = null;
    String                  result=null;
    GFileItem[]             selectedFiles=null;
        
    // get the key browser before we show the option panel
    brw = (GBrowserPanelController)keyWindow.getKeyBrowser();
    
    selectedFiles = brw.getSelection();

    if( Array.getLength(selectedFiles) == 0 )
    {
        JOptionPane.showMessageDialog( null,GLocalized.NO_SELECTION);
        return;
    }

    if( brw == null )
    {
        System.out.println("Browser is null");
    }
    
    GBookmark keBM = brw.getBookmark();

    int tCount = Array.getLength(selectedFiles);

    for( int i = 0 ; i < tCount ; i++ )
    {
        GFileItem   cItem = (GFileItem)Array.get(selectedFiles, i);

        if( cItem.getIsDir() )
        {
            keBM.addFavoritePath(new GBookmarkFavoritePath(cItem.getCompletePath()));
        }

    }
    
    
}

/**
    This method is called before the menu is drawn. At this point the action can enable/disable associated menu items.
*/
public boolean validateItem(GFrameManager theManager,
                                    GFrame  keyWindow,
                                        boolean keyWindowIsBrowser,
                                        GBrowserPanelCommandInterface keyBrowser, 
                                            GBrowserPanelCommandInterface localBrowser, 
                                                GBrowserPanelCommandInterface remoteBrowser,   
                                                    int gideonActionCode )
{
    // if the key window is not a GBrowserWindow - don't bother validating it.
    // for instance - task manager and bookmark manager are GFrames
    if( !keyWindowIsBrowser )
    {
        return false;
    }

    if( keyBrowser == null ) return false;
    
    GConnection c = keyBrowser.getConnection();
    
    if( c != null && c.getIsRemote() )
    {
        putValue(NAME, GLocalized.ADDFAVORITE_BOOKMARK );
    }else{
        putValue(NAME, GLocalized.ADDFAVORITE_LOCAL );
    }
    
    // if not connected bail
    if( !keyBrowser.isConnected() ) return false;
    
    // validate - only enable for folders
    // if any items in a selection are folders - return true.
    GFileItem[] selection = keyBrowser.getSelection();
    
    if( selection == null ) return false;
    
    for( int i = 0 ; i < Array.getLength(selection) ; i++ )
    {
        GFileItem   cF = (GFileItem)Array.get(selection, i);
    
        if( cF.getIsDir() )
        {
            return true;
        }
        
    }
    
    return false;

}

}
