package com.gideonsoftworks.gideon.mirroring;

import com.gideonsoftworks.gcore.GBookmark;
import com.gideonsoftworks.gcore.GConnection;
import com.gideonsoftworks.gcore.GConnectionException;
import com.gideonsoftworks.gcore.GFileItem;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The COPY phase runs bounded-parallel over a pool of cloned SFTP sessions (plan §3.4): never more
 * than {@code maxInFlight} sessions, every mkdir before the first copy and every copy before the
 * first delete, the replica equal to the master afterwards, and a cancel that leaves no worker or
 * clone behind. {@code maxInFlight = 1} is exactly the serial engine.
 */
@Tag("integration")
class GMirrorEngineParallelTest
{
    private static final String DELAY_PROP = "relaysftp.mirror.copyDelayMs";
    private static SshServer sshd;
    private static int port;
    @TempDir static Path serverKeyDir;
    private String previousDelay;

    @BeforeAll
    static void startServer() throws Exception
    {
        sshd = SshServer.setUpDefaultServer();
        sshd.setPort( 0 );
        sshd.setKeyPairProvider( new SimpleGeneratorHostKeyProvider( serverKeyDir.resolve( "hostkey.ser" ) ) );
        sshd.setPasswordAuthenticator( ( u, p, s ) -> "testuser".equals( u ) && "testpass".equals( p ) );
        sshd.setSubsystemFactories( Collections.singletonList( new SftpSubsystemFactory() ) );
        sshd.start();
        port = sshd.getPort();
    }

    @AfterAll
    static void stopServer() throws Exception { if( sshd != null ) sshd.stop( true ); }

    @AfterEach
    void restoreDelay()
    {
        if( previousDelay == null ) System.clearProperty( DELAY_PROP ); else System.setProperty( DELAY_PROP, previousDelay );
    }

    /** The throttle is read once per engine, so set it before constructing one. */
    private GMirrorEngine throttledEngine( long ms )
    {
        previousDelay = System.getProperty( DELAY_PROP );
        System.setProperty( DELAY_PROP, Long.toString( ms ) );
        return new GMirrorEngine();
    }

    /** Samples the run state and the server every few ms: peak sessions and any phase-order violation. */
    private static final class Sampler extends Thread
    {
        final GMirrorRunState state;
        final AtomicInteger peakSessions = new AtomicInteger();
        final AtomicInteger peakRunningCopies = new AtomicInteger();
        final List<String> violations = Collections.synchronizedList( new ArrayList<>() );
        volatile boolean stop;

        Sampler( GMirrorRunState state ) { super( "sampler" ); this.state = state; setDaemon( true ); }

        @Override public void run()
        {
            while( !stop )
            {
                peakSessions.accumulateAndGet( sshd.getActiveSessions().size(), Math::max );
                int running = 0;
                boolean copyRunning = false, deleteRunning = false;
                for( int i = 0; i < state.size(); i++ )
                {
                    GMirrorAction.Type t = state.action( i ).type;
                    if( state.status( i ) == GMirrorRunState.Status.RUNNING )
                    {
                        if( t == GMirrorAction.Type.COPY ) { running++; copyRunning = true; }
                        if( t == GMirrorAction.Type.DELETE || t == GMirrorAction.Type.RMDIR ) deleteRunning = true;
                    }
                }
                peakRunningCopies.accumulateAndGet( running, Math::max );
                for( int i = 0; i < state.size(); i++ )
                {
                    GMirrorAction.Type t = state.action( i ).type;
                    GMirrorRunState.Status s = state.status( i );
                    if( copyRunning && t == GMirrorAction.Type.MKDIR && s != GMirrorRunState.Status.DONE )
                        violations.add( "copy running while mkdir " + state.action( i ).relPath + " is " + s );
                    if( deleteRunning && t == GMirrorAction.Type.COPY && s != GMirrorRunState.Status.DONE && s != GMirrorRunState.Status.SKIPPED )
                        violations.add( "delete running while copy " + state.action( i ).relPath + " is " + s );
                }
                try { Thread.sleep( 2 ); } catch( InterruptedException e ) { return; }
            }
        }
    }

