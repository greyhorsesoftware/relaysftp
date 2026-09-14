//
//  GideonUIAction.java
//  Gideon
//
//  Created by David on 10/12/04.
//  Copyright 2004 Grey Horse Software. All rights reserved.
//

package com.gideonsoftworks.gideon.actions;

import com.gideonsoftworks.gideon.widgets.GRenamePanel;
import com.gideonsoftworks.gideon.core.GFrame;
import com.gideonsoftworks.gideon.core.GFrameManager;
import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.core.GideonConfiguration;
import com.gideonsoftworks.gideon.core.GCommonEvents;
import com.gideonsoftworks.gideon.core.GValidationInterface;
import com.gideonsoftworks.gideon.browser.GBrowserPanelController;
import com.gideonsoftworks.gideon.browser.GBrowserWindowCommandInterface;
import com.gideonsoftworks.gideon.browser.GBrowserWindow;
import com.gideonsoftworks.gideon.browser.GBrowserPanelCommandInterface;
import com.gideonsoftworks.gideon.bookmarks.GBookmarkManagerWindow;
import com.gideonsoftworks.gideon.preferences.GSFTPPreferences;
import  com.gideonsoftworks.editor.*;
import  com.gideonsoftworks.gcore.*;
import  javax.swing.*;
import  java.awt.event.ActionEvent;
import  com.gideonsoftworks.gja.*;
import  java.lang.reflect.Array;
import	java.awt.*;
import	java.awt.datatransfer.*;
import	java.util.prefs.Preferences;
import	java.awt.event.WindowEvent;
import java.io.*;

/** The GUIAction class is the base class for all Gideon Action classes. It adds support for 
context information and easy menu/toolbar validation */

public class GUIAction extends AbstractAction implements GValidationInterface
{
    public  final   static  int GA_NONE            = -1;
    public  final   static  int GA_BOOKMARK_OPEN   = 0;
    public  final   static  int GA_BOOKMARK_ADD    = 1;
    public  final   static  int GA_BOOKMARK_MANAGE = 2;
    public  final   static  int GA_SHOWSIDEBAR     = 3;
    public  final   static  int GA_SET_PERMISSIONS = 4;
    public  final   static  int GA_DOWNLOAD        = 5;
    public  final   static  int GA_DELETE          = 6;
    public  final   static  int GA_CANCEL          = 7;
    public  final   static  int GA_DISCONNECT      = 8;
    public  final   static  int GA_REFRESH         = 9;
    public  final   static  int GA_NEW_FOLDER      = 11;
    public  final   static  int GA_NEW_BROWSER     = 13;
    public  final   static  int GA_NEW_CONNECTION  = 14;
    public  final   static  int GA_CLOSE_BROWSER   = 15;
    public  final   static  int GA_COPY_URL        = 16;
    public  final   static  int GA_TRANSFER_BIN    = 17;
    public  final   static  int GA_TRANSFER_ASCII  = 18;
    public  final   static  int GA_TRANSFER_AUTO   = 19;
    public  final   static  int GA_RENAME          = 20;
    public  final   static  int GA_CUT             = 21;
    public  final   static  int GA_COPY            = 22;
    public  final   static  int GA_PASTE           = 23;
    public  final   static  int GA_SELECTALL       = 24;
    public  final   static  int GA_RELAYHOME      = 26;
    public  final   static  int GA_ABOUTRELAY     = 27;
    public  final   static  int GA_OPENPREFERENCES = 28;
    public  final   static  int GA_EXIT            = 29;
    public  final   static  int GA_MINIMIZE        = 30;
    public  final   static  int GA_SELECTWINDOW    = 31;
    public  final   static  int GA_QUEUEMANGER     = 32;
    public  final   static  int GA_UPLOAD          = 33;
    public  final   static  int GA_REMOTEONLY      = 35;
    public  final   static  int GA_PREVIEW         = 36;
    public  final   static  int GA_EDIT            = 37;
    public  final   static  int GA_UNDO            = 38;
    public  final   static  int GA_REDO            = 39;
    public  final   static  int GA_SAVE            = 40;
    public  final   static  int GA_SAVEAS               = 41;
    public  final   static  int GA_SAVEACOPYAS          = 42;
    public  final   static  int GA_FIND                 = 43;
    public  final   static  int GA_SHOWTF               = 45;
    public  final   static  int GA_SUPPORTFORUM         = 47;
    public  final   static  int GA_FOLLOWME             = 48;
    public  final   static  int GA_MIRROR               = 49;
    public  final   static  int GA_COPY_TEMP_LINK       = 50;
    public  final   static  int GA_ADD_FAVORITE         = 51;
    public  final   static  int	GA_CLOSE_TAB            = 54;
    public  final   static  int GA_NEW_REMOTE_BROWSER   = 55;
    public  final   static  int GA_EXPORT_DIAGNOSTICS    = 56;
    public  final   static  int GA_NEW_FILE              = 58;
    public  final   static  int GA_SET_HOME_FOLDER       = 59;

