package com.gideonsoftworks.gideon.preferences;

import com.gideonsoftworks.gideon.core.GGideonPanelController;
import com.gideonsoftworks.gideon.core.GLocalized;

import javax.swing.*;
import java.awt.*;

public class GSFTPPreferencePanelController extends GGideonPanelController {

    private JCheckBox sftpDefault;
    private JCheckBox useExec;
    private JCheckBox monitorRemote;

    public GSFTPPreferencePanelController() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 15, 10, 15));
        panel.setPreferredSize(new Dimension(420, 150));

        JPanel checkboxPanel = new JPanel();
        checkboxPanel.setLayout(new BoxLayout(checkboxPanel, BoxLayout.Y_AXIS));

        sftpDefault = new JCheckBox(GLocalized.PG_SFTPDEFAULT);
        useExec = new JCheckBox("Use faster methods where available");
        useExec.setToolTipText("Enables fast delete and fast listing via SSH exec channel");

        monitorRemote = new JCheckBox("Monitor remote directory for changes");
        monitorRemote.setToolTipText("Automatically refresh the remote pane when the current folder changes on the server");

        checkboxPanel.add(sftpDefault);
        checkboxPanel.add(Box.createVerticalStrut(2));
        checkboxPanel.add(useExec);
        checkboxPanel.add(Box.createVerticalStrut(2));
        checkboxPanel.add(monitorRemote);

        panel.add(checkboxPanel, BorderLayout.NORTH);

        setPanel(panel);
    }

    public void open() {
        sftpDefault.setSelected(GSFTPPreferences.defaultToSFTP());
        useExec.setSelected(GSFTPPreferences.useExec());
        monitorRemote.setSelected(GSFTPPreferences.monitorRemoteChangesOn());
    }

    public void save() {
        GSFTPPreferences.setDefaultToSFTP(sftpDefault.isSelected());
        GSFTPPreferences.setUseExec(useExec.isSelected());
        GSFTPPreferences.setMonitorRemoteChangesOn(monitorRemote.isSelected());
    }
}
