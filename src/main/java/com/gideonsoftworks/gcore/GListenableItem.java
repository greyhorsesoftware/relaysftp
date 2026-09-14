//
//  GListenableItem.java
//  Gideon
//
//  Created by David on 10/20/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gcore;

import java.util.*;

public class GListenableItem 
{

    protected ArrayList       listeners = new ArrayList();

    public synchronized void addStatusListener(Object ml) {
        
        if( ml == null )
        {
            throw new NullPointerException("GListenableItem:addStatusListener null listener");
        }

        listeners.add(ml);
        
    }

    public synchronized void removeStatusListener(Object ml) {

       listeners.remove(ml);
    }
    
    public synchronized void clearListeners()
    {
        listeners.clear();
    }

}
