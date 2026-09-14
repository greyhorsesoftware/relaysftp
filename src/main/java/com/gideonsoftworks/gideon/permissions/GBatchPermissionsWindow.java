//
//  GBatchPermissionsWindow.java
//  Gideon
//
//  Created by David on 10/28/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.permissions;

import com.gideonsoftworks.gideon.core.GFrame;
import com.gideonsoftworks.gideon.core.GFrameManager;
import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.core.GideonConfiguration;
import com.gideonsoftworks.gideon.browser.GBrowserPanelController;
import com.gideonsoftworks.gideon.browser.GBrowserPanelListenerInterface;
import com.gideonsoftworks.gideon.browser.GBrowserPanelCommandInterface;
import com.gideonsoftworks.gcore.*;
import com.gideonsoftworks.tabkit.GTabBarItem;
import javax.swing.*;
import java.awt.*;
import java.lang.reflect.*;
import java.util.HashMap;
import javax.swing.table.*;
import javax.swing.event.*;
import javax.swing.tree.*;

public class GBatchPermissionsWindow extends GFrame implements GPermissionSetterDelegateInterface, GBrowserPanelListenerInterface
{
    private GPermissionSetterPanelController    gbpPermSetter = null;
    private GFileVetoerPanelController          gbpFileSelector = null;
    private GBrowserPanelCommandInterface       gbpBrowser = null;

    public  GBatchPermissionsWindow( GFrameManager fm )
    {
        super(fm);
  
        // setup 
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        int x = (int)(dim.getWidth() - getWidth())/2;
        int y = (int)(dim.getHeight() - getHeight())/2;
        setLocation(x,y);
        this.setResizable(true);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

    }
	
	public JComponent getContentView( final GTabBarItem tbi )
	{
		// create our needed instance 
                gbpPermSetter = new GPermissionSetterPanelController((GPermissionSetterDelegateInterface)this);
                gbpFileSelector = new GFileVetoerPanelController();
		
		// generate content view
                JPanel master = new JPanel(new BorderLayout());
                master.setBorder(BorderFactory.createEmptyBorder(5,20,20,20));
                master.add( gbpFileSelector.getPanel(), BorderLayout.LINE_START );
                gbpPermSetter.setEnableApplyToContents(true);
                gbpPermSetter.setPermissions(new GPermissions("-rwxrwxrwx"));
                master.add( gbpPermSetter.getPanel(), BorderLayout.LINE_END );
		
		return master;
		
	}

    public  boolean hasToolbar()
    {
        return false;
    }

    public void show( GFileItem[] items, GBrowserPanelCommandInterface brw )
    {
    
        gbpBrowser = brw;
        
        // add this window as a listener for browser panel events
        // this is so if the connection is "disconnected" the set perms window disappears.
        gbpBrowser.addBrowserPanelListener( this );

        //((GBrowserPanelController)gbpBrowser).addStatusListener((GBrowserPanelListenerInterface)this);
        
        this.setTitle(GLocalized.SET_PERMISSIONS);

        show();

        // populate after show() so getContentView() has created the panels
        gbpFileSelector.setVetoableFiles(items);
 
    }
    
    // 
    public HashMap getFrameSettings() 
    {
        HashMap settings = new HashMap();
        settings.put(GFrame.MINIMUM_WIDTH, Integer.valueOf(589) );
        settings.put(GFrame.MINIMUM_HEIGHT, Integer.valueOf(308) );
        return settings;
    }

    /** Unregister on dispose (any path), so a closed window never acts on a disconnect. */
    @Override
    public void removeNotify()
    {
        if( gbpBrowser != null ) gbpBrowser.removeBrowserPanelListener( this );
        super.removeNotify();
    }

    // GBrowserPanelListenerInterface - called when the connection is going down
    @Override
    public void bpl_disconnecting( GBrowserPanelController panel )
    {
        // hide the window
        hide();
    }
    
    // GPermissionSetterDelegateInterface - called from the permission setter view
    public void bp_setPermissions( GPermissions nPerms, boolean applyToEnclosed )
    {
    
        if( GideonConfiguration.PERMISSION_UI_DEBUG ) System.out.println("User is setting permissions to : " + nPerms.getNumericValue() + " symbolic : " +  nPerms.getSymbolicValue() );
        if( GideonConfiguration.PERMISSION_UI_DEBUG ) System.out.println("Apply to contained : " + ( applyToEnclosed ? "yes" : "no" ) ); 
        
        // get the final user selection
        GFileItem[] items = gbpFileSelector.getNonVetoedFiles();
        
        if( items == null )
        {
            throw new NullPointerException("gbpFileSelector.getNonVetoedFiles - returned null file array");
        }
        
        if( Array.getLength(items) > 0 )
        {
            // tell the browser to work its magic
            gbpBrowser.setPermissions( items, nPerms, applyToEnclosed, this );
        }
        
        // after user request queued - hide ourselves
        hide();
        
    }
    
}
