package com.gideonsoftworks.ui;

import com.gideonsoftworks.gideon.browser.GBrowserWindow;
import com.gideonsoftworks.gideon.preferences.GSFTPPreferences;
import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;
import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises a remote-only browser window (File > New Browser > New Remote Only).
 * Verifies that with no local pane we can still connect over SFTP and perform
 * basic remote file operations (rename, delete).
 */
@Tag("ui") class RemoteOnlyBrowserUITest extends UITestBase {

    @Override GBrowserWindow createBrowserWindow() {
        return new GBrowserWindow(frameManager, null, true);
    }

    @Test void test() throws Exception {
        GSFTPPreferences.setUseExec(false);

        // The window is remote-only: there should be no local browser pane.
        assertNull(localBrowser(), "remote-only window should have no local pane");
        assertFalse(browserWindow.showsLocalFileArea(), "remote-only window should not show local files");

        createServerFile("ro_rename.txt", "rename me");
        createServerDir("ro_delete_dir");
        createServerFileInDir("ro_delete_dir", "x.txt", "x");
        createServerFile("ro_download.txt", "download me");

        loginToServer();

        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());

        // --- Rename a remote file ---
        int renameRow = awaitRowByName(table, "ro_rename.txt");
        assertTrue(renameRow >= 0, "ro_rename.txt should be listed on the remote");

        Timer autoRename = new Timer(500, e -> {
            for (java.awt.Window w : java.awt.Window.getWindows()) {
                if (w instanceof JDialog && w.isVisible() && "Rename...".equals(((JDialog) w).getTitle())) {
                    JTextField field = findTextField(w);
                    if (field != null) {
                        field.selectAll();
                        field.replaceSelection("ro_renamed.txt");
                        clickButton(w, "OK", "Rename");
                    }
                }
            }
        });
        autoRename.setRepeats(true);
        autoRename.start();
        try {
            JobWaiter waiter = waitForJob(GJobDescription.RENAME_JOB_TYPE);
            rightClickAndSelectMenuItem(table, renameRow, "Rename...");
            waiter.await(10);
        } finally {
            autoRename.stop();
        }

        assertFalse(Files.exists(serverRoot.resolve("ro_rename.txt")));
        assertTrue(Files.exists(serverRoot.resolve("ro_renamed.txt")));

        // --- Delete a remote directory ---
        int deleteRow = awaitRowByName(table, "ro_delete_dir");
        assertTrue(deleteRow >= 0, "ro_delete_dir should be listed on the remote");

        Timer autoDismiss = new Timer(300, e -> clickButtonInDialog("Yes", "OK"));
        autoDismiss.setRepeats(true);
        autoDismiss.start();
        try {
            JobWaiter waiter = waitForJob(GJobDescription.DELETE_JOB_TYPE);
            rightClickAndSelectMenuItem(table, deleteRow, "Delete...");
            waiter.await(15);
        } finally {
            autoDismiss.stop();
            GSFTPPreferences.setUseExec(true);
        }

        assertFalse(Files.exists(serverRoot.resolve("ro_delete_dir")));

        // --- Download a remote file ---
        // With no local pane, download falls back to the user's ~/Downloads folder.
        // Redirect user.home to a temp dir so we test the real path without touching the real one.
        String originalHome = System.getProperty("user.home");
        Path fakeHome = Files.createDirectories(tempDir.resolve("home"));
        Path downloads = fakeHome.resolve("Downloads");
        System.setProperty("user.home", fakeHome.toAbsolutePath().toString());
        try {
            // The remote table re-lists asynchronously after the delete refresh.
            int downloadRow = awaitRowByName(table, "ro_download.txt");
            assertTrue(downloadRow >= 0, "ro_download.txt should be listed on the remote");

            JobWaiter waiter = waitForJob(GJobDescription.DOWNLOAD_JOB_TYPE);
            rightClickAndSelectMenuItem(table, downloadRow, "Download");
            waiter.await(15);

            Path localFile = downloads.resolve("ro_download.txt");
            assertTrue(Files.exists(localFile), "downloaded file should land in the user's Downloads folder");
            assertEquals("download me", Files.readString(localFile));
        } finally {
            System.setProperty("user.home", originalHome);
        }
    }

    /** Polls for a row to appear (the remote table refreshes asynchronously after operations). */
    private int awaitRowByName(JTableFixture table, String name) throws Exception {
        int row = -1;
        for (int attempt = 0; attempt < 50 && row < 0; attempt++) {
            robot.waitForIdle();
            row = findRowByName(table, name);
            if (row < 0) Thread.sleep(100);
        }
        return row;
    }

    private JTextField findTextField(java.awt.Container c) {
        for (java.awt.Component comp : c.getComponents()) {
            if (comp instanceof JTextField) return (JTextField) comp;
            if (comp instanceof java.awt.Container) {
                JTextField f = findTextField((java.awt.Container) comp);
                if (f != null) return f;
            }
        }
        return null;
    }

    private void clickButton(java.awt.Container c, String... texts) {
        for (java.awt.Component comp : getAllComponents(c)) {
            if (comp instanceof JButton) {
                for (String t : texts) {
                    if (t.equals(((JButton) comp).getText())) {
                        ((JButton) comp).doClick();
                        return;
                    }
                }
            }
        }
    }
}
