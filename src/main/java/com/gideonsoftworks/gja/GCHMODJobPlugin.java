//
//  GCHMODJobPlugin.java
//  Gideon
//
//  Created by David on 10/28/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gja;

import com.gideonsoftworks.gcore.*;
import com.gideonsoftworks.gja.*;
import java.util.*;

/**
*
*   GCHMODJobPlugin
*   
*   Source and target 
*       Target File Item is the file/folder to be chmodded.
*       Source File Item is unused
*
*   Properties ( see GJobDescription for description of property )
*       PERMISSIONS_JOB_PROP
*       RECURSUVE_JOB_PROP
*
*
*/

public class GCHMODJobPlugin extends GRecursiveJobPlugin
{
    
    public  GCHMODJobPlugin( final  GJobStatus js )
    {
    
        super(js);
    
    }
    
    /**
      CHMOD is different in that the user has to specify they want recursion.
      @param d Job Desc we are processing
    */
    public boolean  requiresPreprocessing(final GJobDescription d )
    { 
        
        Boolean isRecursive = (Boolean)d.getProperty(GJobDescription.RECURSIVE_JOB_PROP);
        
        if( isRecursive == null )
        {
            this.signalError("GCHMODJobPlugin : \"recursive\" property was null");
            return false;
        }

        return isRecursive.booleanValue();
        
    }
    
    /**
      Use this method to begin processing the job description. Note : preflight checks should be done first.
      @param d Job Desc we are processing
	  @param c connection to use
	  @param tFI top file item ?
    */
    public boolean recursiveProcessJobDescription(final GJobDescription d, final GConnection c, final GFileItem tFI ) throws GConnectionException
    {
        
		// call the super class and return if we get a false
		if(!super.recursiveProcessJobDescription( d, c, tFI ))
		{
			return false;
		}
		
        GPermissions newPerms = (GPermissions)this.getProperty(GJobDescription.PERMISSIONS_JOB_PROP);
        
        if( newPerms == null )
        {
            this.signalError("GCHMODJobPlugin : \"permissions\" property was null");
            return true;
        }


        c.chmod( tFI, newPerms );
		
		return true;
		
    }   

}