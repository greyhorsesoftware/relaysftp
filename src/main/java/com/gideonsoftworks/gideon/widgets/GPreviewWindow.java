//
//  GPreviewWindow.java
//  Gideon
//
//  Created by David on 11/12/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.widgets;

import com.gideonsoftworks.gideon.toolbar.GToolbarItem;
import com.gideonsoftworks.gideon.widgets.GImageView;
import com.gideonsoftworks.gideon.toolbar.GToolbar;
import com.gideonsoftworks.gideon.core.GFrame;
import com.gideonsoftworks.gideon.core.GFrameManager;
import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.actions.GUIAction;
import com.gideonsoftworks.gideon.browser.GBrowserPanelCommandInterface;
import com.gideonsoftworks.gcore.*;
import com.gideonsoftworks.tabkit.GTabBarItem;
import javax.swing.*;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.util.HashMap;
import javax.imageio.*;

public class GPreviewWindow extends GFrame implements ActionListener
{
    
    GImageView                      gpwImageView    = null;
    GTemporaryFileItem              gpwTempFile     = null;
    GToolbarItem                    saTBI           = null;
    GFileItem                       gpwFile         = null;
    GBrowserPanelCommandInterface   gpwBrowser      = null;
    Image                           gpwImg          = null;
    
    public  GPreviewWindow( GFrameManager fm, GBrowserPanelCommandInterface browser )
    {

        super( fm );
        
        int lowerGap    = 0;

        gpwBrowser = browser;

    }
    
    /*
     * 
     * GFrame overrides...
     *
     */

    public HashMap getFrameSettings()
    {
        HashMap settings = new HashMap();
        settings.put(GFrame.MINIMUM_WIDTH, Integer.valueOf(500) );
        settings.put(GFrame.MINIMUM_HEIGHT, Integer.valueOf(400) );
        return settings;
    }

    public JComponent getContentView( final GTabBarItem tbi )
    {
        int lowerGap = 0;

        JPanel  masterPanel = new JPanel(new BorderLayout(0,0));

        // XXX - this should not be here - platform specific 
        if( GLocalized.onMacOSX )
        {
            lowerGap = 10;
        }

        masterPanel.setBorder(BorderFactory.createEmptyBorder(5,5,lowerGap,5));

        gpwImageView=new GImageView();
		masterPanel.add( gpwImageView, BorderLayout.CENTER );

        return masterPanel;
    }

    public  boolean    windowWillClose()
    {
    
        // delete the temp file
        if( gpwTempFile != null ) 
        {
            gpwTempFile.delete();
        }
        
        return true;
        
    }

    /*
     *
     * End GFrame overrides...
     *
     */

    public void setFileItem( GFileItem fi )
    {
    
        gpwFile = fi;
    
    }
    
    public GFileItem    getFileItem()
    {
    
        return gpwFile;
    
    }
    
    /**
     * Preview a local file. Remote files are downloaded first by
     * GPreviewUIAction (on the browser's job manager, so the transfer shows in
     * the Activity panel) and arrive here via showDownloaded().
     */
    public  void    show( GFileItem fItem )
    {

        setFileItem( fItem );

        super.show();

        setTitle(GLocalized.PREVIEWING + " \"" + fItem.getName() + "\"");

        previewFileItem( fItem );

    }

    /**
     * Show a remote file whose download has already completed.
     * Takes ownership of the temp file — it is deleted when the window closes.
     */
    public  void    showDownloaded( GFileItem fItem, GTemporaryFileItem tempFile )
    {

        setFileItem( fItem );

        gpwTempFile = tempFile;

        super.show();

        setTitle(GLocalized.PREVIEWING + " \"" + fItem.getName() + "\"");

        previewFileItem( tempFile );

    }

    private void    previewFileItem( GFileItem fItem )
    {
        try {
            BufferedImage bImg = ImageIO.read(new File(fItem.getCompletePath()));
            if( bImg != null )
            {
                gpwImg = bImg;
                gpwImageView.setImage(gpwImg);
                gpwImageView.update();

                int imgW = bImg.getWidth();
                int imgH = bImg.getHeight();
                // cap at 800x800, keep at least minimum
                int winW = Math.max(500, Math.min(imgW + 20, 800));
                int winH = Math.max(400, Math.min(imgH + 100, 800));
                setSize(winW, winH);
            }
        } catch( IOException e ) {
            System.err.println("Error loading image: " + e.getMessage());
        }

    }

    // status panel controll calls this after image has loaded
    public void imageLoaded()
    {
    }

    public GToolbar   getToolbar()
    {

        GToolbar toolBar = new GToolbar("Gideon TB");

        GUIAction action = new GUIAction(GLocalized.SAVELOCALCOPY,
            new GDownloadCircleIcon(18), GLocalized.SAVEACOPY_TOOLTIP);
        saTBI = new GToolbarItem(action);

        // pin the save button to the right-hand side
        toolBar.add(Box.createHorizontalGlue());
        toolBar.add(saTBI);

        saTBI.addActionListener(this);

        return toolBar;

    }
    
    public void actionPerformed( ActionEvent e )
    {

        GToolbarItem tbi = (GToolbarItem)e.getSource();

        if( !tbi.equals(saTBI) ) return;
        if( gpwFile == null ) return;

        // the on-disk copy of what we are previewing (temp for remote files)
        File sourceFile = new File( gpwTempFile != null
            ? gpwTempFile.getCompletePath() : gpwFile.getCompletePath() );

        File saveLocation = getSavePath( GLocalized.SAVELOCALCOPY, gpwFile.getName() );
        if( saveLocation == null ) return;

        try{
            String targetExt = extensionOf(saveLocation.getName());
            String sourceExt = extensionOf(gpwFile.getName());

            if( targetExt == null || targetExt.equalsIgnoreCase(sourceExt) )
            {
                // same format — copy the original bytes, no re-encode
                java.nio.file.Files.copy( sourceFile.toPath(), saveLocation.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING );
            }else{
                // different extension typed — transcode to the requested format
                BufferedImage src = ImageIO.read(sourceFile);
                if( src == null ) throw new IOException("Could not read image.");

                String format = targetExt.toLowerCase();
                BufferedImage out = src;
                if( format.equals("jpg") || format.equals("jpeg") || format.equals("bmp") )
                {
                    // formats without alpha — flatten onto white
                    out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
                    Graphics2D g2 = out.createGraphics();
                    g2.setColor(Color.WHITE);
                    g2.fillRect(0, 0, src.getWidth(), src.getHeight());
                    g2.drawImage(src, 0, 0, null);
                    g2.dispose();
                }

                if( !ImageIO.write(out, format, saveLocation) )
                {
                    throw new IOException("Unsupported image format: " + targetExt);
                }
            }
        }catch(IOException ioe)
        {
            JOptionPane.showMessageDialog(this, ioe.getMessage(),
                "Save Failed", JOptionPane.ERROR_MESSAGE);
        }

    }

    private static String extensionOf( String name )
    {
        int dot = name.lastIndexOf('.');
        return ( dot > 0 && dot < name.length() - 1 ) ? name.substring(dot + 1) : null;
    }
    
}
