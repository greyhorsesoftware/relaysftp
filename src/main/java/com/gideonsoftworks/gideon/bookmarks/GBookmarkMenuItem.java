//
//  GBookmarkMenuItem.java
//  Gideon
//
//  Created by David on 10/31/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.bookmarks;

import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gcore.*;
import javax.swing.*;

public class GBookmarkMenuItem extends JMenuItem implements GBookmarkChangeListenerInterface
{

    public GBookmarkMenuItem(Action a)
    {
    
        super(a);
    
    }
   
    public void bm_bookmarkChanged(GBookmark bm)
    {
            
        Action  myAction = this.getAction();

        myAction.putValue(Action.NAME, bm.getName());
        
        if( bm.isSecure() )
        {
            myAction.putValue(Action.SMALL_ICON, GLocalized.SECURE_ICON);
        }else{
            myAction.putValue(Action.SMALL_ICON, GLocalized.INSECURE_ICON);
        }
   
    }

    // ignored
    public void bm_bookmarksChanged()
    {
    
    }


}
