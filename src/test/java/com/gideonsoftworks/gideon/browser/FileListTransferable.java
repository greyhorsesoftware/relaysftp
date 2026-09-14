package com.gideonsoftworks.gideon.browser;

import java.awt.datatransfer.*;
import java.io.File;
import java.util.List;

/**
 * Test-only Transferable that simulates an OS file drop (Finder/Explorer/Nautilus).
 */
public class FileListTransferable implements Transferable {

    private final List<File> files;

    public FileListTransferable(List<File> files) {
        this.files = files;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[]{ DataFlavor.javaFileListFlavor };
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return DataFlavor.javaFileListFlavor.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
        return files;
    }
}
