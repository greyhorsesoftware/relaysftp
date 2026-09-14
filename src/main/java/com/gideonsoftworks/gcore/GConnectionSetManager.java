//
//  GConnectionSetManager.java
//  Gideon
//
//  Created by David on 10/19/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gcore;

import com.gideonsoftworks.gja.*;
import java.util.*;
import java.lang.ref.*;

public class GConnectionSetManager
{

    private WeakReference   gcsmActiveConnectionSet = null;
    private ArrayList       gcsmConnectionSets = new ArrayList();

    public  GConnectionSetManager()
    {
    
        
    }
    
    public  boolean    containsConnectionSet( final GConnectionSet cs )
    {
        
        return gcsmConnectionSets.contains(cs);
 
    }
    
    public void removeConnectionSet( final GConnectionSet cs )
    {
        if( cs == null )
        {
            throw new NullPointerException("GConnectionSetManager removeConnectionSet - null GConnectionSet");
        }
        
        removeConnectionSet(cs, false, true);
    }
    
    public  void    removeConnectionSetNoAutoSet( final GConnectionSet cs )
    {
    
        if( cs == null )
        {
            throw new NullPointerException("GConnectionSetManager removeConnectionSet - null GConnectionSet");
        }
        
        removeConnectionSet(cs, false, false);
    
    }
    
    public  void    removeConnectionSet( final GConnectionSet cs, final boolean doUpdate, final boolean assignNewConnectionSet )
    {

        if( cs == null )
        {
            throw new NullPointerException("GConnectionSetManager addConnectionSet - null GConnectionSet");
        }

        gcsmConnectionSets.remove(cs);
        
        if( assignNewConnectionSet )
        {
        
            GConnectionSet  cS = getConnectionSet(0);
                
            System.out.println("Setting Active : " + cS.getName() );
                
            setActiveConnectionSet(cS);
 
        }
 
    }

    public  void    addConnectionSet( final GConnectionSet cs, final boolean makeActiveSet )
    {
  
        if( cs == null )
        {
            throw new NullPointerException("GConnectionSetManager addConnectionSet - null GConnectionSet");
        }
    
        gcsmConnectionSets.add(cs);

        if(makeActiveSet)
        {
            this.setActiveConnectionSet(cs);
        }
        
    }
    
    /**
    *
    *   Adds all connection sets to the reaper.
    *
    */
    public void reapAll()
    {
        // walk all connection sets and add them to the reaper
        Iterator i = gcsmConnectionSets.iterator();
        
        while( i.hasNext() )
        {
        
            GConnectionSet  cs = (GConnectionSet)i.next();
            
            if( cs == null )
            {
                throw new NullPointerException("Attempted to reap null Connection Set");
            }
            
            reap( cs, false );
        
        }
    
        gcsmConnectionSets.clear();
    
    }
    
    public void reap(final GConnectionSet cs )
    {
          reap(cs, true );  
    }
    
    private void reap(final GConnectionSet cs, final boolean removeConnectionSet )
    {
        GConnectionReaper   sReaper = GConnectionReaper.getSharedReaper();
        
        if( cs == null )
        {
            throw new NullPointerException("Attempted to reap null Connection Set");
        }

        // reap the connection set
        sReaper.reapConnectionSet(cs);
        
        // remove the connection set from the array
        if( removeConnectionSet ) gcsmConnectionSets.remove(cs);
        
    }
    
    public  void    setActiveConnectionSet( final GConnectionSet acs )
    {
        if( acs == null )
        {
            throw new NullPointerException("GConnectionSetManager setActiveConnectionSet - null GConnectionSet");
        }
        
        // make sure this connection isn't the same as last...
        if( ( gcsmActiveConnectionSet != null ) && 
            ( acs.equals(gcsmActiveConnectionSet.get()) ) )
        {
            System.out.println("Ignoring setActiveConnection");
            //...if it is ignore the request
            return;
        }
        
        gcsmActiveConnectionSet = new WeakReference(acs);
    }
    
    public  GConnectionSet    getActiveConnectionSet()
    {
		// handle null connection set 
		if( gcsmActiveConnectionSet == null )
		{
			return null;
		}

        return  (GConnectionSet)gcsmActiveConnectionSet.get();
        
    }
    
    public  GConnectionSet    getConnectionSet( int i )
    {
        return  (GConnectionSet)gcsmConnectionSets.get(i);
    }

}
