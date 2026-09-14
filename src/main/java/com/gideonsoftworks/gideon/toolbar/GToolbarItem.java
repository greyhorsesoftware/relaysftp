//
//  GToolbarItem.java
//  Gideon
//
//  Created by David on 10/16/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.toolbar;
import javax.swing.*;
import java.awt.*;

public class GToolbarItem extends JButton
{
    public  GToolbarItem( final Action action )
    {
     
        super( (Icon)action.getValue(Action.SMALL_ICON) );

        setToolTipText((String)action.getValue(Action.NAME));

        setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        putClientProperty("JButton.buttonType", "toolBarButton");  // flat, but highlights on hover/press
        setMinimumSize(new Dimension(36,36));

        setAction(action);
        setText(null);
    }

}
