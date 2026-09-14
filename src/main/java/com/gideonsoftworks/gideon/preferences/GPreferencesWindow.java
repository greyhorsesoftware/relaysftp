//
//  GGetInfoWindow.java
//  Gideon
//
//  Created by David on 10/18/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.preferences;

import com.gideonsoftworks.gideon.core.GFrame;
import com.gideonsoftworks.gideon.core.GFrameManager;
import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gcore.*;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.prefs.*;

public class GPreferencesWindow extends GFrame
{
    private GGeneralPreferencePanelController   gpwGeneralPanelController = null;
    private GSFTPPreferencePanelController     gpwSFTPPanelController = null;
    private GFTPPreferencePanelController       gpwFTPPanelController = null;
    private GSocksPreferencePanelController     gpwSocksPanelController = null;
    private GFavoritesPreferencePanelController gpwFavoritesPanelController = null;
    private JTabbedPane                         gpwTabPane = null;
    private Preferences                         gpwPreferences = null;
    
    public  GPreferencesWindow( GFrameManager fm )
    {
    
        super(fm);

        Container   content = this.getContentPane();
        gpwGeneralPanelController = new GGeneralPreferencePanelController();
        gpwSFTPPanelController = new GSFTPPreferencePanelController();
        gpwFTPPanelController = new GFTPPreferencePanelController();
        gpwSocksPanelController = new GSocksPreferencePanelController();
        gpwFavoritesPanelController = new GFavoritesPreferencePanelController();

        JPanel rootPanel = new JPanel( new BorderLayout() );
        gpwTabPane = new JTabbedPane();
        gpwTabPane.add( GLocalized.GENERALTABTITLE, gpwGeneralPanelController.getPanel() );
        gpwTabPane.add( "SFTP", gpwSFTPPanelController.getPanel() );
        gpwTabPane.add( "FTP", gpwFTPPanelController.getPanel() );
        gpwTabPane.add( "SOCKS5", gpwSocksPanelController.getPanel() );
        gpwTabPane.add( GLocalized.FAVORITESTABTITLE, gpwFavoritesPanelController.getPanel() );
        rootPanel.add( gpwTabPane, BorderLayout.CENTER );
        
        rootPanel.setBorder( BorderFactory.createEmptyBorder(5,10,10,10) );
        
        content.add(rootPanel);

        this.setTitle(GLocalized.PREFERENCESTITLE);
        this.setResizable(false);
        
        this.pack();
        
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        int x = (int)(dim.getWidth() - getWidth())/2;
        int y = (int)(dim.getHeight() - getHeight())/2;
        setLocation(x,y);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        
        
        
    }

    public  boolean hasToolbar()
    {
        return false;
    }
   
    public void show()
    {
        
        gpwGeneralPanelController.open();
        gpwSFTPPanelController.open();
        gpwFTPPanelController.open();
        gpwSocksPanelController.open();
        gpwFavoritesPanelController.open();

        super.show();
    
    }
    
    public boolean windowWillClose()
    {
        
        gpwGeneralPanelController.save();
        gpwSFTPPanelController.save();
        gpwFTPPanelController.save();
        gpwSocksPanelController.save();
        gpwFavoritesPanelController.save();

        return true;
            
    }
    
}
