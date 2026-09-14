//
//  GTransferFilterListPanel.java
//  GSFTP
//
//  Created by David J. Clark on 2/19/05.
//  Copyright 2005 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.core;

import com.gideonsoftworks.gideon.toolbar.GToolbarItem;
import com.gideonsoftworks.gideon.toolbar.GToolbar;
import com.gideonsoftworks.gideon.core.GFrame;
import com.gideonsoftworks.gideon.core.GFrameManager;
import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.core.GideonConfiguration;
import com.gideonsoftworks.gideon.actions.GUIAction;
import java.lang.reflect.Array;
import com.gideonsoftworks.gja.*;
import com.gideonsoftworks.gideon.widgets.GTable;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.*;
import java.util.*;

public class GTransferFilterListPanel extends GFrame implements ActionListener, TableModel {
    // Declare beans.
    private JButton								enableButton;
    private JLabel								tfStatusField;
    private GTable	tfTable;
	private	GToolbarItem						addToolbarItem = null;
	private	GToolbarItem						removeToolbarItem = null;
	private	String[] value = {"Select Type...", "Contains", "Matches", "Starts With", "Ends With"};
	
	// TableModel stuff
	private java.util.List						treeModelListeners = new java.util.concurrent.CopyOnWriteArrayList();
    
	
	// This renderer extends a component. It is used each time a
    // cell must be displayed.
    private class GComboRenderer extends JComboBox implements TableCellRenderer {
        public GComboRenderer(Object[] items) {
            super(items);
        }
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setSelectedItem(getItemAt(((Integer)value).intValue()));
            return this;
        }
    }
	
	// Constructor.
    public GTransferFilterListPanel(GFrameManager fm) {
        // This dialog is modal.
        super(fm);
		
		JPanel	masterPanel = new JPanel( new BorderLayout() );

		// Build the panel by hand (replaces GTransferFilterListPanel.gui_xml)
		JPanel panel = new JPanel(new BorderLayout());
		tfTable = new GTable();
		tfTable.setFocusable(false);
		JScrollPane scrollPane = new JScrollPane(tfTable,
		    JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
		    JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		panel.add(scrollPane, BorderLayout.CENTER);

		tfStatusField = new JLabel();

		masterPanel.add(panel, BorderLayout.CENTER);

		JPanel  toolbarArea = new JPanel(new BorderLayout());
        toolbarArea.setFocusable(false);
        toolbarArea.add( toolbar(),  BorderLayout.PAGE_START );
        toolbarArea.add( new JSeparator(),  BorderLayout.PAGE_END );
        masterPanel.add(toolbarArea, BorderLayout.PAGE_START);

        setContentPane(masterPanel);
		
		// configure us as table model
		tfTable.setModel((TableModel)this);
		tfTable.setRowHeight(25);
		tfTable.setShowGrid( false );
        tfTable.setShowHorizontalLines( false );
        tfTable.setShowVerticalLines( false );
		tfTable.setIntercellSpacing( new Dimension( 0, 0 ) );
		
		// size columns
		TableColumn myColumn = tfTable.getColumnModel().getColumn(0);
		myColumn.setPreferredWidth(10);
		myColumn = tfTable.getColumnModel().getColumn(1);
		myColumn.setPreferredWidth(100);
		
        // Add dialog as ActionListener.
        //enableButton.addActionListener(this);
		
		setTitle(GLocalized.TRANSFERFILTERTITLE);
		
        pack();

		// configure table
		myColumn = tfTable.getColumnModel().getColumn(2);		
		JComboBox box = new JComboBox(value);
        box.setSelectedIndex(0);
        myColumn.setCellEditor(new DefaultCellEditor(box));
		myColumn.setCellRenderer(new GComboRenderer(value));

    }

    /**
     * Invoke the onXxx() action handlers.
     * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
     */
    public void actionPerformed(ActionEvent e) {
        int		i = 0;
		Object	s = e.getSource();
		
		if( s.equals(removeToolbarItem) )
		{
			if( GideonConfiguration.TRANSFERFILTERUIDEBUG ) System.out.println("User clicked remove");
			
			GTransferFilter[]	tfArray = getSelection();
			
			if( tfArray == null ) return;
			
			// handle deleteing the items
			
			for( i = 0; i<Array.getLength(tfArray) ; i++ )
			{
			
				GTransferFilter	tFilter	= (GTransferFilter)Array.get( tfArray, i );
				
				
				GTransferFilterManager.removeFilter(tFilter);
			
			}
			
			refreshTable();
			
		}else if(s.equals(addToolbarItem))
		{
			if( GideonConfiguration.TRANSFERFILTERUIDEBUG ) System.out.println("User clicked add");
			
			GTransferFilter	tf = new GTransferFilter( Boolean.valueOf(true), "New Filter", Integer.valueOf(GTransferFilter.TF_NONE), "." );
			GTransferFilterManager.addFilter(tf);
			
			refreshTable();
			
		}else if(s.equals(enableButton))
		{
			if( GideonConfiguration.TRANSFERFILTERUIDEBUG ) System.out.println("User clicked enable");
		}
		
		
    }
	
	private	void refreshTable()
	{
		tfTable.tableChanged(new TableModelEvent(tfTable.getModel()));
	}

    public GToolbar  toolbar()
    {
        GToolbar toolBar = new GToolbar("Transfer Filter Toolbar");
        
        GUIAction action = new GUIAction(GLocalized.ADD_TF, GLocalized.SIDEBAR_ICON, GLocalized.ADD_TF_TOOLTIP);
        addToolbarItem = new GToolbarItem(action);
        addToolbarItem.setIcon(GLocalized.ADD_SVG_ICON);   // SVG, foreground-tinted; FlatSVGIcon auto-handles disabled
        if( GLocalized.onMacOSX )
        {
            addToolbarItem.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 12));
        }
        addToolbarItem.addActionListener(this);
        toolBar.add(addToolbarItem);

        action = new GUIAction(GLocalized.DEL_TF, GLocalized.DELETE_ICON, GLocalized.DEL_TF_TOOLTIP );
        removeToolbarItem = new GToolbarItem(action);
        removeToolbarItem.setIcon(GLocalized.DELETE_SVG_ICON);
        if( GLocalized.onMacOSX )
        {
            removeToolbarItem.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 12));
        }
        removeToolbarItem.addActionListener(this);
        toolBar.add(removeToolbarItem);

        // Delete is enabled only when a filter row is selected.
        removeToolbarItem.setEnabled(false);
        tfTable.getSelectionModel().addListSelectionListener(
            e -> removeToolbarItem.setEnabled(tfTable.getSelectedRow() >= 0));

        return toolBar;
        
    }

