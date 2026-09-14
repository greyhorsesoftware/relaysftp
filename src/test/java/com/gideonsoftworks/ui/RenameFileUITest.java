package com.gideonsoftworks.ui;

import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;
import javax.swing.*;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui") class RenameFileUITest extends UITestBase {
    @Test void test() throws Exception {
        boolean previousMonitor =
            com.gideonsoftworks.gideon.preferences.GSFTPPreferences.monitorRemoteChangesOn();
        com.gideonsoftworks.gideon.preferences.GSFTPPreferences.setMonitorRemoteChangesOn(false);
        try {
        createServerFile("ren_file.txt", "rename me");
        loginToServer();

        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
        int row = findRowByName(table, "ren_file.txt");
        assertTrue(row >= 0);

        RefreshCounter refreshes = countRefreshJobs();

        Timer autoRename = new Timer(500, e -> {
            for (java.awt.Window w : java.awt.Window.getWindows()) {
                if (w instanceof JDialog && w.isVisible() && "Rename...".equals(((JDialog) w).getTitle())) {
                    JTextField field = findTextField(w);
                    if (field != null) {
                        field.selectAll();
                        field.replaceSelection("ren_renamed.txt");
                        clickButton(w, "OK", "Rename");
                    }
                }
            }
        });
        autoRename.setRepeats(true);
        autoRename.start();

        try {
            JobWaiter waiter = waitForJob(GJobDescription.RENAME_JOB_TYPE);
            rightClickAndSelectMenuItem(table, row, "Rename...");
            waiter.await(10);
        } finally {
            autoRename.stop();
        }

        assertFalse(Files.exists(serverRoot.resolve("ren_file.txt")));
        assertTrue(Files.exists(serverRoot.resolve("ren_renamed.txt")));

        // the row renames in place - no re-list
        waitForRowPresent(table, "ren_renamed.txt", 5);
        assertTrue(findRowByName(table, "ren_file.txt") < 0, "old name must be gone");
        assertEquals(0, refreshes.completed.get(),
            "rename must update the listing in place - no REFRESH jobs");
        } finally {
            com.gideonsoftworks.gideon.preferences.GSFTPPreferences.setMonitorRemoteChangesOn(previousMonitor);
        }
    }

    private JTextField findTextField(java.awt.Container c) {
        for (java.awt.Component comp : c.getComponents()) {
            if (comp instanceof JTextField) return (JTextField) comp;
            if (comp instanceof java.awt.Container) {
                JTextField f = findTextField((java.awt.Container) comp);
                if (f != null) return f;
            }
        }
        return null;
    }

    private void clickButton(java.awt.Container c, String... texts) {
        for (java.awt.Component comp : getAllComponents(c)) {
            if (comp instanceof JButton) {
                for (String t : texts) {
                    if (t.equals(((JButton) comp).getText())) {
                        ((JButton) comp).doClick();
                        return;
                    }
                }
            }
        }
    }
}
