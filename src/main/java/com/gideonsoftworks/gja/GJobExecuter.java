//
//  GJobExecuter.java
//  Gideon
//
//  Created by David on Mon Jul 19 2004.
//  Copyright (c) 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gja;

import java.util.*;
import com.gideonsoftworks.gja.*;
import com.gideonsoftworks.gideon.core.GideonConfiguration;

/**
  An GSJobExecuter object handles the processing of a job description. The job executer instantiates an 
  instance of the required job and executes it. The Job Executer object acts as a proxy between the main thread and the 
  "job" thread.
*/

public class GJobExecuter implements Runnable
{
    private GJobManager     gjeJobManager=null;
    private GJobStatus      gjeJobStatus=null;
    private GJobPlugin      gjeJobPlugin=null;
    
    /**
      Use this method to create a new GJobExecuter. This method also creates a GJobStatus object for the Executer.
      @param js GJobStatus
	  @param jm GJobManager
    */
    public  GJobExecuter(final GJobStatus js, final GJobManager jm)
    {
        setJobStatus(js);
        setJobManager(jm);
    }
    
    /**
      Set the job manager object for this executer.  
      @param   jm   Job Manager in charge of this executer.
    */
    public  void setJobManager(GJobManager jm)
    {  
        if( jm == null )
        {
            throw new NullPointerException("GJobExecuter->setJobStatus : Attempted to add a null GJobManager");
        }
        
        gjeJobManager = jm;
    }

    /**
      Returns the job manger object for this executer.
      @return GJobManager
    */
    public  GJobManager getJobManager()
    {  

        return gjeJobManager;  

    }
    
    /**
      Set the job status object for this executer.
    */
    public  void setJobStatus(GJobStatus js)
    {  
        if( js == null )
        {
            throw new NullPointerException("GJobExecuter->setJobStatus : Attempted to add a null GJobStatus");
        }
        
        gjeJobStatus = js;
    }

    /**
      Returns the job status object for this executer ( Created in initWithJobDescription )
      @return returns GJobStatus
    */
    public  GJobStatus getJobStatus()
    {  

        return gjeJobStatus;  

    }
    
    /**
      Set the job plugin for this executer.
    */
    public  void setJobPlugin(GJobPlugin jp)
    {  
        if( jp == null )
        {
            throw new NullPointerException("GJobExecuter->setJobPlugin : Attempted to add a null GJobPlugin");
        }
        
        gjeJobPlugin = jp;
    }
    
    public  void setJobPluginNONULLCHECK(GJobPlugin jp)
    {  
        gjeJobPlugin = jp;
    }
    
    /**
      Returns the job status object for this executer ( Created in initWithJobDescription )
      @return returns GJobStatus
    */
    public  GJobPlugin getJobPlugin()
    {  

        return gjeJobPlugin;  

    }

    /** The worker thread running the plugin, or null before start()/after completion. */
    public  Thread getThread()
    {
        GJobPlugin p = gjeJobPlugin;
        return ( p != null ) ? p.getThread() : null;
    }

