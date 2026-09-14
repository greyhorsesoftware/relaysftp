package com.gideonsoftworks.gideon.mirroring;

import com.gideonsoftworks.gcore.GBookmark;
import com.gideonsoftworks.gcore.GConnection;
import com.gideonsoftworks.gcore.GFileItem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deletes + the §5 deletion-safety guards, exercised locally (FILE↔FILE): extras are removed
 * only with {@code deleteExtras}, and the empty-master / blast-radius / containment guards refuse
 * dangerous runs.
 */
class GMirrorEngineDeleteTest
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

    private GMirrorSpec spec( File masterDir, GConnection masterConn, File replicaDir, GConnection replicaConn,
                             boolean deleteExtras )
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

    // ---- tests ----

    @Test
    void deleteExtras_removesReplicaOnlyItems( @TempDir Path tmp ) throws Exception
    {
        File master = mkdir( tmp, "master" ), replica = mkdir( tmp, "replica" );
        for( String n : List.of( "a.txt", "b.txt", "c.txt", "d.txt" ) )
            write( master.toPath().resolve( n ), n + " content" );

        GConnection mc = localConn( master ), rc = localConn( replica );

        // Mirror additively first, then introduce an extra on the replica.
        engine.execute( engine.scan( spec( master, mc, replica, rc, false ) ),
                        spec( master, mc, replica, rc, false ), null );
        write( replica.toPath().resolve( "extra.txt" ), "delete me" );

        GMirrorPlan plan = engine.scan( spec( master, mc, replica, rc, true ) );
        assertEquals( 1, plan.deleteCount() );
        assertEquals( 1, plan.count( GMirrorAction.Reason.EXTRA ) );

        engine.execute( plan, spec( master, mc, replica, rc, true ), null );

        assertFalse( Files.exists( replica.toPath().resolve( "extra.txt" ) ), "extra must be deleted" );
        assertTrue( Files.exists( replica.toPath().resolve( "a.txt" ) ), "master files must remain" );
    }

    @Test
    void blastRadiusGuard_abortsLargeDelete_unlessConfirmed( @TempDir Path tmp ) throws Exception
    {
        File master = mkdir( tmp, "master" ), replica = mkdir( tmp, "replica" );
        write( master.toPath().resolve( "keep.txt" ), "keep" );

        GConnection mc = localConn( master ), rc = localConn( replica );
        engine.execute( engine.scan( spec( master, mc, replica, rc, false ) ),
                        spec( master, mc, replica, rc, false ), null );

        // 3 extras on a 4-item replica → 75% delete, over the 50% default threshold.
        for( String n : List.of( "x1", "x2", "x3" ) ) write( replica.toPath().resolve( n ), n );

        GMirrorPlan plan = engine.scan( spec( master, mc, replica, rc, true ) );
        assertEquals( 3, plan.deleteCount() );
        assertTrue( plan.blastRadiusFraction() > 0.5 );

        // Without confirmation → refused, and nothing deleted.
        assertThrows( GMirrorSafetyException.class,
            () -> engine.execute( plan, spec( master, mc, replica, rc, true ), null ) );
        assertTrue( Files.exists( replica.toPath().resolve( "x1" ) ), "guard must abort before deleting" );

        // With confirmation → proceeds.
        GMirrorSpec confirmed = spec( master, mc, replica, rc, true );
        confirmed.confirmedLargeDelete = true;
        engine.execute( engine.scan( spec( master, mc, replica, rc, true ) ), confirmed, null );
        assertFalse( Files.exists( replica.toPath().resolve( "x1" ) ), "confirmed large delete should proceed" );
    }

    @Test
    void emptyMasterGuard_refusesToWipeReplica( @TempDir Path tmp ) throws Exception
    {
        File master = mkdir( tmp, "master" ), replica = mkdir( tmp, "replica" );
        write( replica.toPath().resolve( "important1.txt" ), "data" );
        write( replica.toPath().resolve( "important2.txt" ), "data" );

        GConnection mc = localConn( master ), rc = localConn( replica );

        // Empty master + deleteExtras → hard stop at scan.
        assertThrows( GMirrorSafetyException.class,
            () -> engine.scan( spec( master, mc, replica, rc, true ) ) );

        // Additive (deleteExtras off) over an empty master is a harmless no-op.
        GMirrorPlan additive = engine.scan( spec( master, mc, replica, rc, false ) );
        assertTrue( additive.isEmpty() );
        assertTrue( Files.exists( replica.toPath().resolve( "important1.txt" ) ) );
    }

    @Test
    void containmentGuard_refusesDeleteOutsideRoot( @TempDir Path tmp ) throws Exception
    {
        File master = mkdir( tmp, "master" ), replica = mkdir( tmp, "replica" );
        GConnection mc = localConn( master ), rc = localConn( replica );

        // Hand-craft a plan whose delete target is outside the replica root.
        GFileItem outside = new GFileItem( new File( tmp.resolve( "OUTSIDE.txt" ).toString() ) );
        GMirrorEntry r = new GMirrorEntry( "OUTSIDE.txt", false, 0, 0, outside );
        GMirrorAction del = new GMirrorAction( "OUTSIDE.txt", GMirrorAction.Type.DELETE,
                                               GMirrorAction.Reason.EXTRA, null, r, 0 );
        GMirrorPlan plan = new GMirrorPlan( List.of( del ), 100 ); // low blast radius → reaches containment

        assertThrows( GMirrorSafetyException.class,
            () -> engine.execute( plan, spec( master, mc, replica, rc, true ), null ) );
    }

    private static File mkdir( Path tmp, String name )
    {
        File f = tmp.resolve( name ).toFile();
        assertTrue( f.mkdirs() );
        return f;
    }
}
