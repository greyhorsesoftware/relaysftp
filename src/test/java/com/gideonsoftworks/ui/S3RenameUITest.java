package com.gideonsoftworks.ui;

import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;
import javax.swing.*;

import static org.junit.jupiter.api.Assertions.*;

/** Rename an S3 object (copy + delete) via the browser context menu. */
@Tag("ui")
class S3RenameUITest extends S3UITestBase {

    @Test
    void test() throws Exception {
        seedObject("rename_me.txt", "content");
        loginToServer();

        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
        int row = findRowByName(table, "rename_me.txt");
        assertTrue(row >= 0, "rename_me.txt should be listed");

        Timer autoRename = new Timer(500, e -> {
            for (java.awt.Window w : java.awt.Window.getWindows()) {
                if (w instanceof JDialog && w.isVisible() && "Rename...".equals(((JDialog) w).getTitle())) {
                    JTextField field = findTextField(w);
                    if (field != null) {
                        field.selectAll();
                        field.replaceSelection("renamed.txt");
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
            waiter.await(15);
        } finally {
            autoRename.stop();
        }

        assertFalse(objectExists("rename_me.txt"), "original key should be gone");
        assertTrue(objectExists("renamed.txt"), "renamed key should exist");

        // the row renames in place - no re-list
        waitForRowPresent(table, "renamed.txt", 5);
        assertTrue(findRowByName(table, "rename_me.txt") < 0, "old name must be gone");
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
