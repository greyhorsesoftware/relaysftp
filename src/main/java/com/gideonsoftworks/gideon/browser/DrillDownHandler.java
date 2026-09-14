package com.gideonsoftworks.gideon.browser;

import com.gideonsoftworks.gcore.GFileItem;
import javax.swing.*;

public class DrillDownHandler {

    private static final int HOVER_DELAY_MS = 800;
    private static final int BACK_UP_DELAY_MS = 1200;

    private final JTable table;
    private final GBrowserPanelCommandInterface panel;
    private Timer hoverTimer;
    private Timer backUpTimer;
    private int lastHoverRow = -1;
    private int drillDownDepth = 0;

    public DrillDownHandler(JTable table, GBrowserPanelCommandInterface panel) {
        this.table = table;
        this.panel = panel;

        table.addPropertyChangeListener("dropLocation", evt -> {
            JTable.DropLocation dl = (JTable.DropLocation) evt.getNewValue();
            if (dl == null) {
                onRowChanged(-1);
                return;
            }
            onRowChanged(dl.getRow());
        });
    }

    /** Package-private for testing — called when the hover row changes during a drag. */
    void onRowChanged(int viewRow) {
        if (viewRow == lastHoverRow) return;
        lastHoverRow = viewRow;

        cancelDrillDown();

        if (viewRow < 0) {
            // Drag left the table — start back-up timer if we drilled down
            if (drillDownDepth > 0) {
                startBackUpTimer();
            }
            return;
        }

        // Drag is over a row — cancel any back-up timer
        cancelBackUp();

        // Convert view row to model row to handle sorting
        int modelRow = table.convertRowIndexToModel(viewRow);
        GFileItem item = panel.getItemAt(modelRow, 0);
        if (item == null || !item.getIsDir() || item.getIsCDUP()) return;

        hoverTimer = new Timer(HOVER_DELAY_MS, e -> {
            panel.changeToFolder(item);
            drillDownDepth++;
            lastHoverRow = -1;
        });
        hoverTimer.setRepeats(false);
        hoverTimer.start();
    }

    /** Reset drill-down tracking — call when drag ends. */
    void resetDrillDown() {
        cancelDrillDown();
        cancelBackUp();
        drillDownDepth = 0;
        lastHoverRow = -1;
    }

    private void startBackUpTimer() {
        cancelBackUp();
        backUpTimer = new Timer(BACK_UP_DELAY_MS, e -> {
            GFileItem cwd = panel.getVirtualCWD();
            if (cwd != null && cwd.getParentFileItem() != null) {
                panel.changeToFolder(cwd.getParentFileItem());
                drillDownDepth--;
                // If still drilled down and still outside, keep going
                if (drillDownDepth > 0 && lastHoverRow < 0) {
                    startBackUpTimer();
                }
            }
        });
        backUpTimer.setRepeats(false);
        backUpTimer.start();
    }

    private void cancelDrillDown() {
        if (hoverTimer != null) {
            hoverTimer.stop();
            hoverTimer = null;
        }
    }

    private void cancelBackUp() {
        if (backUpTimer != null) {
            backUpTimer.stop();
            backUpTimer = null;
        }
    }
}
