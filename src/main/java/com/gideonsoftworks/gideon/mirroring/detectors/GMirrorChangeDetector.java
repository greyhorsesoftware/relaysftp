package com.gideonsoftworks.gideon.mirroring.detectors;

import com.gideonsoftworks.gideon.mirroring.GMirrorEntry;
import com.gideonsoftworks.gideon.mirroring.GMirrorSpec;

/**
 * Decides whether a file present on both sides has <em>changed</em> (master differs from
 * replica) and so must be re-copied. One-way semantics: this answers "are they different",
 * not "which is newer". Directories are never passed here — only regular files.
 *
 * Pluggable so the engine can pick the strongest signal per protocol (size+mtime for
 * SFTP/local, size-only for FTP upload, ETag/checksum for S3). See MIRROR_ENGINE_DESIGN §4.
 */
public interface GMirrorChangeDetector
{
    /** @return true if {@code replica} must be overwritten from {@code master}. */
    boolean isChanged( GMirrorEntry master, GMirrorEntry replica, GMirrorSpec spec );

    /** Short human-readable name, e.g. "size+mtime" — shown in the Configure screen dropdown. */
    String name();
}
