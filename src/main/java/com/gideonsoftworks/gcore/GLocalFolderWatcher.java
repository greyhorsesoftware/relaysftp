package com.gideonsoftworks.gcore;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Watches a single local folder (non-recursive) for filesystem changes
 * using Java's WatchService. Only detects changes to direct children.
 */
public class GLocalFolderWatcher {

    public interface Listener {
        /**
         * Called on the EDT when changes are detected.
         * @param changedNames names of direct children that changed
         */
        void folderContentsChanged(List<String> changedNames);
    }

    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean running = false;
    private Listener listener;

    public GLocalFolderWatcher(Listener listener) {
        this.listener = listener;
    }

    public void watch(GFileItem folderItem) {
        stop();

        if (folderItem == null || folderItem.getCompletePath() == null) return;

        Path dir = Path.of(folderItem.getCompletePath());
        if (!Files.isDirectory(dir)) return;

        try {
            watchService = FileSystems.getDefault().newWatchService();
            dir.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);

            running = true;
            watchThread = Thread.ofVirtual().name("folder-watch").start(() -> {
                while (running) {
                    try {
                        WatchKey key = watchService.take();
                        List<WatchEvent<?>> events = key.pollEvents();

                        // Collect unique changed names
                        ArrayList<String> names = new ArrayList<>();
                        for (WatchEvent<?> event : events) {
                            Path context = (Path) event.context();
                            if (context != null) {
                                String name = context.toString();
                                if (!names.contains(name)) names.add(name);
                            }
                        }

                        key.reset();

                        // Debounce: wait briefly for more events to accumulate
                        Thread.sleep(500);

                        // Drain any events that arrived during the wait
                        WatchKey extraKey;
                        while ((extraKey = watchService.poll()) != null) {
                            for (WatchEvent<?> event : extraKey.pollEvents()) {
                                Path context = (Path) event.context();
                                if (context != null) {
                                    String name = context.toString();
                                    if (!names.contains(name)) names.add(name);
                                }
                            }
                            extraKey.reset();
                        }

                        if (!names.isEmpty()) {
                            SwingUtilities.invokeLater(() -> {
                                if (listener != null) listener.folderContentsChanged(names);
                            });
                        }
                    } catch (ClosedWatchServiceException e) {
                        break;
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
        } catch (IOException e) {
            System.err.println("GLocalFolderWatcher: failed to watch " + dir + " — " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                // ignore
            }
            watchService = null;
        }
        if (watchThread != null) {
            watchThread.interrupt();
            watchThread = null;
        }
    }
}
