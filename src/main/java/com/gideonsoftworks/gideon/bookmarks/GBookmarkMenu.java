//
//  GBookmarkMenu.java
//  Gideon
//
//  Created by David on 10/31/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.bookmarks;

import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gcore.*;

import java.awt.*;
import java.net.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import java.util.*;

public class GBookmarkMenu extends JMenu implements GBookmarkChangeListenerInterface {

public GBookmarkMenu( String name )
{

    super(name);

}

// called from the bookmark manager when it detects a 
// change in the bookmark list - ie add/removal
public void bm_bookmarksChanged()
{

    int         shortcutKeyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

    // clear the menu
    this.removeAll();
    
    // generate new menu
    JMenuItem   mi;
    // create the file menu
    this.add( mi = new JMenuItem( new GBookmarkUIAction( GLocalized.ADDBOOKMARK, null, GBookmarkUIAction.GA_BOOKMARK_ADD ) ) );
    mi.setAccelerator( KeyStroke.getKeyStroke(KeyEvent.VK_D, shortcutKeyMask ) );
    
    this.add( new JMenuItem( new GBookmarkUIAction( GLocalized.MANAGEBOOKMARK, null, GBookmarkUIAction.GA_BOOKMARK_MANAGE ) ) );
    
    this.insertSeparator(2);

    int cmCount = GBookmarkManager.getBookmarkCount();
    
	if( cmCount == 0 )
	{	

		    add( mi=new JMenuItem( GLocalized.NOBOOKMARKS ) );
			mi.setEnabled(false);
			
	}else{
	
		for( int i = 0 ; i < cmCount ; i++ )
		{
			GBookmarkUIAction   act = null;
			GBookmark   bm = GBookmarkManager.getBookmark(i);
			this.add( mi = new GBookmarkMenuItem( act = new GBookmarkUIAction( bm.getName(), null, GBookmarkUIAction.GA_BOOKMARK_OPEN ) ) );
			bm.addStatusListener(mi);
			
			if( bm.isSecure() )
			{
				mi.setIcon(GLocalized.SECURE_ICON);
			}else{
				mi.setIcon(GLocalized.INSECURE_ICON);
			}
			
			act.setRepresentedObject(bm);
		}

	}

}

// ignored
public void bm_bookmarkChanged(GBookmark bm)
{


}

}
