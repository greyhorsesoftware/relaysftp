package com.gideonsoftworks.ui;

import com.gideonsoftworks.gideon.preferences.GSFTPPreferences;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Decoupling test: monitoring works over plain SFTP with Use-Exec OFF. Because the mtime
 * check now uses native SFTP stat (no shell exec), a server with exec disabled still gets
 * auto-refresh as long as "Monitor remote directory for changes" is on. This is the SFTP-only
 * case that the shell-based implementation could not serve.
 */
@Tag("ui") class MtimeMonitorNoExecUITest extends AbstractMtimeUITest {

    @Test void noExec_monitoringOn_stillAutoRefreshes() throws Exception {
        String previousInterval = System.getProperty(INTERVAL_PROP);
        boolean previousExec = GSFTPPreferences.useExec();
        System.setProperty(INTERVAL_PROP, "400");
        GSFTPPreferences.setUseExec(false);   // no shell exec — mtime must still work via SFTP stat
        try {
            createServerFile("initial.txt", "initial");
            loginToServer();

            assertTrue(serverChangeAutoAppears(10_000),
                "monitoring should auto-refresh over plain SFTP even with Use-Exec disabled");
        } finally {
            GSFTPPreferences.setUseExec(previousExec);
            if (previousInterval == null) System.clearProperty(INTERVAL_PROP);
            else System.setProperty(INTERVAL_PROP, previousInterval);
        }
    }
}
