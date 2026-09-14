package com.gideonsoftworks.gideon.mirroring;

import com.gideonsoftworks.gcore.GFileItem;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Renders a human-readable mirror report from the spec + plan: the direction (upload/download), the settings,
 * the detector, and — per file — the size@mtime each side was compared on. Pure (no Swing), so it's testable;
 * the per-side values make a churn/skew issue visible at a glance. Used by {@code GMirrorPanel}'s Save Report.
 */
public final class GMirrorReport
{
    private GMirrorReport() {}

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss" );

    public static String build( GMirrorSpec s, GMirrorPlan plan )
    {
        return build( s, plan, null );
    }

    /** Same, with an outcome column per action when a run's {@code state} is supplied (done / skipped · reason / cancelled …). */
    public static String build( GMirrorSpec s, GMirrorPlan plan, GMirrorRunState state )
    {
        StringBuilder sb = new StringBuilder();
        sb.append( "RelaySFTP Mirror Report\n=======================\n\n" );

        if( s != null )
        {
            boolean up = s.direction == GMirrorSpec.Direction.UPLOAD;
            sb.append( "Direction:         " ).append( up ? "UPLOAD (local → remote)" : "DOWNLOAD (remote → local)" ).append( '\n' );
            sb.append( "Master (source):   " ).append( rootDesc( s.masterRoot ) ).append( '\n' );
            sb.append( "Replica (dest):    " ).append( rootDesc( s.replicaRoot ) ).append( '\n' );
            sb.append( "Detector:          " ).append( s.detector != null ? s.detector.name() : "?" ).append( '\n' );
            sb.append( "Clock offset:      " ).append( s.clockOffsetMillis ).append( " ms" )
              .append( s.clockOffsetAuto ? " (auto — subtracted from master mtime)" : " (manual)" ).append( '\n' );
            sb.append( "Delete extras:     " ).append( s.deleteExtras ).append( '\n' );
            sb.append( "Modified within:   " ).append( s.modifiedWithin != null ? s.modifiedWithin.toString() : "all files" ).append( '\n' );
            sb.append( "Transfer filters:  " ).append( s.applyTransferFilters ).append( '\n' );
            sb.append( "Follow symlinks:   " ).append( s.followSymlinks ).append( "\n\n" );
        }

        if( plan != null )
        {
            int copy = plan.count( GMirrorAction.Reason.NEW ) + plan.count( GMirrorAction.Reason.CHANGED );
            sb.append( "Summary: " ).append( copy ).append( " to copy, " )
              .append( plan.deleteCount() ).append( " to delete, " )
              .append( plan.count( GMirrorAction.Reason.EQUAL ) ).append( " unchanged" )
              .append( "  (replica had " ).append( plan.replicaEntryCount() ).append( " items, " )
              .append( plan.filteredCount() ).append( " filtered)\n\n" );

            sb.append( "Per-file detail — the values the engine compared on each side (size, mtime, digest as available):\n" );
            sb.append( "action/reason | path | master | replica | bytes" ).append( state != null ? " | outcome" : "" ).append( '\n' );
            sb.append( "--------------------------------------------------------------------------------\n" );
            for( GMirrorAction a : plan.actions() )
            {
                sb.append( a.type ).append( '/' ).append( a.reason )
                  .append( " | " ).append( a.relPath )
                  .append( " | master: " ).append( entryDesc( a.master ) )
                  .append( " | replica: " ).append( entryDesc( a.replica ) )
                  .append( a.bytes > 0 ? " | " + a.bytes + "b" : "" );
                if( state != null ) sb.append( " | " ).append( outcome( state, a ) );
                sb.append( '\n' );
            }
        }
        return sb.toString();
    }

    private static String outcome( GMirrorRunState state, GMirrorAction a )
    {
        int i = state.indexOf( a );
        if( i < 0 ) return "not run";   // SKIP rows and anything deselected on Review
        switch( state.status( i ) )
        {
            case DONE:      return "done";
            case SKIPPED:   return "skipped · " + state.reason( i );
            case RUNNING:   return "running";
            case CANCELLED: return "cancelled";
            default:        return "queued";
        }
    }

    private static String rootDesc( GFileItem root )
    {
        return root != null ? root.getCompletePath() : "?";
    }

    /** "<size>b @ <local time>" for one side, or an em-dash when that side has no node (new / deleted). */
    private static String entryDesc( GMirrorEntry e )
    {
        if( e == null ) return "—";
        StringBuilder b = new StringBuilder().append( e.size ).append( "b" );
        if( e.mtimeMillis != 0 )
            b.append( " @ " ).append( Instant.ofEpochMilli( e.mtimeMillis ).atZone( ZoneId.systemDefault() ).format( TIME ) );
        if( e.digest != null && !e.digest.isEmpty() )
            b.append( " [" ).append( e.digest ).append( ']' );   // S3 ETag / checksum, when the engine had one
        return b.toString();
    }
}
