//
//  GFileItemViewer.java
//  Gideon
//
//  Created by David on 10/2/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.browser;

import com.gideonsoftworks.gideon.preferences.GSFTPPreferences;
import com.gideonsoftworks.gideon.core.GFrame;
import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.core.GideonConfiguration;
import com.gideonsoftworks.gideon.core.GConnectionPanelInterface;
import com.gideonsoftworks.gideon.core.GFTPConnectionPanel;
import com.gideonsoftworks.gideon.core.GS3ConnectionPanel;
import com.gideonsoftworks.gideon.widgets.*;
import com.gideonsoftworks.gideon.core.GGideonPanelController;
import com.gideonsoftworks.gideon.core.GPopupMenuProviderInterface;
import com.gideonsoftworks.gcore.*;
import com.gideonsoftworks.gja.*;
import com.gideonsoftworks.gdo.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.List;
import javax.swing.table.*;
import javax.swing.event.*;
import java.lang.reflect.Array;
import java.util.prefs.*;
import java.text.DateFormat;

// Lists
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import javax.swing.table.TableRowSorter;
import javax.swing.RowFilter;

/**
  Browser panel ( the thing you browser sites with ) in GSFTP
  
  - See GBrowserPanelListenerInterface for the connection-state notifications this object fires.
  - All commands it understands are specified in GBrowerPanelCommandInterface.java - CALL only methods defined there.
*/

public class GBrowserPanelController extends GGideonPanelController implements GBrowserPanelCommandInterface, GBrowserWindowListenerInterface, GJobManagerListenerInterface, TableModel, MouseListener, ListSelectionListener, ActionListener, PopupMenuListener
{
    // Login panel
    private     JButton             fivLoginButton;
    private     JComboBox           protocolCombo;
    private     JPanel              fivLoginPanel;
    private     JPanel              connectionCardPanel;
    private     CardLayout          connectionCardLayout;
    private     java.util.Map<String, GConnectionPanelInterface> connectionPanels = new java.util.LinkedHashMap<>();
    
    // Browser Panel
    private     JPanel              fivBrowserPanel=null;
    private     JComboBox           fivPathSelector;
    private     JTable              fivFileTable;
    private     JLabel              fileCountField;
    private     JButton             backButton;
    private     JButton             reloadButton;

    // Permission Settings Panel
    
    // Class Fields
    private     GConnection                     fivConnection=null;
    private     java.util.List                  treeModelListeners = new java.util.concurrent.CopyOnWriteArrayList();
    private     GFileItem                       fivCWD=null;
    private     Object                          delegate=null;
    private     ArrayList                       listeners = new ArrayList();
    private     JPanel                          bPanelMasterPanel;

    // if we have been provided a bookmark from a connection - we use that.
    // otherwise we create and populate a new one.
    private     GBookmark                       currentBookmark=null;
    private     GJobManager                     jobManager = null;
    
    private     boolean                         gbpPopupShowing = false;
    private     JPopupMenu                      gbpPUM = null;
    
    private     javax.swing.Timer               bpwPingTimer;
    private     long                            lastKnownMtime = -1;
    
    // Rendezvous - replaced by a global host manager
    //private		GRendezvousHostManager			bpcRendezvousController = null;


    // Follow Me
    private		boolean		reallyFollowMe = false;

    // table sorting/filtering
    private		TableRowSorter<TableModel>		rowSorter = null;
    public		boolean					fConfiguredTable = false;
    private             boolean                                 initialWidthNotSet = true;
        
    /**
    *
    * This class makes the path selector pretty by adding a nice folder image to the left
    *
    *
    */
     class GPathComboBoxRenderer extends JLabel implements ListCellRenderer {

        public GPathComboBoxRenderer() {
            setOpaque(true);
        }

        /*
         * This method finds the image and text corresponding
         * to the selected value and returns the label, set up
         * to display the text and image.
         */
        public Component getListCellRendererComponent(
                                           JList list,
                                           Object value,
                                           int index,
                                           boolean isSelected,
                                           boolean cellHasFocus) {
            
            if( value == null ) return this;
            
            if (isSelected) {
            
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
                
                if( ((GFileItem)value).getIsMyComputer() )
                {
                    setIcon(GLocalized.SMALL_MYCOMPUTER_ICON);
                }else if( ((GFileItem)value).getIsHardDrive() )
                {
                    setIcon(GLocalized.SMALL_HD_ICON);
                }else if( ((GFileItem)value).getIsDesktop() )
                {
                    setIcon(GLocalized.DESKTOP_SMALL_ICON );
                }else{
                    setIcon(GLocalized.SMALL_FOLDER_ICON);
                }
            
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
                
                if( ((GFileItem)value).getIsMyComputer() )
                {
                    setIcon(GLocalized.SMALL_MYCOMPUTER_ICON);
                }else if( ((GFileItem)value).getIsHardDrive() )
                {
                    setIcon(GLocalized.SMALL_HD_ICON);
                }else if( ((GFileItem)value).getIsDesktop() )
                {
                    setIcon(GLocalized.DESKTOP_SMALL_ICON);
                }else{
                    setIcon(GLocalized.SMALL_FOLDER_ICON);
                }
 
            }
 
            setText(value.toString());

            return this;
        }

    }
    
	class GNameComparator implements Comparator
	{
        public int compare( Object a, Object b )
		{
			String sa = (a == null) ? "" : a.toString();
			String sb = (b == null) ? "" : b.toString();
			return sa.compareToIgnoreCase(sb);
        }
    }
	
	class GDateComparator implements Comparator
	{
        public int compare( Object a, Object b )
		{
			if( a == null && b == null ) return 0;
			if( a == null ) return -1;
			if( b == null ) return 1;
			return ((java.util.Date)a).compareTo((java.util.Date)b);
        }
    }
	
	class GSizeComparator implements Comparator
	{
        public int compare( Object a, Object b )
		{
			long la = (a instanceof Long) ? ((Long)a).longValue() : -1;
			long lb = (b instanceof Long) ? ((Long)b).longValue() : -1;
			return Long.compare(la, lb);
        }
    }
	
    /**
    *
    * Main browser panel
    *
    *
    */
    /**
     * Flatten a combo's arrow button: the FlatMac (macOS) themes render combos as a native pop-up
     * button with a heavy accent-blue up/down stepper. This overrides it to a single themed chevron
     * with the button blended into the field. Theme-adaptive ($ComboBox.* refs) — correct in light + dark,
     * and a no-op visual change under the plain (already-flat) themes.
     */
    private static void flattenComboArrow( JComboBox combo )
    {
        combo.putClientProperty( com.formdev.flatlaf.FlatClientProperties.STYLE,
            "buttonStyle: auto; arrowType: chevron; buttonSeparatorWidth: 0;"
            + " buttonBackground: $ComboBox.background; buttonArrowColor: $ComboBox.buttonArrowColor" );
    }

    public  GBrowserPanelController( GFrame browserWindow )
    {
        
		super();
		
        if( browserWindow == null )
        {
            throw new NullPointerException("GBrowserPanelController : null browser window provided in constructor");
        }
        
        setParentFrame(browserWindow);
        setJobManager(browserWindow.getJobManager());
        
        /*
        *
        * Create new panel with a card layout to hold the eventual parts
        * all the parts are loaded on demand.
        */
        bPanelMasterPanel = new JPanel(new CardLayout());
        bPanelMasterPanel.setMinimumSize(new Dimension(0,0));
        
        if( GideonConfiguration.LAYOUT_DEBUG ) bPanelMasterPanel.setBorder(BorderFactory.createLineBorder(Color.black));
        
        // assign our panel
        setPanel( bPanelMasterPanel );
        
        // Build the browser panel by hand (replaces GBrowserPanel.gui_xml)
        fivBrowserPanel = new JPanel(new BorderLayout(0, 6));

        // Toolbar row: back, reload, path selector, search field
        JPanel toolRow = new JPanel(new BorderLayout(4, 0));
        toolRow.setBorder(BorderFactory.createEmptyBorder(4, 8, 2, 8));

        backButton = new JButton();
        backButton.setIcon(GLocalized.PWD_ICON);
        backButton.setToolTipText("Back");
        backButton.setFocusable(false);
        backButton.addActionListener(this);
        backButton.putClientProperty("JButton.buttonType", "roundRect");
        backButton.setMargin(new Insets(4, 6, 4, 6));

        reloadButton = new JButton();
        reloadButton.setIcon(GLocalized.RELOAD_SM_ICON);
        reloadButton.setToolTipText("Reload");
        reloadButton.setFocusable(false);
        reloadButton.addActionListener(this);
        reloadButton.putClientProperty("JButton.buttonType", "roundRect");
        reloadButton.setMargin(new Insets(4, 6, 4, 6));

        JButton favoritesButton = new JButton();
        favoritesButton.setIcon(GLocalized.FAVORITES_ICON);
        favoritesButton.setToolTipText("Favorites");
        favoritesButton.setFocusable(false);
        favoritesButton.putClientProperty("JButton.buttonType", "roundRect");
        favoritesButton.setMargin(new Insets(4, 6, 4, 6));
        favoritesButton.addActionListener(e -> showFavoritesPopup(favoritesButton));

        JPanel navButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        navButtons.add(backButton);
        navButtons.add(reloadButton);
        navButtons.add(favoritesButton);
        toolRow.add(navButtons, BorderLayout.WEST);

        fivPathSelector = new JComboBox();
        fivPathSelector.setFocusable(false);
        fivPathSelector.setEditable(false);
        fivPathSelector.addActionListener(this);
        fivPathSelector.putClientProperty("JComponent.roundRect", true);
        flattenComboArrow(fivPathSelector);
        GPathComboBoxRenderer renderer= new GPathComboBoxRenderer();
        fivPathSelector.setRenderer(renderer);
        toolRow.add(fivPathSelector, BorderLayout.CENTER);


        fivBrowserPanel.add(toolRow, BorderLayout.NORTH);

        // File table in scroll pane
        fivFileTable = new GTable();
        ((GTable)fivFileTable).setBrowserPanel(this);
        if( !GideonConfiguration.ENABLESORTING )
        {
            fivFileTable.setModel( (TableModel)this );
        }
        fivFileTable.addMouseListener( (MouseListener)this );
        fivFileTable.setIntercellSpacing( new Dimension( 0, 0 ) );
        fivFileTable.setShowGrid( false );
        fivFileTable.setShowHorizontalLines( false );
        fivFileTable.setShowVerticalLines( false );
        fivFileTable.getSelectionModel().addListSelectionListener((ListSelectionListener)this);
        fivFileTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION );

