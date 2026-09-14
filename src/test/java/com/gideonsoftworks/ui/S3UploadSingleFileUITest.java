package com.gideonsoftworks.ui;

import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/** Upload a single local file to S3 (via TransferManager — the OOM-safe path). */
@Tag("ui")
class S3UploadSingleFileUITest extends S3UITestBase {

    @Test
    void test() throws Exception {
        createLocalFile("up.txt", "upload me to S3");
        loginToServer();

        JTableFixture table = new JTableFixture(robot, localBrowser().getFileTable());
        int row = findRowByName(table, "up.txt");
        assertTrue(row >= 0, "up.txt should be in the local pane");

        JobWaiter waiter = waitForJob(GJobDescription.UPLOAD_JOB_TYPE);
        rightClickAndSelectMenuItem(table, row, "Upload");
        waiter.await(20);

        assertTrue(objectExists("up.txt"), "object should exist in the bucket after upload");
    }
}
