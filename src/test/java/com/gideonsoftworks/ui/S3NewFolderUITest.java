package com.gideonsoftworks.ui;

import com.gideonsoftworks.gja.GJobDescription;
import org.junit.jupiter.api.*;
import javax.swing.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Create a new S3 "folder" (zero-byte key/ placeholder) through the browser's
 * New Folder action. The File-menu/toolbar widget isn't reachable on the browser
 * window in the test harness (no in-window menu bar), so this drives the same
 * controller entry point the action invokes — exercising the real NEWFOLDER job
 * → gpp_mkdir pipeline.
 */
@Tag("ui")
class S3NewFolderUITest extends S3UITestBase {

    @Test
    void test() throws Exception {
        loginToServer();

        JobWaiter waiter = waitForJob(GJobDescription.NEWFOLDER_JOB_TYPE);
        SwingUtilities.invokeAndWait(() -> remoteBrowser().newFolder("newdir", null));
        waiter.await(15);
        robot.waitForIdle();

        assertTrue(objectExists("newdir/"), "zero-byte folder marker should be created");
    }
}
