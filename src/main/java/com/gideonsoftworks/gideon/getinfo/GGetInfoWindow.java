//
//  GGetInfoWindow.java
//  Gideon
//
//  Created by David on 10/18/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.getinfo;

import com.gideonsoftworks.gideon.permissions.GPermissionSetterDelegateInterface;
import com.gideonsoftworks.gideon.permissions.GPermissionSetterPanelController;
import com.gideonsoftworks.gideon.core.GFrame;
import com.gideonsoftworks.gideon.core.GFrameManager;
import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.browser.GBrowserPanelController;
import com.gideonsoftworks.gideon.browser.GBrowserPanelListenerInterface;
import com.gideonsoftworks.gideon.browser.GBrowserPanelCommandInterface;
import com.gideonsoftworks.gcore.*;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.lang.reflect.Array;

public class GGetInfoWindow extends GFrame implements GPermissionSetterDelegateInterface, GBrowserPanelListenerInterface
{
    private GGetInfoPanelController             ggiInfoPanelController = null;
    private GPermissionSetterPanelController    ggiPermissionSetterPanelController = null;
    private GGetInfoPanelTitlePanelController   ggiNameBanner = null;
    private JTabbedPane                         ggiTabPane = null;
    private GFileItem                           ggiFile=null;
    private GBrowserPanelCommandInterface       ggiBrowser=null;
    
    public  GGetInfoWindow( GFrameManager fm, GBrowserPanelCommandInterface browser )
    {
    
        super(fm);
        
        if( browser == null )
        {
            throw new NullPointerException("GGetInfoWindow->GGetInfoWindow - browser is null");
        }
        
        ggiBrowser  = browser;
        
        Container   content = this.getContentPane();
        ggiInfoPanelController = new GGetInfoPanelController();
        ggiPermissionSetterPanelController = new GPermissionSetterPanelController((GPermissionSetterDelegateInterface)this);
        ggiNameBanner = new GGetInfoPanelTitlePanelController();
        
        JPanel rootPanel = new JPanel( new BorderLayout() );
        rootPanel.add( ggiNameBanner.getPanel(), BorderLayout.PAGE_START );
        ggiTabPane = new JTabbedPane();
        ggiTabPane.add( GLocalized.GENERALTABTITLE, ggiInfoPanelController.getPanel() ); 
        ggiTabPane.add( GLocalized.PERMISSIONTABTITLE, ggiPermissionSetterPanelController.getPanel() ); 
        rootPanel.add( ggiTabPane, BorderLayout.CENTER );
        
        rootPanel.setBorder( BorderFactory.createEmptyBorder(5,10,10,10) );
        
        content.add(rootPanel);

        this.setTitle(GLocalized.GETINFONAME);
        this.setResizable(false);
        
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        int x = (int)(dim.getWidth() - getWidth())/2;
        int y = (int)(dim.getHeight() - getHeight())/2;
        setLocation(x,y);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        this.pack();
        
    }
    
    // GPermissionSetterDelegateInterface
    public void bp_setPermissions( GPermissions nPerms, boolean applyToEnclosed )
    {
    
        
        GFileItem[] item = (GFileItem[])Array.newInstance(GFileItem.class,1);
        Array.set( (Object)item, 0, (Object)ggiFile );
        
        // tell the browser to work its magic
        ggiBrowser.setPermissions( item, nPerms, applyToEnclosed, this );
        
    }
    
    // GBrowserPanel event callback
    @Override
    public void bpl_disconnecting( GBrowserPanelController panel )
    {
        // don't allow permission changes...
        ggiPermissionSetterPanelController.setAllowsPermissionChanges(false);
    }

    // END GBrowserPanelListenerInterface
    
    public  boolean hasToolbar()
    {
        return false;
    }

        
    public  void    show(GFileItem fi)
    {

        setTitle(GLocalized.GETINFOTITLE);
            
        ggiInfoPanelController.populateForFile(fi);
        ggiNameBanner.populateForFile(fi);
        ggiPermissionSetterPanelController.populateForFile(fi);
        
        // add ourself the connection for this file item so we know when it goes away
        //fi.getConnection().addStatusListener((GBrowserPanelListenerInterface)this);
        
        ggiFile = fi; 
        
        // add the get info window as a listener for browser panel events
        // this is so if the connection is "disconnected" the get info panel disappears.
        ((GBrowserPanelController)ggiBrowser).addBrowserPanelListener( this );

        //((GBrowserPanelController)ggiBrowser).addStatusListener((GBrowserPanelListenerInterface)this);
        
        show();
        
            
    }
    
    /** Unregister on dispose (any path: close box or programmatic), so a closed window never acts on a disconnect. */
    @Override
    public void removeNotify()
    {
        if( ggiBrowser != null ) ggiBrowser.removeBrowserPanelListener( this );
        super.removeNotify();
    }

}