        JScrollPane fileScrollPane = new JScrollPane(fivFileTable,
            JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
            JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS) {
            @Override
            public void paint(Graphics g) {
                super.paint(g);
                if( ((GTable)fivFileTable).isShowingDropBorder() ) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(UIManager.getColor("Component.focusColor"));
                    g2.setStroke(new BasicStroke(2));
                    g2.drawRect(1, 1, getWidth() - 2, getHeight() - 2);
                    g2.dispose();
                }
            }
        };
        // Repaint scroll pane when table's drop location changes
        fivFileTable.addPropertyChangeListener("dropLocation", evt -> fileScrollPane.repaint());
        fivBrowserPanel.add(fileScrollPane, BorderLayout.CENTER);

        // File count field at bottom
        fileCountField = new JLabel("x Items.");
        fileCountField.setFont(new Font("Lucida Grande", Font.PLAIN, 10));
        fileCountField.setHorizontalAlignment(SwingConstants.RIGHT);
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBorder(BorderFactory.createEmptyBorder(2, 8, 4, 8));
        bottomBar.add(fileCountField, BorderLayout.EAST);
        fivBrowserPanel.add(bottomBar, BorderLayout.SOUTH);

        fivFileTable.setDragEnabled(true);
        fivFileTable.setDropMode(DropMode.ON);
        fivFileTable.setTransferHandler(new BrowserTransferHandler(this,
            () -> (GBrowserPanelCommandInterface) getBuddy()));
        new DrillDownHandler(fivFileTable, this);

        // window closing -> stop this pane's timers/watchers (typed listener; same lifetime as the window)
        if( browserWindow instanceof GBrowserWindow bw ) bw.addBrowserWindowListener( this );
	
        getPanel().add("Browser", fivBrowserPanel);

    }
    
    /*
    *
    * EventCenter event callbacks
    *
    */
    @Override
    public void bwl_browserClosing( GBrowserWindow window )
    {
        // Ping Timer
        if( bpwPingTimer != null ) bpwPingTimer.stop();
        bpwPingTimer = null;
        // Folder watcher
        if( getConnection() != null ) getConnection().stopWatching();
        stopRemoteMtimeTimer();
    }
    
    /*
    *
    *
    *
    */
    
    /**
      Returns the job manger object for this executer.
      @return GJobManager
    */
    public  GJobManager getJobManager()
    {  

        return jobManager;  

    }
    
    /**
      Sets the job manger object for this panel.
    */
    public  void setJobManager( GJobManager jm )
    {  

        jobManager = jm;  

    }
        
    /*
    * End drag and drop
    *
    */

    public  void    addFocusListener( FocusListener focusListener )
    {
    
        if( fivFileTable != null )
        {
            fivFileTable.addFocusListener(focusListener);
        }
        
    }
    
    public  void    removeFocusListener( FocusListener focusListener )
    {
    
        if( fivFileTable != null )
        {
            fivFileTable.removeFocusListener(focusListener);
        }
    
    }
    
    public  void    setDelegate( Object del )
    {
        
        delegate = del;
        
        if( del != null )
        {
            // add ourselves as a listener so we know when to tell the ui to update
            ((GFrame)del).getJobManager().addStatusListener(this);
        }
        
    }
    
    public  Object  getDelegate()
    {
        return delegate;
    }
    
    // Preserve row selection across table refreshes (e.g. after an upload re-lists the folder).
    private java.util.Set<String> gbpSelectedNames = new java.util.HashSet<String>();
    private String                gbpSelectedFolderPath = null;
    private boolean               gbpRestoringSelection = false;

    // EDT-owned snapshot of the virtual CWD's children - the table renders from this,
    // never from the live list job workers mutate (roadmap 3.3)
    private java.util.ArrayList   gbpTableRows = new java.util.ArrayList();

    private void rebuildTableRows()
    {
        GFileItem cwd = getVirtualCWD();
        gbpTableRows = ( cwd != null ) ? cwd.getChildrenSnapshot() : new java.util.ArrayList();
    }

    // Bounds-checked model-row lookup against the snapshot.
    private GFileItem rowItem( int mr )
    {
        java.util.ArrayList rows = gbpTableRows;
        if( mr < 0 || mr >= rows.size() ) return null;
        return (GFileItem)rows.get(mr);
    }

    public  void    valueChanged( ListSelectionEvent e )
    {


        if( e.getValueIsAdjusting() ) return;

        // remember the current selection (by name) so a later refresh can restore it
        if( !gbpRestoringSelection ) captureSelectedNames();

        ((GFrame)delegate).validateToolbar();

    }

    // Snapshot the selected file names + folder while the table and data are in sync.
    private void captureSelectedNames()
    {
        java.util.HashSet<String> names = new java.util.HashSet<String>();
        GFileItem cwd = getVirtualCWD();
        if( fivFileTable != null && cwd != null )
        {
            for( int vr : fivFileTable.getSelectedRows() )
            {
                int mr = fivFileTable.convertRowIndexToModel(vr);
                GFileItem ch = rowItem(mr);
                if( ch != null ) names.add( ch.getName() );
            }
        }
        gbpSelectedNames = names;
        gbpSelectedFolderPath = ( cwd != null ) ? cwd.getCompletePath() : null;
    }

    // Re-select the previously captured files after a refresh cleared the JTable selection.
    private void restoreSelectionByName()
    {
        if( fivFileTable == null || gbpSelectedNames == null || gbpSelectedNames.isEmpty() ) return;

        GFileItem cwd = getVirtualCWD();
        if( cwd == null ) return;

        // only restore within the same folder — don't carry selection across navigation
        if( gbpSelectedFolderPath == null || !gbpSelectedFolderPath.equals(cwd.getCompletePath()) ) return;

        ListSelectionModel selm = fivFileTable.getSelectionModel();
        for( int mr = 0; mr < gbpTableRows.size(); mr++ )
        {
            GFileItem ch = rowItem(mr);
            if( ch != null && gbpSelectedNames.contains(ch.getName()) )
            {
                int vr = fivFileTable.convertRowIndexToView(mr);
                if( vr >= 0 ) selm.addSelectionInterval(vr, vr);
            }
        }
    }

    public  void    showLoginPanel( GBookmark bm )
    {

        if( fivLoginPanel == null )
        {
        
            ActionListener lpal;

            // Login Panel
            fivLoginPanel = new JPanel(new BorderLayout());

            // Top: Protocol selector — centered
            JPanel protocolPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            protocolPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
            protocolCombo = new JComboBox();
            protocolCombo.setName("protocolCombo");
            protocolCombo.setFocusable(false);
            protocolCombo.putClientProperty("JComponent.roundRect", true);
            flattenComboArrow(protocolCombo);   // single themed chevron, not the FlatMac accent stepper
            protocolCombo.addItem(GLocalized.FTP);
            protocolCombo.addItem(GLocalized.SFTP);
            protocolCombo.addItem(GLocalized.FTPS);
            protocolCombo.addItem(GLocalized.S3);
            protocolPanel.add(protocolCombo);
            fivLoginPanel.add(protocolPanel, BorderLayout.NORTH);

            // Center: CardLayout with protocol-specific panels
            connectionCardLayout = new CardLayout();
            connectionCardPanel = new JPanel(connectionCardLayout);
            connectionCardPanel.setBorder(BorderFactory.createEmptyBorder(0, 18, 10, 20));

            GFTPConnectionPanel ftpPanel = new GFTPConnectionPanel(GBookmark.G_FTP_PROTOCOL);
            GFTPConnectionPanel sftpPanel = new GFTPConnectionPanel(GBookmark.G_SFTP_PROTOCOL);
            GFTPConnectionPanel ftpsPanel = new GFTPConnectionPanel(GBookmark.G_FTPSSL_PROTOCOL);
            GS3ConnectionPanel s3Panel = new GS3ConnectionPanel();

            connectionPanels.put("FTP", ftpPanel);
            connectionPanels.put("SFTP", sftpPanel);
            connectionPanels.put("FTPS", ftpsPanel);
            connectionPanels.put("S3", s3Panel);

            connectionCardPanel.add(ftpPanel.getPanel(), "FTP");
            connectionCardPanel.add(sftpPanel.getPanel(), "SFTP");
            connectionCardPanel.add(ftpsPanel.getPanel(), "FTPS");
            connectionCardPanel.add(s3Panel.getPanel(), "S3");

            // Swap cards when protocol changes
            protocolCombo.addItemListener(e -> {
                if (e.getStateChange() == java.awt.event.ItemEvent.SELECTED) {
                    String card = protocolComboToCardName();
                    connectionCardLayout.show(connectionCardPanel, card);
                }
            });

            // Default protocol
            if( GSFTPPreferences.defaultToSFTP() )
            {
                protocolCombo.setSelectedIndex(1);
            }else{
                protocolCombo.setSelectedIndex(0);
            }

            // Connect button — directly below the connection fields
            fivLoginButton = new JButton(GLocalized.LP_CONNECT);
            fivLoginButton.setName("connectButton");
            fivLoginButton.putClientProperty("JButton.buttonType", "roundRect");
            fivLoginButton.setFont(fivLoginButton.getFont().deriveFont(Font.BOLD));
            fivLoginButton.addActionListener(lpal=(ActionListener)new GBPLoginPanelActionListener(this));
            fivLoginButton.setDefaultCapable(true);

            JPanel connectPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
            connectPanel.add(fivLoginButton);

            // Wrap fields + button together so button sits right below form
            JPanel centerWrapper = new JPanel(new BorderLayout());
            centerWrapper.add(connectionCardPanel, BorderLayout.NORTH);
            centerWrapper.add(connectPanel, BorderLayout.CENTER);
            fivLoginPanel.add(centerWrapper, BorderLayout.CENTER);

            this.getPanel().add("Login", fivLoginPanel);

        }
        
        //currentBookmark=bm;

        fivLoginButton.setEnabled(true);

        populateLoginPanelForBookmark(bm);
 
        CardLayout  cLay = (CardLayout)this.getPanel().getLayout();
        ((GFrame)delegate).setDefaultButton(fivLoginButton);
        cLay.show( this.getPanel(), "Login" );
	
    }   

    public  void    showBrowserPanel()
    {

	if( fivLoginButton != null ) fivLoginButton.setEnabled(false);
        CardLayout  cLay = (CardLayout)this.getPanel().getLayout();
        cLay.show( this.getPanel(), "Browser" );
        
    }
    
    private void    populateLoginPanelForBookmark( GBookmark bm )
    {
        if( bm == null ) return;

        // Set protocol combo to match bookmark protocol
        int proto = bm.getProtocol();
        switch (proto) {
            case GBookmark.G_FTP_PROTOCOL:      protocolCombo.setSelectedIndex(0); break;
            case GBookmark.G_SFTP_PROTOCOL:     protocolCombo.setSelectedIndex(1); break;
            case GBookmark.G_FTPSSL_PROTOCOL:   protocolCombo.setSelectedIndex(2); break;
            case GBookmark.G_S3_PROTOCOL:       protocolCombo.setSelectedIndex(3); break;
            default:                            protocolCombo.setSelectedIndex(0); break;
        }

        // Delegate to the active connection panel
        String card = protocolComboToCardName();
        connectionCardLayout.show(connectionCardPanel, card);
        GConnectionPanelInterface activePanel = connectionPanels.get(card);
        if (activePanel != null) {
            activePanel.populateFromBookmark(bm);
        }
    }

    private String protocolComboToCardName() {
        int idx = protocolCombo.getSelectedIndex();
        switch (idx) {
            case 0: return "FTP";
            case 1: return "SFTP";
            case 2: return "FTPS";
            case 3: return "S3";
            default: return "FTP";
        }
    }

    GConnectionPanelInterface getActiveConnectionPanel() {
        return connectionPanels.get(protocolComboToCardName());
    }


