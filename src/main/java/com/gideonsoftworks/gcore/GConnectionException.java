//
//  GConnectionException.java
//  Gideon
//
//  Created by David on 9/17/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gcore;

import com.gideonsoftworks.gideon.core.GLocalized;
import  com.gideonsoftworks.gcore.*;
import  java.text.ParseException;

public class GConnectionException extends Exception
{   

    public  static  final   int     GCE_CONNECTION_LOST = 0;
    public  static  final   int     GCE_OPERATION_NOT_SUPPORTED = 1;
    public  static  final   int     GCE_PROTOCOL_ERROR = 2;
    public  static  final   int     GCE_SERVER_NEEDS_ATTENTION = 3;
    public  static  final   int     GCE_LISTING_PARSER_ERROR = 4;
    public  static  final   int     GCE_NETWORK_ERROR = 5;
    public  static  final   int     GCE_BAD_LOGIN = 6;
    public  static  final   int     GCE_BAD_FOLDER = 7;
    
    private     GLogMessage     gceMsg;
    private     int             gceType;
    private     int             gceParseIssueOffset;
    
    /*
    *
    * Constructors
    *
    */
    
    public GConnectionException( final String msgTxt )
    {
        
        this(new GLogMessage( GLogMessage.GLOG_ERROR, msgTxt ), GConnectionException.GCE_PROTOCOL_ERROR);
        
    }
    
    public GConnectionException( ParseException e )
    {
        
        this(new GLogMessage( GLogMessage.GLOG_ERROR, e.getMessage()), GConnectionException.GCE_LISTING_PARSER_ERROR );
        
    }
    
    public GConnectionException( final String msgTxt, final int type )
    {
        
        this(new GLogMessage( GLogMessage.GLOG_ERROR, msgTxt ), type);
        
    }

    public GConnectionException( final GLogMessage msg, final int type )
    {
    
        if( msg == null )
        {
            throw new NullPointerException("GConnectionException : msg was null");
        }

        gceMsg = msg;
        gceType = type;
        
    }
    
    public int getType()
    {
        return gceType;
    }
    
    public String getMessage()
    {
        return gceMsg.getMessageText();
    }
    
    public  String getDescription()
    {
        String  theString=null;;
    
        switch(gceType)
        {
    
            case GCE_CONNECTION_LOST:
                theString = GLocalized.GCE_CONNECTION_LOST;
            break;
            case GCE_OPERATION_NOT_SUPPORTED:
                theString = GLocalized.GCE_OPERATION_NOT_SUPPORTED;
            break;
            case GCE_PROTOCOL_ERROR:
                theString = GLocalized.GCE_PROTOCOL_ERROR;
            break;
            case GCE_SERVER_NEEDS_ATTENTION:
                theString = GLocalized.GCE_SERVER_NEEDS_ATTENTION;
            break;
            case GCE_LISTING_PARSER_ERROR:
                theString = GLocalized.GCE_LISTING_PARSER_ERROR;
            break;
            case GCE_NETWORK_ERROR:
                theString = GLocalized.GCE_NETWORK_ERROR;
            break;
            case GCE_BAD_LOGIN:
                theString = GLocalized.GCE_BAD_LOGIN;
            break;
            case GCE_BAD_FOLDER:
                theString = GLocalized.GCE_BAD_FOLDER;
            break;
            
        }
        
        return theString;
        
    }
    
}
