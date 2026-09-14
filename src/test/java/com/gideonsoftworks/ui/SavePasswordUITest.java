package com.gideonsoftworks.ui;

import com.gideonsoftworks.gcore.*;
import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.timing.Condition;
import org.assertj.swing.timing.Pause;
import org.junit.jupiter.api.*;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the changed-password flow in GBrowserPanelController's post-login handling:
 * when a host's password is already stored, connecting with a different password must
 * silently refresh the stored copy — no "Save Password" re-prompt.
 *
 * The keyring is disabled under test (-Drelaysftp.keyring.disabled=true), so
 * GKeyringManager holds passwords in an in-memory map: seeding it before the connect
 * simulates a previous session having saved the password, and the test asserts
 * against that map afterwards.
 */
@Tag("ui") class SavePasswordUITest extends UITestBase {

    @Override void startBackend() throws Exception {
        super.startBackend();
        // The server must accept the new password for the changed-password login to succeed.
        sshd.setPasswordAuthenticator((u, p, s) ->
            "test".equals(u) && ("test".equals(p) || "changed".equals(p)));
    }

    @Test void test() throws Exception {
        GBookmark bm = new GBookmark("test", "localhost", toSftpPath(serverRoot),
            "test", null, sftpPort, GBookmark.G_SFTP_PROTOCOL);
        String key = GBookmarkStore.passwordKey(bm);

        // Simulate a previous session: password saved to the keyring for this bookmark.
        GKeyringManager.setPassword(key, "test");
        GBookmarkManager.addBookmark(bm);

        SwingUtilities.invokeAndWait(() -> remoteBrowser().showLoginPanel(bm));
        robot.waitForIdle();

        // The stored password is pre-filled; the user types a different one over it.
        // (window.textBox matches only the SHOWING passwordField — the FTP/SFTP/FTPS
        // cards each have one with the same name.)
        window.textBox("passwordField").requireText("test").setText("changed");

        boolean[] promptShown = { false };
        Timer watchPrompt = new Timer(200, e -> {
            for (java.awt.Window w : java.awt.Window.getWindows()) {
                if (w instanceof JDialog && w.isVisible()
                        && "Save Password".equals(((JDialog) w).getTitle())) {
                    promptShown[0] = true;
                    clickButtonInDialog("No");   // dismiss so the test can't hang
                }
            }
        });
        watchPrompt.setRepeats(true);
        watchPrompt.start();
        try {
            JobWaiter waiter = waitForJob(GJobDescription.LOGIN_JOB_TYPE);
            window.button("connectButton").click();
            waiter.await(15);
            // The refresh runs on the EDT after the login job completes, so poll the store.
            Pause.pause(new Condition("stored password refreshed to the changed one") {
                @Override public boolean test() {
                    return "changed".equals(GKeyringManager.getPassword(key));
                }
            }, 10_000);
        } finally {
            watchPrompt.stop();
        }
        assertFalse(promptShown[0],
            "changed password should be saved silently, without re-prompting");
        assertEquals("changed", GKeyringManager.getPassword(key));
    }
}
