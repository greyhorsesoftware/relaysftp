//
//  GJobPlugin.java
//  Gideon
//
//  Created by David on Wed Jul 21 2004.
//  Copyright (c) 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gja;

import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.core.GideonConfiguration;
import	com.gideonsoftworks.gcore.*;
import	com.gideonsoftworks.gja.*;
import	java.util.*;
import	javax.swing.*;
import	java.lang.reflect.*;

/**
	GJobPlugin is the superclass of all job plugins. It acts as a controller - managing status, job properties, job descriptions.
*/
public class GJobPlugin implements Runnable, GTransferProgressListener
{
    private volatile Thread	gjpThread=null;
    private HashMap			gjpProperties=null; // Properties copied from primary job description
    private HashMap			gjpUserInput=null;
    private GJobStatus		gjpJobStatus=null;
    private boolean			gjpCreatedConnection=false;
    private GConnection		gjpConnection=null;
    private volatile GJobUserMessage	gjpLastUserMessage=null;

    void setThread(Thread t) { gjpThread = t; }
    Thread getThread() { return gjpThread; }
	
	
/**
  Use this constructor to create a new job plugin instance. If supplied FS is null job should/will create new connection.
*/
public GJobPlugin( final GJobStatus js )
{

    if( js == null )
    {
        throw new NullPointerException("GJobPlugin : Attempted to add a null GJobStatus");
    }

    gjpJobStatus=js;
    
}

/**
	Return message from job plugin to user levels so user can act on job. Job User messages are things like
	collisions etc...
	@return GJobUserMessage	Message that contains question
*/
public GJobUserMessage  getJobUserMessage()
{

    return gjpLastUserMessage;

}

/**
	Return message current job status object associated with job plugin.
	@return GJobStatus
*/
public GJobStatus  getJobStatus()
{

    return gjpJobStatus;

}

/**
	Return root job description associated with job plugin.
	@return GJobDescription
*/
public GJobDescription  getJobDescription()
{

    return gjpJobStatus.getJobDescription();

}

/**
      Since GJobPlugin is a subclass of Thread. This method is run when start() is called on this class.
*/
/**
 * Hook for subclasses that open a per-file data stream (upload/download): close it here. Called from a
 * finally in {@link #run()} so the stream is released on every path — success, exception and cancel (#1).
 */
protected void closeDataStream() {}

public void run()
{
    GConnection preprocessingJobConnection = null;

    try{
        
        this.getJobStatus().recordStartTime();
        
        if( GideonConfiguration.DEBUG_GJA ) System.out.println("GJobPlugin->run()");
        
        GJobDescription jd = getJobDescription();
        
        
        // assign the properties to the job plugin - see the description below 
        setProperties(jd.getProperties());
        
        // if there is no connection - create one
        if( jd.isTransferJob() )
        {
        
            if( GideonConfiguration.DEBUG_GJA ) System.out.println("GJobPlugin->run() : Creating connection for transfer job");
        
            // create a new connection
            GConnection myConnection = new GConnection(new GBookmark(jd.getConnectionToUse().getBookmark()));
            setJobConnection(myConnection);
            setCreatedConnection(true);
            
            // otherwise utilize the one passed in
        }else{
            // record this for later
            setJobConnection(jd.getConnectionToUse());
            setCreatedConnection(false);
        }
        
        if( GideonConfiguration.DEBUG_GJA ) System.out.println("GJobPlugin->run() : Testing for connection.");

        // see if the connection is up
        if( !getJobConnection().getIsConnected() )
        {
            if( GideonConfiguration.DEBUG_GJA ) System.out.println("GJobPlugin->run() : not connected - Logging in.");
            
            try{
				// login
                getJobConnection().login();
            }catch(GConnectionException e)
            {
                this.signalException(e);
                return;
            }
        }
			
										        
		// generate the current working directory
		Boolean		cMode	= (Boolean)jd.getProperty(GJobDescription.GENERATE_ROOTS_PROP);
		GFileItem	cwd		= getJobConnection().getCurrentWorkingDirectory();
		
                if( GideonConfiguration.DEBUG_GJA )
                {
		}
                
		// if we are to generate the path AND the cwd is null - do it.
		if( cMode != null &&
			cwd == null &&
			cMode.booleanValue() )
		{
			// this job requires the remote roots are generated
			doPostLoginPathWork(jd, getJobConnection());
		}
			
		if( GideonConfiguration.DEBUG_GJA ) System.out.println("GJobPlugin->run() : Testing to see if preprocessing required");
        
        if( requiresPreprocessing(jd) )
        {
            if( GideonConfiguration.DEBUG_GJA ) System.out.println("GJobPlugin->run() : preprocessing required");
            
            changeJobStatusState(GJobStatus.STATE_PREPARING);
              
            try{
				            
                // for the upload job we pass in a nice sexy FILE connection,
                // this is so preprocessing can enumerate the local file system
                if( jd.isUploadJob())
                {
                    GBookmark   lBM = new GBookmark("local", "/", null, null, 0, GBookmark.G_FILE_PROTOCOL );
                    preprocessingJobConnection = new GConnection(new GBookmark(lBM));
                    preprocessingJobConnection.login();
					// generate default working folder
					doPostLoginPathWork(jd, preprocessingJobConnection);
				}else{
                    preprocessingJobConnection = getJobConnection();
                }

                preProcessJobDescription(jd, preprocessingJobConnection);

                if( GideonConfiguration.JOB_PLUGIN_PREPROCESSING_DEBUG ) this.enumerateJobDescriptions(jd);

                // Check for unreadable files in download jobs
                if( jd.isDownloadJob() && jd.getHasPrerequisites() )
                {
                    List<String> unreadable = new ArrayList<>();
                    List<GJobDescription> unreadablePrereqs = new ArrayList<>();
                    for( int i = 0; i < jd.getPrerequisiteCount(); i++ )
                    {
                        GJobDescription prereq = jd.getPrerequisite(i);
                        GFileItem f = prereq.getTargetFile();
                        if( f != null && !f.getIsDir() && f.getPermissions() != null )
                        {
                            if( (f.getPermissions().getBitField() & GPermissions.OWNER_READ) == 0 )
                            {
                                unreadable.add(f.getCompletePath()
                                    + "  (" + f.getPermissions().getNumericValueAsString() + ")");
                                unreadablePrereqs.add(prereq);
                            }
                        }
                    }

                    if( !unreadable.isEmpty() )
                    {
                        GJobUserMessage response = signalUnreadableFiles(unreadable);

                        if( response.isCancellation() )
                        {
                            signalCancellation();
                            return;
                        }

                        // User chose to skip — remove unreadable prereqs
                        for( GJobDescription r : unreadablePrereqs )
                        {
                            GFileItem f = r.getTargetFile();
                            getJobConnection().getLog().logMessage(new GLogMessage(
                                GLogMessage.GLOG_INFORMATIONAL,
                                "Skipping unreadable: " + f.getCompletePath()));
                            jd.removePrerequisite(r);
                        }

                    }
                }

            }catch( GConnectionException e )
            {
                this.signalException(e);
                return;
            }
            
        }

        if( Thread.currentThread().isInterrupted() )
        {
            this.signalCancellation();
            return;
        }
        
        if( GideonConfiguration.DEBUG_GJA ) System.out.println("GJobPlugin->run() : Processing Job Description");

        // After preprocessing, verify the connection is still alive.
        // Long preprocessing (many LISTs) can cause server-side timeouts.
        if( getCreatedConnection() )
        {
            boolean alive = false;
            try {
                getJobConnection().ping();
                alive = true;
            } catch( Exception e ) {
                // Connection is dead
            }

            if( !alive )
            {
                try {
                    getJobConnection().getLog().logMessage(
                        new GLogMessage(GLogMessage.GLOG_INFORMATIONAL, "Connection lost after preprocessing — reconnecting..."));
                    getJobConnection().login();
                } catch( GConnectionException e ) {
                    signalException(e);
                    return;
                }
            }
        }

        this.changeJobStatusState(GJobStatus.STATE_RUNNING);

        if( jd.getHasPrerequisites() )
        {
            
            // deleters only at this time
            // you delete the deepest item first then work your way out.
            if( this.processDeeperItemsFirst() )
            {

                int pCount = jd.getPrerequisiteCount();
                pCount--;
 
                
                for ( int i = pCount ; i >= 0 ; i-- )
                {
                    
                    GJobDescription childJD = jd.getPrerequisite(i);
                    
                    if( childJD == null )
                    {
                        signalError("Child was null");
                        return;
                    }
                    
                    if( Thread.currentThread().isInterrupted() )
                    {
                        signalCancellation();
                        return;
                    }
                    
                    getJobStatus().setActivePrerequisite(childJD);
                    processJobDescription( childJD, getJobConnection() );
                    
					// user cancelled then return
					if( getJobStatus().isCancelled() )
					{
						System.out.println("Catching user cancellation.");
						signalCancellation();
						return;
					}
					
                }
                
                // allow the plugins to do something neat at the end
                jobCompleted(jd);
                
                /**
                *
                * CHANGED : 11/02/04 - The day Bush was elected - WOOHOO
                * target item is processed as a prereq. ie it is contained in the 
                * prereq listing. 
                *
                */

            // everything else - process items as they are created
            }else{

                /**
                *
                * CHANGED : 11/02/04 - The day Bush was elected - WOOHOO
                * target item is processed as a prereq. ie it is contained in the 
                * prereq listing. 
                *
                */
                
                int pCount = jd.getPrerequisiteCount();
                
                
                for ( int i = 0 ; i < pCount ; i++ )
                {
                    
                    GJobDescription childJD = jd.getPrerequisite(i);
                    
                    if( childJD == null )
                    {
                        this.signalError("Child was null");
                        return;
                    }
                    
                    if( Thread.currentThread().isInterrupted() )
                    {
                        this.signalCancellation();
                        return;
                    }
                    
                    this.getJobStatus().setActivePrerequisite(childJD);
                    this.processJobDescription( childJD, getJobConnection() );
					
					// user cancelled then return
					if( getJobStatus().isCancelled() )
					{
						System.out.println("Catching user cancellation.");
						signalCancellation();
						return;
					}
					
                }
                
                // allow the plugins to do something neat at the end
                this.jobCompleted(jd);
            
            }

        }else{
            getJobStatus().setActivePrerequisite(null);
            processJobDescription( jd, getJobConnection() );
            jobCompleted(jd);
        }
       
    }catch( GConnectionException e )
    {
        if( Thread.currentThread().isInterrupted() )
        {
            signalCancellation();
            return;
        }else{
            signalException(e);
            return;
        }
    }catch( Exception e )
    {
        // A cancel can surface here as a RuntimeException rather than a GConnectionException —
        // e.g. an S3 transfer future cancelled by gpp_cancelTransfer() throws CancellationException
        // out of join(). Report it as a cancellation, not a generic error (#13).
        if( Thread.currentThread().isInterrupted() || e instanceof java.util.concurrent.CancellationException )
        {
            signalCancellation();
            return;
        }
        e.printStackTrace();
        signalError(e.getMessage());
        return;

    }finally
    {
        closeDataStream();   // release the per-file transfer stream on every path (#1)
    }


    // disconnect from the host if we created the connection
    if(getCreatedConnection())
    {
        try{
            getJobConnection().logout();
        }catch( GConnectionException e )
        {
            this.signalException(e);
            return;
        }
    }
    
    if( GideonConfiguration.DEBUG_GJA ) System.out.println("GJobPlugin->run() : Finished Processing Job Description");
    
    this.getJobStatus().recordCompletionTime();
    
    this.changeJobStatusState(GJobStatus.STATE_COMPLETE);
    
    // dipatch the callbacks on the event thread.
    try{
        SwingUtilities.invokeLater( this.getJobStatus().getJobExecuter() );
    }catch(Exception e)
    {
    }
    
}

/**
      Helper that enumerates a passed in job description.
      @param jd Job Desc we are processing
*/
private void    enumerateJobDescriptions( GJobDescription jd )
{

    GFileItem   tf=null;
    GFileItem   sf=null;
    
    System.out.println("Job Named : " + jd.getName());
    System.out.println("Code : " + jd.hashCode());
    
    if( ( tf = jd.getTargetFile() ) != null )
    {
        System.out.println("Target File Name : " + tf.getName());
        System.out.println("Target File Path : " + tf.getCompletePath());
    }
    
    if( ( sf = jd.getSourceFile() ) != null )
    {
        System.out.println("\t\tSource File Name : " + sf.getName());
        System.out.println("\t\tSource File Path : " + sf.getCompletePath());
    }else{
        System.out.println("\t\tNo Source File");
    }

    if( jd.getHasPrerequisites() )
    {
        
        int pc = jd.getPrerequisiteCount();
        
        System.out.println("Job has prerequsites : " + pc + " total.");
        
        for( int i = 0 ; i < pc ; i++ )
        {
            
            GJobDescription pjd = jd.getPrerequisite(i);
            
            System.out.println("\tJob Named : " + pjd.getName());
            System.out.println("\tObject ID : " + pjd.hashCode());
            
            if( ( tf = pjd.getTargetFile() ) != null )
            {
                System.out.println("\t\tTarget File Name : " + tf.getName());
                System.out.println("\t\tTarget File Path : " + tf.getCompletePath());
            }
            
            if( ( sf = pjd.getSourceFile() ) != null )
            {
                System.out.println("\t\tSource File Name : " + sf.getName());
                System.out.println("\t\tSource File Path : " + sf.getCompletePath());
            }else{
                System.out.println("\t\tNo Source File");
            }
            
        }
        
    }
    
    System.out.println("Done for Job Named : " + jd.getName());
    
}
/**
* Determines the order in which prereqs are processed
* ex. the deleter plugin returns yes to this so it processes deeper items first
*/
public boolean processDeeperItemsFirst()
{

    return false;

}

/*
* Determines if the GJobPlugin will generate and record the "root" path in the "root" plugin property.
*/
public boolean needsRootPropertyGenerated()
{

    return false;

}

public void changeJobStatusState( final int newJobState )
{

    gjpJobStatus.setState(newJobState);

}

/**
  Returns YES if the job in question requires pre processing. Preflight is the process of enumerating folders etc...
  @param d Job description to test.
  @return YES if needs to preflight.
*/	
public boolean  requiresPreprocessing(final GJobDescription d )
{ 
    
    return false;
    
}

/**
  Returns YES if the job in question requires pre processing. Preflight is the process of enumerating folders etc...
  @param d Job description to test.
  @param c GConnection to use.
*/	
public  void    preProcessJobDescription( final GJobDescription d, final GConnection c ) throws GConnectionException
{
    GJobStatus  js=null;
    GFileItem   sourceFileItem=null;
    GFileItem   targetFileItem = d.getTargetFile();
    String      rootPath=null;
    String      targetPath = null;
    
	
    // if we have no  target item - ignore the request.
    if( targetFileItem == null )
    {
         this.signalError("Null target file");
         return;
    }
    
    sourceFileItem = d.getSourceFile();
	
    System.out.println("Is Transfer Job : " + d.isTransferJob() );
    
    if( sourceFileItem == null &&
            d.isTransferJob() )
    {
        System.out.println("Source file item is becoming current working directory");
        // use the root as the "source"
        sourceFileItem = c.getCurrentWorkingDirectory();
    }
    
    /*
    if( sourceFileItem == null )
    {
        throw new NullPointerException("GJobPlugin::preProcessJobDescription - source file item was null");
    }
    */
    
    // get the job status object for recording progress.
    js = this.getJobStatus();

    // generate the root file location and assign as a property to the job plugin
    if( needsRootPropertyGenerated() ) 
    {
        String  sepItem = null;
        
        targetPath = sourceFileItem.getCompletePath();

        String targetFileName = targetFileItem.getName();
        
        /*
        *   Here we decide what separator item to use for source item path generation. This is
        *   kind of messy and can probably be done a better way. 
        *
        *   Download jobs require the sep be the same as the native platform.
        *       UNIX, Mac OS X use "/" as the separator
        *       Winblows uses "\" as the separator
        *   
        *   Upload Jobs require the sep to be "/" - as all the servers we support use "/"
        */
        
        if( d.isDownloadJob() )
        {
            
            if( GLocalized.onWindows )
            {
                sepItem = "\\";
            }else{ 
                sepItem = "/";
            }
            
        }else if( d.isUploadJob() )
        {
            sepItem = "/";
        }
        
        /*
        *
        *  END Separator Item calculations
        *
        */
        
        
		System.out.println("Target File Item : " + sourceFileItem.getCompletePath() );
		
		// if our target location is a folder - generate a new root path
		if( sourceFileItem.getIsDir() )
		{
		
			System.out.println("Source File Item is folder");
		
			// handle case where user is copying to root ( ie we don't need a / )
			if( sourceFileItem.getIsRoot() )
			{
				System.out.println("Target File Item is root" );
				rootPath = targetPath+targetFileName;
			}else{
				System.out.println("Target File Item is NOT root" );
				rootPath = targetPath + sepItem + targetFileName;
			}
		// if its a file - then we generate a file name to upload to
        }else{
			rootPath = sourceFileItem.getCompletePath();
		}
		 
        addProperty("root", rootPath );
		
		System.out.println("Root Path is : " + rootPath );
		
        /*
        // create a file item representing the reote/local "root" folder ie downloading "~/WEb Site" - this is "<localpath>/WebSite"
        GFileItem nFileItem = new GFileItem( targetFileItem.getName(), rootPath, c );
        nFileItem.setFileType(GFileItem.G_FOLDER);
        d.setSourceFile(nFileItem);
        */
    }
    
    // the "root" item is added and processed as if it was a 
    // child item.
    GJobDescription newJob = new GJobDescription(d.getJobType(), c, null );
    newJob.setTargetFile(targetFileItem);
    // allow the job plugin to do some work on the job desc
    d.addPrerequisite( newJob );
    this.didAddPrerequisite(d, newJob);

    // if was a file - we bes done.
    if( targetFileItem.getIsFile() ||
        targetFileItem.getIsLink() )
    {
        return;
    }
    
    js.incrementTotalFoldersBy(1);

    // Try fast recursive listing first (single round-trip via SSH exec)
    long listStartTime = System.currentTimeMillis();
    boolean usedFastList = false;
    try {
        usedFastList = c.fastListRecursive(targetFileItem);
    } catch (GConnectionException e) {
        // Warp-list failed — fall back silently
    }

    this.processFolder( targetFileItem, d, d.getJobType(), c, js );

    long totalElapsed = System.currentTimeMillis() - listStartTime;
    c.getLog().logMessage(new GLogMessage(GLogMessage.GLOG_INFORMATIONAL,
        "Preprocessing " + targetFileItem.getCompletePath() + " complete: " + js.getTotalFileCount() + " files, "
        + js.getTotalFolderCount() + " folders, "
        + formatBytes(js.getTotalSize())
        + " \u2014 " + totalElapsed + "ms"
        + (usedFastList ? " (warp)" : " (standard)")));

    if( GideonConfiguration.JOB_PLUGIN_PREPROCESSING_DEBUG ) System.out.println("Item Total Size : " + js.getTotalSize() );
    if( GideonConfiguration.JOB_PLUGIN_PREPROCESSING_DEBUG ) System.out.println("Item Total File Count : " + js.getTotalFileCount() );
    if( GideonConfiguration.JOB_PLUGIN_PREPROCESSING_DEBUG ) System.out.println("Item Total Folder Count : " + js.getTotalFolderCount() );
     
}

private void   processFolder( final GFileItem targetFileItem, final GJobDescription pJD, final int jType, final GConnection c, final GJobStatus js ) throws GConnectionException
{
    String      cName=null;

    // if we have no  target item - ignore the request.
    if( targetFileItem == null )
    {
         this.signalError("Null target file");
    }
    
    if( !targetFileItem.hasBeenListed() )
    {
        // do the initial listing
        c.list( targetFileItem );
    }
    
    if( GideonConfiguration.JOB_PLUGIN_PREPROCESSING_DEBUG ) System.out.println("Folder name : " + targetFileItem.getName() );
    if( GideonConfiguration.JOB_PLUGIN_PREPROCESSING_DEBUG ) System.out.println("\tItem count in folder : " + targetFileItem.getChildCount() );
    
    // now process each item
    // create a new job description
    // list it if necessary
    for( int i = 0 ; i < targetFileItem.getChildCount() ; i++ )
    {   
    
        if( Thread.currentThread().isInterrupted() )
        {
            this.signalCancellation();
            return;
        }
    
        GFileItem currChild = targetFileItem.getChild(i);

        if( currChild == null )
        {
            this.signalError("Received null child " + i ); 
            return;       
        }
        
        cName = currChild.getName();
        
        if( GideonConfiguration.JOB_PLUGIN_PREPROCESSING_DEBUG ) System.out.println("\tProcessing child (" + i + ") : " + cName );
        
        // ignore this and parent entries
        if( ( cName.compareTo("..") == 0 ) ||
            ( cName.compareTo(".") == 0 ) )
        {
            if( GideonConfiguration.JOB_PLUGIN_PREPROCESSING_DEBUG ) System.out.println("\tSkipping child named \""+ cName + "\"" );
            continue;
        }
        
        if( currChild.getIsDir() )
        {
            
            // add an entry for the parent folder itself
            GJobDescription newJob = new GJobDescription(jType, c, null );
            newJob.setTargetFile(currChild);
            pJD.addPrerequisite( newJob );
         
            // allow the job plugin to do some work on the job desc
            this.didAddPrerequisite(pJD, newJob);
                  
            if( GideonConfiguration.JOB_PLUGIN_PREPROCESSING_DEBUG ) System.out.println("\tChild is folder");
            this.processFolder( currChild, pJD, jType, c, js );

            // some stats
            js.incrementTotalFoldersBy(1);
        
        }else{

            if( GideonConfiguration.JOB_PLUGIN_PREPROCESSING_DEBUG ) System.out.println("\tChild is file/link creating new job prereq.");
            // create a list and cwd job description
            GJobDescription cJob = new GJobDescription(jType, c, null );
            cJob.setTargetFile(currChild);
            pJD.addPrerequisite( cJob );

            // allow the job plugin to do some work on the job desc
            this.didAddPrerequisite(pJD, cJob);

            // some stats
            js.incrementTotalFilesBy(1);
            js.incrementTotalSizeBy(currChild.getRawSize());
            
        }
        
    }

}

/**
*
*
* Transfer callback
*
*
*/
public void bytesTransferred( long count )
{

    GJobStatus  so = this.getJobStatus();
    
    if( so == null )
    {
        this.signalError("Status Object was null in bytesTransferred");
        return;
    }
    
    so.setBytesTransferred(count);

}

/**
* 
* The primary job descriptions properties are recorded by the job. This is so any "prereqs" have
* access to any "global" data - such as a permission value etc...
*
*/

public void setProperties( HashMap properties )
{
    
    gjpProperties = properties;

}

public void addProperty( Object key, Object value )
{
    
    gjpProperties.put( key, value );

}

public void removeProperty( Object key )
{

    gjpProperties.remove( key );

}

public Object getProperty( Object key )
{

    return gjpProperties.get( key );

}

// did we create the connection or use another ???
public boolean getCreatedConnection()
{

    return gjpCreatedConnection;

}

public void setCreatedConnection( boolean cConnection )
{
    
    gjpCreatedConnection = cConnection;

}

private static String formatBytes(long bytes) {
    if (bytes >= 1073741824) return String.format("%.1f GB", bytes / 1073741824.0);
    if (bytes >= 1048576) return String.format("%.1f MB", bytes / 1048576.0);
    if (bytes >= 1024) return String.format("%.0f KB", bytes / 1024.0);
    return bytes + " bytes";
}

public void setJobConnection( GConnection jConnection )
{
    gjpConnection = jConnection;
}

public GConnection getJobConnection()
{
    return gjpConnection;
}
    
/*
* called by the plugin to inform us of transfer stats
*/
public void updateTransferStats( final long transferred )
{
    getJobStatus().setBytesTransferred(transferred);  
}

/*
*
* Methods for subclasses to override
*
*/

/**
  Process the job description.
  @param d Job description to test.
  @param c GConnection to use.
*/	
public  void    processJobDescription(final GJobDescription d, final GConnection c ) throws GConnectionException
{
    
	// if we have been disconnected - reconnect
    if( !c.getIsConnected() )
    {
		System.out.println("Calling login on connection");
        c.login();
    }

}

/**
*
* This method does two things :
*
*	1. Generates "roots" or My Computer item
*	2. Generates initial current working directory file item tree and changes to lowest level.
*
*/

protected void doPostLoginPathWork( final GJobDescription d, final GConnection c ) throws GConnectionException
{

        boolean     isLocal = false;
        String[]    sParts = null;
        
        if( GideonConfiguration.LOGINPATHGENERATIONDEBUG )
        {
            System.out.println("Entered doPostLoginPathWork");
        }
    
        Integer cMode = (Integer)d.getProperty(GJobDescription.CONNECT_MODE_PROP);
        
        if( cMode != null )
        {
            c.setConnectMode(cMode.intValue());
        }

        String defaultPath = (String)d.getProperty(GJobDescription.DEFAULT_FOLDER_JOB_PROP);

        /*
        * Generate 
        */
        if( defaultPath == null )
        {
            defaultPath = c.pathForWorkingDirectory();
        }else{
            /*  handle case where user specified folder is not absolute.
                in this situation we append it to the pathForWorkingDirectory()
                returned from the connection
                NOTE : we aren't worried about windows etc... here because
                this code should NEVER be executed for a local connection. */
            if( c.getIsRemote() && 
                !defaultPath.startsWith("/") )
            {
                defaultPath = c.pathForWorkingDirectory() + "/" + defaultPath;
            }
        }

        GFileItem   cFileItem=null;
        // break the path into components
        
        String  pSep = c.getSeparator();
        
        // check to see if we are under windows
        if( pSep.compareTo("\\") == 0 )
        {
            sParts = defaultPath.split("\\\\");
        }else{
            sParts = defaultPath.split(pSep);
        }

        if( GideonConfiguration.LOGINPATHGENERATIONDEBUG )
        {
            System.out.println("\tDefault Path : " + defaultPath);
            System.out.println("\tPath Components : ");
            for( int i = 1 ; i < Array.getLength(sParts) ; i++ )
            {
                String  cItem = (String)Array.get(sParts, i);
                System.out.println("\t\tPath Part : " + cItem);
            }
            System.out.println("\tEND Path Components");
        }

        /*
        * Make sure the file items know their type.
        */
        int bmType = c.getBookmark().getProtocol();

        /*
        * Generate file item hierarchy
        */

        // my computer contains all the "roots" ie A:\, C:\ etc...
        // this seems to be broken under mac os x - all that returns is / - bad apple !
        GFileItem myComputer = c.getMyComputerFileItem();
        
        if( myComputer == null )
        {
            throw new NullPointerException("GJobPlugin::doPostLoginPathWork() - myComputer is null");
        }

        // from the myComputer file item we want to get the root for the default path
        GFileItem   rFileItem = myComputer.getRootFileItemForPath(defaultPath);
    
        if( rFileItem == null )
        {
            throw new NullPointerException("GJobPlugin::doPostLoginPathWork() - getRootFileItemForPath() failed for path \"" + defaultPath + "\"");
        }
        
        // based on our root file item we make the files below local/remote file items
        isLocal = rFileItem.getIsFileLocal();
        
        if( GideonConfiguration.LOGINPATHGENERATIONDEBUG )
        {
            System.out.println("\tDefault Path : " + defaultPath );
            System.out.println("\tRemote File Item for Path : " + rFileItem.getCompletePath() );
        }
        
        // only process the path if the default path does not match the root
        // root complete path. this comparison is case insensitive as the user may 
        // type the path all lower case.
        if( defaultPath.compareToIgnoreCase(rFileItem.getCompletePath()) != 0 )
        {
            int iCount = Array.getLength(sParts);
            
            GFileItem cParentItem = rFileItem;
            
            for( int i = 1 ; i < iCount ; i++ )
            {
                String  itemPath    =   null;
                String  pPath       =   null;
                String  rPath       =   null;
                String  cItem       =   (String)Array.get(sParts, i);
                
                pPath = cParentItem.getCompletePath();
                rPath = rFileItem.getCompletePath();
                
                if( GideonConfiguration.LOGINPATHGENERATIONDEBUG )
                {
                    System.out.println("\tProcessing Part : " + cItem );
                    System.out.println("\t\tParent File ID : \"" + cParentItem.hashCode() + "\" parent name : \"" + cParentItem.getName() + "\"" );
                    System.out.println("\t\tParent Path : " + pPath );
                    System.out.println("\t\tFile Item Path : " + rPath );
                }
                
                // check and see if the current parent path
                // matches the remote file item path - if it does 
                // we just append our item name to the parent items path.
                // this applies if the root - is a forward slash and the first part is "/"
                // that is why there is not separator here...
                if( pPath.compareToIgnoreCase(rPath) == 0 )
                {
                    itemPath = cParentItem.getCompletePath() + cItem;
                // ...if that does not apply we just append the item with a 
                // separator to create the items path
                }else{
                    itemPath = cParentItem.getCompletePath() + c.getSeparator() + cItem;
                }
                
                if( GideonConfiguration.LOGINPATHGENERATIONDEBUG ) 
                {
                    System.out.println("\t\tGenerated Item Path : " + itemPath );
                }
                
                // create a new item with the new path
                cFileItem = new GFileItem( cItem, itemPath, c );
                cFileItem.setFileIsLocal(isLocal);
                
                if( GideonConfiguration.LOGINPATHGENERATIONDEBUG ) 
                {
                    System.out.println("\t\tGenerated Item Path : " + itemPath );
                }
                
                // tag any folders that are named Desktop as a desktop object
                // they'll get a special icon in the ui
                if( isLocal )
                {
                    if( cItem.compareToIgnoreCase("Desktop") == 0 )
                    {
                        cFileItem.setIsDesktop(true);
                    }
                }
                
                // set some stuff
                cFileItem.setFileType(GFileItem.G_FOLDER);
                cFileItem.setParentFileItem(cParentItem);
                
                if( GideonConfiguration.LOGINPATHGENERATIONDEBUG ) 
                {
                    System.out.println("\t\tGenerated File Item ID : \"" + cFileItem.hashCode() + "\" parent name : \"" + cParentItem.getName() + "\" parent id: \"" + cParentItem.hashCode() + "\"" );
                }
                
                // set placeholder ???
                cParentItem.setPlaceHolder(cFileItem);
                
                // increment the cParentItem
                cParentItem = cFileItem;
                
                if( GideonConfiguration.LOGINPATHGENERATIONDEBUG ) System.out.println("\tDone Processing Part : " + cItem );
            }
            
            // for mac os x, unix, etc... we don't want to show the My Computer item.
            if( !GLocalized.onWindows )
            {
                rFileItem.setParentFileItem(null);
            }else{
                // we are on windows - if the item is a remote dir - don't show the "My Computer" item
                if( !rFileItem.getIsFileLocal() )
                {
                    rFileItem.setParentFileItem(null);
                }
            }

        }else{
        
            // for mac os x, unix, etc... we don't want to show the My Computer item.
            if( !GLocalized.onWindows && !isLocal )
            {
                rFileItem.setParentFileItem(null);
            }
        
            cFileItem = rFileItem;
        }
        
        if( GideonConfiguration.LOGINPATHGENERATIONDEBUG ) System.out.println("\tchanging to and listing : " + cFileItem.getCompletePath());
        
        c.changeWorkingDirectoryAndList(cFileItem);
        
        if( GideonConfiguration.LOGINPATHGENERATIONDEBUG )
        {
        }
        
}

/**
* Allows subclasses to further process prereq. job descriptions after the actual work is done.
* @param d Root job description.
*/
public  void    jobCompleted(final GJobDescription d)
{


}

/**
* allows subclasses to further process prereq. job descriptions - the GDownload/Upload plugins use this to generate Source file items.
* @param parentJD Orignating job description.
* @param newJD Job Description to modify.
*/

public  void    didAddPrerequisite( final GJobDescription parentJD, GJobDescription newJD) throws GConnectionException{}


/*
*
* End Methods for subclasses to override
*
*/

/*
*
* Completion/Input Request methods
*
*/

public boolean needsForceCancel()
{

    return false;

}

/**
  This method is called if needsForceCancel() returns true. Called in event thread.
*/
public void forceCancellation()
{


}

/**
  This method is called once the job has been "cancelled". Called in job thread.
*/
public void cleanupAfterCancellation()
{


}

/**
  Used by subclasses to signal a user requested cancellation - called from main thread
*/
public void signalCancellation()
{
    System.out.println("GJobPlugin -> signalCancellation");

    // log the cancellation
    if( getJobConnection() != null && getJobConnection().getLog() != null )
    {
        getJobConnection().getLog().logMessage(
            new GLogMessage(GLogMessage.GLOG_INFORMATIONAL, "Job cancelled"));
    }

    // change the state of this job to errored
    this.changeJobStatusState(GJobStatus.STATE_CANCELLING);
    
    // disconnect from the host if we created the connection - if possible
    if(this.getCreatedConnection())
    {
        try{
            this.getJobConnection().logout();
        }catch( GConnectionException e )
        {
            // ignore any errors here - we are fucked anyway.
        }
    }

    // give the executer control back in the main app thread (skipped when the app is quitting:
    // the EDT is inside the quit sequence and everything is being torn down anyway)
    if( !GAppShutdown.isShuttingDown() ) handOffToExecuter();
    
    if( this.needsForceCancel() )
    {
        this.cleanupAfterCancellation();
    }
    
    System.out.println("GJobPlugin -> nulling out properties and status");

    // null some things out.    
    gjpProperties = null;

}

/**
  Used by subclasses to signal a custom error, see above consequences.
  @param source Source file.
  @param dest Destination file item
*/
// NOT synchronized: this blocks in invokeAndWait below - holding the plugin monitor
// across that wait is a latent deadlock. gjpLastUserMessage is volatile instead.
public GJobUserMessage signalCollision( final GFileItem source, final GFileItem dest )
{
	String	actionTitleString=null;
	String	actionFileString=null;
	
	// create an action string
	switch( getJobDescription().getJobType() )
	{
			case GJobDescription.DOWNLOAD_JOB_TYPE:
                actionTitleString = GLocalized.DOWNLOAD_FE;
				actionFileString = GLocalized.DOWNLOADED;
            break;
            
            case GJobDescription.UPLOAD_JOB_TYPE:
                actionTitleString = GLocalized.UPLOAD_FE;
				actionFileString = GLocalized.UPLOADED;
            break;

            case GJobDescription.MOVE_JOB_TYPE:
				actionTitleString = GLocalized.MOVE;
				actionFileString = GLocalized.MOVED;
            break;
			
			default:
				actionTitleString = "!UHOH!";
				actionFileString = "!UHOH!";
			break;
	}
	
	System.out.println("Source : " + source.getCompletePath() );
	System.out.println("Destination : " + dest.getCompletePath() );
	
	// save it in class var - JM will fetch it
	gjpLastUserMessage = new GJobUserMessage( source, dest, actionTitleString, actionFileString );
	
	if( gjpLastUserMessage==null )
	{
		throw new NullPointerException("GJobPlugin : signalCollision() - collision is null");
	}
	
	changeJobStatusState(GJobStatus.STATE_WAITING_FOR_USER);

	// give the job executer control in the main thread; a Stop pressed while the dialog is up,
	// or an app quit, comes back as "cancelled" instead of resuming the transfer (#13, #19)
	if( !handOffToExecuter() )
	{
		gjpLastUserMessage.setAction( GJobUserMessage.CANCEL_ACTION );
		return gjpLastUserMessage;
	}
	// Restore state to running now that the user has responded
	changeJobStatusState(GJobStatus.STATE_RUNNING);

	System.out.println("Continuing...");

	return gjpLastUserMessage;
	
}


/**
*
* Error Stuff
*           - Job State Changed to STATE_ERROR
*           - if this plugin created a connection - the connection is told to disconnect and is released
*           - control is given over to the main thread
*           - some stuff is nulled out
*           - thread exits.
*/

/**
  Used by subclasses to signal a custom error, see above consequences.
  @param e Custom error string.
*/
public void signalError( final String e )
{

    this.signalException( new GConnectionException(e, GConnectionException.GCE_PROTOCOL_ERROR ) );
    
}


/**
  Used by subclasses to signal a custom error, see above consequences.
  @param e Connection Exception
*/
public void signalException( final GConnectionException e )
{

    // change the state of this job to errored
    changeJobStatusState(GJobStatus.STATE_ERROR);

    System.err.println("JOB ERROR: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
    e.printStackTrace(System.err);

    // log the error
    if( getJobConnection() != null && getJobConnection().getLog() != null )
    {
        String msg = "ERROR: " + (e.getMessage() != null ? e.getMessage() : "Unknown error");
        getJobConnection().getLog().logMessage(
            new GLogMessage(GLogMessage.GLOG_ERROR, msg));
    }

    // record the exception for later perusal
    getJobStatus().setException(e);

    // disconnect from the host if we created the connection and it's still connected
    if(getCreatedConnection() && getJobConnection() != null)
    {
        try{
            if( getJobConnection().getIsConnected() ) {
                getJobConnection().logout();
            }
        }catch( GConnectionException oe )
        {
            // ignore
        }
    }

    // give the executer control back in the main app thread (skipped when the app is quitting)
    if( !GAppShutdown.isShuttingDown() ) handOffToExecuter();
    
    // null some things out.
    gjpProperties = null;

}

/**
 * Signal that unreadable files were found during preprocessing.
 * Uses the same invokeAndWait/jobNeedsInput path as signalCollision.
 */
public GJobUserMessage signalUnreadableFiles( List<String> problemPaths )
{
    gjpLastUserMessage = new GJobUserMessage( GJobUserMessage.TYPE_UNREADABLE, problemPaths );

    changeJobStatusState(GJobStatus.STATE_WAITING_FOR_USER);

    if( !handOffToExecuter() )
    {
        gjpLastUserMessage.setAction( GJobUserMessage.CANCEL_ACTION );   // see signalCollision (#13, #19)
        return gjpLastUserMessage;
    }
    changeJobStatusState(GJobStatus.STATE_PREPARING);

    return gjpLastUserMessage;
}

/**
 * Hand control to the job executer on the EDT and wait for it to come back (the executer notifies
 * the job manager; for a collision/unreadable-files message that is where the dialog runs).
 * @return false when the wait did not happen or did not complete: the app is quitting (the EDT is
 *         inside the quit sequence — waiting on it would deadlock), or this worker was interrupted
 *         while waiting (Stop). In both cases the job is marked cancelling and the interrupt flag is
 *         left set so run()'s next check ends the job.
 */
private boolean handOffToExecuter()
{
    if( GAppShutdown.isShuttingDown() )
    {
        changeJobStatusState(GJobStatus.STATE_CANCELLING);
        Thread.currentThread().interrupt();
        return false;
    }
    try
    {
        SwingUtilities.invokeAndWait( getJobStatus().getJobExecuter() );
        return true;
    }
    catch( InterruptedException e )
    {
        Thread.currentThread().interrupt();
        changeJobStatusState(GJobStatus.STATE_CANCELLING);
        return false;
    }
    catch( InvocationTargetException e )
    {
        GAuditLog.error( "", "job hand-off to EDT", null, e.getCause() != null ? e.getCause() : e );
        return false;
    }
}

}