/*
*
*
* Primary Login "code"
*
*
*/

/**
*
* Inform the browsers of the new connections
*
* connectToConnection() is called on this class from the parent GBrowserWindow each browser. 
* When we receive this method we check to see if the connection is up and if it is
* we init our virtual cwd from the connections current working directory. We then "refresh" the table.
* If the connection is NOT connection we get its bookmark and call connectForBookmark()
*
*/
public void connectionToConnection( GConnection c )
{
    
    try{
    
        // if we are connected this could be an "idle" connection
        if( ( c != null) && c.getIsConnected() )
        {
            
            
            // record this connection as ours
            this.setConnection(c);
            
            //update title
            if( c.getIsRemote() )
            {
                if( delegate instanceof GBrowserWindowCommandInterface )
                {
                    ((GBrowserWindowCommandInterface)delegate).setTitleTo(c.getBookmark().getHostname());
                }
            }
            
            GFileItem cwd = c.getCurrentWorkingDirectory();
            
            if( cwd != null )
            {
                // get the CWD and make it our virtual CWD
                setVirtualCWD(cwd);
                // show the browser
                showBrowserPanel();
                // refresh the table
                refreshTable();
                // update the button and drop down
                updateButtonAndDropDown();
                // update file count field
                updateFileCountArea(cwd);
            }

        // we have to handle connecting.
        }else{

            if( c != null && 
                !c.userDisconnectedConnection() )
            {
            
                // record the connection as ours
                setConnection(c);

                // connect
                connectForBookmark(c.getBookmark());
            
            }else{ // null connection so show the login panel
                // clear our connection and "currentBookmark"
                this.setConnection(null);
                // clear our virtual CWD
                this.setVirtualCWD(null);
                // show the login panel
                this.showLoginPanel(null);
            }
            
        }
    
    }catch( GConnectionException e )
    {
        
        JOptionPane.showMessageDialog( null,GLocalized.ERROR_GETTINGCONNECTIONSTATUS + e.toString());
        
        System.exit(0);
    
    }
    
}

private	void	updateFileCountArea( GFileItem fi )
{

        GConnection con = getConnection();
        
        if( con == null )
        {
            throw new NullPointerException("Connection was null line 889 GBrowserPanelController.java");
        }

        if( GideonConfiguration.USELOCATIONLABELS )
        {
                if( con.getIsRemote() ) 
                {
                        GBookmark   bm = con.getBookmark();
                
                        if( bm == null )
                        {
                            throw new NullPointerException("Bookmark was null line 900 GBrowserPanelController.java");
                        }
                
                       fileCountField.setText( GLocalized.REMOTEFILESLABEL + " (" + fi.getChildCount() + " " + GLocalized.ITEMS_STRING + ")" );
                       //fileCountField.setText( bm.getHostname() + " (" + fi.getChildCount() + " " + GLocalized.ITEMS_STRING + ")" ); 
                
                }else{
                        fileCountField.setText( GLocalized.LOCALFILESLABEL + " (" + fi.getChildCount() + " " + GLocalized.ITEMS_STRING + ")" );
                }
                
        }else{
                fileCountField.setText( fi.getChildCount() + " " + GLocalized.ITEMS_STRING + "." );
        }

}

/**
    This method does three things
        1. Shows the login panel in the following situations
                FTP - Username or password is null 
                SFTP - Username or password is null
            - Login panel will not show for bookmark when :
                FTP - both null - assume anonymous
                SFTP - both password and username filled in
        2. IF this browser panel has not been assigned a connection - this method will create one.
                - the login panel code ALSO will create a corresponding GBookmark which the 
                    GConnection is created from.
        3. IF this browser has been assigned a connection - the setConnection code gets its GBookmark and 
                assigns it to the "currentBookmark" class field. This field is referenced in the 
                login panel handling code. In other words if currentBookmark is non null - we fill
                in that bookmark instead of creating a new one as we would in state 2.
        
*/
public  void    connectForBookmark( GBookmark bm )
{

        String          name        = null;
        String          bmPath      = null;
        GFileItem       nFI         = null;
        GConnection     conn        = null;
        GJobManager     sjm         = getJobManager();
        GJobDescription loginJob    = null;

        // switch to the login panel and fill it in
        if( !bm.isLocal() ) showLoginPanel(bm);
        
        if( bm.getHostname() == null )
        {
            JOptionPane.showMessageDialog( null,GLocalized.ENTERHOSTNAME);
            return;
        }
        
        // if either of the username/password is null - ask the user 
        // ftp defaults to anonymous and gideon@ for a password
        if( bm.getProtocol() == GBookmark.G_FTP_PROTOCOL ) 
        {
        
            String un = bm.getUsername();
            String pw = GBookmarkManager.getPassword(bm);
        
            if( ( ( un == null ) && ( pw != null ) ) )
            {
                JOptionPane.showMessageDialog( null,GLocalized.FTP_ENTERPASSANDUN);
                return;
            }
        }
        
        // sftp make the user enter a username at the least - no password means PKA
        if( ( bm.getProtocol() == GBookmark.G_SFTP_PROTOCOL ) &&
            ( bm.getUsername() == null ) )
        {
            JOptionPane.showMessageDialog( null,GLocalized.SFTP_ENTERPASSANDUN);
            return;
        }

        // S3 requires access key, secret key, and bucket name
        if( bm.getProtocol() == GBookmark.G_S3_PROTOCOL )
        {
            if( bm.getHostname() == null || bm.getS3AccessKey() == null || GBookmarkManager.getPassword(bm) == null )
            {
                JOptionPane.showMessageDialog( null, "Please specify a Bucket, Access Key, and Secret Key for S3.");
                return;
            }
        }

        try{
            
            // see if we have a connection - if we do cool.
            conn = getConnection();
        
            // we haven't been assigned a connection so we create our own.
            if( conn == null )
            {
                // generate and record a connection
                conn = new GConnection(bm);
            }
            
        }catch( GConnectionException e )
        {
            
        
        }
        
        // notify any listeners that we bes connecting
        firePanelEvent( "browser-panel connecting", l -> l.bpl_connecting( this ) );
 
        name = GLocalized.LOGIN_JOB_TYPE + " to \"" + bm.getHostname() + "\"";
        
        // create a login job description
        loginJob = new GJobDescription(GJobDescription.LOGIN_JOB_TYPE, conn, name );
        
        bmPath = bm.getHomePath();
        if( bmPath != null )
        {
            loginJob.addProperty( GJobDescription.DEFAULT_FOLDER_JOB_PROP, bmPath );
        }
        
        /*
        // if the user does not wany pasv mode - flag active mode
        if( bm.getDontUsePASV() )
        {
            loginJob.addProperty( GJobDescription.CONNECT_MODE_PROP, Integer.valueOf(1) );
        }
        */
        
        loginJob.setOriginator(this);

        // disable the connect button so the user does not 
        // click connect in the middle of a connect
        // don't do this for local connections - or we get a NPE
        if( !bm.isLocal() ) fivLoginButton.setEnabled(false);
        
        // then issue the connect command...
        queueAndStartJob(loginJob);
        
}

