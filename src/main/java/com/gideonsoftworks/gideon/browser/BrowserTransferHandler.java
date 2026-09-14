package com.gideonsoftworks.gideon.browser;

import com.gideonsoftworks.gcore.GFileItem;
import com.gideonsoftworks.gideon.core.GLocalized;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.function.Supplier;

public class BrowserTransferHandler extends TransferHandler {

    private final GBrowserPanelCommandInterface panel;
    private final Supplier<GBrowserPanelCommandInterface> buddySupplier;
    private boolean exporting = false;

    public BrowserTransferHandler(GBrowserPanelCommandInterface panel,
                                   Supplier<GBrowserPanelCommandInterface> buddySupplier) {
        this.panel = panel;
        this.buddySupplier = buddySupplier;
    }

    // --- Export (drag out of table) ---

    @Override
    public int getSourceActions(JComponent c) {
        return COPY;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        GFileItem[] selection = panel.getSelection();
        if (selection == null || selection.length == 0) return null;

        exporting = true;
        setDragImage(buildDragImage(selection));
        setDragImageOffset(new Point(-32, -32));

        return new GFileItemSelection(selection, panel.hasLocalSelection());
    }

    private BufferedImage buildDragImage(GFileItem[] items) {
        int iconSize = 64;

        if (items.length == 1) {
            ImageIcon icon = items[0].getIsDir() ? GLocalized.FOLDER_BIG : GLocalized.FILE_BIG;
            BufferedImage img = new BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(icon.getImage(), 0, 0, iconSize, iconSize, null);
            g.dispose();
            return img;
        }

        int count = Math.min(items.length, 4);
        // Rotation angles for fan: back card most rotated, front card straight
        double[] angles = { -12, -4, 4, 12 };
        int padding = 20; // extra space for rotated edges
        int totalSize = iconSize + padding * 2;

        BufferedImage composite = new BufferedImage(totalSize, totalSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = composite.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int cx = totalSize / 2;
        int cy = totalSize / 2;

        // Draw icons back to front, each rotated slightly
        // Center the fan angles based on count
        int startIdx = (4 - count) / 2;
        for (int i = count - 1; i >= 0; i--) {
            ImageIcon icon = items[i].getIsDir() ? GLocalized.FOLDER_BIG : GLocalized.FILE_BIG;
            double angle = Math.toRadians(angles[startIdx + i]);

            Graphics2D gc = (Graphics2D) g2.create();
            gc.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            gc.rotate(angle, cx, cy);
            gc.drawImage(icon.getImage(),
                cx - iconSize / 2, cy - iconSize / 2,
                iconSize, iconSize, null);
            gc.dispose();
        }

        // Draw badge with count
        if (items.length > 1) {
            String badge = String.valueOf(items.length);
            g2.setFont(new Font("SansSerif", Font.BOLD, 12));
            FontMetrics fm = g2.getFontMetrics();
            int badgeW = Math.max(fm.stringWidth(badge) + 8, 20);
            int badgeH = 18;
            int bx = totalSize - badgeW - 2;
            int by = 2;

            g2.setColor(new Color(220, 50, 50));
            g2.fillRoundRect(bx, by, badgeW, badgeH, badgeH, badgeH);
            g2.setColor(Color.WHITE);
            g2.drawString(badge, bx + (badgeW - fm.stringWidth(badge)) / 2,
                by + badgeH - fm.getDescent() - 1);
        }

        g2.dispose();
        return composite;
    }

    @Override
    protected void exportDone(JComponent source, Transferable data, int action) {
        exporting = false;
    }

    // --- Import (drop onto table) ---

    @Override
    public boolean canImport(TransferSupport support) {
        // Reject drops back onto the source panel
        if (exporting) return false;
        if (!panel.isConnected() && !panel.hasLocalSelection()) return false;
        if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return true;
        if (support.isDataFlavorSupported(GFileItemSelection.FLAVOR)) return true;
        return false;
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) return false;

        GFileItem targetFolder = resolveDropTarget(support);

        try {
            Transferable t = support.getTransferable();

            if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                return handleOsFileDrop(files, targetFolder);
            }

            if (support.isDataFlavorSupported(GFileItemSelection.FLAVOR)) {
                GFileItemSelection sel = (GFileItemSelection)
                    t.getTransferData(GFileItemSelection.FLAVOR);
                return handleCrossPanelDrop(sel, targetFolder);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private GFileItem resolveDropTarget(TransferSupport support) {
        if (support.isDrop()) {
            JTable.DropLocation dl = (JTable.DropLocation) support.getDropLocation();
            int viewRow = dl.getRow();
            if (viewRow >= 0 && !dl.isInsertRow()) {
                // Convert view row to model row to handle sorting
                JTable table = (JTable) support.getComponent();
                int modelRow = table.convertRowIndexToModel(viewRow);
                GFileItem item = panel.getItemAt(modelRow, 0);
                if (item != null && item.getIsDir() && !item.getIsCDUP()) {
                    return item;
                }
            }
        }
        return panel.getVirtualCWD();
    }

    private boolean handleOsFileDrop(List<File> files, GFileItem targetFolder) {
        if (files == null || files.isEmpty()) return false;

        if (panel.hasLocalSelection()) {
            File first = files.get(0);
            File navTarget = first.isDirectory() ? first : first.getParentFile();
            // Walk up from CWD to find tree root for cache reuse
            GFileItem root = panel.getVirtualCWD();
            while( root != null && root.getParentFileItem() != null ) {
                root = root.getParentFileItem();
            }
            GFileItem localFolder = GBrowserPanelController.buildLocalFileItemWithParents(navTarget, root);
            panel.changeToFolder(localFolder);
        } else {
            GFileItem[] items = files.stream()
                .map(GFileItem::new)
                .toArray(GFileItem[]::new);
            buddySupplier.get().uploadTo(items, targetFolder, this);
        }
        return true;
    }

    private boolean handleCrossPanelDrop(GFileItemSelection sel, GFileItem targetFolder) {
        GFileItem[] items = sel.getItems();
        if (items == null || items.length == 0) return false;

        GBrowserPanelCommandInterface buddy = buddySupplier.get();

        if (sel.isFromLocal() && !panel.hasLocalSelection()) {
            buddy.uploadTo(items, targetFolder, this);
        } else if (!sel.isFromLocal() && panel.hasLocalSelection()) {
            buddy.downloadTo(items, targetFolder, this);
        }
        return true;
    }
}