/*
*
*
*
*
*
*/

public GTransferFilter[]  getSelection()
{
    
    GTransferFilter[]		transferFilterArray=null;
    int						startIndex=0;
    int						endIndex=0;
    GTransferFilterManager	tManager=null;
    int						j=0;
    int						i=0;
    int						length=0;
    
    if(tfTable==null) return null;
    
    // get the file tables list selection model
    ListSelectionModel  selModel = tfTable.getSelectionModel();
    
    // if we have an empty selection - return null.
    if( selModel.isSelectionEmpty() ) return null;
    
    // get the starting and ending selection
    startIndex = selModel.getMinSelectionIndex();
    endIndex = selModel.getMaxSelectionIndex();


    if( startIndex==endIndex )
    {
		GTransferFilter		cTransferFilter;
		transferFilterArray = (GTransferFilter[])Array.newInstance(GTransferFilter.class, 1);
		cTransferFilter = tManager.getChild(startIndex);
		Array.set(transferFilterArray,0,cTransferFilter);
		return transferFilterArray;
	}
    
	// calculate multiple selection length
	j=0;
	length=0;
	for( i = startIndex ; i <= endIndex ; i++ )
	{

		if (selModel.isSelectedIndex(i))
		{
			length++;
		}else{
		}

	}
	
	transferFilterArray = (GTransferFilter[])Array.newInstance(GTransferFilter.class, length);
	
	
	// generate an array of items
	j=0;
	for( i = startIndex ; i <= endIndex ; i++ )
	{

		if (selModel.isSelectedIndex(i))
		{
			GTransferFilter   cTransferFilter = null;
			cTransferFilter = GTransferFilterManager.getChild(i);
			Array.set(transferFilterArray,j++,cTransferFilter);
		}else{
		}

	}
	
    return transferFilterArray;

}


/*
*
* TABLE MODEL
*
*
*/

/*
*   File Table Methods
*
*/

/**
 * Returns the number of available columns.
 */
public int getColumnCount(){ return 4; }

public int getRowCount()
{ 

    return  GTransferFilterManager.getChildCount();

}

/**
 * Returns the name for column number <code>column</code>.
 */
public String getColumnName(int column)
{ 

    String  value = null;
    
    if( column == 0 ){
        
        value = " ";
        
    }else if(column==1){
        
        value = GLocalized.NAME_COLUMN_TITLE;
        
    }else if( column==2 )
    {
        
        value = "Type";
        
    }else if( column==3 )
    {
        
        value = "Mask";
        
    }
	   
    return value;

}

/**
 * Returns the type for column number <code>column</code>.
 */
public Class getColumnClass(int column)
{ 
	
	if( column == 0 )
	{
		return Boolean.class;
	}else if( column == 2 )
	{
		return Integer.class;
	}else{
		return String.class;
	}
	
}

/**
 * Returns the value to be displayed for node <code>node</code>, 
 * at column number <code>column</code>.
 */
public Object getValueAt(int row, int column)
{

	GTransferFilter	tf = GTransferFilterManager.getChild(row);

    if( column == 0 ) return tf.getEnabled();
    else if( column == 1 ) return tf.getName();
	else if( column == 2 ) return tf.getType();
	else if( column == 3 ) return tf.getMask();
	
	return "-";
    
}

/**
 * Indicates whether the the value for node <code>node</code>, 
 * at column number <code>column</code> is editable.
 */
public boolean isCellEditable(int row, int column){ return true; }

/**
 * Sets the value for node <code>node</code>, 
 * at column number <code>column</code>.
 */
public void setValueAt(Object aValue, int row, int column)
{

	GTransferFilter	tf = GTransferFilterManager.getChild(row);

    if( column == 0 ) tf.setEnabled((Boolean)aValue);
    else if( column == 1 ) tf.setName((String)aValue);
	else if( column == 2 ) 
	{
		tf.setType( Integer.valueOf( indexForObject(aValue) ) );
	}
	else if( column == 3 ) tf.setMask((String)aValue);


}

private int indexForObject( Object newValue )
{

	int	i		= 0;
	int	count	= Array.getLength(value);
	
	for( i = 0 ; i < count ; i++ )
	{
		Object ci = Array.get(value, i);
		
		if( ci.equals( newValue ) )
		{
			return i;
		}
	
	}
	
	return -1;
	
}
        
public void addTableModelListener(TableModelListener l) {
    treeModelListeners.add(l);
}

public void removeTableModelListener(TableModelListener l) {
    treeModelListeners.remove(l);
}

	
}
