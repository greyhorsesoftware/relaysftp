package com.gideonsoftworks.gideon.browser;

import com.gideonsoftworks.gcore.GFileItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GFileItemSelectionTest {

    @TempDir
    Path tempDir;

    @Test
    void localSelection_supportsBothFlavors() throws Exception {
        File f = Files.createFile(tempDir.resolve("file1.txt")).toFile();
        GFileItem[] items = { new GFileItem(f) };
        GFileItemSelection sel = new GFileItemSelection(items, true);

        assertTrue(sel.isDataFlavorSupported(GFileItemSelection.FLAVOR));
        assertTrue(sel.isDataFlavorSupported(DataFlavor.javaFileListFlavor));
    }

    @Test
    void remoteSelection_supportsOnlyCustomFlavor() {
        GFileItem remote = new GFileItem("/remote", "remote.txt", 100, null);
        GFileItem[] items = { remote };
        GFileItemSelection sel = new GFileItemSelection(items, false);

        assertTrue(sel.isDataFlavorSupported(GFileItemSelection.FLAVOR));
        assertFalse(sel.isDataFlavorSupported(DataFlavor.javaFileListFlavor));
    }

    @Test
    void getTransferData_customFlavor_returnsSelf() throws Exception {
        File f = Files.createFile(tempDir.resolve("file1.txt")).toFile();
        GFileItem[] items = { new GFileItem(f) };
        GFileItemSelection sel = new GFileItemSelection(items, true);

        assertSame(sel, sel.getTransferData(GFileItemSelection.FLAVOR));
    }

    @Test
    void getTransferData_fileListFlavor_returnsFileList() throws Exception {
        File f = Files.createFile(tempDir.resolve("file1.txt")).toFile();
        GFileItem[] items = { new GFileItem(f) };
        GFileItemSelection sel = new GFileItemSelection(items, true);

        Object data = sel.getTransferData(DataFlavor.javaFileListFlavor);
        assertInstanceOf(List.class, data);
        @SuppressWarnings("unchecked")
        List<File> files = (List<File>) data;
        assertEquals(1, files.size());
        assertEquals(f.getAbsolutePath(), files.get(0).getAbsolutePath());
    }

    @Test
    void getTransferData_unsupportedFlavor_throws() {
        GFileItem remote = new GFileItem("/remote", "remote.txt", 100, null);
        GFileItem[] items = { remote };
        GFileItemSelection sel = new GFileItemSelection(items, false);

        assertThrows(UnsupportedFlavorException.class, () ->
            sel.getTransferData(DataFlavor.javaFileListFlavor));
    }

    @Test
    void getItems_returnsOriginalArray() throws Exception {
        File f = Files.createFile(tempDir.resolve("a.txt")).toFile();
        GFileItem[] items = { new GFileItem(f) };
        GFileItemSelection sel = new GFileItemSelection(items, true);

        assertSame(items, sel.getItems());
    }

    @Test
    void isFromLocal_reflectsConstructorArg() {
        GFileItem remote = new GFileItem("/remote", "r.txt", 0, null);
        GFileItem[] items = { remote };

        assertTrue(new GFileItemSelection(items, true).isFromLocal());
        assertFalse(new GFileItemSelection(items, false).isFromLocal());
    }
}
