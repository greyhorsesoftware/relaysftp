package com.gideonsoftworks.ui;

import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Upload a local folder (with nested files) to S3. */
@Tag("ui")
class S3UploadFolderUITest extends S3UITestBase {

    @Test
    void test() throws Exception {
        Path folder = Files.createDirectories(localRoot.resolve("upfolder"));
        Files.writeString(folder.resolve("a.txt"), "a");
        Files.writeString(folder.resolve("b.txt"), "b");
        Files.createDirectories(folder.resolve("sub"));
        Files.writeString(folder.resolve("sub").resolve("c.txt"), "c");

        loginToServer();

        JTableFixture table = new JTableFixture(robot, localBrowser().getFileTable());
        int row = findRowByName(table, "upfolder");
        assertTrue(row >= 0, "upfolder should be in the local pane");

        JobWaiter waiter = waitForJob(GJobDescription.UPLOAD_JOB_TYPE);
        rightClickAndSelectMenuItem(table, row, "Upload");
        waiter.await(30);

        assertTrue(objectExists("upfolder/a.txt"), "a.txt should be uploaded");
        assertTrue(objectExists("upfolder/b.txt"), "b.txt should be uploaded");
        assertTrue(objectExists("upfolder/sub/c.txt"), "nested c.txt should be uploaded");
    }
}
