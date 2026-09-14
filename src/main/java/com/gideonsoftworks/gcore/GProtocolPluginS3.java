//
//  GProtocolPluginS3.java
//  Gideon
//
//  S3 and S3-compatible (MinIO, Backblaze B2, DigitalOcean Spaces, Cloudflare R2) protocol plugin.
//

package com.gideonsoftworks.gcore;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.multipart.MultipartConfiguration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;
import software.amazon.awssdk.transfer.s3.model.FileDownload;
import software.amazon.awssdk.transfer.s3.model.FileUpload;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;
import software.amazon.awssdk.transfer.s3.progress.TransferListener;

import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

class GProtocolPluginS3 extends GProtocolPlugin
{

private S3Client          s3Client        = null;
private S3AsyncClient     s3AsyncClient   = null;
private S3TransferManager transferManager = null;
private FileDownload      currentDownload = null;
private FileUpload        currentUpload   = null;
private String            currentUploadKey = null;
private boolean           awsEndpoint     = false;
private String            bucketName      = null;
private String            currentPrefix   = "";

// Captured at connect so a presigner can be built the same way as the client (temporary links).
private AwsCredentialsProvider s3Credentials = null;
private String                 s3Region      = null;
private String                 s3Endpoint    = null;

public GProtocolPluginS3( final GConnection c ) throws GConnectionException
{
    super(c);
    setIsSecure(true);
}

public GFileItem gpp_exists( String fPath, String fName, boolean isDir ) throws GConnectionException
{
    try {
        String key = stripKey(fPath);

        if( isDir )
        {
            // Check for folder marker or any objects with this prefix
            String prefix = key.endsWith("/") ? key : key + "/";
            ListObjectsV2Response resp = s3Client.listObjectsV2(
                ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .maxKeys(1)
                    .build());
            if( resp.keyCount() > 0 )
            {
                GFileItem nFI = new GFileItem(null, fName, 0, null);
                nFI.setFileType(GFileItem.G_FOLDER);
                return nFI;
            }
            return null;
        } else {
            HeadObjectResponse head = s3Client.headObject(
                HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());
            Date mDate = head.lastModified() != null
                ? Date.from(head.lastModified())
                : null;
            GFileItem nFI = new GFileItem(null, fName, head.contentLength(), mDate);
            nFI.setFileType(GFileItem.G_FILE);
            return nFI;
        }
    } catch( NoSuchKeyException e ) {
        return null;                                  // genuinely absent
    } catch( S3Exception e ) {
        if( e.statusCode() == 404 ) return null;      // not found
        throw audit(GS3ErrorMapper.mapS3(e, "exists " + fName));  // 403/network must NOT read as not-found
    }
}

public void gpp_connect( GBookmark b ) throws GConnectionException
{
    if( b == null )
    {
        throw new NullPointerException();
    }

    bucketName = b.getHostname();

    String accessKey = b.getS3AccessKey();
    String secretKey = GBookmarkManager.getPassword(b);
    String region    = b.getS3Region();
    String endpoint  = b.getS3EndpointUrl();
    String token     = b.getS3SessionToken();

    if( accessKey == null || secretKey == null )
    {
        throw new GConnectionException("S3 credentials not configured",
            GConnectionException.GCE_BAD_LOGIN);
    }

    // Temporary credentials when a session token is present; otherwise static key+secret.
    AwsCredentialsProvider credentials =
        ( "session".equals(b.getS3AuthMethod()) && token != null && !token.isEmpty() )
            ? StaticCredentialsProvider.create(
                AwsSessionCredentials.create(accessKey, secretKey, token))
            : StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey));

    // keep these for presigned-URL generation (same creds/region/endpoint as the client)
    s3Credentials = credentials;
    s3Region      = region;
    s3Endpoint    = endpoint;

    boolean connected = false;
    try {
        // Route S3 through the system HTTP proxy if one is configured (no-op when there's none).
        String svcUrl = ( endpoint != null && !endpoint.isEmpty() )
            ? endpoint
            : "https://s3." + ( region != null ? region : "us-east-1" ) + ".amazonaws.com";

        S3ClientBuilder builder = S3Client.builder()
            .credentialsProvider(credentials)
            .region(Region.of(region != null ? region : "us-east-1"));

        var apacheProxyClient = GSystemProxy.apacheClientFor( svcUrl );
        if( apacheProxyClient != null )
        {
            builder.httpClientBuilder( apacheProxyClient );
            GAuditLog.proxy( bucketName, "HTTP-CONNECT", GSystemProxy.httpViaFor( svcUrl ), URI.create( svcUrl ).getHost() + ":443", "configured (system)" );
        }

        if( endpoint != null && !endpoint.isEmpty() )
        {
            builder.endpointOverride(URI.create(endpoint));
            builder.serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build());
        }

        s3Client = builder.build();

        // Verify connectivity by checking bucket exists
        s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());

        // Async multipart client + TransferManager for streaming uploads/downloads
        // (auto-switches to parallel multipart above the 100 MB threshold; pure-Java, no CRT).
        var asyncBuilder = S3AsyncClient.builder()
            .credentialsProvider(credentials)
            .region(Region.of(region != null ? region : "us-east-1"))
            .multipartEnabled(true)
            .multipartConfiguration(MultipartConfiguration.builder()
                .thresholdInBytes(100L * 1024 * 1024)
                .minimumPartSizeInBytes(8L * 1024 * 1024)
                .build());

        var nettyProxyClient = GSystemProxy.nettyClientFor( svcUrl );
        if( nettyProxyClient != null ) asyncBuilder.httpClientBuilder( nettyProxyClient );

        if( endpoint != null && !endpoint.isEmpty() )
        {
            asyncBuilder.endpointOverride(URI.create(endpoint));
            asyncBuilder.forcePathStyle(true);
        }

        s3AsyncClient = asyncBuilder.build();
        transferManager = S3TransferManager.builder().s3Client(s3AsyncClient).build();

        // Checksums are AWS-only — compatible providers may reject x-amz-checksum-*.
        awsEndpoint = ( endpoint == null || endpoint.isEmpty()
            || endpoint.toLowerCase().contains("amazonaws.com") );

        currentPrefix = "";
        setIsConnected(true);
        connected = true;

        this.logMessage(GLogMessage.GLOG_INFORMATIONAL,
            "Connected to S3 bucket: " + bucketName);

    } catch( S3Exception e ) {
        throw audit(GS3ErrorMapper.mapS3(e, "connect"));
    } catch( RuntimeException e ) {
        // SdkClientException (DNS/endpoint failure) etc. aren't S3Exceptions — map so the UI shows a clean error.
        throw audit(new GConnectionException("S3 connect failed: " + e.getMessage(),
            GConnectionException.GCE_NETWORK_ERROR));
    } finally {
        // #2: a failed connect must not leak the already-built clients (connection pools + Netty threads).
        if( !connected ) closeS3ClientsQuietly();
    }
}

