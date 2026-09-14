package com.gideonsoftworks.ui;

import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui") class UploadSingleFileUITest extends UITestBase {
    @Test void test() throws Exception {
        createLocalFile("ul_test.txt", "upload me");
        loginToServer();

        JTableFixture table = new JTableFixture(robot, localBrowser().getFileTable());
        int row = findRowByName(table, "ul_test.txt");
        assertTrue(row >= 0);

        JobWaiter waiter = waitForJob(GJobDescription.UPLOAD_JOB_TYPE);
        rightClickAndSelectMenuItem(table, row, "Upload");
        waiter.await(15);

        assertTrue(Files.exists(serverRoot.resolve("ul_test.txt")));
        assertEquals("upload me", Files.readString(serverRoot.resolve("ul_test.txt")));
    }
}
