package com.gideonsoftworks.gideon.mirroring;

import com.gideonsoftworks.gcore.GConnection;
import com.gideonsoftworks.gcore.GFileItem;
import com.gideonsoftworks.gcore.GBookmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end engine test over real {@link GConnection}s, using the local-filesystem protocol on
 * both sides (now that FILE upload/download are implemented). Seeds a master tree, mirrors it,
 * and asserts the replica matches — exercising scan + execute through the same interfaces the
 * remote protocols use, with no server needed.
 */
class GMirrorEngineLocalTest
{
    private final GMirrorEngine engine = new GMirrorEngine();

    // ---- helpers ----

    private static GConnection localConn( File dir ) throws Exception
    {
        GBookmark bm = new GBookmark( "local", dir.getAbsolutePath(), "u", "p", 0, GBookmark.G_FILE_PROTOCOL );
        GConnection c = new GConnection( bm );
        c.login();
        return c;
    }

    /** Fresh root GFileItem so each scan re-lists from disk (listed-state is per item). */
    private static GFileItem root( File dir, GConnection c )
    {
        GFileItem r = new GFileItem( dir );
        r.setConnection( c );
        return r;
    }

    private static void write( Path file, String content ) throws Exception
    {
        Files.createDirectories( file.getParent() );
        Files.writeString( file, content );
    }

    private GMirrorSpec uploadSpec( File masterDir, GConnection masterConn,
                                    File replicaDir, GConnection replicaConn )
    {
        GMirrorSpec s = new GMirrorSpec();
        s.masterConn  = masterConn;
        s.masterRoot  = root( masterDir, masterConn );
        s.replicaConn = replicaConn;
        s.replicaRoot = root( replicaDir, replicaConn );
        s.direction   = GMirrorSpec.Direction.UPLOAD;
        return s;
    }

    // ---- the test ----

    @Test
    void mirrorsTreeThenIsIdempotentAndAdditive( @TempDir Path tmp ) throws Exception
    {
        File master  = tmp.resolve( "master" ).toFile();
        File replica = tmp.resolve( "replica" ).toFile();
        assertTrue( master.mkdirs() );
        assertTrue( replica.mkdirs() );

        // Seed the master tree.
        write( master.toPath().resolve( "a.txt" ), "hello" );
        write( master.toPath().resolve( "sub/b.txt" ), "world" );
        write( master.toPath().resolve( "sub/deep/c.txt" ), "deep file" );

        // A replica-only file that an additive mirror must NOT delete.
        write( replica.toPath().resolve( "keepme.txt" ), "preexisting" );

        GConnection masterConn  = localConn( master );
        GConnection replicaConn = localConn( replica );

        // 1. Scan → plan: 3 new files + 2 new dirs, nothing else.
        GMirrorPlan plan = engine.scan( uploadSpec( master, masterConn, replica, replicaConn ) );
        assertEquals( 5, plan.count( GMirrorAction.Reason.NEW ), "3 files + 2 dirs are all new" );
        assertEquals( 3, plan.count( GMirrorAction.Type.COPY ) );
        assertEquals( 2, plan.count( GMirrorAction.Type.MKDIR ) );
        assertEquals( 0, plan.count( GMirrorAction.Reason.EXTRA ), "additive scan must not plan deletes" );

        // 2. Execute → replica now mirrors master.
        engine.execute( plan, uploadSpec( master, masterConn, replica, replicaConn ), null );

        assertEquals( "hello",     Files.readString( replica.toPath().resolve( "a.txt" ), StandardCharsets.UTF_8 ) );
        assertEquals( "world",     Files.readString( replica.toPath().resolve( "sub/b.txt" ), StandardCharsets.UTF_8 ) );
        assertEquals( "deep file", Files.readString( replica.toPath().resolve( "sub/deep/c.txt" ), StandardCharsets.UTF_8 ) );

        // Additive: the pre-existing replica-only file survived.
        assertTrue( Files.exists( replica.toPath().resolve( "keepme.txt" ) ), "additive mirror must not delete extras" );

        // 3. Idempotent: a second scan sees everything as unchanged (mtime was preserved).
        GMirrorPlan again = engine.scan( uploadSpec( master, masterConn, replica, replicaConn ) );
        assertTrue( again.isEmpty(), "re-mirror of an unchanged tree must be a no-op, was: " + again );

        // 4. Change detection: edit a file (different size) → exactly one CHANGED copy.
        write( master.toPath().resolve( "a.txt" ), "hello again, now longer" );
        GMirrorPlan changed = engine.scan( uploadSpec( master, masterConn, replica, replicaConn ) );
        assertEquals( 1, changed.count( GMirrorAction.Reason.CHANGED ) );
        assertEquals( 0, changed.count( GMirrorAction.Reason.NEW ) );

        engine.execute( changed, uploadSpec( master, masterConn, replica, replicaConn ), null );
        assertEquals( "hello again, now longer",
            Files.readString( replica.toPath().resolve( "a.txt" ), StandardCharsets.UTF_8 ) );
    }
}