/** Close any partially-built S3 clients without throwing (used on connect failure and disconnect). */
private void closeS3ClientsQuietly()
{
    try { if( transferManager != null ) transferManager.close(); } catch( Exception ignore ) {}
    try { if( s3AsyncClient   != null ) s3AsyncClient.close();   } catch( Exception ignore ) {}
    try { if( s3Client        != null ) s3Client.close();        } catch( Exception ignore ) {}
    transferManager = null;
    s3AsyncClient   = null;
    s3Client        = null;
}

public void gpp_disconnect( GConnection c ) throws GConnectionException
{
    if( c == null )
    {
        throw new NullPointerException();
    }
    if( transferManager != null )
    {
        transferManager.close();
        transferManager = null;
    }
    if( s3AsyncClient != null )
    {
        s3AsyncClient.close();
        s3AsyncClient = null;
    }
    if( s3Client != null )
    {
        s3Client.close();
        s3Client = null;
    }
    this.setIsConnected(false);
}

public GListParser gpp_parser_instance() throws GConnectionException
{
    return new GListParserS3();
}

public Object gpp_list( String p ) throws GConnectionException
{
    try {
        String prefix = p;
        if( prefix.startsWith("/") ) prefix = prefix.substring(1);
        if( !prefix.isEmpty() && !prefix.endsWith("/") ) prefix = prefix + "/";

        List<Object> items = new ArrayList<Object>();
        String continuationToken = null;

        do {
            ListObjectsV2Request.Builder reqBuilder = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .delimiter("/");

            if( continuationToken != null )
            {
                reqBuilder.continuationToken(continuationToken);
            }

            ListObjectsV2Response resp = s3Client.listObjectsV2(reqBuilder.build());

            // Folders (common prefixes)
            if( resp.commonPrefixes() != null )
            {
                for( CommonPrefix cp : resp.commonPrefixes() )
                {
                    items.add(cp);
                }
            }

            // Files (objects that are not folder markers)
            if( resp.contents() != null )
            {
                for( S3Object obj : resp.contents() )
                {
                    // Skip the prefix itself (folder marker)
                    if( obj.key().equals(prefix) ) continue;
                    items.add(obj);
                }
            }

            continuationToken = resp.isTruncated() ? resp.nextContinuationToken() : null;

        } while( continuationToken != null );

        return items.toArray();

    } catch( S3Exception e ) {
        throw audit(GS3ErrorMapper.mapS3(e, "list"));
    }
}

