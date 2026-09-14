package com.gideonsoftworks.gideon.mirroring.detectors;

import com.gideonsoftworks.gcore.GBookmark;
import com.gideonsoftworks.gcore.GConnection;
import com.gideonsoftworks.gideon.mirroring.GMirrorSpec;

/**
 * Picks the default change detector for a spec, keyed on the <em>remote</em> side's protocol and
 * the direction (design §4 "Default selection"):
 *
 * <ul>
 *   <li>SFTP (either direction) → size+mtime (mtime is preserved)</li>
 *   <li>FTP <b>upload</b> → size-only (remote mtime not settable); FTP <b>download</b> → size+mtime</li>
 *   <li>S3 (any endpoint) → S3 ETag (single-part); AWS multipart exactness via the SHA-256
 *       checksum detector is a follow-up — those objects fall back to size+mtime meanwhile</li>
 *   <li>local↔local → size+mtime</li>
 * </ul>
 *
 * The UI calls this to seed {@code spec.detector}; it stays overridable.
 */
public final class GMirrorDetectorSelector
{
    private GMirrorDetectorSelector() {}

    public static GMirrorChangeDetector selectFor( GMirrorSpec spec )
    {
        // The "remote" side is the one being talked to over the network: the replica on upload,
        // the master on download. Its protocol decides which signal is reliable.
        GConnection remote = ( spec.direction == GMirrorSpec.Direction.UPLOAD ) ? spec.replicaConn : spec.masterConn;
        int proto = ( remote != null && remote.getBookmark() != null )
            ? remote.getBookmark().getProtocol()
            : GBookmark.G_FILE_PROTOCOL;
        return selectFor( proto, spec.direction );
    }

    /** The core decision, on the remote side's protocol + direction — no connection needed. */
    public static GMirrorChangeDetector selectFor( int remoteProtocol, GMirrorSpec.Direction direction )
    {
        switch( remoteProtocol )
        {
            case GBookmark.G_SFTP_PROTOCOL:
                return new SizeMtimeChangeDetector();

            case GBookmark.G_FTP_PROTOCOL:
            case GBookmark.G_FTPSSL_PROTOCOL:
                return ( direction == GMirrorSpec.Direction.UPLOAD )
                    ? new SizeOnlyChangeDetector()
                    : new SizeMtimeChangeDetector();

            case GBookmark.G_S3_PROTOCOL:
                return new S3EtagChangeDetector();

            case GBookmark.G_FILE_PROTOCOL:
            default:
                return new SizeMtimeChangeDetector();
        }
    }
}
