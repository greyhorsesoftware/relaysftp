//
//  GTabBarItem.java
//  TabLand
//
//  Created by David on 5/19/06.
//  Copyright 2006 Grey Horse Software. All rights reserved.
//
package com.gideonsoftworks.tabkit;

import java.util.*;
import javax.swing.*;

/**
 * Lightweight data holder for a tab's content view and custom attributes.
 * Previously a custom-painted tab component; now just stores per-tab state
 * while JTabbedPane handles all rendering.
 */
public class GTabBarItem
{
    private String      tabItemTitle    = null;
    private JComponent  tabView         = null;
    private HashMap     attributes      = new HashMap();

    public GTabBarItem()
    {
    }

    public void setTabview( final JComponent view )
    {
        tabView = view;
    }

    public JComponent getTabview()
    {
        return tabView;
    }

    public Object getAttribute( final String key )
    {
        return attributes.get(key);
    }

    public void setAttribute( final String key, final Object value )
    {
        attributes.put(key, value);
    }

    public void setTitle( final String title )
    {
        tabItemTitle = title;
    }

    public String getTitle()
    {
        return tabItemTitle;
    }

}
