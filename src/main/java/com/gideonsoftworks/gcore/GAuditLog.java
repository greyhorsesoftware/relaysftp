//
//  GAuditLog.java
//  Gideon
//
//  Always-on, app-wide diagnostics sink. Records user actions, errors, transfer
//  milestones, lifecycle, and crashes as JSON Lines (one JSON object per line) to a
//  rotating file under the per-OS data folder, so a user can forward a single log
//  after a crash. There is no opt-out; safety comes from redaction (GRedact), not
//  from disabling.
//

package com.gideonsoftworks.gcore;

import com.gideonsoftworks.gideon.core.GLocalized;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class GAuditLog
{

// Disable HTML escaping so '=', '<', '&' render literally — these logs are read by humans.
private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

private static final int DEFAULT_LIMIT = 2 * 1024 * 1024;   // 2 MB per file
private static final int DEFAULT_COUNT = 5;                  // keep 5 rotations

private static volatile GAuditLog shared = null;

private final Logger        logger;     // null when the sink failed to open → no-op

/**
 * Open a sink writing rotating JSONL files into {@code logsDir}. Tolerant: if the
 * file cannot be opened the instance degrades to a silent no-op so audit logging
 * can never take down the app.
 */
GAuditLog( final File logsDir, final int limit, final int count )
{
    Logger l = null;
    try
    {
        if( !logsDir.exists() )
        {
            logsDir.mkdirs();
        }
        String pattern = new File(logsDir, "relaysftp-audit.%g.jsonl").getPath();
        FileHandler handler = new FileHandler(pattern, limit, count, true);
        handler.setLevel(Level.ALL);
        handler.setFormatter(new Formatter()
        {
            public String format( LogRecord record )
            {
                return record.getMessage() + System.lineSeparator();
            }
        });

        // Anonymous logger: not shared via LogManager, so multiple instances
        // (incl. tests) never stack handlers on a cached logger.
        l = Logger.getAnonymousLogger();
        l.setUseParentHandlers(false);
        l.setLevel(Level.ALL);
        l.addHandler(handler);
    }
    catch( Exception e )
    {
        System.err.println("GAuditLog: could not open audit log: " + e);
        l = null;
    }
    this.logger = l;
}

/**
 * The process-wide sink, lazily opened under {@code DATA_FOLDER_PATH/logs/}.
 */
public static GAuditLog shared()
{
    GAuditLog s = shared;
    if( s == null )
    {
        synchronized( GAuditLog.class )
        {
            if( shared == null )
            {
                shared = new GAuditLog(
                    new File(GLocalized.DATA_FOLDER_PATH, "logs"),
                    DEFAULT_LIMIT, DEFAULT_COUNT);
            }
            s = shared;
        }
    }
    return s;
}

/** Swap the process-wide sink (tests only). Pass null to restore lazy init. */
static void setSharedForTesting( GAuditLog instance )
{
    synchronized( GAuditLog.class )
    {
        shared = instance;
    }
}

// ---- public static API (producers call these) ----

public static void lifecycle( String event )
{
    shared().logLifecycle(event);
}

public static void action( String op, String conn, String path )
{
    shared().logAction(op, conn, path);
}

public static void transfer( String op, String conn, String remote, long bytes, long ms )
{
    shared().logTransfer(op, conn, remote, bytes, ms);
}

/** Outcome of a background job (download/upload/delete/chmod/...) — see GAuditJobListener. */
public static void job( String op, String conn, String target, long bytes, long ms, String result, String error )
{
    shared().logJob(op, conn, target, bytes, ms, result, error);
}

/** Summary of a mirror run (copies/deletes/bytes/ms) — see GMirrorEngine. */
public static void mirror( String conn, int copies, int deletes, long bytes, long ms )
{
    shared().logMirror(conn, copies, deletes, bytes, ms);
}

public static void error( String conn, String ctx, String gce, Throwable t )
{
    shared().logError(conn, ctx, gce, t);
}

public static void crash( String thread, Throwable t )
{
    shared().logCrash(thread, t);
}

/** Login outcome with how long it took (host, protocol, elapsed ms, success). */
public static void login( String conn, String proto, long ms, boolean ok )
{
    shared().logLogin(conn, proto, ms, ok);
}

/** Tee of an in-app GLog WARNING/ERROR message (see GLog.logMessage). */
public static void logged( int glogType, String conn, String text )
{
    shared().logLogged(glogType, conn, text);
}

/** A proxy was used for a connection: type (SOCKS5/HTTP-CONNECT/FTP-TypeN), the proxy address, the target, outcome. */
public static void proxy( String conn, String type, String via, String target, String result )
{
    shared().logProxy(conn, type, via, target, result);
}

/** Default uncaught-exception handler: record a CRASH line, then flush. */
public static Thread.UncaughtExceptionHandler uncaughtHandler()
{
    return shared().asUncaughtHandler();
}

// ---- instance implementation (tests target these directly) ----

void logLifecycle( String event )
{
    emit("LIFECYCLE", "event", event);
}

void logAction( String op, String conn, String path )
{
    emit("ACTION", "conn", conn, "op", op, "path", path);
}

void logLogin( String conn, String proto, long ms, boolean ok )
{
    emit("LOGIN", "conn", conn, "proto", proto, "ms", ms, "result", ok ? "ok" : "failed");
}

void logTransfer( String op, String conn, String remote, long bytes, long ms )
{
    emit("TRANSFER", "conn", conn, "op", op, "remote", remote, "bytes", bytes, "ms", ms);
}

void logJob( String op, String conn, String target, long bytes, long ms, String result, String error )
{
    emit("JOB", "conn", conn, "op", op, "target", target,
         "bytes", bytes, "ms", ms, "result", result, "error", error);
}

void logMirror( String conn, int copies, int deletes, long bytes, long ms )
{
    emit("MIRROR", "conn", conn, "copies", (long) copies, "deletes", (long) deletes, "bytes", bytes, "ms", ms);
}

void logError( String conn, String ctx, String gce, Throwable t )
{
    emit("ERROR", "conn", conn, "ctx", ctx, "gce", gce,
         "ex", t == null ? null : t.getClass().getName(),
         "msg", t == null ? null : t.getMessage());
}

void logCrash( String thread, Throwable t )
{
    emit("CRASH", "thread", thread,
         "ex", t == null ? null : t.getClass().getName(),
         "msg", t == null ? null : t.getMessage(),
         "stack", stackToString(t));
}

void logLogged( int glogType, String conn, String text )
{
    // WARNING and ERROR are the only types teed; keep severity in the category.
    String cat = ( glogType == GLogMessage.GLOG_WARNING ) ? "WARN" : "ERROR";
    emit(cat, "conn", conn, "msg", text);
}

void logProxy( String conn, String type, String via, String target, String result )
{
    emit("PROXY", "conn", conn, "type", type, "via", via, "target", target, "result", result);
}

Thread.UncaughtExceptionHandler asUncaughtHandler()
{
    return ( thread, throwable ) ->
    {
        logCrash(thread == null ? null : thread.getName(), throwable);
        flush();
    };
}

/**
 * Build one JSON object from {@code ts}, {@code cat}, and the supplied key/value
 * pairs (null values skipped), redact every string value, and write it as one line.
 */
private void emit( String cat, Object... kv )
{
    if( logger == null )
    {
        return;
    }

    Map<String, Object> m = new LinkedHashMap<String, Object>();
    m.put("ts", DateTimeFormatter.ISO_INSTANT.format(
        Instant.now().truncatedTo(ChronoUnit.MILLIS)));
    m.put("cat", cat);

    for( int i = 0; i + 1 < kv.length; i += 2 )
    {
        Object value = kv[i + 1];
        if( value == null )
        {
            continue;
        }
        if( value instanceof String )
        {
            value = GRedact.apply((String) value);
        }
        m.put(String.valueOf(kv[i]), value);
    }

    // Gson escapes quotes and newlines, so a multi-line stack stays on one line.
    logger.log(Level.INFO, GSON.toJson(m));
}

private static String stackToString( Throwable t )
{
    if( t == null ) return null;
    java.io.StringWriter sw = new java.io.StringWriter();
    t.printStackTrace(new java.io.PrintWriter(sw));
    return sw.toString();
}

/** Force buffered records to disk without releasing the file handle. */
void flush()
{
    if( logger != null )
    {
        for( java.util.logging.Handler h : logger.getHandlers() )
        {
            h.flush();
        }
    }
}

/** Flush and release the file handle (mainly for tests / clean shutdown). */
void close()
{
    if( logger != null )
    {
        for( java.util.logging.Handler h : logger.getHandlers() )
        {
            h.close();
        }
    }
}

}
