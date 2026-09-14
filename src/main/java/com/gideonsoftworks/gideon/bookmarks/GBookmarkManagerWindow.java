//
//  GBookmarkManagerWindow.java
//  Gideon
//
//  Created by David on 10/18/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.bookmarks;

import com.gideonsoftworks.gideon.toolbar.GToolbarItem;
import com.gideonsoftworks.gideon.preferences.GFavoritesPreferencePanelController;
import com.gideonsoftworks.gideon.toolbar.GToolbar;
import com.gideonsoftworks.gideon.core.GFrame;
import com.gideonsoftworks.gideon.core.GFrameManager;
import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.actions.GUIAction;
import com.gideonsoftworks.gcore.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.table.*;
import javax.swing.event.*;
import java.io.*;
import java.util.ArrayList;


public class GBookmarkManagerWindow extends GFrame implements ActionListener, TableModel, ListSelectionListener, GBookmarkChangeListenerInterface
{
    
    private GBookmarkGeneralPanelController bmwBookmarkInfo=null;
    private GFavoritesPreferencePanelController gpwFavoritesPanelController=null;
    private JTable                          bmwBookmarkList=null;
    private JButton                         deleteBookmark=null;
    private JButton                         newBookmark=null;
    
    public  GBookmarkManagerWindow( GFrameManager fm )
    {
        
        super( fm );
        
        Container content = this.getContentPane();

        bmwBookmarkInfo = new GBookmarkGeneralPanelController();
        bmwBookmarkInfo.setBookmarkWindow(this);
        
        gpwFavoritesPanelController = new GFavoritesPreferencePanelController();
        
        JPanel masterPanel = new JPanel(new BorderLayout());
        masterPanel.setBorder( BorderFactory.createEmptyBorder(10,10,10,10) );
        
        bmwBookmarkList = new JTable();
        JScrollPane scrollPane = new JScrollPane();
        scrollPane.getViewport().add( bmwBookmarkList, BorderLayout.CENTER );
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        scrollPane.setPreferredSize(new Dimension(200,200));
        
        // createa  bookmark tab area
        JTabbedPane  tabbedView = new JTabbedPane();
        tabbedView.add( bmwBookmarkInfo.getPanel(), GLocalized.GENERALTABTITLE );
        tabbedView.add( gpwFavoritesPanelController.getPanel(), GLocalized.FAVORITEFOLDERTABTITLE );
        
        JSplitPane parentPanel = new JSplitPane( JSplitPane.HORIZONTAL_SPLIT, scrollPane, tabbedView );
        parentPanel.setContinuousLayout(true);
        parentPanel.setDividerSize(0);
        parentPanel.setBorder(BorderFactory.createEmptyBorder());
        masterPanel.add(parentPanel, BorderLayout.CENTER);
        
        JPanel  toolbarArea = new JPanel(new BorderLayout());
        toolbarArea.setFocusable(false);
        toolbarArea.add( this.bookmarkManagerToolBar(),  BorderLayout.PAGE_START );
        toolbarArea.add( new JSeparator(),  BorderLayout.PAGE_END );
        content.add(toolbarArea, BorderLayout.PAGE_START);
        content.add(masterPanel,BorderLayout.CENTER );
                    
        bmwBookmarkList.setModel((TableModel)this);
        bmwBookmarkList.getSelectionModel().addListSelectionListener((ListSelectionListener)this);
        bmwBookmarkList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION );
        bmwBookmarkList.setShowGrid( false );
        bmwBookmarkList.setShowHorizontalLines( false );
        bmwBookmarkList.setShowVerticalLines( false );
        
        // no resize and sexy title
        this.setResizable(false);    
        this.setTitle(GLocalized.BOOKMARKMANAGERNAME);
        this.pack();
        
        // center it
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        int x = (int)(dim.getWidth() - getWidth())/2;
        int y = (int)(dim.getHeight() - getHeight())/2;
        setLocation(x,y);
        
