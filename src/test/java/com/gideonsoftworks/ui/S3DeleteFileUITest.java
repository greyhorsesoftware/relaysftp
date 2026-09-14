package com.gideonsoftworks.ui;

import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;
import javax.swing.*;

import static org.junit.jupiter.api.Assertions.*;

/** Delete a single S3 object via the browser context menu. */
@Tag("ui")
class S3DeleteFileUITest extends S3UITestBase {

    @Test
    void test() throws Exception {
        seedObject("doomed.txt", "bye");
        seedObject("keep.txt", "stay");
        loginToServer();

        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
        int row = findRowByName(table, "doomed.txt");
        assertTrue(row >= 0, "doomed.txt should be listed");

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

        assertFalse(objectExists("doomed.txt"), "deleted object should be gone");
        assertTrue(objectExists("keep.txt"), "other object should remain");

        // the row is removed in place from the model - no re-list involved
        waitForRowAbsent(table, "doomed.txt", 5);
        assertTrue(findRowByName(table, "keep.txt") >= 0, "keep.txt row must survive");
    }
}
