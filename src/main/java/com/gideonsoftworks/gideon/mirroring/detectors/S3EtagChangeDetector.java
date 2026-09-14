package com.gideonsoftworks.gideon.mirroring.detectors;

import com.gideonsoftworks.gideon.mirroring.GMirrorEntry;
import com.gideonsoftworks.gideon.mirroring.GMirrorSpec;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * S3-compatible change detection via the object's ETag. For a single-part object the ETag is the
 * MD5 of its content, which is portable across providers (B2, Wasabi, R2, Spaces, MinIO). So:
 * compare the S3 side's single-part ETag against the other (local) side's computed MD5. When the
 * ETag is multipart ("…-N") or absent — i.e. no portable signal — fall back to size+mtime. See
 * design §4.
 *
 * A size mismatch is a definitive change (checked first, avoids hashing).
 */
public final class S3EtagChangeDetector implements GMirrorChangeDetector
{
    private final GMirrorChangeDetector fallback = new SizeMtimeChangeDetector();

    @Override
    public boolean isChanged( GMirrorEntry master, GMirrorEntry replica, GMirrorSpec spec )
    {
        if( master.size != replica.size ) return true;

        String me = usableMd5Etag( master.digest );
        String re = usableMd5Etag( replica.digest );

        if( me != null && re != null )                 // S3 ↔ S3, both single-part
            return !me.equalsIgnoreCase( re );

        if( me != null || re != null )                 // one S3 side, one local side
        {
            String s3Etag   = ( me != null ) ? me : re;
            GMirrorEntry local = ( me != null ) ? replica : master;
            String localMd5 = md5Hex( local );
            if( localMd5 != null )
                return !localMd5.equalsIgnoreCase( s3Etag );
        }

        // No usable single-part ETag (multipart or missing) → weaker but stable size+mtime.
        return fallback.isChanged( master, replica, spec );
    }

    /** @return the digest if it's a 32-hex MD5 (single-part ETag, no "-N" multipart suffix), else null. */
    private static String usableMd5Etag( String digest )
    {
        return ( digest != null && digest.matches( "[0-9a-fA-F]{32}" ) ) ? digest : null;
    }

    private static String md5Hex( GMirrorEntry e )
    {
        if( e.item == null || e.isDir ) return null;
        try {
            File f = new File( e.item.getCompletePath() );
            if( !f.isFile() ) return null;
            MessageDigest md5 = MessageDigest.getInstance( "MD5" );
            byte[] buf = new byte[8192];
            try( InputStream in = Files.newInputStream( f.toPath() ) ) {
                int n;
                while( ( n = in.read( buf ) ) != -1 )
                    md5.update( buf, 0, n );
            }
            return HexFormat.of().formatHex( md5.digest() );
        } catch( IOException | java.security.NoSuchAlgorithmException ex ) {
            return null;
        }
    }

    @Override
    public String name()
    {
        return "S3 ETag";
    }
}