// ---- typed connection-state listeners (replaces the reflection event center, roadmap Phase 4) ----

private final GListenerList<GBrowserPanelListenerInterface> panelListeners = new GListenerList<>();

@Override
public void addBrowserPanelListener( GBrowserPanelListenerInterface l )    { panelListeners.add( l ); }

@Override
public void removeBrowserPanelListener( GBrowserPanelListenerInterface l ) { panelListeners.remove( l ); }

private void firePanelEvent( String context, Consumer<GBrowserPanelListenerInterface> call )
{
    panelListeners.dispatch( context, call );
}

/** Number of registered connection-state listeners (for tests: proves windows unregister on dispose). */
public int browserPanelListenerCount()
{
    return panelListeners.size();
}


public void jm_jobCompleted( final GJobManagerEvent e )
{   
    
    GJobDescription jd  = e.getJobDescription();
    GFileItem       ci  = null;
    
    // make sure we only watch for our job(s)
    if( jd.getOriginator().equals(this) )
    {
        int             jType   = jd.getJobType();
        GConnection     c       = (GConnection)jd.getConnectionToUse();
        
        switch(jType)
        {
        
            case GJobDescription.MTIME_JOB_TYPE:
                handleMtimeResult(jd);
            break;

            case GJobDescription.LOGIN_JOB_TYPE:
            
                
                // record our connection as the one
                setConnection(c); 
                // at this point the connection class has its "current working directory" set and populated
                // all we have to do is list the listing.
                
                // we no longer want to hear about job manager events
                //GJobManager.getSharedJobManager().removeStatusListener(this);
                        
                // change the browser to the file view
                showBrowserPanel();
                
                ci = c.getCurrentWorkingDirectory();
                
                if( ci == null ) 
                {
                    System.out.println("Current Working Directory was null");
                    return;
                }
                
                // we keep a "virtual" cwd as the user can move around the 
                // cached file items. The Connection is ALWAYS in synch with 
                // how the server sees the world.
                setVirtualCWD(ci);
  
                // update file count field
                updateFileCountArea(ci);
								
                refreshTable();
                
                updateButtonAndDropDown();

                // XXX this is kind of a hack so the title bar of the window
                // is always in synch with the connection status
                if( c.getIsRemote() )
                {
                    ((GBrowserWindowCommandInterface)delegate).setTitleTo(c.getBookmark().getHostname());
                }
                
                
                // do the intial width setup here for table rows.
                if( initialWidthNotSet )
                {
                
                    TableColumnModel colModel = fivFileTable.getColumnModel();
                    TableColumn tCol = colModel.getColumn(0);
                    tCol.setPreferredWidth(270);

                    initialWidthNotSet = false;
                
                }
                
                // Offer to remember password if it wasn't already stored
                GBookmark loginBM = c.getBookmark();
                String loginPW = GBookmarkManager.getPassword(loginBM);
                if( loginBM != null &&
                    !loginBM.isLocal() &&
                    loginPW != null &&
                    !loginPW.isEmpty() )
                {
                    if( GBookmarkManager.hasStoredPassword(loginBM) )
                    {
                        // Already opted in to storing this host's password — silently refresh
                        // the keyring copy in case the user connected with a changed password
                        // (the keyring layer skips the write when the value is unchanged).
                        GBookmarkManager.savePassword(loginBM);
                    }
                    else if( !loginBM.getNeverSavePassword() )
                    {
                        String[] options = { "Yes", "No", "Never ask for this host" };
                        int choice = JOptionPane.showOptionDialog(null,
                            "Would you like to save the password for \"" + loginBM.getName() + "\"?",
                            "Save Password",
                            JOptionPane.DEFAULT_OPTION,
                            JOptionPane.QUESTION_MESSAGE,
                            null, options, options[0]);
                        if( choice == 0 ) // Yes
                        {
                            GBookmarkManager.savePassword(loginBM);
                        }
                        else if( choice == 2 ) // Never
                        {
                            loginBM.setNeverSavePassword(true);
                            GBookmarkManager.saveBookmarks();
                        }
                    }
                }

                handlePingTimer(true);

                ((GBrowserWindowCommandInterface)delegate).validateToolbar();

                // nofify listeners we are connected...
                firePanelEvent( "browser-panel connected", l -> l.bpl_connected( this ) );
                
            break;
            
            case GJobDescription.CHANGE_AND_LIST_JOB_TYPE:
                
                ci = c.getCurrentWorkingDirectory();
												
                setVirtualCWD(ci);
                // update file count field
                updateFileCountArea(ci);
                //
                refreshTable();
                
                // update our drop down and back button
                updateButtonAndDropDown();
				
                if( GideonConfiguration.FOLLOWMEENABLED && reallyFollowMe )
                {
                
                        GGideonPanelController  cnt = getBuddy();
                
                        if( cnt instanceof GBrowserPanelController )
                        {
                                ((GBrowserPanelController)cnt).changeToFolderNamed(ci.getName());
                        }

                }
 
            break;
 
            case GJobDescription.DISCONNECT_JOB_TYPE:
 
                ((GBrowserWindowCommandInterface)delegate).validateToolbar();
                
                handlePingTimer(false);
                
            break;
           
            case GJobDescription.DOWNLOAD_JOB_TYPE:
            case GJobDescription.UPLOAD_JOB_TYPE:

                // coalesce: only the last transfer of a batch refreshes the buddy -
                // the destination pane learns about transferred files via this re-list
                if( !getJobManager().getTransferQueue().hasOtherJobsForOriginator(this, jd) )
                {
                    GGideonPanelController  cnt = getBuddy();

                    if( cnt instanceof GBrowserPanelController )
                    {
                        ((GBrowserPanelController)cnt).refresh(this);
                    }
                }

            break;


            case GJobDescription.NEWFOLDER_JOB_TYPE:

                // the re-list fills in the created folder's real date/permissions -
                // the model only holds a synthetic child (post-3.3 this no longer blanks)
                this.refresh(this);

            break;

            case GJobDescription.RENAME_JOB_TYPE:
            case GJobDescription.DELETE_JOB_TYPE:

                // the worker already updated the model ( delete unlinked the item,
                // rename set the new name/path ) - re-snapshot in place, no re-list
                if( jType == GJobDescription.RENAME_JOB_TYPE )
                {
                    // keep the renamed row selected: restore matches by (new) name
                    String newName = (String)jd.getProperty(GJobDescription.NAME_JOB_PROP);
                    if( newName != null ) gbpSelectedNames.add(newName);
                }

                ci = getVirtualCWD();
                if( ci != null ) updateFileCountArea(ci);
                refreshTable();

            break;

            case GJobDescription.REFRESH_JOB_TYPE:

                this.refreshTable();

            break;
   
        }
         
    }
    
}

public void handlePingTimer( boolean connected )
{
    
    if( !GSFTPPreferences.serverKeepAliveOn() ) return;
    
    GConnection c = getConnection();

    if( c == null ) return;

    if( connected && c.needsKeepAlive() )
    {
        // only create the timer once.
        if( bpwPingTimer == null )
        {
            bpwPingTimer = new javax.swing.Timer(150000, (ActionListener)this);
            bpwPingTimer.setRepeats(true);
            bpwPingTimer.start();
        }
    }else{
        if( bpwPingTimer != null ) bpwPingTimer.stop();
        bpwPingTimer = null;
    }

    // Mtime timer — start if the user opted into monitoring and the server supports it.
    // Safe to read the capability here: this runs in the LOGIN job's completion handler,
    // which is dispatched to the EDT only after the login worker (connect + capability
    // probe) has finished, so the probe's result is fully published by then.
    if( connected && !hasLocalSelection()
        && GSFTPPreferences.monitorRemoteChangesOn()
        && c.hasCapability(GConnectionStats.CAP_MTIME) )
    {
        if( remoteMtimeTimer == null )
        {
            startRemoteMtimeTimer();
        }
    }else{
        stopRemoteMtimeTimer();
    }

}


/**
*
* This method updates the drop down and back button. Basically called whenever the virtual cwd changes.
*
*/
private void    updateButtonAndDropDown()
{
    GFileItem   pFi = null;
    GFileItem   fi  = this.getVirtualCWD(); 
    
    if( fi==null )
    {
        
        throw new NullPointerException("GBrowserControlPanel updateButtonAndDropDown : virtual cwd was null"); 
    
    }
    
    if( fi.getParentFileItem() == null )
    {
        backButton.setEnabled(false);
    }else{
        backButton.setEnabled(true);
    }
    
    // create an entry in the path selector for 
    // each parent item - starting at the virtualCWD.
    
    fivPathSelector.removeAllItems();
    
    fi = this.getVirtualCWD();
    
    // if we have a file item init it
    if( fi != null )
    {
        // add current item
        fivPathSelector.addItem(fi);
        
        // init parent file item
        pFi = fi.getParentFileItem();
        
        // for each parent add — skip "Computer" node for remote connections
        boolean isRemote = getConnection() != null && getConnection().getIsRemote();
        while( pFi != null )
        {
            if( !(isRemote && pFi.getIsMyComputer()) )
            {
                fivPathSelector.addItem(pFi);
            }
            pFi = pFi.getParentFileItem();
        }
    }else{
        fivPathSelector.setEnabled(false);
    }
    
    /* Secured icon moved to status panel controller ( 6/25/05 )
    // update secure lock
    if( getConnection().getIsSecure() )
    {
        lockPanel.add(secureLabel);
    }else{
        lockPanel.removeAll();
    }
    */
}
    
