package com.gideonsoftworks.ui;

import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Download an S3 "folder" (common prefix) and its contents to the local pane. */
@Tag("ui")
class S3DownloadFolderUITest extends S3UITestBase {

    @Test
    void test() throws Exception {
        seedObject("dlfolder/child1.txt", "child1");
        seedObject("dlfolder/child2.txt", "child2");
        seedObject("dlfolder/sub/deep.txt", "deep");
        loginToServer();

        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
        int row = findRowByName(table, "dlfolder");
        assertTrue(row >= 0, "dlfolder/ prefix should show as a folder");

        JobWaiter waiter = waitForJob(GJobDescription.DOWNLOAD_JOB_TYPE);
        rightClickAndSelectMenuItem(table, row, "Download");
        waiter.await(30);

        Path f = localRoot.resolve("dlfolder");
        assertTrue(Files.isDirectory(f), "local folder should be created");
        assertEquals("child1", Files.readString(f.resolve("child1.txt")));
        assertEquals("child2", Files.readString(f.resolve("child2.txt")));
        assertTrue(Files.exists(f.resolve("sub/deep.txt")), "nested object should be downloaded");
    }
}
