package com.gideonsoftworks.ui;

import com.gideonsoftworks.gideon.preferences.GSFTPPreferences;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Gate test: with "Monitor remote directory for changes" turned OFF, the poller must not
 * start, so an out-of-band server change is NOT auto-reflected in the remote pane.
 */
@Tag("ui") class MtimeMonitorDisabledUITest extends AbstractMtimeUITest {

    @Test void monitoringOff_doesNotAutoRefresh() throws Exception {
        String previousInterval = System.getProperty(INTERVAL_PROP);
        boolean previousMonitor = GSFTPPreferences.monitorRemoteChangesOn();
        System.setProperty(INTERVAL_PROP, "400");
        GSFTPPreferences.setMonitorRemoteChangesOn(false);   // read at login, when the timer decision is made
        try {
            createServerFile("initial.txt", "initial");
            loginToServer();

            // Generous window: several poll intervals. With monitoring off the timer never
            // starts, so appeared.txt should stay invisible until an explicit refresh.
            assertFalse(serverChangeAutoAppears(2500),
                "with monitoring disabled, the remote pane must NOT auto-refresh");
        } finally {
            GSFTPPreferences.setMonitorRemoteChangesOn(previousMonitor);
            if (previousInterval == null) System.clearProperty(INTERVAL_PROP);
            else System.setProperty(INTERVAL_PROP, previousInterval);
        }
    }
}
