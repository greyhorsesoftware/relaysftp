package com.gideonsoftworks.gja;

import com.gideonsoftworks.gcore.GAppShutdown;
import com.gideonsoftworks.gcore.GBookmark;
import com.gideonsoftworks.gcore.GConnection;
import com.gideonsoftworks.gcore.GFileItem;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The quit sequence's two heavy steps against real jobs: stop-and-join every active transfer on a
 * manager, then log out every open connection. Three parallel downloads are mid-transfer when the
 * sequence runs; afterwards no worker thread is alive and no connection is open, well inside the
 * budgets.
 */
@Tag("integration")
class GShutdownSequenceIntegrationTest
{
    private static SshServer sshd;
    private static int port;
    @TempDir static Path tempDir;
    private static Path remoteDir, localDir;

    @BeforeAll
    static void startServer() throws Exception
    {
        remoteDir = Files.createDirectory( tempDir.resolve( "remote" ) ).toRealPath();
        localDir  = Files.createDirectory( tempDir.resolve( "local" ) ).toRealPath();
        for( int i = 0; i < 3; i++ )
            try( RandomAccessFile raf = new RandomAccessFile( remoteDir.resolve( "big_" + i + ".bin" ).toFile(), "rw" ) ) { raf.setLength( 96L * 1024 * 1024 ); }
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

    @AfterEach void reset() { GAppShutdown.reset(); }

    @Test
    void stopManagersThenDisconnectAll_endsEveryWorkerAndConnectionWithinBudget() throws Exception
    {
        GJobManager jm = new GJobManager( "shutdown-test" );
        GQueue transfers = jm.getTransferQueue();
        transfers.setMaxActiveJobs( 3 );
        CountDownLatch started = new CountDownLatch( 3 );
        jm.addStatusListener( new GAbstractJobManagerListener() {
            @Override public void jm_jobStarted( GJobManagerEvent e ) { started.countDown(); }
        } );

        GBookmark bm = new GBookmark( "test", "localhost", remoteDir.toAbsolutePath().toString(), "test", "test", port, GBookmark.G_SFTP_PROTOCOL );
        GConnection browserConn = new GConnection( bm );
        browserConn.login();                                   // the "pane" connection
        for( int i = 0; i < 3; i++ )
        {
            GJobDescription jd = new GJobDescription( GJobDescription.DOWNLOAD_JOB_TYPE, browserConn, "download_" + i );
            GFileItem target = new GFileItem( remoteDir.resolve( "big_" + i + ".bin" ).toAbsolutePath().toString(), "big_" + i + ".bin", 96L * 1024 * 1024, null );
            jd.setTargetFile( target );
            jd.setSourceFile( new GFileItem( localDir.resolve( "big_" + i + ".bin" ).toFile() ) );
            transfers.addJobToQueue( jd );
        }
        jm.startNextJobForQueue( transfers );
        assertTrue( started.await( 15, TimeUnit.SECONDS ), "three downloads should start" );
        Thread.sleep( 300 );                                   // let them log their clones in and start moving bytes
        assertTrue( GConnection.openConnections().size() >= 1, "transfer clones and/or the pane connection are open" );

        long t0 = System.currentTimeMillis();
        GAppShutdown.begin();
        GAppShutdown.stopManagers( List.of( jm ), GAppShutdown.JOIN_BUDGET_MS );
        int left = GAppShutdown.disconnectAll( GAppShutdown.DISCONNECT_BUDGET_MS );
        long took = System.currentTimeMillis() - t0;

        assertEquals( 0, left, "every connection logged out within the budget" );
        assertTrue( took < GAppShutdown.JOIN_BUDGET_MS + GAppShutdown.DISCONNECT_BUDGET_MS, "took " + took + " ms" );
        assertTrue( GConnection.openConnections().isEmpty(), "no open connection remains: " + GConnection.openConnections() );

        // no transfer worker survives (they are named job-<job name>)
        Thread.sleep( 200 );
        for( Thread t : Thread.getAllStackTraces().keySet() )
            assertFalse( t.isAlive() && t.getName().startsWith( "job-download_" ), "worker still alive: " + t.getName() );
    }
}
