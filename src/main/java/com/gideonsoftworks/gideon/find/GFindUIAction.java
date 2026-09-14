//
//  GFindUIAction.java
//  Gideon
//
//  Created by David on 10/12/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.find;

import com.gideonsoftworks.gideon.core.GFrame;
import com.gideonsoftworks.gideon.core.GFrameManager;
import com.gideonsoftworks.gideon.core.GValidationInterface;
import com.gideonsoftworks.gideon.actions.GUIAction;
import com.gideonsoftworks.gideon.browser.GBrowserPanelCommandInterface;
import com.gideonsoftworks.editor.*;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class GFindUIAction extends GUIAction implements GValidationInterface {
    
    public  GFindUIAction( String name, Icon icon, int type )
    {
         super( name, icon, type );
    }
    
    public void  actionPerformed( ActionEvent e )
    {
        
        GFrameManager                   theManager      = GUIAction.getSharedGFrameManager();
        GFrame							editorWindow    = theManager.getKeyWindow();
        GEditorPanelCommandInterface    ed              = null;

        switch( this.getGideonActionCode() )
        {

            case GA_FIND:

                if( !(editorWindow instanceof GEditorWindow) ) return;
                ((GEditorWindow)editorWindow).find();
				
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

        // handle editor window case. We allow the user
        // to "get info" on the current item being edited.
        if( keyWindow instanceof GEditorWindow )
        {
                return true;
        }

        return false;
    
    }
    
}
