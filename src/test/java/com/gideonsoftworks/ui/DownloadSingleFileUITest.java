package com.gideonsoftworks.ui;

import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui") class DownloadSingleFileUITest extends UITestBase {
    @Test void test() throws Exception {
        createServerFile("dl_single.txt", "single file content");
        loginToServer();

        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
        int row = findRowByName(table, "dl_single.txt");
        assertTrue(row >= 0);

        JobWaiter waiter = waitForJob(GJobDescription.DOWNLOAD_JOB_TYPE);
        rightClickAndSelectMenuItem(table, row, "Download");
        waiter.await(15);

        Path localFile = localRoot.resolve("dl_single.txt");
        assertTrue(Files.exists(localFile));
        assertEquals("single file content", Files.readString(localFile));
    }
}
