package com.gideonsoftworks.ui;

import com.gideonsoftworks.gcore.GConnection;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.server.FileHandle;
import org.apache.sshd.sftp.server.SftpEventListener;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.JTableFixture;
import org.assertj.swing.timing.Condition;
import org.assertj.swing.timing.Pause;
import org.junit.jupiter.api.*;

import javax.swing.*;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Closing a window while a job runs asks "are you sure?". Answering No must leave the window
 * connected: GFrame used to call windowWillClose() - which reaps the browser's connections -
 * before asking.
 */
@Tag("ui") class CloseWindowCancelKeepsConnectionUITest extends UITestBase {

    private final CountDownLatch releaseRead = new CountDownLatch(1);

    @Override
    void startBackend() throws Exception {
        super.startBackend();
        // hold reads of "slow.bin" so its download stays running until the test releases it
        ((SftpSubsystemFactory) sshd.getSubsystemFactories().get(0)).addSftpEventListener(new SftpEventListener() {
            @Override public void reading(ServerSession s, String remoteHandle, FileHandle h,
                                          long offset, byte[] data, int dataOffset, int dataLen) {
                if (h.getFile().getFileName().toString().equals("slow.bin")) {
                    try { releaseRead.await(30, TimeUnit.SECONDS); } catch (InterruptedException ignore) {}
                }
            }
        });
    }

    @Test void answeringNoKeepsTheWindowConnected() throws Exception {
        createServerFile("slow.bin", "held until released");
        loginToServer();
        final GConnection conn = remoteBrowser().getConnection();

        try {
            JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
            rightClickAndSelectMenuItem(table, findRowByName(table, "slow.bin"), "Download");
            Pause.pause(new Condition("download running") {
                @Override public boolean test() { return GuiActionRunner.execute(() -> jobManager().hasRunningJobs()); }
            }, 10_000);

            // the confirm dialog is modal, so post the close rather than waiting on it
            SwingUtilities.invokeLater(() ->
                browserWindow.dispatchEvent(new WindowEvent(browserWindow, WindowEvent.WINDOW_CLOSING)));
            Pause.pause(new Condition("are-you-sure dialog showing") {
                @Override public boolean test() {
                    return GuiActionRunner.execute(() -> {
                        for (Window w : Window.getWindows()) if (w instanceof JDialog && w.isVisible()) return true;
                        return false;
                    });
                }
            }, 10_000);
            SwingUtilities.invokeAndWait(() -> clickButtonInDialog("No"));
            robot.waitForIdle();
            Thread.sleep(1000);   // give a (wrongly) queued reaper disconnect time to land

            assertTrue(browserWindow.isDisplayable(), "the window stays open");
            assertTrue(conn.getIsConnected(), "answering No must not disconnect the window's connection");
            assertSame(conn, remoteBrowser().getConnection());
        } finally {
            releaseRead.countDown();
        }
    }
}
