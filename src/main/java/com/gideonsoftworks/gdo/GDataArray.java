//
//  GDataArray.java
//  GSFTP
//
//  Created by David on 9/11/05.
//  Copyright 2005 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gdo;

import com.gideonsoftworks.gcore.*;
import java.util.ArrayList;
import com.gideonsoftworks.gideon.core.GideonConfiguration;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.*;
import java.io.*;
import com.gideonsoftworks.gdo.*;
import com.google.gson.*;

/**
    A GDataArray holds a collection of DataItems and knows how to work with a GDataTableController UI element.
*/

public class GDataArray extends GListenableItem
{

public final static int GDA_ITEM_ADDED      = 0;
public final static int GDA_ITEM_REMOVED    = 1;

private ArrayList                       items;
private GDataTableController            viewController;
private boolean                         fGrabbedKeys            = false;
private HashMap                         myIndexToKeyMappings;

/**
      Use this method to create a new GDataController
*/
public GDataArray()
{
    super();
    items = new ArrayList();
    viewController = null;
}

/**
        Use this method to create a new GDataArray from a JSON file
        @param file File to parse for items
        @param dataClassName Class to represent data
*/
public GDataArray( final File file, final String dataClassName )
{
    super();
    items = new ArrayList();
    viewController = null;

    if( file == null || !file.exists() ) return;

    try {
        JsonElement root = JsonParser.parseString(java.nio.file.Files.readString(
            file.toPath(), java.nio.charset.StandardCharsets.UTF_8));

        // Current format: an object { "schemaVersion": 1, "items": [...] }.
        // Legacy format (pre-schemaVersion): a bare JSON array. Tolerate both; missing version == 1.
        JsonArray arr = root.isJsonObject()
            ? root.getAsJsonObject().getAsJsonArray("items")
            : root.getAsJsonArray();
        if( arr == null ) arr = new JsonArray();

        Constructor ctor = Class.forName(dataClassName).getConstructor((Class[])null);

        for( int i = 0; i < arr.size(); i++ )
        {
            GAbstractDataItem item = (GAbstractDataItem) ctor.newInstance((Object[])null);
            item.loadFromJsonObject(arr.get(i).getAsJsonObject());
            addItem(item);
        }
    } catch( Exception e )
    {
        System.out.println("Error loading JSON data array: " + e.toString());
    }
}



public JsonArray getJsonArray()
{
    JsonArray arr = new JsonArray();
    int count = getItemCount();
    for( int i = 0; i < count; i++ )
    {
        GDataItemInterface item = getItemAtIndex(i);
        arr.add(item.toJsonObject());
    }
    return arr;
}

public boolean writeToJsonFile( final File outputFile )
{
    try {
        // Wrap the items array in a versioned object. An empty list writes an empty items array
        // (rather than deleting the file), so the on-disk shape is always valid, versioned JSON.
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.add("items", getJsonArray());

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        GAtomicFileWriter.writeUtf8(outputFile, gson.toJson(root));
        return true;
    } catch( IOException e )
    {
        return false;
    }
}

public void setViewController( GDataTableController vController )
{
    
    if( vController == null )
    {
        throw new NullPointerException("GDataArray::setViewController - vController was null");
    }
    
    viewController = vController;

}

public int getItemCount()
{
    if( GideonConfiguration.GDODEBUG ) System.out.println("GDataArray::getItemCount() called");
    
    if( items == null ) return 0;
    
    int itemCount = items.size();
    
    if( GideonConfiguration.GDODEBUG ) System.out.println("\tReturning : " + itemCount );
    
    return itemCount;
}

public GDataItemInterface getItemAtIndex( final int index )
{   
    
    if( GideonConfiguration.GDODEBUG ) System.out.println("GDataArray::getItemAtIndex() called" + index );
    
    return (GDataItemInterface)items.get(index);
}

public void addItem( final GDataItemInterface nItem )
{
    if( GideonConfiguration.GDODEBUG ) System.out.println("GDataArray::addItem() called >" + nItem );
    
    if( nItem == null )
    {
        throw new NullPointerException("GDataArray : addItem - DataItem was null");
    }
    
    // first item in - get the index mapping
    if( !fGrabbedKeys )
    {
        myIndexToKeyMappings = nItem.getIndexMapping();
        fGrabbedKeys = true;
    }

    items.add( nItem );
    
    // let our controller know about the change
    if( viewController != null ){ viewController.update(nItem, GDataArray.GDA_ITEM_ADDED); }
    
    // let anyone watching the contents know about the changes
    notifyListeners();
}

public HashMap getIndexMapping()
{
    return myIndexToKeyMappings;
}

public void removeItems( final GDataItemInterface[] nDataItems )
{
    if( GideonConfiguration.GDODEBUG ) System.out.println("GDataArray::removeItems() called" );
    
    int length = Array.getLength(nDataItems);
    int i = 0;
    
    for( i = 0 ; i < length ; i++ )
    {
        GDataItemInterface di = (GDataItemInterface)Array.get(nDataItems, i);
        items.remove(di);
    }

    // let our controller know about the change
    if( viewController != null ){ viewController.update(null, GDataArray.GDA_ITEM_REMOVED); }

    // let watching the contents know about the changes
    notifyListeners();
}

public void removeItem( final GDataItemInterface nItem )
{
    if( GideonConfiguration.GDODEBUG ) System.out.println("GDataArray::removeItem() called >" + nItem );

    items.remove(nItem);
    
    // let our controller know about the change
    if( viewController != null ){ viewController.update(nItem, GDataArray.GDA_ITEM_REMOVED); }
    
    // let watching the contents know about the changes
    notifyListeners();
    
}

/**
*
* GListenableItem interface 
*
*
*/
private void notifyListeners()
{
     Iterator en = listeners.iterator();

      while (en.hasNext()) 
      {
            GDataArrayChangeInterface listener = (GDataArrayChangeInterface)en.next();
            listener.dataItemsChanged();      
      }
}

}

