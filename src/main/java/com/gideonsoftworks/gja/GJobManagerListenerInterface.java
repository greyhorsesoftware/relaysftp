//
//  GJobManagerListenerInterface.java
//  Gideon
//
//  Created by David on Mon Jul 19 2004.
//  Copyright (c) 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gja;

import com.gideonsoftworks.gja.*;
import com.gideonsoftworks.gcore.*;

public interface GJobManagerListenerInterface 
{

public  void    jm_keyJobChanged( final GJobManagerEvent e );
public  void    jm_shuttingDown( final GJobManagerEvent e );
public  void    jm_jobCancelling( final GJobManagerEvent e );
public  void    jm_jobStarted( final GJobManagerEvent e );
public  void    jm_jobStopped( final GJobManagerEvent e );
public  void    jm_jobFailed( final GJobManagerEvent e );
public  void    jm_jobCompleted( final GJobManagerEvent e );
public  void    jm_jobNeedsInput( final GJobManagerEvent e, final GJobUserMessage m );

// not implemented
public  void    jm_jobHasMessage( final GJobManagerEvent e );


}