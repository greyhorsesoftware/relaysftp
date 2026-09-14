//
//  GLog.java
//  Gideon
//
//  Created by David on Mon Jul 19 2004.
//  Copyright (c) 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gcore;

import  com.gideonsoftworks.gcore.*;
import  com.gideonsoftworks.gdo.*;
import  java.util.*;
import  java.util.concurrent.BlockingQueue;

public class GLog extends GDataArray
{

private String      glName = null;
private int         lastMax  = 0;
private volatile BlockingQueue<String> messageQueue = null;

public void setMessageQueue(BlockingQueue<String> q) { messageQueue = q; }

/**
  Create a log without a specified name.
*/
public  GLog()
{
    this("Gideon Log");
}

/**
  Create a log with a specified name.
*/
public  GLog( final String logName )
{
    super();

    if( logName == null )
    {
        throw new NullPointerException();
    }

    glName = logName;
}

/**
  Log a message to the log.
  @return msg  Corresponding message
*/
public String getName()
{
    return glName;
}

/**
  Log a message to the log.
  @param msg Message to log.
*/
public void logMessage( final GLogMessage msg )
{
    if( msg == null )
    {
        throw new NullPointerException("GLog - passed in message was null");
    }

    String line = null;
    BlockingQueue<String> q = messageQueue;

    synchronized( this )
    {
        addItem( msg );

        if( q != null )
        {
            int index = getItemCount() - 1;
            StringBuilder sb = new StringBuilder(msg.getMessageText());
            if( msg.isServerReply() )
            {
                sb.insert(0, "   ");
            } else {
                int arrowIdx = sb.indexOf("--->");
                if( arrowIdx != -1 ) sb.replace(0, 5, "");
            }
            // Line numbers provided by RSyntaxTextArea gutter
            sb.append("\n");
            line = sb.toString();
        }
    }

    // Offer to queue outside the lock — non-blocking
    if( line != null && q != null )
    {
        q.offer(line);
    }

    // Tee warnings/errors to the persistent audit log (text is already redacted).
    int type = msg.getMessageType();
    if( type == GLogMessage.GLOG_ERROR || type == GLogMessage.GLOG_WARNING )
    {
        GAuditLog.logged( type, glName, msg.getMessageText() );
    }

}

synchronized public String getNewMessageText()
{

    StringBuilder    nText = new StringBuilder();

    for( int i = lastMax ; i < getItemCount() ; i++ )
    {
        GLogMessage msg = (GLogMessage)getItemAtIndex(i);
        StringBuilder tString =  new StringBuilder(msg.getMessageText());

        if( msg.isServerReply() )
        {
            tString = tString.insert(0, "   ");
        }else{
            if( tString.indexOf("--->") != -1 )
            {
                tString = tString.replace(0, 5, "");
            }
        }

        // Line numbers provided by RSyntaxTextArea gutter
        tString.append("\n");

        nText.append(tString.toString());

    }

    lastMax = getItemCount();

    return nText.toString();

}

}
