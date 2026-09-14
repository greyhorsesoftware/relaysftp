package com.gideonsoftworks.gideon.mirroring;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for {@link GMirrorEngine#diff} — the new/changed/extra/equal matrix on
 * synthetic trees, no live connection. (Scan enumeration and execute are integration-tested
 * separately.)
 */
class GMirrorEngineDiffTest
{
    private final GMirrorEngine engine = new GMirrorEngine();

    // ---- helpers ----

    private static GMirrorEntry file( String rel, long size, long mtime )
    {
        return new GMirrorEntry( rel, false, size, mtime, null );
    }

    private static GMirrorEntry dir( String rel )
    {
        return new GMirrorEntry( rel, true, 0, 0, null );
    }

    private static Map<String, GMirrorEntry> tree( GMirrorEntry... entries )
    {
        Map<String, GMirrorEntry> m = new LinkedHashMap<>();
        for( GMirrorEntry e : entries ) m.put( e.relPath, e );
        return m;
    }

    private static GMirrorSpec spec( boolean deleteExtras )
    {
        GMirrorSpec s = new GMirrorSpec();           // detector defaults to size+mtime
        s.deleteExtras = deleteExtras;
        return s;
    }

    private static GMirrorAction action( GMirrorPlan p, String rel )
    {
        for( GMirrorAction a : p.actions() ) if( a.relPath.equals( rel ) ) return a;
        return null;
    }

    private static int indexOf( GMirrorPlan p, String rel )
    {
        for( int i = 0; i < p.actions().size(); i++ )
            if( p.actions().get( i ).relPath.equals( rel ) ) return i;
        return -1;
    }

    // ---- new ----

    @Test
    void newFile_isCopy()
    {
        GMirrorPlan p = engine.diff( tree( file( "a.txt", 10, 1000 ) ), tree(), spec( false ) );
        GMirrorAction a = action( p, "a.txt" );
        assertEquals( GMirrorAction.Type.COPY, a.type );
        assertEquals( GMirrorAction.Reason.NEW, a.reason );
        assertEquals( 10, a.bytes );
    }

    @Test
    void newDir_isMkdir()
    {
        GMirrorPlan p = engine.diff( tree( dir( "sub" ) ), tree(), spec( false ) );
        GMirrorAction a = action( p, "sub" );
        assertEquals( GMirrorAction.Type.MKDIR, a.type );
        assertEquals( GMirrorAction.Reason.NEW, a.reason );
    }

    // ---- changed ----

    @Test
    void changedBySize_isCopy()
    {
        GMirrorPlan p = engine.diff( tree( file( "a", 20, 1000 ) ),
                                     tree( file( "a", 10, 1000 ) ), spec( false ) );
        assertEquals( GMirrorAction.Reason.CHANGED, action( p, "a" ).reason );
        assertEquals( GMirrorAction.Type.COPY, action( p, "a" ).type );
    }

    @Test
    void changedByMtime_beyondTolerance_isCopy()
    {
        GMirrorPlan p = engine.diff( tree( file( "a", 10, 1_000_000 ) ),
                                     tree( file( "a", 10, 1_005_000 ) ), spec( false ) );
        assertEquals( GMirrorAction.Reason.CHANGED, action( p, "a" ).reason );
    }

    // ---- equal ----

    @Test
    void sameSizeAndMtimeWithinTolerance_isEqualSkip()
    {
        GMirrorPlan p = engine.diff( tree( file( "a", 10, 1_000_000 ) ),
                                     tree( file( "a", 10, 1_000_500 ) ), spec( false ) );
        GMirrorAction a = action( p, "a" );
        assertEquals( GMirrorAction.Reason.EQUAL, a.reason );
        assertEquals( GMirrorAction.Type.SKIP, a.type );
    }

    @Test
    void matchingDirs_areEqualSkip()
    {
        GMirrorPlan p = engine.diff( tree( dir( "d" ) ), tree( dir( "d" ) ), spec( false ) );
        assertEquals( GMirrorAction.Type.SKIP, action( p, "d" ).type );
    }

    @Test
    void unknownMtime_fallsBackToSizeOnly()
    {
        // Same size, but replica mtime unknown (0) → must NOT report a spurious change.
        GMirrorPlan p = engine.diff( tree( file( "a", 10, 1_000_000 ) ),
                                     tree( file( "a", 10, 0 ) ), spec( false ) );
        assertEquals( GMirrorAction.Reason.EQUAL, action( p, "a" ).reason );
    }

    // ---- extras / deleteExtras gate ----

    @Test
    void extra_additiveMirror_isNotDeleted()
    {
        GMirrorPlan p = engine.diff( tree(), tree( file( "stale", 5, 1000 ) ), spec( false ) );
        assertNull( action( p, "stale" ), "additive mirror must not touch replica-only files" );
        assertTrue( p.isEmpty() );
    }

    @Test
    void extraFile_withDeleteExtras_isDeleted()
    {
        // Non-empty master (a shared file) so the extra is a real extra, not an empty-master wipe.
        GMirrorPlan p = engine.diff( tree( file( "keep", 1, 1 ) ),
                                     tree( file( "keep", 1, 1 ), file( "stale", 5, 1000 ) ), spec( true ) );
        GMirrorAction a = action( p, "stale" );
        assertEquals( GMirrorAction.Type.DELETE, a.type );
        assertEquals( GMirrorAction.Reason.EXTRA, a.reason );
    }

    @Test
    void extraDir_withDeleteExtras_isRmdir()
    {
        GMirrorPlan p = engine.diff( tree( file( "keep", 1, 1 ) ),
                                     tree( file( "keep", 1, 1 ), dir( "old" ) ), spec( true ) );
        assertEquals( GMirrorAction.Type.RMDIR, action( p, "old" ).type );
    }

    @Test
    void emptyMasterWithDeleteExtras_isRefused()
    {
        // §5.5: an empty master + deletes must never be read as "wipe the replica".
        assertThrows( GMirrorSafetyException.class,
            () -> engine.diff( tree(), tree( file( "data", 5, 1000 ) ), spec( true ) ) );
    }

    // ---- ordering guarantees ----

    @Test
    void creates_parentBeforeChild()
    {
        GMirrorPlan p = engine.diff( tree( dir( "a" ), file( "a/b.txt", 10, 1000 ) ),
                                     tree(), spec( false ) );
        assertTrue( indexOf( p, "a" ) < indexOf( p, "a/b.txt" ),
            "mkdir of a parent must precede copying its contents" );
    }

    @Test
    void deletes_childBeforeParent()
    {
        GMirrorPlan p = engine.diff( tree( file( "keep", 1, 1 ) ),
                                     tree( file( "keep", 1, 1 ), dir( "old" ), file( "old/x.txt", 3, 1000 ) ),
                                     spec( true ) );
        assertTrue( indexOf( p, "old/x.txt" ) < indexOf( p, "old" ),
            "a directory must be emptied before it is removed" );
    }

    // ---- summary ----

    @Test
    void planSummary_countsAndBytes()
    {
        Map<String, GMirrorEntry> master = tree(
            file( "new.bin", 100, 2000 ),     // NEW copy
            file( "changed.txt", 50, 3000 ),  // CHANGED copy
            file( "same.txt", 10, 1000 ) );   // EQUAL
        Map<String, GMirrorEntry> replica = tree(
            file( "changed.txt", 40, 3000 ),
            file( "same.txt", 10, 1000 ),
            file( "gone.tmp", 7, 1000 ) );    // EXTRA

        GMirrorPlan p = engine.diff( master, replica, spec( true ) );

        assertEquals( 1, p.count( GMirrorAction.Reason.NEW ) );
        assertEquals( 1, p.count( GMirrorAction.Reason.CHANGED ) );
        assertEquals( 1, p.count( GMirrorAction.Reason.EQUAL ) );
        assertEquals( 1, p.count( GMirrorAction.Reason.EXTRA ) );
        assertEquals( 150, p.copyBytes() );   // 100 (new) + 50 (changed)
        assertFalse( p.isEmpty() );
    }

    // ---- modified-within window (§4) ----

    private static final long NOW = 1_000_000_000_000L;
    private static final long DAY = 86_400_000L;
    private static final long HR  = 3_600_000L;

    @Test
    void modifiedWithin_newFile_copiedOnlyIfRecent()
    {
        GMirrorSpec s = spec( false );
        s.modifiedWithin = java.time.Duration.ofDays( 1 );

        Map<String, GMirrorEntry> master = tree(
            file( "recent.txt", 10, NOW - 1000 ),
            file( "old.txt",    10, NOW - 2 * DAY ) );

        GMirrorPlan p = engine.diff( master, tree(), s, NOW );   // empty replica → both would be "new"
        assertEquals( GMirrorAction.Type.COPY, action( p, "recent.txt" ).type );
        assertEquals( GMirrorAction.Type.SKIP, action( p, "old.txt"    ).type, "outside window → not copied" );
    }

    @Test
    void modifiedWithin_changedFile_copiedOnlyIfRecent()
    {
        GMirrorSpec s = spec( false );
        s.modifiedWithin = java.time.Duration.ofDays( 1 );

        Map<String, GMirrorEntry> master  = tree( file( "a.txt", 20, NOW - 1000 ), file( "b.txt", 20, NOW - 2 * DAY ) );
        Map<String, GMirrorEntry> replica = tree( file( "a.txt", 10, NOW - 1000 ), file( "b.txt", 10, NOW - 2 * DAY ) );

        GMirrorPlan p = engine.diff( master, replica, s, NOW );
        assertEquals( GMirrorAction.Type.COPY, action( p, "a.txt" ).type, "recent + changed → copy" );
        assertEquals( GMirrorAction.Type.SKIP, action( p, "b.txt" ).type, "changed but old → not copied" );
    }

    @Test
    void modifiedWithin_neverCausesDeletes()
    {
        GMirrorSpec s = spec( true );   // deleteExtras ON
        s.modifiedWithin = java.time.Duration.ofDays( 1 );

        Map<String, GMirrorEntry> master  = tree( file( "old.txt",   10, NOW - 5 * DAY ) );  // on master, old
        Map<String, GMirrorEntry> replica = tree( file( "extra.txt", 10, NOW ) );             // replica-only

        GMirrorPlan p = engine.diff( master, replica, s, NOW );
        assertEquals( GMirrorAction.Type.SKIP,   action( p, "old.txt"   ).type, "old master file: not copied, not deleted" );
        assertEquals( GMirrorAction.Type.DELETE, action( p, "extra.txt" ).type, "a real extra is still deleted" );
    }

    @Test
    void modifiedWithin_appliesClockOffset()
    {
        GMirrorSpec s = spec( false );
        s.modifiedWithin = java.time.Duration.ofHours( 1 );
        // file really modified 30 min ago, but the server clock runs 3h behind → its mtime reads 3.5h old
        long masterMtime = NOW - 30 * 60_000L - 3 * HR;
        Map<String, GMirrorEntry> master = tree( file( "r.txt", 10, masterMtime ) );

        s.clockOffsetMillis = -3 * HR;   // correct for the 3h-behind server → lands 30 min ago → within 1h
        assertEquals( GMirrorAction.Type.COPY, action( engine.diff( master, tree(), s, NOW ), "r.txt" ).type );

        s.clockOffsetMillis = 0;          // uncorrected → reads 3.5h old → outside the window
        assertEquals( GMirrorAction.Type.SKIP, action( engine.diff( master, tree(), s, NOW ), "r.txt" ).type );
    }

    // ---- auto clock-offset measurement ----

    @Test
    void measureClockOffset_isMedianOfSameSizePairs()
    {
        Map<String, GMirrorEntry> master = tree(
            file( "a", 10, 100_000 + HR ), file( "b", 20, 200_000 + HR ), file( "c", 30, 300_000 + HR ),
            file( "d", 99, 999_999 ) );                       // different size on replica → excluded
        Map<String, GMirrorEntry> replica = tree(
            file( "a", 10, 100_000 ), file( "b", 20, 200_000 ), file( "c", 30, 300_000 ),
            file( "d", 50, 0 ) );

        assertEquals( HR, GMirrorEngine.measureClockOffset( master, replica ) );
    }

    @Test
    void measureClockOffset_zeroWhenNoCommonSameSizeFiles()
    {
        assertEquals( 0, GMirrorEngine.measureClockOffset(
            tree( file( "a", 10, 5 ) ), tree( file( "b", 10, 5 ) ) ) );   // no shared keys
    }

    @Test
    void measureClockOffset_zeroBelowSampleThreshold()
    {
        // only 2 matching same-size pairs (< MIN_OFFSET_SAMPLES) → not trusted, don't "explain away"
        Map<String, GMirrorEntry> master  = tree( file( "a", 10, 100_000 + HR ), file( "b", 20, 200_000 + HR ) );
        Map<String, GMirrorEntry> replica = tree( file( "a", 10, 100_000 ),      file( "b", 20, 200_000 ) );
        assertEquals( 0, GMirrorEngine.measureClockOffset( master, replica ) );
    }
}