public String gpp_get_pwd() throws GConnectionException
{
    if( currentPrefix.isEmpty() ) return "/";
    return "/" + currentPrefix;
}

public void gpp_cwd( String p ) throws GConnectionException
{
    if( p.equals("/") || p.isEmpty() )
    {
        currentPrefix = "";
        return;
    }

    String target;
    if( p.startsWith("/") )
    {
        target = p.substring(1);
    } else {
        target = currentPrefix + p;
    }

    // Handle parent navigation
    if( target.endsWith("..") || target.endsWith("../") )
    {
        // Go up one level
        String base = currentPrefix;
        if( base.endsWith("/") ) base = base.substring(0, base.length() - 1);
        int lastSlash = base.lastIndexOf('/');
        currentPrefix = lastSlash >= 0 ? base.substring(0, lastSlash + 1) : "";
        return;
    }

    if( !target.endsWith("/") ) target = target + "/";
    currentPrefix = target;
}

// Normalize a path to an S3 key: strip ALL leading slashes. Callers (e.g. the
// rename/mkdir path builders) may pass "/", "//key", etc. — a single substring(1)
// would leave a stray leading slash and create a malformed key.
private static String stripKey( String path )
{
    if( path == null ) return null;
    int i = 0;
    while( i < path.length() && path.charAt(i) == '/' ) i++;
    return path.substring(i);
}

// Generate a presigned GET URL (a temporary, expiring download link) for an object.
public String gpp_presignGet( String fPath, java.time.Duration expiry ) throws GConnectionException
{
    String key = stripKey( fPath );
    try( S3Presigner presigner = buildPresigner() )
    {
        GetObjectRequest get = GetObjectRequest.builder().bucket( bucketName ).key( key ).build();
        GetObjectPresignRequest req = GetObjectPresignRequest.builder()
            .signatureDuration( expiry )
            .getObjectRequest( get )
            .build();
        return presigner.presignGetObject( req ).url().toString();
    }
    catch( Exception e )
    {
        throw new GConnectionException( "Could not create temporary link for " + key + " : " + e.getMessage(),
            GConnectionException.GCE_PROTOCOL_ERROR );
    }
}

// Build a presigner the same way as the S3 client (creds/region, endpoint + path-style for compatibles).
private S3Presigner buildPresigner()
{
    S3Presigner.Builder b = S3Presigner.builder()
        .credentialsProvider( s3Credentials )
        .region( Region.of( s3Region != null ? s3Region : "us-east-1" ) );
    if( s3Endpoint != null && !s3Endpoint.isEmpty() )
    {
        b.endpointOverride( URI.create( s3Endpoint ) );
        b.serviceConfiguration( S3Configuration.builder().pathStyleAccessEnabled( true ).build() );
    }
    return b.build();
}

// Log a mapped error to the connection log (which tees to the audit log) and
// return it for throwing.
private GConnectionException audit( GConnectionException e )
{
    logMessage(GLogMessage.GLOG_ERROR, e.getMessage());
    return e;
}

// SHA-256 of a file as lowercase hex, via the JDK. Best-effort: returns null on any
// failure — only used to stamp optional metadata, must never break an upload. The file
// is streamed in chunks; it is not read into memory.
private static String sha256Hex( File f )
{
    try( InputStream in = Files.newInputStream(f.toPath()) )
    {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[8192];
        int n;
        while( ( n = in.read(buf) ) != -1 )
            sha256.update(buf, 0, n);
        return HexFormat.of().formatHex(sha256.digest());
    }
    catch( Exception e )
    {
        return null;
    }
}

