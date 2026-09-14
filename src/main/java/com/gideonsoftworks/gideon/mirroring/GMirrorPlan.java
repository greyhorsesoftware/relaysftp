package com.gideonsoftworks.gideon.mirroring;

import java.util.Collections;
import java.util.List;

/**
 * The result of {@code GMirrorEngine.scan} — an ordered, read-only list of {@link GMirrorAction}s
 * plus summary counts. This is the artifact the Review screen renders and execute consumes;
 * producing it performs no writes (design §2, §5: scan is a dry run).
 *
 * Ordering guarantee: creates come first in parent-before-child order (so a directory exists
 * before its contents are copied), then deletes in child-before-parent order (so a directory
 * is empty before it is removed), then the unchanged (SKIP) entries for transparency.
 */
public final class GMirrorPlan
{
    private final List<GMirrorAction> actions;
    private final int replicaEntryCount;   // total nodes scanned on the replica side (for blast-radius)
    private int filteredCount;             // items excluded by the transfer filters (set by scan)

    public GMirrorPlan( List<GMirrorAction> actions, int replicaEntryCount )
    {
        this.actions = Collections.unmodifiableList( actions );
        this.replicaEntryCount = replicaEntryCount;
    }

    public List<GMirrorAction> actions() { return actions; }

    public int replicaEntryCount() { return replicaEntryCount; }

    public int  filteredCount() { return filteredCount; }
    public void setFilteredCount( int n ) { filteredCount = n; }

    /** Number of delete operations (files + dirs). */
    public int deleteCount()
    {
        return count( GMirrorAction.Type.DELETE ) + count( GMirrorAction.Type.RMDIR );
    }

    /** Fraction of the replica that would be deleted — the blast-radius guard's input (§5.4). */
    public double blastRadiusFraction()
    {
        return ( replicaEntryCount == 0 ) ? 0.0 : (double) deleteCount() / replicaEntryCount;
    }

    public boolean isEmpty()
    {
        for( GMirrorAction a : actions ) if( a.type != GMirrorAction.Type.SKIP ) return false;
        return true;
    }

    public int count( GMirrorAction.Reason reason )
    {
        int n = 0;
        for( GMirrorAction a : actions ) if( a.reason == reason ) n++;
        return n;
    }

    public int count( GMirrorAction.Type type )
    {
        int n = 0;
        for( GMirrorAction a : actions ) if( a.type == type ) n++;
        return n;
    }

    /** Total bytes the COPY actions will move — drives the "N MB to transfer" footer. */
    public long copyBytes()
    {
        long b = 0;
        for( GMirrorAction a : actions ) if( a.type == GMirrorAction.Type.COPY ) b += a.bytes;
        return b;
    }

    @Override
    public String toString()
    {
        return "GMirrorPlan[new=" + count( GMirrorAction.Reason.NEW )
            + " changed=" + count( GMirrorAction.Reason.CHANGED )
            + " extra=" + count( GMirrorAction.Reason.EXTRA )
            + " equal=" + count( GMirrorAction.Reason.EQUAL )
            + " copyBytes=" + copyBytes() + "]";
    }
}
