package com.gideonsoftworks.ui;

import com.gideonsoftworks.gcore.GConnection;
import org.assertj.swing.timing.Condition;
import org.assertj.swing.timing.Pause;
import org.junit.jupiter.api.*;

import javax.swing.*;
import java.awt.event.WindowEvent;

import static org.junit.jupiter.api.Assertions.*;

/** Closing a browser window disconnects the connections of every tab in it. */
@Tag("ui") class CloseWindowDisconnectsUITest extends UITestBase {

    @Test void closingTheWindowDisconnectsItsConnection() throws Exception {
        loginToServer();
        final GConnection conn = remoteBrowser().getConnection();
        assertTrue(conn.getIsConnected());

        SwingUtilities.invokeAndWait(() ->
            browserWindow.dispatchEvent(new WindowEvent(browserWindow, WindowEvent.WINDOW_CLOSING)));
        robot.waitForIdle();

        Pause.pause(new Condition("window's connection disconnected") {
            @Override public boolean test() {
                try { return !conn.getIsConnected(); } catch (Exception e) { return true; }
            }
        }, 15_000);
    }
}
