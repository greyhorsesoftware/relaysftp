package com.gideonsoftworks.gideon.mirroring;

import com.gideonsoftworks.gcore.GBookmark;
import com.gideonsoftworks.gcore.GConnection;
import com.gideonsoftworks.gcore.GConnectionException;
import com.gideonsoftworks.gcore.GFileItem;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

/** Per-action run state written by {@link GMirrorEngine#execute} (plan §3.1–3.3), over two local FILE connections. */
class GMirrorRunStateTest
{
    private final GMirrorEngine engine = new GMirrorEngine();

    private static GConnection localConn( File dir ) throws Exception
    {
        GConnection c = new GConnection( new GBookmark( "local", dir.getAbsolutePath(), "u", "p", 0, GBookmark.G_FILE_PROTOCOL ) );
        c.login();
        return c;
    }
    private static GFileItem root( File dir, GConnection c ) { GFileItem r = new GFileItem( dir ); r.setConnection( c ); return r; }
    private static void write( Path f, String content ) throws Exception { Files.createDirectories( f.getParent() ); Files.writeString( f, content ); }

    private GMirrorSpec upload( File master, GConnection mc, File replica, GConnection rc )
    {
        GMirrorSpec s = new GMirrorSpec();
        s.masterConn = mc; s.masterRoot = root( master, mc );
        s.replicaConn = rc; s.replicaRoot = root( replica, rc );
        s.direction = GMirrorSpec.Direction.UPLOAD;
        return s;
    }

    @Test
    void everyRowEndsDone_withItsOwnByteCount( @TempDir Path tmp ) throws Exception
    {
        File master = Files.createDirectory( tmp.resolve( "m" ) ).toFile(), replica = Files.createDirectory( tmp.resolve( "r" ) ).toFile();
        write( master.toPath().resolve( "a.txt" ), "hello" );
        write( master.toPath().resolve( "sub/b.txt" ), "world!!" );
        GConnection mc = localConn( master ), rc = localConn( replica );

        GMirrorPlan plan = engine.scan( upload( master, mc, replica, rc ) );
        GMirrorRunState state = new GMirrorRunState( plan, 1 );
        assertEquals( 3, state.size(), "mkdir sub + 2 copies; no SKIP rows" );
        assertEquals( 12, state.totalBytes() );
        for( int i = 0; i < state.size(); i++ ) assertEquals( GMirrorRunState.Status.QUEUED, state.status( i ) );

        GMirrorResult r = engine.execute( plan, upload( master, mc, replica, rc ), null, state );

        assertEquals( 3, state.doneCount() );
        assertEquals( 0, state.skippedCount() );
        for( int i = 0; i < state.size(); i++ )
        {
            assertEquals( GMirrorRunState.Status.DONE, state.status( i ), state.action( i ).relPath );
            if( state.action( i ).type == GMirrorAction.Type.COPY )
                assertEquals( state.action( i ).bytes, state.bytesDone( i ), "bytes for " + state.action( i ).relPath );
        }
        assertEquals( 12, state.bytesSoFar() );
        assertEquals( 2, r.copies );
        assertTrue( r.skips.isEmpty() );
        assertEquals( -1, state.firstRunning() );
    }

    @Test
    void unwritableDestination_isSkippedWithReason_andTheRunContinues( @TempDir Path tmp ) throws Exception
    {
        Assumptions.assumeTrue( FileSystems.getDefault().supportedFileAttributeViews().contains( "posix" ) );
        Assumptions.assumeFalse( "root".equals( System.getProperty( "user.name" ) ), "root ignores mode bits" );

        File master = Files.createDirectory( tmp.resolve( "m" ) ).toFile(), replica = Files.createDirectory( tmp.resolve( "r" ) ).toFile();
        write( master.toPath().resolve( "locked/x.txt" ), "x" );
        write( master.toPath().resolve( "ok.txt" ), "ok" );
        Path locked = Files.createDirectory( replica.toPath().resolve( "locked" ) );
        Files.setPosixFilePermissions( locked, EnumSet.of( PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE ) );
        try
        {
            GConnection mc = localConn( master ), rc = localConn( replica );
            GMirrorPlan plan = engine.scan( upload( master, mc, replica, rc ) );
            GMirrorRunState state = new GMirrorRunState( plan, 1 );
            GMirrorResult r = engine.execute( plan, upload( master, mc, replica, rc ), null, state );

            int lockedRow = -1, okRow = -1;
            for( int i = 0; i < state.size(); i++ )
            {
                if( state.action( i ).relPath.equals( "locked/x.txt" ) ) lockedRow = i;
                if( state.action( i ).relPath.equals( "ok.txt" ) ) okRow = i;
            }
            assertTrue( lockedRow >= 0 && okRow >= 0 );
            assertEquals( GMirrorRunState.Status.SKIPPED, state.status( lockedRow ) );
            assertEquals( "permission denied", state.reason( lockedRow ) );
            assertEquals( GMirrorRunState.Status.DONE, state.status( okRow ), "the run continues past a skip" );
            assertEquals( 1, r.skipped );
            assertEquals( 1, r.skips.size() );
            assertEquals( "locked/x.txt", r.skips.get( 0 ).relPath() );
            assertEquals( "permission denied", r.skips.get( 0 ).reason() );
            assertEquals( "ok", Files.readString( replica.toPath().resolve( "ok.txt" ) ) );
        }
        finally
        {
            Files.setPosixFilePermissions( locked, EnumSet.of( PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE ) );
        }
    }

    @Test
    void maxInFlight_isThree_cappedByTheBookmarkMaximum()
    {
        GBookmark bm = new GBookmark( "b", "h", "/", "u", "p", 22, GBookmark.G_SFTP_PROTOCOL );
        bm.setMaxConcurrentTransfers( 1 );
        assertEquals( 1, GMirrorPanel.maxInFlight( bm ) );
        bm.setMaxConcurrentTransfers( 2 );
        assertEquals( 2, GMirrorPanel.maxInFlight( bm ) );
        bm.setMaxConcurrentTransfers( 3 );
        assertEquals( 3, GMirrorPanel.maxInFlight( bm ) );
        bm.setMaxConcurrentTransfers( 6 );
        assertEquals( 3, GMirrorPanel.maxInFlight( bm ), "never more than three" );
        assertEquals( 3, GMirrorPanel.maxInFlight( null ) );
    }

    @Test
    void shortReason_mapsCommonMessages()
    {
        assertEquals( "permission denied", GMirrorEngine.shortReason( new GConnectionException( "Local copy failed: /a -> /b : /b/x: Permission denied", 0 ) ) );
        assertEquals( "not found", GMirrorEngine.shortReason( new GConnectionException( "No such file: /gone", 0 ) ) );
        assertEquals( "cancelled", GMirrorEngine.shortReason( new GConnectionException( "Upload cancelled: k", 0 ) ) );
        assertEquals( "failed", GMirrorEngine.shortReason( new GConnectionException( "   ", 0 ) ) );
        assertEquals( "permission denied", GMirrorEngine.shortReason( new GConnectionException( "Local copy failed: /a -> /b : AccessDeniedException: /b", 0 ) ) );
        assertEquals( "not found", GMirrorEngine.shortReason( new GConnectionException( "Local copy failed: /a -> /b : NoSuchFileException: /a", 0 ) ) );
        String longMsg = "x".repeat( 100 );
        assertEquals( 60, GMirrorEngine.shortReason( new GConnectionException( longMsg, 0 ) ).length() );
    }
}
