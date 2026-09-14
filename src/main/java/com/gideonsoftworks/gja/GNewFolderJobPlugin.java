//
//  GNewFolderJobPlugin.java
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
*   GNewFolderJobPlugin
*   
*   Source and target 
*       Target File Item is parent folder that created folder will be added to.
*       Source File Item is unused
*
*   Properties ( see GJobDescription for description of property )
*       NAME_JOB_PROP
*
*
*/

public class GNewFolderJobPlugin extends GJobPlugin
{
    
    public  GNewFolderJobPlugin( final  GJobStatus js )
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
            this.signalError("GChangeAndListJobPlugin : Target File was null");
        }
        
        String name = (String)d.getProperty(GJobDescription.NAME_JOB_PROP);
        
        if( name == null )
        {
            this.signalError("GRenameMoveJobPlugin : NAME_JOB_PROP property was null");
            return;
        }
        
        c.newfolder(targetFileItem, name );

    }

}