//
//  GS3ErrorMapper.java
//  Gideon
//
//  Maps AWS SDK / S3 exceptions to the right GConnectionException type so the UI
//  shows actionable errors (login vs network vs not-found) instead of collapsing
//  everything to a generic protocol error.
//

package com.gideonsoftworks.gcore;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

final class GS3ErrorMapper
{

private GS3ErrorMapper() { }

/**
 * Map {@code t} (an S3/SDK failure, possibly wrapped) to a GConnectionException
 * with an accurate {@code GCE_*} type and a message that names the S3 error code.
 */
static GConnectionException mapS3( Throwable t, String context )
{
    Throwable cause = unwrap(t);

    if( cause instanceof S3Exception )
    {
        S3Exception e = (S3Exception) cause;
        int status = e.statusCode();
        String code = ( e.awsErrorDetails() != null ) ? e.awsErrorDetails().errorCode() : null;

        String msg = "S3 " + context + ": "
            + ( code != null ? code : e.getMessage() ) + " (HTTP " + status + ")";

        int type;
        if( status == 403 || status == 401 || isAuthCode(code) )
        {
            type = GConnectionException.GCE_BAD_LOGIN;
        }
        else if( status == 404 || "NoSuchKey".equals(code) || "NoSuchBucket".equals(code) )
        {
            type = GConnectionException.GCE_BAD_FOLDER;
        }
        else if( status >= 500 || "SlowDown".equals(code) || "RequestTimeout".equals(code) )
        {
            type = GConnectionException.GCE_NETWORK_ERROR;
        }
        else
        {
            type = GConnectionException.GCE_PROTOCOL_ERROR;
        }
        return new GConnectionException(msg, type);
    }

    // Client-side failure with no HTTP response: DNS, connect/read timeout, SSL, offline.
    if( cause instanceof SdkClientException )
    {
        return new GConnectionException("S3 " + context + ": " + cause.getMessage(),
            GConnectionException.GCE_NETWORK_ERROR);
    }

    String m = ( cause.getMessage() != null ) ? cause.getMessage() : cause.toString();
    return new GConnectionException("S3 " + context + ": " + m,
        GConnectionException.GCE_PROTOCOL_ERROR);
}

private static Throwable unwrap( Throwable t )
{
    Throwable c = t;
    while( ( c instanceof CompletionException || c instanceof ExecutionException )
            && c.getCause() != null )
    {
        c = c.getCause();
    }
    return c;
}

private static boolean isAuthCode( String code )
{
    if( code == null ) return false;
    switch( code )
    {
        case "AccessDenied":
        case "InvalidAccessKeyId":
        case "SignatureDoesNotMatch":
        case "InvalidToken":
        case "ExpiredToken":
        case "TokenRefreshRequired":
        case "InvalidClientTokenId":
        case "InvalidSecurity":
            return true;
        default:
            return false;
    }
}

}
