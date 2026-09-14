//
//  GPingJobPlugin.java
//  GSFTP
//
//  Created by David on 11/24/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gja;

import com.gideonsoftworks.gcore.*;
import com.gideonsoftworks.gja.*;
import java.util.*;
import java.lang.reflect.*;

/**
*
*   GPingJobPlugin
*   
*   Source and target 
*       Target File Item is not used
*       Source File Item is not used
*
*   Properties
*       none
*
*
*/

public class GPingJobPlugin extends GJobPlugin
{
    
    public  GPingJobPlugin( final  GJobStatus js )
    {
        super(js);
    }
    
    /**
      Use this method to begin processing the job description. Note : preflight checks should be done first.
	  @param d Job Desc we are processing
	  @param c GConnection to use
    */
    public void processJobDescription(final GJobDescription d, final GConnection c ) throws GConnectionException
    {
        super.processJobDescription(d, c);
        c.ping();
    }

}