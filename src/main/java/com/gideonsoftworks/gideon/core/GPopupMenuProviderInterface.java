//
//  GPopupMenuProviderInterface.java
//  GSFTP
//
//  Created by David on 1/8/05.
//  Copyright 2005 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.core;

import com.gideonsoftworks.gcore.*;
import javax.swing.*;

public interface GPopupMenuProviderInterface
{
    public  JPopupMenu  popupMenu(GFileItem[] selection);
}