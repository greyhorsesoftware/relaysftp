//
//  GCancelUIAction.java
//  Gideon
//
//  Created by David on 11/9/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.actions;

import com.gideonsoftworks.gideon.core.GideonConfiguration;
import com.gideonsoftworks.gja.*;
import javax.swing.*;
import java.awt.event.*;

public class GCancelUIAction extends GUIAction implements GJobManagerListenerInterface
{

    private GJobManager cancelJobManager;
    
    public  GCancelUIAction( String name, Icon icon, int code )
    {
        super( name, icon, code );
        setEnabled(false);
    }
    
    public void setJobManager( GJobManager jm )
    {
        cancelJobManager = jm;
        cancelJobManager.addStatusListener(this);
    }
    
    public void actionPerformed( ActionEvent e )
    {
        cancelJobManager.stopKeyJob();
    }
    
    /**
    *
    * job manager call backs
    *
    */
    
    public  void    jm_jobStarted( final GJobManagerEvent e )
    {
            if( GideonConfiguration.CANCELBUTTON ) System.out.println("jm_jobStarted called");
            setEnabled(true);
    }
    
    public  void    jm_jobStopped( final GJobManagerEvent e )
    {
            if( GideonConfiguration.CANCELBUTTON ) System.out.println("jm_jobStopped called");
            setEnabled(false);
    }
    
    public  void    jm_jobCompleted( final GJobManagerEvent e )
    {
            if( GideonConfiguration.CANCELBUTTON ) System.out.println("jm_jobCompleted called");
            setEnabled(false);
    }
    
    public  void    jm_jobCancelling( final GJobManagerEvent e )
    {
        if( GideonConfiguration.CANCELBUTTON ) System.out.println("jm_jobCompleted called");
        setEnabled(false);
    }
    
    public  void    jm_jobFailed( final GJobManagerEvent e )
    {
        if( GideonConfiguration.CANCELBUTTON ) System.out.println("jm_jobFailed called");
        setEnabled(false);
    }
    
    public  void    jm_keyJobChanged( final GJobManagerEvent e ){}
    public  void    jm_jobNeedsInput( final GJobManagerEvent e, final GJobUserMessage m ){}
    public  void    jm_jobHasMessage( final GJobManagerEvent e ){}
    public  void    jm_shuttingDown( final GJobManagerEvent e ){}
    
    /**
    *
    * The cancel item validates itself - ie new jobs running or not.
    *
    */
    public boolean  shouldValidateItem()
    {
    
        return false;
        
    }

}
