//
//  GBookmarkGeneralPanelController.java
//  Gideon
//
//  Created by David on 10/18/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.bookmarks;

import com.gideonsoftworks.gideon.core.GFrame;
import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.core.GGideonPanelController;
import com.gideonsoftworks.gideon.core.GConnectionPanelInterface;
import com.gideonsoftworks.gideon.core.GFTPConnectionPanel;
import com.gideonsoftworks.gideon.core.GS3ConnectionPanel;
import com.gideonsoftworks.gcore.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class GBookmarkGeneralPanelController extends GGideonPanelController implements ActionListener, GBookmarkPanelInterface {

    private JComboBox       protocolCombo;
    private JTextField      localHomeField;
    private JButton         setLocalHome;
    private GFrame          bmWindow;

    private CardLayout      connectionCardLayout;
    private JPanel          connectionCardPanel;
    private Map<String, GConnectionPanelInterface> connectionPanels = new LinkedHashMap<>();

    private GBookmark   bmgCurrentBookmark = null;

    public  GBookmarkGeneralPanelController()
    {

        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(413, 355));
        panel.setMinimumSize(new Dimension(413, 355));

        // Top: Protocol selector — centered, rounded
        JPanel protocolPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        protocolPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        protocolCombo = new JComboBox();
        protocolCombo.putClientProperty("JComponent.roundRect", true);
        protocolCombo.addItem(GLocalized.FTP);
        protocolCombo.addItem(GLocalized.SFTP);
        protocolCombo.addItem(GLocalized.FTPS);
        protocolCombo.addItem(GLocalized.S3);
        protocolCombo.setSelectedIndex(0);
        protocolPanel.add(protocolCombo);
        panel.add(protocolPanel, BorderLayout.NORTH);

        // Center: CardLayout with protocol-specific panels
        connectionCardLayout = new CardLayout();
        connectionCardPanel = new JPanel(connectionCardLayout);
        connectionCardPanel.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));

        GFTPConnectionPanel ftpPanel = new GFTPConnectionPanel(GBookmark.G_FTP_PROTOCOL);
        GFTPConnectionPanel sftpPanel = new GFTPConnectionPanel(GBookmark.G_SFTP_PROTOCOL);
        GFTPConnectionPanel ftpsPanel = new GFTPConnectionPanel(GBookmark.G_FTPSSL_PROTOCOL);
        GS3ConnectionPanel s3Panel = new GS3ConnectionPanel();

        connectionPanels.put("FTP", ftpPanel);
        connectionPanels.put("SFTP", sftpPanel);
        connectionPanels.put("FTPS", ftpsPanel);
        connectionPanels.put("S3", s3Panel);

        connectionCardPanel.add(ftpPanel.getPanel(), "FTP");
        connectionCardPanel.add(sftpPanel.getPanel(), "SFTP");
        connectionCardPanel.add(ftpsPanel.getPanel(), "FTPS");
        connectionCardPanel.add(s3Panel.getPanel(), "S3");

        panel.add(connectionCardPanel, BorderLayout.CENTER);

        // Swap cards when protocol changes
        protocolCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                connectionCardLayout.show(connectionCardPanel, protocolComboToCardName());
            }
        });

        // Bottom: Local Home fields
        JPanel localPanel = new JPanel(new GridBagLayout());
        localPanel.setBorder(BorderFactory.createEmptyBorder(0, 6, 6, 6));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);

        JLabel localFolderLabel = new JLabel(GLocalized.BM_LOCALFOLDER);
        localFolderLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        localPanel.add(localFolderLabel, gbc);

        localHomeField = new JTextField();
        localHomeField.setEditable(false);
        localHomeField.setEnabled(false);
        gbc.gridx = 1; gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        localPanel.add(localHomeField, gbc);

        setLocalHome = new JButton(GLocalized.BM_SETLOCALHOME);
        gbc.gridx = 1; gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        localPanel.add(setLocalHome, gbc);

        panel.add(localPanel, BorderLayout.SOUTH);

        setLocalHome.addActionListener(this);

        this.setPanel(panel);

    }

    public void setBookmarkWindow( GFrame window )
    {

        bmWindow = window;

    }

    public void actionPerformed( ActionEvent e )
    {
        String  dirPath = null;

        JButton button = (JButton)e.getSource();

        if( button.getText().compareTo(GLocalized.CLEARHOMEPATH) == 0 )
        {
            bmgCurrentBookmark.setLocalHomePath(null);
            button.setText(GLocalized.SETHOMEPATH);
            localHomeField.setText("");

        }else{

            dirPath = getFolderPath();

            if( dirPath != null )
            {
                bmgCurrentBookmark.setLocalHomePath(dirPath);
                button.setText(GLocalized.CLEARHOMEPATH);
                localHomeField.setText(dirPath);
            }

        }

    }

    public  void    saveCurrentBookmark()
    {
        if( bmgCurrentBookmark == null ) return;

        // Delegate to active connection panel
        GConnectionPanelInterface activePanel = connectionPanels.get(protocolComboToCardName());
        if (activePanel != null) {
            activePanel.applyToBookmark(bmgCurrentBookmark);
        }
    }

    private void    clearFields( boolean enabled )
    {
        protocolCombo.setSelectedIndex(0);
        protocolCombo.setEnabled(enabled);
        localHomeField.setText( "" );
        localHomeField.setEnabled(enabled);
        setLocalHome.setEnabled(enabled);
    }

    public void populateForBookmark(GBookmark bm)
    {

        if( bm == null )
        {
            clearFields(false);
            bmgCurrentBookmark=null;
            return;
        }

        clearFields( true );

        // Set protocol combo to match bookmark protocol
        int proto = bm.getProtocol();
        switch (proto) {
            case GBookmark.G_FTP_PROTOCOL:      protocolCombo.setSelectedIndex(0); break;
            case GBookmark.G_SFTP_PROTOCOL:     protocolCombo.setSelectedIndex(1); break;
            case GBookmark.G_FTPSSL_PROTOCOL:   protocolCombo.setSelectedIndex(2); break;
            case GBookmark.G_S3_PROTOCOL:       protocolCombo.setSelectedIndex(3); break;
            default:                            protocolCombo.setSelectedIndex(0); break;
        }

        // Show correct card and populate
        String card = protocolComboToCardName();
        connectionCardLayout.show(connectionCardPanel, card);
        GConnectionPanelInterface activePanel = connectionPanels.get(card);
        if (activePanel != null) {
            activePanel.populateFromBookmark(bm);
        }

        // Local home
        String localHostHome = bm.getLocalHomePath();
        if( localHostHome != null )
        {
            localHomeField.setText( localHostHome );
            setLocalHome.setText(GLocalized.CLEARHOMEPATH);
        }

        bmgCurrentBookmark = bm;

    }

    private String protocolComboToCardName() {
        int idx = protocolCombo.getSelectedIndex();
        switch (idx) {
            case 0: return "FTP";
            case 1: return "SFTP";
            case 2: return "FTPS";
            case 3: return "S3";
            default: return "FTP";
        }
    }

}
