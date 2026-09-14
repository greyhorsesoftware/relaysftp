package com.gideonsoftworks.gideon.toolbar;

import com.gideonsoftworks.gideon.core.GActivityPanelController;
import com.gideonsoftworks.gideon.core.GActivityToolbarItemInterface;
import com.gideonsoftworks.gideon.core.GLocalized;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class GActivityToolbarItem extends JButton implements GActivityToolbarItemInterface {

    private final GActivityPanelController controller;
    private int badgeCount = 0;
    private boolean hasErrors = false;

    // Pulse animation for the running-jobs orb
    private float pulsePhase = 0f;
    private final Timer pulseTimer;
    private static final int PULSE_INTERVAL = 40; // ms
    private static final float PULSE_STEP = 0.10f; // radians per tick (~2.5s per cycle)

    public GActivityToolbarItem(GActivityPanelController controller) {
        this.controller = controller;
        controller.setToolbarItem(this);

        setIcon(GLocalized.ACTIVITY_SVG_ICON);
        setToolTipText(GLocalized.TB_ACTIVITY);
        // FlatSVGIcon renders its own disabled (grayed) state from the component, so no
        // separate pressed/disabled icons are needed.

        setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        putClientProperty("JButton.buttonType", "toolBarButton");  // flat, but highlights on hover/press
        setMinimumSize(new Dimension(36, 36));
        setFocusable(false);

        pulseTimer = new Timer(PULSE_INTERVAL, e -> {
            pulsePhase += PULSE_STEP;
            if (pulsePhase >= (float) (Math.PI * 2)) pulsePhase -= (float) (Math.PI * 2);
            repaint();
        });

        addActionListener(this::onClicked);
    }

    private void onClicked(ActionEvent e) {
        controller.toggle(this);
    }

    @Override
    public void updateBadge(int activeCount, boolean hasErrors) {
        this.badgeCount = activeCount;
        this.hasErrors = hasErrors;

        if (activeCount > 0 && !pulseTimer.isRunning()) {
            pulsePhase = 0f;
            pulseTimer.start();
        } else if (activeCount <= 0 && pulseTimer.isRunning()) {
            pulseTimer.stop();
        }

        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        // Let super paint everything normally (background, hover, text, static icon)
        super.paintComponent(g);

        Icon icon = getIcon();
        if (icon == null) return;

        int iconX = (getWidth() - icon.getIconWidth()) / 2;
        int iconY = getInsets().top;

        // Badge in the lower-right corner of the icon: alert sign when a job
        // errored, else a pulsing green orb while jobs are running.
        int badgeSize = 10;
        int bx = iconX + icon.getIconWidth() - badgeSize + badgeSize / 4;
        int by = iconY + icon.getIconHeight() - badgeSize + badgeSize / 4;

        if (hasErrors) {
            paintAlertBadge(g, bx, by, badgeSize);
        } else if (badgeCount > 0) {
            paintPulsingOrb(g, bx, by, badgeSize);
        }
    }

    private void paintPulsingOrb(Graphics g, int bx, int by, int size) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Pulse the opacity smoothly between ~45% and 100%
        float pulse = 0.725f + 0.275f * (float) Math.sin(pulsePhase);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pulse));

        g2.setColor(new Color(52, 179, 84)); // green
        g2.fillOval(bx, by, size, size);
        g2.dispose();
    }

    private void paintAlertBadge(Graphics g, int bx, int by, int size) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // Rounded warning triangle
        int[] xs = { bx + size / 2, bx + size, bx };
        int[] ys = { by, by + size, by + size };
        g2.setColor(new Color(224, 68, 58)); // alert red
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.fillPolygon(xs, ys, 3);
        g2.drawPolygon(xs, ys, 3);

        // Exclamation mark
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = bx + size / 2;
        g2.drawLine(cx, by + size / 3, cx, by + size - size / 3);
        g2.fillOval(cx - 1, by + size - size / 4 - 1, 2, 2);
        g2.dispose();
    }
}
