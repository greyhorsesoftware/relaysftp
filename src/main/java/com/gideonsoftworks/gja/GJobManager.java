//
//  GJobManager.java
//  Gideon
//
//  Created by David on Mon Jul 19 2004.
//  Copyright (c) 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gja;

import com.gideonsoftworks.gja.*;
import com.gideonsoftworks.gcore.*;
import com.gideonsoftworks.gideon.core.GideonConfiguration;
import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gcore.GListenableItem;
import java.util.*;

/**
  The GJobManager class handles starting/stopping the processing of a GSJob. 
  It creates a new thread, loads the correct plugin to handle the job and starts the job on its way.
*/

public class GJobManager extends GListenableItem implements GJobExecuterListenerInterface
{

	// shared by ALL job manager instances
	//private			static				GEventLogManager	gjmEventLog		=	new GEventLogManager();

    private         String				gjmName             =   null;
    private         HashMap				gjmExecuters        =   new HashMap();
    private         ArrayList			gjmZombies          =   new ArrayList(); // array of zombie jobs
    private         GQueue				gjmTaskQueue        =   new GQueue(GLocalized.TASKS);
    private         GQueue				gjmTransferQueue    =   new GQueue(GLocalized.TRANSFERS);
    private         GJobDescription		gjmErroredJob       =   null;
    private         boolean				shuttingDown        =   false;
	
	
/*public static	GEventLogManager	getGlobalEventManager()
{

	return gjmEventLog;

}*/
	
public GJobManager( String name )
{

    gjmName = name;

}

/**
*
*
*
*/

public boolean  isShuttingDown()
{
    return shuttingDown;
}

/**
  Shuts down this job manager. 
        Stops all jobs
        Flushes all queues, 
        Releases ( ie sets to null ) all queues
*/
public  void    shutdown()
{
    shutdown( 0 );
}

/**
  Shuts down this job manager: stops EVERY active job in both queues (not just each queue's legacy
  key job — parallel transfers have several), then waits up to {@code joinMs} for their worker
  threads to end before flushing. The executer map is kept so a late cancellation callback from a
  job that was still unwinding cannot NPE on the EDT.
  @param joinMs how long to wait for interrupted workers; 0 = do not wait (the old behaviour)
*/
public  void    shutdown( final long joinMs )
{
    // flag used in stopJob() etc...
    shuttingDown = true;
    this.notifyModified(new GJobManagerEvent( GJobManagerEvent.JM_SHUTTING_DOWN, null ));

    // stop every active job and remember its thread
    List<Thread> workers = new ArrayList<>();
    GQueue[] queues = { gjmTaskQueue, gjmTransferQueue };
    for( GQueue q : queues )
    {
        if( q == null ) continue;
        for( GJobDescription j : q.getActiveJobs() )
        {
            GJobExecuter ex = ( gjmExecuters != null ) ? (GJobExecuter)gjmExecuters.get(j) : null;
            if( ex == null ) continue;
            Thread t = ex.getThread();
            if( t != null ) workers.add( t );
            try { ex.stop(); } catch( RuntimeException ignore ) {}
        }
    }

    // clear queues
    if( gjmTransferQueue != null ) gjmTransferQueue.flushQueue();
    if( gjmTaskQueue != null ) gjmTaskQueue.flushQueue();

    // bounded join: cooperative cancellation is checked between steps; a socket read that
    // ignores the interrupt is unblocked later by the connection logout, or by the watchdog
    stopAndJoinAll( workers, joinMs );

    // null out the queues - help the gc
    gjmTaskQueue        = null;
    gjmTransferQueue    = null;
    gjmErroredJob       = null;
}

/** Join every thread against one shared deadline; never waits longer than {@code joinMs} in total. */
static void stopAndJoinAll( final Collection<Thread> workers, final long joinMs )
{
    long deadline = System.currentTimeMillis() + joinMs;
    for( Thread t : workers )
    {
        if( t == null || !t.isAlive() ) continue;
        long remaining = deadline - System.currentTimeMillis();
        if( remaining <= 0 ) break;
        try { t.join( remaining ); } catch( InterruptedException e ) { Thread.currentThread().interrupt(); break; }
    }
}
                
/**
  Returns the application wide job queue.
*/
public GQueue  getTaskQueue()
{

    return gjmTaskQueue;

}

/**
  Returns this job managers transfer queue.
*/
public GQueue  getTransferQueue()
{
    
    return gjmTransferQueue;

}

public boolean  hasRunningJobs()
{

    if(gjmExecuters.size()>0) return true;
    return false;

}


/**
  This method is used to begin processing a specific job. It handles creating a GJobExecuter 
  and an associated GJobStatus object ( which is assigned to the GJobExecuter ). 
  The Job Manager registers itself as a listener for GJobStatus event messages. 
  The callback is used to propogate status information to GJobManager listeners ( implement GJobManagerListenerInterface ). 
  This method also adds the created GJobExecuter object to its internal "running job" array.
  @param q Queue to get next job from.
*/
public void startNextJobForQueue( GQueue q )
{
    // Fill available slots — supports parallel transfers
    while( q.canStartMore() )
    {
        GJobDescription j = q.getNextQueuedJob();
        if( j == null ) break;

        if( GideonConfiguration.DEBUG_GJA ) System.out.println("GJobManager->startJob: " + j.getName());

        GJobStatus newStatusObject = new GJobStatus(j);
        GJobExecuter newExecuter = new GJobExecuter(newStatusObject, this);

        if(!newExecuter.loadPluginForJob())
        {
            throw new IllegalArgumentException("GJobManager->startJob() : No plugin for job description type");
        }

        newStatusObject.setJobExecuter(newExecuter);
        j.setJobStatus(newStatusObject);

        gjmExecuters.put(j, newExecuter);
        q.addActiveJob(j);

        this.notifyModified(new GJobManagerEvent( GJobManagerEvent.JM_KEY_JOB_CHANGED, j ));
        this.notifyModified(new GJobManagerEvent( GJobManagerEvent.JM_JOB_STARTED, j ));

        newExecuter.start();
    }
}

/**
  This method is used to stop processing a specific job. This method also removes the job manager as a listener for
  GJobStatus messages specific to this job.
  @param j GJobDescription to stop
*/
public void stopJob(final GJobDescription j )
{

    GJobExecuter jdExecuter = (GJobExecuter)gjmExecuters.get(j);
    
    if( jdExecuter == null )
    {
        System.out.println("GJobManager->stopJob : Could not find executer for job.");
        return;
    }
    
    // stop the executer thread
    jdExecuter.stop();

    // login jobs are immediately "cancelled"
    // all other jobs we want to wait for them to "stop"
    if( j.isLoginJob() )
    {
        jobCancelled(j);
        // record this zombie ;)
        gjmZombies.add(j);
    }

}

/**
*
* This method is used to stop the current key job.
*
*/

public  void    stopKeyJob()
{
    
    // stop any job in the "tasks" queue...
    if( !stopKeyJobInQueue(getTaskQueue()) )
    {
        // ...no job running in task queue - stop the transfer in progress if there is one
        stopKeyJobInQueue(getTransferQueue());
    }
    
}

public  boolean   stopKeyJobInQueue(GQueue q)
{

    GJobDescription job = null;
    
    if( ( job = q.getKeyJob() ) != null )
    {
        if( !shuttingDown ) this.notifyModified(new GJobManagerEvent( GJobManagerEvent.JM_JOB_BEING_CANCELLED, job ));
        this.stopJob(job);
        return true;
    }
    
    return false;
    
}

/**
*
* Callbacks from the GJobExecuter class
*
*
*/

public  void    jobCancelled( GJobDescription j )
{
    
    if( j == null ) System.out.println("job desc into job cancelled was null");
    
    // remove the job from the running list.
    gjmExecuters.remove(j);
    
    /// notify the assholes listening to the job state change
    if( !shuttingDown ) notifyModified(new GJobManagerEvent( GJobManagerEvent.JM_JOB_STOPPED, j ));
        
    if( !shuttingDown ) cleanupAndStartNextJob(j);

}

public  void    jobCompleted( GJobDescription j )
{
        
    // null job desc bail
    if( j == null ) return;
         
    // remove the job from the running list.
    gjmExecuters.remove(j);
    
    // notify listeners the job is done.
    notifyModified(new GJobManagerEvent( GJobManagerEvent.JM_JOB_COMPLETED, j ));
        
	// start next job
    cleanupAndStartNextJob(j);

}

/**
*
*		The job has had a collision OR needs user intervention
*
*/
public  void    jobNeedsInput( GJobDescription j )
{
        
    // null job desc bail
    if( j == null ) 
	{
		throw new NullPointerException("GJobManagaer-jobNeedsInput - Job Description was null");
	}
    
    // notify listeners the job is done.
    notifyModified(new GJobManagerEvent( GJobManagerEvent.JM_JOB_NEEDS_INPUT, j ));

}

/**
*
* Used to return the queue to normal state before starting next job
*       Null out key job
*       Remote job from queue
*
*/
private   void   cleanupAndStartNextJob( GJobDescription j )
{

    GQueue q = j.getParentQueue();

    if( q == null )
    {
        throw new NullPointerException("GJobManager cleanupAndStartNextJob : null parent queue");
    }

    // remove from active set and queue
    q.removeActiveJob(j);
    q.removeJobFromQueue(j);

    // fill any open slots
    startNextJobForQueue(q);

}

public  void    jobFailed( GJobDescription j )
{

    // record this - it's later used in clearErroredJob()
    gjmErroredJob = j;

    // remove from running list
    gjmExecuters.remove(j);

    // notify listeners the job failed
    this.notifyModified(new GJobManagerEvent( GJobManagerEvent.JM_JOB_FAILED, j ));

    // release the queue slot so next job can run
    cleanupAndStartNextJob(j);

}

/**
  Remove the errored job from the queue and begin processing next job.
*/
public void clearErroredJob()
{
    if (gjmErroredJob == null) return;
    gjmExecuters.remove(gjmErroredJob);
    GQueue pQ = gjmErroredJob.getParentQueue();
    if (pQ != null) {
        pQ.removeActiveJob(gjmErroredJob);
        pQ.removeJobFromQueue(gjmErroredJob);
    }
	    
	// begin processing next job
    startNextJobForQueue(pQ);
    gjmErroredJob = null;
}


/**
  Dispatch method to inform listeners of job status changes.
*/

private synchronized void notifyModified(GJobManagerEvent e) {
      // Iterate a snapshot: a listener that removes itself during dispatch (e.g. a one-shot
      // completion waiter) must not cause the next listener to be skipped by the index loop.
      List snapshot = new ArrayList( listeners );

	  // get the executer out of the hash
	  GJobExecuter je = (GJobExecuter)gjmExecuters.get(e.getJobDescription());

      for( int i = 0 ; i < snapshot.size() ; i++ ) {
            GJobManagerListenerInterface listener = (GJobManagerListenerInterface)snapshot.get(i);
            
            switch( e.getStatusCode() )
            {   
            
                case GJobManagerEvent.JM_SHUTTING_DOWN:
                
                    if( GideonConfiguration.DEBUG_GJA ) System.out.println("\tJM_SHUTTING_DOWN " + listener);

                    listener.jm_shuttingDown( e );
                    
                break;
                
                case GJobManagerEvent.JM_JOB_FAILED:
 
                    if( GideonConfiguration.DEBUG_GJA ) System.out.println("\tJM_JOB_FAILED " + listener);
                    
                    listener.jm_jobFailed(e);
                    
                break;
                
                case GJobManagerEvent.JM_KEY_JOB_CHANGED:
                    
                    if( GideonConfiguration.DEBUG_GJA ) System.out.println("\tJM_KEY_JOB_CHANGED " + listener);
                    
                    listener.jm_keyJobChanged(e);
                    
                break;
                
                case GJobManagerEvent.JM_JOB_STARTED:
                    
                    if( GideonConfiguration.DEBUG_GJA ) System.out.println("\tJM_JOB_STARTED " + listener);
                    
                    listener.jm_jobStarted(e);
                    
                break;
                
                case GJobManagerEvent.JM_JOB_STOPPED:
                
                    if( GideonConfiguration.DEBUG_GJA ) System.out.println("\tJM_JOB_STOPPED " + listener);
                    
                    listener.jm_jobStopped(e);
                    
                break;
                
                case GJobManagerEvent.JM_JOB_NEEDS_INPUT:
                    
                    if( GideonConfiguration.DEBUG_GJA ) System.out.println("\tJM_JOB_NEEDS_INPUT " + listener);

					// call the callback - specifying the user message
                    listener.jm_jobNeedsInput(e, je.getJobPlugin().getJobUserMessage());
                    
                break;
                
                case GJobManagerEvent.JM_JOB_HAS_MESSAGE:
                    
                    if( GideonConfiguration.DEBUG_GJA ) System.out.println("\tJM_JOB_HAS_MESSAGE " + listener);
                    
                    listener.jm_jobHasMessage(e);
                    
                break;
                
                case GJobManagerEvent.JM_JOB_COMPLETED:
                    
                    if( GideonConfiguration.DEBUG_GJA ) System.out.println("\tJM_JOB_COMPLETED " + listener);
                    
                    listener.jm_jobCompleted(e);
                    
                break;

                case GJobManagerEvent.JM_JOB_BEING_CANCELLED:
                    
                    if( GideonConfiguration.DEBUG_GJA ) System.out.println("\tJM_JOB_BEING_CANCELLED " + listener);
                    
                    listener.jm_jobCancelling(e);
                    
                break;
	
                default:

                    System.out.println("Unknown Job Manager Event Fired");
                
                break;
                
            }

      }
      
      if( GideonConfiguration.DEBUG_GJA ) System.out.println("GJobManager->notifyModified : done.");
      
    }
    
}