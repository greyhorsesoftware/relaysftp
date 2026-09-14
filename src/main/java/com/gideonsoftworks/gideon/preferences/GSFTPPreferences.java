//
//  GSFTPPreferences.java
//  Gideon
//
//  Created by David on 11/19/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.preferences;

import java.util.prefs.*;
import com.gideonsoftworks.gcore.*;
import java.awt.Dimension;
import java.awt.Point;

public class GSFTPPreferences 
{

    public  static  String  GPREF_FIRST_SETUP           =   "com.greyhorsesoftware.GPREF_FIRST_SETUP_Rv13";
    public  static  String  GPREF_ONLY_SHOW_REMOTE      =   "com.greyhorsesoftware.GPREF_ONLY_SHOW_REMOTE";
    public  static  String  GPREF_SHOW_HIDDEN           =   "com.greyhorsesoftware.GPREF_SHOW_HIDDEN";
    public  static  String  GPREF_DEFAULT_LHOME         =   "com.greyhorsesoftware.GPREF_DEFAULT_LHOME";
    public  static  String  GPREF_KEEP_ALIVE_ON         =   "com.greyhorsesoftware.GPREF_KEEP_ALIVE_ON";
    public  static  String  GPREF_SHOW_DOT_FILES        =   "com.greyhorsesoftware.GPREF_SHOW_DOT_FILES";
    public  static  String  GPREF_SFTP_IS_DEFAULT       =   "com.greyhorsesoftware.GPREF_SFTP_IS_DEFAULT";
    public  static  String  GPREF_DONT_USE_PASV         =   "com.greyhorsesoftware.GPREF_DONT_USE_PASV";
    public  static  String  GPREF_USE_EXEC              =   "com.greyhorsesoftware.GPREF_USE_EXEC";
    public  static  String  GPREF_MONITOR_REMOTE        =   "com.greyhorsesoftware.GPREF_MONITOR_REMOTE";
    public  static  String  GPREF_THEME                 =   "com.greyhorsesoftware.GPREF_THEME";
    public  static  String  GPREF_PROXY_TYPE            =   "com.greyhorsesoftware.GPREF_PROXY_TYPE";
    public  static  String  GPREF_PROXY_HOST            =   "com.greyhorsesoftware.GPREF_PROXY_HOST";
    public  static  String  GPREF_PROXY_PORT            =   "com.greyhorsesoftware.GPREF_PROXY_PORT";
    public  static  String  GPREF_PROXY_USER            =   "com.greyhorsesoftware.GPREF_PROXY_USER";
    public  static  String  GPREF_PROXY_HAS_PW          =   "com.greyhorsesoftware.GPREF_PROXY_HAS_PW";
    public  static  String  GPREF_SOCKS_ENABLED         =   "com.greyhorsesoftware.GPREF_SOCKS_ENABLED";
    public  static  String  GPREF_SOCKS_HOST            =   "com.greyhorsesoftware.GPREF_SOCKS_HOST";
    public  static  String  GPREF_SOCKS_PORT            =   "com.greyhorsesoftware.GPREF_SOCKS_PORT";
    public  static  String  GPREF_SOCKS_USER            =   "com.greyhorsesoftware.GPREF_SOCKS_USER";
    public  static  String  GPREF_SOCKS_HAS_PW          =   "com.greyhorsesoftware.GPREF_SOCKS_HAS_PW";

    //window size
    public  static  String  GPREF_WINDOW_LOCATION_X     =   "GPREF_WINDOW_LOCATION_X";
    public  static  String  GPREF_WINDOW_LOCATION_Y     =   "GPREF_WINDOW_LOCATION_Y";
    public  static  String  GPREF_WINDOW_SIZE_WIDTH     =   "GPREF_WINDOW_SIZE_WIDTH";   
    public  static  String  GPREF_WINDOW_SIZE_HEIGHT    =   "GPREF_WINDOW_SIZE_HEIGHT";
    
    private static  Preferences prefs = Preferences.userNodeForPackage(GSFTPPreferences.class);
    
    protected GSFTPPreferences()
    {
    
    
    }
    
    public  static  boolean defaultToSFTP()
    {

        return prefs.getBoolean(GSFTPPreferences.GPREF_SFTP_IS_DEFAULT, true );

    }

    public  static  void   setDefaultToSFTP( boolean defaultToSFTP )
    {

        prefs.putBoolean(GSFTPPreferences.GPREF_SFTP_IS_DEFAULT, defaultToSFTP );

    }

    public  static  boolean useExec()
    {

        return prefs.getBoolean(GSFTPPreferences.GPREF_USE_EXEC, true );

    }

    public  static  void   setUseExec( boolean useExec )
    {

        prefs.putBoolean(GSFTPPreferences.GPREF_USE_EXEC, useExec );

    }

    public  static  boolean monitorRemoteChangesOn()
    {

        return prefs.getBoolean(GSFTPPreferences.GPREF_MONITOR_REMOTE, true );

    }

    public  static  void   setMonitorRemoteChangesOn( boolean monitor )
    {

        prefs.putBoolean(GSFTPPreferences.GPREF_MONITOR_REMOTE, monitor );

    }

    public  static  void   setWindowSizeDefaults( Dimension size, Point point )
    {
    
        prefs.putDouble(GSFTPPreferences.GPREF_WINDOW_SIZE_WIDTH, size.getWidth() );
        prefs.putDouble(GSFTPPreferences.GPREF_WINDOW_SIZE_HEIGHT, size.getHeight() );
        
        prefs.putDouble(GSFTPPreferences.GPREF_WINDOW_LOCATION_X, point.getX() );
        prefs.putDouble(GSFTPPreferences.GPREF_WINDOW_LOCATION_Y, point.getY() );

    }
    
