//
//  GRenameMoveJobPlugin.java
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
*   GRenameMoveJobPlugin
*   
*   Source and target 
*       Target File Item is file/folder to be renamed.
*       Source File Item is unused.
*
*   Properties
*       name  - new folder name
*
*
*/

public class GRenameMoveJobPlugin extends GJobPlugin
{
    
    public  GRenameMoveJobPlugin( final  GJobStatus js )
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
        
        GFileItem   targetFileItem = d.getTargetFile();
        
        if( targetFileItem == null )
        {
            this.signalError("GRenameMoveJobPlugin : Target File was null");
            return;
        }
        
        String newName = (String)d.getProperty(GJobDescription.NAME_JOB_PROP);
        
        if( newName == null )
        {
            this.signalError("GRenameMoveJobPlugin : NAME_JOB_PROP property was null");
            return;
        }
        
        c.rename( targetFileItem, newName );
    
    }

}