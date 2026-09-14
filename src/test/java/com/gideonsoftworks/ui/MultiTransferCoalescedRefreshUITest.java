package com.gideonsoftworks.ui;

import com.gideonsoftworks.gideon.preferences.GSFTPPreferences;
import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Coalesced buddy refresh (roadmap 3.6): a multi-select upload of 3 files must
 * refresh the destination pane exactly ONCE (after the last transfer), not once
 * per completed job.
 */
@Tag("ui") class MultiTransferCoalescedRefreshUITest extends UITestBase {
    @Test void test() throws Exception {
        boolean previousMonitor = GSFTPPreferences.monitorRemoteChangesOn();
        GSFTPPreferences.setMonitorRemoteChangesOn(false);
        try {
            createLocalFile("up_a.txt", "a");
            createLocalFile("up_b.txt", "b");
            createLocalFile("up_c.txt", "c");
            loginToServer();

            JTableFixture localTable = new JTableFixture(robot, localBrowser().getFileTable());
            int rowA = findRowByName(localTable, "up_a.txt");
            int rowB = findRowByName(localTable, "up_b.txt");
            int rowC = findRowByName(localTable, "up_c.txt");
            assertTrue(rowA >= 0 && rowB >= 0 && rowC >= 0, "all three up_ files should be listed locally");

            RefreshCounter refreshes = countRefreshJobs();

            JobWaiter waiter = waitForJobs(GJobDescription.UPLOAD_JOB_TYPE, 3);
            rightClickSelectionAndSelectMenuItem(localTable, new int[]{ rowA, rowB, rowC }, "Upload");
            waiter.await(30);
            robot.waitForIdle();

            assertTrue(Files.exists(serverRoot.resolve("up_a.txt")));
            assertTrue(Files.exists(serverRoot.resolve("up_b.txt")));
            assertTrue(Files.exists(serverRoot.resolve("up_c.txt")));

            // the single coalesced refresh must make all three visible in the remote pane
            JTableFixture remoteTable = new JTableFixture(robot, remoteBrowser().getFileTable());
            waitForRowPresent(remoteTable, "up_a.txt", 10);
            waitForRowPresent(remoteTable, "up_b.txt", 10);
            waitForRowPresent(remoteTable, "up_c.txt", 10);

            assertEquals(1, refreshes.completed.get(),
                "a 3-file upload batch must trigger exactly one destination refresh");
        } finally {
            GSFTPPreferences.setMonitorRemoteChangesOn(previousMonitor);
        }
    }
}
