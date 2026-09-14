package com.gideonsoftworks.gcore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class GRedactTest {

    private static final String MASK = "********";

    // ---- secrets that MUST be masked ----

    static Stream<Arguments> redactedCases() {
        return Stream.of(
            // FTP PASS command (preserves original-case keyword, drops the secret)
            Arguments.of("PASS hunter2", "PASS " + MASK),
            Arguments.of("pass hunter2", "pass " + MASK),
            Arguments.of("   PASS s3cr3t", "   PASS " + MASK),

            // AWS access key ids (permanent + temporary) anywhere in the text
            Arguments.of("AKIAIOSFODNN7EXAMPLE", MASK),
            Arguments.of("ASIAIOSFODNN7EXAMPLE", MASK),
            Arguments.of("The AWS Access Key Id AKIAIOSFODNN7EXAMPLE does not exist",
                         "The AWS Access Key Id " + MASK + " does not exist"),

            // labeled single-token secrets
            Arguments.of("secretKey=wJalrXUtnFEMIK7MDENGbPxRfiCYEXAMPLEKEY", "secretKey=" + MASK),
            Arguments.of("password=hunter2", "password=" + MASK),
            Arguments.of("aws_secret_access_key=abc123def456", "aws_secret_access_key=" + MASK),
            Arguments.of("session_token: AQoDYXdzEXAMPLEtoken", "session_token: " + MASK),
            Arguments.of("pwd=\"quotedsecret\"", "pwd=\"" + MASK + "\""),

            // Authorization / session-token headers — masked to end of value
            Arguments.of("Authorization: AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/sig",
                         "Authorization: " + MASK),
            Arguments.of("x-amz-security-token: AQoDYXdzEXAMPLElongtoken",
                         "x-amz-security-token: " + MASK),

            // Bearer + JWT
            Arguments.of("Bearer abc123opaquetoken", "Bearer " + MASK),
            Arguments.of("id_token eyJhbGciOi.eyJzdWIiOj.SflKxwRJSMeKKF",
                         "id_token " + MASK)
        );
    }

    @ParameterizedTest(name = "[{index}] redacts: {0}")
    @MethodSource("redactedCases")
    void masksSecrets(String input, String expected) {
        assertEquals(expected, GRedact.apply(input));
    }

    // ---- diagnostic data that MUST survive untouched ----

    static Stream<Arguments> preservedCases() {
        return Stream.of(
            Arguments.of("USER bob"),
            Arguments.of("the secret is safe"),                 // prose, no '=' / ':' separator
            Arguments.of("GET /bucket/reports/2026/q2.pdf"),
            Arguments.of("host=s3.amazonaws.com region=us-east-1"),
            Arguments.of("ETag: d41d8cd98f00b204e9800998ecf8427e"),
            // SHA-256 checksum (64 hex) must not be mistaken for a secret
            Arguments.of("checksum=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
            Arguments.of("S3 listing failed: NoSuchBucket (HTTP 404)")
        );
    }

    @ParameterizedTest(name = "[{index}] preserves: {0}")
    @MethodSource("preservedCases")
    void keepsDiagnosticData(String input) {
        assertEquals(input, GRedact.apply(input));
    }

    // ---- null / empty safety ----

    @Test
    void nullAndEmptyPassThrough() {
        assertNull(GRedact.apply(null));
        assertEquals("", GRedact.apply(""));
    }

    @Test
    void maskAppearsAndSecretDoesNot() {
        String out = GRedact.apply("connecting with secretKey=TOPSECRETvalue12345");
        assertTrue(out.contains(MASK), "expected mask in output");
        assertFalse(out.contains("TOPSECRETvalue12345"), "secret leaked into output");
    }
}
