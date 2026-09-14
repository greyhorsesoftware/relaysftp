package com.gideonsoftworks.gja;

import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-thread safety of GJobStatus: counters incremented on a worker thread
 * must total exactly, and state written on a worker must become visible to
 * another thread (the app reads these from 1s EDT timers).
 */
class GJobStatusTest {

    private GJobStatus newStatus() {
        GJobDescription jd = new GJobDescription(GJobDescription.DELETE_JOB_TYPE, null, "test job");
        return new GJobStatus(jd);
    }

    @Test
    void countersTotalExactlyAcrossThreads() throws Exception {
        GJobStatus status = newStatus();
        final int increments = 50_000;

        Thread worker = new Thread(() -> {
            for (int i = 0; i < increments; i++) {
                status.incrementProcessedFilesBy(1);
                status.incrementProcessedFoldersBy(1);
                status.incrementTotalFilesBy(1);
                status.incrementTotalFoldersBy(1);
                status.incrementTotalSizeBy(2);
            }
        });

        // reader polls concurrently - totals must never exceed the final value
        Thread reader = new Thread(() -> {
            while (status.getProcessedFilesCount() < increments) {
                assertTrue(status.getTotalProcessedCount() <= increments * 2L);
                Thread.onSpinWait();
            }
        });

        worker.start();
        reader.start();
        worker.join(10_000);
        reader.join(10_000);

        assertEquals(increments, status.getProcessedFilesCount());
        assertEquals(increments, status.getProcessedFoldersCount());
        assertEquals(increments * 2L, status.getTotalProcessedCount());
        assertEquals(increments * 2L, status.getTotalCount());
        assertEquals(increments * 2L, status.getTotalSize());
    }

    @Test
    void stateWrittenOnWorkerIsVisibleToReader() throws Exception {
        GJobStatus status = newStatus();
        CountDownLatch stateSet = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            status.setBytesTransferred(12345L);
            status.setState(GJobStatus.STATE_COMPLETE);
            stateSet.countDown();
        });
        worker.start();

        assertTrue(stateSet.await(5, TimeUnit.SECONDS));
        // bounded poll: visibility should be effectively immediate with volatile
        long deadline = System.currentTimeMillis() + 5_000;
        while (status.getState() != GJobStatus.STATE_COMPLETE
                && System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(GJobStatus.STATE_COMPLETE, status.getState());
        assertEquals(12345L, status.getBytesTransferred());
        worker.join(5_000);
    }
}
