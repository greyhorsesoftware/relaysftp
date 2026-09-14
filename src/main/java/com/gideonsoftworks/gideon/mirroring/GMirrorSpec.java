package com.gideonsoftworks.gideon.mirroring;

import com.gideonsoftworks.gcore.GConnection;
import com.gideonsoftworks.gcore.GFileItem;
import com.gideonsoftworks.gideon.mirroring.detectors.GMirrorChangeDetector;
import com.gideonsoftworks.gideon.mirroring.detectors.SizeMtimeChangeDetector;

/**
 * Configuration for one mirror run (design §7). One-way: {@code master} is the source of truth,
 * {@code replica} is brought into line with it. {@link Direction} records whether that means
 * uploading (local master) or downloading (remote master) so execute picks the right transfer.
 *
 * A plain config holder — built by the Configure screen (§11 Screen A), consumed by
 * {@code GMirrorEngine.scan}. Fields marked "deferred" are wired into the spec now so the UI
 * and engine signatures are stable, but are not yet honored by the v1 scan.
 */
public final class GMirrorSpec
{
    public enum Direction { UPLOAD, DOWNLOAD }

    // Which side is the source of truth, and which gets updated.
    public GConnection masterConn;
    public GFileItem   masterRoot;
    public GConnection replicaConn;
    public GFileItem   replicaRoot;

    public Direction   direction = Direction.UPLOAD;

    /** Remove replica items that aren't on the master (design §1, §5). Default off = additive. */
    public boolean deleteExtras = false;

    /** Blast-radius guard (§5.4): abort if deletes exceed this fraction of the replica… */
    public double  deleteBlastRadiusFraction = 0.5;
    /** …unless the user has explicitly confirmed the large delete (the UI sets this). */
    public boolean confirmedLargeDelete = false;

    /** Local↔remote clock skew, subtracted from master mtimes by the size+mtime detector (§4). */
    public long clockOffsetMillis = 0;
    /** When true, {@code scan()} measures {@link #clockOffsetMillis} automatically; false = use it as set. */
    public boolean clockOffsetAuto = true;

    /** How "changed" is decided for files present on both sides (§4). */
    public GMirrorChangeDetector detector = new SizeMtimeChangeDetector();

    /** Only copy master files modified within this window; null = no window (§4). Gates copies, not deletes. */
    public java.time.Duration modifiedWithin = null;
    /** Drop entries matching the user's transfer-filter rules from both sides (§10.1). */
    public boolean applyTransferFilters = false;

    // --- deferred (signature reserved; not yet honored by v1 scan) ---
    /** Follow symbolic links instead of skipping them (§10.2). */
    public boolean followSymlinks = false;
}
