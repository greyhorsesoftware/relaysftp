package com.gideonsoftworks.gideon.mirroring.detectors;

import com.gideonsoftworks.gideon.mirroring.GMirrorEntry;
import com.gideonsoftworks.gideon.mirroring.GMirrorSpec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SizeMtimeChangeDetector} — the default detector, whose own edge logic
 * (size, the 2s mtime tolerance, clock-offset correction, and the zero-mtime fallback) was
 * previously only covered indirectly via the diff/UI tests.
 */
class SizeMtimeChangeDetectorTest
{
    private final SizeMtimeChangeDetector d = new SizeMtimeChangeDetector();
    private final GMirrorSpec spec = new GMirrorSpec();   // clockOffsetMillis defaults to 0

    private static GMirrorEntry e( long size, long mtime )
    {
        return new GMirrorEntry( "f", false, size, mtime, null );
    }

    @Test
    void sizeDiffers_isChanged()
    {
        assertTrue( d.isChanged( e( 10, 1_000_000 ), e( 20, 1_000_000 ), spec ) );
    }

    @Test
    void sameSizeAndMtime_isUnchanged()
    {
        assertFalse( d.isChanged( e( 10, 1_000_000 ), e( 10, 1_000_000 ), spec ) );
    }

    @Test
    void mtimeWithinTolerance_isUnchanged()
    {
        assertFalse( d.isChanged( e( 10, 1_001_500 ), e( 10, 1_000_000 ), spec ), "1500ms < 2000ms tolerance" );
    }

    @Test
    void toleranceBoundary_isInclusive()
    {
        // exactly 2000ms is NOT > tolerance → unchanged; 2001ms is → changed
        assertFalse( d.isChanged( e( 10, 1_002_000 ), e( 10, 1_000_000 ), spec ), "==tolerance → unchanged" );
        assertTrue(  d.isChanged( e( 10, 1_002_001 ), e( 10, 1_000_000 ), spec ), ">tolerance → changed" );
    }

    @Test
    void zeroMtimeOnEitherSide_fallsBackToSizeOnly()
    {
        // a missing timestamp must never report a spurious change when sizes match
        assertFalse( d.isChanged( e( 10, 0 ), e( 10, 9_999_999 ), spec ), "master mtime 0 → trust size" );
        assertFalse( d.isChanged( e( 10, 9_999_999 ), e( 10, 0 ), spec ), "replica mtime 0 → trust size" );
        // ...but size still decides
        assertTrue(  d.isChanged( e( 10, 0 ), e( 20, 0 ), spec ), "size differs → changed" );
    }

    @Test
    void clockOffsetIsSubtractedFromMaster()
    {
        spec.clockOffsetMillis = 5000;   // master clock runs 5s ahead of the replica
        // 1_005_000 - 5000 == 1_000_000 → within tolerance → unchanged
        assertFalse( d.isChanged( e( 10, 1_005_000 ), e( 10, 1_000_000 ), spec ) );

        spec.clockOffsetMillis = 0;      // without correction the 5s gap exceeds tolerance
        assertTrue(  d.isChanged( e( 10, 1_005_000 ), e( 10, 1_000_000 ), spec ) );
    }
}
