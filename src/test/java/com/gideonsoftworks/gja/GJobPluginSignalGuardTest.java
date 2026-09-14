package com.gideonsoftworks.gja;

import com.gideonsoftworks.gcore.GAppShutdown;
import com.gideonsoftworks.gcore.GFileItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Leak audit #19: the worker-to-EDT hand-off behind signalCollision must not block forever on an
 * EDT that is busy quitting, and a Stop while the collision dialog is up must come back as a
 * cancellation with the interrupt flag intact.
 */
class GJobPluginSignalGuardTest
{
    @AfterEach void reset() { GAppShutdown.reset(); }

    private static GJobPlugin plugin()
    {
        GJobDescription jd = new GJobDescription( GJobDescription.DOWNLOAD_JOB_TYPE, null, "guard" );
        GJobStatus js = new GJobStatus( jd );
        GJobManager jm = new GJobManager( "guard-test" );
        GJobExecuter ex = new GJobExecuter( js, jm );
        js.setJobExecuter( ex );
        GJobPlugin p = new GJobPlugin( js );
        ex.setJobPlugin( p );
        return p;
    }

    private static GFileItem item( String name ) { GFileItem f = new GFileItem( null, name, 1, new Date() ); f.setFileType( GFileItem.G_FILE ); return f; }

    @Test
    void whileShuttingDown_collisionReturnsCancellationWithoutTouchingTheEdt() throws Exception
    {
        GAppShutdown.begin();
        GJobPlugin p = plugin();

        // Block the EDT for the whole test: if the plugin tried invokeAndWait it would hang here.
        CountDownLatch release = new CountDownLatch( 1 );
        SwingUtilities.invokeLater( () -> { try { release.await(); } catch( InterruptedException ignore ) {} } );
        try
        {
            AtomicReference<GJobUserMessage> msg = new AtomicReference<>();
            AtomicReference<Boolean> interrupted = new AtomicReference<>();
            Thread worker = new Thread( () -> {
                msg.set( p.signalCollision( item( "src" ), item( "dst" ) ) );
                interrupted.set( Thread.currentThread().isInterrupted() );
            }, "job-guard-worker" );
            worker.start();
            worker.join( 3_000 );

            assertFalse( worker.isAlive(), "signalCollision must return without waiting on the busy EDT" );
            assertNotNull( msg.get() );
            assertTrue( msg.get().isCancellation() );
            assertEquals( GJobStatus.STATE_CANCELLING, p.getJobStatus().getState() );
            assertEquals( Boolean.TRUE, interrupted.get(), "interrupt flag left set for run()'s next check" );
        }
        finally { release.countDown(); }
    }

    @Test
    void stopWhileWaitingOnTheDialog_returnsCancellationAndKeepsTheInterrupt() throws Exception
    {
        GJobPlugin p = plugin();

        // Park the EDT so the worker really waits inside invokeAndWait (the dialog would be up).
        CountDownLatch edtParked = new CountDownLatch( 1 );
        CountDownLatch release = new CountDownLatch( 1 );
        SwingUtilities.invokeLater( () -> { edtParked.countDown(); try { release.await(); } catch( InterruptedException ignore ) {} } );
        assertTrue( edtParked.await( 2, TimeUnit.SECONDS ) );
        try
        {
            AtomicReference<GJobUserMessage> msg = new AtomicReference<>();
            AtomicReference<Boolean> interrupted = new AtomicReference<>();
            Thread worker = new Thread( () -> {
                msg.set( p.signalCollision( item( "src" ), item( "dst" ) ) );
                interrupted.set( Thread.currentThread().isInterrupted() );
            }, "job-guard-worker" );
            worker.start();
            Thread.sleep( 200 );                       // let it reach invokeAndWait
            assertEquals( GJobStatus.STATE_WAITING_FOR_USER, p.getJobStatus().getState() );

            worker.interrupt();                        // what GJobExecuter.stop() does
            worker.join( 3_000 );

            assertFalse( worker.isAlive(), "must not stay parked on the EDT after Stop" );
            assertTrue( msg.get().isCancellation() );
            assertEquals( GJobStatus.STATE_CANCELLING, p.getJobStatus().getState() );
            assertEquals( Boolean.TRUE, interrupted.get(), "the interrupt must survive the hand-off" );
        }
        finally { release.countDown(); }
    }
}
