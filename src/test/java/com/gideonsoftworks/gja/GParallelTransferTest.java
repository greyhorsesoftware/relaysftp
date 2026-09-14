package com.gideonsoftworks.gja;

import com.gideonsoftworks.gcore.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.shell.ProcessShellCommandFactory;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests parallel transfer support via GQueue and GJobManager.
 */
@Tag("integration")
class GParallelTransferTest {

    private static SshServer sshd;
    private static int port;

    @TempDir
    static Path tempDir;
    private static Path remoteDir;
    private static Path localDir;

    @BeforeAll
    static void startServer() throws Exception {
        remoteDir = Files.createDirectory(tempDir.resolve("remote")).toRealPath();
        localDir = Files.createDirectory(tempDir.resolve("local")).toRealPath();

        // Create test files
        for (int i = 0; i < 10; i++) {
            Files.writeString(remoteDir.resolve("file_" + i + ".txt"), "content " + i);
        }

        sshd = SshServer.setUpDefaultServer();
        sshd.setPort(0);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(tempDir.resolve("hostkey.ser")));
        sshd.setPasswordAuthenticator((u, p, s) -> "test".equals(u) && "test".equals(p));
        sshd.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
        sshd.setCommandFactory(ProcessShellCommandFactory.INSTANCE);
        sshd.start();
        port = sshd.getPort();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (sshd != null) sshd.stop(true);
    }

    // --- Test: Queue allows multiple active jobs ---

    @Test
    void queueSupportsMultipleActiveJobs() {
        GQueue queue = new GQueue("test-transfer");
        queue.setMaxActiveJobs(3);

        assertEquals(3, queue.getMaxActiveJobs());
        assertTrue(queue.canStartMore());
        assertEquals(0, queue.getActiveJobCount());

        // Simulate 3 active jobs
        GBookmark bm = new GBookmark("test", "host", null, null, null, 0, GBookmark.G_SFTP_PROTOCOL);
        for (int i = 0; i < 3; i++) {
            GJobDescription jd = new GJobDescription(GJobDescription.DOWNLOAD_JOB_TYPE, null, "job" + i);
            queue.addActiveJob(jd);
        }

        assertEquals(3, queue.getActiveJobCount());
        assertFalse(queue.canStartMore());
    }

    @Test
    void queueCanStartMoreAfterRemoval() {
        GQueue queue = new GQueue("test-transfer");
        queue.setMaxActiveJobs(2);

        GJobDescription jd1 = new GJobDescription(GJobDescription.DOWNLOAD_JOB_TYPE, null, "job1");
        GJobDescription jd2 = new GJobDescription(GJobDescription.DOWNLOAD_JOB_TYPE, null, "job2");

        queue.addActiveJob(jd1);
        queue.addActiveJob(jd2);
        assertFalse(queue.canStartMore());

        queue.removeActiveJob(jd1);
        assertTrue(queue.canStartMore());
        assertEquals(1, queue.getActiveJobCount());
    }

    @Test
    void flushQueueClearsActiveJobs() {
        GQueue queue = new GQueue("test-transfer");
        queue.setMaxActiveJobs(3);

        for (int i = 0; i < 3; i++) {
            GJobDescription jd = new GJobDescription(GJobDescription.DOWNLOAD_JOB_TYPE, null, "job" + i);
            queue.addActiveJob(jd);
        }

        queue.flushQueue();
        assertEquals(0, queue.getActiveJobCount());
        assertTrue(queue.canStartMore());
    }

    // --- Test: Bookmark max concurrent transfers ---

    @Test
    void bookmarkDefaultsToThreeConcurrent() {
        GBookmark bm = new GBookmark("test", "host", null, null, null, 0, GBookmark.G_SFTP_PROTOCOL);
        assertEquals(3, bm.getMaxConcurrentTransfers());
    }

    @Test
    void bookmarkClampsToRange() {
        GBookmark bm = new GBookmark("test", "host", null, null, null, 0, GBookmark.G_SFTP_PROTOCOL);

        bm.setMaxConcurrentTransfers(0);
        assertEquals(1, bm.getMaxConcurrentTransfers());

        bm.setMaxConcurrentTransfers(15);
        assertEquals(10, bm.getMaxConcurrentTransfers());

        bm.setMaxConcurrentTransfers(5);
        assertEquals(5, bm.getMaxConcurrentTransfers());
    }

    // --- Test: GJobManager starts multiple transfers ---

    @Test
    void jobManagerStartsMultipleTransfers() throws Exception {
        GJobManager jm = new GJobManager("test-parallel");
        GQueue transferQueue = jm.getTransferQueue();
        transferQueue.setMaxActiveJobs(3);

        AtomicInteger startedCount = new AtomicInteger(0);
        CountDownLatch allStarted = new CountDownLatch(3);

        jm.addStatusListener(new GAbstractJobManagerListener() {
            @Override
            public void jm_jobStarted(GJobManagerEvent e) {
                startedCount.incrementAndGet();
                allStarted.countDown();
            }
        });

        // Create bookmark and connection for job descriptions
        GBookmark bm = new GBookmark("test", "localhost",
            remoteDir.toAbsolutePath().toString(),
            "test", "test", port, GBookmark.G_SFTP_PROTOCOL);

        GConnection conn = new GConnection(bm);

        // Queue 5 download jobs
        for (int i = 0; i < 5; i++) {
            GJobDescription jd = new GJobDescription(
                GJobDescription.DOWNLOAD_JOB_TYPE, conn, "download_" + i);
            GFileItem target = new GFileItem(
                remoteDir.resolve("file_" + i + ".txt").toAbsolutePath().toString(),
                "file_" + i + ".txt", 10, null);
            jd.setTargetFile(target);
            GFileItem source = new GFileItem(
                localDir.resolve("file_" + i + ".txt").toFile());
            jd.setSourceFile(source);
            transferQueue.addJobToQueue(jd);
        }

        // Start processing — should start 3 simultaneously
        jm.startNextJobForQueue(transferQueue);

        // Wait for at least 3 to start
        boolean started = allStarted.await(10, TimeUnit.SECONDS);
        assertTrue(started, "Should have started 3 jobs");
        assertEquals(3, transferQueue.getActiveJobCount(), "Should have 3 active jobs");
        assertTrue(startedCount.get() >= 3, "At least 3 jobs should have started");
    }

    // --- Test: Sequential queue still works (max=1) ---

    @Test
    void sequentialQueueOnlyStartsOne() {
        GJobManager jm = new GJobManager("test-sequential");
        GQueue taskQueue = jm.getTaskQueue();
        // Task queue defaults to max 1

        AtomicInteger startedCount = new AtomicInteger(0);
        jm.addStatusListener(new GAbstractJobManagerListener() {
            @Override
            public void jm_jobStarted(GJobManagerEvent e) {
                startedCount.incrementAndGet();
            }
        });

        GBookmark bm = new GBookmark("test", "localhost",
            "/tmp", "test", "test", port, GBookmark.G_SFTP_PROTOCOL);
        GConnection conn;
        try {
            conn = new GConnection(bm);
        } catch (Exception e) {
            return; // skip if connection can't be created
        }

        for (int i = 0; i < 3; i++) {
            GJobDescription jd = new GJobDescription(
                GJobDescription.DELETE_JOB_TYPE, conn, "delete_" + i);
            taskQueue.addJobToQueue(jd);
        }

        jm.startNextJobForQueue(taskQueue);

        // Only 1 should be active
        assertEquals(1, taskQueue.getActiveJobCount());
    }
}
