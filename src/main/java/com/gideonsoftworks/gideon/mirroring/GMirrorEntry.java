package com.gideonsoftworks.gideon.mirroring;

import com.gideonsoftworks.gcore.GFileItem;

/**
 * One node in a scanned tree, normalized to a root-relative key so the two sides of a mirror
 * can be compared regardless of protocol. {@code relPath} is '/'-separated with no leading
 * slash (e.g. {@code "docs/readme.md"}); the root itself is not an entry.
 *
 * Created by {@code GMirrorEngine.scan}; consumed by the change detectors and the diff. The
 * underlying {@link GFileItem} is carried for the execute phase (it has the absolute path and
 * connection); the diff itself only uses the metadata fields, so it stays pure and testable.
 */
public final class GMirrorEntry
{
    public final String   relPath;
    public final boolean  isDir;
    public final long     size;          // bytes; 0 for directories
    public final long     mtimeMillis;   // last-modified epoch millis; 0 when unknown
    public final GFileItem item;         // source node (may be null in pure-diff unit tests)
    public final String   digest;        // listing-provided content digest (S3 ETag); else null

    public GMirrorEntry( String relPath, boolean isDir, long size, long mtimeMillis, GFileItem item )
    {
        this( relPath, isDir, size, mtimeMillis, item, null );
    }

    public GMirrorEntry( String relPath, boolean isDir, long size, long mtimeMillis, GFileItem item, String digest )
    {
        this.relPath     = relPath;
        this.isDir       = isDir;
        this.size        = size;
        this.mtimeMillis = mtimeMillis;
        this.item        = item;
        this.digest      = digest;
    }

    @Override
    public String toString()
    {
        return ( isDir ? "DIR  " : "FILE " ) + relPath + " (" + size + "b @" + mtimeMillis + ")";
    }
}
