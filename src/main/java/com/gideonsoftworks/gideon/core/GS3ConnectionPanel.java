package com.gideonsoftworks.gideon.core;

import com.gideonsoftworks.gcore.GBookmark;
import com.gideonsoftworks.gcore.GBookmarkManager;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;

public class GS3ConnectionPanel implements GConnectionPanelInterface {

    // Auth method option labels (values, not localized — like PROVIDER_NAMES).
    private static final String AUTH_BASIC     = "Access Key + Secret";
    private static final String AUTH_TEMPORARY = "Temporary Credentials (Session Token)";

    private final JPanel panel;
    private final JComboBox providerCombo;
    private JComboBox authMethodCombo;
    private final JTextField bucketField;
    private final JTextField accessKeyField;
    private final JPasswordField secretKeyField;
    private final JLabel sessionTokenLabel;
    private final JPasswordField sessionTokenField;
    private final JTextField regionField;
    private final JTextField endpointField;
    private final JTextField initialPathField;

    private boolean populatingFromBookmark = false;

    private static final String[] PROVIDER_NAMES = {
        "Amazon S3",
        "Backblaze B2",
        "Wasabi",
        "DigitalOcean Spaces",
        "Cloudflare R2",
        "MinIO",
        "Custom"
    };

    private static final String[] PROVIDER_ENDPOINTS = {
        "",                                                     // Amazon S3
        "https://s3.us-west-004.backblazeb2.com",              // Backblaze B2
        "https://s3.us-east-1.wasabisys.com",                  // Wasabi
        "https://nyc3.digitaloceanspaces.com",                  // DigitalOcean Spaces
        "https://<account_id>.r2.cloudflarestorage.com",        // Cloudflare R2
        "http://localhost:9000",                                // MinIO
        null                                                    // Custom
    };

    private static final String[] PROVIDER_REGIONS = {
        "us-east-1",       // Amazon S3
        "us-west-004",     // Backblaze B2
        "us-east-1",       // Wasabi
        "us-east-1",       // DigitalOcean Spaces (SDK region must be us-east-1)
        "auto",            // Cloudflare R2
        "us-east-1",       // MinIO
        null               // Custom
    };

