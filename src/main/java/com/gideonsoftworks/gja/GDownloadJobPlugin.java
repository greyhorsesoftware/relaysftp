//
//  GDownloaderJobPlugin.java
//  Gideon
//
//  Created by David on 10/29/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gja;

import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.core.GideonConfiguration;
import com.gideonsoftworks.gja.*;
import com.gideonsoftworks.gcore.*;
import java.io.*;

/**
*
*   GDownloaderJobPlugin
*   
*   Source and target 
*       Target File Item is the file/folder to be downloaded.
*       Source File Item is the destination for the file/folder to be downloaded
*
*   Properties ( see GJobDescription for description of property )
*       TRANSFERMODE_JOB_PROP 
*
*/

public class GDownloadJobPlugin extends GRecursiveJobPlugin 
{   

    private Object  gdjStream = null;
    
    public  GDownloadJobPlugin( final  GJobStatus js )
    {

        super(js);
    
    }
    
    public  void    setOurDataStream( Object dStream )
    {
        
        gdjStream = dStream;
        
    }
    
    public Object   getOurDataStream()
    {

        return gdjStream;

    }

    /** Close the per-file transfer stream (FileInputStream upload / FileOutputStream download) on every path (#1). */
    @Override
    protected void closeDataStream()
    {
        Object s = getOurDataStream();
        if( s instanceof java.io.Closeable )
        {
            try { ((java.io.Closeable) s).close(); }
            catch( java.io.IOException ignore ) {}
        }
        setOurDataStream( null );
    }

    /**
      <democrat mode>We r'urn WAZZUP all the time because we bes needins to done generatins' de 
      source ile tems</democrat mode>
    */
    public boolean  requiresPreprocessing(final GJobDescription d )
    { 
        
        return true;
    
    }
    
    public boolean needsRootPropertyGenerated()
    {
    
        return true;
    
    }
    
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
    public  String  getSeparator(GJobDescription d)
    {
        String sepItem = null;
    
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
        
        return sepItem;
    }

