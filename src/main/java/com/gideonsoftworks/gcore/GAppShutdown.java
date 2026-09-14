package com.gideonsoftworks.gcore;

import com.gideonsoftworks.gja.GJobManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * App-wide shutdown state and the bounded steps of an orderly quit
 * (docs/1.8.0/GRACEFUL_SHUTDOWN_PLAN.md). The flag is application-wide (each browser window has
 * its own {@link GJobManager} with its own per-manager flag, which is not enough for a worker to
 * know the EDT is busy quitting). Every step here is bounded; the watchdog is the last line.
 */
public final class GAppShutdown
{
    /** Wait for interrupted job threads to finish. */
    public static final long JOIN_BUDGET_MS = 4_000;
    /** Wait for every open connection to log out. */
    public static final long DISCONNECT_BUDGET_MS = 4_000;
    /** Hard deadline for the whole quit sequence; after it the process halts regardless. */
    public static final long WATCHDOG_MS = 10_000;

    private static volatile boolean shuttingDown = false;

    private GAppShutdown() {}

    public static void begin() { shuttingDown = true; }

    public static boolean isShuttingDown() { return shuttingDown; }

    /** Tests only: the flag is process-wide and a test that set it must clear it. */
    public static void reset() { shuttingDown = false; }

    /**
     * Start a daemon platform thread that runs {@code onDeadline} after {@code deadlineMs} unless
     * the process has exited first. Platform, not virtual: it must not depend on the scheduler the
     * hung workers may be starving.
     */
    public static Thread startWatchdog( long deadlineMs, Runnable onDeadline )
    {
        Thread t = new Thread( () -> {
            try { Thread.sleep( deadlineMs ); } catch( InterruptedException e ) { return; }
            onDeadline.run();
        }, "shutdown-watchdog" );
        t.setDaemon( true );
        t.start();
        return t;
    }

    /**
     * Shut down every manager, sharing one join budget across all of them: each manager stops
     * all of its active jobs, then waits for their threads up to the remaining budget.
     */
    public static void stopManagers( Collection<GJobManager> managers, long joinBudgetMs )
    {
        long deadline = System.currentTimeMillis() + joinBudgetMs;
        for( GJobManager jm : managers )
        {
            if( jm == null ) continue;
            long remaining = Math.max( 0, deadline - System.currentTimeMillis() );
            try { jm.shutdown( remaining ); }
            catch( RuntimeException e ) { GAuditLog.error( "", "shutdown: job manager " + jm, null, e ); }
        }
    }

    /**
     * Log every open connection out, each on its own virtual thread so one hung server cannot
     * serialise the rest, and wait up to {@code budgetMs} for them all. Closing the socket is also
     * what unblocks a worker stuck in a read that ignored its interrupt.
     * @return the number of connections that had not finished logging out when the budget ran out
     */
    public static int disconnectAll( long budgetMs )
    {
        List<GConnection> open = GConnection.openConnections();
        if( open.isEmpty() ) return 0;
        CountDownLatch done = new CountDownLatch( open.size() );
        for( GConnection c : open )
        {
            Thread.ofVirtual().name( "shutdown-logout" ).start( () -> {
                try { c.logout(); }
                catch( Exception e ) { GAuditLog.error( "", "shutdown: logout", null, e ); }
                finally { done.countDown(); }
            } );
        }
        try
        {
            if( !done.await( budgetMs, TimeUnit.MILLISECONDS ) ) return (int) done.getCount();
        }
        catch( InterruptedException e )
        {
            Thread.currentThread().interrupt();
            return (int) done.getCount();
        }
        return 0;
    }

    /** Copy helper for callers that hold managers in several places. */
    public static List<GJobManager> managers( GJobManager... ms )
    {
        List<GJobManager> out = new ArrayList<>();
        for( GJobManager m : ms ) if( m != null ) out.add( m );
        return out;
    }
}
