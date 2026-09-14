//
//  GGeneralPreferencePanelController.java
//  Gideon
//
//  Created by David on 11/17/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.preferences;

import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.core.GGideonPanelController;
import javax.swing.*;

import java.awt.*;
import java.awt.event.*;


public class GGeneralPreferencePanelController extends GGideonPanelController implements ActionListener
{
    // Declare beans.
    private JButton choose;
    private JCheckBox showDotFiles;
    private JTextField bean2;

    public GGeneralPreferencePanelController()
    {

        // Build the panel layout by hand (replaces GGeneralPreferencePanel.gui_xml)
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 15, 10, 15));
        panel.setPreferredSize(new Dimension(420, 170));

        // Checkboxes panel (top)
        JPanel checkboxPanel = new JPanel();
        checkboxPanel.setLayout(new BoxLayout(checkboxPanel, BoxLayout.Y_AXIS));

        showDotFiles = new JCheckBox("Show files starting with \".\"");
        checkboxPanel.add(showDotFiles);

        // Downloads Folder group box
        JPanel defaultHomeBox = new JPanel(new BorderLayout(0, 4));
        defaultHomeBox.setBorder(BorderFactory.createTitledBorder(GLocalized.PG_DEFAULTLOCALHOME));

        bean2 = new JTextField();
        bean2.setEditable(false);
        bean2.setFocusable(false);

        choose = new JButton(GLocalized.BM_SETLOCALHOME);

        JPanel browseRow = new JPanel(new BorderLayout(8, 0));
        browseRow.setBorder(BorderFactory.createEmptyBorder(4, 10, 8, 10));
        browseRow.add(bean2, BorderLayout.CENTER);
        browseRow.add(choose, BorderLayout.EAST);

        // NORTH (not CENTER) so the field/button keep their natural height instead of expanding to fill.
        defaultHomeBox.add(browseRow, BorderLayout.NORTH);

        // Stack the checkboxes + downloads box at the top; CENTER absorbs the slack.
        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        checkboxPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        defaultHomeBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(checkboxPanel);
        north.add(Box.createVerticalStrut(10));
        north.add(defaultHomeBox);

        panel.add(north, BorderLayout.NORTH);

        // wire us up
        choose.addActionListener(this);

        setPanel(panel);

    }

    public void open()
    {

        bean2.setText(GSFTPPreferences.defaultLocalHomeFolder());
        showDotFiles.setSelected(GSFTPPreferences.showDotFiles());

    }

    public void save()
    {

        GSFTPPreferences.setShowDotFiles(showDotFiles.isSelected());

        // save the path
        String path = bean2.getText();

        if( path.length()!=0 )
        {
            GSFTPPreferences.setDefaultLocalHomeFolder(path);
        }

    }

    public void actionPerformed(ActionEvent e )
    {

        String dirPath = getFolderPath();

        if( dirPath != null )
        {
            bean2.setText(dirPath);
        }

    }

}
