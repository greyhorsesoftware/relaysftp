//
//  GAboutBox.java
//  Gideon
//
//  Created by David on 11/11/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.widgets;

import com.gideonsoftworks.gideon.core.GFrame;
import com.gideonsoftworks.gideon.core.GFrameManager;
import com.gideonsoftworks.gideon.core.GLocalized;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class GAboutBox extends GFrame implements ActionListener {

    // Constructor.
    public GAboutBox(GFrameManager fm) {

        super(fm);

        JPanel masterPanel = new JPanel();
        masterPanel.setLayout(new BoxLayout(masterPanel, BoxLayout.Y_AXIS));
        masterPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        // Logo — scale to 128x128
        if (GLocalized.RELAYICON != null) {
            Image scaled = GLocalized.RELAYICON.getImage().getScaledInstance(128, 128, Image.SCALE_SMOOTH);
            JLabel logoLabel = new JLabel(new ImageIcon(scaled));
            logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            masterPanel.add(logoLabel);
            masterPanel.add(Box.createVerticalStrut(12));
        }

        // Title
        JLabel titleLabel = new JLabel("RelaySFTP");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 18f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        masterPanel.add(titleLabel);

        masterPanel.add(Box.createVerticalStrut(4));

        // Version
        JLabel versionLabel = new JLabel(GLocalized.VERSION);
        versionLabel.setFont(versionLabel.getFont().deriveFont(Font.PLAIN, 11f));
        versionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        masterPanel.add(versionLabel);

        masterPanel.add(Box.createVerticalStrut(12));

        // Copyright
        JLabel copyrightLabel = new JLabel(GLocalized.COPYRIGHT);
        copyrightLabel.setFont(copyrightLabel.getFont().deriveFont(Font.PLAIN, 10f));
        copyrightLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        masterPanel.add(copyrightLabel);

        setContentPane(masterPanel);
        pack();
        setLocationRelativeTo(null);
        setResizable(false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
    }
}
