//
//  GDiagnostics.java
//  Gideon
//
//  Bundles the audit logs plus a small environment summary into a single zip the
//  user can attach to a support email. No secrets are added beyond what the logs
//  already contain (which are redacted by GRedact at write time).
//

package com.gideonsoftworks.gcore;

import com.gideonsoftworks.gideon.core.GLocalized;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class GDiagnostics
{

private GDiagnostics() { }

/**
 * Write a diagnostics bundle (logs + environment.txt) to {@code targetZip}.
 *
 * @return the number of log files included.
 */
public static int exportTo( File targetZip ) throws IOException
{
    return exportTo(targetZip, new File(GLocalized.DATA_FOLDER_PATH, "logs"));
}

static int exportTo( File targetZip, File logsDir ) throws IOException
{
    int logCount = 0;

    try( ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(targetZip)) )
    {
        zip.putNextEntry(new ZipEntry("environment.txt"));
        zip.write(environment().getBytes("UTF-8"));
        zip.closeEntry();

        File[] logs = logsDir.listFiles(
            (d, n) -> n.startsWith("relaysftp-audit.") && n.endsWith(".jsonl"));

        if( logs != null )
        {
            for( File log : logs )
            {
                zip.putNextEntry(new ZipEntry("logs/" + log.getName()));
                Files.copy(log.toPath(), zip);
                zip.closeEntry();
                logCount++;
            }
        }
    }

    return logCount;
}

private static String environment()
{
    String version = GDiagnostics.class.getPackage().getImplementationVersion();
    StringBuilder sb = new StringBuilder();
    sb.append("RelaySFTP diagnostics\n");
    sb.append("version=").append(version != null ? version : "dev").append('\n');
    sb.append("os=").append(System.getProperty("os.name", "?"))
      .append(' ').append(System.getProperty("os.version", "?"))
      .append(" (").append(System.getProperty("os.arch", "?")).append(")\n");
    sb.append("java=").append(System.getProperty("java.version", "?")).append('\n');
    sb.append("locale=").append(Locale.getDefault()).append('\n');
    sb.append("dataDir=").append(GLocalized.DATA_FOLDER_PATH).append('\n');
    return sb.toString();
}

}
