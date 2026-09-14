//
//  GGetInfoUIAction.java
//  Gideon
//
//  Created by David on 10/12/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.getinfo;

import com.gideonsoftworks.gideon.core.GFrame;
import com.gideonsoftworks.gideon.core.GFrameManager;
import com.gideonsoftworks.gideon.core.GValidationInterface;
import com.gideonsoftworks.gideon.actions.GUIAction;
import com.gideonsoftworks.gideon.browser.GBrowserWindowCommandInterface;
import com.gideonsoftworks.gideon.browser.GBrowserPanelCommandInterface;
import com.gideonsoftworks.gcore.*;
import com.gideonsoftworks.editor.*;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.lang.reflect.Array;

public class GGetInfoUIAction extends GUIAction implements GValidationInterface {
    
    public  GGetInfoUIAction( String name, Icon icon )
    {
    
         super( name, icon, GUIAction.GA_NONE );
    
    }
    
    public void  actionPerformed( ActionEvent e )
    {
        
        GFrameManager                   theManager  = GUIAction.getSharedGFrameManager();
        GFrame                          keyWindow   = null;
        GBrowserPanelCommandInterface   keyBrowser  = null;
        GFileItem[]                     fileItems   = null;
        
        keyWindow = (GFrame)theManager.getKeyWindow();
        
        if(keyWindow==null)
        {
            return;
        }
        
        keyBrowser = ((GBrowserWindowCommandInterface)keyWindow).getKeyBrowser();
		
        if(keyBrowser==null)
        {

			return;

        }else{
			fileItems = keyBrowser.getSelection();
		}

        
        if( fileItems != null )
        {
            for( int i=0 ; i < Array.getLength(fileItems) ; i++ )
            {
                GFileItem fileItem = (GFileItem)Array.get(fileItems, i);                
                GGetInfoWindow  giWindow = new GGetInfoWindow(theManager, keyBrowser);
                if(giWindow!=null)
                {
                    giWindow.show(fileItem);
                }else{
                    // should never get here...
                    throw new NullPointerException("GGetInfoUIAction->actionPerformed - error createing get info window");
                }
            }
        }
        
    }
    
    public boolean validateItem(GFrameManager theManager,
                                    GFrame  keyWindow,
                                        boolean keyWindowIsBrowser,
                                        GBrowserPanelCommandInterface keyBrowser, 
                                            GBrowserPanelCommandInterface localBrowser, 
                                                GBrowserPanelCommandInterface remoteBrowser,   
                                                    int gideonActionCode )
    {


        // handle editor window case. We allow the user to "get info" on the current item being edited.
        if( keyWindow instanceof GEditorWindow )
        {
                if( ((GEditorWindow)keyWindow).getFileItem() != null &&
                        keyBrowser==null )
                {
                        return true;
                }
        }
                
        if(keyBrowser==null)
        {
            return false;
        }
                
        if( keyBrowser.hasSelection() )
        {
            return true;
        }
        
        return false;
    
    }
    
}
