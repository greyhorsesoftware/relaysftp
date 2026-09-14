package com.gideonsoftworks.gideon.core;

import com.gideonsoftworks.gcore.GBookmark;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Panel-logic checks for the S3 auth-method selector: default, session round-trip,
 * and provider-gating (temporary credentials only for AWS-capable providers).
 * Tagged {@code ui} since it constructs Swing components (display-dependent).
 */
@Tag("ui")
class GS3ConnectionPanelTest {

    private GBookmark s3Bookmark(String endpoint, String authMethod, String token) {
        GBookmark bm = new GBookmark("b", "bucket", "/", "AK", "SK", 0, GBookmark.G_S3_PROTOCOL);
        bm.setS3AccessKey("AK");
        bm.setS3SecretKey("SK");
        if (endpoint != null) bm.setS3EndpointUrl(endpoint);
        bm.setS3AuthMethod(authMethod);
        bm.setS3SessionToken(token);
        return bm;
    }

    private GBookmark blank() {
        return new GBookmark("b", "bucket", "/", "AK", "SK", 0, GBookmark.G_S3_PROTOCOL);
    }

    @Test
    void defaultsToBasic() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GS3ConnectionPanel panel = new GS3ConnectionPanel();
            GBookmark out = blank();
            panel.applyToBookmark(out);
            assertEquals("basic", out.getS3AuthMethod());
            assertNull(out.getS3SessionToken());
        });
    }

    @Test
    void sessionRoundTripsForAmazonS3() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GS3ConnectionPanel panel = new GS3ConnectionPanel();
            panel.populateFromBookmark(s3Bookmark(null, "session", "TOKEN123")); // no endpoint = Amazon S3
            GBookmark out = blank();
            panel.applyToBookmark(out);
            assertEquals("session", out.getS3AuthMethod());
            assertEquals("TOKEN123", out.getS3SessionToken());
        });
    }

    @Test
    void temporaryGatedOffForNonAwsProvider() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GS3ConnectionPanel panel = new GS3ConnectionPanel();
            // Wasabi endpoint → not AWS-capable; Temporary Credentials must not survive.
            panel.populateFromBookmark(
                s3Bookmark("https://s3.us-east-1.wasabisys.com", "session", "TOKEN123"));
            GBookmark out = blank();
            panel.applyToBookmark(out);
            assertEquals("basic", out.getS3AuthMethod(),
                "temporary credentials must be gated off for non-AWS providers");
            assertNull(out.getS3SessionToken());
        });
    }
}
