package com.gideonsoftworks.gideon.mirroring;

import com.gideonsoftworks.gcore.GBookmark;
import com.gideonsoftworks.gcore.GConnection;
import com.gideonsoftworks.gcore.GFileItem;

import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Catches the SFTP mirror "churn" bug: a size+mtime upload mirror must be idempotent — a second run with no
 * source changes copies nothing. The only end-to-end mirror test was local↔local, where the FILE plugin
 * preserves mtime via {@code Files.copy(COPY_ATTRIBUTES)} — so it could never expose an SFTP upload that
 * forgot to stamp the mtime. This runs the real SFTP upload path (MINA sshd) with <b>aged, spread-out</b>
 * source mtimes so the auto clock-offset can't mask a uniform skew; before the fix, unchanged files
 * re-appeared on the second scan.
 */
@Tag("integration")
class GMirrorSftpIdempotenceTest
{
    private static SshServer sshd;
    private static int port;
    @TempDir static Path serverKeyDir;

    private final GMirrorEngine engine = new GMirrorEngine();

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

    @Test
    void sftpUploadMirror_isIdempotent_whenSourceMtimesAreOldAndSpread( @TempDir Path tmp ) throws Exception
    {
        // toRealPath: macOS /var -> /private/var, so the bookmark path matches the server's resolution.
        File master  = Files.createDirectories( tmp.resolve( "master" ) ).toRealPath().toFile();
        File replica = Files.createDirectories( tmp.resolve( "replica" ) ).toRealPath().toFile();

        long day = 86_400_000L;
        long now = System.currentTimeMillis();
        seed( master, "a.txt", "alpha",   now - 40 * day );   // spread well beyond the 2s tolerance,
        seed( master, "b.txt", "bravo",   now - 20 * day );   // and not all the same age — so a single
        seed( master, "c.txt", "charlie", now -  5 * day );   // clock-offset can't explain them away.

        GConnection masterConn  = localConn( master );
        GConnection replicaConn = sftpConn( replica );

        // 1. Upload-mirror everything.
        GMirrorPlan plan = engine.scan( uploadSpec( master, masterConn, replica, replicaConn ) );
        assertEquals( 3, plan.count( GMirrorAction.Reason.NEW ), "all three files are new on first run" );
        engine.execute( plan, uploadSpec( master, masterConn, replica, replicaConn ), null );

        // 2. The bug: a second scan of an unchanged tree must be a no-op (mtime preserved on the replica).
        GMirrorPlan again = engine.scan( uploadSpec( master, masterConn, replica, replicaConn ) );
        assertTrue( again.isEmpty(),
            "re-mirror of an unchanged tree must copy nothing — churn means mtime wasn't preserved: " + again );

        // 3. And a real edit is still detected.
        Files.writeString( master.toPath().resolve( "a.txt" ), "alpha edited longer" );
        GMirrorPlan changed = engine.scan( uploadSpec( master, masterConn, replica, replicaConn ) );
        assertEquals( 1, changed.count( GMirrorAction.Reason.CHANGED ) );
        assertEquals( 0, changed.count( GMirrorAction.Reason.NEW ) );
    }

    // ---- helpers ----

    private static void seed( File dir, String name, String content, long mtime ) throws Exception
    {
        File f = new File( dir, name );
        Files.writeString( f.toPath(), content );
        assertTrue( f.setLastModified( mtime ), "set source mtime" );
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

    private static GMirrorSpec uploadSpec( File masterDir, GConnection masterConn, File replicaDir, GConnection replicaConn )
    {
        GMirrorSpec s = new GMirrorSpec();
        s.masterConn  = masterConn;
        s.masterRoot  = root( masterDir, masterConn );
        s.replicaConn = replicaConn;
        s.replicaRoot = root( replicaDir, replicaConn );
        s.direction   = GMirrorSpec.Direction.UPLOAD;
        return s;
    }
}
