//
//  GWindow.java
//  Gideon
//
//  Created by David on 10/14/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.core;

import com.gideonsoftworks.gideon.toolbar.GToolbar;
import com.gideonsoftworks.gideon.actions.GUIAction;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import java.util.*;
import java.io.*;

// MRJ 

// GS Stuff
import com.gideonsoftworks.gja.*;
import com.gideonsoftworks.gcore.*;
import com.gideonsoftworks.editor.*;

import com.gideonsoftworks.tabkit.GTabBarItem;

/** The GFrame is used for all Gideon window creation. It overrides show() and hide() to integrate with the 
GFrameManager to provide a central window menu ( Mac OS X ) and in conjunction with GUIACtion and GFrameManagaer helps to provide 
streamlined menu/toolbar validation ( All Platforms ) */
public class GFrame extends JFrame implements WindowListener, ComponentListener, ChangeListener, GJobManagerListenerInterface
{
	static	private     final	String      TAB_PANEL_KEY       =	"TAB_PANEL_KEY";
    static	public      final	String      MINIMUM_WIDTH       =	"MINIMUM_WIDTH";
    static	public      final	String      MINIMUM_HEIGHT      =	"MINIMUM_HEIGHT";
        
    private GJobManager     gfJobManager;
    private GFrameManager   gfManager				= null;
    private boolean         gfNeedDotManagement		= true;
    protected boolean       gfBoundsRestored        = false;   // true once a saved position is applied; else center on show
	private	JTabbedPane     gfTabbedPane			= null;	// tabbed pane for this window if useTabs is on
	private	boolean			gUseTabs				= false;
	private	GTabBarItem		currentTabItem			= null;
	private	HashMap			viewToTab				= new HashMap();	// Component view -> GTabBarItem mapping
        private boolean showsMenu;
	
    /**
        Create a new GFrame
        @param m GFrameManager that is going to manage this window.
    */
    public  GFrame( GFrameManager m )
    {   
		
        super();
		
        if( m == null )
        {
            throw new NullPointerException("GFrame : GFrameManager is null");
        }
        
        addWindowListener(this);
        addComponentListener(this);
        
        // this window requires a job manager create one
        if( needsJobManager() )
        {
            // create job manager
            gfJobManager = new GJobManager("BrowserWindow " + hashCode());
            // add this frame as a listener
            gfJobManager.addStatusListener(this);
            // audit every background job (download/upload/delete/chmod/...) to the global log
            gfJobManager.addStatusListener(new GAuditJobListener());
        }
        
        gfManager = m;
        
        // create a event center 

        // all our windows should die when closed
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		
        // get rid of java cup on windows and unix
        if( !GLocalized.onMacOSX ) {
            setIconImages(java.util.List.of(
                GLocalized.APP_ICON.getImage(),
                GLocalized.RELAY_SMALL_ICON.getImage()
            ));
        }
		
        this.setUseTabs(false);
	
        this.setShowsMenu(false);
	
    }   
	
    public  boolean showsMenu()
    {
        if( GLocalized.onMacOSX )
        {
            return true;
        }
        // always show menu on os x - it ends up in the menu bar
        return showsMenu;
    }

    public  void    setShowsMenu( final boolean showMenu )
    {
            showsMenu = showMenu;
    }
    
	public	GMenuBar setupMenuBar()
	{	


        GMenuBar mBar = null;

        // create a menu bar for the window
        mBar = getFrameMenuBar();

        if( mBar == null )
        {
            throw new NullPointerException("Menu Bar was null.");
        }

        menuBarGenerated(mBar);

        if( this.showsMenu() )
        {
            setJMenuBar(mBar);
        }

		return mBar;
	
	}	
	
	public	GMenuBar	getFrameMenuBar()
	{
	
		GFrameManager	fm = GUIAction.getSharedGFrameManager();
		
		if( fm == null )
		{
			throw new NullPointerException("Could not get shared frame manager.");
		}	
		
		return GCommonPartsFactory.generateMenuBar(fm);
	
	}

