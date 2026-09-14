//
//  GGetInfoPanelTitlePanelController.java
//  Gideon
//
//  Created by David on 10/18/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.getinfo;

import com.gideonsoftworks.gideon.core.GGideonPanelController;
import com.gideonsoftworks.gcore.*;
import javax.swing.*;
import java.awt.*;

public class GGetInfoPanelTitlePanelController extends GGideonPanelController  {

    private JLabel fileNameField;
    private JPanel imageView;
    private JTextArea filePathField;

    public  GGetInfoPanelTitlePanelController()
    {
        // Build the panel layout by hand (replaces GetInfoPanel_NameBanner.gui_xml)
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(1, 10, 0, 0));
        panel.setPreferredSize(new Dimension(320, 77));
        panel.setMinimumSize(new Dimension(320, 77));

        // Icon on the left
        imageView = new JPanel();
        imageView.setOpaque(false);
        imageView.setFocusable(false);
        imageView.setPreferredSize(new Dimension(75, 67));

        // Right side: filename label on top, path area below
        JPanel rightPanel = new JPanel(new BorderLayout(0, 2));

        fileNameField = new JLabel("Filename");
        rightPanel.add(fileNameField, BorderLayout.NORTH);

        filePathField = new JTextArea();
        filePathField.setFont(new Font("Lucida Grande", Font.PLAIN, 11));
        filePathField.setEditable(false);
        filePathField.setLineWrap(true);
        filePathField.setOpaque(false);
        rightPanel.add(filePathField, BorderLayout.CENTER);

        panel.add(imageView, BorderLayout.WEST);
        panel.add(rightPanel, BorderLayout.CENTER);

        this.setPanel( panel );
    }

    public  void    populateForFile( GFileItem fi )
    {

        fileNameField.setText(fi.getName());
        filePathField.setText(fi.getCompletePath());

        ImageIcon iImage = fi.getInfoIcon();
        imageView.add( new JLabel(iImage, SwingConstants.CENTER) );


    }

}
