//
//  GConnectionReaper.java
//  GSFTP
//
//  Created by David on 10/23/05.
//  Copyright 2005 Grey Horse Software All rights reserved.
//


package com.gideonsoftworks.gcore;

import java.util.*;
import com.gideonsoftworks.gcore.*;
import com.gideonsoftworks.gja.*;

/**
*
*   GConnectionReaper
*
*       This class takes a connection set and disconnects the connection.
*
*/

public class GConnectionReaper extends GAbstractJobManagerListener
{

// shared
private static  GConnectionReaper       sharedConnectionReaper  =  new GConnectionReaper();     
private GJobManager                     reaperJobManager            = null;
// instance
ArrayList    csArray            = new ArrayList();

public static  GConnectionReaper   getSharedReaper()
{
    return sharedConnectionReaper;
}

public GConnectionReaper()
{
    super();
    reaperJobManager = new GJobManager("Connection Reaper Job Manager");
    reaperJobManager.addStatusListener(this);
}

/** The reaper's own job manager (its DISCONNECT jobs), so an orderly quit can stop it too. */
public GJobManager getJobManager()
{
    return reaperJobManager;
}

public  void reapConnectionSet( GConnectionSet cs )
{
    GJobDescription disconnectJob = null;

    // issue disconnect for local connection
    GConnection lConnection = cs.getLocalConnection();
    
    if( lConnection != null )
    {
        csArray.add(lConnection);
    
        // reap it
        disconnectJob = new GJobDescription(GJobDescription.DISCONNECT_JOB_TYPE, 
                                                                lConnection, 
                                                                "Reaper" );
        disconnectJob.setOriginator(this);

        queueAndStartJob(disconnectJob);
    }
 
    // issue disconnect for remote connection
    GConnection rConnection = cs.getRemoteConnection();
    
    if( rConnection != null )
    {
    
        // add to our hashmap
        csArray.add(rConnection);
    
        // reap it
        disconnectJob = new GJobDescription(GJobDescription.DISCONNECT_JOB_TYPE, 
                                                                rConnection, 
                                                                "Reaper" );
        disconnectJob.setOriginator(this);

        queueAndStartJob(disconnectJob);

    }

    
    
}

public void jm_jobCompleted( final GJobManagerEvent e )
{   
    
    GJobDescription jd  = e.getJobDescription();
    GFileItem       ci  = null;
    
    // make sure we only watch for our job(s)
    if( jd.getOriginator().equals(this) )
    {
        
        int             jType   = jd.getJobType();
        GConnection     c       = (GConnection)jd.getConnectionToUse();
        
        switch(jType)
        {

            case GJobDescription.DISCONNECT_JOB_TYPE:
                
                System.out.println("Connection was disconnected : " + c.getBookmark().getHostname() );
                csArray.remove(c);
                
            break;
   
        }
        
    }
    
}

public  void    jm_jobFailed( final GJobManagerEvent e )
{

    GJobDescription jd  = e.getJobDescription();

    // make sure we only watch for our job(s)
    if( jd.getOriginator().equals(this) )
    {
        int             jType   = jd.getJobType();
        GConnection     c       = (GConnection)jd.getConnectionToUse();
        
        switch(jType)
        {
            // the user messed up the login - reenabled the connect button
            case GJobDescription.DISCONNECT_JOB_TYPE:
                    System.out.println("Connection disconnection failed : " + c.getBookmark().getHostname() );
                    csArray.remove(c);
            break;
        }
        
    }

}

private void    queueAndStartJob( GJobDescription theJob )
{
    GQueue          queue;
    // can happen - typically on shutdown
    if( reaperJobManager == null ) return;
    queue   =  reaperJobManager.getTaskQueue();
    queue.addJobToQueue(theJob);
    // tell the job manager to keep processing...
    reaperJobManager.startNextJobForQueue(queue);
}

}
