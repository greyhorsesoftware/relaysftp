//
//  GGideonPanelController.java
//  Gideon
//
//  Created by David on 10/5/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.core;

// Java
import java.awt.FileDialog;
import javax.swing.*;
import java.io.File;

import java.io.File;

public class GGideonPanelController
{

    private JPanel                  gpcPanel        =null;
    private GGideonPanelController  gbpBuddy        =null;
    private boolean                 hasFocus        =false;
    private GFrame                  parentFrame     =null;
    
    public  GGideonPanelController()
    {
            super();
    }
    
    public void setParentFrame( final GFrame pFrame )
    {
        parentFrame = pFrame;
    }
        
    public GFrame getParentFrame()
    {
        return parentFrame;
    }
        
    public  void    setPanel( JPanel thePanel )
    {

        if( thePanel == null )
        {
            throw new NullPointerException();
        }
            
        gpcPanel = thePanel;
    
    }

    public  JPanel  getPanel()
    {
    
        return gpcPanel;
    
    }
    
    public  String  getFolderPath()
    {
        String  dirPath     = null;

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int returnVal = chooser.showOpenDialog((JFrame)getPanel().getTopLevelAncestor());

        if(returnVal == JFileChooser.APPROVE_OPTION)
        {
            File nFile = chooser.getSelectedFile();
            if( nFile != null )
            {
                dirPath = nFile.getAbsolutePath();
            }
        }

        return dirPath;
    }
        
    // Focus API
    public  boolean hasFocus()
    {

        return hasFocus;  

    }

    public void setHasFocus( boolean focused )
    {

        hasFocus = focused;

    }
        
    // Buddy API
    public  void    setBuddy( GGideonPanelController buddy )
    {
    
        gbpBuddy = buddy;
    
    }
    
    public GGideonPanelController getBuddy()
    {
    
        return gbpBuddy;
    
    }

        
}
