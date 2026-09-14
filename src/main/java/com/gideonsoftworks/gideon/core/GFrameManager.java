//
//  GFrameManager.java
//  Gideon
//
//  Created by David on 10/14/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.core;

import com.gideonsoftworks.gideon.actions.GUIAction;
import com.gideonsoftworks.gideon.actions.GWindowUIAction;
import com.gideonsoftworks.gideon.actions.GShowTransferFiltersAction;
import com.gideonsoftworks.gideon.browser.GBrowserWindow;
import com.gideonsoftworks.gideon.logviewer.GLogWindow;
import java.awt.*;
import java.net.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.*;
import java.util.List;
import javax.swing.event.*;

/** The GFrameManager is used to mangaer all Gideon windows.  GFrameManager provides a central window menu ( Mac OS X ) 
and in conjunction with GUIACtion and GFrame helps provide streamlined menu/toolbar validation ( All Platforms ) */

public class GFrameManager implements WindowFocusListener, MenuListener
{
    private GFrame  gfmKeyWindow    = null;
    private HashMap gfmWindowList   = null;
    
    /**
        Create a new GFrameManager
    */
    public  GFrameManager()
    {
        gfmWindowList   = new HashMap();
    }


    /**
        Add a window to the window menu ( OS X ) and internal window list.
        @param win  GFrame to add to our window list and global window menu ( OS X )
    */
    public  void addToWindowMenu( final GFrame  win )
    {
        JMenuItem       mi;
        JMenu           windowMenu;
        Iterator        iIT         = null;
        Collection      items       = null;
        
        
        if( win == null )
        {
            throw new NullPointerException("<GFrameManager> : Window Event points to null window");
        }
        
        gfmWindowList.put( win, null );
        
        if( GLocalized.onMacOSX )
        {
            // walk through the window list and rebuilt each windows window menu
            items = gfmWindowList.keySet();
            iIT = items.iterator();
            while( iIT.hasNext() )
            {
               this.updateWindowMenusForWindow((JFrame)iIT.next(), null);            
            }
        }
        
        // register ourselves as a window listener
        win.addWindowFocusListener(this);
        
    }
    
    /**
        Remove a window from the window menu ( OS X ) and internal window list.
        @param win  GFrame to remove
    */
    public  void removeFromWindowMenu( final JFrame  win )
    {
        JMenu           windowMenu  = null;
        Iterator        iIT         = null;
        Collection      items       = null;
        
        if( win == null )
        {
            throw new NullPointerException("<GFrameManager> : Window Event points to null window");
        }

        // if under os x - rebuild the window menus
        if( GLocalized.onMacOSX )
        {    
            // walk through the window list and rebuilt each windows window menu
            items = gfmWindowList.keySet();
            iIT = items.iterator();
            while( iIT.hasNext() )
            {
               JFrame nWin = (JFrame)iIT.next();
               // remove from internal window list
               updateWindowMenusForWindow(nWin, null);            
            }
        }
        
        gfmWindowList.remove( win );
		
        win.removeWindowFocusListener(this);

        if( gfmWindowList.size() == 0 )
        {
            // zero out the key window thing
            gfmKeyWindow = null;
        }
        
    }
    