/*
*
*
*/
public void actionPerformed( ActionEvent e )
{
    int flag = 0;
    
    if( e.getSource().equals(backButton) )
    {
		
		goToParentFolder();
		
		if( GideonConfiguration.FOLLOWMEENABLED )
		{
			GGideonPanelController  cnt = getBuddy();
			if( cnt instanceof GBrowserPanelController )
			{
				((GBrowserPanelController)cnt).goToParentFolder();
			}
		}
        
    }else if( e.getSource().equals(reloadButton) )
    {
		
        refresh( null );
    
    }else if( e.getSource().equals(fivPathSelector) )
    {
            
            
        GFileItem sItem = (GFileItem)fivPathSelector.getSelectedItem();
            
        if( sItem == null ) return;
            
        // ignore selecting the current fso
        if( sItem.equals(this.getVirtualCWD()) )
        {
            return;
        }
        
		// clear the selection
		fivFileTable.getSelectionModel().clearSelection();
				    
        changeToFolder(sItem);
        
    }else if( bpwPingTimer !=null && e.getSource().equals(bpwPingTimer) )
    {
        ping();
    }
	
}

public  void    jm_keyJobChanged( final GJobManagerEvent e ){}
public  void    jm_jobStarted( final GJobManagerEvent e ){}
public  void    jm_jobStopped( final GJobManagerEvent e )
{
    GJobDescription jd = e.getJobDescription();
    if( jd != null && jd.getOriginator() == this && jd.getJobType() == GJobDescription.MTIME_JOB_TYPE )
        mtimeJobPending = false;   // a cancelled poll must not block the next tick
}
public  void    jm_jobNeedsInput( final GJobManagerEvent e, final GJobUserMessage m ){}
public  void    jm_jobHasMessage( final GJobManagerEvent e ){}
public  void    jm_jobCancelling( final GJobManagerEvent e ){}
public  void    jm_shuttingDown( final GJobManagerEvent e ){}

public  void    jm_jobFailed( final GJobManagerEvent e )
{
    GJobDescription jd  = e.getJobDescription();
    GFileItem       ci  = null;

    // make sure we only watch for our job(s)
    if( jd.getOriginator().equals(this) )
    {
        int             jType   = jd.getJobType();
        GConnection     c       = (GConnection)jd.getConnectionToUse();

        switch(jType)
        {
            case GJobDescription.MTIME_JOB_TYPE:
                // the connection is gone (the job runner's re-login failed): stop polling rather
                // than raising the error dialog every interval
                mtimeJobPending = false;
                stopRemoteMtimeTimer();
            break;

            // the user messed up the login - reenabled the connect button
			case GJobDescription.LOGIN_JOB_TYPE:
				if( fivLoginButton != null ) fivLoginButton.setEnabled(true);
			break;

            case GJobDescription.DELETE_JOB_TYPE:

                // a partially-failed ( recursive ) delete leaves unknown server
                // state - resync with a real re-list
                this.refresh(this);

            break;

            case GJobDescription.DOWNLOAD_JOB_TYPE:
            case GJobDescription.UPLOAD_JOB_TYPE:

                // if the batch's LAST transfer fails, jm_jobCompleted never runs its
                // coalesced refresh - do it here so earlier successes become visible
                if( !getJobManager().getTransferQueue().hasOtherJobsForOriginator(this, jd) )
                {
                    GGideonPanelController  fcnt = getBuddy();

                    if( fcnt instanceof GBrowserPanelController )
                    {
                        ((GBrowserPanelController)fcnt).refresh(this);
                    }
                }

            break;
        }

    }

}

public  void    refreshTable()
{
	
	if( GideonConfiguration.ENABLESORTING )
	{
		GFileItem	ci = getVirtualCWD();

		if( GideonConfiguration.SORTINGDEBUG ) System.out.println("Folder name is : " + ci.getName());

		// one-time setup
		if( !fConfiguredTable )
		{
			fivFileTable.setModel(this);

			rowSorter = new TableRowSorter<TableModel>(this);
			rowSorter.setComparator(0, new GNameComparator());
			rowSorter.setComparator(1, new GSizeComparator());
			rowSorter.setComparator(2, new GDateComparator());
			rowSorter.toggleSortOrder(0); // sort by name ascending
			fivFileTable.setRowSorter(rowSorter);

			// custom cell renderer for icons and formatting
			javax.swing.table.DefaultTableCellRenderer fileRenderer = new javax.swing.table.DefaultTableCellRenderer() {
				public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
					super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
					int modelRow = table.convertRowIndexToModel(row);
					GFileItem child = rowItem(modelRow);
					if( child == null ) return this;

					if( column == 0 ) {
						setIconTextGap(2);
						setHorizontalTextPosition(JLabel.RIGHT);
						setHorizontalAlignment(JLabel.LEFT);
						boolean noRead = !child.getIsFileLocal()
							&& child.getPermissions() != null
							&& !child.getPermissions().getOwnerHasRead();
						if( child.getIsDir() ) {
							if( child.getIsDesktop() ) {
								setIcon(GLocalized.DESKTOP_SMALL_ICON);
							} else {
								setIcon(noRead ? GLocalized.SMALL_FOLDER_NOACCESS_ICON : GLocalized.SMALL_FOLDER_ICON);
							}
						} else if( child.getIsLink() ) {
							setIcon(GLocalized.SMALL_LINK_ICON);
						} else if( child.getIsCDUP() ) {
							setIcon(GLocalized.CDUP_ICON);
						} else if( child.getIsApplication() ) {
							setIcon(GLocalized.SMALL_APP_ICON);
						} else if( child.getIsHardDrive() ) {
							setIcon(GLocalized.SMALL_HD_ICON);
						} else if( child.getIsRemovableMedia() ) {
							setIcon(GLocalized.SMALL_RM_ICON);
						} else if( child.getIsFloppy() ) {
							setIcon(GLocalized.SMALL_FLOPPY_ICON);
						} else {
							setIcon(noRead ? GLocalized.SMALL_FILE_NOACCESS_ICON : GLocalized.SMALL_FILE_ICON);
						}
						setText( child.getIsCDUP() ? "" : child.getName() );
					} else if( column == 1 ) {
						setIcon(null);
						setHorizontalAlignment(JLabel.RIGHT);
						if( child.getIsCDUP() ) {
							setText("");
						} else if( child.getIsFile() ) {
							setText(child.getHumanReadableSize());
						} else {
							setText("--");
						}
					} else if( column == 2 ) {
						setIcon(null);
						setHorizontalAlignment(JLabel.LEFT);
						if( child.getIsCDUP() || child.getDateModified() == null ) {
							setText("");
						} else {
							java.text.DateFormat df = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT);
							setText(df.format(child.getDateModified()));
						}
					}
					return this;
				}
			};
			fivFileTable.setDefaultRenderer(Object.class, fileRenderer);
			fivFileTable.setDefaultRenderer(Long.class, fileRenderer);
			fivFileTable.setDefaultRenderer(java.util.Date.class, fileRenderer);


			fConfiguredTable = true;
		}

		// swap in a fresh snapshot of the canonical children (roadmap 3.3), then
		// notify table that the data has changed (this clears the JTable selection),
		// then restore the previous selection by name so it survives the refresh
		rebuildTableRows();
		gbpRestoringSelection = true;
		fireTableDataChanged();
		restoreSelectionByName();
		gbpRestoringSelection = false;
	}

}

/*
*
*
* GBrowserPanelCommandInterface methods
*
*
*/

/**
    This method is used to point to the current user specified CWD. Because of the fact the
    browser allows the user to navigate back and forth through listed GFileItems, this class takes it
    upon itself to keep track of thsi "virtual" cwd. It is called virtual because the GConnection sees
    the session as the server sees the session. At times this "virtual" cwd is the same as the Gconnections
    other times it isn't.
    @param c  New Virtual CWD
*/
public void setVirtualCWD(GFileItem c )
{
    // record the last one

    // specify the new one
    fivCWD = c;

    // Reset mtime tracking when folder changes
    lastKnownMtime = -1;

    // keep the rendered rows in sync with the new folder
    rebuildTableRows();

}

private javax.swing.Timer remoteMtimeTimer;
/** EDT-only: an MTIME job is queued or running; the timer must not stack another behind it (leak audit #17). */
private boolean mtimeJobPending = false;

private void startRemoteMtimeTimer()
{
    stopRemoteMtimeTimer();
    lastKnownMtime = -1;

    // Poll interval is overridable (via -Drelaysftp.mtime.interval.ms) so UI tests can
    // drive the auto-refresh quickly; production uses the 15s default.
    int intervalMs = Integer.getInteger("relaysftp.mtime.interval.ms", 15000);
    remoteMtimeTimer = new javax.swing.Timer(intervalMs, e -> queueMtimeCheck());
    remoteMtimeTimer.setRepeats(true);
    remoteMtimeTimer.start();
}

/** Stop the keep-alive ping and the mtime poll: nothing may enqueue work on this pane's connection any more (quit). */
public void stopPollers()
{
    if( bpwPingTimer != null ) { bpwPingTimer.stop(); bpwPingTimer = null; }
    stopRemoteMtimeTimer();
}

private void stopRemoteMtimeTimer()
{
    if( remoteMtimeTimer != null )
    {
        remoteMtimeTimer.stop();
        remoteMtimeTimer = null;
    }
}

