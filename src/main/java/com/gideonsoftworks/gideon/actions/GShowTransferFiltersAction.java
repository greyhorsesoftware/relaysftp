//
//  GShowTransferFiltersAction.java
//  GSFTP
//
//  Created by David on 9/24/05.
//  Copyright 2005 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.actions;

import com.gideonsoftworks.gideon.core.GFrame;
import com.gideonsoftworks.gideon.core.GFrameManager;
import com.gideonsoftworks.gideon.core.GCommonEvents;
import com.gideonsoftworks.gideon.browser.GBrowserPanelCommandInterface;
import javax.swing.*;
import java.awt.event.*;

public class GShowTransferFiltersAction extends GUIAction 
{

public  GShowTransferFiltersAction( String name )
{
     super( name, null, GUIAction.GA_SHOWTF );
}

public void  actionPerformed( ActionEvent e )
{
    GCommonEvents.doTransferFilter();
}

public boolean needsBrowser()
{
    return false;
}

public boolean validateItem(GFrameManager theManager,
                                    GFrame  keyWindow,
                                        boolean keyWindowIsBrowser,
                                        GBrowserPanelCommandInterface keyBrowser, 
                                            GBrowserPanelCommandInterface localBrowser, 
                                                GBrowserPanelCommandInterface remoteBrowser,   
                                                    int gideonActionCode )
{
    return true;
}

}
