package com.gideonsoftworks.gideon.mirroring.detectors;

import com.gideonsoftworks.gcore.GBookmark;
import com.gideonsoftworks.gideon.mirroring.GMirrorSpec;

import org.junit.jupiter.api.Test;

import static com.gideonsoftworks.gideon.mirroring.GMirrorSpec.Direction.DOWNLOAD;
import static com.gideonsoftworks.gideon.mirroring.GMirrorSpec.Direction.UPLOAD;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies the default-detector selection by remote protocol + direction (design §4). */
class GMirrorDetectorSelectorTest
{
    private static GMirrorChangeDetector pick( int proto, GMirrorSpec.Direction dir )
    {
        return GMirrorDetectorSelector.selectFor( proto, dir );
    }

    @Test
    void sftp_usesSizeMtime()
    {
        assertInstanceOf( SizeMtimeChangeDetector.class, pick( GBookmark.G_SFTP_PROTOCOL, UPLOAD ) );
        assertInstanceOf( SizeMtimeChangeDetector.class, pick( GBookmark.G_SFTP_PROTOCOL, DOWNLOAD ) );
    }

    @Test
    void ftpUpload_usesSizeOnly_butDownloadUsesSizeMtime()
    {
        assertInstanceOf( SizeOnlyChangeDetector.class,  pick( GBookmark.G_FTP_PROTOCOL, UPLOAD ) );
        assertInstanceOf( SizeMtimeChangeDetector.class, pick( GBookmark.G_FTP_PROTOCOL, DOWNLOAD ) );
        assertInstanceOf( SizeOnlyChangeDetector.class,  pick( GBookmark.G_FTPSSL_PROTOCOL, UPLOAD ) );
    }

    @Test
    void s3_usesEtag()
    {
        assertInstanceOf( S3EtagChangeDetector.class, pick( GBookmark.G_S3_PROTOCOL, UPLOAD ) );
    }

    @Test
    void local_usesSizeMtime()
    {
        assertInstanceOf( SizeMtimeChangeDetector.class, pick( GBookmark.G_FILE_PROTOCOL, UPLOAD ) );
    }
}
