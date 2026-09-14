//
//  GFileVetoerPanelController.java
//  Gideon
//
//  Created by David on 10/28/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.permissions;

import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.core.GGideonPanelController;
import com.gideonsoftworks.gcore.*;
import javax.swing.*;
import java.awt.*;
import java.lang.reflect.*;
import javax.swing.table.*;
import javax.swing.event.*;

public class GFileVetoerPanelController extends GGideonPanelController implements TableModel
{

    // Declare beans.
    private JTable          fileTable=null;
    private GFileItem[]    gfvItems=null;


    public  GFileVetoerPanelController()
    {
        // Build the panel by hand (replaces GFileVetoerPanelController.gui_xml)
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(3, 3, 4, 3));
        panel.setPreferredSize(new Dimension(255, 207));
        panel.setMinimumSize(new Dimension(255, 207));

        fileTable = new JTable();
        fileTable.setModel(this);
	fileTable.setIntercellSpacing( new Dimension( 0, 0 ) );

        JScrollPane scrollPane = new JScrollPane(fileTable,
            JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
            JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        panel.add(scrollPane, BorderLayout.CENTER);

        this.setPanel( panel );
    }

    public  void setVetoableFiles( GFileItem[] fileItems )
    {
        gfvItems = fileItems;
        fileTable.tableChanged(new TableModelEvent(this));
    }

    public  GFileItem[] getNonVetoedFiles()
    {

        return gfvItems;

    }

/*
*
*   File Table Methods
*
*/

/**
 * Returns the number of available columns.
 */
public int getColumnCount(){ return 2; }

public int getRowCount()
{

    if( gfvItems==null ) return 0;

    return  Array.getLength(gfvItems);

}

/**
 * Returns the name for column number <code>column</code>.
 */
public String getColumnName(int column)
{

    String  value;

    if( column == 0 ){

        value = GLocalized.NAME_COLUMN_TITLE;

    }else if(column==1){

        value = GLocalized.PERMISSIONS_COLUMN_TITLE;

    }else{

        value = "!UNDEFINED!";
    }

    return value;

}

/**
 * Returns the type for column number <code>column</code>.
 */
public Class getColumnClass(int column)
{

    Class  value;

    if(column==0){

        value = String.class;

    }else
    {

        value = String.class;

    }

    return value;

}

/**
 * Returns the value to be displayed for node <code>node</code>,
 * at column number <code>column</code>.
 */
public Object getValueAt(int row, int column)
{
    GFileItem   cItem = (GFileItem) Array.get(gfvItems, row);

    if( column==0 )
    {
        return cItem.getName();
    }else{
        return cItem.getPermissions().getSymbolicValue();
    }

}

/**
 * Indicates whether the the value for node <code>node</code>,
 * at column number <code>column</code> is editable.
 */
public boolean isCellEditable(int row, int column){ return false; }

/**
 * Sets the value for node <code>node</code>,
 * at column number <code>column</code>.
 */
public void setValueAt(Object aValue, int row, int column){}

public void addTableModelListener(TableModelListener l) {

}

public void removeTableModelListener(TableModelListener l) {

}

}