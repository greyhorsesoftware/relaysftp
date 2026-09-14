//
//  GChangeAndListJobPlugin.java
//  Gideon
//
//  Created by David on 10/23/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gja;

import com.gideonsoftworks.gcore.*;
import com.gideonsoftworks.gja.*;
import java.util.*;

/**
*
*   GChangeAndListJobPlugin
*   
*   Source and target 
*       Target File Item is folder we are cwd to a listing
*       Source File Item is unused
*
*   Properties
*       none
*
*
*/

public class GChangeAndListJobPlugin extends GJobPlugin
{
    
    public  GChangeAndListJobPlugin( final  GJobStatus js )
    {
    
        super(js);
    
    }
    
    /**
      Use this method to begin processing the job description. Note : preflight checks should be done first.
      @param d Job Desc we are processing
	  @param c Connection to use
    */
    public void processJobDescription(final GJobDescription d, final GConnection c ) throws GConnectionException
    {
        
        super.processJobDescription(d, c);
        
        GFileItem   targetFileItem = d.getTargetFile();
        
        if( targetFileItem == null )
        {
            this.signalError("GChangeAndListJobPlugin : Target File was null");
        }
        
        c.changeWorkingDirectoryAndList(targetFileItem);

    }

}