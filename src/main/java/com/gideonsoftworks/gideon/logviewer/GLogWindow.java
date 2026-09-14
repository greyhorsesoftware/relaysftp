package com.gideonsoftworks.gideon.logviewer;

import com.gideonsoftworks.gcore.GConnection;
import com.gideonsoftworks.gcore.GConnectionStats;
import com.gideonsoftworks.gcore.GLog;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Window with a tab per connection. Each tab has connection stats
 * on the left and a live log on the right.
 */
public class GLogWindow extends JFrame implements GLogManager.Listener {

    private static GLogWindow instance;

    private final JTabbedPane tabbedPane;
    private final Map<String, ConnectionTab> tabs = new LinkedHashMap<>();
    private Timer refreshTimer;

    private GLogWindow() {
        super("Connections");
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setSize(750, 400);

        tabbedPane = new JTabbedPane(SwingConstants.TOP);
        getContentPane().add(tabbedPane, BorderLayout.CENTER);

        // Add tabs for any logs already registered
        for (Map.Entry<String, GLog> entry : GLogManager.shared().getLogs().entrySet()) {
            logAdded(entry.getKey(), entry.getValue());
        }

        GLogManager.shared().addListener(this);

        refreshTimer = new Timer(2000, e -> refreshStats());
        refreshTimer.setRepeats(true);
        // #5: started/stopped with visibility (see setVisible) — no perpetual 2s EDT wakeups while hidden.
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) { if (!refreshTimer.isRunning()) refreshTimer.start(); }
        else         { refreshTimer.stop(); }
        super.setVisible(visible);
    }

    public static GLogWindow shared() {
        if (instance == null) {
            instance = new GLogWindow();
        }
        return instance;
    }

    public static void toggle() {
        GLogWindow w = shared();
        w.setVisible(!w.isVisible());
        if (w.isVisible()) w.toFront();
    }

    /** Show the window and select the tab for the given log key. */
    public static void showForLog(String key) {
        GLogWindow w = shared();
        w.setVisible(true);
        w.toFront();
        ConnectionTab tab = w.tabs.get(key);
        if (tab != null) {
            int index = w.tabbedPane.indexOfComponent(tab.splitPane);
            if (index >= 0) w.tabbedPane.setSelectedIndex(index);
        }
    }

    /** Register a connection for stats display on an existing or new tab. */
    public static void registerConnection(String key, GConnection conn) {
        GLogWindow w = shared();
        SwingUtilities.invokeLater(() -> {
            ConnectionTab tab = w.tabs.get(key);
            if (tab != null) {
                tab.connection = conn;
                tab.refreshStats();
            }
            // If no tab yet, it will be created when logAdded fires
        });
    }

    @Override
    public void logAdded(String key, GLog log) {
        SwingUtilities.invokeLater(() -> {
            if (tabs.containsKey(key)) {
                tabs.get(key).logPanel.setLog(log);
                tabs.get(key).logPanel.startMonitoring();
                return;
            }

            ConnectionTab tab = new ConnectionTab(log);
            tabs.put(key, tab);
            tabbedPane.addTab(key, tab.splitPane);
            tabbedPane.setSelectedIndex(tabbedPane.getTabCount() - 1);
        });
    }

    private void refreshStats() {
        for (ConnectionTab tab : tabs.values()) {
            tab.refreshStats();
        }
    }

    // --- Inner class: one tab per connection ---

    private static class ConnectionTab {
        GConnection connection;
        GLogPanelController logPanel;
        JSplitPane splitPane;

        // Stats labels
        JLabel protocolLabel;
        JLabel hostnameLabel;
        JLabel portLabel;
        JLabel connectedLabel;
        JLabel warpListLabel;
        JLabel mtimeLabel;
        JLabel lastMtimeLabel;

        ConnectionTab(GLog log) {
            logPanel = new GLogPanelController(log);
            logPanel.startMonitoring();

            // Stats panel (left)
            JPanel statsPanel = buildStatsPanel();

            // Log panel (right)
            JPanel logWrapper = logPanel.getPanel();

            splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, statsPanel, logWrapper);
            splitPane.setDividerLocation(200);
            splitPane.setContinuousLayout(true);
            splitPane.setBorder(BorderFactory.createEmptyBorder());
        }

        private JPanel buildStatsPanel() {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBorder(new EmptyBorder(12, 12, 12, 8));
            panel.setMinimumSize(new Dimension(180, 0));

            GridBagConstraints lbl = new GridBagConstraints();
            lbl.gridx = 0; lbl.anchor = GridBagConstraints.EAST;
            lbl.insets = new Insets(2, 0, 2, 6);

            GridBagConstraints val = new GridBagConstraints();
            val.gridx = 1; val.anchor = GridBagConstraints.WEST;
            val.fill = GridBagConstraints.HORIZONTAL;
            val.weightx = 1.0;
            val.insets = new Insets(2, 0, 2, 0);

            int row = 0;

            lbl.gridy = row; val.gridy = row;
            panel.add(label("Protocol"), lbl);
            protocolLabel = value("—"); panel.add(protocolLabel, val);

            row++; lbl.gridy = row; val.gridy = row;
            panel.add(label("Host"), lbl);
            hostnameLabel = value("—"); panel.add(hostnameLabel, val);

            row++; lbl.gridy = row; val.gridy = row;
            panel.add(label("Port"), lbl);
            portLabel = value("—"); panel.add(portLabel, val);

            row++; lbl.gridy = row; val.gridy = row;
            panel.add(label("Uptime"), lbl);
            connectedLabel = value("—"); panel.add(connectedLabel, val);

            // Separator
            row++;
            JSeparator sep = new JSeparator();
            GridBagConstraints sepC = new GridBagConstraints();
            sepC.gridx = 0; sepC.gridy = row; sepC.gridwidth = 2;
            sepC.fill = GridBagConstraints.HORIZONTAL;
            sepC.insets = new Insets(6, 0, 6, 0);
            panel.add(sep, sepC);

            row++; lbl.gridy = row; val.gridy = row;
            panel.add(label("Warp List"), lbl);
            warpListLabel = value("—"); panel.add(warpListLabel, val);

            row++; lbl.gridy = row; val.gridy = row;
            panel.add(label("Mtime"), lbl);
            mtimeLabel = value("—"); panel.add(mtimeLabel, val);

            row++; lbl.gridy = row; val.gridy = row;
            panel.add(label("Last Mtime"), lbl);
            lastMtimeLabel = value("—"); panel.add(lastMtimeLabel, val);

            // Push to top
            row++;
            GridBagConstraints filler = new GridBagConstraints();
            filler.gridx = 0; filler.gridy = row; filler.weighty = 1.0;
            panel.add(Box.createGlue(), filler);

            return panel;
        }

        void refreshStats() {
            if (connection == null) return;
            GConnectionStats stats = connection.getStats();

            protocolLabel.setText(stats.getProtocol());
            hostnameLabel.setText(stats.getHostname().isEmpty() ? "—" : stats.getHostname());
            portLabel.setText(stats.getPort() > 0 ? String.valueOf(stats.getPort()) : "Default");

            if (stats.getConnectedSince() > 0) {
                long sec = (System.currentTimeMillis() - stats.getConnectedSince()) / 1000;
                connectedLabel.setText(formatDuration(sec));
            } else {
                connectedLabel.setText("—");
            }

            if (stats.isWarpListChecked()) {
                warpListLabel.setText(stats.isWarpListSupported() ? "Yes" : "No");
                warpListLabel.setForeground(stats.isWarpListSupported()
                    ? new Color(0, 140, 0) : UIManager.getColor("Label.foreground"));
            } else {
                warpListLabel.setText("—");
            }

            if (stats.isMtimeChecked()) {
                mtimeLabel.setText(stats.isMtimeSupported() ? "Yes" : "No");
                mtimeLabel.setForeground(stats.isMtimeSupported()
                    ? new Color(0, 140, 0) : UIManager.getColor("Label.foreground"));
            } else {
                mtimeLabel.setText("—");
            }

            String resp = stats.getLastMtimeResponse();
            lastMtimeLabel.setText(resp != null && !resp.isEmpty() ? resp : "—");
        }

        private static JLabel label(String text) {
            JLabel l = new JLabel(text);
            l.setFont(l.getFont().deriveFont(Font.BOLD, 11f));
            l.setForeground(UIManager.getColor("Label.disabledForeground") != null
                ? UIManager.getColor("Label.disabledForeground") : Color.GRAY);
            return l;
        }

        private static JLabel value(String text) {
            JLabel l = new JLabel(text);
            l.setFont(l.getFont().deriveFont(11f));
            return l;
        }

        private static String formatDuration(long seconds) {
            if (seconds < 60) return seconds + "s";
            long min = seconds / 60;
            long sec = seconds % 60;
            if (min < 60) return min + "m " + sec + "s";
            long hr = min / 60;
            min = min % 60;
            return hr + "h " + min + "m";
        }
    }
}