    // return the default window width and height
    public static Dimension getDefaultWindowSize()
    {   
            
        double width = prefs.getDouble(GSFTPPreferences.GPREF_WINDOW_SIZE_WIDTH, 0 );
        double height = prefs.getDouble(GSFTPPreferences.GPREF_WINDOW_SIZE_HEIGHT, 0 );
        
        if( width == 0 &&
            height == 0 )
        {
            return null;
        }
        
        Dimension nD = new Dimension();
        nD.setSize(width,height);
        
        return nD;
            
    }
    
    public static Point getDefaultWindowLocation()
    {
        double x = prefs.getDouble(GSFTPPreferences.GPREF_WINDOW_LOCATION_X, 0 );
        double y = prefs.getDouble(GSFTPPreferences.GPREF_WINDOW_LOCATION_Y, 0 );
        
        if( x == 0 &&
            y == 0 )
        {
            return null;
        }
        
        Point nP = new Point();
        nP.setLocation(x,y);
        
        return nP;
    }

    public static void setShowDotFiles( boolean showDotFiles )
    {
            prefs.putBoolean(GSFTPPreferences.GPREF_SHOW_DOT_FILES, showDotFiles );
    }

    
    public static boolean showDotFiles()
    {
            return prefs.getBoolean(GSFTPPreferences.GPREF_SHOW_DOT_FILES, false );
    }
    
    public  static  boolean dontUsePASV()
    {
    
        return prefs.getBoolean(GSFTPPreferences.GPREF_DONT_USE_PASV, false );
    
    }
    
    public  static  void   setDontUsePASV( boolean noUsePASV )
    {
    
        prefs.putBoolean(GSFTPPreferences.GPREF_DONT_USE_PASV, noUsePASV );
    
    }
    
    public  static  boolean serverKeepAliveOn()
    {
    
        return prefs.getBoolean(GSFTPPreferences.GPREF_KEEP_ALIVE_ON, true );
    
    }
    
    public  static  void   setServerKeepAliveOn( boolean keepServerCAlive )
    {
    
        prefs.putBoolean(GSFTPPreferences.GPREF_KEEP_ALIVE_ON, keepServerCAlive );
    
    }
	
    /*
    *
    */
    public  static  String defaultLocalHomeFolder()
    {
    
        return prefs.get(GSFTPPreferences.GPREF_DEFAULT_LHOME, System.getProperty("user.home") );
    
    }
    
    public  static  void   setDefaultLocalHomeFolder( String defaultHome )
    {

        prefs.put(GSFTPPreferences.GPREF_DEFAULT_LHOME, defaultHome );

    }

    public static String getTheme()
    {
        return prefs.get(GPREF_THEME, null);   // null = unset → GTheme picks the platform default
    }

    public static void setTheme(String id)
    {
        if (id != null) prefs.put(GPREF_THEME, id);
    }

    // --- Proxy (non-secret fields; passwords live in the keyring via GProxyConfig). The has-password flags
    //     let the UI show "bubbles" for a stored secret WITHOUT querying the keychain. ---

    // FTP firewall proxy
    public static String getProxyType()        { return prefs.get(GPREF_PROXY_TYPE, "NONE"); }
    public static void   setProxyType(String t){ prefs.put(GPREF_PROXY_TYPE, t != null ? t : "NONE"); }
    public static String getProxyHost()        { return prefs.get(GPREF_PROXY_HOST, ""); }
    public static void   setProxyHost(String h){ prefs.put(GPREF_PROXY_HOST, h != null ? h : ""); }
    public static int    getProxyPort()        { return prefs.getInt(GPREF_PROXY_PORT, 0); }
    public static void   setProxyPort(int p)   { prefs.putInt(GPREF_PROXY_PORT, p); }
    public static String getProxyUser()        { return prefs.get(GPREF_PROXY_USER, ""); }
    public static void   setProxyUser(String u){ prefs.put(GPREF_PROXY_USER, u != null ? u : ""); }
    public static boolean proxyHasPassword()        { return prefs.getBoolean(GPREF_PROXY_HAS_PW, false); }
    public static void    setProxyHasPassword(boolean b){ prefs.putBoolean(GPREF_PROXY_HAS_PW, b); }

    // SOCKS5 proxy (used by SFTP + FTP). HTTP proxying is handled by the system proxy, not configured here.
    public static boolean socksEnabled()        { return prefs.getBoolean(GPREF_SOCKS_ENABLED, false); }
    public static void    setSocksEnabled(boolean b){ prefs.putBoolean(GPREF_SOCKS_ENABLED, b); }
    public static String getSocksHost()        { return prefs.get(GPREF_SOCKS_HOST, ""); }
    public static void   setSocksHost(String h){ prefs.put(GPREF_SOCKS_HOST, h != null ? h : ""); }
    public static int    getSocksPort()        { return prefs.getInt(GPREF_SOCKS_PORT, 0); }
    public static void   setSocksPort(int p)   { prefs.putInt(GPREF_SOCKS_PORT, p); }
    public static String getSocksUser()        { return prefs.get(GPREF_SOCKS_USER, ""); }
    public static void   setSocksUser(String u){ prefs.put(GPREF_SOCKS_USER, u != null ? u : ""); }
    public static boolean socksHasPassword()        { return prefs.getBoolean(GPREF_SOCKS_HAS_PW, false); }
    public static void    setSocksHasPassword(boolean b){ prefs.putBoolean(GPREF_SOCKS_HAS_PW, b); }

}
