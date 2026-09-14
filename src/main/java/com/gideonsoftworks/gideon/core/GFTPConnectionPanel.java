package com.gideonsoftworks.gideon.core;

import com.gideonsoftworks.gcore.GBookmark;
import com.gideonsoftworks.gcore.GBookmarkManager;
import javax.swing.*;
import java.awt.*;

public class GFTPConnectionPanel implements GConnectionPanelInterface {

    private final int protocol;
    private final JPanel panel;
    private final JTextField hostnameField;
    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JTextField folderField;
    private final JTextField portField;

    public GFTPConnectionPanel(int protocol) {
        this.protocol = protocol;

        panel = new JPanel(new BorderLayout());

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        // Row 0: Hostname
        JLabel hostnameLabel = new JLabel(GLocalized.LP_HOSTNAME);
        hostnameLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        form.add(hostnameLabel, gbc);

        hostnameField = new JTextField();
        hostnameField.setName("hostnameField");
        gbc.gridx = 1; gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        form.add(hostnameField, gbc);

        // Row 1: Username
        JLabel usernameLabel = new JLabel(GLocalized.LP_USERNAME);
        usernameLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        form.add(usernameLabel, gbc);

        usernameField = new JTextField();
        usernameField.setName("usernameField");
        gbc.gridx = 1; gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        form.add(usernameField, gbc);

        // Row 2: Password
        JLabel passwordLabel = new JLabel(GLocalized.LP_PASSWORD);
        passwordLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        form.add(passwordLabel, gbc);

        passwordField = new JPasswordField();
        passwordField.setName("passwordField");
        gbc.gridx = 1; gbc.gridy = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        form.add(passwordField, gbc);

        // Row 3: Remote Path
        JLabel folderLabel = new JLabel(GLocalized.LP_REMOTEPATH);
        folderLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        form.add(folderLabel, gbc);

        folderField = new JTextField();
        folderField.setName("folderField");
        folderField.setToolTipText("The folder this bookmark opens. Use different remote paths to keep multiple bookmarks for the same host distinct.");
        gbc.gridx = 1; gbc.gridy = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        form.add(folderField, gbc);

        // Row 4: Port
        JLabel portLabel = new JLabel(GLocalized.LP_PORT);
        portLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        gbc.gridx = 0; gbc.gridy = 4;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        form.add(portLabel, gbc);

        portField = new JTextField();
        portField.setName("portField");
        portField.setPreferredSize(new Dimension(66, portField.getPreferredSize().height));
        gbc.gridx = 1; gbc.gridy = 4;
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        form.add(portField, gbc);

        panel.add(form, BorderLayout.NORTH);
    }

    @Override
    public JPanel getPanel() { return panel; }

    @Override
    public int getProtocolConstant() { return protocol; }

    @Override
    public String getCardName() {
        switch (protocol) {
            case GBookmark.G_SFTP_PROTOCOL: return "SFTP";
            case GBookmark.G_FTPSSL_PROTOCOL: return "FTPS";
            default: return "FTP";
        }
    }

    @Override
    public void populateFromBookmark(GBookmark bm) {
        if (bm == null) return;

        hostnameField.setText(bm.getHostname() != null ? bm.getHostname() : "");
        usernameField.setText(bm.getUsername() != null ? bm.getUsername() : "");
        String pw = GBookmarkManager.getPassword(bm);
        passwordField.setText(pw != null ? pw : "");
        folderField.setText(bm.getHomePath() != null ? bm.getHomePath() : "");
        portField.setText(bm.getPort() != 0 ? Integer.toString(bm.getPort()) : "");
    }

    @Override
    public void applyToBookmark(GBookmark bm) {
        String hn = hostnameField.getText();
        String un = usernameField.getText();
        String pw = new String(passwordField.getPassword());
        String folder = folderField.getText();
        String pString = portField.getText();

        // Support user@host shorthand
        if (hn.contains("@")) {
            String[] parts = hn.split("@", 2);
            if (parts.length == 2) {
                un = parts[0];
                hn = parts[1];
            }
        }

        bm.setHostname(hn.isEmpty() ? null : hn);
        bm.setUsername(un.isEmpty() ? null : un);
        GBookmarkManager.setPassword(bm, pw.isEmpty() ? null : pw);
        bm.setHomePath(folder.isEmpty() ? null : folder);
        bm.setPort(pString.isEmpty() ? 0 : Integer.parseInt(pString));
        bm.setProtocol(protocol);
    }

}
