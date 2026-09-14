package com.gideonsoftworks.ui;

import com.gideonsoftworks.gja.GAbstractJobManagerListener;
import com.gideonsoftworks.gja.GJobDescription;
import com.gideonsoftworks.gja.GJobManagerEvent;
import com.gideonsoftworks.gja.GQueue;
import org.junit.jupiter.api.*;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Roadmap 3.1: the mtime poll runs as an MTIME_JOB_TYPE job on the task queue (never off-queue),
 * at most one is outstanding per pane, and disconnecting drops the poll instead of letting a late
 * tick log the connection back in.
 *
 * One login for the class (the poll interval is read when the timer starts, at login); the
 * disconnect case runs last because it ends the session.
 */
@Tag("ui")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MtimeQueuedJobUITest extends AbstractMtimeUITest {

    private String previousInterval;

    @BeforeAll
    void fastIntervalAndLogin() throws Exception {
        previousInterval = System.getProperty(INTERVAL_PROP);
        System.setProperty(INTERVAL_PROP, "150");
        createServerFile("initial.txt", "initial");
        JobWaiter firstPoll = waitForJob(GJobDescription.MTIME_JOB_TYPE);
        loginToServer();
        firstPoll.await(10);                                     // a poll ran as a queued job
    }

    @AfterAll
    void restoreInterval() {
        if (previousInterval == null) System.clearProperty(INTERVAL_PROP);
        else System.setProperty(INTERVAL_PROP, previousInterval);
    }

    @Test @Order(1)
    void pollRunsAsTaskQueueJob_andStillAutoRefreshes() throws Exception {
        assertTrue(serverChangeAutoAppears(10_000), "auto-refresh must still work through the queued poll");
    }

    @Test @Order(2)
    void pollsKeepCompletingWithReadings_andAtMostOneOutstanding() throws Exception {
        // every completed poll must carry a real reading (>= 0); count them over the window
        AtomicInteger completedWithReading = new AtomicInteger();
        jobManager().addStatusListener(new GAbstractJobManagerListener() {
            @Override public void jm_jobCompleted(GJobManagerEvent e) {
                GJobDescription jd = e.getJobDescription();
                if (jd != null && jd.getJobType() == GJobDescription.MTIME_JOB_TYPE
                    && jd.getProperty(GJobDescription.MTIME_RESULT_JOB_PROP) instanceof Long l && l >= 0)
                    completedWithReading.incrementAndGet();
            }
        });

        GQueue task = jobManager().getTaskQueue();
        int max = 0;
        long end = System.currentTimeMillis() + 2_000;           // ~13 ticks at 150 ms
        while (System.currentTimeMillis() < end) {
            int n = 0;
            for (int i = 0; i < task.getJobCount(); i++)
                if (task.getJob(i).getJobType() == GJobDescription.MTIME_JOB_TYPE) n++;
            max = Math.max(max, n);
            Thread.sleep(10);
        }
        assertTrue(max <= 1, "never more than one MTIME job queued at a time, saw " + max);
        assertTrue(completedWithReading.get() >= 3,
            "polling must keep running with real readings (150 ms interval, 2 s window), saw " + completedWithReading.get());
    }

    @Test @Order(3)
    void disconnectDropsPendingPoll() throws Exception {
        AtomicBoolean pollAfterDisconnect = new AtomicBoolean();
        AtomicBoolean disconnecting = new AtomicBoolean();
        jobManager().addStatusListener(new GAbstractJobManagerListener() {
            @Override public void jm_jobCompleted(GJobManagerEvent e) {
                GJobDescription jd = e.getJobDescription();
                if (jd == null) return;
                if (jd.getJobType() == GJobDescription.DISCONNECT_JOB_TYPE) disconnecting.set(true);
                else if (disconnecting.get() && jd.getJobType() == GJobDescription.MTIME_JOB_TYPE) pollAfterDisconnect.set(true);
            }
        });

        JobWaiter dc = waitForJob(GJobDescription.DISCONNECT_JOB_TYPE);
        SwingUtilities.invokeAndWait(() -> remoteBrowser().disconnect(null));
        dc.await(15);
        Thread.sleep(1_000);                                     // ~6 would-be ticks
        robot.waitForIdle();

        assertFalse(pollAfterDisconnect.get(), "no MTIME job may complete after DISCONNECT");
        assertFalse(remoteBrowser().getConnection() != null && remoteBrowser().getConnection().getIsConnected(),
            "a late poll must not have logged the connection back in");
    }
}
