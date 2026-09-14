//
//  GMenuFactory.java
//  Gideon
//
//  Created by David on 10/13/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.core;

import com.gideonsoftworks.gideon.actions.GPreviewUIAction;
import com.gideonsoftworks.gideon.actions.GHelpAction;
import com.gideonsoftworks.gideon.actions.GAddFavoriteUIAction;
import com.gideonsoftworks.gideon.actions.GSetPermissionsUIAction;
import com.gideonsoftworks.gideon.actions.GUIAction;
import com.gideonsoftworks.gideon.bookmarks.GBookmarkMenu;
import com.gideonsoftworks.gideon.find.GFindUIAction;
import com.gideonsoftworks.gideon.getinfo.GGetInfoUIAction;
import com.gideonsoftworks.gideon.actions.GCloseTabAction;
import com.gideonsoftworks.gideon.actions.GCancelUIAction;
import com.gideonsoftworks.gideon.actions.GWindowUIAction;
import com.gideonsoftworks.gideon.actions.GCloseTabDisconnectUIAction;
import com.gideonsoftworks.gideon.logviewer.GLogWindow;
import com.gideonsoftworks.gideon.actions.GShowTransferFiltersAction;
import com.gideonsoftworks.editor.*;
import com.gideonsoftworks.gcore.*;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import java.util.*;

public class GCommonPartsFactory {
    
    /**
        Generates a menu bar containing all menus.
        @param ml Menu listener.
    */
    public static GMenuBar    generateMenuBar( MenuListener ml )
    {
        HashMap toolbarActions = new HashMap();
        
        if( ml == null )
        {
            throw new NullPointerException("Passed null menu listener to GMenuFactory.generateMenuBar()");
        }
        
        GMenuBar menuBar = new GMenuBar();
        int         shortcutKeyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        JMenu       m = null;
        menuBar.add(m = fileMenu(shortcutKeyMask, toolbarActions));
        m.addMenuListener(ml);
        menuBar.add(m = editMenu(shortcutKeyMask, toolbarActions));
        m.addMenuListener(ml);
        
        menuBar.add(m = bookmarkMenu(shortcutKeyMask));
        m.addMenuListener(ml);
        menuBar.add(m = specialMenu(shortcutKeyMask, toolbarActions));
        m.addMenuListener(ml);
        menuBar.add(m = windowMenu(shortcutKeyMask));
        m.addMenuListener(ml);
        menuBar.add(m = helpMenu(shortcutKeyMask));
        m.addMenuListener(ml);
        
        menuBar.setToolbarActionHashmap(toolbarActions);
        
        return menuBar;
    
    }
        
