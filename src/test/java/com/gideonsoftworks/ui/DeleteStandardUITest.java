package com.gideonsoftworks.ui;

import com.gideonsoftworks.gideon.preferences.GSFTPPreferences;
import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;
import javax.swing.*;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui") class DeleteStandardUITest extends UITestBase {
    @Test void test() throws Exception {
        GSFTPPreferences.setUseExec(false);
        createServerDir("del_std");
        createServerFileInDir("del_std", "x.txt", "x");
        loginToServer();

        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
        int row = findRowByName(table, "del_std");
        assertTrue(row >= 0);

        Timer autoDismiss = new Timer(300, e -> clickButtonInDialog("Yes", "OK"));
        autoDismiss.setRepeats(true);
        autoDismiss.start();

        try {
            JobWaiter waiter = waitForJob(GJobDescription.DELETE_JOB_TYPE);
            rightClickAndSelectMenuItem(table, row, "Delete...");
            waiter.await(15);
        } finally {
            autoDismiss.stop();
            GSFTPPreferences.setUseExec(true);
        }

        assertFalse(Files.exists(serverRoot.resolve("del_std")));
    }
}
