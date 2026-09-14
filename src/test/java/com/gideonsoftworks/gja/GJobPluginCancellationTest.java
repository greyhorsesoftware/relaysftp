package com.gideonsoftworks.gja;

import com.gideonsoftworks.gcore.GBookmark;
import com.gideonsoftworks.gcore.GConnection;
import com.gideonsoftworks.gcore.GConnectionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Leak-audit #13: a cancellation that surfaces from the plugin as a plain {@link RuntimeException}
 * (rather than a {@link GConnectionException}) must end the job as cancelled, not as an error.
 * Drives {@link GJobPlugin#run()} on a real executer/manager with a local FILE connection, so the
 * whole catch chain in run() is exercised without any server.
 */
class GJobPluginCancellationTest
{
    /** A plugin whose processing step does whatever the test wants. */
    private static final class StubPlugin extends GJobPlugin
    {
        interface Body { void run() throws Exception; }
        private final Body body;
        StubPlugin( GJobStatus js, Body body ) { super( js ); this.body = body; }

        @Override
        public void processJobDescription( GJobDescription d, GConnection c ) throws GConnectionException
        {
            try { body.run(); }
            catch( GConnectionException | RuntimeException e ) { throw e; }
            catch( Exception e ) { throw new RuntimeException( e ); }
        }
    }

    private static GConnection localConn( Path dir ) throws Exception
    {
        GBookmark bm = new GBookmark( "local", dir.toAbsolutePath().toString(), "u", "p", 0, GBookmark.G_FILE_PROTOCOL );
        GConnection c = new GConnection( bm );
        c.login();
        return c;
    }

    /** Run the stub to completion on a real executer and return the job's final state. */
    private static int runToEnd( Path dir, StubPlugin.Body body ) throws Exception
    {
        GConnection conn = localConn( dir );
        GJobManager jm = new GJobManager( "cancellation-test" );
        GJobDescription jd = new GJobDescription( GJobDescription.PING_JOB_TYPE, conn, "stub" );
        GJobStatus js = new GJobStatus( jd );
        GJobExecuter ex = new GJobExecuter( js, jm );
        js.setJobExecuter( ex );
        StubPlugin plugin = new StubPlugin( js, body );
        ex.setJobPlugin( plugin );

        ex.start();

        long deadline = System.currentTimeMillis() + 10_000;
        while( System.currentTimeMillis() < deadline )
        {
            int s = js.getState();
            if( s == GJobStatus.STATE_COMPLETE || s == GJobStatus.STATE_ERROR
                || s == GJobStatus.STATE_CANCELLING || s == GJobStatus.STATE_STOPPED )
            {
                Thread.sleep( 100 );   // let the EDT hand-off (executer.run) finish
                return js.getState();
            }
            Thread.sleep( 20 );
        }
        fail( "job did not reach a terminal state; last state " + js.getState() );
        return -1;
    }

    private static boolean isCancelled( int state )
    {
        return state == GJobStatus.STATE_CANCELLING || state == GJobStatus.STATE_STOPPED;
    }

    @Test
    void runtimeExceptionWhileInterrupted_endsAsCancelled( @TempDir Path tmp ) throws Exception
    {
        int state = runToEnd( tmp, () -> {
            Thread.currentThread().interrupt();          // as GJobExecuter.stop() does
            throw new IllegalStateException( "boom while interrupted" );
        } );
        assertTrue( isCancelled( state ), "expected cancelled, got state " + state );
    }

    @Test
    void cancellationExceptionWithoutInterrupt_endsAsCancelled( @TempDir Path tmp ) throws Exception
    {
        // S3: gpp_cancelTransfer() cancels the transfer future; join() throws CancellationException,
        // a RuntimeException, before anyone checked the interrupt flag.
        int state = runToEnd( tmp, () -> { throw new CancellationException( "transfer future cancelled" ); } );
        assertTrue( isCancelled( state ), "expected cancelled, got state " + state );
    }

    @Test
    void plainRuntimeException_stillEndsAsError( @TempDir Path tmp ) throws Exception
    {
        // Control: the guard must not turn genuine failures into cancellations.
        int state = runToEnd( tmp, () -> { throw new IllegalStateException( "genuine failure" ); } );
        assertEquals( GJobStatus.STATE_ERROR, state );
    }

    @Test
    void normalCompletion_unaffected( @TempDir Path tmp ) throws Exception
    {
        int state = runToEnd( tmp, () -> {} );
        assertEquals( GJobStatus.STATE_COMPLETE, state );
    }
}