public void gpp_rename( String p, String n ) throws GConnectionException
{
    try {
        String srcKey = stripKey(p);
        String dstKey = stripKey(n);

        s3Client.copyObject(CopyObjectRequest.builder()
            .sourceBucket(bucketName)
            .sourceKey(srcKey)
            .destinationBucket(bucketName)
            .destinationKey(dstKey)
            .build());

        s3Client.deleteObject(DeleteObjectRequest.builder()
            .bucket(bucketName)
            .key(srcKey)
            .build());

    } catch( S3Exception e ) {
        throw audit(GS3ErrorMapper.mapS3(e, "rename"));
    }
}

public void gpp_move( String p, String n ) throws GConnectionException
{
    gpp_rename(p, n);
}

public void gpp_rm( String p, boolean isDir ) throws GConnectionException
{
    try {
        String key = stripKey(p);

        if( !isDir )
        {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());
        } else {
            // Delete all objects with this prefix
            String prefix = key.endsWith("/") ? key : key + "/";
            String continuationToken = null;

            do {
                ListObjectsV2Request.Builder reqBuilder = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix);

                if( continuationToken != null )
                {
                    reqBuilder.continuationToken(continuationToken);
                }

                ListObjectsV2Response resp = s3Client.listObjectsV2(reqBuilder.build());

                if( resp.contents() != null && !resp.contents().isEmpty() )
                {
                    List<ObjectIdentifier> toDelete = new ArrayList<ObjectIdentifier>();
                    for( S3Object obj : resp.contents() )
                    {
                        toDelete.add(ObjectIdentifier.builder().key(obj.key()).build());
                    }

                    s3Client.deleteObjects(DeleteObjectsRequest.builder()
                        .bucket(bucketName)
                        .delete(Delete.builder().objects(toDelete).build())
                        .build());
                }

                continuationToken = resp.isTruncated() ? resp.nextContinuationToken() : null;

            } while( continuationToken != null );
        }

    } catch( S3Exception e ) {
        throw audit(GS3ErrorMapper.mapS3(e, "delete"));
    }
}

public void gpp_chmod( final String p, final GPermissions pmO ) throws GConnectionException
{
    // S3 has no Unix permissions — no-op
}

public void gpp_mkdir( String dP ) throws GConnectionException
{
    try {
        String key = stripKey(dP);
        if( !key.endsWith("/") ) key = key + "/";

        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentLength(0L)
                .build(),
            RequestBody.empty());

    } catch( S3Exception e ) {
        throw audit(GS3ErrorMapper.mapS3(e, "mkdir"));
    }
}

public String gpp_send_command( String c ) throws GConnectionException
{
    return null;
}

public boolean gpp_needsCompletePathForRename()
{
    return true;
}

/**
*
*   Transfer API
*
*/

public void gpp_prepareToTransferFile( GFileItem f, int flag ) throws GConnectionException
{
    // No preparation needed for S3
}

