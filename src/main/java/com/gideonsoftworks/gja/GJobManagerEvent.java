//
//  GJobManagerEvent.java
//  Gideon
//
//  Created by David on 10/21/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gja;

import java.util.*;
import java.lang.*;
import java.lang.ref.WeakReference;
import com.gideonsoftworks.gcore.*;

public class GJobManagerEvent {
            
        // Status codes
        static  final   int     JM_NONE                     = -1;
        static  final   int     JM_KEY_JOB_CHANGED          = 0; // job created, plugin loaded 
        static  final   int     JM_JOB_STARTED              = 1; // job begins running
        static  final   int     JM_JOB_STOPPED              = 2; // job has stopped running ie job plugin thread no longer exists
                                                                 // this can be an error state or simply the user cancelled
        static  final   int     JM_JOB_NEEDS_INPUT          = 3; // job has a question for the (l)user
        static  final   int     JM_JOB_HAS_MESSAGE          = 4; // used for welcome messages etc...
        static  final   int     JM_JOB_COMPLETED            = 5; // job has stopped and has completed successfully
        static  final   int     JM_JOB_BEING_CANCELLED      = 6; // job is in the process of being cancelled
        static  final   int     JM_JOB_FAILED               = 7; // job has failed
        static  final   int     JM_SHUTTING_DOWN            = 8; // job manager shutting down
        
        private int             gjmStatusCode=JM_NONE;
        private WeakReference   gjmObject=null;
        //private GJobDescription   gjmObject=null;
        
        public  GJobManagerEvent( int status, GJobDescription jd )
        {
        
            gjmStatusCode = status;
            
            if( jd != null ) 
            {
                gjmObject = new WeakReference(jd);
            }else{
                gjmObject = null;
            }
            
        }

        public GJobDescription getJobDescription() {
        
            if( gjmObject == null ) return null;
        
            return (GJobDescription)gjmObject.get();
            
        }
        
        public int getStatusCode() {
            return gjmStatusCode;
        }
        
}
