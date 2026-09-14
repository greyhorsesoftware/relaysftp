//
//  GBookmark.java
//  Gideon
//
//  Created by David on 8/10/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gcore;

import com.gideonsoftworks.gcore.*;
import com.gideonsoftworks.gdo.*;
import java.io.*;
import java.util.*;

public class GBookmark extends GListenableItem
{

public  static  final   int G_UNKNOWN_PROTOCOL  =-1;
public  static  final   int G_FTP_PROTOCOL      =0;
public  static  final   int G_SFTP_PROTOCOL     =1;
public  static  final   int G_FILE_PROTOCOL     =2;
public  static  final   int G_FTPSSL_PROTOCOL   =3;
public  static  final   int G_S3_PROTOCOL       =4;

private int             bProtocol           = 0;
private int             bPort               = 0;
private boolean         bUsePORTMode        = false;
private String          bUsername           = null;
private String          bPassword           = null;
private String          bHostname           = null;
private String          bName               = null;
private String          bHomePath           = null;
private String          bLocalHomePath      = null;
private GDataArray      bFavoritePaths      = new GDataArray();

private String          bS3AccessKey        = null;
private String          bS3Region           = null;
private String          bS3EndpointUrl      = null;
private String          bS3SessionToken     = null;   // in-memory only (temporary creds; not persisted)
private String          bS3AuthMethod       = null;   // "basic" (default) or "session"

private boolean         bNeverSavePassword  = false;
private int             bMaxConcurrentTransfers = 3;


    /**
      Use this method to create a new GBookmark.
      @param name Bookmark name
      @param host hostname or dotted decimal string.
      @param homeFolder Home Directory 
      @param un username. 
      @param pw password for un. 
      @param port port to connect on. 
      @param protocol protocol type. 
    */
    public  GBookmark( final String name, final String host, final String homeFolder, final String un, final String pw, final int port, final int protocol )
    {
            
            bPort = port;
            bProtocol = protocol;
            
            bHostname = host;
            bUsername = un;
            bPassword = pw;
            
            if( (homeFolder != null) &&
                (homeFolder.length() != 0 ) )
            {
                bHomePath = homeFolder;
            }
            
            if( name == null )
            {
                throw new NullPointerException();
            }
            
            bName = name;
 
    }

    /**
      Use this method to create a new GBookmark from a bookmark.
      @param bm bookmark
    */
    public  GBookmark( final GBookmark bm )
    {

            this( bm.getName(), bm.getHostname(), bm.getHomePath(), bm.getUsername(), bm.getPassword(), bm.getPort(), bm.getProtocol() );

            // S3 credential/config fields aren't part of the base constructor — copy them
            // so cloned bookmarks (e.g. parallel-transfer connections) keep their credentials.
            setS3AccessKey( bm.getS3AccessKey() );
            setS3Region( bm.getS3Region() );
            setS3EndpointUrl( bm.getS3EndpointUrl() );
            setS3SessionToken( bm.getS3SessionToken() );
            setS3AuthMethod( bm.getS3AuthMethod() );

    }
    
