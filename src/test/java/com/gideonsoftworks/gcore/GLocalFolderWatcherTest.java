package com.gideonsoftworks.gcore;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests local folder watching: file create, delete, rename detection,
 * and subfolder cache invalidation.
 */
class GLocalFolderWatcherTest {

    @TempDir
    Path tempDir;

    private GConnection conn;

    private Path testDir;

    @BeforeEach
    void setUp() throws Exception {
        // Create an isolated test directory inside tempDir
        testDir = Files.createDirectory(tempDir.resolve("watchtest")).toRealPath();

        GBookmark bm = new GBookmark("local", "/", null, null, 0, GBookmark.G_FILE_PROTOCOL);
        conn = new GConnection(bm);
        conn.login();
    }

    @AfterEach
    void tearDown() {
        try {
            conn.stopWatching();
            conn.logout();
        } catch (Exception ignored) {}
    }

    // --- Helper: create a GFileItem for a directory and list it ---

    private GFileItem listFolder(Path dir) throws GConnectionException {
        GFileItem item = new GFileItem(dir.toFile());
        item.setConnection(conn);
        conn.list(item);
        return item;
    }

    private boolean waitForEvent(CountDownLatch latch) throws InterruptedException {
        // WatchService on macOS polls every ~2 seconds; give it 5 seconds
        return latch.await(5, TimeUnit.SECONDS);
    }

    // --- Test: Detect file creation ---

    @Test
    void detectsFileCreation() throws Exception {
        // Create initial structure
        Files.createFile(testDir.resolve("existing.txt"));
        GFileItem folder = listFolder(testDir);
        assertEquals(1, folder.getChildCount());

        // Set up watcher
        CountDownLatch latch = new CountDownLatch(1);
        conn.setFolderChangeListener(event -> latch.countDown());
        conn.watchFolder(folder);

        // Create a new file
        Files.createFile(testDir.resolve("newfile.txt"));

        assertTrue(waitForEvent(latch), "Should detect file creation");
    }

    // --- Test: Detect file deletion ---

    @Test
    void detectsFileDeletion() throws Exception {
        Path fileToDelete = Files.createFile(testDir.resolve("deleteme.txt"));
        GFileItem folder = listFolder(testDir);
        assertEquals(1, folder.getChildCount());

        CountDownLatch latch = new CountDownLatch(1);
        conn.setFolderChangeListener(event -> latch.countDown());
        conn.watchFolder(folder);

        Files.delete(fileToDelete);

        assertTrue(waitForEvent(latch), "Should detect file deletion");
    }

    // --- Test: Detect file rename ---

    @Test
    void detectsFileRename() throws Exception {
        Path original = Files.createFile(testDir.resolve("original.txt"));
        GFileItem folder = listFolder(testDir);

        CountDownLatch latch = new CountDownLatch(1);
        conn.setFolderChangeListener(event -> latch.countDown());
        conn.watchFolder(folder);

        Files.move(original, testDir.resolve("renamed.txt"));

        assertTrue(waitForEvent(latch), "Should detect file rename");
    }

    // --- Test: Detect file modification ---

    @Test
    void detectsFileModification() throws Exception {
        Path file = Files.createFile(testDir.resolve("modify.txt"));
        GFileItem folder = listFolder(testDir);

        CountDownLatch latch = new CountDownLatch(1);
        conn.setFolderChangeListener(event -> latch.countDown());
        conn.watchFolder(folder);

        Files.writeString(file, "new content");

        assertTrue(waitForEvent(latch), "Should detect file modification");
    }

    // --- Test: Detect directory creation ---

    @Test
    void detectsDirectoryCreation() throws Exception {
        GFileItem folder = listFolder(testDir);
        assertEquals(0, folder.getChildCount());

        CountDownLatch latch = new CountDownLatch(1);
        conn.setFolderChangeListener(event -> latch.countDown());
        conn.watchFolder(folder);

        Files.createDirectory(testDir.resolve("newdir"));

        assertTrue(waitForEvent(latch), "Should detect directory creation");
    }

    // --- Test: Event includes correct folder reference ---

    @Test
    void eventContainsCorrectFolder() throws Exception {
        Files.createFile(testDir.resolve("test.txt"));
        GFileItem folder = listFolder(testDir);

        CountDownLatch latch = new CountDownLatch(1);
        final GLocalFileEvent[] captured = { null };
        conn.setFolderChangeListener(event -> {
            captured[0] = event;
            latch.countDown();
        });
        conn.watchFolder(folder);

        Files.createFile(testDir.resolve("another.txt"));

        assertTrue(waitForEvent(latch), "Should detect change");
        assertNotNull(captured[0]);
        assertEquals(GLocalFileEvent.LOCAL_FOLDER_CHANGED, captured[0].getEventType());
        assertEquals(testDir.toString(), captured[0].getChangedPath());
    }

    // --- Test: Re-list after event shows updated contents ---

