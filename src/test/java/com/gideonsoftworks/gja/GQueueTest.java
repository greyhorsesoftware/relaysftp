package com.gideonsoftworks.gja;

import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Listener dispatch in GQueue must survive listeners that mutate the listener
 * list mid-dispatch (a one-shot waiter removing itself, or a listener adding
 * another) - no ConcurrentModificationException, no skipped listener.
 */
class GQueueTest {

    private GJobDescription newJob(String name) {
        return new GJobDescription(GJobDescription.DELETE_JOB_TYPE, null, name);
    }

    @Test
    void selfRemovingListenerDoesNotSkipOthers() {
        GQueue queue = new GQueue("test");
        List<String> fired = new ArrayList<>();

        GQueueEventListenerInterface[] selfRemover = new GQueueEventListenerInterface[1];
        selfRemover[0] = e -> {
            fired.add("remover");
            queue.removeStatusListener(selfRemover[0]);
        };
        GQueueEventListenerInterface second = e -> fired.add("second");

        queue.addStatusListener(selfRemover[0]);
        queue.addStatusListener(second);

        queue.addJobToQueue(newJob("job1"));

        assertEquals(List.of("remover", "second"), fired,
            "listener after a self-removing one must still be notified");

        // remover is gone - a second dispatch reaches only the survivor
        fired.clear();
        queue.addJobToQueue(newJob("job2"));
        assertEquals(List.of("second"), fired);
    }

    @Test
    void listenerAddingListenerDoesNotThrow() {
        GQueue queue = new GQueue("test");
        List<String> fired = new ArrayList<>();

        GQueueEventListenerInterface late = e -> fired.add("late");
        GQueueEventListenerInterface adder = e -> {
            fired.add("adder");
            queue.addStatusListener(late);
        };
        queue.addStatusListener(adder);

        assertDoesNotThrow(() -> queue.addJobToQueue(newJob("job1")));
        assertEquals(List.of("adder"), fired, "listener added mid-dispatch fires next time");

        fired.clear();
        queue.addJobToQueue(newJob("job2"));
        assertEquals(List.of("adder", "late"), fired);
    }
}
