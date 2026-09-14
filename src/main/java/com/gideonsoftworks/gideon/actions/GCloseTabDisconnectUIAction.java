//
//  GCloseTabDisconnectUIAction.java
//  Gideon
//
//  Created by David on 02/12/06.
//  Copyright 2006 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.actions;

import com.gideonsoftworks.gideon.browser.GBrowserWindow;
import com.gideonsoftworks.gideon.browser.GBrowserPanelController;
import com.gideonsoftworks.gcore.GConnection;
import com.gideonsoftworks.gja.*;
import javax.swing.*;
import java.awt.event.*;

public class GCloseTabDisconnectUIAction extends GUIAction
{

    public  GCloseTabDisconnectUIAction( String name, Icon icon, int code )
    {
        super( name, icon, code );
        setEnabled(false);
    }

    public boolean  shouldValidateItem()
    {
        return true;
    }

    public boolean  validateItem()
    {
        JFrame keyWindow = getSharedGFrameManager().getKeyWindow();
        if( keyWindow instanceof GBrowserWindow )
        {
            GBrowserWindow bw = (GBrowserWindow) keyWindow;
            Object remote = bw.getRemoteBrowser();
            if( remote instanceof GBrowserPanelController )
            {
                GConnection c = ((GBrowserPanelController) remote).getConnection();
                try {
                    return c != null && c.getIsConnected();
                } catch (Exception e) {
                    return false;
                }
            }
        }
        return false;
    }

}
