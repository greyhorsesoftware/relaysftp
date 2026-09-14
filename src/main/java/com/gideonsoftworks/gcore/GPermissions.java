//
//  GPermissions.java
//  Gideon
//
//  Created by David on 10/26/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gcore;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;

public class GPermissions 
{
    
    public  final   static  int     OWNER_READ = 1;
    public  final   static  int     OWNER_WRITE = 2;
    public  final   static  int     OWNER_EXECUTE = 4;
    public  final   static  int     GROUP_READ = 8;
    public  final   static  int     GROUP_WRITE = 16;
    public  final   static  int     GROUP_EXECUTE = 32;
    public  final   static  int     OTHER_READ = 64;
    public  final   static  int     OTHER_WRITE = 128;
    public  final   static  int     OTHER_EXECUTE = 256;
    private                 int     gpPermissions;
    private                 String  gpStartChar;
    
    public  GPermissions( GPermissions perms )
    {
        
        gpPermissions = perms.getBitField();
        gpStartChar   = perms.getStartChar();
        
    }
    
    public  GPermissions( int permOctal, String sChar )
    {
        
        gpPermissions = permOctal;
        gpStartChar   = new String(sChar);
        
    }
    
    public  GPermissions( String permissionsString )
    {
    
        //System.out.print( "Permissions : " + permissionsString );
    
        gpStartChar = permissionsString.substring(0,1);
        
        //System.out.print( " -+- startChar : " + gpStartChar );
        
        // convert the permissions to a bit field contained in gpPermissions
        gpPermissions = this.processPermissionString(permissionsString);
        // test our numeric generator
        //System.out.print( " -+- Numeric Value : " + this.getNumericValue() );
    }
       
    public  boolean getOwnerHasRead()
    {
        
        if( (gpPermissions & OWNER_READ) > 0 )
        {
            return true;
        }
            
        return false;

    }
    
    public  boolean getOwnerHasWrite()
    {
        if( (gpPermissions & OWNER_WRITE) > 0 )
        {
            return true;
        }
            
        return false;
    }
    
    public  boolean getOwnerHasExecute()
    {
    
        if( (gpPermissions & OWNER_EXECUTE) > 0 )
        {
            return true;
        }
            
        return false;
    
    }
    
    public  boolean getGroupHasRead()
    {
    
        if( (gpPermissions & GROUP_READ) > 0 )
        {
            return true;
        }
        
        return false;
        
    }
    
    public  boolean getGroupHasWrite()
    {
        if( (gpPermissions & GROUP_WRITE) > 0 )
        {
            return true;
        }
        
        return false;
    }
    
    public  boolean getGroupHasExecute()
    {
    
        if( (gpPermissions & GROUP_EXECUTE) > 0 )
        {
            return true;
        }
        
        return false;
        
    }
    
    public  boolean getWorldHasRead()
    {
    
        if( (gpPermissions & OTHER_READ) > 0 )
        {
            return true;
        }
        
        return false;

    }
    
    public  boolean getWorldHasWrite()
    {
        if( (gpPermissions & OTHER_WRITE) > 0 )
        {
            return true;
        }
        
        return false;

    }
    
    public  boolean getWorldHasExecute()
    {
        if( (gpPermissions & OTHER_EXECUTE) > 0 )
        {
            return true;
        }
        
        return false;
    }
             
    public  String  getNumericValueAsString()
    {
    
        int value = this.getNumericValue();

        StringBuilder    nString = new StringBuilder(Integer.valueOf(value).toString());
        
        if( !this.getOwnerHasRead() &
            !this.getOwnerHasWrite() &
            !this.getOwnerHasExecute() )
            {
                nString.insert(0,"0");
            }
                      
        return nString.toString();
    
    }
             
    public  int getNumericValue()
    {
        int ownerNumber = 0;
        int groupNumber = 0;
        int otherNumber = 0;
        
        if( (gpPermissions & OWNER_READ) > 0 )
        {
            ownerNumber +=4;
        }
        
        if( (gpPermissions & OWNER_WRITE) > 0 )
        {
            ownerNumber +=2;
        }
        
        if( (gpPermissions & OWNER_EXECUTE) > 0 )
        {
            ownerNumber +=1;
        }
        
        if( (gpPermissions & GROUP_READ) > 0 )
        {
            groupNumber +=4;
        }
        
        if( (gpPermissions & GROUP_WRITE) > 0 )
        {
            groupNumber +=2;
        }
        
        if( (gpPermissions & GROUP_EXECUTE) > 0 )
        {
            groupNumber +=1;
        }
        
        if( (gpPermissions & OTHER_READ) > 0 )
        {
            otherNumber +=4;
        }
        
        if( (gpPermissions & OTHER_WRITE) > 0 )
        {
            otherNumber +=2;
        }
        
        if( (gpPermissions & OTHER_EXECUTE) > 0 )
        {
            otherNumber +=1;
        }
        
        String  string = new StringBuilder().append(ownerNumber).append(groupNumber).append(otherNumber).toString();
        
        return Integer.valueOf(string).intValue();
    
    }
    
