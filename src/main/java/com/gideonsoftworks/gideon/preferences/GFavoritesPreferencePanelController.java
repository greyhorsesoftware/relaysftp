//
//  GSecurityPreferencePanelController.java
//  GSFTP
//
//  Created by David on 11/21/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.preferences;

import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.core.GideonConfiguration;
import com.gideonsoftworks.gideon.core.GGideonPanelController;
import com.gideonsoftworks.gideon.bookmarks.GBookmarkPanelInterface;
import java.io.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import com.gideonsoftworks.gcore.*;
import com.gideonsoftworks.gideon.widgets.*;
import com.gideonsoftworks.gdo.*;

public class GFavoritesPreferencePanelController extends GGideonPanelController implements ActionListener, GBookmarkPanelInterface
{

    // Declare beans.
    private GTable                  favoritesTable;
    private JButton                 chooseButton;
    private JButton                 removeButton;
    private GDataTableController    tableController;
    private GBookmark               currentBookmark;

    public  GFavoritesPreferencePanelController()
    {
        // do some magic setup
        String[] columnNames = {GLocalized.NAME_COLUMN_TITLE, GLocalized.PATH};

        // Build the panel layout by hand (replaces GFavoritesPreferencePanel.gui_xml)
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 14, 10, 14));
        panel.setPreferredSize(new Dimension(390, 224));
        panel.setMinimumSize(new Dimension(390, 224));

        // Table in scroll pane
        favoritesTable = new GTable();
        JScrollPane scrollPane = new JScrollPane(favoritesTable,
            JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
            JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);

        panel.add(scrollPane, BorderLayout.CENTER);

        // Button row below table
        chooseButton = new JButton("Choose...");
        removeButton = new JButton("Remove");

        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.add(chooseButton, BorderLayout.WEST);
        buttonPanel.add(removeButton, BorderLayout.EAST);

        panel.add(buttonPanel, BorderLayout.SOUTH);

        // localize
        chooseButton.setText(GLocalized.CHOOSE);
        removeButton.setText(GLocalized.TB_REMOVE);

        // wire us up
        chooseButton.addActionListener(this);
        removeButton.addActionListener(this);

        // create our little table controller - we will assign the data array later
        tableController = new GDataTableController(favoritesTable, columnNames, null);

        // show folder icon in Name column
        favoritesTable.getColumnModel().getColumn(0).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setIcon(GLocalized.SMALL_FOLDER_ICON);
                return this;
            }
        });

        //
        setPanel(panel);

    }

    // this if for the local preferences area
    public void open()
    {

        GBookmark dummyBookmark = new GBookmark("local", "something", null, null, 0, GBookmark.G_FILE_PROTOCOL );

        // grab our standard things
        GDataArray  nDA = GBookmarkManager.getSharedLocalFavoritePaths();
        dummyBookmark.setFavoritePaths( nDA );
        //

        populateForBookmark(dummyBookmark);

    }

    public void save()
    {
    }

    // this is called from the bookmark manager
    public void populateForBookmark(GBookmark bm)
    {

        if( bm == null ) return;

        // if we are editing a remote bookmark - choose makes no sense.
        if( !bm.isLocal() )
        {
            chooseButton.setText(GLocalized.ADD);
        }

        // wire our table up to the gideon data objects stuff
        //tableController = new GDataTableController(favoritesTable, columnNames, bm.getFavoritePaths());
        tableController.setDataArray(bm.getFavoritePaths());

        System.out.println("Favorite Path ID : " + bm.getFavoritePaths());

        currentBookmark = bm;

    }

    public void saveCurrentBookmark()
    {
    }

    public void actionPerformed(ActionEvent e )
    {

        GBookmarkFavoritePath   favItem;

        if( e.getSource().equals(chooseButton) )
        {

            if( currentBookmark.isLocal() )
            {
                String dirPath = getFolderPath();
                if( dirPath == null ) return;
                favItem = new GBookmarkFavoritePath( GStringUtilities.getLastPathComponent(dirPath), dirPath );
                if( GideonConfiguration.GFAVORITESUI ) System.out.println( "User selected : " + dirPath );
            }else{
                favItem = new GBookmarkFavoritePath( GLocalized.NAME_COLUMN_TITLE, GLocalized.ENTERCOMPLETEPATH );
            }

            currentBookmark.addFavoritePath(favItem);
        }else{
            if( GideonConfiguration.GFAVORITESUI ) System.out.println("Remove clicked");
            GDataItemInterface[]  nDataItems = tableController.getSelected();
            currentBookmark.removeFavoritePaths((GDataItemInterface[])nDataItems);
        }
    }

}
