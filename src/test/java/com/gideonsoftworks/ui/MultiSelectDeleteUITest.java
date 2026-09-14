package com.gideonsoftworks.ui;

import com.gideonsoftworks.gideon.preferences.GSFTPPreferences;
import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;
import javax.swing.*;
import javax.swing.event.TableModelListener;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Multi-select delete from the context menu (roadmap 3.6): both selected files are
 * deleted, the rows are removed IN PLACE - zero REFRESH jobs, and the listing never
 * renders empty while the deletes run.
 */
@Tag("ui") class MultiSelectDeleteUITest extends UITestBase {
    @Test void test() throws Exception {
        boolean previousMonitor = GSFTPPreferences.monitorRemoteChangesOn();
        // a poller refresh would pollute the zero-REFRESH assertion
        GSFTPPreferences.setMonitorRemoteChangesOn(false);
        try {
            createServerFile("del_a.txt", "delete me a");
            createServerFile("del_b.txt", "delete me b");
            createServerFile("keep.txt", "survivor");
            loginToServer();

            JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
            int rowA = findRowByName(table, "del_a.txt");
            int rowB = findRowByName(table, "del_b.txt");
            assertTrue(rowA >= 0 && rowB >= 0, "both del_ files should be listed");

            RefreshCounter refreshes = countRefreshJobs();

            // record the minimum row count the EDT ever renders - the old behavior
            // blanked the table to 0 rows during each post-delete re-list
            AtomicInteger minRowCount = new AtomicInteger(Integer.MAX_VALUE);
            JTable jt = remoteBrowser().getFileTable();
            TableModelListener minTracker = e ->
                minRowCount.getAndAccumulate(jt.getModel().getRowCount(), Math::min);
            jt.getModel().addTableModelListener(minTracker);

            Timer autoDismiss = new Timer(300, e -> clickButtonInDialog("Yes", "OK"));
            autoDismiss.setRepeats(true);
            autoDismiss.start();

            try {
                JobWaiter waiter = waitForJobs(GJobDescription.DELETE_JOB_TYPE, 2);
                rightClickSelectionAndSelectMenuItem(table, new int[]{ rowA, rowB }, "Delete...");
                waiter.await(15);
            } finally {
                autoDismiss.stop();
            }
            robot.waitForIdle();

            assertFalse(Files.exists(serverRoot.resolve("del_a.txt")));
            assertFalse(Files.exists(serverRoot.resolve("del_b.txt")));
            assertTrue(Files.exists(serverRoot.resolve("keep.txt")));

            waitForRowAbsent(table, "del_a.txt", 5);
            waitForRowAbsent(table, "del_b.txt", 5);
            assertTrue(findRowByName(table, "keep.txt") >= 0, "keep.txt row must survive");

            assertEquals(0, refreshes.completed.get(),
                "delete must update the listing in place - no REFRESH jobs");
            assertTrue(minRowCount.get() >= 1,
                "listing must never render empty during deletes (min observed: "
                    + minRowCount.get() + ")");
        } finally {
            GSFTPPreferences.setMonitorRemoteChangesOn(previousMonitor);
        }
    }
}
