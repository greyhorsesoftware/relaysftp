package com.gideonsoftworks.gideon.mirroring.detectors;

import com.gideonsoftworks.gideon.mirroring.GMirrorEntry;
import com.gideonsoftworks.gideon.mirroring.GMirrorSpec;

/**
 * Changed iff the size differs. Used for FTP <em>upload</em> mirrors: the remote mtime isn't
 * settable after an upload, so a mtime comparison would churn (every file looks "changed" on the
 * next run). Size-only is weaker — it misses same-size content edits — but it's stable. See
 * design §4.
 */
public final class SizeOnlyChangeDetector implements GMirrorChangeDetector
{
    @Override
    public boolean isChanged( GMirrorEntry master, GMirrorEntry replica, GMirrorSpec spec )
    {
        return master.size != replica.size;
    }

    @Override
    public String name()
    {
        return "size only";
    }
}
