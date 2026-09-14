package com.gideonsoftworks.ui;

import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Download a single S3 object to the local pane (via TransferManager). */
@Tag("ui")
class S3DownloadSingleFileUITest extends S3UITestBase {

    @Test
    void test() throws Exception {
        seedObject("dl.txt", "downloaded content");
        loginToServer();

        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
        int row = findRowByName(table, "dl.txt");
        assertTrue(row >= 0, "dl.txt should be listed");

        JobWaiter waiter = waitForJob(GJobDescription.DOWNLOAD_JOB_TYPE);
        rightClickAndSelectMenuItem(table, row, "Download");
        waiter.await(20);

        Path localFile = localRoot.resolve("dl.txt");
        assertTrue(Files.exists(localFile), "file should be downloaded locally");
        assertEquals("downloaded content", Files.readString(localFile));
    }
}
