package com.gideonsoftworks.ui;

import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui") class DownloadDuplicateUITest extends UITestBase {
    @Test void test() throws Exception {
        createServerFile("dl_dup.txt", "dup content");
        loginToServer();
        createLocalFile("dl_dup.txt", "original");

        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
        int row = findRowByName(table, "dl_dup.txt");
        assertTrue(row >= 0);

        JobWaiter waiter = waitForJob(GJobDescription.DOWNLOAD_JOB_TYPE);
        rightClickAndSelectMenuItem(table, row, "Download");
        waiter.await(15);

        assertTrue(Files.exists(localRoot.resolve("dl_dup (1).txt")),
            "Should auto-rename with (1)");
    }
}