    /**
      We use this method to generate source file items for preqs. This involves calculating the path of source file
      items and creating.
      @param parentJD Primary job description.
      @param newJD Job description describing child of parentJD.
    */
    public  void    didAddPrerequisite( final GJobDescription parentJD, GJobDescription newJD) throws GConnectionException
    {
        GFileItem       nFileItem=null;
        String          sourceFilePath=null;
        // get our root property
        String          rootLocalPath = (String)this.getProperty("root");
    
        if( GideonConfiguration.DLULSourceFileGeneration ) System.out.println( "New Job Hash : " + newJD.hashCode() );
        if( GideonConfiguration.DLULSourceFileGeneration ) System.out.println( "\tRoot Path is : " + rootLocalPath );
        
        String sepItem = getSeparator(newJD);

        GFileItem targetFileItem = newJD.getTargetFile();
        GFileItem rootFileItem = parentJD.getTargetFile();
        GFileItem sourceFileItem = parentJD.getSourceFile();
        
        // we have a specific download location for this file
        if( sourceFileItem != null &&
            sourceFileItem instanceof GTemporaryFileItem )
        {
			if( GideonConfiguration.DLULSourceFileGeneration ) System.out.println( "\tTemporary File Item" );
            sourceFilePath = sourceFileItem.getCompletePath();
        
        // if our target location is a file - our source uploads to exactly that
        }else if( sourceFileItem != null &&
                sourceFileItem.getIsFile() )
        {
		
			if( GideonConfiguration.DLULSourceFileGeneration ) System.out.println( "\tDirect to file upload" );

            sourceFilePath = sourceFileItem.getCompletePath();

        // this first state happens once. The first job description processed replicates the parent job ( targetFileItem==rootFileItem )
        // this is because of the changes I made where items with parent job descriptions have an entry 
        // created for themselves in their preq table. Without this test the else state is executed - as these items are eq. 
        // the root search goes on until / is reached. At that point its parent returns null, then the subsequent
        // call to getName() results in a NullPointerException.
        // case 2 : we are downloading a single file - this case is used.
        }
        // this first state happens once. The first job description processed replicates the parent job ( targetFileItem==rootFileItem )
        // this is because of the changes I made where items with parent job descriptions have an entry 
        // created for themselves in their preq table. Without this test the else state is executed - as these items are eq. 
        // the root search goes on until / is reached. At that point its parent returns null, then the subsequent
        // call to getName() results in a NullPointerException.
        // case 2 : we are downloading a single file - this case is used.
        else if( targetFileItem.equals(rootFileItem) )
        {
			if( GideonConfiguration.DLULSourceFileGeneration ) System.out.println( "\tTarget File Item is root" );
			if( GideonConfiguration.DLULSourceFileGeneration ) System.out.println( "\tRoot File Item :" + rootFileItem.getCompletePath() );
            if( GideonConfiguration.DLULSourceFileGeneration ) System.out.println( "\tTarget File Item :" + targetFileItem.getCompletePath() );
			sourceFilePath = rootLocalPath;

        // XXX this can be simplified - this else if can go away. The else code would 
        // XXX do the same thing.
        }else if( rootFileItem.isParentOf(targetFileItem) )
        {
			if( GideonConfiguration.DLULSourceFileGeneration ) System.out.println( "\trootFileItem is parent" );
			if( GideonConfiguration.DLULSourceFileGeneration ) System.out.println( "\t Root : " + rootFileItem.getCompletePath() );
			if( GideonConfiguration.DLULSourceFileGeneration ) System.out.println( "\t Target : " + targetFileItem.getCompletePath() );

            // item exists in the root - just take the root path and append a separator and the file name
            sourceFilePath = rootLocalPath + sepItem + targetFileItem.getName();
        }else{
			
			if( GideonConfiguration.DLULSourceFileGeneration ) System.out.println( "\tItem does not exist in the root" );
			
            // item DOES NOT exist in the root we have to 
            // walk the parents until we reach the rootFileItem.
            // as we walk the parents we append the parent "names" to the 
            // string then finally the name of the targetfile
            
            StringBuilder    path = new StringBuilder();
            
            GFileItem pFI = targetFileItem.getParentFileItem();
			
            if(pFI == null ) System.out.println( "\tParent File Item is null" );
			
			if( GideonConfiguration.DLULSourceFileGeneration ) System.out.println( "\tParent File Item : " + pFI.getCompletePath() );
			
            while( !(rootFileItem.equals(pFI)) )
            {
                
                
                path.insert( 0, sepItem + pFI.getName() );
                
                
                // get this items parent
                pFI=pFI.getParentFileItem();
                
            }
            
            path.append(sepItem + targetFileItem.getName());
            
            sourceFilePath = rootLocalPath + path.toString();
            
        }
	
		if( GideonConfiguration.DLULSourceFileGeneration ) System.out.println( "\tTarget File Name : " + targetFileItem.getName() );

        // generate a source file item for this job description
        nFileItem = new GFileItem( targetFileItem.getName(), sourceFilePath, parentJD.getConnectionToUse() );
        nFileItem.setFileType(targetFileItem.getFileType());
        
        newJD.setSourceFile(nFileItem);
        
		if( GideonConfiguration.DLULSourceFileGeneration ) System.out.println( "\tSource File Path : " + nFileItem.getCompletePath() );
		if( GideonConfiguration.DLULSourceFileGeneration ) System.out.println( "\tTarget File Path : " + targetFileItem.getCompletePath() );
		
        if( GideonConfiguration.DLULSourceFileGeneration ) System.out.println( "End willAddPreq() : " + newJD.hashCode() );
        
    }
    
