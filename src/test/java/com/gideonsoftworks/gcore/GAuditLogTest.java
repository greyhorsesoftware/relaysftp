package com.gideonsoftworks.gcore;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GAuditLogTest {

    private static final Gson GSON = new Gson();
    private static final int BIG = 2 * 1024 * 1024;

    private List<String> readCurrent(File dir) throws IOException {
        // FileHandler writes the newest data to generation 0.
        return Files.readAllLines(new File(dir, "relaysftp-audit.0.jsonl").toPath());
    }

    @Test
    void actionWritesOneValidJsonLine(@TempDir Path tmp) throws Exception {
        File dir = tmp.toFile();
        GAuditLog log = new GAuditLog(dir, BIG, 5);
        log.logAction("download", "s3:my-bucket", "reports/q2.pdf");
        log.close();

        List<String> lines = readCurrent(dir);
        assertEquals(1, lines.size());

        JsonObject o = GSON.fromJson(lines.get(0), JsonObject.class);
        assertEquals("ACTION", o.get("cat").getAsString());
        assertEquals("download", o.get("op").getAsString());
        assertEquals("s3:my-bucket", o.get("conn").getAsString());
        assertEquals("reports/q2.pdf", o.get("path").getAsString());

        String ts = o.get("ts").getAsString();
        assertTrue(ts.endsWith("Z"), "ts must be UTC: " + ts);
        assertDoesNotThrow(() -> Instant.parse(ts));
    }

    @Test
    void proxyWritesStructuredLine(@TempDir Path tmp) throws Exception {
        File dir = tmp.toFile();
        GAuditLog log = new GAuditLog(dir, BIG, 5);
        log.logProxy("sftp.example.com", "SOCKS", "proxy.local:1080", "sftp.example.com:22", "configured");
        log.close();

        JsonObject o = GSON.fromJson(readCurrent(dir).get(0), JsonObject.class);
        assertEquals("PROXY", o.get("cat").getAsString());
        assertEquals("sftp.example.com", o.get("conn").getAsString());
        assertEquals("SOCKS", o.get("type").getAsString());
        assertEquals("proxy.local:1080", o.get("via").getAsString());
        assertEquals("sftp.example.com:22", o.get("target").getAsString());
        assertEquals("configured", o.get("result").getAsString());
    }

    @Test
    void secretsAreRedactedBeforeWrite(@TempDir Path tmp) throws Exception {
        File dir = tmp.toFile();
        GAuditLog log = new GAuditLog(dir, BIG, 5);
        log.logError("s3:b", "connect secretKey=TOPSECRETvalue12345", "GCE_BAD_LOGIN", null);
        log.close();

        String all = String.join("\n", readCurrent(dir));
        assertFalse(all.contains("TOPSECRETvalue12345"), "secret leaked into audit log");
        assertTrue(all.contains("********"));
    }

    @Test
    void crashStackStaysOnOnePhysicalLine(@TempDir Path tmp) throws Exception {
        File dir = tmp.toFile();
        GAuditLog log = new GAuditLog(dir, BIG, 5);
        log.logCrash("AWT-EventQueue-0", new NullPointerException("boom"));
        log.close();

        List<String> lines = readCurrent(dir);
        assertEquals(1, lines.size(), "crash record must be a single physical line");

        JsonObject o = GSON.fromJson(lines.get(0), JsonObject.class);
        assertEquals("CRASH", o.get("cat").getAsString());
        assertEquals("java.lang.NullPointerException", o.get("ex").getAsString());
        assertTrue(o.get("stack").getAsString().contains("\n"),
            "stack field should carry newlines (escaped within the JSON string)");
    }

    @Test
    void appendPreservesPriorLinesAcrossInstances(@TempDir Path tmp) throws Exception {
        File dir = tmp.toFile();

        GAuditLog a = new GAuditLog(dir, BIG, 5);
        a.logLifecycle("start");
        a.close();

        GAuditLog b = new GAuditLog(dir, BIG, 5);
        b.logLifecycle("stop");
        b.close();

        List<String> lines = readCurrent(dir);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("start"));
        assertTrue(lines.get(1).contains("stop"));
    }

    @Test
    void rotationRollsAndCapsFileCount(@TempDir Path tmp) throws Exception {
        File dir = tmp.toFile();
        int count = 3;
        GAuditLog log = new GAuditLog(dir, 1024 /* tiny → force rotation */, count);
        for (int i = 0; i < 500; i++) {
            log.logAction("download", "s3:bucket", "path/to/object-" + i + ".bin");
        }
        log.close();

        File[] files = dir.listFiles(
            (d, n) -> n.startsWith("relaysftp-audit.") && n.endsWith(".jsonl"));
        assertNotNull(files);
        assertTrue(files.length > 1, "expected rotation to produce multiple files");
        assertTrue(files.length <= count,
            "rotation should cap at " + count + " files, got " + files.length);
    }

    @Test
    void bytesFieldSerializesAsNumber(@TempDir Path tmp) throws Exception {
        File dir = tmp.toFile();
        GAuditLog log = new GAuditLog(dir, BIG, 5);
        log.logTransfer("upload", "s3:b", "big.bin", 104857600L, 4200L);
        log.close();

        JsonObject o = GSON.fromJson(readCurrent(dir).get(0), JsonObject.class);
        assertEquals(104857600L, o.get("bytes").getAsLong());
        assertEquals(4200L, o.get("ms").getAsLong());
    }

    @Test
    void loginRecordsDurationAndResult(@TempDir Path tmp) throws Exception {
        File dir = tmp.toFile();
        GAuditLog log = new GAuditLog(dir, BIG, 5);
        log.logLogin("my-bucket", "S3", 1234L, true);
        log.logLogin("badhost", "SFTP", 56L, false);
        log.close();

        List<String> lines = readCurrent(dir);
        JsonObject ok = GSON.fromJson(lines.get(0), JsonObject.class);
        assertEquals("LOGIN", ok.get("cat").getAsString());
        assertEquals("S3", ok.get("proto").getAsString());
        assertEquals(1234L, ok.get("ms").getAsLong());
        assertEquals("ok", ok.get("result").getAsString());

        JsonObject failed = GSON.fromJson(lines.get(1), JsonObject.class);
        assertEquals("failed", failed.get("result").getAsString());
        assertEquals(56L, failed.get("ms").getAsLong());
    }

    @Test
    void teedErrorAndWarningUseSeverityCategory(@TempDir Path tmp) throws Exception {
        File dir = tmp.toFile();
        GAuditLog log = new GAuditLog(dir, BIG, 5);
        log.logLogged(GLogMessage.GLOG_ERROR, "ftp:host", "login failed");
        log.logLogged(GLogMessage.GLOG_WARNING, "ftp:host", "slow response");
        log.close();

        List<String> lines = readCurrent(dir);
        assertEquals("ERROR", GSON.fromJson(lines.get(0), JsonObject.class).get("cat").getAsString());
        assertEquals("WARN", GSON.fromJson(lines.get(1), JsonObject.class).get("cat").getAsString());
    }

    @Test
    void uncaughtHandlerWritesCrashLine(@TempDir Path tmp) throws Exception {
        File dir = tmp.toFile();
        GAuditLog log = new GAuditLog(dir, BIG, 5);

        Thread t = new Thread(() -> { throw new IllegalStateException("kaboom"); }, "worker-1");
        t.setUncaughtExceptionHandler(log.asUncaughtHandler());
        t.start();
        t.join();
        log.close();

        JsonObject o = GSON.fromJson(readCurrent(dir).get(0), JsonObject.class);
        assertEquals("CRASH", o.get("cat").getAsString());
        assertEquals("worker-1", o.get("thread").getAsString());
        assertEquals("java.lang.IllegalStateException", o.get("ex").getAsString());
    }
}
