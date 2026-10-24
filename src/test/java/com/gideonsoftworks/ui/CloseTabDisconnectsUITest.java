package com.gideonsoftworks.ui;

import com.gideonsoftworks.gcore.GConnection;
import com.gideonsoftworks.gideon.browser.GBrowserPanelController;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.timing.Condition;
import org.assertj.swing.timing.Pause;
import org.junit.jupiter.api.*;

import javax.swing.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Closing a tab disconnects its connection. GBrowserWindow.tabWillClose used to take a HashMap,
 * so it never overrode GFrame.tabWillClose(GTabBarItem) and a closed tab's session stayed open
 * (and kept pinging) until the whole window closed.
 */
@Tag("ui") class CloseTabDisconnectsUITest extends UITestBase {

    @Test void closingATabDisconnectsItsConnection() throws Exception {
        loginToServer();
        final GBrowserPanelController closingPane = remoteBrowser();
        final GConnection conn = closingPane.getConnection();
        assertTrue(conn.getIsConnected());
        int listenersBefore = closingPane.browserPanelListenerCount();

        // open a second tab so the window survives, then go back to the connected tab and close it
        SwingUtilities.invokeAndWait(() -> {
            browserWindow.newTab();
            JTabbedPane tp = browserWindow.getTabbedPane();
            for (int i = 0; i < tp.getTabCount() && remoteBrowser() != closingPane; i++) tp.setSelectedIndex(i);
        });
        robot.waitForIdle();
        assertSame(closingPane, remoteBrowser(), "the connected tab should be selected");

        SwingUtilities.invokeAndWait(() -> browserWindow.closeKeyTab());
        robot.waitForIdle();

        Pause.pause(new Condition("closed tab's connection disconnected") {
            @Override public boolean test() {
                try { return !conn.getIsConnected(); } catch (Exception e) { return true; }
            }
        }, 15_000);

        assertFalse(GuiActionRunner.execute(() -> GConnection.openConnections().contains(conn)),
            "closed tab's connection should be gone from the open-connection registry");
        assertEquals(listenersBefore - 1, closingPane.browserPanelListenerCount(),
            "the window should stop listening to the closed tab's pane");
        assertTrue(browserWindow.isDisplayable(), "the window itself stays open");
    }
}
