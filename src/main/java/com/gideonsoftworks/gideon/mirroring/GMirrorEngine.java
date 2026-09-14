package com.gideonsoftworks.gideon.mirroring;

import com.gideonsoftworks.gcore.GAuditLog;
import com.gideonsoftworks.gcore.GBookmark;
import com.gideonsoftworks.gcore.GConnection;
import com.gideonsoftworks.gcore.GConnectionException;
import com.gideonsoftworks.gcore.GFileItem;
import com.gideonsoftworks.gcore.GTransferProgressListener;
import com.gideonsoftworks.gja.GJobPlugin;
import com.gideonsoftworks.gja.GTransferFilterManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Protocol-agnostic one-way mirror engine (design §2). {@code scan()} reads both trees through
 * their {@link GConnection}s into root-relative maps and diffs them into a {@link GMirrorPlan} —
 * a dry run with no writes. {@code diff()} is pure (no I/O) so the new/changed/extra/equal
 * matrix is unit-tested on synthetic maps.
 *
 * v1 scope: scan + plan with the size+mtime detector; symlinks are skipped; transfer-filter and
 * modified-within gating are reserved on {@link GMirrorSpec} but not yet applied. Execute and the
 * richer detectors land in later increments (build order steps 3–5).
 */
public final class GMirrorEngine
{
    /** Test hook: sleep this long after each copy's transfer, before the row is marked done (default 0). Read once per engine. */
    private final long copyDelayMs = Long.getLong( "relaysftp.mirror.copyDelayMs", 0L );

    // ---- scan: live enumeration, then diff ----

    public GMirrorPlan scan( GMirrorSpec spec ) throws GConnectionException
    {
        Set<String> filtered = new LinkedHashSet<>();   // relPaths excluded by the transfer filters (deduped across both sides)
        Map<String, GMirrorEntry> master  = enumerate( spec.masterConn,  spec.masterRoot,  spec.applyTransferFilters, filtered );
        Map<String, GMirrorEntry> replica = enumerate( spec.replicaConn, spec.replicaRoot, spec.applyTransferFilters, filtered );

        // Auto clock-offset (§4): read-only — derive the systematic local↔server mtime skew from
        // files present on both sides with equal size, so a timezone difference doesn't churn.
        if( spec.clockOffsetAuto )
            spec.clockOffsetMillis = measureClockOffset( master, replica );

        GMirrorPlan plan = diff( master, replica, spec );
        plan.setFilteredCount( filtered.size() );
        return plan;
    }

    private Map<String, GMirrorEntry> enumerate( GConnection conn, GFileItem root, boolean applyFilters, Set<String> filtered )
        throws GConnectionException
    {
        Map<String, GMirrorEntry> map = new LinkedHashMap<>();
        walk( conn, root, "", map, applyFilters, filtered );
        return map;
    }

    private void walk( GConnection conn, GFileItem folder, String prefix, Map<String, GMirrorEntry> map,
                       boolean applyFilters, Set<String> filtered )
        throws GConnectionException
    {
        if( !folder.hasBeenListed() ) conn.list( folder );

        for( int i = 0; i < folder.getChildCount(); i++ )
        {
            GFileItem c = folder.getChild( i );
            String n = c.getName();
            if( n == null || n.equals( "." ) || n.equals( ".." ) ) continue;
            if( c.getFileType() == GFileItem.G_LINK ) continue;   // v1: skip symlinks (§10.2)

            String rel = prefix.isEmpty() ? n : prefix + "/" + n;

            // §10.1: items matching the transfer filters are dropped from BOTH sides at scan, so an
            // excluded file (.git, .DS_Store, …) is invisible to the diff — never copied, never deleted.
            if( applyFilters && GTransferFilterManager.matchesFilter( c ) ) { filtered.add( rel ); continue; }

            map.put( rel, new GMirrorEntry( rel, c.getIsDir(), c.getRawSize(), mtimeOf( c ), c, c.getDigest() ) );

            if( c.getIsDir() ) walk( conn, c, rel, map, applyFilters, filtered );
        }
    }

    private static long mtimeOf( GFileItem f )
    {
        Date d = f.getDateModified();
        return ( d != null ) ? d.getTime() : 0L;
    }

    // ---- execute: build-order step 3 — additive (copies + mkdirs). Deletes land in step 4. ----

