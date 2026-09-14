package com.gideonsoftworks.ui;

import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui") class UploadFolderUITest extends UITestBase {
    @Test void test() throws Exception {
        Files.createDirectories(localRoot.resolve("ul_folder"));
        Files.writeString(localRoot.resolve("ul_folder/uf1.txt"), "uf1");
        Files.writeString(localRoot.resolve("ul_folder/uf2.txt"), "uf2");
        loginToServer();

        JTableFixture table = new JTableFixture(robot, localBrowser().getFileTable());
        int row = findRowByName(table, "ul_folder");
        assertTrue(row >= 0);

        JobWaiter waiter = waitForJob(GJobDescription.UPLOAD_JOB_TYPE);
        rightClickAndSelectMenuItem(table, row, "Upload");
        waiter.await(30);

        assertTrue(Files.isDirectory(serverRoot.resolve("ul_folder")));
        assertTrue(Files.exists(serverRoot.resolve("ul_folder/uf1.txt")));
    }
}