	/**
	 *	private void setupWindow(GFrameManager m)
	 * 
	 *	This method creates the window menu bar, asks subclasser for its content view then adds
	 *  the content view and a toolbar to the window. toolbar is devined from the getToolbar() method.
	 *
	 *  @param nItem GFrameManager
	 *
	 */
	public void setupWindow(GFrameManager m)
	{
		Container       contentPane     = null;
        JMenuBar        mBar            = null;
        GConnectionSet  cs              = null;
		
		
        contentPane = getContentPane();
		
		System.out.println("contentpane [ " +  contentPane + " ]");
		
        contentPane.setLayout(new BorderLayout());
		
        setupMenuBar();

		if( !useTabs() )
		{
			/*
			*
			* Create master panel that includes browsers and status view
			*
			*/
			JComponent	browserRootPanel = getContentView(null);
			
			if( browserRootPanel == null ) 
			{
				// enable exception for production
				//throw new NullPointerException("getContentView() returned null content view");
				return;
			}
		
			contentPane.add(browserRootPanel, BorderLayout.CENTER);
		}

		JPanel  toolbarArea = new JPanel(new BorderLayout());

		if( hasToolbar() )
		{
			// setup toolbar area
			toolbarArea.setFocusable(false);
			JToolBar tb = getToolbar();
			toolbarArea.add( tb,  BorderLayout.PAGE_START );
			if( !useTabs() )
			{
				toolbarArea.add( new JSeparator(),  BorderLayout.PAGE_END );
				contentPane.add(toolbarArea, BorderLayout.PAGE_START);
			}
		}
		
		if( useTabs() )
		{
			// Create standard JTabbedPane — FlatLaf handles styling
			JTabbedPane ctp = new JTabbedPane(SwingConstants.TOP);
			ctp.putClientProperty("JTabbedPane.tabHeight", 40);
			setTabbedPane(ctp);
			ctp.getModel().addChangeListener((ChangeListener)this);

			// Put toolbar buttons as trailing component in the tab strip
			if( hasToolbar() )
			{
				JToolBar tb = getToolbar();
				tb.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 4));
				ctp.putClientProperty("JTabbedPane.trailingComponent", tb);
			}

			contentPane.add(ctp, BorderLayout.CENTER);

			// Create initial custom tab if subclass provides one
			GTabBarItem tbi = getCustomTab();
			if( tbi != null )
			{
				addTab(tbi);
			}

