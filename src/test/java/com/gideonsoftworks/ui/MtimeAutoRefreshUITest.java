package com.gideonsoftworks.ui;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Default case: "Monitor remote directory for changes" on (default) and Use-Exec on. A file
 * appearing on the server out of band shows up in the remote pane with no user refresh.
 */
@Tag("ui") class MtimeAutoRefreshUITest extends AbstractMtimeUITest {

    @Test void remoteChangeAutoRefreshesPane() throws Exception {
        String previousInterval = System.getProperty(INTERVAL_PROP);
        System.setProperty(INTERVAL_PROP, "400");   // fast poll; read when the timer starts (at login)
        try {
            createServerFile("initial.txt", "initial");
            loginToServer();

            assertTrue(serverChangeAutoAppears(10_000),
                "the remote pane should auto-refresh and show appeared.txt without a user refresh");
        } finally {
            if (previousInterval == null) System.clearProperty(INTERVAL_PROP);
            else System.setProperty(INTERVAL_PROP, previousInterval);
        }
    }
}
