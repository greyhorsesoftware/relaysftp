//
//  GJobStatus.java
//  Gideon
//
//  Created by David on Mon Jul 19 2004.
//  Copyright (c) 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gja;

import com.gideonsoftworks.gja.*;
import com.gideonsoftworks.gcore.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
    An GJobStatus object is used to record the status of an in progress job. There is one GJobStatus for each GJobExecuter. A Job Status can represent a job with 1 Primary and N required job descriptions. 
*/

public class GJobStatus //extends GListenableItem
{
    
    /**
     Job status flags.
    */
    public  static  final   int     STATE_CREATED           = -1;       // default
    public  static  final   int     STATE_QUEUED            = 0;        // added to queue
    public  static  final   int     STATE_PREPARING         = 1;        // being processed - ie handling prerequisite generation etc...
    public  static  final   int     STATE_RUNNING           = 2;        // job is in progress
    public  static  final   int     STATE_STOPPED           = 3;        // job has been stopped
    public  static  final   int     STATE_ERROR             = 4;        // duh
    public  static  final   int     STATE_WAITING_FOR_USER  = 5;        // job has asked user for user input to resolve file namespace collision
    public  static  final   int     STATE_COMPLETE          = 6;        // job is complete
    public  static  final   int     STATE_CANCELLING        = 7;        // job is being cancelled
    public  static  final   int     STATE_SKIPPED           = 8;        // job was skipped ( pre-reqs )
	
    static  final   int     MIN_STATE = STATE_CREATED;
    static  final   int     MAX_STATE = STATE_SKIPPED;
    
    // written by the job worker thread, read by 1s EDT timers (status/activity panels) -
    // volatile for visibility, AtomicLong where the update is a read-modify-write increment
    private GJobDescription             gjsJobDescription=null;
    private volatile GJobDescription    gjsActiveJobDescription=null; // for items with preqs - flags the current item being processed
    private volatile GJobExecuter       gjsJobExecuter=null;
    private volatile GConnectionException gjsException=null;

    // Statistics Recorded by GJA
    private volatile int    gjsState            =   STATE_CREATED;
    private volatile long   gjsStartTime        =   0;
    private volatile long   gjsEndTime          =   0;
    private final AtomicLong gjsTotalSize        =   new AtomicLong(0);
    private final AtomicLong gjsTotalFiles       =   new AtomicLong(0);
    private final AtomicLong gjsTotalFolders     =   new AtomicLong(0);
    private final AtomicLong gjsProcessedFiles   =   new AtomicLong(0);
    private final AtomicLong gjsProcessedFolders =   new AtomicLong(0);
    private volatile long   gjsBytesTransferred =   0;
    
    /**
      Use this method to create a new GSJobStatus.
    */
    public GJobStatus( GJobDescription j )
    {

        if( j == null )
        {
            throw new NullPointerException("GJobStatus->GJobStatus : Attempt to set GJobDescription to null");
        }

        gjsJobDescription = j;
            
    }

    public  GJobDescription getJobDescription()
    {

        return gjsJobDescription;

    }
    
	public boolean isQueued()
	{
		if( gjsState==STATE_QUEUED) return true;
		return false;
	}
	
    public void  setException( final GConnectionException ex )
    {
         gjsException = ex;

    }
    
    public  GConnectionException getException()
    {

        return gjsException;

    }
    
    public void  setJobExecuter( final GJobExecuter ex )
    {
         gjsJobExecuter = ex;

    }
    
    public  GJobExecuter getJobExecuter()
    {

        return gjsJobExecuter;

    }
    
    public void  setActivePrerequisite( final GJobDescription ex )
    {
         gjsActiveJobDescription = ex;

    }
    
    public  GJobDescription getActivePrerequisite()
    {

        return gjsActiveJobDescription;

    }
    
    
    /**
      This method is used to set the job state.
      @param theNewState Specific job state.
    */
    public  void setState( final int theNewState )
    {   

        if( ( theNewState < MIN_STATE ) ||
            ( theNewState > MAX_STATE ) )
        {
            throw new IllegalArgumentException("GJobStatus->setState : invalid state : " + theNewState );
        }

        gjsState = theNewState;

    }

