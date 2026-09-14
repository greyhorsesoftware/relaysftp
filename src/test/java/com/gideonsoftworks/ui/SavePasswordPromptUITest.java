package com.gideonsoftworks.ui;

import com.gideonsoftworks.gcore.*;
import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.timing.Condition;
import org.assertj.swing.timing.Pause;
import org.junit.jupiter.api.*;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the first-time save-password flow: after a successful login with a password
 * that isn't stored yet, the "Save Password" prompt appears and clicking Yes stores it.
 * The keyring is disabled under test, so GKeyringManager's in-memory map is asserted.
 */
@Tag("ui") class SavePasswordPromptUITest extends UITestBase {

    @Test void test() throws Exception {
        GBookmark bm = new GBookmark("test", "localhost", toSftpPath(serverRoot),
            "test", "test", sftpPort, GBookmark.G_SFTP_PROTOCOL);
        bm.setLocalHomePath(localRoot.toAbsolutePath().toString());
        String key = GBookmarkStore.passwordKey(bm);

        SwingUtilities.invokeAndWait(() -> remoteBrowser().connectForBookmark(bm));
        robot.waitForIdle();

        Timer clickYes = new Timer(300, e -> clickButtonInDialog("Yes"));
        clickYes.setRepeats(true);
        clickYes.start();
        try {
            JobWaiter waiter = waitForJob(GJobDescription.LOGIN_JOB_TYPE);
            window.button("connectButton").click();
            waiter.await(15);
            // The prompt fires on the EDT after the login job completes, so poll the store.
            Pause.pause(new Condition("password stored after clicking Yes") {
                @Override public boolean test() {
                    return "test".equals(GKeyringManager.getPassword(key));
                }
            }, 10_000);
        } finally {
            clickYes.stop();
        }
        assertTrue(GBookmarkManager.hasStoredPassword(bm));
    }
}
