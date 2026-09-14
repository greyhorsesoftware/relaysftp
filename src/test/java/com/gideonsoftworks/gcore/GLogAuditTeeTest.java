package com.gideonsoftworks.gcore;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4 integration: a WARNING/ERROR logged through {@link GLog} is teed to the
 * persistent audit log, while routine chatter (client commands, info) is not.
 * Also covers Phase 3: the stored message text is redacted.
 */
class GLogAuditTeeTest {

    private static final Gson GSON = new Gson();

    @AfterEach
    void restoreSharedSink() {
        GAuditLog.setSharedForTesting(null);   // back to lazy production init
    }

    private List<String> auditLines(File dir) throws Exception {
        File f = new File(dir, "relaysftp-audit.0.jsonl");
        return f.exists() ? Files.readAllLines(f.toPath()) : List.of();
    }

    @Test
    void errorIsTeedAndChatterIsNot(@TempDir Path tmp) throws Exception {
        File dir = tmp.toFile();
        GAuditLog sink = new GAuditLog(dir, 2 * 1024 * 1024, 5);
        GAuditLog.setSharedForTesting(sink);

        GLog log = new GLog("s3:my-bucket");
        log.logMessage(new GLogMessage(GLogMessage.GLOG_CLIENT_COMMAND, "LIST /"));
        log.logMessage(new GLogMessage(GLogMessage.GLOG_INFORMATIONAL, "connected"));
        log.logMessage(new GLogMessage(GLogMessage.GLOG_ERROR, "S3 listing failed: AccessDenied"));
        sink.close();

        List<String> lines = auditLines(dir);
        assertEquals(1, lines.size(), "only the ERROR should be teed");

        JsonObject o = GSON.fromJson(lines.get(0), JsonObject.class);
        assertEquals("ERROR", o.get("cat").getAsString());
        assertEquals("s3:my-bucket", o.get("conn").getAsString());
        assertTrue(o.get("msg").getAsString().contains("AccessDenied"));
    }

    @Test
    void teedMessageIsRedacted(@TempDir Path tmp) throws Exception {
        File dir = tmp.toFile();
        GAuditLog sink = new GAuditLog(dir, 2 * 1024 * 1024, 5);
        GAuditLog.setSharedForTesting(sink);

        GLog log = new GLog("ftp:host");
        log.logMessage(new GLogMessage(GLogMessage.GLOG_ERROR,
            "auth error for secretKey=TOPSECRETvalue12345"));
        sink.close();

        String all = String.join("\n", auditLines(dir));
        assertFalse(all.contains("TOPSECRETvalue12345"), "secret leaked through the tee");
        assertTrue(all.contains("********"));
    }

    @Test
    void logMessageRedactsStoredText() {
        // Phase 3: GLogMessage itself stores redacted text.
        GLogMessage m = new GLogMessage(GLogMessage.GLOG_CLIENT_COMMAND, "PASS hunter2");
        assertEquals("PASS ********", m.getMessageText());
    }
}