/**
 * Timer tick (EDT): queue one MTIME job on the task queue, which already serialises every job
 * on this connection (roadmap 3.1) — the poll can no longer interleave with a list/rename/chmod
 * on the shared SSH/FTP channel. If a poll is still queued or running, do nothing: the next
 * tick tries again (never more than one outstanding poll per pane).
 */
private void queueMtimeCheck()
{
    if( fivCWD == null || fivCWD.getIsFileLocal() || getConnection() == null ) return;
    if( mtimeJobPending ) return;

    GJobDescription job = new GJobDescription(GJobDescription.MTIME_JOB_TYPE, getConnection(), GLocalized.MTIME_JOB_TYPE);
    job.setTargetFile(fivCWD);
    job.setOriginator(this);
    mtimeJobPending = true;
    queueAndStartJob(job);
}

/** MTIME job completed (EDT): apply the reading the worker stored on the job. */
private void handleMtimeResult( final GJobDescription jd )
{
    mtimeJobPending = false;

    // the user moved on while the poll waited its turn — the reading is for the old folder
    if( jd.getTargetFile() != fivCWD ) return;

    Object v = jd.getProperty(GJobDescription.MTIME_RESULT_JOB_PROP);
    long mtime = ( v instanceof Long l ) ? l : -1L;
    if( mtime == -1 )
    {
        // Server/protocol doesn't support mtime — stop polling it (self-disable).
        stopRemoteMtimeTimer();
        return;
    }

    // On first successful check, disable ping timer (mtime acts as keep-alive)
    if( bpwPingTimer != null ) { bpwPingTimer.stop(); bpwPingTimer = null; }

    if( lastKnownMtime == -1 )
    {
        lastKnownMtime = mtime;
        return;
    }

    if( mtime != lastKnownMtime )
    {
        lastKnownMtime = mtime;
        // 3.2: re-list through the background job system; the list I/O runs on a worker and
        // the table update is marshalled back at job completion.
        if( fivCWD != null ) refresh( null );
    }
}

/**
    This method is used to point to the current user specified CWD. Because of the fact the
    browser allows the user to navigate back and forth through listed GFileItems, this class takes it
    upon itself to keep track of thsi "virtual" cwd. It is called virtual because the GConnection sees
    the session as the server sees the session. At times this "virtual" cwd is the same as the Gconnections
    other times it isn't.
    @return c  Users location.
*/
public GFileItem  getVirtualCWD()
{

    return fivCWD;

}

public JTable getFileTable()
{
    return fivFileTable;
}

/*
*
* GBrowserPanelCommandInterface implementations
*
*
*/

public GBookmark    getBookmark()
{
        
    GConnection c = this.getConnection();
    
    if( c == null ) return null;
         
    return c.getBookmark();

}

public void setConnection(GConnection c )
{
    
    fivConnection = c;
    
    // synch the bookmarks
    if(c!=null)
    {
        currentBookmark = c.getBookmark();
    }else{
        currentBookmark = null;
    }
    
}

public GConnection  getConnection()
{
    
    return fivConnection;

}

public void  setPermissions( final GFileItem[] fileItems, final GPermissions permissions, final boolean recrusive, final Object listener )
{

    // something else
    for( int i = 0 ; i < Array.getLength(fileItems) ; i++ )
    {
        GFileItem   cItem = (GFileItem)Array.get( fileItems, i );
        String name = GLocalized.CHMOD_JOB_TYPE + " \"" + cItem.getName() + "\"";
        // create a list and cwd job description
        GJobDescription chmodJob = new GJobDescription(GJobDescription.CHMOD_JOB_TYPE, this.getConnection(), name );
        chmodJob.addProperty( GJobDescription.PERMISSIONS_JOB_PROP, permissions );
        chmodJob.addProperty( GJobDescription.RECURSIVE_JOB_PROP, Boolean.valueOf(recrusive) );
        chmodJob.setTargetFile(cItem);
        chmodJob.setOriginator(this);
        this.queueAndStartJob(chmodJob);
    }

}

public void deleteItems( final GFileItem[] fileItems, final Object listener)
{

    for( int i = 0 ; i < Array.getLength(fileItems) ; i++ )
    {
        GFileItem   targetItem = (GFileItem)Array.get( fileItems, i );
        String name = GLocalized.DELETE_JOB_TYPE + " \"" + targetItem.getName() + "\"";
        // create a list and cwd job description
        GJobDescription deleteJob = new GJobDescription(GJobDescription.DELETE_JOB_TYPE, this.getConnection(), name );
        deleteJob.setTargetFile(targetItem);
        deleteJob.setOriginator(this);
        this.queueAndStartJob(deleteJob);
    }
    
}

public void  downloadTo( final GFileItem[] fileItems, final GFileItem targetFile, final Object listener )
{   
    GConnection bConnection =null;
    GFileItem   downloadLocation  = null;
        
    if( hasLocalSelection() )
    {
        bConnection = ((GBrowserPanelController)getBuddy()).getConnection();
    }else{
        bConnection = getConnection();
    }
        
    if( targetFile == null )
    {
        if( getBuddy() != null )
        {
            downloadLocation = ((GBrowserPanelController)getBuddy()).getVirtualCWD();
        }else{
            // remote-only window: no local pane, fall back to the user's Downloads folder
            java.io.File downloadsDir = new java.io.File( System.getProperty("user.home"), "Downloads" );
            downloadsDir.mkdirs();
            downloadLocation = new GFileItem( downloadsDir );
        }
    }else{
        downloadLocation = targetFile;
    }

    for( int i = 0 ; i < Array.getLength(fileItems) ; i++ )
    {
        GFileItem   targetItem = (GFileItem)Array.get( fileItems, i );
        String name = GLocalized.DOWNLOAD_JOB_TYPE + " \"" + targetItem.getName() + "\"";
        // create a list and cwd job description
        GJobDescription downloadJob = new GJobDescription(GJobDescription.DOWNLOAD_JOB_TYPE, bConnection, name );
        // target is the item we are downloading
        downloadJob.setTargetFile(targetItem);
        downloadJob.setSourceFile(downloadLocation);
        downloadJob.setOriginator(this);
        
        // configure this jd with the user specified tm
        handleTransferMode(downloadJob);
        queueAndStartJob(downloadJob);
    }
    
}

public void download( final GFileItem[] fileItems, final Object listener )
{
    downloadTo( fileItems, null, listener );
}

public void         uploadTo( final GFileItem[] sourceItems, final GFileItem targetFile, final Object listener )
{
    GConnection bConnection = null;
    GFileItem   uploadLocation = null;
    
    if( hasLocalSelection() )
    {
        bConnection = ((GBrowserPanelController)getBuddy()).getConnection();
    }else{
        bConnection = getConnection();
    }

    if( targetFile == null )
    {
        uploadLocation = ((GBrowserPanelController)getBuddy()).getVirtualCWD();
    }else{
        uploadLocation = targetFile;
    }
    
    for( int i = 0 ; i < Array.getLength(sourceItems) ; i++ )
    {
        GFileItem   targetItem = (GFileItem)Array.get( sourceItems, i );
        String name = GLocalized.UPLOAD_JOB_TYPE + " \"" + targetItem.getName() + "\"";
        // create a list and cwd job description
        GJobDescription uploadJob = new GJobDescription(GJobDescription.UPLOAD_JOB_TYPE, bConnection, name );
        // target is the item we are downloading
        uploadJob.setTargetFile(targetItem);
        uploadJob.setSourceFile(uploadLocation);
        uploadJob.setOriginator(this);
        // configure this jd with the user specified tm
        this.handleTransferMode(uploadJob);
        this.queueAndStartJob(uploadJob); 
    }

}


public void upload( final GFileItem[] sourceItems, final Object listener )
{

    uploadTo(sourceItems, null, listener);
    
}

private void handleTransferMode( GJobDescription j )
{
    Preferences prefs = Preferences.userNodeForPackage(GideonConfiguration.class);
    int tMode = prefs.getInt(GJobDescription.TRANSFERMODE_JOB_PROP, -1 );
    if(tMode==-1)
    {
        tMode=0;
    }
    Integer tModeObject = Integer.valueOf(tMode);
    j.addProperty(GJobDescription.TRANSFERMODE_JOB_PROP, tModeObject);
}

public void ping()
{
	// the pinger was deactivated so we ignore this ping and just return.
	if( bpwPingTimer == null )
	{
		System.out.println("Ping timer is null - not pinging");
		return;
	}

    GJobDescription pingJob = new GJobDescription(GJobDescription.PING_JOB_TYPE, this.getConnection(), GLocalized.PING_JOB_TYPE );
    pingJob.setOriginator(this);
    this.queueAndStartJob(pingJob);
}

public void setName( final String newName, final GFileItem fItem, final Object listener )
{
    
    String name = GLocalized.RENAME_JOB_TYPE + " \"" + newName + "\"";
    // create a list and cwd job description
    GJobDescription renameJob = new GJobDescription(GJobDescription.RENAME_JOB_TYPE, this.getConnection(), name );
    renameJob.addProperty( GJobDescription.NAME_JOB_PROP, newName );
    renameJob.setTargetFile(fItem);
    renameJob.setOriginator(this);
    this.queueAndStartJob(renameJob);

}

public boolean isConnected()
{
        boolean	retValue = false;
        
        GConnection	con = getConnection();
        
        if( con == null ) 
        {
                return false;
        }
        try{
                retValue = con.getIsConnected();
        }catch( GConnectionException e )
        {
        
        }
        
        return retValue;
}

public GFileItem	getWorkingFolder()
{
	return getVirtualCWD();
}

