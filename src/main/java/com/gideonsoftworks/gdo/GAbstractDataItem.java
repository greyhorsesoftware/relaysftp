//
//  GAbstractDataItem.java
//  JavaDataObjects
//
//  Created by David on 9/10/05.
//  Copyright 2005 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gdo;

import com.gideonsoftworks.gideon.core.GideonConfiguration;
import com.gideonsoftworks.gdo.GDataItemInterface;
import com.google.gson.JsonObject;
import java.util.*;

public class GAbstractDataItem implements GDataItemInterface
{

    private HashMap contentHash;
    private HashMap indexMapping;

public GAbstractDataItem()
{
    super();
    contentHash     = new HashMap();
    indexMapping    = null;
}

public GAbstractDataItem( HashMap contents )
{
    super();
    contentHash     = new HashMap(contents);
    indexMapping    = null;
}

public void  setContents( HashMap contents )
{

    if( contents == null )
    {
        throw new NullPointerException("GAbstractDataItem::setContents() - contents were null");
    }
    contentHash = null;
    contentHash = contents;
}

public  HashMap getIndexMapping()
{

    if( indexMapping == null )
    {        
        indexMapping = new HashMap();
        // get the keys
        ArrayList   keys = getKeyNames();
        if( keys != null )
        {
            int i = 0;
            int t = keys.size();
            for( i = 0 ; i < t ; i++ )
            {
                String value = (String)keys.get(i);
                // create index mapping
                String key = Integer.valueOf(i).toString();
                indexMapping.put( key, value);
                if( GideonConfiguration.GDODEBUG ) System.out.println("Key : " + key + " Value : " + value );
                
            }
        }       
    }
    
    return indexMapping;
                    
}

public ArrayList  getKeyNames()
{
    return null;
}

public boolean  setObjectForKey( final String key, final Object value )
{   
    
    if( key == null )
    {
        throw new NullPointerException("GAbstractDataItem::setValueForKey() - Key was null");
    }
    
    if( value == null )
    {
        throw new NullPointerException("GAbstractDataItem::setValueForKey() - Value was null");
    }    

    contentHash.put( key, value );
    
    return true;
    
}

public Object   getObjectForKey( final String key )
{

    if( key == null )
    {
        throw new NullPointerException("GAbstractDataItem::getValueForKey() - Key was null");
    }    

    return contentHash.get(key);
    
}

public boolean  allowChangeValueForKey( final String key )
{
    return false;
}

public JsonObject toJsonObject()
{
    JsonObject obj = new JsonObject();
    for (Object key : contentHash.keySet())
    {
        String k = (String) key;
        Object v = contentHash.get(k);
        obj.addProperty(k, v != null ? v.toString() : "");
    }
    return obj;
}

public void loadFromJsonObject(JsonObject obj)
{
    for (String key : obj.keySet())
    {
        contentHash.put(key, obj.get(key).getAsString());
    }
}

}
