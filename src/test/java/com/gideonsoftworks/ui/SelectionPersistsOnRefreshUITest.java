package com.gideonsoftworks.ui;

import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.data.TableCell;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;
import javax.swing.*;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the "highlight disappears after upload/replace" bug.
 *
 * Each upload re-lists the destination (buddy) pane, and the refresh fires a whole-table
 * model event that Swing reacts to by clearing the selection. GBrowserPanelController must
 * re-apply the selection by name so it survives. This drives several uploads (so the remote
 * pane refreshes repeatedly) plus a final explicit refresh, and asserts a remote-pane
 * selection persists throughout.
 */
@Tag("ui") class SelectionPersistsOnRefreshUITest extends UITestBase {
    @Test void test() throws Exception {
        // A remote file we keep selected the whole time.
        createServerFile("keep.txt", "keep");
        // Local files we'll upload one at a time (each upload refreshes the remote pane).
        createLocalFile("ul1.txt", "1");
        createLocalFile("ul2.txt", "2");
        createLocalFile("ul3.txt", "3");
        loginToServer();

        JTable remoteTable = remoteBrowser().getFileTable();
        JTableFixture remote = new JTableFixture(robot, remoteTable);
        JTableFixture local = new JTableFixture(robot, localBrowser().getFileTable());

        // Click keep.txt on the remote pane to select it (a real mouse click → the row highlights).
        int keepRow = findRowByName(remote, "keep.txt");
        assertTrue(keepRow >= 0, "keep.txt should be listed on the remote");
        clickRow(remote, keepRow);
        assertEquals("keep.txt", selectedName(remote),
            "precondition: clicking keep.txt selects/highlights it");

        // Upload several files; each completion re-lists the remote (buddy) pane.
        for (String f : new String[]{ "ul1.txt", "ul2.txt", "ul3.txt" }) {
            int row = awaitRow(local, f);
            assertTrue(row >= 0, f + " should be in the local pane");

            JobWaiter waiter = waitForJob(GJobDescription.UPLOAD_JOB_TYPE);
            rightClickAndSelectMenuItem(local, row, "Upload");
            waiter.await(15);
            assertTrue(Files.exists(serverRoot.resolve(f)), f + " should have uploaded");

            // keep.txt must still be selected after this upload's refresh
            assertEquals("keep.txt", awaitSelectedName(remote, "keep.txt"),
                "remote selection should survive the refresh after uploading " + f);
        }

        // ...and after an explicit refresh too.
        JobWaiter refreshWaiter = waitForJob(GJobDescription.REFRESH_JOB_TYPE);
        SwingUtilities.invokeLater(() -> remoteBrowser().refresh(null));
        refreshWaiter.await(15);
        assertEquals("keep.txt", awaitSelectedName(remote, "keep.txt"),
            "remote selection should survive an explicit refresh after the uploads");
    }

    private String selectedName(JTableFixture table) {
        int r = table.target().getSelectedRow();
        if (r < 0) return null;
        return table.valueAt(TableCell.row(r).column(0));
    }

    /** Selection is restored on the EDT during job-completion handling; poll briefly. */
    private String awaitSelectedName(JTableFixture table, String expected) throws Exception {
        String name = null;
        for (int i = 0; i < 50; i++) {
            robot.waitForIdle();
            name = selectedName(table);
            if (expected.equals(name)) break;
            Thread.sleep(100);
        }
        return name;
    }

    /** Poll for a row to appear (panes list asynchronously). */
    private int awaitRow(JTableFixture table, String name) throws Exception {
        int row = -1;
        for (int i = 0; i < 50 && row < 0; i++) {
            robot.waitForIdle();
            row = findRowByName(table, name);
            if (row < 0) Thread.sleep(100);
        }
        return row;
    }
}
