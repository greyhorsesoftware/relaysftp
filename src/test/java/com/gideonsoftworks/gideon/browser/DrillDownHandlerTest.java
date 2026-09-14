package com.gideonsoftworks.gideon.browser;

import com.gideonsoftworks.gcore.GFileItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.*;

class DrillDownHandlerTest {

    @TempDir
    Path tempDir;

    private GBrowserPanelCommandInterface panel;
    private DrillDownHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(tempDir.resolve("folderA"));
        Files.createFile(tempDir.resolve("file1.txt"));

        panel = mock(GBrowserPanelCommandInterface.class);
        handler = new DrillDownHandler(new JTable(), panel);
    }

    @Test
    void hoverOverFolder_navigatesAfterDelay() throws Exception {
        GFileItem folder = new GFileItem(tempDir.resolve("folderA").toFile());
        when(panel.getItemAt(0, 0)).thenReturn(folder);

        handler.onRowChanged(0);

        Thread.sleep(1000);
        SwingUtilities.invokeAndWait(() -> {});

        verify(panel).changeToFolder(folder);
    }

    @Test
    void hoverUnderThreshold_doesNotNavigate() throws Exception {
        GFileItem folder = new GFileItem(tempDir.resolve("folderA").toFile());
        when(panel.getItemAt(0, 0)).thenReturn(folder);

        handler.onRowChanged(0);
        Thread.sleep(400);
        handler.onRowChanged(-1); // drag left the table

        Thread.sleep(600);
        SwingUtilities.invokeAndWait(() -> {});

        verify(panel, never()).changeToFolder(any());
    }

    @Test
    void hoverOverFile_doesNotNavigate() throws Exception {
        GFileItem file = new GFileItem(tempDir.resolve("file1.txt").toFile());
        when(panel.getItemAt(0, 0)).thenReturn(file);

        handler.onRowChanged(0);

        Thread.sleep(1000);
        SwingUtilities.invokeAndWait(() -> {});

        verify(panel, never()).changeToFolder(any());
    }

    @Test
    void hoverOverNull_doesNotNavigate() throws Exception {
        when(panel.getItemAt(0, 0)).thenReturn(null);

        handler.onRowChanged(0);

        Thread.sleep(1000);
        SwingUtilities.invokeAndWait(() -> {});

        verify(panel, never()).changeToFolder(any());
    }

    @Test
    void drillDownThenLeaveTable_backsUpToParentAfterDelay() throws Exception {
        GFileItem parent  = new GFileItem(tempDir.toFile());
        GFileItem folderA = new GFileItem(tempDir.resolve("folderA").toFile());
        folderA.setParentFileItem(parent);
        when(panel.getItemAt(0, 0)).thenReturn(folderA);

        // 1) hover over the folder long enough to drill in
        handler.onRowChanged(0);
        Thread.sleep(1000);
        SwingUtilities.invokeAndWait(() -> {});
        verify(panel).changeToFolder(folderA);

        // 2) drag moves over a non-folder row, then leaves the table entirely
        GFileItem file = new GFileItem(tempDir.resolve("file1.txt").toFile());
        when(panel.getItemAt(1, 0)).thenReturn(file);
        when(panel.getVirtualCWD()).thenReturn(folderA);
        handler.onRowChanged(1);    // over a file — no new drill, but updates the hover row
        handler.onRowChanged(-1);   // drag left the table → back-up timer starts (we drilled down once)

        // 3) after the back-up delay it navigates to the parent
        Thread.sleep(1400);
        SwingUtilities.invokeAndWait(() -> {});
        verify(panel).changeToFolder(parent);
    }

    @Test
    void switchingRows_resetsTimer() throws Exception {
        GFileItem folderA = new GFileItem(tempDir.resolve("folderA").toFile());
        when(panel.getItemAt(0, 0)).thenReturn(folderA);

        handler.onRowChanged(0);
        Thread.sleep(600);

        // Switch to a file row — cancels the folderA timer
        GFileItem file = new GFileItem(tempDir.resolve("file1.txt").toFile());
        when(panel.getItemAt(1, 0)).thenReturn(file);
        handler.onRowChanged(1);

        Thread.sleep(500);
        SwingUtilities.invokeAndWait(() -> {});

        verify(panel, never()).changeToFolder(any());
    }
}
