package com.gideonsoftworks.gideon.browser;

import com.gideonsoftworks.gcore.GFileItem;
import java.awt.datatransfer.*;
import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;

public class GFileItemSelection implements Transferable {

    public static final DataFlavor FLAVOR = new DataFlavor(
        GFileItemSelection.class, "RelaySFTP File Items");

    private static final DataFlavor[] LOCAL_FLAVORS = {
        FLAVOR, DataFlavor.javaFileListFlavor
    };
    private static final DataFlavor[] REMOTE_FLAVORS = { FLAVOR };

    private final GFileItem[] items;
    private final boolean fromLocal;

    public GFileItemSelection(GFileItem[] items, boolean fromLocal) {
        this.items = items;
        this.fromLocal = fromLocal;
    }

    public GFileItem[] getItems() { return items; }
    public boolean isFromLocal() { return fromLocal; }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return fromLocal ? LOCAL_FLAVORS : REMOTE_FLAVORS;
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        for (DataFlavor f : getTransferDataFlavors()) {
            if (f.equals(flavor)) return true;
        }
        return false;
    }

    @Override
    public Object getTransferData(DataFlavor flavor)
            throws UnsupportedFlavorException {
        if (flavor.equals(FLAVOR)) {
            return this;
        }
        if (flavor.equals(DataFlavor.javaFileListFlavor) && fromLocal) {
            return Arrays.stream(items)
                .map(fi -> new File(fi.getCompletePath()))
                .collect(Collectors.toList());
        }
        throw new UnsupportedFlavorException(flavor);
    }
}