    /**
      Use this method to begin processing the job description. Note : preflight checks should be done first.
      @param d Job Desc we are processing
	  @param c GConnection to use
	  @param tFI top file item
    */
    public boolean recursiveProcessJobDescription(final GJobDescription d, final GConnection c, final GFileItem tFI ) throws GConnectionException
    {
        FileOutputStream    fOutputStream = null;
        File                srcFile=null;
        GFileItem           sFI=d.getSourceFile();
        int                 flag;
                
        // filter items...
        super.filter( d, tFI);
                
        System.out.println("Target File Item : " + tFI.getName() );
            
        if( sFI == null )
        { 
            this.signalError("Source file item was null");
            return true;
        }else{
            if( sFI.getIsDir() )
            {   
                System.out.println("Creating folder ( " + tFI.getName() + " ) : " + sFI.getCompletePath() );
                File    f = new File(sFI.getCompletePath());
                // if the folder does not exist - create it, otherwise just move on
				if( !f.exists() ) f.mkdir();
                return true;
            }
        }

        System.out.println("Downloading : " + tFI.getCompletePath() );
        System.out.println("to : " + sFI.getCompletePath() );

        srcFile = new File(sFI.getCompletePath());

        // if the file exists - auto-rename with numeric suffix
        if( srcFile.exists() )
        {
            String basePath = sFI.getCompletePath();
            String nameWithoutExt;
            String ext;
            int dotIndex = basePath.lastIndexOf('.');
            int sepIndex = Math.max(basePath.lastIndexOf('/'), basePath.lastIndexOf('\\'));
            if( dotIndex > sepIndex + 1 ) {
                nameWithoutExt = basePath.substring(0, dotIndex);
                ext = basePath.substring(dotIndex);
            } else {
                nameWithoutExt = basePath;
                ext = "";
            }

            int counter = 1;
            while( srcFile.exists() ) {
                srcFile = new File(nameWithoutExt + " (" + counter + ")" + ext);
                counter++;
            }

            // update the source file item to reflect the new path
            sFI = new com.gideonsoftworks.gcore.GFileItem(
                srcFile.getName(), srcFile.getAbsolutePath(),
                d.getConnectionToUse());
            sFI.setFileType(d.getSourceFile().getFileType());
            d.setSourceFile(sFI);
        }
        
        // open an input stream pointing to the source file item
        try{
            // create an empty file
            if(!srcFile.createNewFile())
            {
                this.signalError("GDownloadJobPlugin : Could not create file :" + sFI.getCompletePath() );
                return true;
            }
        }catch(IOException e )
        {
            this.signalError("GDownloadJobPlugin :" + e.toString() );
            return true;
        }
        
        // open an input stream to the file
        try{
            fOutputStream = new FileOutputStream(srcFile);
        }catch(FileNotFoundException e )
        {
            this.signalError("GDownloadJobPlugin :" + e.toString() );
            return true;
        }
        
        this.setOurDataStream(fOutputStream); 

        Integer tMode = (Integer)this.getProperty(GJobDescription.TRANSFERMODE_JOB_PROP);
        if( tMode==null )
        {
            flag = 0;
        }else{
            flag = tMode.intValue();
        }
        // let the connection do what it has to do - setup transfer modes etc...
        c.prepareToTransferFile(tFI, flag);

        // tell the plugin to start
        try {
            c.download(tFI.getCompletePath(), fOutputStream, srcFile, this);
        } catch( GConnectionException e ) {
            // Log the error but continue with remaining files
            c.getLog().logMessage(new GLogMessage(GLogMessage.GLOG_ERROR,
                "Skipping " + tFI.getName() + ": " + e.getMessage()));
            try { fOutputStream.close(); } catch( IOException ignore ) {}
            srcFile.delete();
            d.getJobStatus().setState(GJobStatus.STATE_SKIPPED);
        }

        return true;

    }
    

    protected boolean handleCollision( final GFileItem fileThatExists, final GJobDescription d )
    {

        GFileItem	tFI = d.getTargetFile();
        GFileItem	sFI = d.getSourceFile();

        // check to see if we are ignoring collisions
        Boolean tMode = (Boolean)getProperty(GJobDescription.IGNORE_COLLISIONS_PROP);
        if( tMode!=null )
        {
                if( tMode.booleanValue() == true )
                {
                        if( GideonConfiguration.JOBCOLLISIONDEBUG ) System.out.println("Ignoring file collisions " + tFI.getCompletePath() );
                        return true;
                }
        }

        // get our current job user message
        GJobUserMessage jum = getJobUserMessage();

        // if we have a current job user message see if it applies to all
        // jobs. if it doesn't - query the user for their input.
        if( jum == null ||
                !jum.getApplysToAll() )
        {
                jum = signalCollision( tFI, fileThatExists );
        }

        if( jum.isCancellation() )
        {
                changeJobStatusState(GJobStatus.STATE_CANCELLING);
                return false;
        }

        if( jum.isSkip() )
        {
                d.getJobStatus().setState(GJobStatus.STATE_SKIPPED);
                return false;
        }

        return true;

    }

    public  void    jobCompleted(final GJobDescription d)
    {
 
    }
    
    public boolean needsForceCancel()
    {

        return true;

    }

    /**
      Used by subclasses to signal a user requested cancellation - called from main thread
    */
    public void forceCancellation()
    {
        
        GConnection c = this.getJobConnection();
        c.cancelTransfer();

    }
    
    public void cleanupAfterCancellation()
    {
    
        FileOutputStream stream = (FileOutputStream)this.getOurDataStream();
        
		if( stream != null )
		{
			try{
				stream.close();
			}catch(IOException e )
			{
				// ignore it - we don't care
			}
        }
		
        if( this.getJobConnection().requiresLoginAfterCancellation() )
        {
            // re login
            try{
                getJobConnection().login();
            }catch(GConnectionException e)
            {
            }
        }
        
    }
    
}