    /**
     * Apply a plan to the replica: create directories and copy new/changed files, then (if the
     * plan contains them) delete extras — all through the same {@code GConnection} the rest of the
     * app uses, in the plan's pre-sorted order (mkdir/copy parent-before-child, delete
     * child-before-parent). The §5 deletion guards are enforced up front, before any write.
     * Fail-fast: the first failed op throws.
     *
     * @param progress optional transfer-progress sink (the MIRROR job plugin in the UI); may be null.
     * @return a summary of what was done (also written to the audit log).
     */
    public GMirrorResult execute( GMirrorPlan plan, GMirrorSpec spec, GJobPlugin progress ) throws GConnectionException
    {
        return execute( plan, spec, progress, new GMirrorRunState( plan, 1 ) );
    }

    /**
     * Same, writing per-action progress into {@code state} as it goes (the Running panel and the
     * Activity popover poll it): each row goes QUEUED → RUNNING → DONE or SKIPPED (with a short
     * reason), copies report their own byte counts, and a cancel marks the rest CANCELLED.
     * The job-level byte counter is kept as a monotonic run total.
     */
    public GMirrorResult execute( GMirrorPlan plan, GMirrorSpec spec, GJobPlugin progress, GMirrorRunState state ) throws GConnectionException
    {
        enforceDeletionGuards( plan, spec );

        long startMs = System.currentTimeMillis();

        // Seed the job-status totals so the Running card / Activity popover can show a ratio.
        // (Progress is polled from GJobStatus; see MIRROR_QUEUE_PLAN §"Progress is polled".)
        if( progress != null )
        {
            progress.getJobStatus().incrementTotalFilesBy( state.size() );
            progress.getJobStatus().incrementTotalSizeBy( state.totalBytes() );
        }

        // Three ordered phases (plan §3.4): the plan's parent-before-child / child-before-parent
        // ordering still holds because each phase finishes before the next starts.
        List<Integer> mkdirs = rowsOfType( state, GMirrorAction.Type.MKDIR );
        List<Integer> copies = rowsOfType( state, GMirrorAction.Type.COPY );
        List<Integer> dels   = new ArrayList<>( rowsOfType( state, GMirrorAction.Type.DELETE ) );
        dels.addAll( rowsOfType( state, GMirrorAction.Type.RMDIR ) );
        dels.sort( null );   // back to plan order (child-before-parent)

        for( int i : mkdirs ) runSerial( spec, state, progress, i );

        if( state.maxInFlight() <= 1 || copies.size() <= 1 )
            for( int i : copies ) runSerial( spec, state, progress, i );
        else
            runCopiesInParallel( spec, state, progress, copies );

        for( int i : dels ) runSerial( spec, state, progress, i );

        int nCopies = 0, nDeletes = 0;
        long bytes = 0;
        for( int i = 0; i < state.size(); i++ )
        {
            if( state.status( i ) != GMirrorRunState.Status.DONE ) continue;
            switch( state.action( i ).type )
            {
                case COPY:   nCopies++; bytes += state.action( i ).bytes; break;
                case DELETE:
                case RMDIR:  nDeletes++; break;
                default: break;
            }
        }
        long ms = System.currentTimeMillis() - startMs;
        GAuditLog.mirror( connLabel( spec.replicaConn ), nCopies, nDeletes, bytes, ms );
        return new GMirrorResult( nCopies, nDeletes, bytes, ms, state.skippedCount(), state.skips() );
    }

    private static List<Integer> rowsOfType( GMirrorRunState state, GMirrorAction.Type t )
    {
        List<Integer> out = new ArrayList<>();
        for( int i = 0; i < state.size(); i++ ) if( state.action( i ).type == t ) out.add( i );
        return out;
    }

    /** One action on the calling thread with the primary connection; a cancel (interrupt) aborts the run. */
    private void runSerial( GMirrorSpec spec, GMirrorRunState state, GJobPlugin progress, int row ) throws GConnectionException
    {
        // Cancellation: GJobExecuter.stop() interrupts our thread; stop between actions.
        if( Thread.currentThread().isInterrupted() ) throw cancelled( state );
        try
        {
            runAction( spec, remoteSide( spec ), state, progress, row );
        }
        catch( GConnectionException e )
        {
            // A cancellation surfaces as an exception on the interrupted thread — let it abort.
            if( Thread.currentThread().isInterrupted() ) throw cancelled( state );
            throw e;
        }
    }

