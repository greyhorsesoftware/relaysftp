//
//  GRecursiveJobPlugin.java
//  Gideon
//
//  Created by David on 10/28/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gja;

import com.gideonsoftworks.gideon.core.GideonConfiguration;
import  com.gideonsoftworks.gcore.*;
import	com.gideonsoftworks.gja.*;

public class GRecursiveJobPlugin extends GJobPlugin 
{

    public  GRecursiveJobPlugin( final  GJobStatus js )
    {
    
        super(js);
    
    }
    
    /**
      We override this method to handle deciding if a job is recursive. It's simple - its a folder - preprocess.
      @param d Job Desc we are processing.
    */
    public boolean  requiresPreprocessing(final GJobDescription d )
    { 
        
        GFileItem   targetFileItem = d.getTargetFile();
        
        // if we have no  target item - ignore the request.
        if( targetFileItem == null )
        {
            return false;
        }

        if( targetFileItem.getIsDir() ) return true;
        
        return false;
        
    }
    
    /**
      We override this method to handle accounting for recursive jobs.
      @param d Job Desc we are processing.
      @param c Connection to use.
    */
    public void processJobDescription(final GJobDescription d, final GConnection c ) throws GConnectionException
    {
        
        
        super.processJobDescription(d, c);

        GFileItem   targetFileItem = d.getTargetFile();
        
        if( targetFileItem == null )
        {
            signalError("GDeleteJobPlugin : Target File was null");
            return;
        }
        
        if( targetFileItem.getIsDir() )
        {
            getJobStatus().incrementProcessedFoldersBy(1);
        }else{
            getJobStatus().incrementProcessedFilesBy(1);
        }
        
        recursiveProcessJobDescription( d, c, targetFileItem );
  
    }
    
    public boolean recursiveProcessJobDescription(final GJobDescription d, final GConnection c, final GFileItem targetFileItem ) throws GConnectionException
    {

        return true;
		
    }
    
    // called from subclassers that want to take advantage of the transfer filtering functionality
    public void filter( final GJobDescription d, final GFileItem targetFileItem ) throws GConnectionException
    {
        // if this is a download/upload we check to make sure it isn't "filtered"
        if( d.isTransferJob() )
        {
                if( GideonConfiguration.FILTERINGDEBUG ) System.out.println("Applying filters...");
                // handle skipping for downloads/uploads
                if( GTransferFilterManager.matchesFilter(targetFileItem) )
                {
                        if( GideonConfiguration.FILTERINGDEBUG ) System.out.println("Skipping : " + targetFileItem.getCompletePath() );
                        d.getJobStatus().setState(GJobStatus.STATE_SKIPPED);
                }
        }
    }
    
}
