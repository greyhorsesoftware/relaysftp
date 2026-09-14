package com.gideonsoftworks.gja;

import com.gideonsoftworks.gcore.GConnection;
import com.gideonsoftworks.gcore.GConnectionException;
import com.gideonsoftworks.gcore.GFileItem;

/**
 * The remote-folder mtime poll as a task-queue job (roadmap 3.1, docs/1.8.0/MTIME_POLL_JOB_PLAN.md).
 * Runs on the browser's own connection like {@link GPingJobPlugin} — the task queue serialises it
 * with every other job on that connection, so the poll can never interleave with a listing or a
 * rename on the same SSH/FTP channel. The reading is stored on the job as
 * {@link GJobDescription#MTIME_RESULT_JOB_PROP} (-1 when unsupported or unreadable; never throws).
 */
public final class GMtimeJobPlugin extends GJobPlugin
{
    public GMtimeJobPlugin( final GJobStatus js )
    {
        super( js );
    }

    @Override
    public void processJobDescription( final GJobDescription d, final GConnection c ) throws GConnectionException
    {
        super.processJobDescription( d, c );
        GFileItem target = d.getTargetFile();
        long mtime = ( target == null ) ? -1L : c.getRemoteMtime( target.getCompletePath() );
        d.addProperty( GJobDescription.MTIME_RESULT_JOB_PROP, Long.valueOf( mtime ) );
    }
}
