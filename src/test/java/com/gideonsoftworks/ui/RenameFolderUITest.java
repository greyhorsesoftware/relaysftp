package com.gideonsoftworks.ui;

import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;
import javax.swing.*;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui") class RenameFolderUITest extends UITestBase {
    @Test void test() throws Exception {
        createServerDir("ren_dir");
        // seed a child so the post-rename drill-in has contents to prove the
        // renamed folder re-lists correctly (stale-subtree regression guard)
        createServerFileInDir("ren_dir", "inner.txt", "inner");
        loginToServer();

        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
        int row = findRowByName(table, "ren_dir");
        assertTrue(row >= 0);

        Timer autoRename = new Timer(500, e -> {
            for (java.awt.Window w : java.awt.Window.getWindows()) {
                if (w instanceof JDialog && w.isVisible() && "Rename...".equals(((JDialog) w).getTitle())) {
                    JTextField field = findTextField(w);
                    if (field != null) {
                        field.selectAll();
                        field.replaceSelection("ren_dir_new");
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

        assertFalse(Files.exists(serverRoot.resolve("ren_dir")));
        assertTrue(Files.isDirectory(serverRoot.resolve("ren_dir_new")));

        // the row renames in place...
        waitForRowPresent(table, "ren_dir_new", 5);
        assertTrue(findRowByName(table, "ren_dir") < 0, "old folder name must be gone");

        // ...and drilling into the renamed folder must list its real contents
        // (the rename invalidated the cached subtree whose paths went stale)
        int newRow = findRowByName(table, "ren_dir_new");
        JobWaiter listWaiter = waitForJob(GJobDescription.CHANGE_AND_LIST_JOB_TYPE);
        table.cell(org.assertj.swing.data.TableCell.row(newRow).column(0)).doubleClick();
        listWaiter.await(10);
        robot.waitForIdle();
        waitForRowPresent(table, "inner.txt", 5);
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
