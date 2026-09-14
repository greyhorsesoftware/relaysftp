package com.gideonsoftworks.ui;

import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;
import javax.swing.*;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui") class DeleteSingleFileUITest extends UITestBase {
    @Test void test() throws Exception {
        createServerFile("del_file.txt", "delete me");
        loginToServer();

        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
        int row = findRowByName(table, "del_file.txt");
        assertTrue(row >= 0);

        Timer autoDismiss = new Timer(300, e -> clickButtonInDialog("Yes", "OK"));
        autoDismiss.setRepeats(true);
        autoDismiss.start();

        try {
            JobWaiter waiter = waitForJob(GJobDescription.DELETE_JOB_TYPE);
            rightClickAndSelectMenuItem(table, row, "Delete...");
            waiter.await(10);
        } finally {
            autoDismiss.stop();
        }

        assertFalse(Files.exists(serverRoot.resolve("del_file.txt")));

        // the row is removed in place from the model - no re-list involved
        waitForRowAbsent(table, "del_file.txt", 5);
    }
}
