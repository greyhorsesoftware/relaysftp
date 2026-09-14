//
//  GJobDescription.java
//  Gideon
//
//  Created by David on Mon Jul 19 2004.
//  Copyright (c) 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gja;

import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gcore.*;
import com.gideonsoftworks.gja.*;
import java.util.*;

/**
   An GSJobDescription object is used to record an action to be performed on an indivdual/group of files.
*/

public class GJobDescription {
    
    /**
        Job Property Constants
    */
    
    public  static  final   String  UNKNOWN_JOB_PROP        = "UNKNOWN_JOB_PROP";
    /** Long: the polled folder's mtime in seconds, or -1 when unsupported/unreadable (MTIME_JOB_TYPE). */
    public  static  final   String  MTIME_RESULT_JOB_PROP   = "MTIME_RESULT_JOB_PROP";
    
    /**
    * Integer representing mode desired ( if supported by protocol ) 
    *           0 Use file mappings 
    *           1 as Text ( ASCII )
    *           2 as Binary
    */
    public  static  final   String  TRANSFERMODE_JOB_PROP   = "TRANSFERMODE_JOB_PROP";
    /**
    * Integer representing octal permission number ie 777
    */
    public  static  final   String  PERMISSIONS_JOB_PROP    = "PERMISSIONS_JOB_PROP";
    /**
    * Boolean representing true or false - user wants recursion
    */
    public  static  final   String  RECURSIVE_JOB_PROP      = "RECURSIVE_JOB_PROP";
    /**
    * String containing new name
    */
    public  static  final   String  NAME_JOB_PROP           = "NAME_JOB_PROP";
    /**
    * String containing path to list after connecting
    */
    public  static  final   String  DEFAULT_FOLDER_JOB_PROP = "DEFAULT_FOLDER_JOB_PROP";
    /**
    * Boolean containing yes or no to using PASV mode
    */
    public  static  final   String  CONNECT_MODE_PROP = "CONNECT_MODE_PROP";
	/**
	*
	* Boolean containg true or false whether to always overwrite
	*
	*/
	public  static  final   String  IGNORE_COLLISIONS_PROP = "IGNORE_COLLISIONS_PROP";
    /**
	*
	* Boolean containg true or false whether to generate the initial folder hieirarchy
	*
	*/
	public  static  final   String  GENERATE_ROOTS_PROP = "GENERATE_ROOTS_PROP";
	
	
    /**
        Job Type Constants
    */
    public  static  final   int     UNKNOWN_JOB_TYPE = -1;
    public  static  final   int     DOWNLOAD_JOB_TYPE = 0;
    public  static  final   int     UPLOAD_JOB_TYPE = 1;
    public  static  final   int     DELETE_JOB_TYPE = 2;
    public  static  final   int     CHMOD_JOB_TYPE = 3;
    public  static  final   int     MOVE_JOB_TYPE = 4;
    public  static  final   int     CHANGE_AND_LIST_JOB_TYPE = 5;
    public  static  final   int     RENAME_JOB_TYPE = 6;
    public  static  final   int     LOGIN_JOB_TYPE = 7;
    public  static  final   int     NEWFOLDER_JOB_TYPE = 8;
    public  static  final   int     REFRESH_JOB_TYPE = 9;
    public  static  final   int     DISCONNECT_JOB_TYPE = 10;
    public  static  final   int     PING_JOB_TYPE = 11;
    public  static  final   int     MIRROR_JOB_TYPE = 12;
    public  static  final   int     MTIME_JOB_TYPE = 13;      // remote-folder mtime poll (roadmap 3.1)
    
    /**
        Job Flag Constants
    */
    static  final   int     NO_FLAG = 0;
    static  final   int     SHOULD_TAKE_KEY_FLAG = 1;

