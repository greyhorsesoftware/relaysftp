package com.gideonsoftworks.gcore;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.UnknownHostException;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class GS3ErrorMapperTest {

    private S3Exception s3(int status, String code) {
        return (S3Exception) S3Exception.builder()
                .statusCode(status)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode(code).build())
                .message(code)
                .build();
    }

    private int type(Throwable t) {
        return GS3ErrorMapper.mapS3(t, "ctx").getType();
    }

    @Test
    void forbiddenAndAuthCodesAreLoginFailure() {
        assertEquals(GConnectionException.GCE_BAD_LOGIN, type(s3(403, "AccessDenied")));
        assertEquals(GConnectionException.GCE_BAD_LOGIN, type(s3(403, "InvalidAccessKeyId")));
        assertEquals(GConnectionException.GCE_BAD_LOGIN, type(s3(403, "SignatureDoesNotMatch")));
        assertEquals(GConnectionException.GCE_BAD_LOGIN, type(s3(401, "Unauthorized")));
        assertEquals(GConnectionException.GCE_BAD_LOGIN, type(s3(400, "ExpiredToken")));
    }

    @Test
    void notFoundIsBadFolder() {
        assertEquals(GConnectionException.GCE_BAD_FOLDER, type(s3(404, "NoSuchKey")));
        assertEquals(GConnectionException.GCE_BAD_FOLDER, type(s3(404, "NoSuchBucket")));
    }

    @Test
    void serverAndThrottleAreNetwork() {
        assertEquals(GConnectionException.GCE_NETWORK_ERROR, type(s3(503, "SlowDown")));
        assertEquals(GConnectionException.GCE_NETWORK_ERROR, type(s3(500, "InternalError")));
        assertEquals(GConnectionException.GCE_NETWORK_ERROR, type(s3(400, "RequestTimeout")));
    }

    @Test
    void clientExceptionWithNoResponseIsNetwork() {
        SdkClientException e = SdkClientException.builder()
                .message("Unable to execute HTTP request")
                .cause(new UnknownHostException("no such host"))
                .build();
        assertEquals(GConnectionException.GCE_NETWORK_ERROR, type(e));
    }

    @Test
    void unknownStatusFallsBackToProtocolError() {
        assertEquals(GConnectionException.GCE_PROTOCOL_ERROR, type(s3(409, "Conflict")));
    }

    @Test
    void unwrapsCompletionException() {
        // TransferManager surfaces async failures wrapped in CompletionException.
        assertEquals(GConnectionException.GCE_BAD_LOGIN,
                type(new CompletionException(s3(403, "AccessDenied"))));
    }

    @Test
    void messageNamesCodeAndStatus() {
        GConnectionException e = GS3ErrorMapper.mapS3(s3(403, "AccessDenied"), "list");
        assertTrue(e.getMessage().contains("AccessDenied"), e.getMessage());
        assertTrue(e.getMessage().contains("403"), e.getMessage());
        assertTrue(e.getMessage().contains("list"), e.getMessage());
    }
}
