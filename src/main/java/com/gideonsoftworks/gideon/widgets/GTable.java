//
//  GTable.java
//  Gideon
//
//  Created by David on 11/10/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.widgets;

import com.gideonsoftworks.gcore.GFileItem;
import com.gideonsoftworks.gideon.browser.GBrowserPanelCommandInterface;
import java.util.*;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.geom.*;

public class GTable extends JTable
{
    private GBrowserPanelCommandInterface browserPanel = null;
    private static final int ARC = 10;
    private static final int INSET = 2;

    public class GTableCellRenderer extends DefaultTableCellRenderer {

        public GTableCellRenderer() { }

        public Component getTableCellRendererComponent(JTable jTable,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int col)
        {
            Component c = super.getTableCellRendererComponent(jTable,
                                                              value,
                                                              isSelected,
                                                              hasFocus,
                                                              row,
                                                              col);
            ((JLabel)c).setBorder(super.noFocusBorder);
            return c;
        }
    }

    public GTable()
    {
        super();
        setDefaultRenderer(Object.class, new GTableCellRenderer());
        setDefaultRenderer(Date.class, new GTableCellRenderer());
        setIntercellSpacing( new Dimension( 0, 0 ) );
        setShowGrid( false );
        setShowHorizontalLines( false );
        setShowVerticalLines( false );
        putClientProperty("Table.showDropLocation", false);
    }

    public void setBrowserPanel(GBrowserPanelCommandInterface panel)
    {
        browserPanel = panel;
    }

    public boolean getScrollableTracksViewportHeight()
    {
        Component parent = getParent();
        if (parent instanceof JViewport)
            return parent.getHeight() > getPreferredSize().height;
        return false;
    }

    private boolean isFolder(int viewRow)
    {
        if( browserPanel == null || viewRow < 0 || viewRow >= getRowCount() ) return false;
        int modelRow = convertRowIndexToModel(viewRow);
        GFileItem item = browserPanel.getItemAt(modelRow, 0);
        return item != null && item.getIsDir() && !item.getIsCDUP();
    }

    private Rectangle getFullRowRect(int row)
    {
        Rectangle first = getCellRect(row, 0, true);
        Rectangle last = getCellRect(row, getColumnCount() - 1, true);
        return new Rectangle(first.x, first.y, last.x + last.width - first.x, first.height);
    }

    /**
     * After default painting, clip the square corners of selected rows
     * to create rounded selection appearance.
     */
    @Override
    public void paint(Graphics g)
    {
        super.paint(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Round the corners of selected rows by painting background over the corners
        int[] selectedRows = getSelectedRows();
        Color bg = getBackground();
        for( int row : selectedRows )
        {
            Rectangle r = getFullRowRect(row);
            // Expand the outer rect slightly to fully cover any anti-aliased edges
            Area corners = new Area(new Rectangle2D.Float(
                r.x, r.y - 1, r.width, r.height + 2));
            corners.subtract(new Area(new RoundRectangle2D.Float(
                r.x + INSET, r.y, r.width - INSET * 2, r.height, ARC, ARC)));
            g2.setColor(bg);
            g2.fill(corners);
        }

        // Paint rounded drop highlight on folder rows
        DropLocation dl = getDropLocation();
        if( dl != null && browserPanel != null )
        {
            int viewRow = dl.getRow();
            if( isFolder(viewRow) )
            {
                Rectangle r = getFullRowRect(viewRow);

                // Erase default highlight
                g2.setColor(bg);
                g2.fillRect(r.x, r.y, r.width, r.height);

                // Paint rounded drop indicator
                Color selColor = getSelectionBackground();
                g2.setColor(selColor);
                g2.fillRoundRect(r.x + INSET, r.y, r.width - INSET * 2, r.height, ARC, ARC);

                // Repaint cell content on top with selection foreground
                for( int col = 0; col < getColumnCount(); col++ )
                {
                    Rectangle cellRect = getCellRect(viewRow, col, true);
                    TableCellRenderer cellRenderer = getCellRenderer(viewRow, col);
                    Component c = prepareRenderer(cellRenderer, viewRow, col);
                    c.setBackground(new Color(0, 0, 0, 0));
                    c.setForeground(getSelectionForeground());
                    if( c instanceof JComponent ) ((JComponent) c).setOpaque(false);
                    SwingUtilities.paintComponent(g2, c, this,
                        cellRect.x, cellRect.y, cellRect.width, cellRect.height);
                }
            }
        }

        g2.dispose();
    }

    /**
     * Suppress default drop row highlight for non-folder rows.
     */
    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column)
    {
        Component c = super.prepareRenderer(renderer, row, column);

        DropLocation dl = getDropLocation();
        if( dl != null && dl.getRow() == row && !isFolder(row) )
        {
            if( isRowSelected(row) )
            {
                c.setBackground(getSelectionBackground());
                c.setForeground(getSelectionForeground());
            } else {
                c.setBackground(getBackground());
                c.setForeground(getForeground());
            }
        }

        return c;
    }

    public boolean isShowingDropBorder()
    {
        DropLocation dl = getDropLocation();
        return dl != null && browserPanel != null && !isFolder(dl.getRow());
    }
}