public void disconnect( final Object listener )
{
    
    // fire the browser is disconnecting event (listeners stay registered: the same pane is
    // reused after a disconnect, and the window's connected-listener must survive it)
    firePanelEvent( "browser-panel disconnecting", l -> l.bpl_disconnecting( this ) );
    
    String name = GLocalized.DISCONNECT_JOB_TYPE;
    // create a list and cwd job description
    GJobDescription disconnectJob = new GJobDescription(GJobDescription.DISCONNECT_JOB_TYPE, this.getConnection(), name );
    
    if( listener != null )
    {
        disconnectJob.setOriginator(listener);
    }else{
        disconnectJob.setOriginator(this);
    }

    // Stop the pollers BEFORE queueing the disconnect: an mtime tick queued behind DISCONNECT
    // would run on a logged-out connection and the job runner would silently log it back in.
    stopRemoteMtimeTimer();
    if( bpwPingTimer != null ) bpwPingTimer.stop();
    bpwPingTimer = null;

    queueAndStartJob(disconnectJob);
	
}


public void refresh( final Object listener )
{
    
    String name = GLocalized.REFRESH_JOB_TYPE;
    // create a list and cwd job description
    GJobDescription listJob = new GJobDescription(GJobDescription.REFRESH_JOB_TYPE, this.getConnection(), name );
    listJob.setTargetFile(this.getVirtualCWD());
    listJob.setOriginator(this);
    this.queueAndStartJob(listJob);
 
}

public void newFolder(  final String folderName, final Object listener )
{

    String name = GLocalized.NEWFOLDER_JOB_TYPE + " \"" + folderName + "\"";
    // create a list and cwd job description
    GJobDescription folderJob = new GJobDescription(GJobDescription.NEWFOLDER_JOB_TYPE, this.getConnection(), name );
    folderJob.addProperty( GJobDescription.NAME_JOB_PROP, folderName );
    folderJob.setTargetFile(this.getVirtualCWD());
    folderJob.setOriginator(this);
    this.queueAndStartJob(folderJob);

}

public  void    queueAndStartJob( GJobDescription theJob )
{

    GQueue          queue;
    GJobManager     sjm = getJobManager();
    
    // can happen - typically on shutdown
    if( sjm == null ) return;
    
    if( theJob.isTransferJob() )
    {
        queue = sjm.getTransferQueue();
        // Set max concurrent transfers from bookmark
        GConnection conn = theJob.getConnectionToUse();
        if( conn != null && conn.getBookmark() != null )
        {
            queue.setMaxActiveJobs(conn.getBookmark().getMaxConcurrentTransfers());
        }
    }else{
        queue = sjm.getTaskQueue();
    }

    queue.addJobToQueue(theJob);

    // tell the job manager to keep processing...
    sjm.startNextJobForQueue(queue);

}

/**
*
* other
*
*/

