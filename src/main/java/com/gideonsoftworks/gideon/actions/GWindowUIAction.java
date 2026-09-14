//
//  GWindowUIAction.java
//  Gideon
//
//  Created by David on 10/14/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.actions;

import com.gideonsoftworks.gideon.core.GFrame;
import com.gideonsoftworks.gideon.core.GFrameManager;
import com.gideonsoftworks.gideon.core.GValidationInterface;
import com.gideonsoftworks.gideon.browser.GBrowserPanelCommandInterface;
import javax.swing.*;
import  java.awt.event.ActionEvent;
import  java.awt.Frame;

public class GWindowUIAction extends GUIAction implements GValidationInterface
{

    public  GWindowUIAction( String name, Icon icon, int gaCode )
    {
    
        super( name, icon, gaCode );
    
    }
    
    public void  actionPerformed( ActionEvent e )
    {
        
        GFrameManager   theManager = GUIAction.getSharedGFrameManager();
        
        switch( this.getGideonActionCode() )
        {
        
            case GUIAction.GA_MINIMIZE:
                
                theManager.getKeyWindow().setExtendedState(Frame.ICONIFIED);
            
            break;
            
            case GUIAction.GA_SELECTWINDOW:

                GFrame w = (GFrame)this.getRepresentedObject();
                w.toFront();
            
            break;
        
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

        if( theManager.getWindowsOpen() ) 
        {
            return true;
        }
        
        return false;
    
    }
    

}
