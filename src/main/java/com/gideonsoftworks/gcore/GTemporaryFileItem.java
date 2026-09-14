//
//  GTemporaryFileItem.java
//  GSFTP
//
//  Created by David on 12/2/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gcore;

import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gcore.*;
import java.lang.*;
import java.io.*;

public class GTemporaryFileItem extends GFileItem
{
    
    public  GTemporaryFileItem(String ext)
    {
        super( new File(new String( GLocalized.TEMP_FILE_PATH + System.currentTimeMillis() + ( ext != null ? "." + ext : "" ) )) );
    }
    
    public  GTemporaryFileItem()
    {
        super( new File(new String( GLocalized.TEMP_FILE_PATH + System.currentTimeMillis() )) );
    }
    
    public void delete()
    {
        getOriginalFile().delete();
    }
    
}
