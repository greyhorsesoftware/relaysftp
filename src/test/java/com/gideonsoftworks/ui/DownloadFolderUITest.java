package com.gideonsoftworks.ui;

import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui") class DownloadFolderUITest extends UITestBase {
    @Test void test() throws Exception {
        createServerDir("dl_folder");
        createServerFileInDir("dl_folder", "child1.txt", "child1");
        createServerFileInDir("dl_folder", "child2.txt", "child2");
        createServerDir("dl_folder/sub");
        createServerFileInDir("dl_folder/sub", "deep.txt", "deep");
        loginToServer();

        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
        int row = findRowByName(table, "dl_folder");
        assertTrue(row >= 0);

        JobWaiter waiter = waitForJob(GJobDescription.DOWNLOAD_JOB_TYPE);
        rightClickAndSelectMenuItem(table, row, "Download");
        waiter.await(30);

        Path f = localRoot.resolve("dl_folder");
        assertTrue(Files.isDirectory(f));
        assertTrue(Files.exists(f.resolve("child1.txt")));
        assertTrue(Files.exists(f.resolve("child2.txt")));
        assertTrue(Files.exists(f.resolve("sub/deep.txt")));
    }
}