        // add ourselves as a listener
        GBookmarkManager.addStatusListener(this);

    }
    
    public void bm_bookmarksChanged()
    {
        this.refreshTable();
    }
    
    public void bm_bookmarkChanged(GBookmark bm){}
    
    public boolean hasToolbar()
    {
        return false;
    }
    
    public void show()
    {
    
        super.show();
        
        // call open so the ui is inited
        gpwFavoritesPanelController.open();
        
        if( GBookmarkManager.getBookmarkCount() != 0 )
        {
            selectBookmarkAtIndex(0); 
        }else{
            bmwBookmarkInfo.populateForBookmark(null);
            gpwFavoritesPanelController.populateForBookmark(null);
        }
        
    }
    
    public  void    valueChanged( ListSelectionEvent e )
    {
        if( e.getValueIsAdjusting() ) return;
        
         // get the file tables list selection model
        ListSelectionModel  selModel = bmwBookmarkList.getSelectionModel();

        // if the selection is empty - handle disabling/enabling toolbar buttons.
        if( selModel.isSelectionEmpty() )
        {
            deleteBookmark.setEnabled(false);
            return;
        }else{
            deleteBookmark.setEnabled(true);
        }
        
        bmwBookmarkInfo.saveCurrentBookmark();
        
        // get the starting and ending selection
        int startIndex = selModel.getMinSelectionIndex();
        
        GBookmark bm = GBookmarkManager.getBookmark(startIndex);
        bmwBookmarkInfo.populateForBookmark(bm);
        gpwFavoritesPanelController.populateForBookmark(bm);
        
    }
    
    public  void    actionPerformed( ActionEvent e )
    {
    
        Object  source = e.getSource();
    
        if( source.equals(deleteBookmark) )
        {
        
            // get the file tables list selection model
            ListSelectionModel  selModel = bmwBookmarkList.getSelectionModel();

            // if the selection is empty - handle disabling/enabling toolbar buttons.
            if( selModel.isSelectionEmpty() )
            {
                return;
            }
        
            // get the starting and ending selection
            int startIndex = selModel.getMinSelectionIndex();
            
            GBookmarkManager.removeBookmark(GBookmarkManager.getBookmark(startIndex));
            
            if( GBookmarkManager.hasBookmarks() )
            {
                this.selectBookmarkAtIndex( startIndex==0 ? 0 : startIndex-1);
            }else{
                bmwBookmarkInfo.populateForBookmark(null);
            }
            
        }else if( source.equals(newBookmark) )
        {
            
            bmwBookmarkInfo.saveCurrentBookmark();
            
            GBookmark   newBM = new GBookmark( "Untitled Bookmark", null, null, null, null, 0, GBookmark.G_FTP_PROTOCOL );
            
            int index = GBookmarkManager.addBookmark(newBM);
            selectBookmarkAtIndex( index );
        }
    
        this.refreshTable();
        
    }
    
    /*
    *   File Table Methods
    *
    */

    /**
     * Returns the number of available columns.
     */
    public int getColumnCount(){ return 1; }

    public int getRowCount()
    { 

        return GBookmarkManager.getBookmarkCount();

    }

    /**
     * Returns the name for column number <code>column</code>.
     */
    public String getColumnName(int column)
    { 
        
        return GLocalized.NAME_COLUMN_TITLE;

    }

    /**
     * Returns the type for column number <code>column</code>.
     */
    public Class getColumnClass(int column)
    { 

        return String.class;

    }

    /**
     * Returns the value to be displayed for node <code>node</code>, 
     * at column number <code>column</code>.
     */
    public Object getValueAt(int row, int column)
    {

        return GBookmarkManager.getBookmark(row);

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
        GBookmark bm = GBookmarkManager.getBookmark(row);
        
        if( bm != null )
        {
            bm.setName((String)aValue);
        }

    }
            
    private ArrayList<TableModelListener> tableModelListeners = new ArrayList<TableModelListener>();

    public void addTableModelListener(TableModelListener l) {
        if( l != null ) tableModelListeners.add(l);
    }

    public void removeTableModelListener(TableModelListener l) {
        tableModelListeners.remove(l);
    } 

    /*
    *
    *  END File Table Methods
    *
    */
    
    /*
    *
    *  Private Methods ( ssssh )
    *
    */
    
    private void    refreshTable()
    {
        TableModelEvent evt = new TableModelEvent(this);
        for( int i = 0; i < tableModelListeners.size(); i++ )
        {
            tableModelListeners.get(i).tableChanged(evt);
        }
        bmwBookmarkList.tableChanged(evt);
    }
    
    private void selectBookmarkAtIndex( int index )
    {
    
        GBookmark bm = GBookmarkManager.getBookmark(index);
        
        // do the mad populating
        bmwBookmarkInfo.populateForBookmark(bm);
        gpwFavoritesPanelController.populateForBookmark(bm);
        
        bmwBookmarkList.getSelectionModel().setLeadSelectionIndex(index);
        
        deleteBookmark.setEnabled(true);
    
    }
        
    private  GToolbar bookmarkManagerToolBar()
    {
            
        final boolean isMacOSX = GLocalized.onMacOSX;
            
        GToolbar toolBar = new GToolbar("Gideon Bookmark Manager TB");
            
        GUIAction   action = new GUIAction(GLocalized.TB_NEWBOOKMARK, GLocalized.NEWBM_ICON, GLocalized.NEWBM_TOOLTIP);
        newBookmark = new GToolbarItem(action);
        if( isMacOSX ) 
        {
            newBookmark.setPressedIcon(GLocalized.NEWBM_ICON_SELECTED);
            newBookmark.setDisabledIcon(GLocalized.NEWBM_ICON_DISABLED);
        }
        newBookmark.addActionListener(this);
        toolBar.add(newBookmark);
          
        action = new GUIAction( GLocalized.TB_DELETEBOOKMARK, GLocalized.DELBM_ICON, GLocalized.DELBM_TOOLTIP );
        deleteBookmark = new GToolbarItem(action);
        if( isMacOSX ) 
        {
            deleteBookmark.setPressedIcon(GLocalized.DELBM_ICON_SELECTED);
            deleteBookmark.setDisabledIcon(GLocalized.DELBM_ICON_DISABLED);
        }
        
        deleteBookmark.addActionListener(this);
        toolBar.add(deleteBookmark);

        return toolBar;
    
    }
    
    public boolean windowWillClose()
    {
        bmwBookmarkInfo.saveCurrentBookmark();
        return true;
    }

}