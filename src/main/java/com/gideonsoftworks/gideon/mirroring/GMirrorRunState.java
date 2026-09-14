package com.gideonsoftworks.gideon.mirroring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Live, per-action progress of one mirror run (docs/1.8.0/MIRROR_RUNNING_PANEL_PLAN.md §3.1).
 * The engine writes it from its worker threads; the Running panel and the Activity popover read
 * it on their timers. Nothing here touches the EDT — progress is polled, not pushed.
 *
 * <p>Rows are the plan's non-SKIP actions in execution order. Every field is atomic, so a reader
 * may see a row a few milliseconds stale but never torn.
 */
public final class GMirrorRunState
{
    public enum Status { QUEUED, RUNNING, DONE, SKIPPED, CANCELLED }

    private final List<GMirrorAction>          actions;
    private final AtomicReferenceArray<Status> status;
    private final AtomicLongArray              bytesDone;
    private final AtomicReferenceArray<String> reason;
    private final int  totalActions;
    private final long totalBytes;
    private final int  maxInFlight;
    private final long startMillis = System.currentTimeMillis();
    private final AtomicInteger doneCount    = new AtomicInteger();
    private final AtomicInteger skippedCount = new AtomicInteger();

    /** @param maxInFlight how many copies may run at once (fixed for the run, see §3.4). */
    public GMirrorRunState( GMirrorPlan plan, int maxInFlight )
    {
        List<GMirrorAction> rows = new ArrayList<>();
        long bytes = 0;
        for( GMirrorAction a : plan.actions() )
        {
            if( a.type == GMirrorAction.Type.SKIP ) continue;
            rows.add( a );
            if( a.type == GMirrorAction.Type.COPY ) bytes += a.bytes;
        }
        this.actions      = Collections.unmodifiableList( rows );
        this.totalActions = rows.size();
        this.totalBytes   = bytes;
        this.maxInFlight  = Math.max( 1, maxInFlight );
        this.status       = new AtomicReferenceArray<>( rows.size() );
        this.bytesDone    = new AtomicLongArray( rows.size() );
        this.reason       = new AtomicReferenceArray<>( rows.size() );
        for( int i = 0; i < rows.size(); i++ ) status.set( i, Status.QUEUED );
    }

    // ---- shape ----

    public int  size()                   { return totalActions; }
    public GMirrorAction action( int i ) { return actions.get( i ); }
    public List<GMirrorAction> actions() { return actions; }
    public long totalBytes()             { return totalBytes; }
    public int  maxInFlight()            { return maxInFlight; }
    public long startMillis()            { return startMillis; }

    /** Row index of {@code a} (identity), or -1. */
    public int indexOf( GMirrorAction a )
    {
        for( int i = 0; i < totalActions; i++ ) if( actions.get( i ) == a ) return i;
        return -1;
    }

    // ---- per-row state ----

    public Status status( int i )    { return status.get( i ); }
    public long   bytesDone( int i ) { return bytesDone.get( i ); }
    public String reason( int i )    { return reason.get( i ); }

    public void markRunning( int i )                 { status.set( i, Status.RUNNING ); }
    public void setBytes( int i, long bytes )        { bytesDone.set( i, bytes ); }

    public void markDone( int i )
    {
        GMirrorAction a = actions.get( i );
        if( a.type == GMirrorAction.Type.COPY ) bytesDone.set( i, a.bytes );
        if( status.getAndSet( i, Status.DONE ) != Status.DONE ) doneCount.incrementAndGet();
    }

    public void markSkipped( int i, String why )
    {
        reason.set( i, why );
        if( status.getAndSet( i, Status.SKIPPED ) != Status.SKIPPED ) skippedCount.incrementAndGet();
    }

    /** Everything still queued or running becomes CANCELLED (a cancel arrived). */
    public void markRemainingCancelled()
    {
        for( int i = 0; i < totalActions; i++ )
        {
            Status s = status.get( i );
            if( s == Status.QUEUED || s == Status.RUNNING ) status.set( i, Status.CANCELLED );
        }
    }

    // ---- derived, for the header/footer ----

    public int  doneCount()    { return doneCount.get(); }
    public int  skippedCount() { return skippedCount.get(); }

    /** Bytes moved so far: finished copies in full plus the in-flight copies' current counts. */
    public long bytesSoFar()
    {
        long b = 0;
        for( int i = 0; i < totalActions; i++ )
        {
            Status s = status.get( i );
            if( s == Status.DONE || s == Status.RUNNING ) b += bytesDone.get( i );
        }
        return b;
    }

    /** Index of the first RUNNING row, or -1 (what the Activity popover shows as "current"). */
    public int firstRunning()
    {
        for( int i = 0; i < totalActions; i++ ) if( status.get( i ) == Status.RUNNING ) return i;
        return -1;
    }

    /** Skipped rows as records, for the result and the report. */
    public List<GMirrorSkip> skips()
    {
        List<GMirrorSkip> out = new ArrayList<>();
        for( int i = 0; i < totalActions; i++ )
            if( status.get( i ) == Status.SKIPPED )
            {
                GMirrorAction a = actions.get( i );
                out.add( new GMirrorSkip( a.relPath, a.type, reason.get( i ) ) );
            }
        return out;
    }
}
