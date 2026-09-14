package com.gideonsoftworks.ui;

import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;
import javax.swing.*;

import static org.junit.jupiter.api.Assertions.*;

/** Delete an S3 "folder" (common prefix) and all objects under it, via the context menu. */
@Tag("ui")
class S3DeleteFolderUITest extends S3UITestBase {

    @Test
    void test() throws Exception {
        seedObject("trash/a.txt", "a");
        seedObject("trash/b.txt", "b");
        seedObject("trash/sub/c.txt", "c");
        seedObject("survivor.txt", "x");
        loginToServer();

        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
        int row = findRowByName(table, "trash");
        assertTrue(row >= 0, "trash/ prefix should show as a folder");

        Timer autoDismiss = new Timer(300, e -> clickButtonInDialog("Yes", "OK"));
        autoDismiss.setRepeats(true);
        autoDismiss.start();
        try {
            JobWaiter waiter = waitForJob(GJobDescription.DELETE_JOB_TYPE);
            rightClickAndSelectMenuItem(table, row, "Delete...");
            waiter.await(15);
        } finally {
            autoDismiss.stop();
        }

        assertEquals(0, countObjects("trash/"), "all objects under the prefix should be removed");
        assertTrue(objectExists("survivor.txt"), "objects outside the prefix should remain");

        // the row is removed in place from the model - no re-list involved
        waitForRowAbsent(table, "trash", 5);
        assertTrue(findRowByName(table, "survivor.txt") >= 0, "survivor.txt row must remain");
    }
}