    @Test
    void relistAfterCreationShowsNewFile() throws Exception {
        Files.createFile(testDir.resolve("first.txt"));
        GFileItem folder = listFolder(testDir);
        assertEquals(1, folder.getChildCount());

        CountDownLatch latch = new CountDownLatch(1);
        conn.setFolderChangeListener(event -> latch.countDown());
        conn.watchFolder(folder);

        Files.createFile(testDir.resolve("second.txt"));
        assertTrue(waitForEvent(latch));

        // Re-list and verify
        folder.setFileState(GFileItem.STATE_LISTED);
        conn.list(folder);
        assertEquals(2, folder.getChildCount());
        assertNotNull(folder.getChildNamed("second.txt"));
    }

    @Test
    void relistAfterDeletionShowsFileRemoved() throws Exception {
        Files.createFile(testDir.resolve("keep.txt"));
        Files.createFile(testDir.resolve("remove.txt"));
        GFileItem folder = listFolder(testDir);
        assertEquals(2, folder.getChildCount());

        CountDownLatch latch = new CountDownLatch(1);
        conn.setFolderChangeListener(event -> latch.countDown());
        conn.watchFolder(folder);

        Files.delete(testDir.resolve("remove.txt"));
        assertTrue(waitForEvent(latch));

        folder.setFileState(GFileItem.STATE_LISTED);
        conn.list(folder);
        assertEquals(1, folder.getChildCount());
        assertNotNull(folder.getChildNamed("keep.txt"));
        assertNull(folder.getChildNamed("remove.txt"));
    }

    // --- Test: Subfolder cache invalidation ---

    @Test
    void subfolderMarkedDirtyAfterContentChange() throws Exception {
        // Create a subfolder with a file
        Path subdir = Files.createDirectory(testDir.resolve("subdir"));
        Files.createFile(subdir.resolve("child.txt"));

        // List the root and the subfolder (both become STATE_LISTED)
        GFileItem root = listFolder(testDir);
        GFileItem subfolderItem = root.getChildNamed("subdir");
        assertNotNull(subfolderItem);
        conn.list(subfolderItem);
        assertEquals(GFileItem.STATE_LISTED, subfolderItem.getFileState());
        assertEquals(1, subfolderItem.getChildCount());

        // Now watch the SUBFOLDER
        CountDownLatch latch = new CountDownLatch(1);
        conn.setFolderChangeListener(event -> latch.countDown());
        conn.watchFolder(subfolderItem);

        // Add a file inside the subfolder
        Files.createFile(subdir.resolve("newchild.txt"));
        assertTrue(waitForEvent(latch), "Should detect change in watched subfolder");

        // Re-list and verify the new file appears
        subfolderItem.setFileState(GFileItem.STATE_LISTED);
        conn.list(subfolderItem);
        assertEquals(2, subfolderItem.getChildCount());
        assertNotNull(subfolderItem.getChildNamed("newchild.txt"));
    }

    // --- Test: Switching watch folder stops old watcher ---

    @Test
    void switchingFolderDetectsNewFolder() throws Exception {
        Path dir1 = Files.createDirectory(testDir.resolve("dir1"));
        Path dir2 = Files.createDirectory(testDir.resolve("dir2"));

        GFileItem folder1 = listFolder(dir1);
        GFileItem folder2 = listFolder(dir2);

        AtomicInteger eventCount = new AtomicInteger(0);
        conn.setFolderChangeListener(event -> eventCount.incrementAndGet());

        // Watch dir1
        conn.watchFolder(folder1);
        Files.createFile(dir1.resolve("indir1.txt"));
        Thread.sleep(3000); // wait for macOS poll

        int countAfterDir1 = eventCount.get();
        assertTrue(countAfterDir1 > 0, "Should have detected change in dir1");

        // Switch to dir2
        eventCount.set(0);
        conn.watchFolder(folder2);
        Files.createFile(dir2.resolve("indir2.txt"));
        Thread.sleep(3000);

        assertTrue(eventCount.get() > 0, "Should detect change in dir2 after switch");
    }

    // --- Test: Stop watching produces no more events ---

    @Test
    void stopWatchingProducesNoEvents() throws Exception {
        Files.createFile(testDir.resolve("existing.txt"));
        GFileItem folder = listFolder(testDir);

        AtomicInteger eventCount = new AtomicInteger(0);
        conn.setFolderChangeListener(event -> eventCount.incrementAndGet());
        conn.watchFolder(folder);

        // Verify watching works
        Files.createFile(testDir.resolve("trigger.txt"));
        Thread.sleep(3000);
        assertTrue(eventCount.get() > 0, "Should have detected initial change");

        // Stop and reset
        conn.stopWatching();
        eventCount.set(0);

        Files.createFile(testDir.resolve("afterstop.txt"));
        Thread.sleep(3000);

        assertEquals(0, eventCount.get(), "Should not detect changes after stopping");
    }
}
