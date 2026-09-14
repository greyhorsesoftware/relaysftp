//
//	File:	GBrowserWindow.java
//

package com.gideonsoftworks.gideon.browser;

import com.gideonsoftworks.gideon.core.GStatusPanelController;
import com.gideonsoftworks.gideon.toolbar.GToolbarItem;
import com.gideonsoftworks.gideon.preferences.GSFTPPreferences;
import com.gideonsoftworks.gideon.toolbar.GCancelToolbarItem;
import com.gideonsoftworks.gideon.toolbar.GActivityToolbarItem;
import com.gideonsoftworks.gideon.toolbar.GToolbar;
import com.gideonsoftworks.gideon.core.GActivityPanelController;
import com.gideonsoftworks.gideon.core.GFrame;
import com.gideonsoftworks.gideon.core.GFrameManager;
import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.core.GideonConfiguration;
import com.gideonsoftworks.gideon.core.GMenuBar;
import com.gideonsoftworks.gideon.core.GGideonPanelController;
import com.gideonsoftworks.gideon.actions.GUIAction;
import com.gideonsoftworks.gideon.logviewer.GLogManager;
import com.gideonsoftworks.gideon.logviewer.GLogWindow;
import com.gideonsoftworks.gideon.actions.GCancelUIAction;
import com.gideonsoftworks.gdo.*;
import com.gideonsoftworks.gja.*;
import com.gideonsoftworks.gcore.*;
import com.gideonsoftworks.gideon.widgets.*;
import com.gideonsoftworks.tabkit.GTabBarItem;

import com.gideonsoftworks.gideon.core.GPopupMenuProviderInterface;

import java.awt.*;
import com.gideonsoftworks.gideon.mirroring.GMirrorPanel;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.table.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.prefs.*;
import java.io.*;
import javax.swing.event.*;

/**
  Browser window class in GSFTP.
  
  Note: See GBrowserEvent.java for events this subclass of GFrame posts.
*/

public class GBrowserWindow extends GFrame implements FocusListener, GBrowserWindowCommandInterface, GBrowserPanelListenerInterface, GPopupMenuProviderInterface
{
    static  private     final	String          REMOTE_BROWSER_KEY		=	"REMOTE_BROWSER_KEY";
    static  private     final	String          LOCAL_BROWSER_KEY		=	"LOCAL_BROWSER_KEY";
    static  private	final	String		CONNECTION_SET			=	"CONNECTION_SET";
	
    protected JPanel					  rootPanel;			// panel that contains browser/tab view and status area
    protected GStatusPanelController      gbwStatusAreaPanel=null;
    protected GConnectionSetManager       gbwConnectionManager = new GConnectionSetManager();
    protected JSplitPane                  browserLogPanel=null;
    protected JButton                     mirrorItem=null;       // Mirror toolbar button; enabled only when remote is connected

    // Flags
    protected boolean                     fLocalFilesVisible = false;

    // Toolbar search field — placeholder reflects which pane a search will target
    private javax.swing.JTextField        fSearchField = null;
    protected boolean                     fRemoteOnly = false;

    public GBrowserWindow( GFrameManager m )
    {
        super( m );
	// turn on tabs for this window type

        setUseTabs(true);
        setShowsMenu(true);
    }
    
    public HashMap getFrameSettings() 
    {
        HashMap settings = new HashMap();
        settings.put(GFrame.MINIMUM_WIDTH, Integer.valueOf(795) );
        settings.put(GFrame.MINIMUM_HEIGHT, Integer.valueOf(570) );
        return settings;
    }
    
    public GBrowserWindow( GFrameManager m, GBookmark bm )
    {

        this(m);
		
        Container       contentPane     = null;
        JMenuBar        mBar            = null;
        GConnectionSet  cs              = null;

        if( m == null )
        {
            throw new NullPointerException("GBrowserWindow-GBrowserWindow : frame manager was null");
        }

        /*
        *
        *
        * Create browser connection manager and register as a listener.
        *
        *
        */            

        if( GideonConfiguration.REMEMBERWINDOSIZES )
        {
            Dimension size = GSFTPPreferences.getDefaultWindowSize();
            Point     location = GSFTPPreferences.getDefaultWindowLocation();
            if ( size != null && location != null )
            {
                setBounds((int)location.getX(), (int)location.getY(), (int)size.getWidth(), (int)size.getHeight());
                gfBoundsRestored = true;   // a saved position was applied — don't re-center in show()
            }
        }

        //setSideBarVisible(false);

    }

