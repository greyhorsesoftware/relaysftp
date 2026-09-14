package com.gideonsoftworks.gcore;

import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GFileItem children safety (roadmap 3.3): getChildrenSnapshot() must be an
 * independent copy, and snapshot/iteration must survive concurrent add/remove
 * from a worker thread (the app renders snapshots on the EDT while job workers
 * mutate the canonical list).
 */
class GFileItemChildrenTest {

    private GFileItem newItem(String name) {
        return new GFileItem(null, name, 0, new Date());
    }

    private GFileItem newFolder(String name) {
        GFileItem f = newItem(name);
        f.setFileType(GFileItem.G_FOLDER);
        return f;
    }

    @Test
    void snapshotIsIndependentCopy() {
        GFileItem parent = newFolder("parent");
        GFileItem a = newItem("a.txt");
        GFileItem b = newItem("b.txt");
        parent.addChild(a);
        parent.addChild(b);

        ArrayList snapshot = parent.getChildrenSnapshot();
        assertEquals(2, snapshot.size());

        // mutating the parent afterwards must not change the snapshot
        parent.removeChild(a);
        parent.addChild(newItem("c.txt"));
        assertEquals(2, snapshot.size());
        assertSame(a, snapshot.get(0));
        assertSame(b, snapshot.get(1));

        // and mutating the snapshot must not change the parent
        snapshot.clear();
        assertEquals(2, parent.getChildCount());
    }

    @Test
    void concurrentMutationVsSnapshotDoesNotThrow() throws Exception {
        GFileItem parent = newFolder("parent");
        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicBoolean failed = new AtomicBoolean(false);

        Thread mutator = new Thread(() -> {
            try {
                for (int i = 0; i < 20_000 && !stop.get(); i++) {
                    GFileItem c = newItem("f" + i);
                    parent.addChild(c);
                    if (i % 3 == 0) parent.removeChild(c);
                    if (i % 500 == 0) parent.removeAllChildren();
                }
            } catch (Throwable t) {
                failed.set(true);
            } finally {
                stop.set(true);
            }
        });

        Thread reader = new Thread(() -> {
            try {
                while (!stop.get()) {
                    ArrayList snap = parent.getChildrenSnapshot();
                    // iterate the whole snapshot - must never CME or go out of bounds
                    for (Object o : snap) {
                        assertNotNull(o);
                    }
                    parent.getChildCount();
                    parent.getChild(0);
                    parent.getChildNamed("f1");
                }
            } catch (Throwable t) {
                failed.set(true);
                stop.set(true);
            }
        });

        mutator.start();
        reader.start();
        mutator.join(30_000);
        reader.join(30_000);
        stop.set(true);

        assertFalse(failed.get(), "concurrent snapshot/mutation raised an exception");
    }
}