    /**
    * Gideon Action Type Code
    */
    private int                                         guaCode;
    private static          GFrameManager               guaFrameManager			= null;
    private Object                                      guaRepresentedObject	= null;
    private static          GBookmarkManagerWindow      guaBookmarkManager		= null;
    private                 JMenu                       myMenu					= null;

    /**
        Set the shared GFrameManager
        @param  theManager  Shared GFrameManager
    */
    public  static void setSharedGFrameManager( GFrameManager theManager )
    {

        if( theManager == null )
        {
            throw new NullPointerException("Someone tried to assign a null GFrameManager");
        }
        
        guaFrameManager = theManager;
        
    }
    
    /**
        Return the shared GFrameManager
    */
    public  static  GFrameManager   getSharedGFrameManager()
    {
        return guaFrameManager;
    }
    
    /**
        Set the shared GBookmarkManagerWindow
        @param  theManager  Shared GBookmarkManagerWindow
    */
    public  static void setSharedGBookmarkManager( final GBookmarkManagerWindow theManager )
    {
    
        if( theManager == null )
        {
            throw new NullPointerException("Someone tried to assign a null GBookmarkManagerWindow");
        }
        
        guaBookmarkManager = theManager;
        
    }
    
    /**
        Return the shared GBookmarkManagerWindow
    */
    public  static  GBookmarkManagerWindow   getSharedGBookmarkManager()
    {
        return guaBookmarkManager;
    }
    
    /**
        Create a GUIAction
        @param  name        Action name
        @param  icon        Action icon
        @param  tooltip     Tooltip
    */
    public  GUIAction( final String name, final Icon icon, final String tooltip )
    {
        super(name, icon);
        setGideonActionCode(GUIAction.GA_NONE);
        putValue(Action.LONG_DESCRIPTION,tooltip);
    }
    
    /**
        Create a GUIAction
        @param  name    Action name
        @param  icon    Action icon
        @param  gaCode  GUIAction action code.
    */
    public  GUIAction( final String name, final Icon icon, final int gaCode )
    {
        super(name, icon);
        setGideonActionCode(gaCode);
    }
    
    /**
        Create a GUIAction
        @param  name    Action name
        @param  gaCode  GUIAction action code.
    */
    public  GUIAction( final String name, final int gaCode )
    {
        this(name, null, gaCode);
    }
    
    /**
        Assign some context info to the action.
        @param  rObject  Context object
    */
    public  void setRepresentedObject( final Object rObject )
    {
        guaRepresentedObject = rObject;
    }
    
    /**
        Retrieve some context info stored in the action.
    */
    public  Object getRepresentedObject()
    {
        return guaRepresentedObject;
    }
    
    /**
        Set the type of Gideon Action
        @param  code  One of the values defined in GUIAction
    */
    public  void setGideonActionCode( int code )
    {
        guaCode = code;
    }
    
    /**
        Methods for actions that need it - upload/download to favorites are two
    */
    public void setMenu(JMenu me)
    {
        myMenu = me;
    }

    public JMenu getMenu()
    {
        return myMenu;
    }

    /**
        Get the type of Gideon Action
    */
    public  int getGideonActionCode()
    {
        return guaCode;
    }

