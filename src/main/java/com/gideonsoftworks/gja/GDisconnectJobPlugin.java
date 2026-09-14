//
//  GDisconnectJobPlugin.java
//  Gideon
//
//  Created by David on 10/26/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gja;

import com.gideonsoftworks.gcore.*;
import com.gideonsoftworks.gja.*;
import java.util.*;

/**
*
*   GDisconnectJobPlugin
*   
*   Source and target 
*       Target File Item is unused
*       Source File Item is unused
*
*   Properties
*       none
*
*
*/

public class GDisconnectJobPlugin extends GJobPlugin
{
    
    public  GDisconnectJobPlugin( final  GJobStatus js )
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
        
        c.logout();

    }

}