    /**
        Handles building a global window menu for each window.
        @param win  Window whose menu needs updating
        @param keyWindow  GFrame that will be "checked" in the global window menu
    */
    private void updateWindowMenusForWindow( JFrame win, JFrame keyWindow )
    {
    
        JMenuItem       mi          = null;
        Iterator        iIT         = null;
        JMenu           windowMenu  = null;
        Collection      items       = null;
        JMenuBar        mbar        = null;
        int             shortcutKeyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
		Action			action		= null;
		
        mbar = win.getJMenuBar();
            
        if(mbar==null)
        {
            return;
        }
            
        // get the window menu from this windows menu
        windowMenu = mbar.getMenu(4);

        if( windowMenu==null)
        {
            throw new NullPointerException("<GFrameManager> : update - Window points to null menu");
        }
            
        // clear the menu
        windowMenu.removeAll();
        
        // add the "top" items
        windowMenu.add( mi = new JMenuItem( new GWindowUIAction( GLocalized.MINIMIZE, null, GUIAction.GA_MINIMIZE ) ) );
        mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_M, shortcutKeyMask ) );
		
        windowMenu.addSeparator();

        windowMenu.add( mi = new JMenuItem( action=new GShowTransferFiltersAction( GLocalized.TRANSFERFILTERS ) ) );
        mi = new JMenuItem("Connections");
        mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_L, shortcutKeyMask | java.awt.event.KeyEvent.SHIFT_DOWN_MASK ) );
        mi.addActionListener(e -> GLogWindow.toggle());
        windowMenu.add(mi);

        windowMenu.add( GTheme.buildMenu() );

        windowMenu.addSeparator();
		
        // walk through the window list and add the items
        items = gfmWindowList.keySet();

        iIT = items.iterator();
        
        ButtonGroup bg = new ButtonGroup();
        
        while( iIT.hasNext() )
        {
        
            JMenuItem       wMI;
            JFrame          nWindow;
            GWindowUIAction wAct;
            
            nWindow = (JFrame)iIT.next();
            
            // add item to menu
            wMI = new JCheckBoxMenuItem( wAct = new GWindowUIAction( (String)nWindow.getTitle(), null, GUIAction.GA_SELECTWINDOW ) );
            bg.add(wMI);
            wAct.setRepresentedObject(nWindow);
            
            if( keyWindow!=null && nWindow.equals(keyWindow) )
            {
                // now get the menu item and place a check next to it
                //wMI.setIcon(GLocalized.MENUCHECK_ICON);
                wMI.setSelected(true);
            }

            // win -> mi hash
            gfmWindowList.put( nWindow, wMI );
            
            windowMenu.add(wMI);
            
        }
    
    }

    /**
        Part of WindowListener, we make the window that gained focus the key window.
        @param e  WindowEvent containing new key window
    */
    public  void    windowGainedFocus( WindowEvent e )
    {
        makeKeyWindow( (GFrame)e.getWindow() );
    }
    
    /**
        Calls updateWindowMenusForWindow() for each window.
        @param win  new key window
    */
    private  void makeKeyWindow( final GFrame win )
    {
        JMenuItem       mItem       = null;
        JMenuItem       mi          = null;
        Iterator        iIT         = null;
        Collection      items       = null;
        GMenuBar        mBar        = null;
        
        if( win == null )
        {
            throw new NullPointerException("<GFrameManager> makeKeyWindow - null window");
        }
        
        
        gfmKeyWindow = win;
        
        if( GLocalized.onMacOSX )
        {
        
            // walk through the window list and rebuilt each windows window menu
            items = gfmWindowList.keySet();
            iIT = items.iterator();
            while( iIT.hasNext() )
            {
               JFrame nWin = (JFrame)iIT.next();
               // remove from internal window list
               updateWindowMenusForWindow(nWin, win);
                           
            }

        }
        
        if( gfmKeyWindow.hasToolbar() )
        {
        
            mBar = (GMenuBar)gfmKeyWindow.getJMenuBar();
            
            if(mBar==null)
            {
                 return;
            }
            
            gfmKeyWindow.validateToolbarItems(mBar.getToolbarActions());
        
        }
        
    }
    
    public  boolean  isLastBrowserWindow( GFrame win )
    {
        int count = 0;
        for( Object w : gfmWindowList.keySet() )
        {
            if( w instanceof GBrowserWindow ) count++;
        }
        return count <= 1;
    }
    
    /**
        Returns the key window or null if no windows open.
        @return key window or null if no windows open.
    */
    /** Copy of the open browser windows (any order). */
    public  List<GBrowserWindow> getBrowserWindows()
    {
        List<GBrowserWindow> out = new ArrayList<>();
        if( gfmWindowList == null ) return out;
        for( Object w : new ArrayList<Object>( gfmWindowList.keySet() ) )
            if( w instanceof GBrowserWindow bw ) out.add( bw );
        return out;
    }

    public  GFrame getKeyWindow()
    {
        return gfmKeyWindow;
    }
    
    /**
        Used oto figure out if any windows are open
        @return true if window list has a 1 or more windows, false of no windows.
    */
    public  boolean getWindowsOpen()
    {
        
        
        if( gfmWindowList.size() > 0 )
        {
            return true;
        }
    
        return false;
    
    }
    
    /**
    *
    *
    *
    * Menu Validation
    *
    *
    *
    */

    /**
        Part of MenuListener, this method is called when a menu is about to be shown. At this time
        we "validate" the menu items contained in that menu. Validation is performed by calling validateMenuItem()
        on the menu items action. The action is then disabled/enabled depending on the return value from validateMenuItem()
        @param e  MenuEvent containing menu
    */
    public void menuSelected( final MenuEvent e )
    {
        
        JMenu   menu;
        
        menu = (JMenu)e.getSource();
        
        this.performMenuValidation(menu);
                        
    }
    
    /**
        For each Menu item in the JMenu, this method calls validateMenuItem() on each menu items action. 
        The action is then disabled/enabled depending on the return value from validateMenuItem(). 
        JMenu items have to implement the GValidationInterface interface. This method also handles 
        deactivating super menus if they contain no active items.
        @param menu  JMenu to validate
    */
    private  int   performMenuValidation( final JMenu menu )
    {
    
        if(GideonConfiguration.DEBUG_VALIDATION_CODE) System.out.println("Menu Showing : " + menu.getText() );
        
        // start our counter to keep track of "active" items
        // we do this so we know to deactivate submenu parent menu items
        // if non of their children are active.
        int activeCount = 0;
        
        // get the total count
        int count = menu.getItemCount();
        
        for( int i = 0 ; i < count ; i++ )
        {
            JMenuItem               cmItem = menu.getItem(i);

            // JSeparators return as null - I guess because they are not MenuItems
            if( cmItem==null ) 
            {   
                if(GideonConfiguration.DEBUG_VALIDATION_CODE) System.out.println("Null Item ie JSeparator" );
                continue;
            }

            GUIAction    theAction = (GUIAction)cmItem.getAction();
            
            if(GideonConfiguration.DEBUG_VALIDATION_CODE) System.out.print("Validating : " + cmItem.getText() + " " );

            // we have a valid action - handle calling validateMenu() 
            // and enabling/disabling the menu item
            if( theAction != null) 
            {
                
                if( !theAction.shouldValidateItem() ){ continue; }
                
                if( theAction.validateItem() )
                {
                    theAction.setEnabled(true);
                    activeCount++;
                }else{
                    theAction.setEnabled(false);
                }
            
                if(GideonConfiguration.DEBUG_VALIDATION_CODE) System.out.println("done.");

            }else{
                
                // we had no action so we check to see if the item is a menu
                // if it is we recursively call performMenuValidation()
                if( (Object)cmItem instanceof JMenu )
                {
                    if( this.performMenuValidation((JMenu)cmItem) == 0 )
                    {
                        // if this sub menu does not have ANY active items - deactivate it.
                        cmItem.setEnabled(false);
                    }
                    else
                    {
                        activeCount++;   // sub-menu has active items — it counts toward its parent
                    }
                }
                else if( cmItem.isEnabled() )
                {
                    // a plain (non-GUIAction) leaf item — e.g. the Theme radios or "Connections".
                    // It's always active, so count it; otherwise an all-plain sub-menu gets disabled.
                    activeCount++;
                }

            }
            
            
        }
        
        // return the number of active menu items
        return  activeCount;
        
    }
    
        
    /**
        Part of WindowListener - not used
        @param e  Window whose menu needs updating
    */
    public  void    windowLostFocus( WindowEvent e )
    {

    }
    
    /**
        Part of MenuListener - not used
        @param e  MenuEvent
    */
    public void menuDeselected( MenuEvent e )
    {
    
    
    }
    
    /**
        Part of MenuListener - not used
        @param e  MenuEvent
    */
    public void menuCanceled( MenuEvent e )
    {
    
    
    }

}
