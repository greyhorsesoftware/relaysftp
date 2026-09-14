//
//  GGetInfoPanelController.java
//  Gideon
//
//  Created by David on 10/18/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.getinfo;

import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.core.GGideonPanelController;
import com.gideonsoftworks.gcore.*;
import javax.swing.*;
import java.awt.*;

public class GGetInfoPanelController extends GGideonPanelController  {

    // General field
    private JLabel typeField;
    private JLabel hostNameField;
    private JTextArea parentPathField;
    private JLabel sizeField;
    private JLabel dateModifiedField;
    private JLabel ownerField;
    private JLabel groupField;
    private JTextArea originalField;
    private JLabel typeTitle;
    private JLabel hostTitle;
    private JLabel locationTitle;
    private JLabel sizeTitle;
    private JLabel modifiedTitle;
    private JLabel ownerTitle;
    private JLabel groupTitle;
    private JLabel originalTitle;

    public  GGetInfoPanelController()
    {
        // Build the panel layout by hand (replaces GGetInfoPanel_GeneralTab.gui_xml)
        // Form layout: labels on left, values on right
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 10));
        panel.setPreferredSize(new Dimension(288, 231));
        panel.setMinimumSize(new Dimension(288, 231));

        Font titleFont = new Font("Lucida Grande", Font.BOLD, 11);
        Font valueFont = new Font("Lucida Grande", Font.PLAIN, 11);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 2, 4, 8);
        gbc.anchor = GridBagConstraints.NORTHEAST;

        int row = 0;

        // Type
        typeTitle = new JLabel("Type :");
        typeTitle.setFont(titleFont);
        typeTitle.setHorizontalAlignment(SwingConstants.RIGHT);
        gbc.gridx = 0; gbc.gridy = row;
        panel.add(typeTitle, gbc);

        typeField = new JLabel();
        typeField.setFont(valueFont);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(typeField, gbc);

        // Host
        row++;
        hostTitle = new JLabel("Host :");
        hostTitle.setFont(titleFont);
        hostTitle.setHorizontalAlignment(SwingConstants.RIGHT);
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0; gbc.anchor = GridBagConstraints.NORTHEAST;
        panel.add(hostTitle, gbc);

        hostNameField = new JLabel();
        hostNameField.setFont(valueFont);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(hostNameField, gbc);

        // Location
        row++;
        locationTitle = new JLabel("Location :");
        locationTitle.setFont(titleFont);
        locationTitle.setHorizontalAlignment(SwingConstants.RIGHT);
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0; gbc.anchor = GridBagConstraints.NORTHEAST;
        panel.add(locationTitle, gbc);

        parentPathField = new JTextArea();
        parentPathField.setFont(valueFont);
        parentPathField.setLineWrap(true);
        parentPathField.setOpaque(false);
        parentPathField.setEditable(false);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 0.3;
        panel.add(parentPathField, gbc);
        gbc.weighty = 0;

        // Size
        row++;
        sizeTitle = new JLabel("Size :");
        sizeTitle.setFont(titleFont);
        sizeTitle.setHorizontalAlignment(SwingConstants.RIGHT);
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0; gbc.anchor = GridBagConstraints.NORTHEAST;
        panel.add(sizeTitle, gbc);

        sizeField = new JLabel();
        sizeField.setFont(valueFont);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(sizeField, gbc);

        // Modified
        row++;
        modifiedTitle = new JLabel("Modified :");
        modifiedTitle.setFont(titleFont);
        modifiedTitle.setHorizontalAlignment(SwingConstants.RIGHT);
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0; gbc.anchor = GridBagConstraints.NORTHEAST;
        panel.add(modifiedTitle, gbc);

        dateModifiedField = new JLabel();
        dateModifiedField.setFont(valueFont);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(dateModifiedField, gbc);

        // Owner
        row++;
        ownerTitle = new JLabel("Owner :");
        ownerTitle.setFont(titleFont);
        ownerTitle.setHorizontalAlignment(SwingConstants.RIGHT);
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0; gbc.anchor = GridBagConstraints.NORTHEAST;
        panel.add(ownerTitle, gbc);

        ownerField = new JLabel();
        ownerField.setFont(valueFont);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(ownerField, gbc);

        // Group
        row++;
        groupTitle = new JLabel("Group :");
        groupTitle.setFont(titleFont);
        groupTitle.setHorizontalAlignment(SwingConstants.RIGHT);
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0; gbc.anchor = GridBagConstraints.NORTHEAST;
        panel.add(groupTitle, gbc);

        groupField = new JLabel();
        groupField.setFont(valueFont);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(groupField, gbc);

        // Original
        row++;
        originalTitle = new JLabel("Original :");
        originalTitle.setFont(titleFont);
        originalTitle.setHorizontalAlignment(SwingConstants.RIGHT);
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0; gbc.anchor = GridBagConstraints.NORTHEAST;
        panel.add(originalTitle, gbc);

        originalField = new JTextArea();
        originalField.setFont(valueFont);
        originalField.setEditable(false);
        originalField.setLineWrap(true);
        originalField.setOpaque(false);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 1.0;
        panel.add(originalField, gbc);

        // Localize
        typeTitle.setText(GLocalized.GI_TYPE);
        hostTitle.setText(GLocalized.GI_HOST);
        locationTitle.setText(GLocalized.GI_LOCATION);
        sizeTitle.setText(GLocalized.GI_SIZE);
        modifiedTitle.setText(GLocalized.GI_MODIFIED);
        ownerTitle.setText(GLocalized.GI_OWNER);
        groupTitle.setText(GLocalized.GI_GROUP);
        originalTitle.setText(GLocalized.GI_ORIGINAL);

        this.setPanel( panel );

    }

    public void    populateForFile(GFileItem fi )
    {

        GFileItem   pFI = null;

        String desc = fi.getHumanReadableDescription();

        if( desc != null )
        {

			if( fi.isPackagedFile() )
			{
				typeField.setText(desc + " " + GLocalized.PACKAGETYPE);
			}else{
				typeField.setText(desc);
			}

        }

        String sN = fi.getServerName();

        if( sN != null )
        {
            hostNameField.setText(sN);
        }

        if( (pFI = fi.getParentFileItem()) != null )
        {
            parentPathField.setText(pFI.getCompletePath());
        }else{
            parentPathField.setText("--");
        }

        if( fi.getIsApplication() &&
            GLocalized.onMacOSX )
        {
            sizeField.setText("--");
        }else if( fi.getIsDir() ||
                  fi.getIsFloppy() ||
                  fi.getIsRemovableMedia() ||
                  fi.getIsHardDrive() )
        {
            sizeField.setText("--");
        }else{
            sizeField.setText(fi.getHumanReadableSize());
        }

        java.util.Date dms = fi.getDateModified();

        if( dms != null )
        {
            dateModifiedField.setText(dms.toString());
        }else{
            dateModifiedField.setText("--");
        }

        String own = fi.getOwner();

        if( own != null )
        {
            ownerField.setText(own);
        }else{
            ownerField.setText("--");
        }

        String gro = fi.getGroup();

        if( gro != null )
        {
            groupField.setText(gro);
        }else{
            groupField.setText("--");
        }

        if( fi.getIsLink() )
        {
            originalField.setText(fi.getLinkedName());
        }else{
            originalField.setText("--");
        }

    }


}