    public GBrowserWindow( GFrameManager m, GBookmark bm, boolean remoteOnly )
    {
        this(m, bm);
        fRemoteOnly = remoteOnly;
    }

	//
    //
    // Return the local view controller for this window
	//
	//
    public  GBrowserPanelController   localViewController()
    {
        GBrowserPanelController lb = new GBrowserPanelController((GFrame)this);
        lb.getPanel().setMinimumSize(new Dimension(0, 0));
        lb.showBrowserPanel();
        lb.setDelegate(this);
        lb.addFocusListener(this);
        return lb;
    }
    
    public  GBrowserPanelController   remoteViewController()
    {
        GBrowserPanelController rb = new GBrowserPanelController((GFrame)this);
        rb.getPanel().setMinimumSize(new Dimension(0, 0));
        rb.addFocusListener(this);
        rb.setDelegate(this);
        rb.showLoginPanel(null);
        return rb;
    }
	
	/**
        The only required one is TAB_PANEL_KEY. Which is used to create a 
		panel. The subclasser can also place other items into the hash as this hash is an argument
		to all tab methods. This method places adds variables for local, remote view and connection set.
    */
	public boolean	newTabCreated( final GTabBarItem item )
	{
        return true;
	}

    // return an 
    public  JComponent  getContentView( final GTabBarItem tbI )
    {
        JComponent		browserPanel	= null;
		GJobManager		gbwJobManager	= getJobManager();

		JPanel	browserRootPanel = new JPanel(new BorderLayout());
		browserRootPanel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));

		GBrowserPanelController   localBrowser = null;
		if( showsLocalFileArea() )
		{
			localBrowser = localViewController();
		}

		GBrowserPanelController   remoteBrowser  = remoteViewController();

		// add us as a listener for connect/disconnect events (typed; not cleared on disconnect)
		remoteBrowser.addBrowserPanelListener( this );

        // store some attributes...
        tbI.setAttribute(GBrowserWindow.LOCAL_BROWSER_KEY, localBrowser);
        tbI.setAttribute(GBrowserWindow.REMOTE_BROWSER_KEY, remoteBrowser);
        
		// wire the two panels together for d&d and download/upload negotiations
		if( remoteBrowser != null ) remoteBrowser.setBuddy(localBrowser);
		if( localBrowser != null )
		{
			localBrowser.setBuddy(remoteBrowser);
			localBrowser.setHasFocus(true);
		}

		// create split pane with local and remote file listers
		if( localBrowser != null )
		{
			browserPanel = (JComponent)new JSplitPane( JSplitPane.HORIZONTAL_SPLIT, localBrowser.getPanel(), remoteBrowser.getPanel() );
			((JSplitPane)browserPanel).setContinuousLayout(true);
			((JSplitPane)browserPanel).setResizeWeight(0.5);
			((JSplitPane)browserPanel).setBorder( BorderFactory.createEmptyBorder() );
		}else{
			browserPanel = (JComponent)remoteBrowser.getPanel();
			setLocalFilesVisible(false);
		}

		browserRootPanel.add(browserPanel);

        // add status area to bottom of master panel
        GStatusPanelController	gbwStatusAreaPanel = new GStatusPanelController(gbwJobManager);
        browserRootPanel.add(gbwStatusAreaPanel.getPanel(), BorderLayout.PAGE_END);

        lastContentPanel = browserRootPanel;   // for the in-place mirror swap (non-tabbed case)
        return browserRootPanel;

    }

    public  GTabBarItem   getCustomTab()
    {
        return null;
    }

    // ---- in-place mirror workspace (design §11) ----

    private JComponent  lastContentPanel;    // browserRootPanel (non-tabbed case)
    private Container   mirrorContent;       // the panel whose CENTER we swapped
    private Component   mirrorSavedCenter;   // the dual-pane split we swapped out

    /** Replace the dual-pane browser with the mirror workspace (Configure → Preflight → Review). */
    /**
     * The Mirror toolbar button is a plain JButton (not a validated toolbar action), so refresh its
     * enabled state here — same gate as the GA_MIRROR menu item: only when the remote pane is logged
     * into an actual host (a FILE/local plugin reports connected, so require getIsRemote() too).
     */
    @Override
    public void validateToolbar()
    {
        super.validateToolbar();
        if( mirrorItem == null ) return;
        boolean enabled = false;
        try
        {
            GBrowserPanelCommandInterface rb = getRemoteBrowser();
            GConnection con = ( rb != null ) ? rb.getConnection() : null;
            enabled = con != null && con.getIsRemote() && con.getIsConnected();
        }
        catch( Exception e ) { enabled = false; }
        mirrorItem.setEnabled( enabled );
    }

    /** Mirror toolbar button: enter the mirror view, or leave it if it's already showing. */
    public void toggleMirrorView()
    {
        if( mirrorContent != null ) exitMirrorView();   // removeNotify() on the panel handles its own teardown
        else                        enterMirrorView();
    }

    public void enterMirrorView()
    {
        if( mirrorContent != null ) return;   // already in the mirror view
        Container content = currentContentPanel();
        if( content == null || !( content.getLayout() instanceof BorderLayout ) ) return;

        BorderLayout bl = (BorderLayout) content.getLayout();
        Component center = bl.getLayoutComponent( BorderLayout.CENTER );
        if( center == null ) return;

        GMirrorPanel mirror = new GMirrorPanel( this );
        mirrorContent     = content;
        mirrorSavedCenter = center;
        content.remove( center );
        content.add( mirror, BorderLayout.CENTER );
        content.revalidate();
        content.repaint();
    }

    /** Restore the dual-pane browser in place (called by the mirror panel on cancel/finish). */
    public void exitMirrorView()
    {
        if( mirrorContent == null || mirrorSavedCenter == null ) return;
        BorderLayout bl = (BorderLayout) mirrorContent.getLayout();
        Component cur = bl.getLayoutComponent( BorderLayout.CENTER );
        if( cur != null ) mirrorContent.remove( cur );
        mirrorContent.add( mirrorSavedCenter, BorderLayout.CENTER );
        mirrorContent.revalidate();
        mirrorContent.repaint();
        mirrorContent     = null;
        mirrorSavedCenter = null;
    }

    private Container currentContentPanel()
    {
        if( useTabs() && getCurrentTab() != null && getCurrentTab().getTabview() instanceof Container )
            return (Container) getCurrentTab().getTabview();
        return lastContentPanel;
    }

	// in this method we have to wire up the ui and perform any
	// startup
	public void tabWillBeAdded( final GTabBarItem tab )
	{
		// custom tabs (e.g. Jobs) don't need connection setup
		if( tab.getAttribute(LOCAL_BROWSER_KEY) == null && tab.getAttribute(REMOTE_BROWSER_KEY) == null )
		{
			return;
		}

		GConnectionSet	cs			=   new GConnectionSet(GLocalized.DEFAULTCONNECTIONNAME);
		GConnection		nConnection	=	null;
		//JComponent		tab			=	(JComponent)tab.getTabview();
		
		try{
			
			GBookmark myBookmark = new GBookmark("local", GSFTPPreferences.defaultLocalHomeFolder(), "user", "password", 0, GBookmark.G_FILE_PROTOCOL );
			  
			// 091705 - get our favorite paths array and assign it to the bookmark 
			// that way all "Add to local favorites" works
			GDataArray  nDA = GBookmarkManager.getSharedLocalFavoritePaths();
			myBookmark.setFavoritePaths( nDA );
			//
			
			nConnection = new GConnection( myBookmark );
			
		}catch(GConnectionException e)
		{
		}
		
		cs.setLocalConnection(nConnection);

        // if the connection set does not exist in the active list
        // we add it here - else we may be dealing with the case
        // where the connection was not "connected" so we reuse it
        if( gbwConnectionManager.containsConnectionSet(cs) == false )
        {
            // add the connectionset and make it key
            gbwConnectionManager.addConnectionSet(cs, true);
        }

        //
        // connect the local/remote browsers
        //
        
        // connect the local browser
        GBrowserPanelCommandInterface   localBrowser = (GBrowserPanelCommandInterface)tab.getAttribute(LOCAL_BROWSER_KEY);
        
        if( localBrowser != null )
        {
            localBrowser.connectionToConnection( cs.getLocalConnection() );
        }
			
		// record the connection set in our tab info
		tab.setAttribute(GBrowserWindow.CONNECTION_SET, cs );
			
		
	}
	
	public void tabSelected( final GTabBarItem tab )
	{

		GConnectionSet	cs = (GConnectionSet)tab.getAttribute(GBrowserWindow.CONNECTION_SET);

		// custom tabs (e.g. Jobs) don't have a connection set — skip connection logic
        if( cs == null )
        {
            return;
        }
        
		if( GideonConfiguration.CONNECTIONSWITCHING ) System.out.println("\tConnection Set : " + cs);

        System.out.println("\tMaking connection set key : " + cs.hashCode() );
        
        // make the connection set key
        // retain old connection set - we are moving to a new tab
        // don't do that last ting
        gbwConnectionManager.setActiveConnectionSet( cs );
   
		updateTitleAndStatusForConnectionSet(cs);
		
        if( GideonConfiguration.CONNECTIONSWITCHING ) System.out.println("Done assigning new connection set");
		
        System.out.println("GBrowserWindow::tabSelected exiting");

	}
	
	/**
        This method changes window title and sets the secure lock icon if required.
    */
	public void updateTitleAndStatusForConnectionSet( final GConnectionSet cs )
	{
			     
		GConnection remoteConnection = cs.getRemoteConnection();
		
        // update our title
        setTitle(GLocalized.DEFAULTBROWSERNAME + " - " + cs.getName());
        
		// update tab title too
		GTabBarItem tab = getCurrentTab();
		
		if(tab == null)
		{
			throw new NullPointerException("updateTitleAndStatusForConnectionSet - could not get current tab");
		}
		
		tab.setTitle(cs.getName());
		updateTabTitle(tab);
		
        // the gideon browser shows a lock for a secure ( SSL/SFTP ) host and a blue orb for normal FTP.
        // the status area is responsible for this icon. here we simply tell the status area
        // if the key connection is secure or not
        boolean secureHost = false;
        
        if( remoteConnection != null &&
            remoteConnection.getIsSecure() )
        {
            secureHost = true;   
        }
		
		System.out.println("Host is secure : " + secureHost);
		
        if( gbwStatusAreaPanel == null ) return;
    
        // let the status view know about the nature of our connection
        gbwStatusAreaPanel.setIsSecureRemoteHost(secureHost);

	}
	
	/**
        Return yes to show local files along with remote file area. Subclassers can
		override this to block the showing of a local file system view.
    */
	public	boolean	showsLocalFileArea()
	{
		return !fRemoteOnly;
	}
	
	/**
        At this point the following exists
			- a tab exists
			- tab specific connection set
    */
    public void show()
    {
        super.show();
		fireWindowEvent( "browser-window opening", l -> l.bwl_browserOpening( this ) );

	}
	
    /**
    *
    * Close key tab
    *
	*	This method is silly. it does alot of the same work the connection set manager callbacks do.
	*
    */
    public void tabWillClose( HashMap tabInfo )
    {
				
		GConnectionSet	cs = (GConnectionSet)tabInfo.get(GBrowserWindow.CONNECTION_SET);
		
		if(cs == null)
		{
			throw	new NullPointerException("tabWillClose - connection set is null");
		}
		
        // remove the connection set from the connection set manager
        // w/o notifying listeners or auto assigning a new key one
        // we handle that below.
        gbwConnectionManager.removeConnectionSetNoAutoSet(cs);
		
        // reap this connection set
        gbwConnectionManager.reap(cs);
				
    }
    
    // ---- typed window listeners (replaces the reflection event center, roadmap Phase 4) ----

    private final GListenerList<GBrowserWindowListenerInterface> windowListeners = new GListenerList<>();

    @Override
    public void addBrowserWindowListener( GBrowserWindowListenerInterface l )    { windowListeners.add( l ); }

    @Override
    public void removeBrowserWindowListener( GBrowserWindowListenerInterface l ) { windowListeners.remove( l ); }

    private void fireWindowEvent( String context, Consumer<GBrowserWindowListenerInterface> call )
    {
        windowListeners.dispatch( context, call );
    }

    // The key browser pane changed; reflect it in the search field's placeholder so the
    // user can see whether a search will apply to the "remote" or "local" pane.
    private void updateSearchScope( Object panel )
    {
        if( fSearchField == null ) return;

        String hint = "Search";
        if( panel == getRemoteBrowser() )      hint = "remote";
        else if( panel == getLocalBrowser() )  hint = "local";

        fSearchField.putClientProperty( "JTextField.placeholderText", hint );
        fSearchField.repaint();
    }


    /**
    *
    * Have our super create a job manager for us.
    *
    */
    public boolean  needsJobManager()
    {
        return true;
    }
    
    /*
    *
    * GBrowserPanelController event callbacks
    *
    */
    
	/**
        In this method we tell the log area to update
    */
    @Override
    public void bpl_connected( GBrowserPanelController panel )
    {
		
		GConnectionSet  cs = gbwConnectionManager.getActiveConnectionSet();

        GBrowserPanelCommandInterface   bp = panel;
        
        if( bp == null )
        {
            throw new NullPointerException("browser_connectionIsConnecting - browser panel was null");
        }
        
        GConnection                     conn = bp.getConnection();
        
        if( conn != null )
        {
            // register log with central log manager
            GLogManager.shared().registerLog(
                conn.getLog().getName(), conn.getLog());
            // register connection stats in log window
            GLogWindow.registerConnection(
                conn.getLog().getName(), conn);
            // assign the connection to the remote connection of the active connection set
            cs.setRemoteConnection(conn);
        }
		
		updateTitleAndStatusForConnectionSet(cs);
        
    }

    /**
    *
    * - If a user disconnects a connection and has others running the previous connection is selected.
    * - If a user disconnects a connection and has no other connections - we show the login panel
    *
    */
    
    public void disconnectBrowser( GBrowserPanelCommandInterface b )
    {
        if( b == null ) return;

        b.disconnect(null);
        b.setConnection(null);
        b.showLoginPanel(null);
    }
 
    /*At this point the following exists
			- a tab exists
			- tab specific connection set
    */
	public void openBookmark(GBookmark bm)
    {
        
        GConnectionSet  cs = null;
        
		
        /*
        *
        *
        * Create browser connection manager and register this and the dashboard panel as listeners.
        *
        *
        */
        if( bm != null )
        {
		
			System.out.println("Bookmark is NOT null");
			
            try{
                // remote connection
                GConnection rConnection = new GConnection( bm );
            
                if( GideonConfiguration.CONNECTIONSWITCHING )System.out.println("\tRemote Hash : " + rConnection.hashCode() );

                String homePath = bm.getLocalHomePath();
                
                if( homePath == null )
                {
                    homePath=GSFTPPreferences.defaultLocalHomeFolder();
                }
				
                GBookmark localBM = new GBookmark("local", homePath, null, null, 0, GBookmark.G_FILE_PROTOCOL );
                
                // 091705 - get our favorite paths array and assign it to the bookmark 
                // that way all "Add to local favorites" works
                GDataArray  nDA = GBookmarkManager.getSharedLocalFavoritePaths();
                localBM.setFavoritePaths( nDA );
                // 
                
                GConnection lConnection = new GConnection( localBM );
               
                if( GideonConfiguration.CONNECTIONSWITCHING )System.out.println("\tLocal Hash : " + lConnection.hashCode() );
                
                cs = gbwConnectionManager.getActiveConnectionSet();

                cs.setLocalConnection(lConnection);
				cs.setRemoteConnection(rConnection);
				cs.setName(bm.getName());
   
            }catch(GConnectionException e)
            {
            }
            
        }

        //
        // connect the local/remote browsers
        //
       
        // connect the local browser
        GBrowserPanelCommandInterface   localBrowser = getLocalBrowser();
        
        if( localBrowser != null )
        {
            localBrowser.connectionToConnection( cs.getLocalConnection() );
        }
        
        // connect the remote browser
        GBrowserPanelCommandInterface   remoteBrowser = getRemoteBrowser();
        
        if( remoteBrowser != null )
        {
            remoteBrowser.connectionToConnection( cs.getRemoteConnection() );
        }
 
    }
	
	/**
	*  used by GBookmarkUIAction.java to know when to create a new tab
	*  and when to use the current tab
	*/
	public boolean isCurrentConnectionActive()
	{
	
		boolean	retValue = false;
		GTabBarItem	tabInfo = getCurrentTab();
		
		if( tabInfo == null )
		{
			throw new NullPointerException("GBrowserWindow : getLocalBrowser() - tab info is null");
		}
		
		GConnectionSet cs = (GConnectionSet)tabInfo.getAttribute(CONNECTION_SET);

		return cs.isRemoteConnected();
		
	}
    
    public  JPopupMenu  popupMenu( GFileItem[] selection )
    {
        GUIAction   action  =   null;
        JMenuItem   mi      =   null;
        JPopupMenu  gbwPopupMenu = null;

        GMenuBar  mmBar=(GMenuBar)this.getJMenuBar();
        HashMap toolbarActions = mmBar.getToolbarActions(); 
        
        gbwPopupMenu = new JPopupMenu();

        action = (GUIAction)toolbarActions.get(GLocalized.TB_NEWFOLDER);
        gbwPopupMenu.add( mi = new JMenuItem( action ) );
        mi.setIcon(null);

        if( GideonConfiguration.EDIT_ENABLED )
        {
            action = (GUIAction)toolbarActions.get(GLocalized.NEWFILE);
            gbwPopupMenu.add( mi = new JMenuItem( action ) );
            mi.setIcon(null);
        }

        gbwPopupMenu.addSeparator();
        
        action = (GUIAction)toolbarActions.get(GLocalized.TB_DOWNLOAD);
        gbwPopupMenu.add( mi = new JMenuItem( action ) );
        mi.setIcon(null);
        
        action = (GUIAction)toolbarActions.get(GLocalized.TB_UPLOAD);
        gbwPopupMenu.add( mi = new JMenuItem( action ) );
        mi.setIcon(null);
        
        gbwPopupMenu.addSeparator();

        if( GideonConfiguration.PREVIEW_ENABLED )
        {
            action = (GUIAction)toolbarActions.get(GLocalized.TB_PREVIEW);
            gbwPopupMenu.add( mi = new JMenuItem( action ) );
            mi.setIcon(null);
        }

        if( GideonConfiguration.EDIT_ENABLED )
        {
            action = (GUIAction)toolbarActions.get(GLocalized.TB_EDIT);
            gbwPopupMenu.add( mi = new JMenuItem( action ) );
            mi.setIcon(null);
        }

        action = (GUIAction)toolbarActions.get(GLocalized.TB_GETINFO);
        gbwPopupMenu.add( mi = new JMenuItem( action ) );
        mi.setIcon(null);
        
        action = (GUIAction)toolbarActions.get(GLocalized.SETPERMISSIONS);
        gbwPopupMenu.add( mi = new JMenuItem( action ) );
        mi.setIcon(null);
              
        action = (GUIAction)toolbarActions.get(GLocalized.RENAME);
        gbwPopupMenu.add( mi = new JMenuItem( action ) );
        mi.setIcon(null);

        action = (GUIAction)toolbarActions.get(GLocalized.TB_DELETE);
        gbwPopupMenu.add( mi = new JMenuItem( action ) );
        mi.setIcon(null);
        
        gbwPopupMenu.addSeparator();
        
        action = (GUIAction)toolbarActions.get(GLocalized.COPYURL);
        gbwPopupMenu.add( mi = new JMenuItem( action ) );
        mi.setIcon(null);

        // Copy Temporary Link — S3 only (presigned, expiring download URL)
        if( getKeyBrowser() instanceof GBrowserPanelController s3kb
            && s3kb.getConnection() != null && s3kb.getConnection().getBookmark() != null
            && s3kb.getConnection().getBookmark().getProtocol() == GBookmark.G_S3_PROTOCOL )
        {
            gbwPopupMenu.add( mi = new JMenuItem(
                new GUIAction( GLocalized.COPY_TEMP_LINK, null, GUIAction.GA_COPY_TEMP_LINK ) ) );
            mi.setIcon(null);
        }

        action = (GUIAction)toolbarActions.get(GLocalized.ADDFAVORITE_LOCAL);
        gbwPopupMenu.add( mi = new JMenuItem( action ) );
        mi.setIcon(null);

        action = (GUIAction)toolbarActions.get(GLocalized.SET_HOME_FOLDER);
        gbwPopupMenu.add( mi = new JMenuItem( action ) );
        mi.setIcon(null);
        
        gbwPopupMenu.addSeparator();

        action = (GUIAction)toolbarActions.get(GLocalized.TB_REFRESH);
        gbwPopupMenu.add( mi = new JMenuItem( action ) );
        mi.setIcon(null);
		
        return gbwPopupMenu;
        
    }

    public GToolbar   getToolbar()
    {
        final boolean   isMacOSX    = GLocalized.onMacOSX;
        GUIAction       action      = null;
        GToolbarItem    b           = null;
        
        GMenuBar  mmBar=(GMenuBar)this.getJMenuBar();
            
        GToolbar toolBar = new GToolbar("Gideon TB");
                
        HashMap toolbarActions = mmBar.getToolbarActions();         
        
        if(toolbarActions==null)
        {
            throw new NullPointerException("Toolbar Actions were null");
        }
        
        toolBar.add(Box.createHorizontalGlue());

        // Search field — filters the key (focused) browser panel
        JTextField searchField = new JTextField();
        fSearchField = searchField;
        // The placeholder ("remote"/"local") is updated from focusGained() whenever the key pane changes.
        searchField.putClientProperty("JTextField.placeholderText", "Search");
        searchField.putClientProperty("JTextField.leadingIcon", new com.formdev.flatlaf.icons.FlatSearchIcon());
        searchField.putClientProperty("JTextField.showClearButton", true);
        searchField.putClientProperty("JComponent.roundRect", true);
        searchField.setPreferredSize(new Dimension(140, searchField.getPreferredSize().height));
        searchField.setMaximumSize(new Dimension(160, 28));
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filterKeyBrowser(searchField); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filterKeyBrowser(searchField); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterKeyBrowser(searchField); }
        });
        toolBar.add(searchField);
        toolBar.addSeparator(new Dimension(6, 0));

        // Activity panel toolbar button
        GActivityPanelController activityController = new GActivityPanelController(getJobManager(), this);
        GActivityToolbarItem activityItem = new GActivityToolbarItem(activityController);
        toolBar.add(activityItem);

        // Mirror toolbar button → opens the one-way mirror workspace (design §11)
        mirrorItem = new JButton(GLocalized.MIRROR_SVG_ICON);
        mirrorItem.setToolTipText("Mirror");
        mirrorItem.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        mirrorItem.putClientProperty("JButton.buttonType", "toolBarButton");  // flat, but highlights on hover/press
        mirrorItem.setFocusable(false);
        mirrorItem.setEnabled(false);                                          // only once a remote host is connected
        mirrorItem.addActionListener(ev -> toggleMirrorView());                // click again to leave the mirror view
        toolBar.add(mirrorItem);

        action = (GCancelUIAction)toolbarActions.get(GLocalized.TB_CANCEL);
        b = new GCancelToolbarItem(action);
        ((GCancelUIAction)action).setJobManager(getJobManager());
        action.setEnabled(false);
        b.setAction(action);
        // Vector icon on the button only (the Cancel menu item keeps its PNG via the action).
        // FlatSVGIcon auto-renders the disabled state, which this often-disabled button needs.
        b.setIcon(GLocalized.CANCEL_SVG_ICON);
        b.setText(null);
        toolBar.add(b);

        return toolBar;
        
    }
    
    /*
    *
    *
    * Connection Set Manager Interface Implementations
    *
    *
    */
    

    
    

    /*
    *
    *
    * GBrowserWindowCommandInterface
    *
    *
    */
    
    // called from REMOTE browser panels when the initial connection 
    // goes through.
    public void setTitleTo( String title )
    {
        // update our title to reflect the current connection
        //setTitle(title);
        
        // update our connection
        GConnectionSet  cSet = gbwConnectionManager.getActiveConnectionSet();
        cSet.setName(title);
        
        // tell the connection manager to fire the name change event
        
    }
	
    /**
    *
    * This window closing stuff is kinda complicated. Basically, the first time through we
    * start the host disconnect process then trap the job completed message. It's in the job completed
    * message that we close the window. If there is no remote connection we just drop it. We don't worry about
    * closing the local connection as it's a non-issue. The remote disconnect is done more or less so the 
    * networking resources and remote connection are torn down. it's also cleaner to the user.
    *
    */
    
    public boolean windowWillClose()
    {
        boolean	ret = true;
            
        // fire the browser closing event.
        fireWindowEvent( "browser-window closing", l -> l.bwl_browserClosing( this ) );
        
        // tell our connection manager to reap all the connections
        gbwConnectionManager.reapAll();
        
        // clear it out
        gbwConnectionManager = null;
        
        // save window size to prefs
        if( GideonConfiguration.REMEMBERWINDOSIZES )
        {
            Dimension size = getSize(); 
            Point location = getLocation();
            GSFTPPreferences.setWindowSizeDefaults(size, location);
        }

        return ret;
        
    }
	
    public  void    jobCompleted( final GJobManagerEvent e )
    {
    }
	
    /*
    *
    * END Magical window closing
    *
    */
    
    public  GBrowserPanelCommandInterface getRemoteBrowser()
    {
        
		GTabBarItem	tabInfo = getCurrentTab();
		
		if( tabInfo == null )
		{
			throw new NullPointerException("GBrowserWindow : getLocalBrowser() - tab info is null");
		}
		
		return (GBrowserPanelCommandInterface)tabInfo.getAttribute(REMOTE_BROWSER_KEY);
		
    }
    
    public  GBrowserPanelCommandInterface getLocalBrowser()
    {
		
		GTabBarItem	tabInfo = getCurrentTab();
		
		if( tabInfo == null )
		{
			throw new NullPointerException("GBrowserWindow : getLocalBrowser() - tab info is null");
		}
		
		return (GBrowserPanelCommandInterface)tabInfo.getAttribute(LOCAL_BROWSER_KEY);
			
    }
      
    private void filterKeyBrowser(JTextField searchField)
    {
        GBrowserPanelCommandInterface kb = getKeyBrowser();
        if( kb instanceof GBrowserPanelController )
        {
            ((GBrowserPanelController)kb).applyFilter(searchField.getText());
        }
    }

    public  GBrowserPanelCommandInterface getKeyBrowser()
    {
        
        GBrowserPanelCommandInterface   lBrowser = getLocalBrowser();
        
        if( lBrowser != null &&
            ((GGideonPanelController)lBrowser).hasFocus() ) 
        {
            return lBrowser;
        }
        
        GBrowserPanelCommandInterface   rBrowser = getRemoteBrowser();
        
        if( rBrowser != null &&
            ((GGideonPanelController)rBrowser).hasFocus() ) 
        {
            return rBrowser;
        }
        
        return null;

    }
 
    public  boolean getSideBarVisible()
    {
        return false;
    }
    
    public  void setSideBarVisible( boolean  isVisible )
    {
    }

    public  boolean getLocalFilesVisible()
    {
        return fLocalFilesVisible;
    }
    
    public  void setLocalFilesVisible( boolean  isVisible )
    {
    
        fLocalFilesVisible = isVisible;
    }

    /*
    *
    *
    * Focus Pocus
    *
    *
    */

    public  void focusGained( FocusEvent e )
    {
        
        validateToolbar();
        
        GBrowserPanelCommandInterface bp = (GBrowserPanelCommandInterface)getKeyBrowser();
        
        if( bp == null )
        {
            throw new NullPointerException("focusGained() key browser panel is null");
        }
        
        updateSearchScope( bp );
        fireWindowEvent( "key panel changed", l -> l.bwl_keyPanelChanged( this, bp ) );
        
    }
    
    public  void    focusLost( FocusEvent e )
    {
        
    }


/*
*
*
* Callbacks from browser panels
*
*
*
*/

public void userDoubleClickedFile(GFileItem f)
{

	if( f.getIsFileLocal() && GLocalized.onMacOSX )
	{
		try{
			Runtime.getRuntime().exec(new String[] { "open", f.getCompletePath() });
		}catch(IOException e)
		{
			
		}
	}
	
}

}