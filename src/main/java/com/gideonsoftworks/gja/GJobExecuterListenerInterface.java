//
//  GJobExecuterListenerInterface.java
//  Gideon
//
//  Created by David on 10/28/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gja;

public interface GJobExecuterListenerInterface 
{
    
// job has completed - job state is JOB_COMPLETED ; task has been finished.
public  void    jobCompleted( GJobDescription j );
    
}