    /**
     * Apply row {@code row} with {@code remote} as the remote-side connection: DONE on success, SKIPPED
     * with a short reason on a per-action failure (the confirmed plan can go stale — a file vanished
     * since preview — so the run continues; §"What runs"). Rethrows when the thread was interrupted
     * so a cancel is never recorded as a skip.
     */
    private void runAction( GMirrorSpec spec, GConnection remote, GMirrorRunState state, GJobPlugin progress, int row )
        throws GConnectionException
    {
        GMirrorAction a = state.action( row );
        state.markRunning( row );
        try
        {
            switch( a.type )
            {
                case MKDIR:
                    spec.replicaConn.makeFolderAtPath( replicaPath( spec, a.relPath ) );
                    break;
                case COPY:
                    copy( spec, remote, a, state, row );
                    break;
                case DELETE:
                case RMDIR:
                    spec.replicaConn.delete( a.replica.item );
                    GAuditLog.action( "mirror-delete", connLabel( spec.replicaConn ), a.replica.item.getCompletePath() );
                    break;
                default:
                    break;
            }
            state.markDone( row );
        }
        catch( GConnectionException e )
        {
            if( Thread.currentThread().isInterrupted() ) throw e;
            GAuditLog.action( "mirror-skip", connLabel( spec.replicaConn ), a.relPath + " : " + e.getMessage() );
            state.markSkipped( row, shortReason( e ) );
        }
        if( progress != null )
        {
            synchronized( progress )   // workers finish concurrently; keep the run total monotonic (plan §3.2)
            {
                progress.getJobStatus().incrementProcessedFilesBy( 1 );
                progress.updateTransferStats( state.bytesSoFar() );
            }
        }
    }

    private static GConnectionException cancelled( GMirrorRunState state )
    {
        state.markRemainingCancelled();
        return new GConnectionException( "Mirror cancelled", GConnectionException.GCE_NETWORK_ERROR );
    }

    /** The connection copies go through: the replica for an upload mirror, the master for a download mirror. */
    private static GConnection remoteSide( GMirrorSpec spec )
    {
        return spec.direction == GMirrorSpec.Direction.UPLOAD ? spec.replicaConn : spec.masterConn;
    }

    // ---- COPY phase: bounded parallel workers over a small pool of cloned connections (plan §3.4) ----

