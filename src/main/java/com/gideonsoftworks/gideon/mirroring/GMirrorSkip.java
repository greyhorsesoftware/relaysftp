package com.gideonsoftworks.gideon.mirroring;

/** One action the engine could not apply and skipped: which, what kind, and a short reason. */
public record GMirrorSkip( String relPath, GMirrorAction.Type type, String reason )
{
    @Override public String toString() { return type + " " + relPath + " — " + reason; }
}
