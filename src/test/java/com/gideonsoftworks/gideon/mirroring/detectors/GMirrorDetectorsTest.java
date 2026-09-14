package com.gideonsoftworks.gideon.mirroring.detectors;

import com.gideonsoftworks.gcore.GFileItem;
import com.gideonsoftworks.gideon.mirroring.GMirrorEntry;
import com.gideonsoftworks.gideon.mirroring.GMirrorSpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests for the SizeOnly and S3-ETag detectors. */
class GMirrorDetectorsTest
{
    private static final String MD5_HELLO = "5d41402abc4b2a76b9719d911017c592"; // md5("hello")

    private final GMirrorSpec spec = new GMirrorSpec();

    private static GMirrorEntry plain( long size, long mtime )
    {
        return new GMirrorEntry( "f", false, size, mtime, null );
    }

    private static GMirrorEntry local( long size, long mtime, File f )
    {
        return new GMirrorEntry( "f", false, size, mtime, new GFileItem( f ), null );
    }

    private static GMirrorEntry s3( long size, long mtime, String etag )
    {
        return new GMirrorEntry( "f", false, size, mtime, null, etag );
    }

    private static File hello( Path tmp ) throws Exception
    {
        File f = tmp.resolve( "hello.txt" ).toFile();
        Files.writeString( f.toPath(), "hello" );
        return f;
    }

    // ---- SizeOnly ----

    @Test
    void sizeOnly_changesOnSizeOnly()
    {
        SizeOnlyChangeDetector d = new SizeOnlyChangeDetector();
        assertTrue(  d.isChanged( plain( 10, 1 ), plain( 20, 1 ), spec ) );
        assertFalse( d.isChanged( plain( 10, 1 ), plain( 10, 999_999 ), spec ), "mtime must be ignored" );
    }

    // ---- S3 ETag ----

    @Test
    void s3Etag_matchingMd5_isUnchanged( @TempDir Path tmp ) throws Exception
    {
        assertFalse( new S3EtagChangeDetector().isChanged( local( 5, 1, hello( tmp ) ), s3( 5, 9, MD5_HELLO ), spec ) );
    }

    @Test
    void s3Etag_differentMd5_isChanged( @TempDir Path tmp ) throws Exception
    {
        assertTrue( new S3EtagChangeDetector().isChanged(
            local( 5, 1, hello( tmp ) ), s3( 5, 9, "00000000000000000000000000000000" ), spec ) );
    }

    @Test
    void s3Etag_sizeDiffers_isChangedWithoutHashing( @TempDir Path tmp ) throws Exception
    {
        assertTrue( new S3EtagChangeDetector().isChanged(
            local( 5, 1, hello( tmp ) ), s3( 99, 9, MD5_HELLO ), spec ) );
    }

    @Test
    void s3Etag_multipartEtag_fallsBackToSizeMtime( @TempDir Path tmp ) throws Exception
    {
        S3EtagChangeDetector d = new S3EtagChangeDetector();
        File f = hello( tmp );
        String multipart = "abc123def4567890abc123def4567890-3"; // not a usable single-part MD5
        assertFalse( d.isChanged( local( 5, 1000, f ), s3( 5, 1000, multipart ), spec ), "same size+mtime → unchanged" );
        assertTrue(  d.isChanged( local( 5, 1000, f ), s3( 5, 500_000, multipart ), spec ), "mtime far apart → changed" );
    }
}
