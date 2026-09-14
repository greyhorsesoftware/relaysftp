package com.gideonsoftworks.gideon.mirroring.detectors;

import com.gideonsoftworks.gideon.mirroring.GMirrorEntry;
import com.gideonsoftworks.gideon.mirroring.GMirrorSpec;

/**
 * Default detector: a file is changed if its size differs, or its modification time differs
 * beyond a small tolerance after correcting for clock skew between the two hosts
 * ({@code spec.clockOffsetMillis}, subtracted from the master side). Used for SFTP (mtime is
 * preserved) and FTP download (local mtime set from MDTM). See design §4.
 *
 * When either side has no usable mtime (0), it falls back to size-only — never reports a
 * spurious change from a missing timestamp.
 */
public final class SizeMtimeChangeDetector implements GMirrorChangeDetector
{
    /** Absorbs second-precision listings and filesystem timestamp granularity. */
    public static final long TOLERANCE_MS = 2000;

    @Override
    public boolean isChanged( GMirrorEntry master, GMirrorEntry replica, GMirrorSpec spec )
    {
        if( master.size != replica.size ) return true;

        if( master.mtimeMillis == 0 || replica.mtimeMillis == 0 ) return false; // trust size only

        long masterAdjusted = master.mtimeMillis - spec.clockOffsetMillis;
        return Math.abs( masterAdjusted - replica.mtimeMillis ) > TOLERANCE_MS;
    }

    @Override
    public String name()
    {
        return "size+mtime";
    }
}
