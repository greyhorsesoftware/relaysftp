package com.gideonsoftworks.gideon.core;

import com.gideonsoftworks.gja.GAbstractJobManagerListener;
import com.gideonsoftworks.gja.GJobManagerEvent;
import com.gideonsoftworks.gja.GJobDescription;
import com.gideonsoftworks.gja.GJobStatus;
import com.gideonsoftworks.gcore.GAuditLog;
import com.gideonsoftworks.gcore.GConnection;
import com.gideonsoftworks.gcore.GConnectionException;
import com.gideonsoftworks.gcore.GFileItem;

/**
 * Audits every meaningful background job (download/upload/delete/chmod/move/rename/mkdir) to
 * the global {@link GAuditLog} when it finishes. Protocol-agnostic: it reads the outcome from
 * the generic {@code GJobDescription}/{@code GJobStatus}, so it covers FTP/SFTP/S3/local with
 * one hook instead of per-plugin calls. One instance is registered on each window's
 * {@code GJobManager} in {@code GFrame}; they all write to the single global audit log.
 *
 * Navigation/housekeeping jobs (login, listing, refresh, ping, disconnect) are intentionally
 * skipped — logins are already audited via {@code GAuditLog.login}.
 */
public final class GAuditJobListener extends GAbstractJobManagerListener
{
    @Override
    public void jm_jobCompleted( final GJobManagerEvent e )
    {
        audit( e, "ok", null );
    }

    @Override
    public void jm_jobFailed( final GJobManagerEvent e )
    {
        GConnectionException ex = exceptionOf( e );
        audit( e, "failed", ex != null ? ex.getMessage() : null );
    }

    @Override
    public void jm_jobStopped( final GJobManagerEvent e )
    {
        audit( e, "cancelled", null );
    }

    private void audit( final GJobManagerEvent e, final String result, final String error )
    {
        if( e == null ) return;
        GJobDescription jd = e.getJobDescription();
        if( jd == null ) return;

        String op = opForJobType( jd.getJobType() );
        if( op == null ) return;   // skip navigation/login noise

        GJobStatus st = jd.getJobStatus();
        long bytes    = ( st != null ) ? st.getBytesTransferred() : 0;
        long ms       = ( st != null ) ? st.getDurationMs() : 0;

        GAuditLog.job( op, connLabel( jd ), targetLabel( jd ), bytes, ms, result, error );
    }

    private static GConnectionException exceptionOf( final GJobManagerEvent e )
    {
        if( e == null || e.getJobDescription() == null || e.getJobDescription().getJobStatus() == null )
            return null;
        return e.getJobDescription().getJobStatus().getException();
    }

    private static String connLabel( final GJobDescription jd )
    {
        GConnection c = jd.getConnectionToUse();
        if( c != null && c.getBookmark() != null && c.getBookmark().getHostname() != null )
            return c.getBookmark().getHostname();
        return "";
    }

    private static String targetLabel( final GJobDescription jd )
    {
        GFileItem f = jd.getTargetFile();
        if( f != null )
            return ( f.getCompletePath() != null ) ? f.getCompletePath() : f.getName();
        return jd.getName();
    }

    private static String opForJobType( final int t )
    {
        switch( t )
        {
            case GJobDescription.DOWNLOAD_JOB_TYPE:  return "download";
            case GJobDescription.UPLOAD_JOB_TYPE:    return "upload";
            case GJobDescription.DELETE_JOB_TYPE:    return "delete";
            case GJobDescription.CHMOD_JOB_TYPE:     return "chmod";
            case GJobDescription.MOVE_JOB_TYPE:      return "move";
            case GJobDescription.RENAME_JOB_TYPE:    return "rename";
            case GJobDescription.NEWFOLDER_JOB_TYPE: return "mkdir";
            default:                                 return null;
        }
    }
}