    public GS3ConnectionPanel() {
        panel = new JPanel(new BorderLayout());

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        int row = 0;

        // Row: Provider
        providerCombo = new JComboBox(PROVIDER_NAMES);
        providerCombo.setSelectedIndex(0);
        addRow(form, gbc, row++, GLocalized.LP_PROVIDER, providerCombo);
        providerCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED && !populatingFromBookmark) {
                applyProviderPreset(providerCombo.getSelectedIndex());
                updateAuthMethods(providerCombo.getSelectedIndex(),
                    (String) authMethodCombo.getSelectedItem());
            }
        });

        // Row: Bucket
        bucketField = new JTextField();
        addRow(form, gbc, row++, GLocalized.LP_BUCKET, bucketField);

        // Row: Auth Method
        authMethodCombo = new JComboBox(new String[] { AUTH_BASIC });
        addRow(form, gbc, row++, GLocalized.LP_AUTHMETHOD, authMethodCombo);
        authMethodCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) updateSessionTokenVisibility();
        });

        // Row: Access Key
        accessKeyField = new JTextField();
        addRow(form, gbc, row++, GLocalized.LP_ACCESSKEY, accessKeyField);

        // Row: Secret Key
        secretKeyField = new JPasswordField();
        addRow(form, gbc, row++, GLocalized.LP_SECRETKEY, secretKeyField);

        // Row: Session Token (hidden unless Temporary Credentials selected)
        sessionTokenLabel = new JLabel(GLocalized.LP_SESSIONTOKEN);
        sessionTokenLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        sessionTokenField = new JPasswordField();
        addRow(form, gbc, row++, sessionTokenLabel, sessionTokenField);

        // Row: Region
        regionField = new JTextField();
        addRow(form, gbc, row++, GLocalized.LP_REGION, regionField);

        // Row: Endpoint URL
        endpointField = new JTextField();
        addRow(form, gbc, row++, GLocalized.LP_ENDPOINT, endpointField);

        // Row: Remote Path (bucket prefix)
        initialPathField = new JTextField();
        initialPathField.setToolTipText("The bucket path/prefix this bookmark opens. Use different remote paths to keep multiple bookmarks for the same host distinct.");
        addRow(form, gbc, row++, GLocalized.LP_REMOTEPATH, initialPathField);

        panel.add(form, BorderLayout.NORTH);

        // Initialize for the default provider (Amazon S3 → Temporary available, but default basic).
        updateAuthMethods(0, AUTH_BASIC);
    }

    private void addRow(JPanel form, GridBagConstraints gbc, int row, String labelText, JComponent field) {
        JLabel label = new JLabel(labelText);
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        addRow(form, gbc, row, label, field);
    }

    private void addRow(JPanel form, GridBagConstraints gbc, int row, JLabel label, JComponent field) {
        gbc.gridx = 0; gbc.gridy = row;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        form.add(label, gbc);

        gbc.gridx = 1; gbc.gridy = row;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        form.add(field, gbc);
    }

    private void applyProviderPreset(int index) {
        if (index < 0 || index >= PROVIDER_ENDPOINTS.length) return;
        String endpoint = PROVIDER_ENDPOINTS[index];
        String region = PROVIDER_REGIONS[index];
        if (endpoint != null) endpointField.setText(endpoint);
        if (region != null) regionField.setText(region);
    }

    /** Session tokens are AWS-only — offer Temporary Credentials only for Amazon S3 / Custom. */
    private void updateAuthMethods(int providerIndex, String preferred) {
        boolean awsCapable = (providerIndex == 0 || providerIndex == PROVIDER_NAMES.length - 1);

        authMethodCombo.removeAllItems();
        authMethodCombo.addItem(AUTH_BASIC);
        if (awsCapable) authMethodCombo.addItem(AUTH_TEMPORARY);

        authMethodCombo.setSelectedItem(
            (awsCapable && AUTH_TEMPORARY.equals(preferred)) ? AUTH_TEMPORARY : AUTH_BASIC);
        updateSessionTokenVisibility();
    }

    private void updateSessionTokenVisibility() {
        boolean show = AUTH_TEMPORARY.equals(authMethodCombo.getSelectedItem());
        sessionTokenLabel.setVisible(show);
        sessionTokenField.setVisible(show);
        if (!show) sessionTokenField.setText("");
        panel.revalidate();
        panel.repaint();
    }

    private int detectProviderFromEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) return 0; // Amazon S3
        String lower = endpoint.toLowerCase();
        if (lower.contains("backblazeb2.com")) return 1;
        if (lower.contains("wasabisys.com")) return 2;
        if (lower.contains("digitaloceanspaces.com")) return 3;
        if (lower.contains("r2.cloudflarestorage.com")) return 4;
        if (lower.contains("localhost") || lower.contains(":9000")) return 5;
        return 6; // Custom
    }

    @Override
    public JPanel getPanel() { return panel; }

    @Override
    public int getProtocolConstant() { return GBookmark.G_S3_PROTOCOL; }

    @Override
    public String getCardName() { return "S3"; }

    @Override
    public void populateFromBookmark(GBookmark bm) {
        if (bm == null) return;

        populatingFromBookmark = true;

        bucketField.setText(bm.getHostname() != null ? bm.getHostname() : "");
        accessKeyField.setText(bm.getS3AccessKey() != null ? bm.getS3AccessKey() : "");
        String sk = GBookmarkManager.getPassword(bm);
        secretKeyField.setText(sk != null ? sk : "");
        regionField.setText(bm.getS3Region() != null ? bm.getS3Region() : "");
        endpointField.setText(bm.getS3EndpointUrl() != null ? bm.getS3EndpointUrl() : "");
        initialPathField.setText(bm.getHomePath() != null ? bm.getHomePath() : "");

        int providerIndex = detectProviderFromEndpoint(bm.getS3EndpointUrl());
        providerCombo.setSelectedIndex(providerIndex);

        // Restore the auth method (session tokens themselves are not persisted).
        String preferred = "session".equals(bm.getS3AuthMethod()) ? AUTH_TEMPORARY : AUTH_BASIC;
        updateAuthMethods(providerIndex, preferred);
        sessionTokenField.setText(bm.getS3SessionToken() != null ? bm.getS3SessionToken() : "");

        populatingFromBookmark = false;
    }

    @Override
    public void applyToBookmark(GBookmark bm) {
        String bucket = bucketField.getText();
        String accessKey = accessKeyField.getText();
        String secretKey = new String(secretKeyField.getPassword());
        String region = regionField.getText();
        String endpoint = endpointField.getText();
        String initialPath = initialPathField.getText();
        boolean temporary = AUTH_TEMPORARY.equals(authMethodCombo.getSelectedItem());
        String token = new String(sessionTokenField.getPassword());

        bm.setHostname(bucket.isEmpty() ? null : bucket);
        bm.setS3AccessKey(accessKey.isEmpty() ? null : accessKey);
        GBookmarkManager.setPassword(bm, secretKey.isEmpty() ? null : secretKey);
        bm.setS3Region(region.isEmpty() ? null : region);
        bm.setS3EndpointUrl(endpoint.isEmpty() ? null : endpoint);
        bm.setHomePath(initialPath.isEmpty() ? null : initialPath);
        bm.setS3AuthMethod(temporary ? "session" : "basic");
        bm.setS3SessionToken(temporary && !token.isEmpty() ? token : null);
        bm.setProtocol(GBookmark.G_S3_PROTOCOL);
    }
}
