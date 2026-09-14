//
//  GFavoriteItem.java
//  GSFTP
//
//  Created by David on 9/10/05.
//  Copyright 2005 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gdo;

import java.util.*;
import com.gideonsoftworks.gdo.GAbstractDataItem;
import com.gideonsoftworks.gcore.GStringUtilities;

public class GBookmarkFavoritePath extends GAbstractDataItem 
{

    // key strings
    public  static  final  String  FAVORITE_NAME = "FAVORITE_NAME";
    public  static  final  String  FAVORITE_PATH = "FAVORITE_PATH";
    
    public GBookmarkFavoritePath()
    {
        super();
    }
    
    public GBookmarkFavoritePath( String dirPath )
    {
        super();
  
        HashMap contents = new HashMap();
        contents.put(GBookmarkFavoritePath.FAVORITE_NAME, GStringUtilities.getLastPathComponent(dirPath));
        contents.put(GBookmarkFavoritePath.FAVORITE_PATH, dirPath);

        setContents(contents);
        
    }
    
    public GBookmarkFavoritePath( String name, String path )
    {
        
        super();
        
        HashMap contents = new HashMap();
        contents.put(GBookmarkFavoritePath.FAVORITE_NAME, name);
        contents.put(GBookmarkFavoritePath.FAVORITE_PATH, path);

        setContents(contents);
        
    }
    
    public boolean  allowChangeValueForKey( final String key )
    {
        return true;
    }
 
    public ArrayList getKeyNames()
    {
        ArrayList al = new ArrayList();
        al.add(GBookmarkFavoritePath.FAVORITE_NAME);
        al.add(GBookmarkFavoritePath.FAVORITE_PATH);
        return al;
    }
          
}
