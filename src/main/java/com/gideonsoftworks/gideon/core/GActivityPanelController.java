package com.gideonsoftworks.gideon.core;

import com.gideonsoftworks.gja.*;
import com.gideonsoftworks.gcore.GFileItem;
import com.gideonsoftworks.gcore.GConnection;
import com.gideonsoftworks.gcore.GLog;
import com.gideonsoftworks.gideon.logviewer.GLogPanelController;
import com.gideonsoftworks.gideon.logviewer.GLogWindow;
import com.gideonsoftworks.gideon.mirroring.GMirrorAction;
import com.gideonsoftworks.gideon.mirroring.GMirrorResult;
import com.gideonsoftworks.gideon.mirroring.GMirrorRunState;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GActivityPanelController extends GAbstractJobManagerListener implements ActionListener, GQueueEventListenerInterface {

    private final GJobManager jobManager;
    private final JFrame parentFrame;
    private JDialog popover;
    private JPanel contentPanel;
    private Timer progressTimer;
    private GActivityToolbarItemInterface toolbarItem;
    private Component lastAnchor;

    // Single ordered list — items stay in position through their lifecycle
    private final List<ActivityEntry> entries = new ArrayList<>();

    private static final int PANEL_WIDTH = 400;
    private static final int ARROW_HEIGHT = 10;
    private static final int ARROW_HALF_WIDTH = 10;
    private static final int CORNER_RADIUS = 12;
    private int arrowOffsetX = PANEL_WIDTH / 2;

    static final int STATE_QUEUED = 0;
    static final int STATE_ACTIVE = 1;
    static final int STATE_COMPLETED = 2;
    static final int STATE_FAILED = 3;
    static final int STATE_CANCELLED = 4;

    static class ActivityEntry {
        GJobDescription jobDescription;
        int state;
        String displayName;        // folder/file name at top level
        String completionSubtitle; // set when finished
        String localPath;          // for reveal-in-finder
        GLog transferLog; // log from transfer connection
        boolean expanded = false;  // detail section expanded
        long cumulativeBytes = 0;  // total bytes across all files
        long lastPerFileBytes = 0; // last seen per-file bytes (to detect resets)
        // UI components (created when popover is visible)
        JPanel panel;
        JProgressBar progressBar;  // overall progress
        JLabel statusLabel;        // "12 of 458 files — 1.2 MB of 42.7 MB"
        JLabel headerLabel;        // folder/file name
        JButton cancelBtn;
        JButton expandBtn;
        // Detail section (current file)
        JPanel detailPanel;
        JLabel detailFileLabel;    // current file name
        JProgressBar detailProgressBar; // current file progress
        JLabel detailStatusLabel;  // "3 KB — 12 KB/sec"
    }

    public GActivityPanelController(GJobManager jobManager, JFrame parentFrame) {
        this.jobManager = jobManager;
        this.parentFrame = parentFrame;
        jobManager.addStatusListener((GJobManagerListenerInterface) this);
        jobManager.getTransferQueue().addStatusListener((GQueueEventListenerInterface) this);
        buildPopover();

        progressTimer = new Timer(1000, this);
        progressTimer.setRepeats(true);
    }

    public void setToolbarItem(GActivityToolbarItemInterface item) {
        this.toolbarItem = item;
    }

    // --- Popover lifecycle ---

    // When the popover is open and focused, clicking the toolbar button first pulls
    // focus away → windowLostFocus → hide(). The button's own click then runs toggle()
    // and would see the popover as hidden and immediately reopen it. This guard treats a
    // click that lands right after a focus-loss hide as a toggle-off instead.
    private static final long REOPEN_GUARD_MILLIS = 250;
    private long lastHiddenAtMillis = 0L;

    public void toggle(Component anchor) {
        if (popover.isVisible()) {
            hide();
        } else {
            if (System.currentTimeMillis() - lastHiddenAtMillis < REOPEN_GUARD_MILLIS) return;
            show(anchor);
        }
    }

    public void show(Component anchor) {
        lastAnchor = anchor;
        rebuildContent();
        popover.pack();
        repositionPopover();
        popover.setVisible(true);
    }

    private void packPopover() {
        popover.pack();
        repositionPopover();
    }

    private void repositionPopover() {
        Component anchor = lastAnchor;
        if (anchor == null || !anchor.isShowing()) return;
        try {
            Point p = anchor.getLocationOnScreen();
            int anchorCenterX = p.x + anchor.getWidth() / 2;
            int x = anchorCenterX - popover.getWidth() / 2;
            int y = p.y + anchor.getHeight() + 2;

            Rectangle screen = anchor.getGraphicsConfiguration().getBounds();
            if (x + popover.getWidth() > screen.x + screen.width) {
                x = screen.x + screen.width - popover.getWidth();
            }
            if (x < screen.x) x = screen.x;

            arrowOffsetX = anchorCenterX - x;
            arrowOffsetX = Math.max(CORNER_RADIUS + ARROW_HALF_WIDTH, Math.min(arrowOffsetX, popover.getWidth() - CORNER_RADIUS - ARROW_HALF_WIDTH));

            popover.setLocation(x, y);
        } catch (IllegalComponentStateException e) {
            popover.setLocationRelativeTo(parentFrame);
        }
        popover.getContentPane().repaint();
    }

    public void hide() {
        lastHiddenAtMillis = System.currentTimeMillis();
        popover.setVisible(false);
    }

    // --- UI construction ---

    private void buildPopover() {
        popover = new JDialog(parentFrame, false);
        popover.setUndecorated(true);
        popover.setFocusableWindowState(true);
        popover.setAlwaysOnTop(true);
        popover.setBackground(new Color(0, 0, 0, 0));

        JPanel main = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                RoundRectangle2D body = new RoundRectangle2D.Float(
                    0, ARROW_HEIGHT, w, h - ARROW_HEIGHT, CORNER_RADIUS, CORNER_RADIUS);

                Path2D arrow = new Path2D.Float();
                arrow.moveTo(arrowOffsetX - ARROW_HALF_WIDTH, ARROW_HEIGHT);
                arrow.lineTo(arrowOffsetX, 0);
                arrow.lineTo(arrowOffsetX + ARROW_HALF_WIDTH, ARROW_HEIGHT);
                arrow.closePath();

                Area shape = new Area(body);
                shape.add(new Area(arrow));

                Color bg = UIManager.getColor("Panel.background");
                if (bg == null) bg = getBackground();
                g2.setColor(bg);
                g2.fill(shape);

                Color border = UIManager.getColor("Component.borderColor");
                if (border == null) border = Color.GRAY;
                g2.setColor(border);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(shape);

                g2.dispose();
            }
        };
        main.setOpaque(false);
        main.setBorder(new EmptyBorder(ARROW_HEIGHT + 10, 12, 10, 12));
        main.setMinimumSize(new Dimension(PANEL_WIDTH, 60 + ARROW_HEIGHT));
        main.setPreferredSize(null); // let it size to content

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 0, 6, 0));

        JLabel titleLabel = new JLabel("Activity");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        header.add(titleLabel, BorderLayout.CENTER);

        JButton clearButton = new JButton(GLocalized.AP_CLEAR);
        clearButton.putClientProperty("JButton.buttonType", "roundRect");
        clearButton.setFont(clearButton.getFont().deriveFont(11f));
        clearButton.setMargin(new Insets(2, 8, 2, 8));
        clearButton.setFocusable(false);
        clearButton.addActionListener(e -> clearFinished());
        header.add(clearButton, BorderLayout.EAST);

        main.add(header, BorderLayout.NORTH);

        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);
        contentPanel.setPreferredSize(null);

        JScrollPane scroll = new JScrollPane(contentPanel) {
            @Override
            public Dimension getPreferredSize() {
                Dimension pref = contentPanel.getPreferredSize();
                pref.width = PANEL_WIDTH - 24;
                return pref;
            }
        };
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);

        main.add(scroll, BorderLayout.CENTER);

        popover.setContentPane(main);

        popover.addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent e) {}
            @Override
            public void windowLostFocus(WindowEvent e) {
                hide();
            }
        });
    }

    private void rebuildContent() {
        contentPanel.removeAll();

        if (entries.isEmpty()) {
            JLabel empty = new JLabel("No activity");
            empty.setForeground(UIManager.getColor("Label.disabledForeground") != null
                ? UIManager.getColor("Label.disabledForeground") : Color.GRAY);
            empty.setFont(empty.getFont().deriveFont(12f));
            empty.setHorizontalAlignment(SwingConstants.CENTER);
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            empty.setBorder(new EmptyBorder(20, 4, 20, 4));
            empty.setMaximumSize(new Dimension(PANEL_WIDTH, 60));
            contentPanel.add(empty);
        }

        for (int i = 0; i < entries.size(); i++) {
            ActivityEntry entry = entries.get(i);
            JPanel panel = buildEntryPanel(entry);
            entry.panel = panel;
            panel.setAlignmentX(Component.LEFT_ALIGNMENT);
            contentPanel.add(panel);
            if (i < entries.size() - 1) {
                contentPanel.add(createSeparator());
            }
        }

        // Immediately update active entries so we don't flash stale state
        for (ActivityEntry entry : entries) {
            if (entry.state == STATE_ACTIVE) {
                updateActiveEntryUI(entry);
            }
        }

        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private JPanel buildEntryPanel(ActivityEntry entry) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(6, 0, 6, 0));
        panel.setMaximumSize(new Dimension(PANEL_WIDTH, Short.MAX_VALUE));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        boolean isFinished = entry.state >= STATE_COMPLETED;
        boolean isTransfer = entry.jobDescription.isTransferJob()
            || entry.jobDescription.getJobType() == GJobDescription.MIRROR_JOB_TYPE;   // a mirror shows its current copy in the detail row
        Color dimColor = UIManager.getColor("Label.disabledForeground") != null
            ? UIManager.getColor("Label.disabledForeground") : Color.GRAY;

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        int row = 0;

        // Column 0: file/folder icon — anchored to top
        GFileItem targetItem = entry.jobDescription.getTargetFile();
        if (targetItem != null) {
            ImageIcon srcIcon = targetItem.getIsDir() ? GLocalized.FOLDER_BIG : GLocalized.FILE_BIG;
            if (srcIcon != null) {
                JLabel iconLabel = new JLabel(new ImageIcon(
                    srcIcon.getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH)));
                gbc.gridx = 0; gbc.gridy = row;
                gbc.anchor = GridBagConstraints.NORTHWEST;
                gbc.insets = new Insets(0, 0, 0, 6);
                panel.add(iconLabel, gbc);
            }
        }

        // Column 1: name + progress + status stacked
        gbc.anchor = GridBagConstraints.WEST;

        int contentWidth = PANEL_WIDTH - 100; // leave room for icon + buttons

        JPanel contentCol = new JPanel();
        contentCol.setLayout(new BoxLayout(contentCol, BoxLayout.Y_AXIS));
        contentCol.setOpaque(false);
        contentCol.setMaximumSize(new Dimension(contentWidth, Short.MAX_VALUE));

        entry.headerLabel = new JLabel(entry.displayName != null ? entry.displayName : getInitialFileName(entry.jobDescription));
        entry.headerLabel.setName("activityHeaderLabel");
        entry.headerLabel.setFont(entry.headerLabel.getFont().deriveFont(Font.BOLD, 12f));
        entry.headerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        entry.headerLabel.setMaximumSize(new Dimension(contentWidth, 20));
        contentCol.add(entry.headerLabel);

        if (isFinished) {
            contentCol.add(Box.createVerticalStrut(2));
            String subtitle = entry.completionSubtitle != null ? entry.completionSubtitle : "";
            JTextArea subtitleArea = new JTextArea(subtitle);
            subtitleArea.setFont(UIManager.getFont("Label.font").deriveFont(11f));
            subtitleArea.setForeground(dimColor);
            subtitleArea.setBackground(null);
            subtitleArea.setEditable(false);
            subtitleArea.setFocusable(false);
            subtitleArea.setLineWrap(true);
            subtitleArea.setWrapStyleWord(true);
            subtitleArea.setOpaque(false);
            subtitleArea.setBorder(null);
            subtitleArea.setAlignmentX(Component.LEFT_ALIGNMENT);
            subtitleArea.setMaximumSize(new Dimension(contentWidth, 60));
            entry.statusLabel = new JLabel(); // keep reference for compatibility
            contentCol.add(subtitleArea);
        } else {
            contentCol.add(Box.createVerticalStrut(3));

            entry.progressBar = new JProgressBar();
            entry.progressBar.setPreferredSize(new Dimension(200, 5));
            entry.progressBar.setMaximumSize(new Dimension(200, 5));
            entry.progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
            entry.progressBar.setIndeterminate(entry.state == STATE_ACTIVE);
            contentCol.add(entry.progressBar);

            contentCol.add(Box.createVerticalStrut(2));

            // Status with expand button
            JPanel statusRow = new JPanel(new BorderLayout(2, 0));
            statusRow.setOpaque(false);
            statusRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            statusRow.setMaximumSize(new Dimension(PANEL_WIDTH, 16));

            if (isTransfer) {
                entry.expandBtn = new JButton(entry.expanded ? "\u25BC" : "\u25B6");
                entry.expandBtn.setFont(entry.expandBtn.getFont().deriveFont(9f));
                entry.expandBtn.setFocusable(false);
                entry.expandBtn.setBorderPainted(false);
                entry.expandBtn.setContentAreaFilled(false);
                entry.expandBtn.setMargin(new Insets(0, 0, 0, 0));
                entry.expandBtn.setPreferredSize(new Dimension(14, 14));
                entry.expandBtn.setVisible(false);
                entry.expandBtn.addActionListener(e -> {
                    entry.expanded = !entry.expanded;
                    entry.expandBtn.setText(entry.expanded ? "\u25BC" : "\u25B6");
                    if (entry.detailPanel != null) entry.detailPanel.setVisible(entry.expanded);
                    packPopover();
                });
                statusRow.add(entry.expandBtn, BorderLayout.WEST);
            }

            entry.statusLabel = new JLabel(entry.state == STATE_ACTIVE ? "Preparing..." : "Waiting...");
            entry.statusLabel.setName("activityStatusLabel");
            entry.statusLabel.setFont(entry.statusLabel.getFont().deriveFont(11f));
            entry.statusLabel.setForeground(dimColor);
            statusRow.add(entry.statusLabel, BorderLayout.CENTER);

            contentCol.add(statusRow);

            // Detail section
            if (isTransfer) {
                entry.detailPanel = new JPanel();
                entry.detailPanel.setLayout(new BoxLayout(entry.detailPanel, BoxLayout.Y_AXIS));
                entry.detailPanel.setBorder(new EmptyBorder(4, 16, 0, 0));
                entry.detailPanel.setOpaque(false);
                entry.detailPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                entry.detailPanel.setVisible(entry.expanded);

                entry.detailFileLabel = new JLabel(" ");
                entry.detailFileLabel.setFont(entry.detailFileLabel.getFont().deriveFont(11f));
                entry.detailFileLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                entry.detailPanel.add(entry.detailFileLabel);

                entry.detailPanel.add(Box.createVerticalStrut(2));

                entry.detailProgressBar = new JProgressBar();
                entry.detailProgressBar.setPreferredSize(new Dimension(100, 4));
                entry.detailProgressBar.setMaximumSize(new Dimension(Short.MAX_VALUE, 4));
                entry.detailProgressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
                entry.detailProgressBar.setIndeterminate(true);
                entry.detailPanel.add(entry.detailProgressBar);

                entry.detailPanel.add(Box.createVerticalStrut(2));

                entry.detailStatusLabel = new JLabel(" ");
                entry.detailStatusLabel.setFont(entry.detailStatusLabel.getFont().deriveFont(10f));
                entry.detailStatusLabel.setForeground(dimColor);
                entry.detailStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                entry.detailPanel.add(entry.detailStatusLabel);

                contentCol.add(entry.detailPanel);
            }
        }

        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(contentCol, gbc);

        // Column 2: buttons — vertically centered
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0)) {
            @Override public Insets getInsets() { return new Insets(0, 0, 0, 0); }
        };
        btnPanel.setOpaque(false);

        boolean showLog = isTransfer
            ? (entry.transferLog != null || !isFinished)
            : entry.jobDescription.getConnectionToUse() != null;
        if (showLog) {
            JButton logBtn = createCircleButton(GLocalized.ACTIVITY_LOG_ICON);
            logBtn.setToolTipText("Show log");
            if (isTransfer) {
                logBtn.addActionListener(e -> openTransferLog(entry));
            } else {
                logBtn.addActionListener(e -> openConnectionLog(entry));
            }
            btnPanel.add(logBtn);
        }
        if (isFinished) {
            if (entry.localPath != null) {
                JButton revealBtn = createCircleButton(GLocalized.ACTIVITY_FIND_ICON);
                revealBtn.setToolTipText("Show in file manager");
                revealBtn.addActionListener(e -> revealInFileManager(entry.localPath));
                btnPanel.add(revealBtn);
            } else {
                JButton dismissBtn = createCircleButton(GLocalized.ACTIVITY_CANCEL_ICON);
                dismissBtn.setToolTipText("Dismiss");
                dismissBtn.addActionListener(e -> {
                    entries.remove(entry);
                    rebuildContent();
                    packPopover();
                    updateToolbarBadge();
                });
                btnPanel.add(dismissBtn);
            }
        } else {
            entry.cancelBtn = createCircleButton(GLocalized.ACTIVITY_CANCEL_ICON);
            entry.cancelBtn.setToolTipText("Cancel");
            entry.cancelBtn.addActionListener(e -> jobManager.stopJob(entry.jobDescription));
            btnPanel.add(entry.cancelBtn);
        }
        gbc.gridx = 2; gbc.gridy = row; gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(btnPanel, gbc);

        return panel;
    }

    // --- Entry lookup ---

    private boolean isTrackedJobType(int jobType) {
        return jobType == GJobDescription.DOWNLOAD_JOB_TYPE
            || jobType == GJobDescription.UPLOAD_JOB_TYPE
            || jobType == GJobDescription.DELETE_JOB_TYPE
            || jobType == GJobDescription.CHMOD_JOB_TYPE
            || jobType == GJobDescription.MIRROR_JOB_TYPE;
    }

    private boolean isAutoRemoveJobType(int jobType) {
        return jobType == GJobDescription.DELETE_JOB_TYPE
            || jobType == GJobDescription.CHMOD_JOB_TYPE;
    }

    private ActivityEntry findEntry(GJobDescription jd) {
        for (ActivityEntry entry : entries) {
            if (entry.jobDescription == jd) return entry;
        }
        return null;
    }

    private boolean hasActiveEntries() {
        for (ActivityEntry entry : entries) {
            if (entry.state == STATE_QUEUED || entry.state == STATE_ACTIVE) return true;
        }
        return false;
    }

    // --- GQueueEventListenerInterface ---

    @Override
    public void jobCountChanged(final GQueueEvent e) {
        GJobDescription jd = e.getJobDescription();
        if (jd == null) return;
        if (!isTrackedJobType(jd.getJobType())) return;
        if (findEntry(jd) != null) return;

        ActivityEntry entry = new ActivityEntry();
        entry.jobDescription = jd;
        entry.state = STATE_QUEUED;
        entry.displayName = getInitialFileName(jd);
        entries.add(0, entry); // newest at top

        if (popover.isVisible()) {
            rebuildContent();
            packPopover();
        }
        updateToolbarBadge();
    }

    // --- GJobManagerListenerInterface ---

    @Override
    public void jm_jobStarted(final GJobManagerEvent e) {
        GJobDescription jd = e.getJobDescription();
        if (jd == null) return;
        if (!isTrackedJobType(jd.getJobType())) return;

        ActivityEntry entry = findEntry(jd);
        if (entry == null) {
            entry = new ActivityEntry();
            entry.jobDescription = jd;
            entry.displayName = getInitialFileName(jd);
            entries.add(0, entry);
        }
        entry.state = STATE_ACTIVE;

        if (!progressTimer.isRunning()) {
            progressTimer.start();
        }

        if (popover.isVisible()) {
            rebuildContent();
            packPopover();
        }
        updateToolbarBadge();
    }

    @Override
    public void jm_keyJobChanged(final GJobManagerEvent e) {
        if (hasActiveEntries() && !progressTimer.isRunning()) {
            progressTimer.start();
        }
    }

    @Override
    public void jm_jobCompleted(final GJobManagerEvent e) {
        GJobDescription jd = e.getJobDescription();
        if (jd == null) return;

        ActivityEntry entry = findEntry(jd);
        if (entry == null) return;

        entry.state = STATE_COMPLETED;
        entry.completionSubtitle = buildCompletionSubtitle(jd, STATE_COMPLETED);
        captureLocalPath(entry, jd);
        stopTimerIfIdle();

        // Auto-remove DELETE/CHMOD entries once complete
        if (isAutoRemoveJobType(jd.getJobType())) {
            entries.remove(entry);
        }

        if (popover.isVisible()) {
            rebuildContent();
            packPopover();
        }
        updateToolbarBadge();
    }

    @Override
    public void jm_jobFailed(final GJobManagerEvent e) {
        GJobDescription jd = e.getJobDescription();
        if (jd == null) return;

        // Mark the failed job's own entry (symmetric with jm_jobCompleted). Fall back to the
        // parent/key job's entry only when this job has no entry of its own — otherwise the
        // real download entry stays ACTIVE and the toolbar spinner never stops.
        ActivityEntry entry = findEntry(jd);
        GJobDescription statusJd = jd;
        if (entry == null) {
            GJobDescription keyJd = jd.getParentQueue() != null ? jd.getParentQueue().getKeyJob() : null;
            if (keyJd != null) { entry = findEntry(keyJd); statusJd = keyJd; }
        }
        if (entry == null) return;

        entry.state = STATE_FAILED;

        String errorMsg = "";
        GJobStatus so = statusJd.getJobStatus();
        if (so != null && so.getException() != null) {
            errorMsg = stripExceptionClassName(so.getException().getMessage());
        }
        entry.completionSubtitle = (errorMsg != null && !errorMsg.isEmpty()) ? errorMsg : "Failed";

        stopTimerIfIdle();

        if (popover.isVisible()) {
            rebuildContent();
            packPopover();
        }
        updateToolbarBadge();
    }

    @Override
    public void jm_jobCancelling(final GJobManagerEvent e) {
        GJobDescription jd = e.getJobDescription();
        if (jd == null) return;

        ActivityEntry entry = findEntry(jd);
        if (entry == null) return;

        if (entry.headerLabel != null) {
            entry.headerLabel.setText("Cancelling...");
        }
        if (entry.progressBar != null) {
            entry.progressBar.setIndeterminate(true);
        }
        if (entry.statusLabel != null) {
            entry.statusLabel.setText("");
        }
    }

    @Override
    public void jm_jobStopped(final GJobManagerEvent e) {
        GJobDescription jd = e.getJobDescription();
        if (jd == null) return;

        ActivityEntry entry = findEntry(jd);
        if (entry == null) return;

        entry.state = STATE_CANCELLED;
        entry.completionSubtitle = "Cancelled";
        stopTimerIfIdle();

        if (popover.isVisible()) {
            rebuildContent();
            packPopover();
        }
        updateToolbarBadge();
    }

    @Override
    public void jm_shuttingDown(final GJobManagerEvent e) {
        if (progressTimer.isRunning()) progressTimer.stop();
        entries.clear();
        hide();
    }

    // --- Timer: progress polling ---

    @Override
    public void actionPerformed(ActionEvent e) {
        for (ActivityEntry entry : entries) {
            if (entry.state == STATE_ACTIVE) {
                updateActiveEntryUI(entry);
            }
        }
    }

    private void updateActiveEntryUI(ActivityEntry entry) {
        GJobStatus so = entry.jobDescription.getJobStatus();
        if (so == null) return;

        // Capture transfer log if not yet captured
        if (entry.transferLog == null && so.getJobExecuter() != null) {
            GJobPlugin plugin = so.getJobExecuter().getJobPlugin();
            if (plugin != null && plugin.getJobConnection() != null) {
                entry.transferLog = plugin.getJobConnection().getLog();
            }
        }

        int state = so.getState();
        int jobType = entry.jobDescription.getJobType();

        // Preparing state — preprocessing, no transfers yet
        if (state == GJobStatus.STATE_PREPARING && so.getBytesTransferred() == 0) {
            if (entry.progressBar != null) {
                entry.progressBar.setIndeterminate(true);
            }
            if (entry.statusLabel != null) {
                entry.statusLabel.setText("Preparing to transfer...");
            }
            // Hide expand button during preprocessing
            if (entry.expandBtn != null) {
                entry.expandBtn.setVisible(false);
            }
            if (entry.detailPanel != null) {
                entry.detailPanel.setVisible(false);
            }
            return;
        }

        // Show expand button once transfers start
        if (entry.expandBtn != null && !entry.expandBtn.isVisible()) {
            entry.expandBtn.setVisible(true);
        }

        // Show progress for any state once the job is active
        // (STATE_WAITING_FOR_USER doesn't reset to STATE_RUNNING after collision dialogs)
        boolean isTransfer = (jobType == GJobDescription.DOWNLOAD_JOB_TYPE
            || jobType == GJobDescription.UPLOAD_JOB_TYPE);

        if (isTransfer) {
            updateTransferProgress(entry, so);
        } else {
            updateItemCountProgress(entry, so);
        }
    }

    private void updateTransferProgress(ActivityEntry entry, GJobStatus so) {
        long perFileBytes = so.getBytesTransferred();
        long totalSize = so.getTotalSize();
        long seconds = so.getSecondsSinceStart();
        long totalFiles = so.getTotalFileCount();
        long processedFiles = so.getProcessedFilesCount();
        GJobDescription active = so.getActivePrerequisite();

        // Track cumulative bytes
        if (perFileBytes < entry.lastPerFileBytes) {
            entry.cumulativeBytes += entry.lastPerFileBytes;
        }
        entry.lastPerFileBytes = perFileBytes;
        long totalBytesTransferred = entry.cumulativeBytes + perFileBytes;

        boolean isSingleFile = totalFiles <= 1;

        // Single file: hide detail section, show transfer stats directly
        if (isSingleFile) {
            if (entry.detailPanel != null) entry.detailPanel.setVisible(false);
            if (entry.expandBtn != null) entry.expandBtn.setVisible(false);

            long fileSize = (active != null && active.getTargetFile() != null)
                ? active.getTargetFile().getRawSize() : totalSize;

            if (entry.progressBar != null) {
                if (fileSize > 0) {
                    entry.progressBar.setIndeterminate(false);
                    entry.progressBar.setMaximum((int)(fileSize / 1024 + 1));
                    entry.progressBar.setValue((int)(perFileBytes / 1024));
                } else {
                    entry.progressBar.setIndeterminate(true);
                }
            }

            if (entry.statusLabel != null) {
                double bytesPerSec = seconds > 0 ? (double) perFileBytes / seconds : 0;
                StringBuilder sb = new StringBuilder();
                sb.append(formatSize(perFileBytes));
                if (fileSize > 0) sb.append(" of ").append(formatSize(fileSize));
                if (bytesPerSec > 0) sb.append(" (").append(formatSize((long) bytesPerSec)).append("/sec)");
                entry.statusLabel.setText(sb.toString());
            }
            return;
        }

        // Multi-file: progress bar based on file count
        if (entry.progressBar != null) {
            if (totalFiles > 0) {
                entry.progressBar.setIndeterminate(false);
                entry.progressBar.setMaximum((int) totalFiles);
                entry.progressBar.setValue((int) processedFiles);
            } else {
                entry.progressBar.setIndeterminate(true);
            }
        }

        if (entry.statusLabel != null) {
            StringBuilder sb = new StringBuilder();
            if (totalFiles > 0) {
                sb.append(processedFiles).append(" of ").append(totalFiles).append(" transferred");
                int pct = (int)(processedFiles * 100 / totalFiles);
                sb.append(" \u2014 ").append(pct).append("% complete");
            }
            entry.statusLabel.setText(sb.toString());
        }

        // Detail section — current file being transferred
        if (active != null && entry.detailFileLabel != null) {
            String currentFileName = null;
            long currentFileSize = 0;
            if (entry.jobDescription.isDownloadJob()
                    && active.getSourceFile() != null) {
                currentFileName = active.getSourceFile().getName();
            }
            if (currentFileName == null && active.getTargetFile() != null) {
                currentFileName = active.getTargetFile().getName();
            }
            if (active.getTargetFile() != null) {
                currentFileSize = active.getTargetFile().getRawSize();
            }

            if (currentFileName != null) {
                entry.detailFileLabel.setText(currentFileName);
            }

            // perFileBytes is per-file (resets for each file)
            if (entry.detailProgressBar != null) {
                if (currentFileSize > 0) {
                    entry.detailProgressBar.setIndeterminate(false);
                    entry.detailProgressBar.setMaximum((int)(currentFileSize / 1024 + 1));
                    entry.detailProgressBar.setValue((int)(perFileBytes / 1024));
                } else {
                    entry.detailProgressBar.setIndeterminate(true);
                }
            }

            // "12 KB of 254 KB (45 KB/sec)"
            if (entry.detailStatusLabel != null) {
                double bytesPerSec = seconds > 0 ? (double) totalBytesTransferred / seconds : 0;
                StringBuilder ds = new StringBuilder();
                ds.append(formatSize(perFileBytes));
                if (currentFileSize > 0) {
                    ds.append(" of ").append(formatSize(currentFileSize));
                }
                if (bytesPerSec > 0) {
                    ds.append(" (").append(formatSize((long) bytesPerSec)).append("/sec)");
                }
                entry.detailStatusLabel.setText(ds.toString());
            }
        }
    }

    private void updateItemCountProgress(ActivityEntry entry, GJobStatus so) {
        long processed = so.getTotalProcessedCount();
        long total = so.getTotalCount();

        // A mirror carries its live run state: same fields the Running card renders, read from the same object.
        Object rs = entry.jobDescription.getProperty(GMirrorJobPlugin.MIRROR_RUN_STATE_PROP);
        if (rs instanceof GMirrorRunState state) {
            updateMirrorProgress(entry, state);
            return;
        }

        GJobDescription active = so.getActivePrerequisite();
        if (entry.headerLabel != null && active != null && active.getTargetFile() != null) {
            entry.headerLabel.setText(active.getTargetFile().getName());
        }

        if (entry.progressBar != null && total > 0) {
            entry.progressBar.setIndeterminate(false);
            entry.progressBar.setMaximum((int) total);
            entry.progressBar.setValue((int) processed);
        }

        if (entry.statusLabel != null) {
            entry.statusLabel.setText("Processing " + processed + " of " + total + " items");
        }
    }

    private void updateMirrorProgress(ActivityEntry entry, GMirrorRunState state) {
        int total = state.size();
        int done = state.doneCount() + state.skippedCount();
        long bytes = state.bytesSoFar();
        long elapsedMs = Math.max(1, System.currentTimeMillis() - state.startMillis());
        int current = state.firstRunning();

        if (entry.headerLabel != null && current >= 0) {
            entry.headerLabel.setText(state.action(current).relPath);
        }
        if (entry.progressBar != null && total > 0) {
            entry.progressBar.setIndeterminate(false);
            entry.progressBar.setMaximum(total);
            entry.progressBar.setValue(done);
        }
        if (entry.statusLabel != null) {
            entry.statusLabel.setText(done + " of " + total + " items · " + formatSize(bytes) + " of "
                + formatSize(state.totalBytes()) + " · " + formatSize(bytes * 1000 / elapsedMs) + "/sec");
        }
        if (entry.detailPanel != null && current >= 0) {
            GMirrorAction a = state.action(current);
            if (entry.detailFileLabel != null) entry.detailFileLabel.setText(a.relPath);
            if (entry.detailProgressBar != null) {
                boolean sized = a.type == GMirrorAction.Type.COPY && a.bytes > 0;
                entry.detailProgressBar.setIndeterminate(!sized);
                if (sized) {
                    entry.detailProgressBar.setMaximum(100);
                    entry.detailProgressBar.setValue((int) Math.min(100, state.bytesDone(current) * 100 / a.bytes));
                }
            }
            if (entry.detailStatusLabel != null) {
                entry.detailStatusLabel.setText(a.type == GMirrorAction.Type.COPY
                    ? formatSize(state.bytesDone(current)) + " of " + formatSize(a.bytes) : a.type.name().toLowerCase());
            }
        }
    }

    // --- Completion helpers ---

    private String buildCompletionSubtitle(GJobDescription jd, int outcome) {
        if (jd.getJobType() == GJobDescription.MIRROR_JOB_TYPE) {
            if (outcome == STATE_COMPLETED) {
                GMirrorResult r = (GMirrorResult) jd.getProperty(GMirrorJobPlugin.MIRROR_RESULT_PROP);
                if (r != null) {
                    String s = r.copies + " copied, " + r.deletes + " deleted";
                    if (r.skipped > 0) s += ", " + r.skipped + " skipped";
                    return s;
                }
                return "Mirror complete";
            }
            return outcome == STATE_CANCELLED ? "Mirror cancelled" : "Mirror failed";
        }

        long totalCount = jd.getJobStatus() != null ? jd.getJobStatus().getTotalCount() : 0;

        if (outcome == STATE_COMPLETED) {
            String verb = jd.isTransferJob()
                ? (jd.isDownloadJob() ? "Downloaded" : "Uploaded")
                : (jd.isDeleteJob() ? "Deleted" : "Changed permissions on");
            return verb + (totalCount > 0 ? " " + totalCount + " items" : "");
        } else if (outcome == STATE_CANCELLED) {
            return "Cancelled";
        }
        return "Failed";
    }

    private void captureLocalPath(ActivityEntry entry, GJobDescription jd) {
        if (jd.isTransferJob()) {
            GFileItem localFile =
                (jd.isDownloadJob())
                    ? jd.getSourceFile() : jd.getTargetFile();
            if (localFile != null && localFile.getIsFileLocal() && localFile.getCompletePath() != null) {
                entry.localPath = localFile.getCompletePath();
            }
        }
    }

    private void clearFinished() {
        entries.removeIf(e -> e.state >= STATE_COMPLETED);
        if (popover.isVisible()) {
            rebuildContent();
            packPopover();
        }
        updateToolbarBadge();
    }

    // --- Toolbar badge ---

    private void updateToolbarBadge() {
        if (toolbarItem == null) return;
        int activeCount = 0;
        boolean hasErrors = false;
        for (ActivityEntry entry : entries) {
            if (entry.state == STATE_QUEUED || entry.state == STATE_ACTIVE) activeCount++;
            if (entry.state == STATE_FAILED) hasErrors = true;
        }
        toolbarItem.updateBadge(activeCount, hasErrors);
    }

    // --- Helpers ---

    // Strip leading fully-qualified exception class names (e.g. "java.net.SocketException: ")
    // from a message so the Activity panel shows just the human-readable part. Handles chains
    // like "java.io.IOException: java.net.SocketException: Network is unreachable".
    private static final java.util.regex.Pattern LEADING_EXCEPTION_CLASS =
        java.util.regex.Pattern.compile("^(?:[a-zA-Z_$][a-zA-Z0-9_$]*\\.)+[a-zA-Z_$][a-zA-Z0-9_$]*(?:Exception|Error|Throwable):\\s*");

    private static String stripExceptionClassName(String msg) {
        if (msg == null) return null;
        String s = msg;
        java.util.regex.Matcher m;
        while ((m = LEADING_EXCEPTION_CLASS.matcher(s)).find()) {
            s = s.substring(m.end());
        }
        return s.trim();
    }

    private JButton createHoverButton(String text) {
        JButton btn = new JButton(text) {
            private boolean hovered = false;

            {
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                    @Override
                    public void mouseExited(MouseEvent e) { hovered = false; repaint(); }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                if (hovered) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int size = Math.min(getWidth(), getHeight());
                    int x = (getWidth() - size) / 2;
                    int y = (getHeight() - size) / 2;
                    Color hoverColor = UIManager.getColor("Button.hoverBackground");
                    if (hoverColor == null) hoverColor = new Color(0, 0, 0, 30);
                    g2.setColor(hoverColor);
                    g2.fillOval(x, y, size, size);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        btn.setFocusable(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        return btn;
    }

    private JButton createHoverButton(Icon icon) {
        JButton btn = createHoverButton("");
        btn.setIcon(icon);
        btn.setText(null);
        return btn;
    }

    private static final int CIRCLE_BTN_SIZE = 36;

    /**
     * Creates a button with a persistent circular background that darkens on hover.
     */
    private JButton createCircleButton(String text) {
        JButton btn = new JButton(text) {
            private boolean hovered = false;

            {
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                    @Override
                    public void mouseExited(MouseEvent e) { hovered = false; repaint(); }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int d = Math.min(getWidth(), getHeight()) - 2;
                int x = (getWidth() - d) / 2;
                int y = (getHeight() - d) / 2;
                if (!hovered) { super.paintComponent(g); return; }
                Color bg = new Color(0, 0, 0, 25);
                g2.setColor(bg);
                g2.fillOval(x, y, d, d);
                g2.dispose();
                super.paintComponent(g);
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(CIRCLE_BTN_SIZE, CIRCLE_BTN_SIZE);
            }

            @Override
            public Dimension getMinimumSize() { return getPreferredSize(); }

            @Override
            public Dimension getMaximumSize() { return getPreferredSize(); }
        };
        btn.setFont(btn.getFont().deriveFont(20f));
        btn.setFocusable(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setMargin(new Insets(0, 0, 0, 0));
        return btn;
    }

    private JButton createCircleButton(Icon icon) {
        JButton btn = createCircleButton("");
        btn.setIcon(icon);
        btn.setText(null);
        return btn;
    }

    private String getInitialFileName(GJobDescription jd) {
        if (jd.getJobType() == GJobDescription.MIRROR_JOB_TYPE) {
            return jd.getName();   // e.g. "Mirroring local → remote"
        }
        if (jd.getTargetFile() != null && jd.getTargetFile().getName() != null) {
            return jd.getTargetFile().getName();
        }
        if (jd.getSourceFile() != null && jd.getSourceFile().getName() != null) {
            return jd.getSourceFile().getName();
        }
        return jd.getName();
    }

    private JSeparator createSeparator() {
        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
        sep.setMaximumSize(new Dimension(PANEL_WIDTH, 1));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        return sep;
    }

    private void stopTimerIfIdle() {
        if (!hasActiveEntries() && progressTimer.isRunning()) {
            progressTimer.stop();
        }
    }


    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " bytes";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        double gb = mb / 1024.0;
        return String.format("%.2f GB", gb);
    }

    private void openConnectionLog(ActivityEntry entry) {
        GConnection conn = entry.jobDescription.getConnectionToUse();
        if (conn != null && conn.getLog() != null) {
            GLogWindow.showForLog(conn.getLog().getName());
        }
    }

    private void openTransferLog(ActivityEntry entry) {
        GLog log = entry.transferLog;
        if (log == null) return;

        String title = "Log — " + (entry.displayName != null ? entry.displayName : "Transfer");

        JFrame logFrame = new JFrame(title);
        logFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        logFrame.setSize(500, 300);

        GLogPanelController logPanel =
            new GLogPanelController(log);
        logPanel.startMonitoring();
        logFrame.getContentPane().add(logPanel.getPanel());

        // #4: stop the log-reader thread when the window closes (DISPOSE_ON_CLOSE → windowClosed)
        logFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) { logPanel.stopMonitoring(); }
        });

        logFrame.setLocationRelativeTo(parentFrame);
        logFrame.setVisible(true);
    }

    private void revealInFileManager(String path) {
        try {
            File file = new File(path);
            File target = file.isDirectory() ? file : file.getParentFile();
            if (target != null && target.exists()) {
                Desktop.getDesktop().open(target);
            }
        } catch (Exception ex) {
            // Silently ignore
        }
    }
}
