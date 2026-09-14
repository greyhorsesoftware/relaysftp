package com.gideonsoftworks.gja;

import com.gideonsoftworks.gcore.GBookmark;
import com.gideonsoftworks.gcore.GConnection;
import com.gideonsoftworks.gcore.GFileItem;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** {@link GMtimeJobPlugin} on a real job manager against an embedded SFTP server. */
@Tag("integration")
class GMtimeJobPluginTest
{
    private static SshServer sshd;
    private static int port;
    @TempDir static Path tempDir;
    private static Path remoteDir;

    @BeforeAll
    static void startServer() throws Exception
    {
        remoteDir = Files.createDirectory( tempDir.resolve( "remote" ) ).toRealPath();
        Files.writeString( remoteDir.resolve( "a.txt" ), "a" );
        sshd = SshServer.setUpDefaultServer();
        sshd.setPort( 0 );
        sshd.setKeyPairProvider( new SimpleGeneratorHostKeyProvider( tempDir.resolve( "hostkey.ser" ) ) );
        sshd.setPasswordAuthenticator( ( u, p, s ) -> "test".equals( u ) && "test".equals( p ) );
        sshd.setSubsystemFactories( Collections.singletonList( new SftpSubsystemFactory() ) );
        sshd.start();
        port = sshd.getPort();
    }

    @AfterAll
    static void stopServer() throws Exception { if( sshd != null ) sshd.stop( true ); }

    private static GConnection connect() throws Exception
    {
        GBookmark bm = new GBookmark( "test", "localhost", remoteDir.toAbsolutePath().toString(), "test", "test", port, GBookmark.G_SFTP_PROTOCOL );
        GConnection c = new GConnection( bm );
        c.login();
        return c;
    }

    /** Queue one MTIME job for {@code target} and wait for it; returns [completed?, failed?, result]. */
    private static Object[] run( GConnection conn, GFileItem target ) throws Exception
    {
        GJobManager jm = new GJobManager( "mtime-test" );
        GJobDescription jd = new GJobDescription( GJobDescription.MTIME_JOB_TYPE, conn, "mtime" );
        if( target != null ) jd.setTargetFile( target );   // setTargetFile rejects null; an MTIME job without a target reads -1
        jd.setOriginator( "test" );
        CountDownLatch done = new CountDownLatch( 1 );
        AtomicBoolean failed = new AtomicBoolean();
        final Object[] result = { null };
        jm.addStatusListener( new GAbstractJobManagerListener() {
            // read the result inside the completion callback, exactly as the browser panel does —
            // the manager tidies the description once its listeners have been notified
            @Override public void jm_jobCompleted( GJobManagerEvent e )
            {
                if( e.getJobDescription() != jd ) return;
                result[0] = jd.getProperty( GJobDescription.MTIME_RESULT_JOB_PROP );
                done.countDown();
            }
            @Override public void jm_jobFailed( GJobManagerEvent e )    { if( e.getJobDescription() == jd ) { failed.set( true ); done.countDown(); } }
        } );
        jm.getTaskQueue().addJobToQueue( jd );
        jm.startNextJobForQueue( jm.getTaskQueue() );
        boolean completed = done.await( 15, TimeUnit.SECONDS );
        return new Object[]{ completed, failed.get(), result[0] };
    }

    @Test
    void storesTheFoldersMtimeOnTheJob() throws Exception
    {
        GConnection conn = connect();
        try
        {
            // the folder item the browser pane would hold as its virtual cwd
            GFileItem cwd = new GFileItem( remoteDir.getFileName().toString(), remoteDir.toAbsolutePath().toString(), conn );
            cwd.setFileType( GFileItem.G_FOLDER );
            Object[] r = run( conn, cwd );
            assertTrue( (Boolean) r[0], "job should complete" );
            assertFalse( (Boolean) r[1], "job should not fail" );
            assertNotNull( r[2] );
            long mtime = (Long) r[2];
            long expected = Files.getLastModifiedTime( remoteDir ).toMillis() / 1000;
            assertTrue( Math.abs( mtime - expected ) <= 1, "mtime " + mtime + " vs folder " + expected );
        }
        finally { conn.logout(); }
    }

    @Test
    void nullTargetYieldsMinusOneAndStillCompletes() throws Exception
    {
        GConnection conn = connect();
        try
        {
            Object[] r = run( conn, null );
            assertTrue( (Boolean) r[0] );
            assertFalse( (Boolean) r[1] );
            assertEquals( -1L, r[2] );
        }
        finally { conn.logout(); }
    }
}
