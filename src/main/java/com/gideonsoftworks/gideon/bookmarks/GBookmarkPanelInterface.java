//
//  GBookmarkPanelInterface.java
//  GSFTP
//
//  Created by David on 9/11/05.
//  Copyright 2005 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.bookmarks;

import com.gideonsoftworks.gcore.*;

public interface GBookmarkPanelInterface 
{
    public void populateForBookmark(GBookmark bm);
    public void saveCurrentBookmark();
}
