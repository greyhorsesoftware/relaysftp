//
//  GString.java
//  GSFTP
//
//  Created by David on 9/10/05.
//  Copyright 2005 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gcore;

import com.gideonsoftworks.gideon.core.GLocalized;

public class GStringUtilities
{
    
public static String getLastPathComponent( String srcString )
{
    int  sep = '/';
    
    // get the divider
    if( GLocalized.onWindows )
    {
        sep = '\\';
    }
    
    // get the last index of the separator
    int lastIndex = srcString.lastIndexOf(sep);
    
    // move past the separator
    lastIndex++;
    
    // now grab to the end of the string
    return srcString.substring(lastIndex);
    
}

}