package com.gideonsoftworks.ui;

import com.gideonsoftworks.gideon.getinfo.GGetInfoWindow;
import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;

import javax.swing.*;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Roadmap Phase 4 (typed listeners replacing the reflection event center):
 * - a Get Info window locks its permission controls when the pane disconnects,
 * - disposing the window really unregisters it (the old removeListener never removed),
 * - reconnecting in the same tab still delivers the window's "connected" notification, which the
 *   old disconnect() wiped along with every other listener (title/log registration went stale).
 */
@Tag("ui") class GetInfoDisconnectUITest extends UITestBase {

    @Test void disconnectLocksGetInfo_disposeUnregisters_reconnectStillNotifiesWindow() throws Exception {
        createServerFile("info.txt", "x");
        loginToServer();
        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
        int row = findRowByName(table, "info.txt");
        assertTrue(row >= 0);

        int baseline = remoteBrowser().browserPanelListenerCount();
        assertTrue(baseline >= 1, "the browser window itself listens for 'connected'");

        String menuLabel = System.getProperty("os.name").toLowerCase().contains("mac") ? "Get Info" : "Properties";
        rightClickAndSelectMenuItem(table, row, menuLabel);
        robot.waitForIdle();
        Thread.sleep(500);

        GGetInfoWindow info = null;
        for (Window w : Window.getWindows()) if (w instanceof GGetInfoWindow g && w.isVisible()) info = g;
        assertNotNull(info, "Get Info window should open");
        assertEquals(baseline + 1, remoteBrowser().browserPanelListenerCount(), "Get Info registers on the pane");

        FrameFixture infoFixture = new FrameFixture(robot, info);
        JTabbedPane tp = findTabbedPane(info);
        SwingUtilities.invokeAndWait(() -> tp.setSelectedIndex(1));
        robot.waitForIdle();
        infoFixture.checkBox("ownerRead").requireEnabled();

        // Disconnect: the window's disconnecting hook must lock the permission controls.
        JobWaiter dc = waitForJob(GJobDescription.DISCONNECT_JOB_TYPE);
        SwingUtilities.invokeAndWait(() -> remoteBrowser().disconnect(null));
        robot.waitForIdle();
        infoFixture.checkBox("ownerRead").requireDisabled();
        dc.await(15);
        robot.waitForIdle();

        // The window's own listener survived the disconnect (it used to be wiped with everything else).
        assertEquals(baseline + 1, remoteBrowser().browserPanelListenerCount(), "disconnect must not clear listeners");

        // Dispose: really unregistered.
        final GGetInfoWindow toClose = info;
        SwingUtilities.invokeAndWait(() -> { toClose.setVisible(false); toClose.dispose(); });
        robot.waitForIdle();
        assertEquals(baseline, remoteBrowser().browserPanelListenerCount(), "dispose must unregister the Get Info window");

        // Reconnect in the same tab: the window's connected hook runs again and the title names the host.
        JobWaiter login = waitForJob(GJobDescription.LOGIN_JOB_TYPE);
        SwingUtilities.invokeAndWait(() -> remoteBrowser().connectForBookmark(remoteBrowser().getBookmark()));
        robot.waitForIdle();
        try { window.button("connectButton").click(); } catch (org.assertj.swing.exception.ComponentLookupException ignore) {}
        login.await(15);
        robot.waitForIdle();
        String title = browserWindow.getTitle();
        assertTrue(title != null && title.contains("localhost"), "title after same-tab reconnect: " + title);
    }

    private static JTabbedPane findTabbedPane(Container c) {
        for (Component child : c.getComponents()) {
            if (child instanceof JTabbedPane t) return t;
            if (child instanceof Container cc) { JTabbedPane t = findTabbedPane(cc); if (t != null) return t; }
        }
        return null;
    }
}
