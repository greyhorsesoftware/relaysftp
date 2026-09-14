//
//  GDataTableController.java
//  GSFTP
//
//  Created by David on 9/10/05.
//  Copyright 2005 Grey Horse Software All rights reserved.
//
package com.gideonsoftworks.gdo;

import com.gideonsoftworks.gideon.core.GideonConfiguration;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.lang.reflect.Array;

/**
*
*   GDataTableController
*
*   Implements a GDO compatible table controller/manager.
*
*/

public class GDataTableController implements TableModel, GDataArrayChangeInterface
{
    private JTable      tableWeControl;
    private String[]    myColumnNames;
    private GDataArray  modelCollection;
    private ArrayList<TableModelListener> tableModelListeners = new ArrayList<TableModelListener>();
    
    /**
    *
    *   GDataTableController
    *
    *   Implements a GDO compatible table controller/manager.
    *
    *   @param table table to manage
    *   @param columnNames column names to use in table
    *   @param mCollection GDataArray that provides data items to work with
    *
    */
    public GDataTableController( final JTable table, final String[] columnNames, final GDataArray mCollection )
    {
    
        super();
        
        if( table == null )
        {
            throw new NullPointerException("GDataTableController::constructor - table was null");
        }

        if (GideonConfiguration.GDODEBUG) 
        {
            table.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    printDebugData(table);
                }
            });
        }
        
        tableWeControl = table;
        myColumnNames = columnNames;
        
        // make us the data model
        table.setModel((TableModel)this);
        
        setDataArray( modelCollection );

    }
    
    /**
     * callback from GDataArray we are watching
     * 
     * @param mCollection GDataArray to monitor and display         
     *
     */
    
    public void setDataArray( GDataArray mCollection )
    {

        // save our model collection
        modelCollection = mCollection;

        if( modelCollection != null )
        {
            // let the model collection know about us
            // XXX - shouldn't this be a listener type interface - allow N tables to view the same data
            modelCollection.setViewController(this);
            // add ourselves as listeners for events
            modelCollection.addStatusListener(this);
            // update the table
            dataItemsChanged();
        }
    }
    
    /**
     * callback from GDataArray we are watching
     *          
     */
    public void dataItemsChanged()
    {
        update( null, GDataArray.GDA_ITEM_ADDED );
    }
    
    /**
     * Returns all selected GDataItems in view.
     *
     *  Takes into account
     *          - multiple selection
     *          - non continuous selections
     *          
     */
    public GDataItemInterface[] getSelected()
    {
         
        GDataItemInterface[] dataItemArray=null;
        int                  startIndex=0;
        int                  endIndex=0;
        GDataItemInterface   currentFI=null;
        int                  j=0;
        int                  i=0; 
        int                  length=0;

        if( tableWeControl.getSelectedRowCount() == 0 )
        {
            return null;
        }

        if(tableWeControl==null) return null;
        
        // get the file tables list selection model
        ListSelectionModel  selModel = tableWeControl.getSelectionModel();
        
        // if we have an empty selection - return null.
        if( selModel.isSelectionEmpty() ) return null;
        
        // get the starting and ending selection
        startIndex = selModel.getMinSelectionIndex();
        endIndex = selModel.getMaxSelectionIndex();

        if( modelCollection == null ) return null;
            
        if( GideonConfiguration.GDODEBUG ) System.out.println("Start : " + startIndex);
        if( GideonConfiguration.GDODEBUG ) System.out.println("End : " + endIndex);

        if( startIndex==endIndex )
        {
                GDataItemInterface       cDataItem;
                dataItemArray   = (GDataItemInterface[])Array.newInstance(GDataItemInterface.class, 1);
                cDataItem = modelCollection.getItemAtIndex(startIndex);
                Array.set(dataItemArray,0,cDataItem);
                return dataItemArray;
        }
        
        // calculate multiple selection length
        j=0;
        length=0;
        for( i = startIndex ; i <= endIndex ; i++ )
        {
                if( GideonConfiguration.GDODEBUG ) System.out.println("Testing index : " + i );

                if (selModel.isSelectedIndex(i))
                {
                        if( GideonConfiguration.GDODEBUG ) System.out.println("Selected index : " + i );
                        length++;
                }else{
                        if( GideonConfiguration.GDODEBUG ) System.out.println("Index not selected : " + i );
                }

        }
        
        dataItemArray = (GDataItemInterface[])Array.newInstance(GDataItemInterface.class, length);

        // generate an array of items
        j=0;
        for( i = startIndex ; i <= endIndex ; i++ )
        {
                if (selModel.isSelectedIndex(i))
                {
                        if( GideonConfiguration.GDODEBUG ) System.out.println("Selected index : " + i );
                        GDataItemInterface   cDataItem = null;
                        cDataItem = modelCollection.getItemAtIndex(i);
                        Array.set(dataItemArray,j++,cDataItem);
                }else{
                        if( GideonConfiguration.GDODEBUG ) System.out.println("Index not selected : " + i );
                }

        }
            
        return dataItemArray;
        
    }
    
    /**
     * Tells the view to update
     *
     *  @param nItem GDataItem that has been either removed or added.
     *  @param stateFlag Type of action on GDataItem - GDataArray.GDA_ITEM_ADDED or GDataArray.GDA_ITEM_REMOVED
     */
    public void update( GDataItemInterface nItem, int stateFlag )
    {
        TableModelEvent evt = new TableModelEvent((TableModel)this);
        // notify registered listeners
        for( int i = 0; i < tableModelListeners.size(); i++ )
        {
            tableModelListeners.get(i).tableChanged(evt);
        }
        // also tell the table directly
        tableWeControl.tableChanged(evt);
    }
       
    private String  getKeyForColumn( int column )
    {   
        HashMap keyMappings = modelCollection.getIndexMapping();
        
        if( keyMappings == null )
        {
            throw new NullPointerException("modelCollection returned null index mapping");
        }
        
        
        return (String)keyMappings.get(Integer.valueOf(column).toString());
    }
    
    private void printDebugData(JTable table) {
        int numRows = table.getRowCount();
        int numCols = table.getColumnCount();
        javax.swing.table.TableModel model = table.getModel();

        System.out.println("Value of data: ");
        for (int i=0; i < numRows; i++) {
            System.out.print("    row " + i + ":");
            for (int j=0; j < numCols; j++) {
                System.out.print("  " + model.getValueAt(i, j));
            }
            System.out.println();
        }
    }

        
    /**
    *
    *   Table Data Model Methods
    *
    */

    public int getColumnCount()
    { 
    
        if( GideonConfiguration.GDODEBUG ) System.out.println("GDataTableController::getColumnCount() called");

        int kCount = Array.getLength(myColumnNames);
        
        if( GideonConfiguration.GDODEBUG ) System.out.println("GDataTableController::getColumnCount() called : " + kCount);

        return kCount;
        
    }

    public int getRowCount()
    { 
    
        if( modelCollection == null ) return 0;
    
        int rCount = modelCollection.getItemCount();
    
        if( GideonConfiguration.GDODEBUG ) System.out.println("GDataTableController::getRowCount() called : " + rCount);

        return rCount;
    }

    public String getColumnName(final int column)
    { 
        if( GideonConfiguration.GDODEBUG ) System.out.println("GDataTableController::getColumnName() called");

        return (String)Array.get(myColumnNames, column);
    }

    public Class getColumnClass(final int column)
    { 
        if( GideonConfiguration.GDODEBUG ) System.out.println("GDataTableController::getColumnClass() called");

        // use the key for the first item
        return String.class;
    
    }
    
    public Object getValueAt(final int row, final int column)
    {
        
        if( GideonConfiguration.GDODEBUG ) System.out.println("GDataTableController::getValueAt() called");
        
        GDataItemInterface  item = modelCollection.getItemAtIndex(row);
        
        if( item == null )
        {
            return null;
        }
        
        String key = getKeyForColumn(column);
        
        if( key == null )
        {
            throw new NullPointerException("getKeyForColumn return a null KEY");
        }
        
        return item.getObjectForKey(key);
        
    }

    public boolean isCellEditable(final int row, final int column)
    { 
        
        GDataItemInterface  item = modelCollection.getItemAtIndex(row);
        
        if( item == null )
        {
            return false;
        }
        
        String key = getKeyForColumn(column); 
        
        return item.allowChangeValueForKey(key);
        
    }

    public void setValueAt(final Object aValue, final int row, final int column)
    {
            
        GDataItemInterface  item = modelCollection.getItemAtIndex(row);
        
        if( item == null )
        {
            return;
        }
        
        String key = getKeyForColumn(column); 
        
        item.setObjectForKey(key, aValue);
            
    }

    public void addTableModelListener(final TableModelListener l)
    {
        if( l != null ) tableModelListeners.add(l);
    }

    public void removeTableModelListener(final TableModelListener l)
    {
        tableModelListeners.remove(l);
    }
 
}