    @Test
    void threeInFlight_respectsBoundAndPhaseOrder_andConverges( @TempDir Path tmp ) throws Exception
    {
        File master  = Files.createDirectories( tmp.resolve( "master" ) ).toRealPath().toFile();
        File replica = Files.createDirectories( tmp.resolve( "replica" ) ).toRealPath().toFile();
        for( int i = 0; i < 20; i++ ) seed( master, ( i % 2 == 0 ? "" : "sub/" ) + "f" + i + ".txt", "content-" + i );
        seed( replica, "extra.txt", "gone soon" );
        GMirrorEngine engine = throttledEngine( 60 );
        GConnection mc = localConn( master ), rc = sftpConn( replica );

        GMirrorPlan plan = engine.scan( uploadSpec( master, mc, replica, rc, true ) );
        assertEquals( 21, plan.count( GMirrorAction.Reason.NEW ), "20 files + the sub dir" );
        assertEquals( 1, plan.deleteCount() );
        GMirrorRunState state = new GMirrorRunState( plan, 3 );
        Sampler sampler = new Sampler( state );
        sampler.start();
        GMirrorSpec run = uploadSpec( master, mc, replica, rc, true );
        run.confirmedLargeDelete = true;   // the one extra is 100% of the replica; the UI would have asked
        GMirrorResult r = engine.execute( plan, run, null, state );
        sampler.stop = true;
        sampler.join();

        assertEquals( 20, r.copies );
        assertEquals( 1, r.deletes );
        assertEquals( 0, r.skipped );
        assertTrue( sampler.peakRunningCopies.get() <= 3, "bound: " + sampler.peakRunningCopies );
        assertTrue( sampler.peakRunningCopies.get() >= 2, "parallelism observed: " + sampler.peakRunningCopies );
        assertTrue( sampler.peakSessions.get() <= 3, "sessions: " + sampler.peakSessions );
        assertTrue( sampler.peakSessions.get() >= 2, "clones were used: " + sampler.peakSessions );
        assertTrue( sampler.violations.isEmpty(), "phase order: " + sampler.violations );
        assertEquals( 1, activeSessionsSettled( 1 ), "clones logged out, primary still open" );
        assertNoCopyWorkers();

        for( int i = 0; i < 20; i++ )
            assertEquals( "content-" + i, Files.readString( replica.toPath().resolve( ( i % 2 == 0 ? "" : "sub/" ) + "f" + i + ".txt" ) ) );
        assertFalse( Files.exists( replica.toPath().resolve( "extra.txt" ) ) );
        assertTrue( engine.scan( uploadSpec( master, mc, replica, rc, true ) ).isEmpty(), "second scan is empty" );
        rc.logout();
    }

    @Test
    void oneInFlight_behavesAsTheSerialEngine( @TempDir Path tmp ) throws Exception
    {
        File master  = Files.createDirectories( tmp.resolve( "master" ) ).toRealPath().toFile();
        File replica = Files.createDirectories( tmp.resolve( "replica" ) ).toRealPath().toFile();
        for( int i = 0; i < 8; i++ ) seed( master, "f" + i + ".txt", "c" + i );
        GMirrorEngine engine = throttledEngine( 20 );
        GConnection mc = localConn( master ), rc = sftpConn( replica );

        GMirrorPlan plan = engine.scan( uploadSpec( master, mc, replica, rc, false ) );
        GMirrorRunState state = new GMirrorRunState( plan, 1 );
        Sampler sampler = new Sampler( state );
        sampler.start();
        GMirrorResult r = engine.execute( plan, uploadSpec( master, mc, replica, rc, false ), null, state );
        sampler.stop = true;
        sampler.join();

        assertEquals( 8, r.copies );
        assertEquals( 1, sampler.peakRunningCopies.get(), "strictly one at a time" );
        assertEquals( 1, sampler.peakSessions.get(), "no clone is ever created" );
        for( int i = 0; i < state.size(); i++ ) assertEquals( GMirrorRunState.Status.DONE, state.status( i ) );
        assertTrue( engine.scan( uploadSpec( master, mc, replica, rc, false ) ).isEmpty() );
        rc.logout();
    }

