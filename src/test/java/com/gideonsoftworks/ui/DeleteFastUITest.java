package com.gideonsoftworks.ui;

import com.gideonsoftworks.gideon.preferences.GSFTPPreferences;
import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledOnOs;
import javax.swing.*;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui") class DeleteFastUITest extends UITestBase {
    @Test
    @DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void test() throws Exception {
        GSFTPPreferences.setUseExec(true);
        createServerDir("del_fast");
        createServerFileInDir("del_fast", "a.txt", "a");
        loginToServer();

        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
        int row = findRowByName(table, "del_fast");
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

        assertFalse(Files.exists(serverRoot.resolve("del_fast")));

        // regression guard: the fast-delete path must unlink the folder from the
        // model itself - there is no follow-up re-list to hide a missing unlink
        waitForRowAbsent(table, "del_fast", 5);
    }
}