    /**
      This method is called once before start(). At this point the GJobExecuter has to find and load a plugin.
      @return returns true if a plugin was found to process the plugin
    */
    public  boolean    loadPluginForJob()
    {
        
        GJobPlugin  plugin  = null;
        int         jType;
        
        if( GideonConfiguration.DEBUG_GJA ) System.out.println("\tGJobExecuter->loadPluginForJob");
        
        GJobDescription jd = this.getJobStatus().getJobDescription();
        
        if(jd==null)
        {
            throw new NullPointerException("GJobExecuter->loadPluginForJob : Job Description was null");
        }
        
        jType = jd.getJobType();
    
        if(jType == GJobDescription.LOGIN_JOB_TYPE){
            if( GideonConfiguration.DEBUG_GJA ) System.out.println("\t\tGJobExecuter->loadPluginForJob: Loaded Login Plugin");
            plugin = new GLoginJobPlugin(this.getJobStatus());
        }else if(jType == GJobDescription.CHANGE_AND_LIST_JOB_TYPE)
        {
            if( GideonConfiguration.DEBUG_GJA ) System.out.println("\t\tGJobExecuter->loadPluginForJob: Loaded GChangeAndListJobPlugin Plugin");
            plugin = new GChangeAndListJobPlugin(this.getJobStatus());
        }else if(jType == GJobDescription.NEWFOLDER_JOB_TYPE)
        {
            if( GideonConfiguration.DEBUG_GJA ) System.out.println("\t\tGJobExecuter->loadPluginForJob: Loaded GNewFolderJobPlugin Plugin");
            plugin = new GNewFolderJobPlugin(this.getJobStatus());
        }else if(jType == GJobDescription.REFRESH_JOB_TYPE)
        {
            if( GideonConfiguration.DEBUG_GJA ) System.out.println("\t\tGJobExecuter->loadPluginForJob: Loaded GRefreshJobPlugin Plugin");
            plugin = new GRefreshJobPlugin(this.getJobStatus());
        }else if(jType == GJobDescription.DISCONNECT_JOB_TYPE)
        {
            if( GideonConfiguration.DEBUG_GJA ) System.out.println("\t\tGJobExecuter->loadPluginForJob: Loaded GDisconnectJobPlugin Plugin");
            plugin = new GDisconnectJobPlugin(this.getJobStatus());
        }else if(jType == GJobDescription.RENAME_JOB_TYPE)
        {
            if( GideonConfiguration.DEBUG_GJA ) System.out.println("\t\tGJobExecuter->loadPluginForJob: Loaded GRenameJobPlugin Plugin");
            plugin = new GRenameMoveJobPlugin(this.getJobStatus());
        }else if(jType == GJobDescription.DELETE_JOB_TYPE)
        {
            if( GideonConfiguration.DEBUG_GJA ) System.out.println("\t\tGJobExecuter->loadPluginForJob: Loaded GDeleteJobPlugin Plugin");
            plugin = new GDeleteJobPlugin(this.getJobStatus());
        }else if(jType == GJobDescription.CHMOD_JOB_TYPE)
        {
            if( GideonConfiguration.DEBUG_GJA ) System.out.println("\t\tGJobExecuter->loadPluginForJob: Loaded GCHMODJobPlugin Plugin");
            plugin = new GCHMODJobPlugin(this.getJobStatus());
        }else if(jType == GJobDescription.DOWNLOAD_JOB_TYPE)
        {
            if( GideonConfiguration.DEBUG_GJA ) System.out.println("\t\tGJobExecuter->loadPluginForJob: Loaded GDownloadJobPlugin Plugin");
            plugin = new GDownloadJobPlugin(this.getJobStatus());
        }else if(jType == GJobDescription.UPLOAD_JOB_TYPE)
        {
            if( GideonConfiguration.DEBUG_GJA ) System.out.println("\t\tGJobExecuter->loadPluginForJob: Loaded GUploadJobPlugin Plugin");
            plugin = new GUploadJobPlugin(this.getJobStatus());
        }else if(jType == GJobDescription.PING_JOB_TYPE)
        {
            if( GideonConfiguration.DEBUG_GJA ) System.out.println("\t\tGJobExecuter->loadPluginForJob: Loaded GPingJobPlugin Plugin");
            plugin = new GPingJobPlugin(this.getJobStatus());
        }else if(jType == GJobDescription.MTIME_JOB_TYPE)
        {
            if( GideonConfiguration.DEBUG_GJA ) System.out.println("\t\tGJobExecuter->loadPluginForJob: Loaded GMtimeJobPlugin Plugin");
            plugin = new GMtimeJobPlugin(this.getJobStatus());
        }else if(jType == GJobDescription.MIRROR_JOB_TYPE)
        {
            if( GideonConfiguration.DEBUG_GJA ) System.out.println("\t\tGJobExecuter->loadPluginForJob: Loaded GMirrorJobPlugin Plugin");
            plugin = new GMirrorJobPlugin(this.getJobStatus());
        }else{
            return false;
        }
        
        if( GideonConfiguration.DEBUG_GJA ) System.out.println("\tGJobExecuter->loadPluginForJob");
        
        if( plugin != null )
        {
            this.setJobPlugin(plugin);
            return true;
        }

        return false;
        
    }

    /**
      Use this method to stop the job the executer is in charge of
    */
    public  void    stop()
    {

        // capture once - the completion/error path nulls the plugin from another thread
        GJobPlugin p = this.getJobPlugin();
        if( p == null ) return;

        Thread t = p.getThread();
        if( t != null ) t.interrupt();

        if( p.needsForceCancel() )
        {
            p.forceCancellation();
        }

    }

    /**
      Use this method to start the job the executer is in charge of.
    */
    public  void    start()
    {
        if( GideonConfiguration.DEBUG_GJA ) System.out.println("GJobExecuter->start()");
        Thread vThread = Thread.ofVirtual()
            .name("job-" + getJobStatus().getJobDescription().getName())
            .start(getJobPlugin());
        getJobPlugin().setThread(vThread);
    }
    
    /**
      Called in the "event" thread from the GJobPlugin
    */
    public void run()
    {

        GJobStatus  js = getJobStatus();
        int jobState = js.getState();

        if( jobState == GJobStatus.STATE_COMPLETE )
        {
            getJobManager().jobCompleted(this.getJobStatus().getJobDescription());
        }else if( jobState == GJobStatus.STATE_ERROR )
        {
            getJobManager().jobFailed(this.getJobStatus().getJobDescription());
            setJobPluginNONULLCHECK(null);
        }else if( jobState == GJobStatus.STATE_CANCELLING ){
            // signal the job stopped
            getJobManager().jobCancelled(this.getJobStatus().getJobDescription());
            setJobPluginNONULLCHECK(null);
		// a collision happened
        }else if( jobState == GJobStatus.STATE_WAITING_FOR_USER ){
			// signal the job needs input
            getJobManager().jobNeedsInput(getJobStatus().getJobDescription());
        }

    }
    
    /**
      Use this method to restart the job the executer is in charge of.
    */
    public  void restart()
    {

        this.start();

    }

}