    /**
      Use this method to get the current job state.
      @return returns GSJobState 
    */
    public  int  getState(){ return gjsState; }
    
    public  long getSecondsSinceStart()
    {
        
        return (System.currentTimeMillis() - gjsStartTime)/1000;
    
    }
    
    /**
      Use this method to have the job status object record the start time.
    */
    public  void    recordStartTime()
    {
        gjsStartTime = System.currentTimeMillis();
    }
    
    /**
      Use this method to have the job status object STOP recording time.
    */
    public  void    recordCompletionTime()
    {
        gjsEndTime = System.currentTimeMillis();
    }

    /**
      Total job duration in milliseconds (end - start). Falls back to the current time when
      the job hasn't recorded a completion time yet.
    */
    public  long    getDurationMs()
    {
        long end = ( gjsEndTime > 0 ) ? gjsEndTime : System.currentTimeMillis();
        return ( gjsStartTime > 0 ) ? ( end - gjsStartTime ) : 0;
    }
        
    /**
      Use this method to increment the total size by a certain amount
    */
    public void incrementTotalSizeBy( final long incValue )
    {
        gjsTotalSize.addAndGet(incValue);
    }
    
    /**
      Use this method to get the total file/folder size
      @return total contained file size
    */
    public long getTotalSize()
    {
        return gjsTotalSize.get();
    }
    
    /**
      Use this method to increment the total file count by a certain amount
    */
    public void incrementTotalFilesBy( final long incValue )
    {
        gjsTotalFiles.addAndGet(incValue);
    }
    
    /**
      Use this method to get the total file count
      @return total contained file count
    */
    public long getTotalFileCount()
    {
        return gjsTotalFiles.get();
    }
    
    /**
      Use this method to increment the total folder count by a certain amount
    */
    public void incrementTotalFoldersBy( final long incValue )
    {
        gjsTotalFolders.addAndGet(incValue);
    }
    
    /**
      Use this method to get the total folder count
      @return total contained folder count
    */
    public long getTotalFolderCount()
    {
        return gjsTotalFolders.get();
    }
    
    /**
      Use this method to get the total folder count
      @return total contained folder count
    */
    public long getTotalCount()
    {
        return gjsTotalFolders.get() + gjsTotalFiles.get();
    }
    
    
    
    /**
      Use this method to increment the total size by a certain amount
    */
    public void incrementProcessedFilesBy( final long incValue )
    {
        gjsProcessedFiles.addAndGet(incValue);
    }
    
    /**
      Use this method to get the total folder count
      @return total contained folder count
    */
    public long getProcessedFilesCount()
    {
        return gjsProcessedFiles.get();
    }
    
    /**
      Use this method to increment the total size by a certain amount
    */
    public void incrementProcessedFoldersBy( final long incValue )
    {
        gjsProcessedFolders.addAndGet(incValue);
    }
    
    /**
      Use this method to get the total folder count
      @return total contained folder count
    */
    public long getProcessedFoldersCount()
    {
        return gjsProcessedFolders.get();
    }
    
    /**
      Use this method to get the total folder count
      @return total contained folder count
    */
    public long getTotalProcessedCount()
    {
        return gjsProcessedFolders.get() + gjsProcessedFiles.get();
    }
    
    /**
      This method is used to set how many bytes have been transferred
    */
    public void setBytesTransferred( final long transferredBytes )
    {
    
        gjsBytesTransferred = transferredBytes;
    
    }
    
    /**
      This method is used to get how many bytes have been transferred
    */
    public long getBytesTransferred()
    {
    
        return gjsBytesTransferred;
    
    }
    
	
	/**
      This method is used to easily check a job to see if its running.
      @return returns YES if job is running
    */
    public  boolean isCancelled()
    {

        if( ( this.getState() == STATE_CANCELLING ) )
        {
            return true;
        }

        return false;
        
    }
	
    /**
      This method is used to easily check a job to see if its running.
      @return returns YES if job is running
    */
    public  boolean isRunning()
    {

        if( ( this.getState() == STATE_RUNNING ) ||
            ( this.getState() == STATE_PREPARING ) )
        {
            return true;
        }

        return false;
        
    }

}
