package com.gideonsoftworks.gideon.mirroring;

import java.util.List;

/** Summary of one {@code GMirrorEngine.execute} run — drives the UI footer and the audit line. */
public final class GMirrorResult
{
    public final int  copies;
    public final int  deletes;
    public final long bytesCopied;
    public final long elapsedMs;
    /** Actions that failed at run time (e.g. a file vanished since the preview) and were skipped. */
    public final int  skipped;

    /** The skipped actions with their reasons, in plan order (empty when nothing was skipped). */
    public final List<GMirrorSkip> skips;

    public GMirrorResult( int copies, int deletes, long bytesCopied, long elapsedMs, int skipped )
    {
        this( copies, deletes, bytesCopied, elapsedMs, skipped, List.of() );
    }

    public GMirrorResult( int copies, int deletes, long bytesCopied, long elapsedMs, int skipped, List<GMirrorSkip> skips )
    {
        this.copies      = copies;
        this.deletes     = deletes;
        this.bytesCopied = bytesCopied;
        this.elapsedMs   = elapsedMs;
        this.skipped     = skipped;
        this.skips       = List.copyOf( skips );
    }

    @Override
    public String toString()
    {
        return "GMirrorResult[copies=" + copies + " deletes=" + deletes
            + " bytes=" + bytesCopied + " ms=" + elapsedMs + " skipped=" + skipped + "]";
    }
}