public GFileItem[]  getSelection()
{
    
    GFileItem[] fileItemArray=null;
    int         startIndex=0;
    int         endIndex=0;
    GFileItem   currentFI=null;
    int         j=0;
    int         i=0;
    int         length=0;
    
    if(fivFileTable==null) return null;
    
    // get the file tables list selection model
    ListSelectionModel  selModel = fivFileTable.getSelectionModel();
    
    // if we have an empty selection - return null.
    if( selModel.isSelectionEmpty() ) return null;
    
    // get the starting and ending selection
    startIndex = selModel.getMinSelectionIndex();
    endIndex = selModel.getMaxSelectionIndex();
    
	// get the virtual cwd
    currentFI = getVirtualCWD();
    
    if( currentFI == null ) return null;
	
	System.out.println("Start : " + startIndex);
    System.out.println("End : " + endIndex);

    if( startIndex==endIndex )
    {
		GFileItem		cFileItem;
		fileItemArray = (GFileItem[])Array.newInstance(GFileItem.class, 1);
	
		if( GideonConfiguration.ENABLESORTING )
		{
			int modelIndex = fivFileTable.convertRowIndexToModel(startIndex);
			cFileItem = rowItem(modelIndex);
		}else{
			cFileItem = rowItem(startIndex);
		}

		Array.set(fileItemArray,0,cFileItem);
		return fileItemArray;
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
	
	fileItemArray = (GFileItem[])Array.newInstance(GFileItem.class, length);
	
	
	// generate an array of items
	j=0;
	for( i = startIndex ; i <= endIndex ; i++ )
	{

		if (selModel.isSelectedIndex(i))
		{
			GFileItem   cFileItem = null;

			if( GideonConfiguration.ENABLESORTING )
			{
				int modelIndex = fivFileTable.convertRowIndexToModel(i);
				cFileItem = rowItem(modelIndex);
			}else{
				cFileItem = rowItem(i);
			}

			Array.set(fileItemArray,j++,cFileItem);
		}else{
		}

	}
	
    return fileItemArray;

}

public boolean      hasLocalSelection()
{

    GFileItem   vwd = this.getVirtualCWD();
    
    if( vwd != null )
    {
        return vwd.getIsFileLocal();
    }
    
    return false;

}

public boolean      hasSelection()
{

    if( fivFileTable.getSelectedRowCount() > 0 )
    {
        return true;
    }

    return false;

}

public boolean      hasMultipleSelection()
{

    if( fivFileTable.getSelectedRowCount() > 1 )
    {
        return true;
    }

    return false;

}

public GFileItem getItemAt( int row, int column )
{

    return rowItem(row);

}

public GDataArray getFavoriteFolderList()
{
    GBookmark   bm = getBookmark();

    if( bm == null ) return null;

    return bm.getFavoritePaths();
}

private void showFavoritesPopup(JButton source)
{
    GDataArray favItems = getFavoriteFolderList();
    JPopupMenu menu = new JPopupMenu();

    int count = (favItems != null) ? favItems.getItemCount() : 0;

    if( count == 0 )
    {
        JMenuItem empty = new JMenuItem(GLocalized.NOFAVORITES);
        empty.setEnabled(false);
        menu.add(empty);
    }else{
        for( int i = 0 ; i < count ; i++ )
        {
            GBookmarkFavoritePath cItem = (GBookmarkFavoritePath)favItems.getItemAtIndex(i);
            String name = (String)cItem.getObjectForKey(GBookmarkFavoritePath.FAVORITE_NAME);
            String path = (String)cItem.getObjectForKey(GBookmarkFavoritePath.FAVORITE_PATH);
            JMenuItem mi = new JMenuItem(name);
            mi.addActionListener(e -> {
                if( hasLocalSelection() )
                {
                    changeToFolder(buildLocalFileItemWithParents(new java.io.File(path)));
                }else{
                    String folderName = GStringUtilities.getLastPathComponent(path);
                    GFileItem remoteFolder = new GFileItem(folderName, path, getConnection());
                    remoteFolder.setFileType(GFileItem.G_FOLDER);
                    changeToFolder(remoteFolder);
                }
            });
            menu.add(mi);
        }
    }

    menu.show(source, 0, source.getHeight());
}

/**
 * Build a GFileItem for a local File, reusing cached items from
 * the existing tree where possible. Only creates new GFileItems
 * for path segments that haven't been visited yet.
 *
 * @param file   the target folder
 * @param treeRoot  the root of the current browser tree (walk up from CWD), or null
 */
static GFileItem buildLocalFileItemWithParents(java.io.File file, GFileItem treeRoot)
{
    // Build path segments from target up to filesystem root
    java.util.ArrayList<java.io.File> chain = new java.util.ArrayList<>();
    java.io.File f = file;
    while( f != null )
    {
        chain.add(f);
        f = f.getParentFile();
    }

    // Walk from root down, reusing cached GFileItems where they exist
    GFileItem current = treeRoot;
    for( int i = chain.size() - 1; i >= 0; i-- )
    {
        java.io.File segment = chain.get(i);
        String segName = segment.getName();

        // At root level (empty name), try to match treeRoot directly
        if( segName.isEmpty() )
        {
            if( current != null && current.getCompletePath() != null &&
                current.getCompletePath().equals(segment.getAbsolutePath()) )
            {
                continue;
            }
            // No match — create new
            GFileItem item = new GFileItem(segment);
            current = item;
            continue;
        }

        // Try to find this segment as a child of current
        GFileItem cached = (current != null) ? current.getChildNamed(segName) : null;
        if( cached != null )
        {
            current = cached;
        }
        else
        {
            GFileItem item = new GFileItem(segment);
            if( current != null )
            {
                item.setParentFileItem(current);
            }
            current = item;
        }
    }
    return current;
}

/** Convenience: build with parent chain using this panel's tree root. */
GFileItem buildLocalFileItemWithParents(java.io.File file)
{
    // Walk up from CWD to find the tree root
    GFileItem root = getVirtualCWD();
    if( root != null )
    {
        while( root.getParentFileItem() != null )
        {
            root = root.getParentFileItem();
        }
    }
    return buildLocalFileItemWithParents(file, root);
}

/*
*
* END GBrowserPanelCommandInterface implementations
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
public int getColumnCount(){ return 3; }

public int getRowCount()
{

    return gbpTableRows.size();

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
        
        value = GLocalized.SIZE_COLUMN_TITLE;
        
    }else if( column==2 )
    {
        
        value = GLocalized.DATE_COLUMN_TITLE;
        
    }else{
        return "!UNDEFINED";
    }
    
    return value;

}

/**
 * Returns the type for column number <code>column</code>.
 */
public Class getColumnClass(int column)
{ 

    Class  value;
    
    if(column==1){

        value = Long.class;

    }else if( column==2 )
    {

        value = Date.class;

    }else{
        return String.class;
    }
    
    return value;

}

public Object getColumnValue( Object childObject, int column )
{
    GFileItem child = (GFileItem)childObject;

    if( column == 0 )
    {
        return child.getIsCDUP() ? "" : child.getName();
    }
    else if( column == 1 )
    {
        return child.getIsCDUP() ? Long.valueOf(-1) : Long.valueOf(child.getRawSize());
    }
    else if( column == 2 )
    {
        return child.getIsCDUP() ? null : child.getDateModified();
    }

    return "";
}

/**
 * Returns the value to be displayed for node <code>node</code>,
 * at column number <code>column</code>.
 */
public Object getValueAt(int row, int column)
{

    GFileItem   child = rowItem(row);
    if( child == null ) return "";
	return getColumnValue(child, column );

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
    treeModelListeners.add(l);
}

public void removeTableModelListener(TableModelListener l) {
    treeModelListeners.remove(l);
}

private void fireTableDataChanged() {
    javax.swing.event.TableModelEvent e = new javax.swing.event.TableModelEvent(this);
    for( int i = 0; i < treeModelListeners.size(); i++ ) {
        ((TableModelListener)treeModelListeners.get(i)).tableChanged(e);
    }
}

public void applyFilter(String text) {
    if( rowSorter == null ) return;
    if( text == null || text.trim().isEmpty() ) {
        rowSorter.setRowFilter(null);
    } else {
        rowSorter.setRowFilter(RowFilter.regexFilter(
            "(?i)" + java.util.regex.Pattern.quote(text), 0));
    }
}

public  void    changeToFolder( GFileItem selectedItem )
{	
	realChangeToFolder( selectedItem, true );
}

private  void    realChangeToFolder( GFileItem selectedItem, boolean doFollowMe )
{
        String          name    = null;
        GFileItem       nFI     = null;
        GJobManager     sjm     = getJobManager();
        GJobDescription listJob = null;
		
        reallyFollowMe = doFollowMe;
		
        // if the item is not listed then list it.
        if( selectedItem.getFileState() == GFileItem.STATE_NOT_LISTED )
        {
            
            System.out.println("Processing UnListed Item");
        
            name = GLocalized.LIST_JOB_TYPE + " \"" + selectedItem.getName() + "\"";
            // create a list and cwd job description
            listJob = new GJobDescription(GJobDescription.CHANGE_AND_LIST_JOB_TYPE, this.getConnection(), name );
            listJob.setTargetFile(selectedItem);
            listJob.setOriginator(this);
            queueAndStartJob(listJob);
           
        }else if( selectedItem.getFileState() == GFileItem.STATE_LISTED ) 
        {
        
            System.out.println("Processing Listed Item");

            // change our virtual cwd to the double clicked
            setVirtualCWD(selectedItem);
		
            // fill in the sexy
            updateFileCountArea(selectedItem);
			
            // update the item count
            //fileCountField.setText( selectedItem.getChildCount() + " " + GLocalized.ITEMS_STRING );
            // refresh the table
            System.out.println("Refreshing Table");
            refreshTable();
            
            // enable/disable the back button
            updateButtonAndDropDown();
			
            if( GideonConfiguration.FOLLOWMEENABLED && doFollowMe )
            {
                    GGideonPanelController  cnt = getBuddy();
                    if( cnt instanceof GBrowserPanelController )
                    {
                            ((GBrowserPanelController)cnt).changeToFolderNamed(selectedItem.getName());
                    }
            }
            
        }

}

public void changeToFolderNamed( String folderName )
{

	GFileItem	fi = getVirtualCWD();
	
	GFileItem	cFI = fi.getChildNamed(folderName);
	
	if( cFI != null )
	{
		realChangeToFolder( cFI, false );
	}else{
		// folder doesn't exist in other view - turn off follow me
		GideonConfiguration.FOLLOWMEENABLED = false;
	}
	
	return;

}

private void    goToParentFolder()
{   
	realChangeToFolder(getVirtualCWD().getParentFileItem(), false);
}

/*
*
* Mouse Listener
*
*/

public  void    mousePressed( MouseEvent e )
{

    String          name    = null;
    GFileItem       nFI     = null;
    GJobManager     sjm     = getJobManager();
    GJobDescription listJob = null;
    AWTEvent        ev      = null;
    
    if( evaluatePopup(e) )
    {
        return;
    }

    int selRow = fivFileTable.getSelectedRow();

    if( selRow != -1 )
    {

        GFileItem selectedItem = null;
		
		if( GideonConfiguration.ENABLESORTING )
		{
			int modelIndex = fivFileTable.convertRowIndexToModel(selRow);
			selectedItem = rowItem(modelIndex);
		}else{
			selectedItem = rowItem(selRow);
		}

        if( e.getClickCount() == 1 )
        {

            if( GideonConfiguration.BROWSER_TREE_DEBUG ) System.out.println("User clicked : " + selectedItem.getName());
            // brilliant focus management
            setHasFocus(true);

            if( hasMultipleSelection() )
            {
                    e.consume();
                    return;
            }

            if( getBuddy()!=null ) ((GGideonPanelController)getBuddy()).setHasFocus(false);

        }else if( e.getClickCount() == 2 )
        {

            if( GideonConfiguration.BROWSER_TREE_DEBUG )
            {
                System.out.println("User double clicked : \"" + selectedItem.getName() + "\" id : \"" + selectedItem.hashCode() + "\"");
                
            }
            // handle processing of the special "change to parent" GFileItem
            if( selectedItem.getIsCDUP() )
            {
                this.goToParentFolder();
            
            // if it's listable - list it.
            }else if( selectedItem.getIsListable() )
            {
                this.changeToFolder(selectedItem);
            }else{
                ((GBrowserWindowCommandInterface)getDelegate()).userDoubleClickedFile(selectedItem);
            }
            
        }
        
    }else{
        
        // so the user can create folders etc...
        fivFileTable.grabFocus();
        setHasFocus(true);
        if( getBuddy()!=null ) ((GGideonPanelController)getBuddy()).setHasFocus(false);
        ((GBrowserWindowCommandInterface)delegate).validateToolbar();

    }

}


public  void    mouseClicked( MouseEvent e )
{

}


public  void    mouseReleased( MouseEvent e )
{

    evaluatePopup(e);

}
public  void    mouseEntered( MouseEvent e ){}
public  void    mouseExited( MouseEvent e ){}

private boolean evaluatePopup(MouseEvent e) 
{

       if (e.isPopupTrigger()) {

            GFileItem[] sel = getSelection();
        
            if( ( sel != null ) && Array.getLength(sel) <= 1 )
            {
                // select the file under the menu
                int r = fivFileTable.rowAtPoint(e.getPoint());
                fivFileTable.getSelectionModel().setSelectionInterval(r,r);
            }/*else{
            
                if( !hasFocus() )
                {
                    fivFileTable.grabFocus();
                    int r = fivFileTable.rowAtPoint(e.getPoint());
                    fivFileTable.getSelectionModel().setSelectionInterval(r,r);
                }
                
            }*/
            
            if( gbpPUM == null )
            {
                if( delegate == null ) return false;
            
                gbpPUM = ((GPopupMenuProviderInterface)delegate).popupMenu(sel);
                
                if( gbpPUM == null ) return false;
                
                gbpPUM.addPopupMenuListener(this);
            }

            gbpPUM.show(fivFileTable, e.getX(), e.getY() );

            // flag the popup as showing so getfocus returns true ( the menu takes focus )
            setPopupShowing(true);
            
            return true;
    }
    
    return false;

}

public JPopupMenu   getPopupMenuForSelection()
{
    GFileItem[] sel = getSelection();
    
    if( sel == null )
    {
        System.out.println("selection was null");
        return null;
    }
    
    return ((GPopupMenuProviderInterface)delegate).popupMenu(sel);
}

public  boolean getPopupShowing()
{
    return gbpPopupShowing;
}

public void setPopupShowing(boolean popupShowing)
{
    gbpPopupShowing = popupShowing;
}

public void popupMenuWillBecomeInvisible( PopupMenuEvent e )
{

}

public void popupMenuCanceled( PopupMenuEvent e )
{

    if(gbpPopupShowing) setPopupShowing(false);

}

public void popupMenuWillBecomeVisible( PopupMenuEvent e )
{
}

/*
*
*
*   GBPLoginPanelActionListener
*
*   Handles processing the login panel actions
*
*
*/

public  class   GBPLoginPanelActionListener implements ActionListener
{

    private GBrowserPanelController lpal;

    public  GBPLoginPanelActionListener( GBrowserPanelController bpc )
    {
            lpal = bpc;
    }

    public  void    actionPerformed( ActionEvent e )
    {
        GBookmark   n = null;

        fivLoginButton.setEnabled(false);

        // if we have been provided a bookmark from a connection - use that.
        // otherwise create a populate a new one.
        if( currentBookmark == null )
        {
            n = new GBookmark( "somehost.com", null, null, null, 0, GBookmark.G_UNKNOWN_PROTOCOL );
        }else{
            n = currentBookmark;
        }

        if( GideonConfiguration.DEBUG_BROWSERPANEL ) System.out.println("LoginPanelActionListener->actionPerformed");

        // Delegate to the active connection panel
        GConnectionPanelInterface activePanel = lpal.getActiveConnectionPanel();
        if (activePanel != null) {
            activePanel.applyToBookmark(n);
        }

        if( GideonConfiguration.DEBUG_BROWSERPANEL )
        {
            System.out.println("Host information");
            System.out.println("\tProtocol : " + n.getProtocol());
            System.out.println("\tHost : " + n.getHostname());
            System.out.println("\tUsername : " + n.getUsername());
            System.out.println("\tPort : " + n.getPort());
            System.out.println("\tFolder : " + n.getHomePath());
            System.out.println("End Host information");
        }

        lpal.connectForBookmark(n);

    }

}


}