    public void  actionPerformed( ActionEvent e )
    {
        
        GFrameManager           theManager = GUIAction.getSharedGFrameManager();
        //GQueueManagerWindow     qmWindow = null;
        GFrame                  keyWindow = theManager.getKeyWindow();
        GBrowserPanelController brw = null;
        String                  result=null;
        GBrowserWindow          bw = null;
        GFileItem[]             selectedFiles=null;
        Preferences             prefs = Preferences.userNodeForPackage(GideonConfiguration.class);
        boolean                 isVisible;
        
        switch( this.getGideonActionCode() )
        {
		
            case GA_MIRROR:

                    if( keyWindow instanceof GBrowserWindow )
                    {
                            ((GBrowserWindow)keyWindow).enterMirrorView();
                    }

            break;

            case GA_TRANSFER_BIN:
                    prefs.putInt(GJobDescription.TRANSFERMODE_JOB_PROP, 2);
            break;
            
            case GA_TRANSFER_ASCII:
                    prefs.putInt(GJobDescription.TRANSFERMODE_JOB_PROP, 1);
            break;
            
            case GA_TRANSFER_AUTO:
                    prefs.putInt(GJobDescription.TRANSFERMODE_JOB_PROP, 0);
            break;
            
            case GUIAction.GA_ABOUTRELAY:
                GCommonEvents.doAbout();
            break;
            
            case GUIAction.GA_EXIT:
                GCommonEvents.doQuit();
            break;
            
            case GUIAction.GA_OPENPREFERENCES:
                GCommonEvents.doPreferences();         
            break;
            
            case GUIAction.GA_NEW_BROWSER:

                bw = new GBrowserWindow(theManager, null);

                bw.show();

            break;

            case GUIAction.GA_NEW_REMOTE_BROWSER:

                bw = new GBrowserWindow(theManager, null, true);

                bw.show();

            break;
            
            case GUIAction.GA_CLOSE_BROWSER:

                GFrame	kWindow = theManager.getKeyWindow();
                WindowEvent	eEv = new WindowEvent((Window)kWindow,WindowEvent.WINDOW_CLOSING);
                kWindow.dispatchEvent(eEv);
				
                //theManager.getKeyWindow().setVisible(false);
            break;

			case GA_FOLLOWME:
						
				isVisible = GideonConfiguration.FOLLOWMEENABLED;

                GideonConfiguration.FOLLOWMEENABLED = !isVisible;
                
				if( keyWindow instanceof GBrowserWindow )
				{
					// GEditorWindow is a subclass of GBrowserWindow
					if( GideonConfiguration.FOLLOWMEENABLED )
					{
						putValue( Action.NAME, GLocalized.DISABLEFOLLOWME );
					}else{
						putValue( Action.NAME, GLocalized.ENABLEFOLLOWME );
					}
				}
			
			break;

            case GA_NEW_FOLDER:
                
                // get the key browser before we show the option panel
                brw = (GBrowserPanelController)((GBrowserWindow)keyWindow).getKeyBrowser();

                result = JOptionPane.showInputDialog(null,  GLocalized.NAME_FOLDER_STRING, GLocalized.NEW_FOLDER_TITLE, JOptionPane.QUESTION_MESSAGE);
                
                if( ( result != null ) &&
                    ( result.length() != 0 ) )
                {

                    if( brw == null )
                    {
                        System.out.println("Browser is null");
                    }
                    
                    GAuditLog.action("mkdir", connLabel(brw), result);
                    brw.newFolder(result, null);
                }

            break;
            
            case GA_DELETE:
                
                // get the key browser before we show the option panel
                brw = (GBrowserPanelController)((GBrowserWindow)keyWindow).getKeyBrowser();
                
                selectedFiles = brw.getSelection();

                if( Array.getLength(selectedFiles) == 0 )
                {
                    JOptionPane.showMessageDialog( null,GLocalized.NO_SELECTION);
                    return;
                }
                
                String deleteMsg = selectedFiles.length == 1
                    ? "Are you sure you want to delete \"" + selectedFiles[0].getName() + "\"?"
                    : "Are you sure you want to delete " + selectedFiles.length + " items?";
                int rep=JOptionPane.showConfirmDialog(null, deleteMsg, GLocalized.SURE_YOU_WANT_TO_DELETE_TITLE,  JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE); 

                if( rep == JOptionPane.YES_OPTION ) 
                {
                    if( brw == null )
                    {
                        System.out.println("Browser is null");
                    }
                    
                    GAuditLog.action("delete", connLabel(brw), selLabel(selectedFiles));
                    brw.deleteItems(selectedFiles, null );
                }

            break;
            
            case GA_RENAME:
                
                // get the key browser before we show the option panel
                brw = (GBrowserPanelController)((GBrowserWindow)keyWindow).getKeyBrowser();
                
                if( brw==null )
                {
                    JOptionPane.showMessageDialog( null,"Browser is null");
                    return;
                }
                
                selectedFiles = brw.getSelection();
                
                if( Array.getLength(selectedFiles) == 0 )
                {
                    JOptionPane.showMessageDialog( null,GLocalized.NO_SELECTION);
                    return;
                }
                
                GFileItem selectedItem = (GFileItem)Array.get( selectedFiles, 0 );

                GAuditLog.action("rename", connLabel(brw), selectedItem.getName());
                GRenamePanel	rnp = new GRenamePanel(keyWindow);
                rnp.show(selectedItem, brw);

            break;

            case GA_REFRESH:
            
                brw = (GBrowserPanelController)((GBrowserWindow)keyWindow).getKeyBrowser();
                
                if( brw == null ) return;
                
                brw.refresh(null);

            break;
            
            case GA_NEW_CONNECTION:
            
                ((GBrowserWindowCommandInterface)keyWindow).newTab();
            
            break;
            
            case GA_DISCONNECT:

                bw = ((GBrowserWindow)keyWindow);
                GAuditLog.action("disconnect", connLabel(bw.getRemoteBrowser()), null);
                bw.disconnectBrowser((GBrowserPanelCommandInterface)bw.getRemoteBrowser());

            break;
            
            case GA_DOWNLOAD:
  
                // get the key browser before we show the option panel
                brw = (GBrowserPanelController)((GBrowserWindow)keyWindow).getKeyBrowser();

                selectedFiles = brw.getSelection();

                if( Array.getLength(selectedFiles) == 0 )
                {
                    JOptionPane.showMessageDialog( null,GLocalized.NO_SELECTION);
                    return;
                }
 
                GAuditLog.action("download", connLabel(brw), selLabel(selectedFiles));
                brw.download( selectedFiles, null );

            break;
            
            case GA_UPLOAD:
                
                // get the key browser before we show the option panel
                brw = (GBrowserPanelController)((GBrowserWindow)keyWindow).getKeyBrowser();
                
                selectedFiles = brw.getSelection();

                if( Array.getLength(selectedFiles) == 0 )
                {
                    JOptionPane.showMessageDialog( null,GLocalized.NO_SELECTION);
                    return;
                }
 
                GAuditLog.action("upload", connLabel(brw), selLabel(selectedFiles));
                brw.upload( selectedFiles, null );

            break;

            case GA_COPY_URL:
                
                // get the key browser before we show the option panel
                brw = (GBrowserPanelController)((GBrowserWindow)keyWindow).getKeyBrowser();
                
                if( brw == null )
                {
                    throw new NullPointerException("Browser was null");
                }
                
                selectedFiles = brw.getSelection();
                
                int tSize = Array.getLength(selectedFiles);
                
                StringBuilder    clipContents = new StringBuilder();
                
                for( int i = 0 ; i < tSize ; i++ )
                {
                    GFileItem   cItem = (GFileItem)Array.get(selectedFiles, i);
                    clipContents.append(cItem.getURLString() + "\n");
                }
                
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                StringSelection data = new StringSelection(clipContents.toString());
                clipboard.setContents (data, data);

            break;

            case GA_SET_HOME_FOLDER:

                brw = (GBrowserPanelController)((GBrowserWindow)keyWindow).getKeyBrowser();
                if( brw == null || brw.getConnection() == null || brw.getConnection().getBookmark() == null ) break;

                GFileItem homeTarget = homeFolderTarget( brw.getSelection(), brw.getVirtualCWD() );
                if( homeTarget == null ) break;

                GBookmark homeBm = brw.getConnection().getBookmark();
                if( homeBm.isLocal() )
                {
                    // local pane: the global default local folder (Preferences > General)
                    GSFTPPreferences.setDefaultLocalHomeFolder( homeTarget.getCompletePath() );
                    GAuditLog.action( "set-home", "local", homeTarget.getCompletePath() );
                }
                else
                {
                    // remote pane: the saved bookmark's remote path, persisted now (bookmarks are
                    // otherwise only written at quit)
                    homeBm.setHomePath( homeTarget.getCompletePath() );
                    GBookmarkManager.saveBookmarks();
                    GAuditLog.action( "set-home", homeBm.getHostname(), homeTarget.getCompletePath() );
                }

            break;

            case GA_COPY_TEMP_LINK:

                brw = (GBrowserPanelController)((GBrowserWindow)keyWindow).getKeyBrowser();
                if( brw == null ) break;

                selectedFiles = brw.getSelection();
                if( selectedFiles == null || Array.getLength(selectedFiles) == 0 ) break;

                // expiry chooser
                String[] expiryChoices = { "1 hour", "24 hours", "7 days" };
                String chosen = (String) JOptionPane.showInputDialog( keyWindow,
                    "Link expires in:", GLocalized.COPY_TEMP_LINK,
                    JOptionPane.QUESTION_MESSAGE, null, expiryChoices, expiryChoices[1] );
                if( chosen == null ) break;   // cancelled

                java.time.Duration expiry = switch( chosen ) {
                    case "1 hour" -> java.time.Duration.ofHours(1);
                    case "7 days" -> java.time.Duration.ofDays(7);
                    default       -> java.time.Duration.ofHours(24);
                };

                GConnection tlConn = brw.getConnection();
                StringBuilder links = new StringBuilder();
                try {
                    int n = Array.getLength(selectedFiles);
                    for( int i = 0 ; i < n ; i++ )
                    {
                        GFileItem it = (GFileItem) Array.get(selectedFiles, i);
                        if( it.getIsDir() ) continue;   // links are for files only
                        links.append( tlConn.presignGet( it.getCompletePath(), expiry ) ).append("\n");
                    }
                } catch( GConnectionException ex ) {
                    JOptionPane.showMessageDialog( keyWindow, ex.getMessage(),
                        GLocalized.COPY_TEMP_LINK, JOptionPane.ERROR_MESSAGE );
                    break;
                }

                if( links.length() > 0 )
                {
                    Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
                    StringSelection sel = new StringSelection( links.toString() );
                    cb.setContents( sel, sel );
                }

            break;

            default:
            
                System.out.println("\"" + this.getValue(Action.NAME) + "\" Selected.");
            
            break;

        }

    }

