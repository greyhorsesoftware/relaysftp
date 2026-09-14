//
//  GListParserFILE.java
//  Gideon
//
//  Created by David on 9/18/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gcore;
import com.gideonsoftworks.gcore.*;
import java.text.ParseException;
import java.io.File;

public class GListParserFILE extends GListParser
{

/**
    This method returns a human readable string denoting parser type. 
    @return Human readable string describing parser.
*/
public  String  getName(){ return "FILE:// Listing Parser"; };

/**
    This method returns a GFileItem for the Java File passed in.
    @param raw Java File Object   
	@return FileItem
*/
public  GFileItem  parseLine( final Object raw ) throws ParseException
{   

    GFileItem   nFileItem = new GFileItem( (File)raw );
	
    return nFileItem;

}   

}