    @Test
    void cancelMidRun_stopsWorkers_closesClones_marksRestCancelled( @TempDir Path tmp ) throws Exception
    {
        File master  = Files.createDirectories( tmp.resolve( "master" ) ).toRealPath().toFile();
        File replica = Files.createDirectories( tmp.resolve( "replica" ) ).toRealPath().toFile();
        for( int i = 0; i < 30; i++ ) seed( master, "f" + i + ".txt", "c" + i );
        GMirrorEngine engine = throttledEngine( 150 );
        GConnection mc = localConn( master ), rc = sftpConn( replica );

        GMirrorPlan plan = engine.scan( uploadSpec( master, mc, replica, rc, false ) );
        GMirrorRunState state = new GMirrorRunState( plan, 3 );
        AtomicReference<Throwable> outcome = new AtomicReference<>();
        Thread runner = new Thread( () -> {
            try { engine.execute( plan, uploadSpec( master, mc, replica, rc, false ), null, state ); }
            catch( Throwable t ) { outcome.set( t ); }
        }, "job-mirror" );
        runner.start();

        long deadline = System.currentTimeMillis() + 10_000;
        while( state.doneCount() < 3 && System.currentTimeMillis() < deadline ) Thread.sleep( 10 );
        assertTrue( state.doneCount() >= 3, "some copies finished before the cancel" );
        runner.interrupt();                         // what GJobExecuter.stop() does
        runner.join( 10_000 );
        assertFalse( runner.isAlive(), "the run must return after a cancel" );

        assertInstanceOf( GConnectionException.class, outcome.get(), String.valueOf( outcome.get() ) );
        assertEquals( "Mirror cancelled", outcome.get().getMessage() );
        int done = 0, cancelled = 0;
        for( int i = 0; i < state.size(); i++ )
        {
            GMirrorRunState.Status s = state.status( i );
            assertNotEquals( GMirrorRunState.Status.RUNNING, s, "row " + i + " still running" );
            assertNotEquals( GMirrorRunState.Status.QUEUED, s, "row " + i + " still queued" );
            if( s == GMirrorRunState.Status.DONE ) done++;
            if( s == GMirrorRunState.Status.CANCELLED ) cancelled++;
        }
        assertTrue( done >= 3 && cancelled >= 1, "done=" + done + " cancelled=" + cancelled );
        assertNoCopyWorkers();
        assertEquals( 1, activeSessionsSettled( 1 ), "clones logged out after the cancel; open: " + GConnection.openConnections() );
        rc.logout();
    }

    // ---- helpers ----

    private static void assertNoCopyWorkers() throws Exception
    {
        Thread.sleep( 100 );
        for( Thread t : Thread.getAllStackTraces().keySet() )
            assertFalse( t.isAlive() && t.getName().startsWith( "mirror-copy-" ), "worker still alive: " + t.getName() );
    }

    /** Session close is asynchronous on the server; wait up to 5 s for the count to settle at {@code expected}. */
    private static int activeSessionsSettled( int expected ) throws Exception
    {
        long deadline = System.currentTimeMillis() + 5_000;
        while( sshd.getActiveSessions().size() != expected && System.currentTimeMillis() < deadline ) Thread.sleep( 50 );
        return sshd.getActiveSessions().size();
    }

    private static void seed( File dir, String rel, String content ) throws Exception
    {
        Path f = dir.toPath().resolve( rel );
        Files.createDirectories( f.getParent() );
        Files.writeString( f, content );
    }

    private static GConnection localConn( File dir ) throws Exception
    {
        GConnection c = new GConnection( new GBookmark( "local", dir.getAbsolutePath(), "u", "p", 0, GBookmark.G_FILE_PROTOCOL ) );
        c.login();
        return c;
    }

    private static GConnection sftpConn( File dir ) throws Exception
    {
        GConnection c = new GConnection( new GBookmark( "sftp", "localhost", dir.getAbsolutePath(),
            "testuser", "testpass", port, GBookmark.G_SFTP_PROTOCOL ) );
        c.login();
        return c;
    }

    private static GFileItem root( File dir, GConnection c )
    {
        GFileItem r = new GFileItem( dir );
        r.setConnection( c );
        return r;
    }

    private static GMirrorSpec uploadSpec( File masterDir, GConnection masterConn, File replicaDir, GConnection replicaConn, boolean deleteExtras )
    {
        GMirrorSpec s = new GMirrorSpec();
        s.masterConn   = masterConn;
        s.masterRoot   = root( masterDir, masterConn );
        s.replicaConn  = replicaConn;
        s.replicaRoot  = root( replicaDir, replicaConn );
        s.direction    = GMirrorSpec.Direction.UPLOAD;
        s.deleteExtras = deleteExtras;
        return s;
    }
}