    /** Best-effort connection label (hostname/bucket) for audit lines; null-safe. */
    private static String connLabel( Object browser )
    {
        try
        {
            if( !(browser instanceof GBrowserPanelController) ) return null;
            GConnection c = ((GBrowserPanelController)browser).getConnection();
            if( c == null ) return null;
            GBookmark b = c.getBookmark();
            return b != null ? b.getHostname() : null;
        }
        catch( Exception e )
        {
            return null;
        }
    }

    /** Compact description of a selection for audit lines. */
    private static String selLabel( GFileItem[] sel )
    {
        if( sel == null || sel.length == 0 ) return null;
        return sel.length == 1 ? sel[0].getName() : sel.length + " items";
    }

    public boolean  shouldValidateItem()
    {
    
        int gideonActionCode = this.getGideonActionCode();
        
        if( gideonActionCode==GUIAction.GA_CANCEL ){
            
            return false;
                
        }
        
        return true;
        
    }
    
    /**
     * The folder "Set as Home Folder" acts on: the single selected folder, or the current folder
     * when nothing is selected; null when a file or several items are selected.
     */
    static GFileItem homeFolderTarget( GFileItem[] selection, GFileItem cwd )
    {
        if( selection == null || selection.length == 0 ) return cwd;
        if( selection.length == 1 && selection[0] != null && selection[0].getIsDir() && !selection[0].getIsCDUP() ) return selection[0];
        return null;
    }