public void gpp_upload( final String c, FileInputStream srcStream, File localFile, GTransferProgressListener l ) throws GConnectionException
{
    final String key = stripKey(c);

    // Stream straight from the local file (no RAM buffering → no OOM); TransferManager
    // auto-switches to parallel multipart above the 100 MB threshold. Release the job's
    // stream handle first so we are the only reader.
    try { srcStream.close(); } catch( IOException ignore ) {}

    // Write-ahead for a future content-checksum mirror detector: on compatible providers
    // (which lack AWS native checksums) stamp our own SHA-256 in user metadata so objects
    // are already comparable when that detector ships. AWS gets the native checksum below,
    // so we skip the redundant hash there. Best-effort — never fail the upload over it.
    final String contentSha256 = awsEndpoint ? null : sha256Hex(localFile);

    UploadFileRequest req = UploadFileRequest.builder()
        .putObjectRequest(b -> {
            b.bucket(bucketName).key(key);
            if( awsEndpoint ) b.checksumAlgorithm(ChecksumAlgorithm.SHA256);  // AWS-only (§ decision)
            else if( contentSha256 != null ) b.metadata(java.util.Map.of("content-sha256", contentSha256));
        })
        .source(localFile.toPath())
        .addTransferListener(progressListener(l))
        .build();

    currentUploadKey = key;
    try {
        // uploadFile() itself can throw synchronously (e.g. UncheckedIOException when the local
        // source vanished since preflight), so it belongs inside the guarded block too.
        FileUpload upload = transferManager.uploadFile(req);
        currentUpload = upload;
        upload.completionFuture().join();
    } catch( CompletionException e ) {
        // Any hard failure can strand an in-progress multipart upload (the SDK only cleans up
        // when the part failure reaches it over a live client). Best-effort abort, then rethrow (#14).
        abortOrphanMultipartUploads(key);
        throw audit(GS3ErrorMapper.mapS3(e, "upload"));
    } catch( CancellationException e ) {
        // gpp_cancelTransfer() cancelled the future: report it as a cancelled transfer, not as a
        // bare RuntimeException the job runner would log as an error (#13).
        abortOrphanMultipartUploads(key);
        throw new GConnectionException("Upload cancelled: " + key, GConnectionException.GCE_NETWORK_ERROR);
    } catch( RuntimeException e ) {
        // Start-up failure before any request completed: never let a raw runtime exception escape
        // the protocol layer; map it like every other failure.
        abortOrphanMultipartUploads(key);
        throw audit(new GConnectionException("Upload failed: " + key + " : " + e.getMessage(),
            GConnectionException.GCE_NETWORK_ERROR));
    } finally {
        currentUpload = null;
        currentUploadKey = null;
    }
}

public void gpp_download( final String c, FileOutputStream dstStream, File localFile, GTransferProgressListener l ) throws GConnectionException
{
    String key = stripKey(c);

    // TransferManager writes the file itself (parallel/ranged GET above the threshold),
    // so release the job's stream handle first to avoid two writers on the same file.
    try { dstStream.close(); } catch( IOException ignore ) {}

    DownloadFileRequest req = DownloadFileRequest.builder()
        .getObjectRequest(b -> b.bucket(bucketName).key(key))
        .destination(localFile.toPath())
        .addTransferListener(progressListener(l))
        .build();

    FileDownload download = transferManager.downloadFile(req);
    currentDownload = download;
    try {
        download.completionFuture().join();
    } catch( CompletionException e ) {
        throw audit(GS3ErrorMapper.mapS3(e, "download"));
    } catch( CancellationException e ) {
        throw new GConnectionException("Download cancelled: " + key, GConnectionException.GCE_NETWORK_ERROR);   // #13
    } finally {
        currentDownload = null;
    }
}

// Bridge TransferManager progress to the job's transfer stats.
private TransferListener progressListener( final GTransferProgressListener l )
{
    return new TransferListener() {
        public void bytesTransferred( Context.BytesTransferred context ) {
            if( l != null )
                l.updateTransferStats(context.progressSnapshot().transferredBytes());
        }
    };
}

public void gpp_cancelTransfer()
{
    FileUpload up = currentUpload;
    if( up != null )
    {
        up.completionFuture().cancel(true);
        // A hard cancel can leave an in-flight multipart upload — reconcile so we
        // never orphan parts (which would otherwise incur silent storage charges).
        abortOrphanMultipartUploads(currentUploadKey);
    }

    FileDownload dl = currentDownload;
    if( dl != null )
    {
        // Leave the partial local file in place to allow resume.
        dl.completionFuture().cancel(true);
    }
}

/** Abort any incomplete multipart uploads for {@code key} (no-op if key is null). */
void abortOrphanMultipartUploads( String key )
{
    if( key == null ) return;
    try {
        ListMultipartUploadsResponse resp = s3Client.listMultipartUploads(
            ListMultipartUploadsRequest.builder().bucket(bucketName).prefix(key).build());

        int cleaned = 0;
        if( resp.uploads() != null )
        {
            for( MultipartUpload mpu : resp.uploads() )
            {
                if( !key.equals(mpu.key()) ) continue;
                s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(bucketName).key(mpu.key()).uploadId(mpu.uploadId()).build());
                cleaned++;
            }
        }
        if( cleaned > 0 )
        {
            logMessage(GLogMessage.GLOG_INFORMATIONAL,
                "Aborted " + cleaned + " incomplete multipart upload(s) for " + key);
        }
    } catch( Exception e ) {
        // best-effort cleanup — don't fail the cancel on a cleanup error
    }
}

}
