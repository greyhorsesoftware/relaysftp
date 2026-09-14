package com.gideonsoftworks.ui;

import org.assertj.swing.fixture.JTableFixture;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Shared harness for the remote-directory-monitoring (mtime auto-refresh) UI tests.
 *
 * Each concrete test configures the relevant preferences, logs in, then drives an
 * out-of-band server change and checks whether the remote pane auto-refreshes. The tests
 * assert observable behaviour (does the pane update on its own?) rather than mechanism, so
 * they keep passing when the poller is later moved onto the task queue (roadmap 3.1).
 */
abstract class AbstractMtimeUITest extends UITestBase {

    static final String INTERVAL_PROP = "relaysftp.mtime.interval.ms";

    /**
     * Precondition: already logged in, "initial.txt" already on the server. Lets the poller
     * take its baseline reading, then creates "appeared.txt" on the server out-of-band (NOT
     * through the app) and reports whether it shows up in the remote pane within the window.
     */
    boolean serverChangeAutoAppears(long maxWaitMs) throws Exception {
        JTableFixture remote = new JTableFixture(robot, remoteBrowser().getFileTable());
        assertTrue(awaitRow(remote, "initial.txt", 6000) >= 0,
            "precondition: initial.txt should be listed on the remote pane after login");

        // Let the poller take its baseline mtime reading and clear the 1-second mtime
        // resolution boundary before we mutate the directory.
        Thread.sleep(1600);

        assertEquals(-1, findRowByName(remote, "appeared.txt"),
            "sanity: appeared.txt must not be visible before it is created");
        createServerFile("appeared.txt", "surprise");

        // No explicit refresh() — the poller must notice the mtime change on its own.
        return awaitRow(remote, "appeared.txt", maxWaitMs) >= 0;
    }

    /** Poll for a row to appear, up to maxWaitMs. */
    int awaitRow(JTableFixture table, String name, long maxWaitMs) throws Exception {
        long deadline = System.nanoTime() + maxWaitMs * 1_000_000L;
        int row = findRowByName(table, name);
        while (row < 0 && System.nanoTime() < deadline) {
            robot.waitForIdle();
            Thread.sleep(100);
            row = findRowByName(table, name);
        }
        return row;
    }
}
