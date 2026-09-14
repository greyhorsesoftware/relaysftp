//
//  GAbstractJobManagerListener.java
//  GSFTP
//
//  Created by David on 10/23/05.
//  Copyright 2005 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gja;

public class GAbstractJobManagerListener implements GJobManagerListenerInterface
{

public  void    jm_keyJobChanged( final GJobManagerEvent e )
{
}

public  void    jm_shuttingDown( final GJobManagerEvent e )
{
}

public  void    jm_jobCancelling( final GJobManagerEvent e )
{
}

public  void    jm_jobStarted( final GJobManagerEvent e )
{
}

public  void    jm_jobStopped( final GJobManagerEvent e )
{
}

public  void    jm_jobFailed( final GJobManagerEvent e )
{
}

public  void    jm_jobCompleted( final GJobManagerEvent e )
{
}

public  void    jm_jobNeedsInput( final GJobManagerEvent e, final GJobUserMessage m )
{
}

public  void    jm_jobHasMessage( final GJobManagerEvent e )
{
}

}