    public boolean validateItem(GFrameManager theManager,
                                    GFrame  keyWindow,
                                        boolean keyWindowIsBrowser,
                                        GBrowserPanelCommandInterface keyBrowser, 
                                            GBrowserPanelCommandInterface localBrowser, 
                                                GBrowserPanelCommandInterface remoteBrowser,   
                                                    int gideonActionCode )
    {

        if( gideonActionCode == GA_ABOUTRELAY ||
			gideonActionCode == GA_SHOWTF )
        {
            return true;
        }
            
        keyWindow = theManager.getKeyWindow();

        if(keyWindow==null)
        {
        
            if( gideonActionCode==GA_NEW_BROWSER ||
                gideonActionCode==GA_NEW_REMOTE_BROWSER )
            {
                return true;
            }

            return false;
        }

        if(theManager==null)
        {
            System.out.println("Shared GFrameManager does not exist !");
            return false;
        }

        if( gideonActionCode==GA_CANCEL )
        {
            return isEnabled();
        }
        
        if( gideonActionCode==GA_SHOWSIDEBAR ){
            
            if( keyWindow instanceof GBrowserWindow )
            {
                    // GEditorWindow is a subclass of GBrowserWindow
                    if( ((GBrowserWindow)keyWindow).getSideBarVisible() )
                    {
                            putValue(Action.NAME, GLocalized.HIDESIDEBAR);
                    }else{
                            putValue(Action.NAME, GLocalized.SHOWSIDEBAR);
                    }
            }else{
                    putValue(Action.NAME, GLocalized.SHOWSIDEBAR);
            }
			
            return true;
                
        }

        if( ( ( gideonActionCode == GA_CLOSE_BROWSER ) &
            ( theManager.getKeyWindow() != null )) ||
            ( gideonActionCode == GA_QUEUEMANGER ) )
        { 
            return true;
        }

        // new browser are always enabled
        if( ( gideonActionCode == GA_NEW_BROWSER ) ||
            ( gideonActionCode == GA_NEW_REMOTE_BROWSER ) ||
            ( gideonActionCode == GA_EXIT) ||
            ( gideonActionCode == GA_OPENPREFERENCES ) ||
            ( gideonActionCode == GA_SHOWSIDEBAR ) )
        { 
            return true;
        }

        // if the key window is not a GBrowserWindow - don't bother validating it.
        // for instance - task manager and bookmark manager are GFrames
        if( !(keyWindow instanceof GBrowserWindow ) )
        {
            return false;
        }
        
		if( gideonActionCode == GA_FOLLOWME )
		{
			// return false if we have no local browser (e.g. a remote-only window)
			if( ((GBrowserWindowCommandInterface)keyWindow).getLocalBrowser() == null )
			{
				return false;
			}
		
			// Follow-Me syncs the panes over a live remote connection. A FILE (local) plugin reports
			// getIsConnected()==true, so require getIsRemote() too — otherwise it would enable on an
			// unconnected remote pane that's still showing local files.
			GConnection con = ((GBrowserWindowCommandInterface)keyWindow).getRemoteBrowser().getConnection();
			try { return con != null && con.getIsRemote() && con.getIsConnected(); }
			catch( Exception e ) { return false; }

		}
		
		
        if( gideonActionCode == GA_DISCONNECT ||
			gideonActionCode == GA_MIRROR )
        {
            // active only when the remote pane is connected to an actual host. A FILE (local) plugin
            // reports getIsConnected()==true, so require getIsRemote() too — otherwise Mirror would be
            // enabled on an unconnected remote pane that's still showing local files.
            GConnection con = ((GBrowserWindowCommandInterface)keyWindow).getRemoteBrowser().getConnection();
            try { return con != null && con.getIsRemote() && con.getIsConnected(); }
            catch( Exception e ) { return false; }
        }

        if( gideonActionCode == GA_REMOTEONLY )
        {
        
            return true;
 
        }
        
        if( (gideonActionCode == GA_TRANSFER_AUTO) ||
            (gideonActionCode == GA_TRANSFER_ASCII) ||
            (gideonActionCode == GA_TRANSFER_BIN) ||
            (gideonActionCode == GA_NEW_CONNECTION ) ){
            
            if( (keyWindow instanceof GEditorWindow) && 
                gideonActionCode==GA_NEW_CONNECTION )
            {
                return false;
            }
            
            return true;
                
        }
        
        keyBrowser = ((GBrowserWindow)keyWindow).getKeyBrowser();
        
        if(keyBrowser==null)
        {
            return false;
        }
        
        if( gideonActionCode == GA_DOWNLOAD){
            
            if( !keyBrowser.hasLocalSelection() )
            {
                return true;
            }
            
            
            return false;
                
        }
        
        if( gideonActionCode == GA_UPLOAD ){
            
            if( keyBrowser.hasLocalSelection() )
            {
                if( ((GBrowserWindow)keyWindow).getRemoteBrowser().getConnection() != null )
                {
                    return true;
                }
            }
            
            return false;
            
        }
        
        if( gideonActionCode == GA_SET_HOME_FOLDER )
        {
            // a single selected folder (or the current folder when nothing is selected), on a
            // connected pane whose bookmark is either local or a saved bookmark (an ad-hoc
            // Quick Connect bookmark has nowhere to persist a home path)
            if( !( keyBrowser instanceof GBrowserPanelController panel ) ) return false;
            if( panel.getConnection() == null || panel.getConnection().getBookmark() == null ) return false;
            if( homeFolderTarget( panel.getSelection(), panel.getVirtualCWD() ) == null ) return false;
            GBookmark bm = panel.getConnection().getBookmark();
            return bm.isLocal() || GBookmarkManager.contains( bm );
        }

        if( gideonActionCode == GA_PREVIEW ){
            
            
            if( keyBrowser.hasSelection() )
            {
            
                GFileItem[] items = keyBrowser.getSelection();
                
                // return false if the item listing returns false
                if( items == null ) return false;
                
                int count = Array.getLength(items);

                GFileItem   firstItem = (GFileItem)Array.get(items, 0);
                
                if( count == 1 && firstItem.getIsDir() )
                {
                    return false;
                }
            
                return true;
                
            }
            
            return false; 
        }

        if( keyBrowser.hasSelection() ||
            // allow for folder/refreshs in empty areas
            ( (gideonActionCode == GA_NEW_FOLDER) || (gideonActionCode == GA_NEW_FILE) || (gideonActionCode == GA_REFRESH) ) )
        {
            return true;
        }
        
        return false;

    }
    
