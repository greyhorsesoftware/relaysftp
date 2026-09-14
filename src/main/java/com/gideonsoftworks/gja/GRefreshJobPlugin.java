//
//  GRefreshJobPlugin.java
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
*   GRefreshJobPlugin
*   
*   Source and target 
*       Target File Item is parent folder that will be "refreshed"
*       Source File Item is unused
*
*   Properties
*       none
*
*
*/

public class GRefreshJobPlugin extends GJobPlugin
{
    
    public  GRefreshJobPlugin( final  GJobStatus js )
    {
    
        super(js);
    
    }
    
    public void processJobDescription(final GJobDescription d, final GConnection c ) throws GConnectionException
    {
        
        super.processJobDescription(d, c);
        
        GFileItem   targetFileItem = d.getTargetFile();
        
        if( targetFileItem == null )
        {
            this.signalError("GChangeAndListJobPlugin : Target File was null");
        }
        
        c.list(targetFileItem);

    }

}
