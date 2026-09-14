//
//  GQueue.java
//  Gideon
//
//  Created by David on Mon Jul 19 2004.
//  Copyright (c) 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gja;

import com.gideonsoftworks.gja.*;
import com.gideonsoftworks.gcore.GListenableItem;
import java.util.*;

/**
   An GJobQueue is used to manage a list of GJobDescription objects.
   Supports multiple concurrent active jobs for parallel transfers.
*/

public class GQueue extends GListenableItem
{

    private Vector              gqJobList   = new Vector();
    private String              gqName      = null;
    private GJobDescription     gjmKeyJob   = null;          // legacy single active job
    private Set<GJobDescription> activeJobs = new HashSet<>(); // parallel active jobs
    private int                 maxActiveJobs = 1;           // default: sequential

/**
  Use this method to create a new empty queue.
*/
public  GQueue()
{

}

/**
  Use this method to create a new queue with the contents of specified queue file.
  @param name Name for new queue.
*/
public  GQueue( final String name )
{

    if( name == null )
    {
        throw new NullPointerException("GQueue->GQueue : null name passed in");
    }

    gqName = name;

}

public  String  getName()
{

    return gqName;

}

public void setMaxActiveJobs(int max) { maxActiveJobs = Math.max(1, max); }
public int getMaxActiveJobs() { return maxActiveJobs; }

/**
  Returns the current "in progress" job (legacy — returns first active job)
*/
public  GJobDescription getKeyJob()
{
    return gjmKeyJob;
}

/**
  Returns true if the queue can accept more active jobs.
*/
public boolean canStartMore()
{
    return activeJobs.size() < maxActiveJobs;
}

/**
  Returns the number of currently active jobs.
*/
public int getActiveJobCount()
{
    return activeJobs.size();
}

/**
  Add a job as active.
*/
public void addActiveJob(GJobDescription jd)
{
    activeJobs.add(jd);
    if (gjmKeyJob == null) gjmKeyJob = jd;
}

/**
  Remove a job from active set.
*/
public void removeActiveJob(GJobDescription jd)
{
    activeJobs.remove(jd);
    if (gjmKeyJob == jd) {
        gjmKeyJob = activeJobs.isEmpty() ? null : activeJobs.iterator().next();
    }
}

/**
  Returns true if a queued or active job other than "excluding" belongs to originator.
  Used by the browser to coalesce per-batch work (e.g. one buddy refresh after the
  last transfer of a batch instead of one per job).
*/
public boolean hasOtherJobsForOriginator( final Object originator, final GJobDescription excluding )
{

    if( originator == null ) return false;

    try
    {
        for( int i = 0 ; i < gqJobList.size() ; i++ )
        {
            GJobDescription jd = (GJobDescription)gqJobList.get(i);
            if( jd != null && jd != excluding && originator.equals( jd.getOriginator() ) ) return true;
        }

        Iterator it = new ArrayList( activeJobs ).iterator();
        while( it.hasNext() )
        {
            GJobDescription jd = (GJobDescription)it.next();
            if( jd != null && jd != excluding && originator.equals( jd.getOriginator() ) ) return true;
        }
    }
    catch( Exception e )
    {
        // a job is being added/removed right now -> treat as busy
        return true;
    }

    return false;

}

/**
  Use this method to add a newly created job to the queue for processing.
  @param j Job to add to the queue.
  @return returns true if successful
*/
public boolean addJobToQueue( final GJobDescription j )
{

    if( j == null )
    {
        throw new NullPointerException("GQueue : null GJobDescription passed in to addJobToQueue");
    }

    if(gqJobList.add(j))
    {
        j.setParentQueue(this);
		j.getJobStatus().setState(GJobStatus.STATE_QUEUED);
        notifyModified( new GQueueEvent( GQueueEvent.STATUS_QUEUE_CONTENTS_CHANGED, this, j ) );
        return true;
    }

    return false;

 }

/**
  Remove a job from the queue.
  @param j Job to remove.
  @return returns true if successful
*/
public boolean removeJobFromQueue( final GJobDescription j )
{

    GJobStatus  jobStatus;

    if( j == null )
    {
        throw new NullPointerException("GQueue : null GJobDescription passed in to removeJobFromQueue");
    }

    jobStatus = j.getJobStatus();

    if( jobStatus.isRunning() )
    {
    }

    if(gqJobList.removeElement(j))
    {
        j.setParentQueue(null);
        removeActiveJob(j);
        this.notifyModified( new GQueueEvent( GQueueEvent.STATUS_QUEUE_CONTENTS_CHANGED, this, null ) );
        return true;
    }

    return false;

}

/**
  Returns number of jobs in the queue.
  @return returns job count
*/
public int getJobCount(){ return gqJobList.size(); }

/**
  Returns next unprocessed job
  @return returns next queued job (FIFO)
*/
public GJobDescription getNextQueuedJob()
{
    GJobDescription nextJob;

    if( gqJobList.isEmpty() ) return null;

    for( int i = 0 ; i < gqJobList.size() ; i++ )
    {
        nextJob = (GJobDescription)gqJobList.get(i);

        // if it is queued, it hasn't been started
        if( nextJob.getJobStatus().isQueued() )
        {
            return nextJob;
        }

    }

    return null;

}

/**
  Returns job at index
  @param i index of job to get
*/
public GJobDescription getJob( final int i ){ return (GJobDescription)gqJobList.get(i); }

/**
  Removes all jobs from the queue.
*/
/** Copy of the jobs currently active on this queue (all of them, not just the legacy key job). */
public synchronized List<GJobDescription> getActiveJobs()
{
    return new ArrayList<>( activeJobs );
}

public void flushQueue()
{

    gqJobList.clear();
    activeJobs.clear();
    gjmKeyJob = null;

}

private synchronized void notifyModified(GQueueEvent e) {
      // Iterate a snapshot: a listener that removes itself during dispatch (e.g. a one-shot
      // completion waiter) must not cause the next listener to be skipped by the index loop.
      Iterator en = new ArrayList( listeners ).iterator();
      while (en.hasNext()) {
            GQueueEventListenerInterface listener = (GQueueEventListenerInterface)en.next();

            switch( e.getStatusCode() )
            {

                case GQueueEvent.STATUS_QUEUE_CONTENTS_CHANGED:

                    listener.jobCountChanged(e);

                break;

                default:

                    System.out.println("Unknown Queue Event Fired");

                break;

            }

      }
    }

}