    public boolean needsBrowser()
    {

        if( getGideonActionCode()==GA_NEW_BROWSER ||
            getGideonActionCode()==GA_NEW_REMOTE_BROWSER )
        {
            return false;
        }

        return true;
    }
    
    /**
    *
    *   
    *
    */

    
    /**
        This method is called before the menu is drawn. At this point the action can enable/disable associated menu items.
    */
    public boolean validateItem()
    {
      
        GFrameManager                   theManager          = GUIAction.getSharedGFrameManager();
        GBrowserPanelCommandInterface   keyBrowser          = null;
        GBrowserPanelCommandInterface   localBrowser        = null;
        GBrowserPanelCommandInterface   remoteBrowser       = null;
        boolean                         keyWindowIsBrowser  = false;
        GFrame                          keyWindow           = null;

        if( theManager != null )
        {
            keyWindow = theManager.getKeyWindow();
        }

        if( keyWindow != null )
        {
            if( keyWindow instanceof GBrowserWindow )
            {
                keyWindowIsBrowser = true;
                keyBrowser = ((GBrowserWindow)keyWindow).getKeyBrowser();
                localBrowser = ((GBrowserWindow)keyWindow).getLocalBrowser();
                remoteBrowser = ((GBrowserWindow)keyWindow).getRemoteBrowser();
            }
        }else{
            if( needsBrowser() )
            {
                return false;
            }
        }
        
        return validateItem(theManager, keyWindow, keyWindowIsBrowser, keyBrowser, localBrowser, remoteBrowser, getGideonActionCode() );
        
    }
    
}
