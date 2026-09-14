package com.gideonsoftworks.gcore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class GDiagnosticsTest {

    private Map<String, String> readZip(File zip) throws Exception {
        Map<String, String> entries = new HashMap<>();
        try (ZipFile zf = new ZipFile(zip)) {
            var en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                try (InputStream in = zf.getInputStream(e)) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    in.transferTo(baos);
                    entries.put(e.getName(), baos.toString("UTF-8"));
                }
            }
        }
        return entries;
    }

    @Test
    void exportBundlesLogsAndEnvironment(@TempDir Path tmp) throws Exception {
        File logsDir = new File(tmp.toFile(), "logs");
        logsDir.mkdirs();

        // Produce a log line that included a secret — written redacted.
        GAuditLog log = new GAuditLog(logsDir, 2 * 1024 * 1024, 5);
        log.logError("s3:bucket", "connect secretKey=TOPSECRETvalue12345", "GCE_BAD_LOGIN", null);
        log.close();

        File zip = new File(tmp.toFile(), "diag.zip");
        int count = GDiagnostics.exportTo(zip, logsDir);
        assertEquals(1, count, "expected one log file bundled");

        Map<String, String> entries = readZip(zip);
        assertTrue(entries.containsKey("environment.txt"));
        assertTrue(entries.containsKey("logs/relaysftp-audit.0.jsonl"));

        // environment summary carries version + os; never the data secret.
        assertTrue(entries.get("environment.txt").contains("version="));
        assertTrue(entries.get("environment.txt").contains("os="));

        // The whole bundle must be free of the secret (redacted at write time).
        String all = String.join("\n", entries.values());
        assertFalse(all.contains("TOPSECRETvalue12345"), "secret present in exported bundle");
        assertTrue(entries.get("logs/relaysftp-audit.0.jsonl").contains("********"));
    }

    @Test
    void exportWithNoLogsStillWritesEnvironment(@TempDir Path tmp) throws Exception {
        File logsDir = new File(tmp.toFile(), "logs");   // never created
        File zip = new File(tmp.toFile(), "diag.zip");

        int count = GDiagnostics.exportTo(zip, logsDir);
        assertEquals(0, count);

        Map<String, String> entries = readZip(zip);
        assertTrue(entries.containsKey("environment.txt"));
    }
}
