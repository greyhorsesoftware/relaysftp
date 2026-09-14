package com.gideonsoftworks.gcore;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Small persistence helpers shared by the on-disk save sites (bookmarks, favorites, transfer filters):
 * crash-safe atomic UTF-8 writes, plus a headless-guarded error dialog so save failures are never silent.
 */
public final class GAtomicFileWriter
{
    private GAtomicFileWriter() {}

    /**
     * Write {@code content} as UTF-8 to a sibling {@code <name>.tmp} file, flush/close it, then move it
     * onto {@code target} atomically. Falls back to a plain (non-atomic) replace if the filesystem does
     * not support atomic moves. A crash mid-write leaves {@code target} untouched rather than truncated.
     */
    public static void writeUtf8( File target, String content ) throws IOException
    {
        File dir = target.getAbsoluteFile().getParentFile();
        File tmp = new File( dir, target.getName() + ".tmp" );
        Path tmpPath    = tmp.toPath();
        Path targetPath = target.toPath();

        Files.writeString( tmpPath, content, StandardCharsets.UTF_8 );   // creates, flushes and closes
        try {
            Files.move( tmpPath, targetPath, StandardCopyOption.ATOMIC_MOVE );
        } catch( AtomicMoveNotSupportedException e ) {
            Files.move( tmpPath, targetPath, StandardCopyOption.REPLACE_EXISTING );
        }
    }

    /**
     * Show a factual, headless-guarded save-failure dialog on the EDT. Safe to call from any thread.
     * Never pass secret values into {@code message}.
     */
    public static void showSaveError( String message )
    {
        if( GraphicsEnvironment.isHeadless() ) return;
        SwingUtilities.invokeLater( () ->
            JOptionPane.showMessageDialog( null, message, "RelaySFTP", JOptionPane.ERROR_MESSAGE ) );
    }
}
