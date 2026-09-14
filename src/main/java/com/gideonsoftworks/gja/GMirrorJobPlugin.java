package com.gideonsoftworks.gja;

import com.gideonsoftworks.gcore.GBookmark;
import com.gideonsoftworks.gcore.GConnection;
import com.gideonsoftworks.gcore.GConnectionException;
import com.gideonsoftworks.gideon.mirroring.GMirrorEngine;
import com.gideonsoftworks.gideon.mirroring.GMirrorPlan;
import com.gideonsoftworks.gideon.mirroring.GMirrorResult;
import com.gideonsoftworks.gideon.mirroring.GMirrorRunState;
import com.gideonsoftworks.gideon.mirroring.GMirrorSpec;

/**
 * Runs one confirmed mirror as a queued job (design: MIRROR_QUEUE_PLAN). The job carries the
 * {@link GMirrorSpec} and the snapshot {@link GMirrorPlan} the user approved on the Review screen
 * (as job properties); this plugin drives {@link GMirrorEngine#execute}, reporting progress through
 * the standard {@link #updateTransferStats}/{@link GJobStatus} channel and honoring cancellation.
 *
 * <p>Not a "transfer job": it manages its <em>own</em> cloned remote connection (like parallel
 * transfers do) rather than the single-connection model, so a running mirror never shares an SFTP
 * transfer channel with the browser's connection. The local side of a mirror is plain file I/O, so
 * only the remote side needs a connection.
 */
public final class GMirrorJobPlugin extends GJobPlugin
{
    /** Job-property keys for the spec, the approved plan, and (on completion) the result. */
    public static final String MIRROR_SPEC_PROP   = "MIRROR_SPEC_PROP";
    public static final String MIRROR_PLAN_PROP   = "MIRROR_PLAN_PROP";
    public static final String MIRROR_RESULT_PROP = "MIRROR_RESULT_PROP";
    /** The live {@link GMirrorRunState} the Running panel polls; set by the panel at submit, else created here. */
    public static final String MIRROR_RUN_STATE_PROP = "MIRROR_RUN_STATE_PROP";

    private final GMirrorEngine engine = new GMirrorEngine();

    public GMirrorJobPlugin( final GJobStatus js )
    {
        super( js );
    }

    @Override
    public void processJobDescription( final GJobDescription d, final GConnection c ) throws GConnectionException
    {
        GMirrorSpec spec = (GMirrorSpec) d.getProperty( MIRROR_SPEC_PROP );
        GMirrorPlan plan = (GMirrorPlan) d.getProperty( MIRROR_PLAN_PROP );
        if( spec == null || plan == null )
        {
            signalError( "Mirror job is missing its spec or plan" );
            return;
        }

        // Clone the remote side and run on that connection, so we don't contend with the browser's
        // connection. UPLOAD talks to the replica; DOWNLOAD talks to the master.
        boolean upload = ( spec.direction == GMirrorSpec.Direction.UPLOAD );
        GConnection browserRemote = upload ? spec.replicaConn : spec.masterConn;
        GConnection remote = new GConnection( new GBookmark( browserRemote.getBookmark() ) );

        try
        {
            remote.login();
            if( upload ) spec.replicaConn = remote; else spec.masterConn = remote;

            GMirrorRunState state = (GMirrorRunState) d.getProperty( MIRROR_RUN_STATE_PROP );
            if( state == null )
            {
                state = new GMirrorRunState( plan, 1 );
                d.addProperty( MIRROR_RUN_STATE_PROP, state );
            }
            GMirrorResult result = engine.execute( plan, spec, this, state );
            d.addProperty( MIRROR_RESULT_PROP, result );
        }
        finally
        {
            // Clear any pending interrupt so the clone logs out cleanly: a cancelled mirror must not
            // leave a half-open session on the server, which can break the browser's next listing.
            boolean wasInterrupted = Thread.interrupted();
            try { remote.logout(); } catch( Exception ignore ) {}
            if( wasInterrupted ) Thread.currentThread().interrupt();   // restore for run()'s cancel check
        }
    }
}
