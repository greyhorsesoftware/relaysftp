//
//  GQueueEvent.java
//  Gideon
//
//  Created by David on 10/20/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gja;

import java.util.*;
import java.lang.*;
import java.lang.ref.WeakReference;
import com.gideonsoftworks.gcore.*;

public class GQueueEvent
{
            
        // Status codes
        static  final   int     STATUS_QUEUE_JOB_NOOP           = -1;
        static  final   int     STATUS_QUEUE_CONTENTS_CHANGED   = 0;
    
        private int             gqeStatusCode=STATUS_QUEUE_JOB_NOOP;
        private WeakReference   gqeObject=null;
        private WeakReference   gqeQueue=null;
        
        public  GQueueEvent( int status, GQueue queue, GJobDescription jd ) 
        {
            gqeStatusCode = status;
            gqeQueue = new WeakReference(queue);
            if( jd != null ) 
            {
                gqeObject = new WeakReference(jd);
            }else{
                gqeObject = null;
            }
        }
        
        public GJobDescription getJobDescription() {
            
            if( gqeObject == null ) return null;
        
            return (GJobDescription)gqeObject.get();
        }
        
        public int getStatusCode() {
            return gqeStatusCode;
        }
        
}

