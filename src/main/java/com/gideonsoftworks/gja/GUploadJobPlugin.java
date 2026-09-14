//
//  GUploadJobPlugin.java
//
//  Created by David on 10/29/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gja;

import com.gideonsoftworks.gja.*;
import com.gideonsoftworks.gcore.*;
import java.io.*;

/**
*
*   GUploadJobPlugin
*   
*   Source and target 
*       Target File Item is the file/folder to be uploaded.
*       Source File Item is the destination for the file/folder to be uploaded
*
*   Properties ( see GJobDescription for description of property )
*       TRANSFERMODE_JOB_PROP
*
*
*   This inherits from GDownloadPlugin as it uses that classes willAddPrereq code.
*
*
*
*/

public class GUploadJobPlugin extends GDownloadJobPlugin 
{

    public  GUploadJobPlugin( final  GJobStatus js )
    {
    
        super(js);
    
    }
    
    /**
      Use this method to begin processing the job description. Note : preflight checks should be done first.
	  @param d Job Desc we are processing
	  @param c GConnection to use
	  @param tFI top file item
    */
    public boolean recursiveProcessJobDescription(final GJobDescription d, final GConnection c, final GFileItem tFI ) throws GConnectionException
    {
        FileInputStream     fInputStream = null;
        File                srcFile=null;
        GFileItem           sFI=d.getSourceFile();
        int                 flag;
        GFileItem			fileThatExists=null;
        
        // filter items...
        super.filter( d, tFI);
        
        System.out.println("Source File Item : " + sFI.getCompletePath() );
        System.out.println("Target File Item : " + tFI.getCompletePath() );
            
        if( sFI == null )
        { 
            this.signalError("Source file item was null");
            return false;
        }else{
            if( sFI.getIsDir() )
            {   
                // create a folder in our source ( destination ) folder
				
                System.out.println("Testing for presence of folder.");
                GFileItem	peFile = c.exists(sFI);
                
                if( peFile == null )
                {
                        System.out.println("Folder does not exist creating.");
                        c.newfolder(sFI);
                }

                return true;
            }
        }

        System.out.println("Upload : " + tFI.getCompletePath() );
        System.out.println("to : " + sFI.getCompletePath() );
        
        /*
        *
        * Test for presence for remote file
        *
        */
		
        fileThatExists = c.exists(sFI);
	
        // if the file exists - handle the Collision
        if( fileThatExists != null )
        {
			
                System.out.println("File that exists : " + fileThatExists.getCompletePath() );
                
                // returns false if the user "cancelled" or "skipped"
                if(!handleCollision(fileThatExists, d))
                {
                        return true;
                }
        }

        /*
        *
        * Open the target file item. and create a File object.
        *
        */
        srcFile = new File(tFI.getCompletePath());
        
        if( srcFile == null )
        {
            signalError("GUploadPlugin : Could not open : " + tFI.getCompletePath() );
            return true;
        }
        
        /*
        *
        * Get input stream from local file.
        *
        */
        try{
            fInputStream = new FileInputStream(srcFile);
        }catch(FileNotFoundException e )
        {
            signalError("GUploadPlugin :" + e.toString() );
            return true;
        }
        
        /*
        *
        * Record it in case we are cancelled
        *
        */
        setOurDataStream(fInputStream);
        
        /*
        *
        * Handle configuring transfer mode.
        *
        */
        Integer tMode = (Integer)this.getProperty(GJobDescription.TRANSFERMODE_JOB_PROP);
        if( tMode==null )
        {
            flag = 0;
        }else{
            flag = tMode.intValue();
        }
        c.prepareToTransferFile(sFI, flag);
        
        
        /*
        *
        * Do It.
        *
        */
        c.upload(sFI.getCompletePath(), fInputStream, srcFile, this);
	
		return true;
	
    }   
    
		
    public  void    jobCompleted(final GJobDescription d)
    {
        
        

    }
    
    /**
     * Stop on an upload must abort the in-flight transfer, exactly as GDownloadJobPlugin does:
     * an S3 multipart upload (or a large SFTP put) otherwise runs to completion after the
     * interrupt, because the transfer itself never checks the flag. Wires the previously
     * unused cancellationRequested() into GJobExecuter.stop()'s force-cancel hook (#13).
     */
    public boolean needsForceCancel()
    {
        return true;
    }

    /** Called in the event thread by GJobExecuter.stop() when needsForceCancel() is true. */
    public void forceCancellation()
    {
        cancellationRequested( this.getJobConnection() );
    }

    public void cancellationRequested( GConnection c )
    {   
        
        if( c==null ) return;
        
        c.cancelTransfer();
        
        FileInputStream stream = (FileInputStream)this.getOurDataStream();
        
        try{
            stream.close();
        }catch(IOException e )
        {
            // ignore it - we don't care
        }
        
    }
    
}

