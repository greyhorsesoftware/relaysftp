package com.gideonsoftworks.gideon.browser;

import com.gideonsoftworks.gcore.GFileItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import javax.swing.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrowserTransferHandlerTest {

    @TempDir
    Path tempDir;

    private GBrowserPanelCommandInterface localPanel;
    private GBrowserPanelCommandInterface remotePanel;
    private BrowserTransferHandler localHandler;
    private BrowserTransferHandler remoteHandler;
    private JTable localTable;
    private JTable remoteTable;

    private GFileItem localCWD;
    private GFileItem remoteCWD;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(tempDir.resolve("folderA"));
        Files.createFile(tempDir.resolve("file1.txt"));
        Files.createFile(tempDir.resolve("file2.txt"));

        localCWD = new GFileItem(tempDir.toFile());
        remoteCWD = new GFileItem("/remote", "remote_dir", 0, null);

        localPanel = mock(GBrowserPanelCommandInterface.class);
        remotePanel = mock(GBrowserPanelCommandInterface.class);

        when(localPanel.hasLocalSelection()).thenReturn(true);
        when(localPanel.isConnected()).thenReturn(true);
        when(localPanel.getVirtualCWD()).thenReturn(localCWD);

        when(remotePanel.hasLocalSelection()).thenReturn(false);
        when(remotePanel.isConnected()).thenReturn(true);
        when(remotePanel.getVirtualCWD()).thenReturn(remoteCWD);

        localTable = new JTable();
        remoteTable = new JTable();

        // Local handler's buddy is remotePanel, remote handler's buddy is localPanel
        localHandler = new BrowserTransferHandler(localPanel, () -> remotePanel);
        remoteHandler = new BrowserTransferHandler(remotePanel, () -> localPanel);
    }

    // --- Test 1: OS drop on local panel -> navigate ---

    @Test
    void osDropFolderOnLocalPanel_navigatesToFolder() {
        File folderA = tempDir.resolve("folderA").toFile();
        Transferable t = new FileListTransferable(List.of(folderA));
        TransferHandler.TransferSupport support = new TransferHandler.TransferSupport(localTable, t);

        assertTrue(localHandler.importData(support));

        ArgumentCaptor<GFileItem> captor = ArgumentCaptor.forClass(GFileItem.class);
        verify(localPanel).changeToFolder(captor.capture());
        assertEquals("folderA", captor.getValue().getName());
    }

    @Test
    void osDropFileOnLocalPanel_navigatesToParent() {
        File file1 = tempDir.resolve("file1.txt").toFile();
        Transferable t = new FileListTransferable(List.of(file1));
        TransferHandler.TransferSupport support = new TransferHandler.TransferSupport(localTable, t);

        assertTrue(localHandler.importData(support));

        ArgumentCaptor<GFileItem> captor = ArgumentCaptor.forClass(GFileItem.class);
        verify(localPanel).changeToFolder(captor.capture());
        assertEquals(tempDir.toFile().getName(), captor.getValue().getName());
    }

    // --- Test 2: OS drop on remote panel -> upload ---

    @Test
    void osDropOnRemotePanel_callsUpload() {
        File file1 = tempDir.resolve("file1.txt").toFile();
        File file2 = tempDir.resolve("file2.txt").toFile();
        Transferable t = new FileListTransferable(List.of(file1, file2));
        TransferHandler.TransferSupport support = new TransferHandler.TransferSupport(remoteTable, t);

        assertTrue(remoteHandler.importData(support));

        ArgumentCaptor<GFileItem[]> itemsCaptor = ArgumentCaptor.forClass(GFileItem[].class);
        ArgumentCaptor<GFileItem> targetCaptor = ArgumentCaptor.forClass(GFileItem.class);
        verify(localPanel).uploadTo(itemsCaptor.capture(), targetCaptor.capture(), any());

        assertEquals(2, itemsCaptor.getValue().length);
        assertEquals("file1.txt", itemsCaptor.getValue()[0].getName());
        assertEquals("file2.txt", itemsCaptor.getValue()[1].getName());
        assertSame(remoteCWD, targetCaptor.getValue());
    }

    // --- Test 3: Local -> Remote (cross-panel upload) ---

    @Test
    void localToRemoteDrop_callsUpload() {
        GFileItem localFile = new GFileItem(tempDir.resolve("file1.txt").toFile());
        when(localPanel.getSelection()).thenReturn(new GFileItem[]{ localFile });

        Transferable t = localHandler.createTransferable(localTable);
        assertNotNull(t);

        TransferHandler.TransferSupport support = new TransferHandler.TransferSupport(remoteTable, t);
        assertTrue(remoteHandler.importData(support));

        ArgumentCaptor<GFileItem[]> captor = ArgumentCaptor.forClass(GFileItem[].class);
        verify(localPanel).uploadTo(captor.capture(), eq(remoteCWD), any());
        assertEquals("file1.txt", captor.getValue()[0].getName());
    }

    // --- Test 4: Remote -> Local (cross-panel download) ---

    @Test
    void remoteToLocalDrop_callsDownload() {
        GFileItem remoteFile = new GFileItem("/remote", "remote_file.dat", 1024, null);
        when(remotePanel.getSelection()).thenReturn(new GFileItem[]{ remoteFile });

        Transferable t = remoteHandler.createTransferable(remoteTable);
        assertNotNull(t);

        TransferHandler.TransferSupport support = new TransferHandler.TransferSupport(localTable, t);
        assertTrue(localHandler.importData(support));

        ArgumentCaptor<GFileItem[]> captor = ArgumentCaptor.forClass(GFileItem[].class);
        verify(remotePanel).downloadTo(captor.capture(), eq(localCWD), any());
        assertEquals("remote_file.dat", captor.getValue()[0].getName());
    }

    // --- Test 6: Drop with no drop location -> uses CWD ---

    @Test
    void dropWithNoDropLocation_usesCurrentWorkingDirectory() {
        File file1 = tempDir.resolve("file1.txt").toFile();
        Transferable t = new FileListTransferable(List.of(file1));
        TransferHandler.TransferSupport support = new TransferHandler.TransferSupport(remoteTable, t);

        remoteHandler.importData(support);

        ArgumentCaptor<GFileItem> targetCaptor = ArgumentCaptor.forClass(GFileItem.class);
        verify(localPanel).uploadTo(any(), targetCaptor.capture(), any());
        assertSame(remoteCWD, targetCaptor.getValue());
    }

    // --- Test 7: Drop on disconnected remote panel -> rejected ---

    @Test
    void dropOnDisconnectedRemotePanel_canImportReturnsFalse() {
        when(remotePanel.isConnected()).thenReturn(false);

        Transferable t = new FileListTransferable(List.of(tempDir.resolve("file1.txt").toFile()));
        TransferHandler.TransferSupport support = new TransferHandler.TransferSupport(remoteTable, t);

        assertFalse(remoteHandler.canImport(support));
    }

    @Test
    void dropOnDisconnectedRemotePanel_importDataReturnsFalse() {
        when(remotePanel.isConnected()).thenReturn(false);

        Transferable t = new FileListTransferable(List.of(tempDir.resolve("file1.txt").toFile()));
        TransferHandler.TransferSupport support = new TransferHandler.TransferSupport(remoteTable, t);

        assertFalse(remoteHandler.importData(support));
        verify(localPanel, never()).uploadTo(any(), any(), any());
    }

    // --- canImport ---

    @Test
    void canImport_acceptsJavaFileListFlavor() {
        Transferable t = new FileListTransferable(List.of(tempDir.resolve("file1.txt").toFile()));
        TransferHandler.TransferSupport support = new TransferHandler.TransferSupport(localTable, t);
        assertTrue(localHandler.canImport(support));
    }

    @Test
    void canImport_acceptsGFileItemSelectionFlavor() {
        GFileItem remoteFile = new GFileItem("/remote", "r.txt", 0, null);
        GFileItemSelection sel = new GFileItemSelection(new GFileItem[]{ remoteFile }, false);
        TransferHandler.TransferSupport support = new TransferHandler.TransferSupport(localTable, sel);
        assertTrue(localHandler.canImport(support));
    }

    @Test
    void canImport_rejectsUnsupportedFlavor() {
        Transferable t = new Transferable() {
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{ DataFlavor.stringFlavor };
            }
            public boolean isDataFlavorSupported(DataFlavor f) {
                return DataFlavor.stringFlavor.equals(f);
            }
            public Object getTransferData(DataFlavor f) { return "text"; }
        };
        TransferHandler.TransferSupport support = new TransferHandler.TransferSupport(localTable, t);
        assertFalse(localHandler.canImport(support));
    }

    // --- createTransferable ---

    @Test
    void createTransferable_noSelection_returnsNull() {
        when(localPanel.getSelection()).thenReturn(null);
        assertNull(localHandler.createTransferable(localTable));
    }

    @Test
    void createTransferable_emptySelection_returnsNull() {
        when(localPanel.getSelection()).thenReturn(new GFileItem[0]);
        assertNull(localHandler.createTransferable(localTable));
    }

    @Test
    void createTransferable_localPanel_createsLocalSelection() {
        GFileItem f = new GFileItem(tempDir.resolve("file1.txt").toFile());
        when(localPanel.getSelection()).thenReturn(new GFileItem[]{ f });

        Transferable t = localHandler.createTransferable(localTable);
        assertInstanceOf(GFileItemSelection.class, t);
        assertTrue(((GFileItemSelection) t).isFromLocal());
    }

    @Test
    void createTransferable_remotePanel_createsRemoteSelection() {
        GFileItem r = new GFileItem("/remote", "r.txt", 0, null);
        when(remotePanel.getSelection()).thenReturn(new GFileItem[]{ r });

        Transferable t = remoteHandler.createTransferable(remoteTable);
        assertInstanceOf(GFileItemSelection.class, t);
        assertFalse(((GFileItemSelection) t).isFromLocal());
    }

    // --- Same-panel drop ---

    @Test
    void samePanelDrop_localToLocal_noTransfer() {
        GFileItem f = new GFileItem(tempDir.resolve("file1.txt").toFile());
        GFileItemSelection sel = new GFileItemSelection(new GFileItem[]{ f }, true);
        TransferHandler.TransferSupport support = new TransferHandler.TransferSupport(localTable, sel);

        assertTrue(localHandler.importData(support));

        verify(localPanel, never()).uploadTo(any(), any(), any());
        verify(localPanel, never()).downloadTo(any(), any(), any());
        verify(remotePanel, never()).uploadTo(any(), any(), any());
        verify(remotePanel, never()).downloadTo(any(), any(), any());
    }

    // --- Multiple files ---

    @Test
    void osDropMultipleFiles_uploadsAll() throws Exception {
        Files.createFile(tempDir.resolve("file3.txt"));
        Transferable t = new FileListTransferable(List.of(
            tempDir.resolve("file1.txt").toFile(),
            tempDir.resolve("file2.txt").toFile(),
            tempDir.resolve("file3.txt").toFile()
        ));
        TransferHandler.TransferSupport support = new TransferHandler.TransferSupport(remoteTable, t);

        remoteHandler.importData(support);

        ArgumentCaptor<GFileItem[]> captor = ArgumentCaptor.forClass(GFileItem[].class);
        verify(localPanel).uploadTo(captor.capture(), any(), any());
        assertEquals(3, captor.getValue().length);
    }

    // --- Empty drop ---

    @Test
    void osDropEmptyFileList_returnsFalse() {
        Transferable t = new FileListTransferable(List.of());
        TransferHandler.TransferSupport support = new TransferHandler.TransferSupport(remoteTable, t);
        assertFalse(remoteHandler.importData(support));
    }

    // --- Source actions ---

    @Test
    void sourceActions_isCopy() {
        assertEquals(TransferHandler.COPY, localHandler.getSourceActions(localTable));
    }
}
