//
//  GMenuBar.java
//  Gideon
//
//  Created by David on 10/18/04.
//  Copyright 2004 __MyCompanyName__. All rights reserved.
//

package com.gideonsoftworks.gideon.core;

import javax.swing.*;
import java.util.*;

public class GMenuBar extends JMenuBar
{
        private HashMap gmbToolbarActions=null;
            
        public void setToolbarActionHashmap( HashMap tbActions )
        {
            
            // error checks
            if( tbActions == null )
            {
                throw new NullPointerException();
            }
            
            gmbToolbarActions = tbActions;
        
        }
        
        public  HashMap getToolbarActions()
        {
        
            return gmbToolbarActions;
            
        }
        
}