    private     int             gjdType;
    private     GJobStatus      gjdJobStatus = new GJobStatus(this);
    private     GConnection     gjdConnectionToUse = null;
    private     String          gjdName=null;
    private     GFileItem       gjdTargetFile = null;
    private     GFileItem       gjdSourceFile = null;
    private     Object          gjdOriginator = null;
    private     HashMap         gjdProperties = new HashMap();
    private     ArrayList       gjdPrereqs = new ArrayList();
    private     GQueue          gjdParentQueue = null;
    
/**
  Use this method to create a new GJobDescription.
  @param jobType         type of job to create.
  @param connectionToUse Connection to use for work. Can be null. In that case a connection is created.
*/
public  GJobDescription( final int jobType, GConnection connectionToUse, String name )
{

    gjdType = jobType;
    
    if( connectionToUse!=null)
    {
        setConnectionToUse(connectionToUse);
    }
    
    if( name != null )
    {
        setName(name);
    }
    
}

/**
* there are no null checks on this - because the only place that uses it GQueue - will never set this to null
*
*/
public void setParentQueue( GQueue q ){
    
    gjdParentQueue = q;

}

public  GQueue  getParentQueue()
{

    return gjdParentQueue;

}

public boolean isSecuredJob()
{
    // source file item is the item being worked on
    GFileItem   sfItem = getTargetFile();
    GFileItem   tfItem = getSourceFile();
    
    if( sfItem.getFileIsSecured() ||
        tfItem.getFileIsSecured() )
    {
        return true;
    }
    
    return false;
    
}

public boolean isTransferJob()
{

    if( ( gjdType==DOWNLOAD_JOB_TYPE ) ||
        ( gjdType==UPLOAD_JOB_TYPE ) )
    {
        return true;
    }
        
    return  false;

}

public boolean isDownloadJob() { return gjdType == DOWNLOAD_JOB_TYPE; }
public boolean isUploadJob() { return gjdType == UPLOAD_JOB_TYPE; }
public boolean isDeleteJob() { return gjdType == DELETE_JOB_TYPE; }
public boolean  isLoginJob()
{

    if( gjdType==LOGIN_JOB_TYPE )
    {
        return true;
    }
        
    return  false;
    
}

/*
* 
* Properties are used to pass in data 
*    like new folder name, permission value etc...
*
*/
public HashMap getProperties()
{
    return gjdProperties;
}

public void addProperty( Object key, Object value )
{
    
    gjdProperties.put( key, value );

}

public void removeProperty( Object key )
{

    gjdProperties.remove( key );

}

public Object getProperty( Object key )
{

    return gjdProperties.get( key );

}

public  int getJobType()
{
    return gjdType;
}

/**
  Specify a name for this job description.
  @param name New name for job desc.
*/
public  void  setName( String name )
{
    
    if( name == null )
    {
        throw new NullPointerException("GJobDescription->setName() name was null");
    }

    gjdName = name;
    
}

/**
  Returns programmer specified name or a generic based on type.
  @return name for job description.
*/
public  String  getName()
{
    
    String      name=null;

    if( gjdName != name )
    {
    
        name=gjdName;
    
    }else{

        switch( gjdType )
        {
            
            case UNKNOWN_JOB_TYPE:
                name = GLocalized.UNKNOWN_JOB_TYPE;
            break;
            
            case DOWNLOAD_JOB_TYPE:
                name = GLocalized.DOWNLOAD_JOB_TYPE;
            break;
            
            case UPLOAD_JOB_TYPE:
                name = GLocalized.UPLOAD_JOB_TYPE;
            break;
            
            case DELETE_JOB_TYPE:
                name = GLocalized.DELETE_JOB_TYPE;
            break;
            
            case CHMOD_JOB_TYPE:
                name = GLocalized.CHMOD_JOB_TYPE;
            break;

            case MOVE_JOB_TYPE:
                name = GLocalized.MOVE_JOB_TYPE;
            break;
            
            case CHANGE_AND_LIST_JOB_TYPE:
                name = GLocalized.LIST_JOB_TYPE;
            break;
            
            case RENAME_JOB_TYPE:
                name = GLocalized.RENAME_JOB_TYPE;
            break;
            
            case LOGIN_JOB_TYPE:
                name = GLocalized.LOGIN_JOB_TYPE;
            break;

        
        }
 
    }
    
    return name;
 
}

/**
  This method is used to set an orignator for a job. This way in the jm_jobCompleted() method
  listeners can differentiate and know if a job pertains to them - without needing state objects in their class.
  @param orig Originator
*/
public  void    setOriginator(final Object orig)
{ 

    if( orig == null )
    {
        throw new NullPointerException("GJobDescription->setOriginator : null Object passed in");
    }

    gjdOriginator = orig;
    
}

/**
  Use this method to get the current jobs originating object.
  @return returns the object that created this job.
*/
public Object  getOriginator()
{ 
    
    return gjdOriginator; 

}

/**
  This method is used to set the connection a job should use
  @param conn Connection to use
*/
public  void    setConnectionToUse(final GConnection conn)
{ 

    if( conn == null )
    {
        throw new NullPointerException("GJobDescription->setConnectionToUse : null GConnection passed in");
    }

    gjdConnectionToUse = conn;
    
}

/**
  Use this method to get the current job status object.
  @return returns GSJobStatus for this Job Description 
*/
public GConnection  getConnectionToUse()
{ 
    
    return gjdConnectionToUse; 

}

/**
  This method is used to set the jobs status object.
  @param js GJobStatus object
*/
public  void    setJobStatus(final GJobStatus js)
{ 
    if( js == null )
    {
        throw new NullPointerException("GJobDescription->setJobStatus : null GJobStatus passed in");
    }

    gjdJobStatus = js;
    
}

/**
  Use this method to get the current job status object.
  @return returns GSJobStatus for this Job Description 
*/
public GJobStatus getJobStatus()
{ 
    
    return gjdJobStatus; 

}

/**
  Use this method to set the target location for source files. How this is used depends on the job type.
  
  UNKNOWN_JOB_TYPE              - Undefined
  DOWNLOAD_JOB_TYPE             - Download location for source files
  UPLOAD_JOB_TYPE               - Upload location for source files
  DELETE_JOB_TYPE               - Folder/File to CHMOD
  CHMOD_JOB_TYPE                - Folder/File to CHMOD
  MOVE_JOB_TYPE                 - Target location for move
  CHANGE_AND_LIST_JOB_TYPE      - Folder to list
  RENAME_JOB_TYPE               - File to rename
  LOGIN_JOB_TYPE                - User specified home folder to navigate to on login

  @param f Target location as a file item.
  @return returns YES if successful 
*/
public boolean  setTargetFile(final GFileItem f )
{ 

    if( f == null )
    {
        throw new NullPointerException("GJobDescription->setTargetFile : null GFileItem passed in");
    }

    gjdTargetFile = f;

    return true; 
        
}

public GFileItem    getTargetFile()
{
    return gjdTargetFile;
}

/**
  Use this method to add a source file for transfer to the targetfile.
  @param f Source file.
  @return returns true if successful 
*/
public boolean  setSourceFile(final GFileItem f )
{ 
	
	// allow null source file items in uploads/downloads
	if( f == null &&
		!isTransferJob() )
    {
        throw new NullPointerException("GJobDescription->setSourceFile : null GFileItem passed in");
    }

    gjdSourceFile = f;

    return true; 
     
}

public GFileItem    getSourceFile()
{
    return gjdSourceFile;
}

/**
  Use this method to get job status information. See GSJob header for keys.
  @return returns YES if successful 
*/
public HashMap getInfo(){  return new HashMap(); }

/**
  Use this method to get a prerequisite
  @param i dependant job to process.
  @return GJobDescription 
*/
public GJobDescription  getPrerequisite(final int i )
{ 

    return (GJobDescription)gjdPrereqs.get(i);

}

/**
  Use this method to add a prerequisite to the job.
  @param j dependant job to process.
  @return returns true if successful 
*/
public boolean  addPrerequisite(final GJobDescription j)
{ 

    return gjdPrereqs.add(j);

}

/**
  Use this method to remove a prerequisite to the job.
  @param j dependant job to process.
  @return returns YES if successful 
*/
public boolean  removePrerequisite(final GJobDescription j)
{ 
        
    return gjdPrereqs.remove(j);

}

/**
  Use this method to see if the job has any prerequisites.
  @return returns true if has prequisites 
*/
public boolean  getHasPrerequisites()
{ 

    if( gjdPrereqs.size() > 0 ) return true;

    return false; 
    
}

public int  getPrerequisiteCount()
{ 

    return gjdPrereqs.size();
 
}

}
