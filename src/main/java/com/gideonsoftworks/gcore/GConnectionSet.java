//
//  GConnectionSet.java
//  Gideon
//
//  Created by David on Mon Jul 19 2004.
//  Copyright (c) 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gcore;

public class GConnectionSet 
{

    private     GConnection     gcmLocalConnection = null;
    private     GConnection     gcmRemoteConnection = null;
    private     String          gcmName = null;
    /**
        Create a new empty connection set.
    */
    public  GConnectionSet( final String name )
    {
    
        this.setName(name);

    }

    
    /**
    Create a new connection set with a local and remote connection.
    @param lConnection  Local GConnection
    @param rConnection  Remote GConnection
    */
    public  GConnectionSet( final GConnection lConnection, final GConnection rConnection, final String name )
    {
        
        if( lConnection == null )
        {
            throw new NullPointerException("GConnectionSet::GConnectionSet - local connection is null");
        }

        gcmLocalConnection = lConnection;
        
        if( rConnection == null )
        {
            throw new NullPointerException("GConnectionSet::GConnectionSet - remote connection is null");
        }
        
        gcmRemoteConnection = rConnection;
        
        this.setName(name);
        
    }
    
    /**
    Use this method to get the connection sets name
    @return Connection set name.
    */
    public  String getName()
    {
    
        return gcmName;
    
    }
    
    public String toString()
    {
        return getName();
    }
    
    /**
    Use this method to get the connection sets name
    @param name connection set name
    */
    public  void setName( final String name )
    {
    
        if( name == null )
        {
            throw new NullPointerException("GConnectionSet::setName - name is null");
        }
        
        gcmName = name;
    
    }
    
    /**
    Use this method to get the remote GConnection 
    @return true if remote connection set is connected
    */
    public  boolean isRemoteConnected()
    {
        boolean     isConnected = false;
        
        if( gcmRemoteConnection == null ) return false;
            
        try{
            isConnected = gcmRemoteConnection.getIsConnected();
        }catch( GConnectionException e )
        {
            isConnected = false;
        }
        
        return isConnected;
        
    }
    
    /**
    Use this method to get the remote GConnection 
    @return Remote GConnection
    */
    public  GConnection getRemoteConnection()
    {
    
        return gcmRemoteConnection;
    
    }
    
    /**
    Use this method to set the remote GConnection 
    @param c Remote connection 
    */
    public  void setRemoteConnection( final GConnection c )
    {
        
        if( c == null )
        {
            throw new NullPointerException("GConnectionSet::setRemoteConnection - Connection is null");
        }
        
        gcmRemoteConnection = c;
    
    }

    /**
    Use this method to get the local GConnection 
    @return Remote GConnection
    */
    public  GConnection getLocalConnection()
    {
    
        return gcmLocalConnection;
    
    }
    
    /**
    Use this method to set the local GConnection 
    @param c local connection 
    */
    public  void setLocalConnection( final GConnection c )
    {
        
        if( c == null )
        {
            throw new NullPointerException("GConnectionSet::setLocalConnection - Connection is null");
        }
        
        gcmLocalConnection = c;
    
    }
    
}
