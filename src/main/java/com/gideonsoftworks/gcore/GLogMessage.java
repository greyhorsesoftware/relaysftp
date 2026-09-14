//
//  GLogMessage.java
//  Gideon
//
//  Created by David on Wed Jul 21 2004.
//  Copyright (c) 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gcore;

import com.gideonsoftworks.gdo.*;
import java.util.*;

/**
   An GLogMessage is used to encapsulate a server/client message pair or information message.
*/

public class GLogMessage extends GAbstractDataItem
{
public static  final   int GLOG_SERVER_REPLY = 0;
public static  final   int GLOG_CLIENT_COMMAND = 1;
public static  final   int GLOG_WARNING = 2;
public static  final   int GLOG_INFORMATIONAL = 3;
public static  final   int GLOG_DEBUG = 4;
public static  final   int GLOG_ERROR = 5;

public  static  final   String GLOG_MESSAGE_KEY = "Message";
public  static  final   String GLOG_TYPE_KEY    = "Type";

private ArrayList   keyNames;

/**
  Use this method to create a new queue with the contents of specified queue file.
  @param tt One of the GLOG types. 
  @param msg Text message to log
*/
public GLogMessage( final int tt, final String msg )
{
        
    super();
    
    if( msg == null )
    {
        throw new NullPointerException( "GLogMessage : msg was null" );
    }
    
    if( msg.length() == 0 )
    {
        throw new IllegalArgumentException( "GLogMessage : msg was 0 length" );
    }
    
    // Sanitize credentials (FTP PASS, keys, tokens, …) via the shared redactor.
    setObjectForKey( GLOG_MESSAGE_KEY, GRedact.apply(msg) );
    setObjectForKey( GLOG_TYPE_KEY, Integer.valueOf(tt) );
        
    keyNames = new ArrayList();
    keyNames.add(GLogMessage.GLOG_TYPE_KEY);
    keyNames.add(GLogMessage.GLOG_MESSAGE_KEY);
        
}

/**
*
* Return mad key names
*
*/ 
public ArrayList getKeyNames()
{
    return keyNames;
}

public  String  getMessageText()
{

    return ((String)getObjectForKey(GLOG_MESSAGE_KEY));

}

public  int  getMessageType()
{

    return ((Integer)getObjectForKey(GLOG_TYPE_KEY)).intValue();

}

/*
*
* Pretty little helpers
*
*/

public  boolean isServerReply()
{
    if( getMessageType() == GLOG_SERVER_REPLY ) return true;
    return false;
}

/**
  Use this method to get the reply code.
  @return Reply code from server response.
*/
public  int    getReplyCode()
{
    Integer     nInt;
    String      rString;
    
    // if this isn't a server reply - narf
    if( !isServerReply() ) return 0;
    
    rString = getMessageText();

    nInt = Integer.valueOf(rString.substring(0,2));

    return nInt.intValue();
    
}

}
