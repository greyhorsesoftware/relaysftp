package com.gideonsoftworks.gideon.mirroring;

import com.gideonsoftworks.gideon.mirroring.detectors.SizeMtimeChangeDetector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The Save Report output carries direction, settings, detector, and per-side size/mtime values. */
class GMirrorReportTest
{
    @Test
    void report_includesDirectionDetectorAndPerSideSizeMtime()
    {
        GMirrorSpec s = new GMirrorSpec();
        s.direction = GMirrorSpec.Direction.UPLOAD;
        s.detector = new SizeMtimeChangeDetector();
        s.clockOffsetMillis = 3600000;

        GMirrorEntry master  = new GMirrorEntry( "/a.txt", false, 100, 1_700_000_000_000L, null );
        GMirrorEntry replica = new GMirrorEntry( "/a.txt", false, 100, 1_699_999_990_000L, null );
        GMirrorAction changed = new GMirrorAction( "/a.txt", GMirrorAction.Type.COPY, GMirrorAction.Reason.CHANGED, master, replica, 100 );
        GMirrorAction added   = new GMirrorAction( "/b.txt", GMirrorAction.Type.COPY, GMirrorAction.Reason.NEW,
            new GMirrorEntry( "/b.txt", false, 50, 1_700_000_000_000L, null ), null, 50 );

        String r = GMirrorReport.build( s, new GMirrorPlan( List.of( changed, added ), 1 ) );

        assertTrue( r.contains( "UPLOAD" ), r );
        assertTrue( r.contains( "size+mtime" ), "detector name" );
        assertTrue( r.contains( "3600000 ms" ), "clock offset" );
        assertTrue( r.contains( "/a.txt" ) && r.contains( "/b.txt" ), "file names" );
        assertTrue( r.contains( "COPY/CHANGED" ) && r.contains( "COPY/NEW" ), "action/reason" );
        assertTrue( r.contains( "master: 100b @" ), "master size/mtime" );
        assertTrue( r.contains( "replica: 100b @" ), "replica size/mtime" );
        assertTrue( r.contains( "replica: —" ), "new file has no replica side" );
    }

    @Test
    void report_showsDigestWhenEngineHadOne()
    {
        // S3-style: the engine compared on size + ETag/digest (mtime unknown) — both must appear.
        GMirrorEntry m = new GMirrorEntry( "/o.bin", false, 10, 0, null, "abc123etag" );
        GMirrorEntry r = new GMirrorEntry( "/o.bin", false, 10, 0, null, "def456etag" );
        GMirrorAction a = new GMirrorAction( "/o.bin", GMirrorAction.Type.COPY, GMirrorAction.Reason.CHANGED, m, r, 10 );

        String out = GMirrorReport.build( null, new GMirrorPlan( List.of( a ), 1 ) );
        assertTrue( out.contains( "[abc123etag]" ), "master digest shown: " + out );
        assertTrue( out.contains( "[def456etag]" ), "replica digest shown" );
        assertFalse( out.contains( " @ " ), "no mtime shown when unknown" );
    }

    @Test
    void report_addsOutcomeColumnWhenRunStateSupplied()
    {
        GMirrorAction ok   = new GMirrorAction( "/ok.txt", GMirrorAction.Type.COPY, GMirrorAction.Reason.NEW, new GMirrorEntry( "/ok.txt", false, 5, 0, null ), null, 5 );
        GMirrorAction bad  = new GMirrorAction( "/bad.txt", GMirrorAction.Type.COPY, GMirrorAction.Reason.NEW, new GMirrorEntry( "/bad.txt", false, 5, 0, null ), null, 5 );
        GMirrorAction same = new GMirrorAction( "/same.txt", GMirrorAction.Type.SKIP, GMirrorAction.Reason.EQUAL, null, null, 0 );
        GMirrorPlan plan = new GMirrorPlan( List.of( ok, bad, same ), 2 );
        GMirrorRunState state = new GMirrorRunState( plan, 3 );
        state.markRunning( 0 ); state.markDone( 0 );
        state.markRunning( 1 ); state.markSkipped( 1, "permission denied" );

        String without = GMirrorReport.build( null, plan );
        assertFalse( without.contains( "outcome" ), "no outcome column without a run state" );

        String with = GMirrorReport.build( null, plan, state );
        assertTrue( with.contains( "| outcome" ), with );
        assertTrue( with.contains( "/ok.txt | master: 5b | replica: — | 5b | done" ), with );
        assertTrue( with.contains( "/bad.txt | master: 5b | replica: — | 5b | skipped · permission denied" ), with );
        assertTrue( with.contains( "/same.txt | master: — | replica: — | not run" ), "SKIP rows are not part of the run: " + with );
    }

    @Test
    void report_handlesNullSpecAndEmptyPlan()
    {
        String r = GMirrorReport.build( null, new GMirrorPlan( List.of(), 0 ) );
        assertTrue( r.contains( "Mirror Report" ) );
        assertTrue( r.contains( "0 to copy" ) );
    }
}