    public static  JMenu   windowMenu( int shortcutKeyMask )
    {
        JMenuItem       mi=null;
        Action		action=null;
        JMenu		fMenu=null;
		
        JMenu       m = new JMenu(GLocalized.WINDOW_MENU);
        m.add( mi = new JMenuItem( new GWindowUIAction( GLocalized.MINIMIZE, null, GUIAction.GA_MINIMIZE ) ) );
        mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_M, shortcutKeyMask ) );
        m.addSeparator();
        m.add( mi = new JMenuItem( action=new GShowTransferFiltersAction( GLocalized.TRANSFERFILTERS) ) );
        mi = new JMenuItem("Connections");
        mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_L, shortcutKeyMask | KeyEvent.SHIFT_DOWN_MASK ) );
        mi.addActionListener(e -> GLogWindow.toggle());
        m.add(mi);
        m.addSeparator();
        m.add( mi = new JMenuItem( GLocalized.NOWINDOWSOPEN ) );
        mi.setEnabled(false);
		
		
        return m;
        
    }
    
    public static JMenu   fileMenu(int shortcutKeyMask, HashMap toolbarActions )
    {
        GUIAction   action;
        JMenuItem   mi;
        // create the file menu
        JMenu fMenu = new JMenu(GLocalized.FILE_MENU);
        
        JMenu newBrowserMenu = new JMenu( GLocalized.NEWBROWSERMENU );

        newBrowserMenu.add( mi = new JMenuItem( new GUIAction( GLocalized.NEWBROWSER, null, GUIAction.GA_NEW_BROWSER ) ) );
        mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_N, shortcutKeyMask ) );

        newBrowserMenu.add( mi = new JMenuItem( new GUIAction( GLocalized.NEWREMOTEONLY, null, GUIAction.GA_NEW_REMOTE_BROWSER ) ) );
        mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_N, shortcutKeyMask|InputEvent.ALT_MASK ) );

        fMenu.add( newBrowserMenu );

        fMenu.add( mi = new JMenuItem( new GUIAction( GLocalized.NEWCONNECTION, null, GUIAction.GA_NEW_CONNECTION ) ) );
        mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_T, shortcutKeyMask ) );
        
        fMenu.add( mi = new JMenuItem( action = new GUIAction( GLocalized.NEWFOLDER, null, GUIAction.GA_NEW_FOLDER ) ) );
        mi.setIcon(null);
        mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_N, shortcutKeyMask|InputEvent.SHIFT_MASK ) );
        toolbarActions.put( GLocalized.TB_NEWFOLDER, action );

        if( GideonConfiguration.EDIT_ENABLED )
        {
            fMenu.add( mi = new JMenuItem( action = new GNewFileUIAction( GLocalized.NEWFILE, null ) ) );
            mi.setIcon(null);
            toolbarActions.put( GLocalized.NEWFILE, action );
        }
        
        fMenu.addSeparator();
        
        fMenu.add( mi = new JMenuItem( new GUIAction( GLocalized.CLOSE, null, GUIAction.GA_CLOSE_BROWSER ) ) );
        mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_W, shortcutKeyMask ) );
    
        fMenu.add( mi = new JMenuItem( new GCloseTabAction( GLocalized.CLOSE_TAB, null ) ) );
        mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_W, shortcutKeyMask|InputEvent.SHIFT_MASK ) );
    
        fMenu.add( mi = new JMenuItem( action=new GEditorUIAction( GLocalized.SAVEMENU, GLocalized.SAVE_ICON, GUIAction.GA_SAVE ) ) );
        mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcutKeyMask ) );
        mi.setIcon(null);
        toolbarActions.put( GLocalized.TB_SAVE, action );
        
        fMenu.add( mi = new JMenuItem( action=new GEditorUIAction( GLocalized.SAVEAS, GLocalized.SAVE_ICON, GUIAction.GA_SAVEAS ) ) );
        mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcutKeyMask|InputEvent.SHIFT_MASK ) );
        mi.setIcon(null);
        
        fMenu.add( mi = new JMenuItem( action=new GEditorUIAction( GLocalized.SAVEACOPYAS, GLocalized.SAVE_ICON, GUIAction.GA_SAVEACOPYAS ) ) );
        mi.setIcon(null);
        
        fMenu.addSeparator();
        
        if( GideonConfiguration.PREVIEW_ENABLED )
        {
            fMenu.add( mi = new JMenuItem( action=new GPreviewUIAction( GLocalized.PREVIEW, GLocalized.PREVIEW_ICON ) ) );
            mi.setIcon(null);
            toolbarActions.put( GLocalized.TB_PREVIEW, action );
            //mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_W, shortcutKeyMask ) );
        }
        
        if( GideonConfiguration.EDIT_ENABLED )
        {
            fMenu.add( mi = new JMenuItem(  action = new GEditUIAction( GLocalized.EDIT, GLocalized.EDIT_ICON ) ) );
            mi.setIcon(null);
            toolbarActions.put( GLocalized.TB_EDIT, action );
        }

        fMenu.add( mi = new JMenuItem( action=new GGetInfoUIAction( GLocalized.GETINFO, GLocalized.GETINFO_ICON ) ) );
        mi.setIcon(null);
        mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_I, shortcutKeyMask ) );
        toolbarActions.put( GLocalized.TB_GETINFO, action );
        
        fMenu.add( new JMenuItem( action=new GSetPermissionsUIAction( GLocalized.SETPERMISSIONS, null ) ) );
        toolbarActions.put(GLocalized.SETPERMISSIONS, action);
        
        fMenu.add( mi = new JMenuItem( action=new GUIAction( GLocalized.RENAME, null, GUIAction.GA_RENAME ) ) );
        mi.setIcon(null);
        toolbarActions.put(GLocalized.RENAME, action);
        
        fMenu.add( mi = new JMenuItem( action=new GUIAction( GLocalized.DELETE, GLocalized.DELETE_ICON, GUIAction.GA_DELETE ) ) );
        mi.setIcon(null);
        mi.setAccelerator( KeyStroke.getKeyStroke( KeyEvent.VK_BACK_SPACE, shortcutKeyMask ) );
        toolbarActions.put(GLocalized.TB_DELETE, action);
        
        fMenu.addSeparator();
        
        fMenu.add( mi = new JMenuItem( action=new GUIAction( GLocalized.COPYURL, null, GUIAction.GA_COPY_URL ) ) );
        toolbarActions.put(GLocalized.COPYURL, action);
        mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_C, shortcutKeyMask|InputEvent.SHIFT_MASK ) );
        
        fMenu.add( mi = new JMenuItem( action=new GAddFavoriteUIAction( GLocalized.ADDFAVORITE_LOCAL, null ) ) );
        toolbarActions.put(GLocalized.ADDFAVORITE_LOCAL, action);

        fMenu.add( mi = new JMenuItem( action=new GUIAction( GLocalized.SET_HOME_FOLDER, null, GUIAction.GA_SET_HOME_FOLDER ) ) );
        toolbarActions.put(GLocalized.SET_HOME_FOLDER, action);

        fMenu.addSeparator();
        //mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_W, shortcutKeyMask ) );
        
        fMenu.add( mi = new JMenuItem( action = new GUIAction( GLocalized.DOWNLOAD, GLocalized.DOWNLOAD_ICON, GUIAction.GA_DOWNLOAD ) ) );
        mi.setIcon(null);
        toolbarActions.put( GLocalized.TB_DOWNLOAD, action );
        mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, shortcutKeyMask ) );
        
        fMenu.add( mi = new JMenuItem( action = new GUIAction( GLocalized.UPLOAD, GLocalized.UPLOAD_ICON, GUIAction.GA_UPLOAD ) ) );
        mi.setIcon(null);
        toolbarActions.put( GLocalized.TB_UPLOAD, action );
        mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_UP, shortcutKeyMask ) );
       
        if( GideonConfiguration.ENABLEDMIRRORING )
        {
                fMenu.add( mi = new JMenuItem( action=new GUIAction( GLocalized.MIRROR, null, GUIAction.GA_MIRROR ) ) );
        }
		
        JMenu transferModeMenu = new JMenu(GLocalized.TRANSFERMODE);
        
        ButtonGroup bg = new ButtonGroup();
        transferModeMenu.add( mi = new JCheckBoxMenuItem( new GUIAction( GLocalized.AUTO_TRANSFERMODE, null, GUIAction.GA_TRANSFER_AUTO ) ) );
        mi.setSelected(true);
        bg.add(mi);
        transferModeMenu.add( mi = new JCheckBoxMenuItem( new GUIAction( GLocalized.ASCII_TRANSFERMODE, null, GUIAction.GA_TRANSFER_ASCII ) ) );
        bg.add(mi);
        transferModeMenu.add( mi = new JCheckBoxMenuItem( new GUIAction( GLocalized.BINARY_TRANSFERMODE, null, GUIAction.GA_TRANSFER_BIN ) ) );
        bg.add(mi);
        fMenu.add( transferModeMenu );
        
        if( !GLocalized.onMacOSX )
        {
            fMenu.addSeparator();
            fMenu.add( new JMenuItem( new GUIAction( GLocalized.EXIT, null, GUIAction.GA_EXIT ) ) );
        }

        return fMenu;
              
    }
    
    public static JMenu   editMenu(int shortcutKeyMask, HashMap toolbarActions)
    {
        JMenuItem           mi;
        GUIAction			action;

        // create the file menu
        JMenu fMenu = new JMenu(GLocalized.EDIT_MENU);
        
        fMenu.add( mi = new JMenuItem( action=new GEditorUIAction( GLocalized.UNDO, GUIAction.GA_UNDO ) ) );
        mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcutKeyMask ) );
        toolbarActions.put(GLocalized.UNDO, action);
        action.setEnabled(false);
        
        fMenu.add( mi = new JMenuItem( action=new GEditorUIAction( GLocalized.REDO, GUIAction.GA_REDO ) ) );
        mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcutKeyMask|InputEvent.SHIFT_MASK ) );
        toolbarActions.put(GLocalized.REDO, action);
        action.setEnabled(false);
        
        fMenu.add( mi = new JMenuItem( new GEditorUIAction( GLocalized.CUT, GUIAction.GA_CUT ) ) );
        mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_X, shortcutKeyMask ) );
        
        fMenu.add( mi = new JMenuItem( new GEditorUIAction( GLocalized.COPY, GUIAction.GA_COPY ) ) );
        mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_C, shortcutKeyMask ) );
        
        fMenu.add( mi = new JMenuItem( new GEditorUIAction( GLocalized.PASTE,GUIAction.GA_PASTE ) ) );
        mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_V, shortcutKeyMask ) );
        
        fMenu.add( mi = new JMenuItem( new GEditorUIAction( GLocalized.SELECTALL,GUIAction.GA_SELECTALL ) ) );
        mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_A, shortcutKeyMask ) );
        
        fMenu.add( mi = new JMenuItem( action=new GFindUIAction( GLocalized.FIND, GLocalized.FIND_ICON, GUIAction.GA_FIND ) ) );
        toolbarActions.put(GLocalized.TBFIND, action);
		mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_F, shortcutKeyMask ) );
        mi.setIcon(null);
		
        fMenu.insertSeparator(2);
        fMenu.insertSeparator(7);
        
        return fMenu;
              
    }
    
    public static JMenu   bookmarkMenu(int shortcutKeyMask)
    {

        GBookmarkMenu fMenu = new GBookmarkMenu(GLocalized.BOOKMARK_MENU);
        fMenu.bm_bookmarksChanged();
        GBookmarkManager.addStatusListener(fMenu);
        
        return (GBookmarkMenu)fMenu;
              
    }
    
    public static JMenu   helpMenu(int shortcutKeyMask)
    {
        JMenuItem   mi;
        // create the file menu
        JMenu fMenu = new JMenu(GLocalized.HELP_MENU);
        
        fMenu.add( new JMenuItem( new GHelpAction( GLocalized.RELAYHOME, GUIAction.GA_RELAYHOME ) ) );
        fMenu.add( new JMenuItem( new GHelpAction( GLocalized.MENU_EXPORT_DIAGNOSTICS, GUIAction.GA_EXPORT_DIAGNOSTICS ) ) );
		//fMenu.add( new JMenuItem( new GHelpAction( GLocalized.FORUM, GUIAction.GA_SUPPORTFORUM ) ) );

        if( !GLocalized.onMacOSX )
        {
            fMenu.add( new JMenuItem( new GUIAction( GLocalized.ABOUTRELAY, GUIAction.GA_ABOUTRELAY ) ) );
            fMenu.insertSeparator(2);
        }
        
        return fMenu;
              
    }
    
    public static JMenu   specialMenu( int shortcutKeyMask, HashMap toolbarActions )
    {
        JMenuItem   mi;
        GUIAction   action;
        // create the file menu
        JMenu fMenu = new JMenu(GLocalized.SPECIAL_MENU);

        /*
        fMenu.add( mi = new JMenuItem( action=new GUIAction( GLocalized.TB_SIDEBAR, GLocalized.SIDEBAR_ICON, GUIAction.GA_SHOWSIDEBAR ) ) );
        toolbarActions.put(GLocalized.TB_SIDEBAR, action);
        mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_E, shortcutKeyMask ) );
        mi.setText(GLocalized.SHOWSIDEBAR);
        mi.setIcon(null);
        
        fMenu.addSeparator();
        */
        
        fMenu.add( mi = new JMenuItem( action=new GCancelUIAction( GLocalized.CANCEL, GLocalized.CANCEL_ICON, GUIAction.GA_CANCEL ) ) );
        mi.setIcon(null);
        mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_PERIOD, shortcutKeyMask ) );
        toolbarActions.put( GLocalized.TB_CANCEL, action );
        
        fMenu.add( mi = new JMenuItem( action=new GUIAction( GLocalized.REFRESH, GLocalized.REFRESH_ICON, GUIAction.GA_REFRESH ) ) );
        mi.setIcon(null);
        mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_R, shortcutKeyMask ) );
        toolbarActions.put(GLocalized.TB_REFRESH, action);

        fMenu.add( mi = new JMenuItem( action=new GCloseTabDisconnectUIAction( GLocalized.DISCONNECT, GLocalized.DISCONNECT_ICON, GUIAction.GA_DISCONNECT ) ) );
        mi.setIcon(null);
        mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_PERIOD, shortcutKeyMask ) );
        toolbarActions.put(GLocalized.TB_DISCONNECT, action);
    
        fMenu.addSeparator();
		
        if( GideonConfiguration.ENABLEDFOLLOWME )
        {
                fMenu.add( mi = new JMenuItem( action=new GUIAction( GLocalized.ENABLEFOLLOWME, null, GUIAction.GA_FOLLOWME ) ) );
        }

        // if we aren't under os x show the transfer filter and action log items here
        // then add the preferences access in...
        if( !GLocalized.onMacOSX )
        {           
                    fMenu.addSeparator();
                    fMenu.add( mi = new JMenuItem( action=new GShowTransferFiltersAction( GLocalized.TRANSFERFILTERS ) ) );
                    fMenu.addSeparator();
                    fMenu.add( new JMenuItem( new GUIAction( GLocalized.OPTIONS, null, GUIAction.GA_OPENPREFERENCES ) ) );
        }

        return fMenu;
              
    }

    
}
