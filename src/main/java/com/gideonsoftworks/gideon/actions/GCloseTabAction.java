//
//  GCloseTabAction.java
//  GSFTP
//
//  Created by David on 10/22/05.
//  Copyright 2005 Grey Horse Software All rights reserved.
//
package com.gideonsoftworks.gideon.actions;

import com.gideonsoftworks.gideon.core.GFrame;
import com.gideonsoftworks.gideon.core.GFrameManager;
import com.gideonsoftworks.gideon.browser.GBrowserWindow;
import com.gideonsoftworks.gideon.browser.GBrowserPanelCommandInterface;
import com.gideonsoftworks.gdo.*;
import com.gideonsoftworks.gcore.*;
import java.lang.reflect.*;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.*;

public class GCloseTabAction extends GUIAction
{

public  GCloseTabAction( String name, Icon icon )
{
     super( name, icon, GUIAction.GA_CLOSE_TAB );
}

public  void actionPerformed( ActionEvent e )
{
        GFrameManager                   theManager          = GUIAction.getSharedGFrameManager();
        GFrame                          keyWindow           = null;
        
        if( theManager != null )
        {
            keyWindow = theManager.getKeyWindow();
        }

        if( keyWindow != null )
        {
            if( keyWindow instanceof GBrowserWindow )
            {
                keyWindow.closeKeyTab();
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

    return true;

}

}
