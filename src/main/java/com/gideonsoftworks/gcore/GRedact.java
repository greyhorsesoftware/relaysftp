//
//  GRedact.java
//  Gideon
//
//  Central redaction for all log output (in-app GLog and the persistent audit log).
//  Strips credential material before any message is stored or written to disk, so a
//  log the user forwards to support never carries secrets.
//

package com.gideonsoftworks.gcore;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative by design: redacts recognizable credential shapes (FTP {@code PASS},
 * AWS access-key ids, labeled secret/password/token values, Authorization headers,
 * JWTs) but deliberately does <b>not</b> blanket-redact arbitrary long strings, so
 * diagnostic data (hostnames, bucket/object keys, ETags/checksums, error codes)
 * survives for support to read.
 */
public final class GRedact
{

private static final String MASK = "********";

// FTP login command: whole line "PASS <secret>". Preserves the historical
// GLogMessage behavior (group 1 keeps the original-case keyword).
private static final Pattern PASS =
    Pattern.compile("(?i)^(\\s*PASS)\\s+\\S.*$");

// Authorization / session-token header: mask the entire value to end of line.
private static final Pattern AUTH_HEADER =
    Pattern.compile("(?i)\\b(authorization|x-amz-security-token)(\\s*[=:]\\s*)(.+)$");

// Bearer token scheme: "Bearer <token>".
private static final Pattern BEARER =
    Pattern.compile("(?i)\\bBearer\\s+\\S+");

// Labeled single-token secret: <label> <=|:> <value>. Requires an explicit
// '=' or ':' separator so prose ("the secret is safe") is not touched.
private static final Pattern LABELED_SECRET =
    Pattern.compile(
        "(?i)\\b(password|passwd|pwd|secret(?:[_-]?access)?(?:[_-]?key)?|" +
        "aws_secret_access_key|access[_-]?key[_-]?id|session[_-]?token|" +
        "security[_-]?token)(\\s*[=:]\\s*)(\"?)([^\\s\"]+)(\"?)");

// AWS access key id: permanent (AKIA) or temporary/STS (ASIA) + 16 upper-alnum.
private static final Pattern AWS_ACCESS_KEY =
    Pattern.compile("\\b(?:AKIA|ASIA)[A-Z0-9]{16}\\b");

// JSON Web Token (e.g. OIDC id_token): three base64url segments.
private static final Pattern JWT =
    Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b");

private GRedact() { }

/**
 * Return a copy of {@code input} with credential material masked. Null-safe;
 * returns the argument unchanged when null or empty.
 */
public static String apply( final String input )
{
    if( input == null || input.isEmpty() )
    {
        return input;
    }

    // Whole-line FTP PASS command short-circuits — nothing else can be present.
    Matcher pass = PASS.matcher(input);
    if( pass.matches() )
    {
        return pass.group(1) + " " + MASK;
    }

    String out = input;

    out = AUTH_HEADER.matcher(out).replaceAll("$1$2" + MASK);
    out = BEARER.matcher(out).replaceAll("Bearer " + MASK);
    out = LABELED_SECRET.matcher(out).replaceAll("$1$2$3" + MASK + "$5");
    out = AWS_ACCESS_KEY.matcher(out).replaceAll(MASK);
    out = JWT.matcher(out).replaceAll(MASK);

    return out;
}

}
