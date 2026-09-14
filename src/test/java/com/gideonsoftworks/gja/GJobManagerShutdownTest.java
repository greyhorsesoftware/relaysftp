package com.gideonsoftworks.gja;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

/** The bounded join behind {@link GJobManager#shutdown(long)}. */
class GJobManagerShutdownTest
{
    /** A worker that blocks until interrupted (cooperative, like a transfer loop between chunks). */
    private static Thread cooperative( CountDownLatch started )
    {
        Thread t = Thread.ofVirtual().name( "job-cooperative" ).unstarted( () -> {
            started.countDown();
            try { new CountDownLatch( 1 ).await(); } catch( InterruptedException e ) { /* done */ }
        } );
        t.start();
        return t;
    }

    /** A worker that ignores interrupts for a while (like a socket read that does not wake). */
    private static Thread stubborn( CountDownLatch started, long ms )
    {
        Thread t = Thread.ofVirtual().name( "job-stubborn" ).unstarted( () -> {
            started.countDown();
            long end = System.currentTimeMillis() + ms;
            while( System.currentTimeMillis() < end ) { try { Thread.sleep( 20 ); } catch( InterruptedException ignore ) {} }
        } );
        t.start();
        return t;
    }

    @Test
    void interruptedWorkersAreJoinedQuickly() throws Exception
    {
        CountDownLatch started = new CountDownLatch( 3 );
        List<Thread> workers = List.of( cooperative( started ), cooperative( started ), cooperative( started ) );
        started.await();
        workers.forEach( Thread::interrupt );

        long t0 = System.currentTimeMillis();
        GJobManager.stopAndJoinAll( workers, 2_000 );
        long took = System.currentTimeMillis() - t0;

        for( Thread t : workers ) assertFalse( t.isAlive(), t.getName() + " still alive" );
        assertTrue( took < 500, "join took " + took + " ms" );
    }

    @Test
    void aWorkerThatIgnoresInterruptsOnlyCostsTheBudget() throws Exception
    {
        CountDownLatch started = new CountDownLatch( 2 );
        Thread good = cooperative( started );
        Thread bad  = stubborn( started, 5_000 );
        started.await();
        good.interrupt(); bad.interrupt();

        long t0 = System.currentTimeMillis();
        GJobManager.stopAndJoinAll( List.of( good, bad ), 300 );
        long took = System.currentTimeMillis() - t0;

        assertFalse( good.isAlive() );
        assertTrue( bad.isAlive(), "the stubborn worker is still running - that is the point" );
        assertTrue( took >= 250 && took < 1_500, "bounded by the budget, took " + took + " ms" );
    }

    @Test
    void zeroBudgetDoesNotWait() throws Exception
    {
        CountDownLatch started = new CountDownLatch( 1 );
        Thread bad = stubborn( started, 3_000 );
        started.await();
        long t0 = System.currentTimeMillis();
        GJobManager.stopAndJoinAll( List.of( bad ), 0 );
        assertTrue( System.currentTimeMillis() - t0 < 200 );
    }
}