    /**
      Use this method to create a new GBookmark.
      @param host hostname or dotted decimal string.
      @param homeFolder Home Directory 
      @param un username. 
      @param pw password for un. 
      @param port port to connect on. 
      @param protocol protocol type. 
    */
    public  GBookmark( final String host, final String homeFolder, final String un, final String pw, final int port, final int protocol )
    {

            this( host, host, homeFolder, un, pw, port, protocol );
            
    }

public boolean isLocal()
{

    if( bProtocol == G_FILE_PROTOCOL )
    {
        return true;
    }

    return false;

}

public boolean isSecure()
{

    if( bProtocol == G_SFTP_PROTOCOL || bProtocol == G_S3_PROTOCOL )
    {
        return true;
    }

    return false;

}

public  String getProtocolString()
{
    
    int prot = this.getProtocol();
    
    if( prot == G_SFTP_PROTOCOL )
    {
        return "sftp://";
    }else if( prot == G_FTP_PROTOCOL )
    {
        return "ftp://";
    }else if( prot == G_S3_PROTOCOL )
    {
        return "s3://";
    }else{
        return "";
    }

}

/**
  Use this method to retrieve a bookmarks url.
  @return url as a string 
*/
public  String    toString()
{
    return bName;
}

/**
  Use this method to set a name for the bookmark.
  @param name Name for bookmark. 
*/
public  void    setName( final String name )
{

    if( name == null )
    {
        throw new NullPointerException("GBookmark-setName() : name was null");
    }

    if( name.length() == 0 )
    {
        throw new IllegalArgumentException("GBookmark-setName() : name length was 0");
    }

    bName = name;

    this.notifyListeners();

}

/**
  Use this method to retrieve a bookmarks name.
  @return Name of bookmark
*/
public  String    getName()
{
    return bName;
    
}

/**
  Use this method to set a local home path for bookmark
  @param homePath Local home path. 
*/
public  void    setLocalHomePath( final String homePath )
{
    bLocalHomePath = homePath;
}

/**
  Use this method to retrieve a bookmarks home path ( ie default folder ).
  @return Name of bookmark
*/
public  String    getLocalHomePath()
{
    return bLocalHomePath;
}

/**
  Use this method to set remote home path.
  @param homePath Remote home path
*/
public  void    setHomePath( final String homePath )
{

    bHomePath = homePath;
}

/**
  Use this method to retrieve a bookmarks home path ( ie default folder ).
  @return Name of bookmark
*/
public  String    getHomePath()
{
    return bHomePath;
}

/**
  Use this method to set the host for the bookmark.
  @param host Hostname or dotted decimal. 
*/
public  void    setHostname( final String host )
{

    bHostname = host;

}

/**
  Use this method to retrieve a bookmarks host.
  @return host Hostname or dotted decimal. 
*/
public  String    getHostname()
{
    
    return bHostname;
}

/**
  Use this method to set the Password for the bookmark.
  @param password Password. 
*/
public  void    setPassword( final  String password )
{
    bPassword = password;
}

/**
  Use this method to retrieve a bookmarks Password.
  @return host username.
*/
public  String    getPassword()
{
    return bPassword;
}


/**
  Use this method to set the username for the bookmark.
  @param username Username. 
*/
public  void    setUsername( final  String username )
{
    bUsername = username;
}

/**
  Use this method to retrieve a bookmarks username.
  @return host username. 
*/
public  String    getUsername()
{
    return bUsername;
}

/**
  Use this method to set the port for the bookmark.
  @param port Port to connect on. 
*/
public  void    setPort( final int port )
{
    bPort = port;
}

/**
  Use this method to retrieve a bookmarks port.
  @return bookmark port 
*/
public  int    getPort(){ return bPort; }

/**
  Use this method to set the protocol for the bookmark.
  @param p Protocol. 
*/
public  void    setProtocol( final int  p )
{
    bProtocol = p;
    
    this.notifyListeners();
}

/**
  Use this method to retrieve a bookmarks port.
  @return bookmark port 
*/
public  int    getProtocol()
{
    return bProtocol;
}

public  String  getS3AccessKey()    { return bS3AccessKey; }
public  void    setS3AccessKey( final String key ) { bS3AccessKey = key; }

public  boolean getNeverSavePassword()  { return bNeverSavePassword; }
public  void    setNeverSavePassword( boolean never ) { bNeverSavePassword = never; }

public  int     getMaxConcurrentTransfers() { return bMaxConcurrentTransfers > 0 ? bMaxConcurrentTransfers : 3; }
public  void    setMaxConcurrentTransfers( int max ) { bMaxConcurrentTransfers = Math.max(1, Math.min(max, 10)); }

public  String  getS3SecretKey()    { return getPassword(); }
public  void    setS3SecretKey( final String key ) { setPassword(key); }

public  String  getS3Region()       { return bS3Region; }
public  void    setS3Region( final String region ) { bS3Region = region; }

public  String  getS3EndpointUrl()  { return bS3EndpointUrl; }
public  void    setS3EndpointUrl( final String url ) { bS3EndpointUrl = url; }

public  String  getS3SessionToken() { return bS3SessionToken; }
public  void    setS3SessionToken( final String token ) { bS3SessionToken = token; }

public  String  getS3AuthMethod()   { return bS3AuthMethod; }
public  void    setS3AuthMethod( final String method ) { bS3AuthMethod = method; }

/**
 Bookmark Favorite Path Support
*/

public GDataArray getFavoritePaths()
{
    return bFavoritePaths;
}

public void setFavoritePaths( GDataArray favs )
{
    if( favs == null )
    {
        throw new NullPointerException("GBookmark - setFavoritePaths() : favorites were null");
    }
    
    bFavoritePaths = favs;

}

public void addFavoritePath( GBookmarkFavoritePath fav )
{
    if( fav == null )
    {
        throw new NullPointerException("GBookmark - addFavoritePath() : favorite was null");
    }

    bFavoritePaths.addItem(fav);
}

public void removeFavoritePaths( GDataItemInterface[] favs )
{
    if( favs == null )
    {
        throw new NullPointerException("GBookmark - removeFavoritePaths() : favs was null");
    }

    bFavoritePaths.removeItems(favs);
}

//
// END Favorite Support
//

// let our listeners know we changed
private void notifyListeners()
{
     Iterator en = listeners.iterator();

      while (en.hasNext()) 
      {
            GBookmarkChangeListenerInterface listener = (GBookmarkChangeListenerInterface)en.next();
            listener.bm_bookmarkChanged(this);      
      }
}

}
