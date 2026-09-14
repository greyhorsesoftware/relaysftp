package com.gideonsoftworks.gideon.mirroring;

/**
 * One planned operation in a {@link GMirrorPlan}. The diff produces these; the Review screen
 * (design §11 Screen C) renders them grouped by {@link Reason}; execute performs them.
 */
public final class GMirrorAction
{
    /** What execute will do. */
    public enum Type { COPY, MKDIR, DELETE, RMDIR, SKIP }

    /** Why — drives the Review-screen tabs (New / Changed / Unchanged / Delete). */
    public enum Reason { NEW, CHANGED, EXTRA, EQUAL }

    public final String       relPath;
    public final Type         type;
    public final Reason       reason;
    public final GMirrorEntry master;   // source node (null for pure deletes)
    public final GMirrorEntry replica;  // existing destination node (null for new items)
    public final long         bytes;    // bytes to transfer (0 for dir ops / deletes / skips)

    public GMirrorAction( String relPath, Type type, Reason reason,
                          GMirrorEntry master, GMirrorEntry replica, long bytes )
    {
        this.relPath = relPath;
        this.type    = type;
        this.reason  = reason;
        this.master  = master;
        this.replica = replica;
        this.bytes   = bytes;
    }

    @Override
    public String toString()
    {
        return type + "/" + reason + " " + relPath + ( bytes > 0 ? " (" + bytes + "b)" : "" );
    }
}