    /**
     * Up to {@code state.maxInFlight()} platform threads named {@code mirror-copy-N} take copy rows in plan
     * order (so the table fills top-down), each borrowing a remote connection from {@link ConnectionPool}.
     * A cancel (interrupt on the calling thread) interrupts every worker, cancels the in-flight transfers,
     * joins the workers with a bound, marks the rest CANCELLED and rethrows. The clones are logged out here.
     */
    private void runCopiesInParallel( GMirrorSpec spec, GMirrorRunState state, GJobPlugin progress, List<Integer> copies )
        throws GConnectionException
    {
        int workers = Math.min( state.maxInFlight(), copies.size() );
        ConnectionPool pool = new ConnectionPool( remoteSide( spec ), workers );
        AtomicInteger next = new AtomicInteger();
        AtomicBoolean cancel = new AtomicBoolean();
        AtomicReference<GConnectionException> failure = new AtomicReference<>();
        Thread[] threads = new Thread[workers];
        try
        {
            for( int w = 0; w < workers; w++ )
            {
                threads[w] = Thread.ofPlatform().name( "mirror-copy-" + w ).daemon( true ).unstarted( () ->
                {
                    while( !cancel.get() && !Thread.currentThread().isInterrupted() )
                    {
                        int n = next.getAndIncrement();
                        if( n >= copies.size() ) return;
                        int row = copies.get( n );
                        GConnection conn = null;
                        try
                        {
                            conn = pool.borrow();
                            if( cancel.get() || Thread.currentThread().isInterrupted() ) return;
                            runAction( spec, conn, state, progress, row );
                        }
                        catch( InterruptedException e )
                        {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        catch( GConnectionException e )
                        {
                            if( cancel.get() || Thread.currentThread().isInterrupted() ) return;
                            failure.compareAndSet( null, e );   // a hard failure (not a per-row skip) — surface it
                            cancel.set( true );
                            return;
                        }
                        finally
                        {
                            if( conn != null ) pool.release( conn );
                        }
                    }
                } );
                threads[w].start();
            }
            for( Thread t : threads ) t.join();
        }
        catch( InterruptedException e )
        {
            // GJobExecuter.stop(): stop the workers, abort their transfers, wait briefly, then restore the interrupt.
            cancel.set( true );
            for( Thread t : threads ) if( t != null ) t.interrupt();
            pool.cancelTransfers();
            for( Thread t : threads )
                if( t != null ) try { t.join( 5_000 ); } catch( InterruptedException ignore ) {}
            Thread.currentThread().interrupt();
            throw cancelled( state );
        }
        finally
        {
            pool.close();
        }
        if( failure.get() != null ) { state.markRemainingCancelled(); throw failure.get(); }
    }

    /**
     * The primary remote connection plus up to {@code max − 1} clones, created lazily only when a worker
     * would otherwise wait (a clone costs a handshake). If a clone's login fails (server session cap,
     * auth throttle) the pool stops growing and the workers share what exists — one {@code mirror-pool}
     * audit line, no failure.
     */
    private static final class ConnectionPool
    {
        private final GConnection primary;
        private final int max;
        private final Deque<GConnection> idle = new ArrayDeque<>();
        private final List<GConnection> clones = new ArrayList<>();
        private int     reserved;        // clones existing or being logged in
        private boolean growthFailed, closed;

        ConnectionPool( GConnection primary, int max )
        {
            this.primary = primary;
            this.max = Math.max( 1, max );
            idle.push( primary );
        }

        GConnection borrow() throws InterruptedException
        {
            boolean grow;
            synchronized( this )
            {
                while( true )
                {
                    if( closed ) throw new InterruptedException( "pool closed" );
                    if( !idle.isEmpty() ) return idle.pop();
                    grow = !growthFailed && 1 + reserved < max;
                    if( grow ) { reserved++; break; }
                    wait();
                }
            }
            // Log the clone in outside the lock: a handshake takes hundreds of ms and must not stall
            // the workers releasing connections meanwhile.
            GConnection c = null;
            try
            {
                c = new GConnection( new GBookmark( primary.getBookmark() ) );
                c.login();
            }
            catch( GConnectionException e )
            {
                if( c != null ) logoutQuietly( c );   // a failed login can still hold an open transport
                synchronized( this )
                {
                    reserved--;
                    growthFailed = true;
                    GAuditLog.action( "mirror-pool", connLabel( primary ),
                        "extra connection refused; continuing with " + ( 1 + clones.size() ) + " : " + e.getMessage() );
                    notifyAll();
                }
                return borrow();
            }
            synchronized( this )
            {
                if( !closed ) { clones.add( c ); return c; }
            }
            logoutQuietly( c );   // the run ended while we were logging in
            throw new InterruptedException( "pool closed" );
        }

        synchronized void release( GConnection c )
        {
            idle.push( c );
            notifyAll();
        }

        void cancelTransfers()
        {
            List<GConnection> all = new ArrayList<>();
            synchronized( this ) { all.add( primary ); all.addAll( clones ); }
            for( GConnection c : all ) try { c.cancelTransfer(); } catch( Exception ignore ) {}
        }

        /** Log the clones out (never the primary — its owner does that), with the interrupt cleared meanwhile. */
        void close()
        {
            List<GConnection> all;
            synchronized( this ) { closed = true; all = new ArrayList<>( clones ); clones.clear(); notifyAll(); }
            for( GConnection c : all ) logoutQuietly( c );
        }

        private static void logoutQuietly( GConnection c )
        {
            boolean wasInterrupted = Thread.interrupted();
            try { c.logout(); } catch( Exception ignore ) {}
            if( wasInterrupted ) Thread.currentThread().interrupt();
        }
    }

    /** A few-word reason for a skipped row: the Review/Running tables and the report show it; the full message goes to the audit log. */
    static String shortReason( GConnectionException e )
    {
        String m = e.getMessage() != null ? e.getMessage() : "";
        String lower = m.toLowerCase();
        if( lower.contains( "permission" ) || lower.contains( "denied" ) || lower.contains( "access" ) ) return "permission denied";
        if( lower.contains( "no such file" ) || lower.contains( "not found" ) || lower.contains( "does not exist" ) || lower.contains( "nosuchfile" ) ) return "not found";
        if( lower.contains( "cancel" ) ) return "cancelled";
        String first = m.split( "\\r?\\n", 2 )[0].trim();
        if( first.isEmpty() ) return "failed";
        return first.length() > 60 ? first.substring( 0, 57 ) + "..." : first;
    }

    // ---- deletion safety (design §5), enforced before any write ----

    private void enforceDeletionGuards( GMirrorPlan plan, GMirrorSpec spec )
    {
        if( plan.deleteCount() == 0 ) return;

        // §5.4 Blast-radius: refuse a large delete unless the user has confirmed it.
        if( !spec.confirmedLargeDelete && plan.blastRadiusFraction() > spec.deleteBlastRadiusFraction )
            throw new GMirrorSafetyException( "Mirror would delete " + plan.deleteCount() + " of "
                + plan.replicaEntryCount() + " replica items (" + Math.round( plan.blastRadiusFraction() * 100 )
                + "%) — over the " + Math.round( spec.deleteBlastRadiusFraction * 100 )
                + "% safety threshold. Confirm to proceed." );

        // §5.3 Containment: every delete target must resolve inside the replica root.
        for( GMirrorAction a : plan.actions() )
        {
            if( a.type != GMirrorAction.Type.DELETE && a.type != GMirrorAction.Type.RMDIR ) continue;
            String target = a.replica.item.getCompletePath();
            if( !isContained( spec, target ) || a.relPath.contains( ".." ) )
                throw new GMirrorSafetyException( "Refusing to delete outside the mirror root: " + target );
        }
    }

    private static boolean isContained( GMirrorSpec spec, String targetPath )
    {
        String sep  = spec.replicaConn.getSeparator();
        String root = spec.replicaRoot.getCompletePath();
        String rootWithSep = root.endsWith( sep ) ? root : root + sep;
        return targetPath != null && ( targetPath.equals( root ) || targetPath.startsWith( rootWithSep ) );
    }

    private static String connLabel( GConnection c )
    {
        if( c != null && c.getBookmark() != null && c.getBookmark().getHostname() != null )
            return c.getBookmark().getHostname();
        return "";
    }

    private void copy( GMirrorSpec spec, GConnection remote, GMirrorAction a, GMirrorRunState state, int row ) throws GConnectionException
    {
        // per-copy progress: this row's cumulative bytes, never the job-level counter (plan §3.2)
        GTransferProgressListener onBytes = n -> state.setBytes( row, n );

        if( spec.direction == GMirrorSpec.Direction.UPLOAD )
        {
            // local master file -> remote replica path
            File local = new File( a.master.item.getCompletePath() );
            String remotePath = replicaPath( spec, a.relPath );
            try( FileInputStream in = new FileInputStream( local ) )
            {
                // open the transfer channel first — upload()/download() assume it is ready (the
                // job plugins call this before every transfer; the SFTP/FTP clients are lazily
                // opened here and reused). flag 0 = binary, the default transfer mode.
                remote.prepareToTransferFile( a.master.item, 0 );
                remote.upload( remotePath, in, local, onBytes );
            }
            catch( IOException e )
            {
                throw new GConnectionException( "Mirror upload failed: " + a.relPath + " : " + e.getMessage(),
                    GConnectionException.GCE_NETWORK_ERROR );
            }
            // Stamp the source mtime onto the uploaded file so size+mtime detection stays idempotent.
            preserveRemoteMtime( remote, a, remotePath );
        }
        else   // DOWNLOAD: remote master path -> local replica file
        {
            File local = new File( replicaPath( spec, a.relPath ) );
            try( FileOutputStream out = new FileOutputStream( local ) )
            {
                remote.prepareToTransferFile( a.master.item, 0 );
                remote.download( a.master.item.getCompletePath(), out, local, onBytes );
            }
            catch( IOException e )
            {
                throw new GConnectionException( "Mirror download failed: " + a.relPath + " : " + e.getMessage(),
                    GConnectionException.GCE_NETWORK_ERROR );
            }
            // The local file's mtime is the write time; stamp it back to the source so re-scans match.
            if( a.master.mtimeMillis > 0 ) local.setLastModified( a.master.mtimeMillis );
        }
        throttle();
    }

    private void throttle() throws GConnectionException
    {
        if( copyDelayMs <= 0 ) return;
        try { Thread.sleep( copyDelayMs ); }
        catch( InterruptedException e )
        {
            Thread.currentThread().interrupt();
            throw new GConnectionException( "Mirror cancelled", GConnectionException.GCE_NETWORK_ERROR );
        }
    }

    /**
     * Best-effort: set the uploaded file's mtime to the master's. Not all SFTP servers allow SETSTAT (and
     * FTP/S3 no-op it), so a failure is logged and the run continues — the only cost is that this file may
     * re-appear in the next plan rather than being recognised as unchanged.
     */
    private void preserveRemoteMtime( GConnection replica, GMirrorAction a, String remotePath )
    {
        if( a.master.mtimeMillis <= 0 ) return;
        try
        {
            replica.setModifiedTime( remotePath, a.master.mtimeMillis );
        }
        catch( GConnectionException e )
        {
            GAuditLog.action( "mirror-mtime-skip", connLabel( replica ), a.relPath + " : " + e.getMessage() );
        }
    }

    /** Absolute path of {@code relPath} on the replica, using the replica connection's separator. */
    private static String replicaPath( GMirrorSpec spec, String relPath )
    {
        String sep  = spec.replicaConn.getSeparator();
        String root = spec.replicaRoot.getCompletePath();
        String rel  = "/".equals( sep ) ? relPath : relPath.replace( "/", sep );
        String base = root.endsWith( sep ) ? root.substring( 0, root.length() - sep.length() ) : root;
        return base + sep + rel;
    }

    // ---- diff: pure (no I/O) — the unit-tested core ----

    /**
     * Compare two scanned trees into an ordered plan. Pure: depends only on the entry metadata
     * and {@code spec} (detector, deleteExtras, clock offset), so it is unit-testable without
     * any live connection.
     */
    public GMirrorPlan diff( Map<String, GMirrorEntry> master,
                             Map<String, GMirrorEntry> replica,
                             GMirrorSpec spec )
    {
        return diff( master, replica, spec, System.currentTimeMillis() );
    }

    /** Same as {@link #diff(Map, Map, GMirrorSpec)} but with an explicit "now" for the modified-within window (testable). */
    GMirrorPlan diff( Map<String, GMirrorEntry> master,
                      Map<String, GMirrorEntry> replica,
                      GMirrorSpec spec,
                      long nowMillis )
    {
        // Empty-master refusal (§5.5): never read "couldn't list the master" as "delete the
        // whole replica". A genuinely-empty master with deletes enabled is a hard stop.
        if( spec.deleteExtras && master.isEmpty() && !replica.isEmpty() )
            throw new GMirrorSafetyException( "Master scan returned no entries; refusing to delete the entire replica ("
                + replica.size() + " items). Check the connection and path, then retry." );

        List<GMirrorAction> creates = new ArrayList<>();   // MKDIR/COPY — parent-before-child
        List<GMirrorAction> deletes = new ArrayList<>();   // DELETE/RMDIR — child-before-parent
        List<GMirrorAction> equals  = new ArrayList<>();   // SKIP — shown for transparency

        // Master-driven: every master node becomes a copy/mkdir (new/changed) or a skip (equal).
        List<String> masterKeys = new ArrayList<>( master.keySet() );
        Collections.sort( masterKeys );                    // ascending → parents before children
        for( String rel : masterKeys )
        {
            GMirrorEntry m = master.get( rel );
            GMirrorEntry r = replica.get( rel );

            if( r == null )
            {
                if( m.isDir )
                    creates.add( new GMirrorAction( rel, GMirrorAction.Type.MKDIR, GMirrorAction.Reason.NEW, m, null, 0 ) );
                else if( withinModifiedWindow( m, spec, nowMillis ) )
                    creates.add( new GMirrorAction( rel, GMirrorAction.Type.COPY,  GMirrorAction.Reason.NEW, m, null, m.size ) );
                else
                    equals.add( new GMirrorAction( rel, GMirrorAction.Type.SKIP,  GMirrorAction.Reason.EQUAL, m, null, 0 ) );  // outside the window → not copied (and not deleted)
            }
            else if( m.isDir && r.isDir )
            {
                equals.add( new GMirrorAction( rel, GMirrorAction.Type.SKIP, GMirrorAction.Reason.EQUAL, m, r, 0 ) );
            }
            else if( !m.isDir && !r.isDir )
            {
                if( spec.detector.isChanged( m, r, spec ) && withinModifiedWindow( m, spec, nowMillis ) )
                    creates.add( new GMirrorAction( rel, GMirrorAction.Type.COPY, GMirrorAction.Reason.CHANGED, m, r, m.size ) );
                else
                    equals.add( new GMirrorAction( rel, GMirrorAction.Type.SKIP, GMirrorAction.Reason.EQUAL, m, r, 0 ) );
            }
            else
            {
                // Type mismatch (file vs dir at the same path): replace — remove the replica
                // node first (if deletes are enabled), then create the master node.
                if( spec.deleteExtras )
                    deletes.add( r.isDir
                        ? new GMirrorAction( rel, GMirrorAction.Type.RMDIR,  GMirrorAction.Reason.CHANGED, m, r, 0 )
                        : new GMirrorAction( rel, GMirrorAction.Type.DELETE, GMirrorAction.Reason.CHANGED, m, r, 0 ) );
                creates.add( m.isDir
                    ? new GMirrorAction( rel, GMirrorAction.Type.MKDIR, GMirrorAction.Reason.CHANGED, m, null, 0 )
                    : new GMirrorAction( rel, GMirrorAction.Type.COPY,  GMirrorAction.Reason.CHANGED, m, null, m.size ) );
            }
        }

        // Replica-driven extras → deletes, only when enabled (otherwise this is an additive mirror).
        if( spec.deleteExtras )
        {
            List<String> replicaKeys = new ArrayList<>( replica.keySet() );
            replicaKeys.sort( Collections.reverseOrder() );   // descending → children before parents
            for( String rel : replicaKeys )
            {
                if( master.containsKey( rel ) ) continue;
                GMirrorEntry r = replica.get( rel );
                deletes.add( r.isDir
                    ? new GMirrorAction( rel, GMirrorAction.Type.RMDIR,  GMirrorAction.Reason.EXTRA, null, r, 0 )
                    : new GMirrorAction( rel, GMirrorAction.Type.DELETE, GMirrorAction.Reason.EXTRA, null, r, 0 ) );
            }
        }

        List<GMirrorAction> all = new ArrayList<>( creates.size() + deletes.size() + equals.size() );
        all.addAll( creates );   // parent-before-child creates
        all.addAll( deletes );   // child-before-parent deletes
        all.addAll( equals );    // unchanged, for the Review screen's transparency
        return new GMirrorPlan( all, replica.size() );
    }

    // ---- modified-within window + auto clock-offset (§4) ----

    /** A master FILE is eligible to copy only if its (offset-corrected) mtime is within the window. */
    private static boolean withinModifiedWindow( GMirrorEntry m, GMirrorSpec spec, long nowMillis )
    {
        if( spec.modifiedWithin == null ) return true;   // no window set
        if( m.mtimeMillis == 0 )          return true;   // unknown mtime → don't exclude
        long effective = m.mtimeMillis - spec.clockOffsetMillis;
        return effective >= nowMillis - spec.modifiedWithin.toMillis();
    }

    /**
     * Read-only estimate of local↔server clock skew: the median {@code master.mtime - replica.mtime}
     * over files present on both sides with equal size (≈ same content); 0 when there's no signal.
     * The size+mtime detector subtracts this so a timezone difference doesn't flag every file changed.
     */
    /** Minimum same-size file pairs before an auto clock-offset is trusted. */
    static final int MIN_OFFSET_SAMPLES = 3;

    static long measureClockOffset( Map<String, GMirrorEntry> master, Map<String, GMirrorEntry> replica )
    {
        List<Long> deltas = new ArrayList<>();
        for( Map.Entry<String, GMirrorEntry> e : master.entrySet() )
        {
            GMirrorEntry m = e.getValue();
            GMirrorEntry r = replica.get( e.getKey() );
            if( r == null || m.isDir || r.isDir ) continue;
            if( m.size != r.size ) continue;                       // size-equal ≈ same content
            if( m.mtimeMillis == 0 || r.mtimeMillis == 0 ) continue;
            deltas.add( m.mtimeMillis - r.mtimeMillis );
        }
        // Need a consistent multi-file signal — one matching file could be coincidence, and using its
        // delta would wrongly "explain away" a genuine single-file mtime difference as clock skew.
        if( deltas.size() < MIN_OFFSET_SAMPLES ) return 0;
        Collections.sort( deltas );
        return deltas.get( deltas.size() / 2 );                    // median (robust to outliers)
    }
}
