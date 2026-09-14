//
//  GListParser.java
//  Gideon
//
//  Created by David on 8/25/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gcore;

import  com.gideonsoftworks.gideon.core.GideonConfiguration;
import  com.gideonsoftworks.gideon.preferences.GSFTPPreferences;
import  java.lang.*;
import  java.util.*;
import  java.text.ParseException;
import  java.lang.reflect.Array;
import  java.util.Vector;

/** 
	Superclass to all the ListParser classes
*/

abstract class GListParser 
{

/**
    This is called with the response to the FTP syst command. This function looks at the 
    actual string and from that returns a parser context object that knows how to parse the listings.
    This can and should return NULL if the parser does not support the context.
*/
public  GListParser(){};

/**
    This method handles parsing all the items contained in fileItems. It calls the parseLine() method for each item.
    @param  fileItems    Array of Server Strings
    @param  c connection we are working for
    @param  p   parent file item for all created GFileItems
*/
public  void  parseList( final Object fileItems, final GConnection c, final GFileItem p ) throws GConnectionException
{ 
    final   Vector  fileListingAsFileItems;
    final   int     aLength;
    long    startTime;
    long    endTime;
    String  pCompletePath = p.getCompletePath();
    
    /*
    // handle inserting the awesome, totally cool change to parent file item - if it is not a root item
    if( !p.getIsRoot() )
    { 
        GFileItem   newCDUP = new GFileItem("..", c );
        // flag this as as a G_CDUP so the ui can utilize it
        newCDUP.setFileType(GFileItem.G_CDUP);
        // just in case the ui driving this code is dumba
        newCDUP.setCompletePath(pCompletePath);
        // make it the first item
        p.addChild(newCDUP);
    }
    */
    if( fileItems == null )
    {
        return;
    }

    // if the folder has no items - just return.
    if( Array.getLength(fileItems) == 0 ) return;
    
    fileListingAsFileItems = new Vector();
    
    // if we haven't already - call configureForItemFormat() on first item
    this.configureForItemFormat( Array.get(fileItems, 0) );
    
    // then iterate through all the items calling parseLine(String fListing)
    // add retured item from parseLine to the fileItems array 
    // return array
    
    aLength = Array.getLength(fileItems);
    
    startTime = System.currentTimeMillis();

    for( int i = 0 ; i < aLength ; i++ )
    {
        Object      individualFileListing;
        GFileItem   nGFileItem=null;
        
        individualFileListing = Array.get(fileItems, i);
        
        if( individualFileListing instanceof String )
        {
            c.getLog().logMessage( new GLogMessage(GLogMessage.GLOG_SERVER_REPLY, (String)individualFileListing ) );
        }
        
        try{
            // process it with the parseLine method
            nGFileItem = this.parseLine(individualFileListing);
            
            // generate the path if the item is a file 
            if( nGFileItem != null )
            {
                // share owner/group/size/permissions instances across the connection's listings
                c.getListingPool().canonicalize(nGFileItem);
                String      cPath   = p.getCompletePath();
                GFileItem   pHolder = p.getPlaceHolder();
                String      fName = nGFileItem.getName();
                String      completePath = null;

                if( !GSFTPPreferences.showDotFiles() )
                {
                        if( fName.startsWith(".") )
                        {
                                continue;
                        }
                }else{
                        // filter out . and .. files
                        if( ( fName.compareTo(".") == 0 ) ||
                                ( fName.compareTo("..") == 0 ) )
                        {
                                continue;
                        }
                }
				
                // if the application is a application flag it as such
                if( ( fName.endsWith(".exe") ) ||
                    ( fName.endsWith(".app") ) )
                {
                    nGFileItem.setFileType(GFileItem.G_APPLICATION);
                }
                
                // trap rtfd/nib etc...
                if( nGFileItem.isPackagedFile() )
                {
                    nGFileItem.setFileType(GFileItem.G_FILE);
                }
				
                // if the parent is the "root" ( ie has no parent )
                // then we don't want to include a forward slash
                // ie //<filename> instead of /<filename>
                if( p.getIsRoot() )
                {
                   completePath = cPath + fName;
                }else{
                   completePath = cPath + c.getSeparator() + fName;
                }
                
                /*
                if( cPath.compareToIgnoreCase("/" ) == 0 )
                {
                   completePath = cPath + fName;
                }else{
                   completePath = cPath + c.getSeparator() + fName;
                }
                */
                nGFileItem.setCompletePath( completePath );

                if( pHolder != null )
                {
                    if( pHolder.getName().compareToIgnoreCase(nGFileItem.getName()) == 0 )
                    {
                        if( pHolder.getFileState() == GFileItem.STATE_LISTED )
                        {
                            if( GideonConfiguration.PLACEHOLDERS_DEBUG ) 
                            {   
                                System.out.println("Parent : \"" + p.getName() + "\"" + " id : \"" + p.hashCode() + "\"" );
                                System.out.println("Place holder name : \"" + pHolder.getName() + "\"" + " id : \"" + pHolder.hashCode() + "\"" );
                                System.out.println("Assigning placeholder stuff to : \"" + nGFileItem.getName() + "\" id : \"" + nGFileItem.hashCode() + "\"" );
                            }
                            nGFileItem.setChildren(pHolder.getChildren());
                            nGFileItem.setFileState(GFileItem.STATE_LISTED);
                        }
                        // remove it - it has done its job
                        p.setPlaceHolder(null);
                    }
                }
               
            }
            
        }catch( ParseException e )
        {
            throw new GConnectionException(e);
        }
        
        // call addChild
        // this adds the child as a "children" of the parent
        // then informs the child of its parent
        if( nGFileItem != null )
        {
            p.addChild(nGFileItem);
            // inform the child of its connection
            nGFileItem.setConnection(c);
            // add created GFileItem to array
            fileListingAsFileItems.add(nGFileItem);
        }
        
    }
    
    endTime = System.currentTimeMillis();
    
    
    //c.getLog().logMessage( new GLogMessage(GLogMessage.GLOG_INFORMATIONAL, "Directory parse time : " + ( (float)(endTime-startTime) * .001 ) + "s." ) );

}


/*
*
* Methods subclassers implement
*
*/

/**
    This method returns a human readable string denoting parser type. 
    @return Human readable string describing parser.
*/
public  String  getName(){ return "Listing Parser"; };


/**
    This method allows parsers subclasses to specify the eol char used. For instance, most unix sites use 0x0D0x0A or \r\n. 
*/
public  String  eolString(){ return "\r\n"; }

/**
    This method allows parsers to activate alternate parsing schemes, for instance, win nt servers can 
    show listings in one of two formats, DOS or UNIX - this method allows the winnt parser to detect this before
    being asked to process a file listing.
*/
public  GFileItem  parseLine( final Object fLine ) throws ParseException
{ 
    return null; 
}

/**
    This method is called from parse() on a plugin. The plugin should parse the string and return a GFileItem.
*/
public  void  configureForItemFormat( final Object line ){ return; }

}

