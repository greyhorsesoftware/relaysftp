//
//  GDataItemInterface.java
//  JavaDataObjects
//
//  Created by David on 9/10/05.
//  Copyright 2005 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gdo;

import java.util.*;
import com.google.gson.JsonObject;

/**
*   GDataItemInterface
*   Standard interface all GDataItems that want to work with GDataArray have to implement.
*/
public interface GDataItemInterface
{
/**
*
*   Implements a GDO compatible table controller/manager.
*
*   @return  HashMap that contains the key->value pairs for this item.
*
*/

/**
*
*   Set/Get properties for this object.
*
*   @param  key Key to set
*   @param  value   value to set it too.
*   @return  true if successful
*
*/
public boolean  setObjectForKey( final String key, final Object value );
public Object   getObjectForKey( final String key );

/**
*
*   Individual items can disallow changing of its keys
*
*   @param  key Key to test for changeability
*   @return  true if successful
*
*/
public boolean  allowChangeValueForKey( final String key );

/**
*
*   Returns an array of index to key names, the view objects can use this to map 
*   between integer indexes and keys.
*   @return  Returns an array of index to key names.
*
*/
public HashMap  getIndexMapping();

/**
*
*   Returns an array of keys this data item uses
*   @return  Returns an array of key names
*
*/
public ArrayList  getKeyNames();

/**
*   Returns a JSON representation of this data item.
*   @return  JsonObject with all key/value pairs
*/
public JsonObject toJsonObject();

}