    public  String  getSymbolicValue()
    {
        StringBuilder  buf = new StringBuilder();

        buf.append(gpStartChar);
        
        if( (gpPermissions & OWNER_READ) > 0 )
        {
            buf.append("r");
        }else{
            buf.append("-");
        }
        
        if( (gpPermissions & OWNER_WRITE) > 0 )
        {
            buf.append("w");
        }else{
            buf.append("-");
        }
        
        if( (gpPermissions & OWNER_EXECUTE) > 0 )
        {
            buf.append("x");
        }else{
            buf.append("-");
        }
        
        if( (gpPermissions & GROUP_READ) > 0 )
        {
            buf.append("r");
        }else{
            buf.append("-");
        }
        
        if( (gpPermissions & GROUP_WRITE) > 0 )
        {
            buf.append("w");
        }else{
            buf.append("-");
        }

        if( (gpPermissions & GROUP_EXECUTE) > 0 )
        {
            buf.append("x");
        }else{
            buf.append("-");
        }
        
        if( (gpPermissions & OTHER_READ) > 0 )
        {
            buf.append("r");
        }else{
            buf.append("-");
        }
        
        if( (gpPermissions & OTHER_WRITE) > 0 )
        {
            buf.append("w");
        }else{
            buf.append("-");
        }
        
        if( (gpPermissions & OTHER_EXECUTE) > 0 )
        {
            buf.append("x");
        }else{
            buf.append("-");
        }

        return buf.toString();
    
    }
    
    public int getBitField()
    {
        return gpPermissions;
    }
    
    public String getStartChar()
    {
        return gpStartChar;
    }
    
    /**
    *
    * Twiddle methods for ui etc...
    *
    *
    *
    *
    *
    */
     
    // owner
    public  void    twiddleOwnerRead()
    {
        gpPermissions ^= OWNER_READ;
    }
    
    public  void    twiddleOwnerWrite()
    {
        gpPermissions ^= OWNER_WRITE;
    }
    
    public  void    twiddleOwnerExecute()
    {
        gpPermissions ^= OWNER_EXECUTE; 
    } 
    
    // group
    public  void    twiddleGroupRead()
    {
        gpPermissions ^= GROUP_READ;
    }
    
    public  void    twiddleGroupWrite()
    {
        gpPermissions ^= GROUP_WRITE;
    }
    
    public  void    twiddleGroupExecute()
    {
        gpPermissions ^= GROUP_EXECUTE; 
    }   
    
    // world
    public  void    twiddleWorldRead()
    {
        gpPermissions ^= OTHER_READ;
    }
    
    public  void    twiddleWorldWrite()
    {
        gpPermissions ^= OTHER_WRITE;
    }
    
    public  void    twiddleWorldExecute()
    {
        gpPermissions ^= OTHER_EXECUTE; 
    }

    /**
     * Parse server supplied permission string, e.g.:
     * 
     * lrwxrwxrwx - > 777
     * 
     * @param symbolicValue   symbolic permission string to parse
     */
    private int processPermissionString( String symbolicValue )
    {

        byte[]  permString   = null;
        int     length      = symbolicValue.length();
        int     permissionValue = 0;
        
        try{
            permString = symbolicValue.getBytes("US-ASCII");
        }catch( UnsupportedEncodingException e )
        {
            
        }
        
            
        // skip the first character as it is d,l,s,- etc...
        for( int i = 1 ; i < length ; i++ )
        {
        
            byte    cChar = ((Byte)Array.get( permString, i )).byteValue();

            
            if( i <= 3 )
            {
                if( cChar == 'r' )
                {
                    permissionValue |= OWNER_READ;
                }else if( cChar == 'w' )
                {
                    permissionValue |= OWNER_WRITE;
                }else if( cChar == 'x' )
                {
                    permissionValue |= OWNER_EXECUTE;
                }

            }else if( i > 3 && i <= 6 ){
                
                if( cChar == 'r' )
                {
                    permissionValue |= GROUP_READ;
                }else if( cChar == 'w' )
                {
                    permissionValue |= GROUP_WRITE;
                }else if( cChar == 'x' )
                {
                    permissionValue |= GROUP_EXECUTE;
                }

            }else if( i > 6 && i <= 9 ){
                
                if( cChar == 'r' )
                {
                    permissionValue |= OTHER_READ;
                }else if( cChar == 'w' )
                {
                    permissionValue |= OTHER_WRITE;
                }else if( cChar == 'x' )
                {
                    permissionValue |= OTHER_EXECUTE;
                }
            }

        }

        return permissionValue;

    }

}
