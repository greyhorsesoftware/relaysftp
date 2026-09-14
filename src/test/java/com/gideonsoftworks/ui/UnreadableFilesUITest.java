package com.gideonsoftworks.ui;

import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledOnOs;
import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui") class UnreadableFilesUITest extends UITestBase {
    @Test
    @DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void test() throws Exception {
        createServerDir("perm_test");
        createServerFileInDir("perm_test", "readable.txt", "can read");
        createServerFileInDir("perm_test", "also_ok.txt", "also readable");
        createServerFileWithPerms("perm_test/no_read.txt", "secret", "200");
        loginToServer();

        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
        int row = findRowByName(table, "perm_test");
        assertTrue(row >= 0);

        Timer autoSkip = new Timer(300, e -> {
            for (java.awt.Window w : java.awt.Window.getWindows()) {
                if (w instanceof JDialog && w.isVisible()) {
                    String title = ((JDialog) w).getTitle();
                    if (title != null && title.contains("Unreadable")) {
                        clickButtonInDialog("Skip Files", "OK");
                    }
                }
            }
        });
        autoSkip.setRepeats(true);
        autoSkip.start();

        try {
            JobWaiter waiter = waitForJob(GJobDescription.DOWNLOAD_JOB_TYPE);
            rightClickAndSelectMenuItem(table, row, "Download");
            waiter.await(30);
        } finally {
            autoSkip.stop();
        }

        Path local = localRoot.resolve("perm_test");
        assertTrue(Files.exists(local.resolve("readable.txt")));
        assertTrue(Files.exists(local.resolve("also_ok.txt")));
        assertFalse(Files.exists(local.resolve("no_read.txt")), "Unreadable should be skipped");
    }
}
