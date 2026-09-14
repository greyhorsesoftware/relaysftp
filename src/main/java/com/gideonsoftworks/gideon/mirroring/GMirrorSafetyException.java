package com.gideonsoftworks.gideon.mirroring;

/**
 * A deletion-safety guard (design §5) refused to proceed — empty-master, blast-radius, or a
 * containment violation. Unchecked because it is a hard stop the caller surfaces to the user,
 * not a recoverable I/O condition.
 */
public final class GMirrorSafetyException extends RuntimeException
{
    public GMirrorSafetyException( String message )
    {
        super( message );
    }
}