			newTab();

		}
		
        // name the window untitled connection for now - once the user connects it will be renamed
        setTitle(GLocalized.DEFAULTBROWSERNAME);
        
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        
	}

    public  GTabBarItem getCustomTab()
    {
            return null;
    }

    private void  addTab(GTabBarItem tbi)
    {
        JTabbedPane ctp = getTabbedPane();
		JComponent browserRootPanel = (JComponent)tbi.getTabview();

		if( browserRootPanel == null )
		{
			throw new NullPointerException("addTab: content view was null");
		}

		// remember view -> tab mapping
		viewToTab.put(browserRootPanel, tbi);

		// call delegate method
		tabWillBeAdded(tbi);

		// add tab to JTabbedPane
		ctp.addTab(tbi.getTitle(), browserRootPanel);
		int index = ctp.indexOfComponent(browserRootPanel);

		// set a custom tab component with close button
		ctp.setTabComponentAt(index, createTabComponent(tbi, ctp));

		// select the new tab
		ctp.setSelectedIndex(index);

    }

	/** Creates a tab header component with title label and close button */
	private JPanel createTabComponent(final GTabBarItem tbi, final JTabbedPane pane)
	{
		JPanel tabComp = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		tabComp.setOpaque(false);

		// Title label that stays in sync with GTabBarItem title
		JLabel titleLabel = new JLabel(tbi.getTitle() != null ? tbi.getTitle() : "");
		tabComp.add(titleLabel);

		// Store label reference so updateTabTitle can find it
		tbi.setAttribute("_tabLabel", titleLabel);

		// Close button
		JButton closeBtn = new JButton("\u00D7"); // multiplication sign as X
		closeBtn.setFont(closeBtn.getFont().deriveFont(Font.PLAIN, 14f));
		closeBtn.setMargin(new Insets(0, 2, 0, 2));
		closeBtn.setFocusable(false);
		closeBtn.setBorderPainted(false);
		closeBtn.setContentAreaFilled(false);
		closeBtn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				// select this tab first, then close it
				int idx = pane.indexOfTabComponent(tabComp);
				if( idx != -1 )
				{
					pane.setSelectedIndex(idx);
					closeKeyTab();
				}
			}
		});
		tabComp.add(closeBtn);

		return tabComp;
	}

	/**
        Basic jist is this. getTabContent() is called. The subclasser can then return a 
		hash of data items. The only required one is TAB_PANEL_KEY. Which is used to create a 
		panel. The subclasser can also place other items into the hash as this hash is an argument
		to all tab methods.
    */
	public void newTab()
	{
		
		// create a GTabItem with the 
		GTabBarItem	cTabItem = new GTabBarItem();

        // ask window for content view...
        JComponent  view = getContentView( cTabItem );

        // assign content view to new tab...
        cTabItem.setTabview(view);
        cTabItem.setTitle(GLocalized.UNTITLED);

		// let subclass play with the new tab
		newTabCreated( cTabItem );

        addTab(cTabItem);

	}
	
    /**
        Called when new tab clicked. And we let the sublcass know which tab was clicked.
    */
    public void stateChanged(ChangeEvent e)
    {
        SingleSelectionModel  ssm = (SingleSelectionModel)e.getSource();
        if( ssm == null ) return;

        int tabIndex = ssm.getSelectedIndex();
        if( tabIndex == -1 ) return;

        JTabbedPane tp = getTabbedPane();
        if( tp == null ) return;

        Component   view = tp.getComponentAt(tabIndex);
		GTabBarItem tabItem = (GTabBarItem)viewToTab.get(view);

		if( tabItem == null ) return;

		setCurrentTab(tabItem);
		tabSelected(tabItem);

    }
	
	/**
        Called to let subclass do any final cleanup before a tab goes bye bye
        @param tabInfo HashMap returned from getTabContent()
    */
	public void tabWillClose( final GTabBarItem tabInfo )
	{
		// override this to handle window specific shit
	}
	
	/**
        Called to let subclass do any wireup, startup needed for ui.
        @param tabInfo HashMap returned from getTabContent()
    */
	public void tabWillBeAdded( final GTabBarItem tabInfo )
	{
		// override this to handle window specific shit
	}

	public void closeKeyTab()
	{
		JTabbedPane pane = getTabbedPane();

		GTabBarItem currentTab = getCurrentTab();
		if( currentTab == null ) return;

		// let subclass clean up
		tabWillClose(currentTab);

		// remove from tracking
		if( currentTab.getTabview() != null )
		{
			viewToTab.remove(currentTab.getTabview());
		}

		// remove from JTabbedPane
		Component selectedTab = pane.getSelectedComponent();
		if( selectedTab != null )
		{
			pane.remove(selectedTab);
		}

		// select whatever tab JTabbedPane chose next
		Component view = pane.getSelectedComponent();
		if( view == null )
		{
			setCurrentTab(null);
			return;
		}

		GTabBarItem map = (GTabBarItem)viewToTab.get(view);
		if( map != null )
		{
			setCurrentTab(map);
			tabSelected(map);
		}

	}
	
	/**
        Called when a tab is selected
		@param tab Tab Bar Item
	*/
	public void tabSelected( final GTabBarItem tab )
	{
		// override this to handle window specific shit
	}
	
	/**
        Called to get the current tab
        @return GTabItem current GTabItem
    */
	public GTabBarItem getCurrentTab()
	{
		return currentTabItem;
	}
	
	/**
        Called to get every tab in the window, in tab order
        @return list of tab items (empty if the window has no tabs)
    */
	public java.util.List<GTabBarItem> getTabs()
	{
		java.util.List<GTabBarItem> tabs = new ArrayList<GTabBarItem>();
		JTabbedPane pane = getTabbedPane();
		if( pane == null ) return tabs;

		for( int i = 0 ; i < pane.getTabCount() ; i++ )
		{
			GTabBarItem tab = (GTabBarItem)viewToTab.get(pane.getComponentAt(i));
			if( tab != null ) tabs.add(tab);
		}
		return tabs;
	}

	/**
        Called to set the current tab info
		@param tabItem tab item
    */
	public void setCurrentTab( final GTabBarItem tabItem )
	{
		currentTabItem = tabItem;
	}
		
	/**
        Called to allow subclass to set a tabs view. Subclassers can also assign attributes to the
		tab for use in other callbacks.
			containing the following keys LOCAL_BROWSER_KEY, REMOTE_BROWSER_KEY, MAIN_PANEL_KEY
        @return boolean yes/no depending on if all went ok
    */
	public boolean	newTabCreated( final GTabBarItem item )
	{
		return false;
	}
	
	public  JTabbedPane getTabbedPane()
    {
        return gfTabbedPane;
    }
    
    public void setTabbedPane( JTabbedPane tPane )
    {
        if( tPane == null )
        {
            throw new NullPointerException("GFrame : setTabbedPane - pane was null");
        }
        gfTabbedPane = tPane;
    }
	
	/** Update the JTabbedPane header to reflect a GTabBarItem's current title */
	public void updateTabTitle( final GTabBarItem tab )
	{
		if( tab == null || tab.getTabview() == null ) return;
		JTabbedPane tp = getTabbedPane();
		if( tp == null ) return;

		int index = tp.indexOfComponent(tab.getTabview());
		if( index != -1 )
		{
			tp.setTitleAt(index, tab.getTitle());
			// also update the label in the custom tab component
			JLabel lbl = (JLabel)tab.getAttribute("_tabLabel");
			if( lbl != null ) lbl.setText(tab.getTitle());
		}
	}

    /**
    * Return this windows job manager
    */
    public GJobManager  getJobManager()
    {
        return gfJobManager;
    }
    
    /**
    * By default we return no.
    */
    public boolean  needsJobManager()
    {
        return false;
    }
    
    public GFrameManager    getFrameManager()
    {
    
        return gfManager;
    
    }
	
    public void windowWillShow()
    {
            System.out.println("Window will show");
            setupWindow(getFrameManager());
    }
    
    /**
        This method is overiden so we can hook into the GFrameManager and let it know about
        newly created windows.
    */
    public void show()
    {
		
        GMenuBar    mBar;

        windowWillShow();

        gfManager.addToWindowMenu(this);


        HashMap settings = this.getFrameSettings();
         
        // if we didn't get any frame settings just return...
        if( settings != null )
        {          
            Integer    minWidth = (Integer)settings.get(GFrame.MINIMUM_WIDTH);
            Integer    minHeight = (Integer)settings.get(GFrame.MINIMUM_HEIGHT);
            this.setSize(minWidth.intValue(),minHeight.intValue());
        }

        // First / un-restored windows open centered on screen rather than at the top-left default.
        if( !gfBoundsRestored )
            setLocationRelativeTo( null );

        super.show();

        validateToolbar();

		
    }
    
    /**
    *
    * Called right after the menu bar for this window is created.
    *
    */
    public void menuBarGenerated( GMenuBar m )
    {

    }
    
    /**
    *
    * Override this to implement the "Are you sure ?" dialog.
    *
    */
    
    protected void processWindowEvent(WindowEvent e)
    {
		int	eventCode = e.getID();

		
        if(eventCode==WindowEvent.WINDOW_CLOSING )
        {

            // is this window has a job manager
            // make sure no jobs are running and warn the user.
            // ask before windowWillClose() - subclasses tear down there (e.g. the browser reaps its connections)
            if( getJobManager() != null && getJobManager().hasRunningJobs() )
            {
                int rep=JOptionPane.showConfirmDialog(null, GLocalized.SURE_YOU_WANT_TO_CLOSE_MSG, GLocalized.SURE_YOU_WANT_TO_CLOSE_TITLE,  JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

                if( rep != JOptionPane.YES_OPTION )
                {
                    return;
                }
            }

            if(!windowWillClose())
            {
				return;
            }

            if( getJobManager() != null )
            {
                // tell the job manager to shut down stopping any jobs in progress.
                getJobManager().shutdown( GAppShutdown.JOIN_BUDGET_MS );
            }
    
            // if the user clicks the close box under windows/unix - we need to exit the vm
            if( !GLocalized.onMacOSX && this instanceof com.gideonsoftworks.gideon.browser.GBrowserWindow )
            {

                if( getFrameManager().isLastBrowserWindow(this) )
                {
                    gfManager.removeFromWindowMenu(this);
                    gfJobManager = null;
                    GCommonEvents.doQuit();
                }

            }
            
			// remove window
            gfManager.removeFromWindowMenu(this);    
            
			// null out manger
			gfJobManager = null;
            //gfManager=null;
            
       }
        
       super.processWindowEvent(e);
    
    }
    
    
    /*
    *
    * Returns the job manager for this browser.
    *
    */
    public void setDefaultButton( JButton defaultButton )
    {
        
        JButton db = getRootPane().getDefaultButton();

        if( db!=null && db.equals(defaultButton) ) return;
        
        getRootPane().setDefaultButton(defaultButton);
    
    }
    
    public  void    validateToolbar()
    {
        
        GMenuBar    mBar;
        
        if( this.hasToolbar() )
        {
            mBar = (GMenuBar)this.getJMenuBar();
            if(mBar==null)
            {
                //throw new NullPointerException("GFrame :: show() window menu bar was null");
                return;
            }
            this.validateToolbarItems(mBar.getToolbarActions());
        }

    
    }
	
	/*
	*
	* End Find API
	*
	*/
    
	public  boolean useTabs()
    {
        return gUseTabs;
    }
	
	public void setUseTabs( final boolean useTabs )
	{
		gUseTabs = useTabs;
	}
	
    public  boolean hasToolbar()
    {
        return true;
    }
	
	// return the toolbar
	public GToolbar   getToolbar()
    {
		return null;
	}

    public JComponent getContentView(final GTabBarItem cTabItem) {
            return null;
    }
	 
    public  void    validateToolbarItems(HashMap tbActions)
    {
        Collection      actions     = null;
        Iterator        iIT         = null;
        
        actions = tbActions.values();
        
        if(actions==null)
        {
             throw new NullPointerException("GFrame validateToolbarItems :: error retrieving window toolbar actions");
        }
 
        iIT = actions.iterator();
        
        if(iIT==null)
        {
             throw new NullPointerException("GFrame validateToolbarItems :: error creating iterator for toolbar actions");
        }
        
        while( iIT.hasNext() )
        {

            GUIAction    theAction = (GUIAction)iIT.next();
            
            if(GideonConfiguration.DEBUG_VALIDATION_CODE) System.out.print("Validating Toolbar Item : " + theAction.getValue(Action.NAME) );
            
            // we have a valid action - handle calling validateMenu() 
            // and enabling/disabling the menu item
            if( theAction != null ) 
            {
            
                if( !theAction.shouldValidateItem()) continue;
            
                if( theAction.validateItem() )
                {
                    if(GideonConfiguration.DEBUG_VALIDATION_CODE) System.out.println("...enabled.");
                    theAction.setEnabled(true);
                }else{
                    if(GideonConfiguration.DEBUG_VALIDATION_CODE) System.out.println("...disabled.");
                    theAction.setEnabled(false);
                }
            }
    
        }

    }
    
    public boolean windowWillClose()
    {
        return true;
    }
    
    public void windowActivated( WindowEvent e )
    {
    
    
    }
    
    public void windowClosed( WindowEvent e )
    {

        
    }

    public void windowClosing( WindowEvent e )
    {
    
            
    }

    public void windowDeactivated( WindowEvent e )
    {
    
    
    }
    
    public void windowDeiconified( WindowEvent e )
    {
    
    
    }
    
    public void windowIconified( WindowEvent e )
    {
    
    
    }
 
    public void windowOpened( WindowEvent e )
    {
    }
    
public  void    jm_jobStarted( final GJobManagerEvent e )
{
    if( gfNeedDotManagement ) getRootPane().putClientProperty("windowModified", Boolean.TRUE);
    jobStarted(e);
}
public  void    jm_jobStopped( final GJobManagerEvent e )
{
    if( gfNeedDotManagement ) getRootPane().putClientProperty("windowModified", Boolean.FALSE);
    jobStopped(e);
}
public  void    jm_jobFailed( final GJobManagerEvent e )
{
    if( gfNeedDotManagement ) getRootPane().putClientProperty("windowModified", Boolean.FALSE);
    jobFailed(e);
}

public  void    jm_jobCompleted( final GJobManagerEvent e )
{
    if( gfNeedDotManagement ) getRootPane().putClientProperty("windowModified", Boolean.FALSE);
    jobCompleted(e);
}

public  void    jm_keyJobChanged( final GJobManagerEvent e )
{
    keyJobChanged(e);
}

public  void    jm_jobCancelling( final GJobManagerEvent e )
{
    jobCancelling(e);
}

public  void    jm_jobNeedsInput( final GJobManagerEvent e, final GJobUserMessage m )
{
    jobNeedsInput(e);
}

public  void    jm_jobHasMessage( final GJobManagerEvent e )
{
    jobHasMessage(e);
}

public  void    jm_shuttingDown( final GJobManagerEvent e )
{
    shuttingDown(e);
}

/*
*
* Job Manager Methods
*
*
*/
public  void    jobStarted( final GJobManagerEvent e )
{

}
public  void    jobStopped( final GJobManagerEvent e )
{
    
}
public  void    jobFailed( final GJobManagerEvent e )
{

}

public  void    jobCompleted( final GJobManagerEvent e )
{

}

public  void    keyJobChanged( final GJobManagerEvent e )
{

}

public  void    jobCancelling( final GJobManagerEvent e )
{

}

public  void    jobNeedsInput( final GJobManagerEvent e )
{

}

public  void    jobHasMessage( final GJobManagerEvent e )
{

}

public  void    shuttingDown( final GJobManagerEvent e )
{

}
          
public  File  getSavePath( String title, String defaultFileName )
{
    File    nFile     = null;
    int     returnVal   = 0;

    if( GLocalized.onMacOSX )
    {

        FileDialog fd = new FileDialog((JFrame)this, title);
        fd.setMode(FileDialog.SAVE);
        fd.setDirectory(System.getProperty("user.home"));
        fd.setFile(defaultFileName);
        fd.setVisible(true);
        
        String  fName = fd.getFile();
        String  fPath = fd.getDirectory();
        
        // no file
        if( fName == null )
        {
            return null;
        }
        
        nFile = new File( fPath + System.getProperty("file.separator") + fName );
        
    }else{
    
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        //chooser.setFile(null);
        returnVal = chooser.showSaveDialog((JFrame)this);
        
        if(returnVal == JFileChooser.APPROVE_OPTION) 
        {
            nFile = chooser.getSelectedFile();
        }
    
    }
    
    return nFile;
    
}

        
/*
 * Min window size enforcement...
 * 
 * 
 * 
 */        


public HashMap getFrameSettings() 
{
    return null;
}

public void componentResized(ComponentEvent e) 
{
         int width = getWidth();
         int height = getHeight();
         
         System.out.println("component resized...");
         
         HashMap settings = getFrameSettings();
         
         System.out.println("settings " + settings);
         
         // if we didn't get any frame settings just return...
         if( settings == null )
         {
             return;
         }
         
         Integer    minWidth = (Integer)settings.get(GFrame.MINIMUM_WIDTH);
         Integer    minHeight = (Integer)settings.get(GFrame.MINIMUM_HEIGHT);
         
         System.out.println("min height : " + minHeight + " min width " + minWidth);
         
         //we check if either the width
         //or the height are below minimum
         boolean resize = false;
           if (width < minWidth.intValue()) 
           {
                resize = true;
                width = minWidth.intValue();
           }
           if (height < minHeight.intValue()) {
                resize = true;
                height = minHeight.intValue();
           }
         if (resize) {
               setSize(width, height);
         }
        }
        public void componentMoved(ComponentEvent e) 
        {
        }
        
        public void componentShown(ComponentEvent e) 
        {
        }
        
        public void componentHidden(ComponentEvent e) 
        {
        }

}
