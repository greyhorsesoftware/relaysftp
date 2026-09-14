//
//  GSetPermissionsUIAction.java
//  Gideon
//
//  Created by David on 10/28/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.actions;

import com.gideonsoftworks.gideon.permissions.GBatchPermissionsWindow;
import com.gideonsoftworks.gideon.core.GFrame;
import com.gideonsoftworks.gideon.core.GFrameManager;
import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.actions.GUIAction;
import com.gideonsoftworks.gideon.browser.GBrowserPanelController;
import com.gideonsoftworks.gideon.browser.GBrowserWindow;
import com.gideonsoftworks.gideon.browser.GBrowserPanelCommandInterface;
import com.gideonsoftworks.gcore.*;
import java.lang.reflect.*;
import javax.swing.*;
import  java.awt.event.ActionEvent;

public class GSetPermissionsUIAction extends GUIAction 
{

public  GSetPermissionsUIAction( String name, Icon icon )
{

     super( name, icon, GUIAction.GA_SET_PERMISSIONS );

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
    
    
    // create a new set permissions window and open it
    GBatchPermissionsWindow newSetPermissionsWindow = new GBatchPermissionsWindow(theManager);
    newSetPermissionsWindow.show(selectedFiles, brw);
  
    //GBatchPermissionsWindow.sharedPermissionWindow().show(selectedFiles, brw);
    
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

        if(keyBrowser==null)
        {
            return false;
        }

        if( remoteBrowser != null )
        {
            if( remoteBrowser.hasSelection() )
            {
                return true;
            }
        }
        
        return false;

    }

}
