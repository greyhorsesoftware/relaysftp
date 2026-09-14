//
//  GFileItem.java
//  Gideon
//
//  Created by David on Mon Jul 19 2004.
//  Copyright (c) 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gcore;

import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.core.GideonConfiguration;
import com.gideonsoftworks.gcore.*;
import java.util.*;
import java.lang.*;
import javax.swing.*;
import java.awt.datatransfer.*;
import java.lang.reflect.*;
import java.net.*;
import java.io.*;

// NIH Stuff

/**
   An GFileItem is an abstraction of a file. It contains methods to set/get the following file properties.
   
    Set
        - Type
        - Link Path
        - Name  
        - Parent File Item ( if app )
        - Path
        - Permissions ( as octal number )
        - Parent GFileSource
        - Owner
        - Group
        - Size
        - Date Modified
        - Extension <-- derived from name
        - Original listing - ie the server line
        
    Get ( in addition to set list )
        - Link
        - Type
        - Permissions ( as string ie rwxrwxrwx )
        - url
        - file count ( for folders )
        - Original listing - ie the server line
    
    Other
        - Method to add subitem
        - Method to remove subitem
        - Method to get children items
    
*/

public class GFileItem /*extends GListenableItem*/ implements Transferable, Comparable
{
    public  static  final   int     G_UNKNOWN = -1;
    public  static  final   int     G_FOLDER = 0;
    public  static  final   int     G_FILE = 1;
    public  static  final   int     G_LINK = 2;
    public  static  final   int     G_CDUP = 3;
    public  static  final   int     G_APPLICATION = 4;
    public  static  final   int     G_HARD_DRIVE = 5;
    public  static  final   int     G_FLOPPY = 6;
    public  static  final   int     G_REMOVABLE_MEDIA = 7;
    
    public  static  final   int     STATE_NONE=-1;
    public  static  final   int     STATE_IDLE = 0;
    public  static  final   int     STATE_IN_USE = 1;
    public  static  final   int     STATE_DELETED = 2;
    public  static  final   int     STATE_HIDDEN = 3;
    public  static  final   int     STATE_LISTED = 4;
    public  static  final   int     STATE_NOT_LISTED = 5;
    
    private     GConnection     fConnection = null;
    private     GPermissions    fPermissions = null;
    private     int             fType=G_UNKNOWN;
    private     int             fState = GFileItem.STATE_NONE;
    private     String          fName = null;
    private     String          fSize = null;
    private     long            fRawSize = 0;
    private     Date            fModified = null;
    private     String          fOwner = null;
    private     String          fGroup = null;
    private     String          fCompletePath = null;
    private     String          fLinkPath = null;
    private     String          fServerFileListing = null;
    private     String          fDigest = null;   // content digest if the listing provides one (S3 ETag); else null
    private     GFileItem       fPlaceholder = null;
    private     GFileItem       fParentFileItem = null;
    private     ArrayList       children = new ArrayList();
    private     File            fOriginalFile=null;
    private     String          fEOL=null;
    
    private     boolean         fIsLocal = false; // is local
    private     boolean         fIsRoot = false; // is a root
    private     boolean         fIsMyComputer = false; // contains roots
    private     boolean         fIsDesktop = false; // contains roots
    
    /** Local files carry no Unix permissions; one shared immutable placeholder instead of one per item. */
    private static final GPermissions LOCAL_DEFAULT_PERMISSIONS = new GPermissions("--");

    private static final String DEFAULT_ASCII_TYPES = "txt,c,cgi,cp,cpp,h,hqx,htm,html,java,l,m,p,py,pas,php,php3,pl,pm,rb,sh,shtml,uu,uue,y";
    private static final String DEFAULT_BINARY_TYPES = "jpg,jpeg,gif,tif,tiff,psd,png,bmp,pdf";
    private static final String DEFAULT_PACKAGE_TYPES = "nib,rtfd,prefPane,loginPlugin,bundle";

    private     static          HashMap    asciiTypes = null;
    private     static          HashMap    binaryTypes = null;
	private     static          HashMap    packageTypes = null;

    static{
        asciiTypes = generateHashMap(loadTypesOrDefault(GLocalized.ASCII_TYPES_FILE, DEFAULT_ASCII_TYPES));
        binaryTypes = generateHashMap(loadTypesOrDefault(GLocalized.BINARY_TYPES_FILE, DEFAULT_BINARY_TYPES));
        packageTypes = generateHashMap(loadTypesOrDefault(GLocalized.PACKAGE_TYPES_FILE, DEFAULT_PACKAGE_TYPES));
    }

    private static String loadTypesOrDefault(String fileName, String defaultTypes)
    {
        try {
            File f = new File(GLocalized.DATA_FOLDER_PATH + fileName);
            if( f.exists() )
            {
                // Preserves the original behavior: lines concatenated with no separators.
                return String.join("", java.nio.file.Files.readAllLines(
                    f.toPath(), java.nio.charset.Charset.defaultCharset()));
            }
        } catch( IOException e ) {
            // fall through to default
        }
        return defaultTypes;
    }
    
    private static HashMap generateHashMap( String csv )
    {
            String[]        extArray        =   null;
            HashMap         hash            =   null;

            if( csv == null || csv.isEmpty() )
            {
                throw new NullPointerException("GFileItem::generateHashMap -> csv string was null or empty");
            }

            // split on the ,
            extArray = csv.split(",");
  
            if( extArray == null )
            {
                throw new NullPointerException("GFileItem::generateHashMap -> Extension array was null");
            }
  
            int aCount = Array.getLength(extArray);
            
            hash = new HashMap();

            for( int i=0; i < aCount ; i++ )
            {
                String ci = (String)Array.get(extArray, i );
                // add the lowercase
                if( GideonConfiguration.EXTENSIONMAPPINGDEBUG ) System.out.println("Adding : " + ci );
                hash.put(ci,null);
                // upper case it in case the user users caps when typing in extensions
                if( GideonConfiguration.EXTENSIONMAPPINGDEBUG ) System.out.println("Adding : " + ci.toUpperCase() );
                hash.put(ci.toUpperCase(),null);
            }
            
            if( GideonConfiguration.EXTENSIONMAPPINGDEBUG ) System.out.println(hash);
            
            return hash;
        
    }
    
    /*
    *
    *
    *
    *
    *
    *
    *
    */
    //final static ResourceBundle ASCII_TYPES_BUNDLE = ResourceBundle.getBundle("I8LN/AsciiTypes");
    //final static ResourceBundle GRAPHICS_TYPES_BUNDLE = ResourceBundle.getBundle("I8LN/ImageTypes");

    private static boolean _isASCII( String ext )
    {

        if( ext == null ) return false;

        if( GideonConfiguration.EXTENSIONMAPPINGDEBUG ) System.out.println("Checking for key : " + ext );
        
        if( asciiTypes==null ) throw new NullPointerException("GFileItem->_isASCII : Ascii Type Array is null");
        
        return asciiTypes.containsKey(ext);
        
    }
    
    public boolean isASCIIFile()
    {
        return GFileItem._isASCII(getFileNameExtension());
    }
    
    private static boolean _isImage(String ext)
    {

        if( ext == null ) return false;
        
        if( GideonConfiguration.EXTENSIONMAPPINGDEBUG ) System.out.println("Checking for key : " + ext );
        
        if( binaryTypes==null ) throw new NullPointerException("GFileItem->_isImage : Binary Type Array is null");

        return binaryTypes.containsKey(ext);
        
    }
    
    public boolean isGraphicsFile()
    {
        return GFileItem._isImage(getFileNameExtension());
    }
	
	private static boolean _isPackage(String ext)
    {

        if( ext == null ) return false;
        
        if( GideonConfiguration.EXTENSIONMAPPINGDEBUG ) System.out.println("Checking for key : " + ext );
        
        if( packageTypes==null ) throw new NullPointerException("GFileItem->_isPackage : Package Type Array is null");

        return packageTypes.containsKey(ext);
        
    }
    
    public boolean isPackagedFile()
    {
        return GFileItem._isPackage(getFileNameExtension());
    }
	
	public int compareTo( Object f )
	{
		return 1;
	
	}
    
    public static   String  completePathToChild( GFileItem parent, String childName )
    {
        
        StringBuilder pathToChild = new StringBuilder(parent.getCompletePath());
        
        if( !parent.getIsRoot() )
        {
            pathToChild.append(parent.getConnection().getSeparator());
        }
        
        pathToChild.append(childName);
        
        return pathToChild.toString();
        
    }
    
    /*
    *
    *
    *  Constructors
    *
    *
    */
    
    public GFileItem( File file )
    {
        long            rawSize     = 0;
        String          abp         = null;
        String          fn          = null;
        String          permissions = null;
        Date            mDate       = null;
        int             fileType    = 0;
        
        fOriginalFile = file;

        // file name and path
        abp = file.getAbsolutePath();
        if( abp!=null ) setCompletePath(abp);
        
        fn = file.getName(); 
        if( fn != null &&
            fn.length() != 0 ){
            
            if( fn.compareTo("Desktop") == 0 )
            {
                setIsDesktop(true);
            }
            
            setName(fn);
            
        }else{
            setName(abp);
        }
        
        // date a size
        mDate = new Date(file.lastModified());
        if( mDate != null ) setDateModified(mDate);

        rawSize = file.length();
        setRawSize(rawSize);
        
        StringBuilder sizeString = new StringBuilder();
        
        if (rawSize>=1073741824)
            sizeString.append(formatNumber(Long.toString(rawSize/1073741824))).append(" GB");
        else if (rawSize>=1048576)
            sizeString.append(formatNumber(Long.toString(rawSize/1048576))).append(" MB");
        else if (rawSize>=1024)
            sizeString.append(formatNumber(Long.toString(rawSize/1024))).append(" KB");
        else
            sizeString.append(formatNumber(Long.toString(rawSize))).append(" B");
        
        if( sizeString != null ) setHumanReadableSize(sizeString.toString());

        if(file.isDirectory())
        {
            fileType = GFileItem.G_FOLDER;
            setFileState(GFileItem.STATE_NOT_LISTED);
        }else
        {
            fileType = GFileItem.G_FILE;
        } 
        
        setFileType(fileType);
        
        setGroup("unknown");
        setOwner("unknown");
        
        // get and assign permissions
        setPermissions( LOCAL_DEFAULT_PERMISSIONS );
        
        setFileIsLocal(true);
 
    }

    /**
    Use this method to create a new file item with specific fields.
    @param fileName Name for file. 
    @param c        Parent connection for file. 
    */
    public  GFileItem( final String fileName, final GConnection c )
    {   
            this( null, fileName, 0, null );
            
            this.setConnection(c);
    }

    /**
    Use this method to create a new file item with specific fields.
    @param fileName Name for file. 
    @param c        Parent connection for file. 
    */
    public  GFileItem( final String fileName, final String filePath, final GConnection c )
    {   

            this( null, fileName, 0, new Date() );
            setCompletePath(filePath);
            setConnection(c);
    }
    
    /**
    Use this method to create a new file item with specific fields.
    @param rL Raw Server Listing
    @param n Name
    @param rawSize duh
    @param date Date modified
    */
    public  GFileItem( final String rL, final String n, final long rawSize, final Date date )
    {   
            this.setRawSize(rawSize);

            // generate a human readable size
            StringBuilder sizeString = new StringBuilder();

            if (rawSize>=1073741824)
                sizeString.append(formatNumber(Long.toString(rawSize/1073741824))).append(" GB");
            else if (rawSize>=1048576)
                sizeString.append(formatNumber(Long.toString(rawSize/1048576))).append(" MB");
            else if (rawSize>=1024)
                sizeString.append(formatNumber(Long.toString(rawSize/1024))).append(" KB");
            else
                sizeString.append(formatNumber(Long.toString(rawSize))).append(" B");
            this.setHumanReadableSize(sizeString.toString());
            
            if( n != null ) this.setName(n);
            if( date != null ) this.setDateModified(date);
            if( rL != null ) this.setRaw(rL);
            setFileState(GFileItem.STATE_NOT_LISTED);
    }
    
    /**
    Returns the local file this GFileItem was created from
    */
    public  File getOriginalFile()
    {

        if( !getIsFileLocal() )
        {
            return null;
        }
        
        // return an unmodifiable set
        return fOriginalFile;

    }
    
    public  boolean    getFileIsSecured()
    {
        
        GConnection theConnection = getConnection();
        
        if( theConnection == null )
        {
            return false;
        }
    
        return theConnection.getIsSecure();
    
    }
    
    public  void    setFileIsLocal( boolean isLocal )
    {
        fIsLocal = isLocal;
    }
    
    public boolean getIsFileLocal()
    {
        return fIsLocal;
    }
    
    public  void    setIsRoot( boolean isRoot )
    {
        fIsRoot = isRoot;
    }
    
    public boolean getIsRoot()
    {
        return fIsRoot;
    }
	
 
	public boolean getIsDesktop()
	{
        return fIsDesktop;
    }
    
    public void setIsDesktop( boolean isDesktop )
    {
    
        fIsDesktop = isDesktop;
    
    }
    
    public  void    setIsMyComputer( boolean isMyComputer )
    {
        fIsMyComputer = isMyComputer;
    }
    
    public boolean getIsMyComputer()
    {
        return fIsMyComputer;
    }
    
/**
* Format a number from 12000123 to 12 000 123.
* @param number Number to be formatted
*/

private static String formatNumber(String number)
{
    StringBuilder formatted = new StringBuilder(number);
    for (int i = number.length(); i > 0; i -= 3)
    {
            formatted.insert(i, ',');
    }
    
    formatted.delete( formatted.length()-1, formatted.length() );
    
    return formatted.toString();
}

    /*
    *
    *
    *  Children Management
    *
    *
    */
	
	/** 
	*
	* Use this method to get a child with the same name.
	* @param name name to find
	*
	*/
    public  synchronized GFileItem    getChildNamed( final String name )
    {
		int			childCount	= children.size();
		String		uCName		= name.toUpperCase();
		int			i;
		
		for( i = 0 ; i < childCount ; i++ )
		{
			GFileItem	cItem = (GFileItem)children.get(i);
			String	cName = cItem.getName().toUpperCase();	
			if( uCName.compareTo(cName) == 0 )
			{
				return cItem;
			}
		}

        return null;
        
    }
	
	/** 
	*
	* Use this method to check and see if a file/folder named name exists in it.
	* @param name name to find
	*/
    public  synchronized boolean    fileNameExists( final String name )
    {
		int			childCount	= children.size();
		String		uCName		= name.toUpperCase();
		int			i;
		
		for( i = 0 ; i < childCount ; i++ )
		{
			GFileItem	cItem = (GFileItem)children.get(i);
			String	cName = cItem.getName().toUpperCase();	
			if( uCName.compareTo(cName) == 0 )
			{
				return true;
			}
		}

        return false;
        
    }
	
    /**
    Use this method to add a child item to the GFileItem ( folders )
    @return true if the removal is successful
    */
    public  boolean    isParentOf( final GFileItem c )
    {
       
        if( c.getParentFileItem().equals(this) ) return true;
        return false;
        
    }
    
    /**
    Use this method to add a child item to the GFileItem ( folders )
    */
    public  synchronized void    addChild( final GFileItem c )
    {
        if( c == null )
        {
            throw new NullPointerException("GFileItem : Attempted to add a null child");
        }
        
        if( children.add(c) )
        {
            c.setParentFileItem(this);
        }

    }
    
    /**
    Use this method set the file source for this GFileItem.
    */
    public  synchronized void removeChild( final GFileItem c )
    {

        if( c == null )
        {
            throw new NullPointerException();
        }
        
        if( children.remove(c) )
        {
            c.setParentFileItem(null);
        }

    }
    
    /**
    Use this method get access to the child GFileItems
    @return Returns true if the removal is successful
    */
    public  synchronized ArrayList getChildren()
    {

        if( children == null )
        {
            return null;
        }

        // return an unmodifiable set
        return children;

    }

    /**
    Snapshot copy - the EDT renders from this, never from the live list workers mutate.
    */
    public  synchronized ArrayList getChildrenSnapshot()
    {

        return new ArrayList(children);

    }

	public  synchronized void setChildren( ArrayList childs )
    {

        if( childs == null )
        {
            throw new NullPointerException("GFileItem : Assigned null children to " + this.getName() + " using setChildren()");
        }
        
        // return an unmodifiable set
        children = childs;

    }
	
    /**
    Use this method remove all the file items from a GFileItem
    */
    public  synchronized void removeAllChildren()
    {

        if( children == null )
        {
            return;
        }
        
        children.clear();

    }
    
    /**
    Use this method get access to a root item
    @return Returns true if the removal is successful
    */
    public  GFileItem   getRootFileItemForPath( String path )
    {
        GFileItem cFI = null;
        
        if( GideonConfiguration.LOGINPATHGENERATIONDEBUG ) 
        {
            System.out.println("\tEntered getRootFileItemForPath() with : " + path);
        }
        
        String   pFC = path.substring(0,1);
        
        if( GideonConfiguration.LOGINPATHGENERATIONDEBUG ) System.out.println("\t\tPath first char is : >" + pFC + "<" );
        
        // walk all our children and return the one that matches the passed in path
        // we are nothing more than a collection of potential roots - ie A:\, C:\ etc... 
        for( int i = 0 ; i < getChildCount() ; i++ )
        {
            cFI = getChild(i);
            
            // XXX this is obsolete...
            if( cFI.getIsCDUP() ) continue;
            
            // compare based on first char
            String fC = cFI.getName().substring(0,1);
            
            if( GideonConfiguration.LOGINPATHGENERATIONDEBUG ) System.out.println("\t\tComparing : " + fC + " to " + pFC );
            
            if(fC.compareToIgnoreCase( pFC ) == 0 )
            {
                if( GideonConfiguration.LOGINPATHGENERATIONDEBUG ) 
                {
                    System.out.println("\t\tMatched a root : " + cFI );
                }
                return cFI;
            }
        
        }
        
        if( GideonConfiguration.LOGINPATHGENERATIONDEBUG ) 
        {
        }
        
        return cFI;
    
    }
    
    /**
    Use this method get access to the child GFileItems
    @return Returns true if the removal is successful
    */
    public  synchronized GFileItem getChild( int i )
    {

        if( children == null )
        {
            return null;
        }

        // out of bounds returns null ( was "i > count" - an off-by-one that threw
        // IndexOutOfBoundsException for i == count, e.g. getChild(0) on an empty folder )
        if( i < 0 || i >= this.getChildCount() )
        {
            return null;
        }

        // return an unmodifiable set
        return (GFileItem)children.get(i);

    }
    
    /**
    Use this method set the file source for this GFileItem.
    @return Returns true if the removal is successful
    */
    public  synchronized int getChildCount()
    {

        if( children == null )
        {
            return 0;
        }

        return children.size();

    }
    
    /*
    *
    *
    *  Connection stuff
    *
    *
    */
    
    /**
    Use this method set the file source for this GFileItem.
    @param f GFileSource
    */
    public  void    setConnection( final GConnection f )
    {
        if( f == null )
        {
            throw new NullPointerException();
        }
        
        fConnection = f;

    }
    
    /**
    Use this method set the file source for this GFileItem.
    @return Returns this file items file source
    */
    public  GConnection getConnection()
    {

        return fConnection;

    }
    
    /**
    Use this method get a url string for a file/ftp/sftp item.
    @return Returns this file items url
    */
    public  String  getURLString()
    {
        String              urlString   = null;
        GConnection         c           = this.getConnection();
        
        if( c == null ) return null;
        
        GBookmark   bm = c.getBookmark();
        
        if( bm == null ) return null;
        
        String protocol = bm.getProtocolString();

        if( protocol == null ) return null;

        switch(bm.getProtocol())
        {
            
            case GBookmark.G_FILE_PROTOCOL:
            
                urlString = protocol + this.getCompletePath();
            
            break;
        
            case GBookmark.G_FTP_PROTOCOL:
            case GBookmark.G_SFTP_PROTOCOL:
                
                urlString = protocol + bm.getHostname() + this.getCompletePath();
            
            break;
        
        }
    
        return urlString;
    
    }
    
    /*
    *
    *
    *  File Data Methods
    *
    *
    */
    
    /**
        This is the problem placeholders solve.
        
        User logs in specifying a path 
        In the login job this path is broken down and GFileItems are created for each "part"
        ie /Users/alice
        
        -> / GFileItem
        -> Users GFileItem
        -> david
        
        / is set as the parent of users, users as the parent of david etc...
        
        now in the ui the user clicks go to parent. Users is correctly selected and as it has not been listed
        a new list job is created and fired. The problem lies in that the listing of Users results in a new 
        "david" GFileItem being created - that does not point to the GFileItem already existing from our login job. Placeholders allow
        us to specify a "david" placeholder in users. Then when users is listed it looks at its placeholders and if one of the 
        "new" children match in name to a placeholder. The placeholders file listings are copied from the place holder
        to the new GFileItem. In this way the original Object graph is not disturbed.
        
        @param placeHolder Place holder.
    */
    public  void    setPlaceHolder( GFileItem placeHolder )
    {
        
        fPlaceholder = placeHolder;

    }
    
    /**
    Use this method get the file items permissions.
    @return Parent file item
    */
    public  GFileItem getPlaceHolder()
    {

        return fPlaceholder;

    }
    
    /*
    *   Size
    */
    
    /**
        Use this method set the date modified for a file.
        @param dateModified Date modified.
    */
    public  void    setDateModified( final Date dateModified )
    {
        
        if( dateModified == null )
        {
            throw new NullPointerException();
        }
        
        fModified = dateModified;

    }
    
    /**
    Use this method get the file items permissions.
    @return Parent file item
    */
    public  Date getDateModified()
    {

        return fModified;

    }
    
    /*
    *   Size
    */
    
    /**
    Use this method set the file items size
    @param size long
    */
    public  void    setRawSize( final long size )
    {

        fRawSize = size;

    }

    /** Content digest from the listing if available (e.g. S3 ETag, quote-stripped); else null. */
    public  void    setDigest( final String digest ) { fDigest = digest; }

    /** @return the listing-provided content digest (S3 ETag), or null if none. */
    public  String  getDigest() { return fDigest; }
    
    /**
    Use this method get the file items size.
    @return File item size.
    */
    public  long getRawSize()
    {
			
		if( getIsDir() ) return 0;
			
        return fRawSize;

    }
    
    /**
    Use this method set the file items human readable size
    @param hrSize human readable size
    */
    public  void    setHumanReadableSize( final String hrSize )
    {

        fSize = hrSize;

    }
    
    /**
    Use this method get the file items human readable size.
    ie 1 B, 2K etc...
    @return File item size.
    */
    public  String getHumanReadableSize()
    {
    
        if( fSize == null ) return "";
    
        return fSize;

    }

    
    
    /*
    *   Permissions
    */
    
    /**
    Use this method set the permissions for an item.
    @param pPerms GPermissions object representing the permissions of this file.
    */
    public  void    setPermissions( final GPermissions pPerms )
    {

        fPermissions = pPerms;
        
    }
    
    public  GPermissions    getPermissions()
    {
        return fPermissions;
    }

    /*
    *   Parent File Item
    */
    
    /**
    Use this method set the parent file item for this GFileItem.
    @param pFileItem GFileItem
    */
    public  void    setParentFileItem( final GFileItem pFileItem )
    {

        fParentFileItem = pFileItem;

    }
    
    /**
    Use this method get the parent filte item.
    @return Parent file item
    */
    public  GFileItem getParentFileItem()
    {

        return fParentFileItem;

    }
    
    /*
    *   Name
    */
    
    /**
    Use this method set the name for this GFileItem
    @param name New name.
    */
    public  void    setName( final String name )
    {
        if( name == null )
        {
            throw new NullPointerException();
        }
        
        if( name.length() == 0 )
        {
            throw new IllegalArgumentException();
        }
        
        fName = name;

    }
    
    public String toString() { return this.getName(); }
    
    /**
    Use this method set the file source for this GFileItem.
    @return Returns this file items file source
    */
    public  String getName()
    {

        return fName;

    }
    
    /**
    Use this method get the server the file item resides on
    @return Returns this file items server name
    */
    public  String getServerName()
    {

        GConnection myConnection = this.getConnection();
        GBookmark   myBookmark = myConnection.getBookmark();

        return myBookmark.getHostname();

    }
    
    /**
    *
    *
    *
    */
    public  String  getEOLCharacters()
    {
        return fEOL;
    }
    
    public  void    setEOLCharacters( String eolChars )
    {
        fEOL=eolChars;
    }
    
    /*
    *   Extension
    */
    
    /**
    Use this method to return the file extension.
    @return extension as string
    */
    public  String    getFileNameExtension()
    {
        int         lastIndexOfDot;
        String      theName;
        String      ext;
        
        theName = this.getName();
        
        if( theName.length() == 0 )
        {
            return null;
        }
        
        // get the last index of the .
        lastIndexOfDot = theName.lastIndexOf(".");
        
        if( lastIndexOfDot == -1 ) return null;
        
        // now grab from lastIndexOfDot to the end - the plus 1 is so we
        // don't include the . 
        ext = theName.substring((lastIndexOfDot+1));
        
        return ext;
        
    }

    /*
    *   Server Listing
    */

    /**
    Use this method set the "server string" for this file item.
    @param serverlisting String
    */
    public  void    setRaw( final String serverlisting )
    {
        if( serverlisting == null )
        {
            throw new NullPointerException();
        }
        
        if( serverlisting.length() == 0 )
        {
            throw new IllegalArgumentException();
        }
        
        fServerFileListing = serverlisting;

    }
    
    /**
    Use this method get the "server string" for this GFileItem. If applicable - can return null.
    @return Server file listing or null if the file item did come come from a "text" source
    */
    public  String getRaw()
    {

        return fServerFileListing;

    }
    
    /*
    *   Owner/Group/Permissions
    */
    
    /**
    Use this method set the owner for this GFileItem.
    @param owner Owner name as string
    */
    public  void    setOwner( final String owner )
    {
        
        if( owner == null )
        {
            throw new NullPointerException();
        }
        
        if( owner.length() == 0 )
        {
            throw new IllegalArgumentException();
        }
        
        fOwner = owner;

    }
    
    /**
    Use this method set owner for this GFileItem.
    @return Returns owner name for file item
    */
    public  String getOwner()
    {

        return fOwner;

    }
    
    /**
    Use this method set the group for this GFileItem.
    @param group group name as string
    */
    public  void    setGroup( final String group )
    {

        if( group == null )
        {
            throw new NullPointerException();
        }
        
        if( group.length() == 0 )
        {
            throw new IllegalArgumentException();
        }

        fGroup = group;

    }
    
    /**
    Use this method set owner for this GFileItem.
    @return Returns owner name for file item
    */
    public  String getGroup()
    {

        return fGroup;

    }
    
    /*
    *
    * Link
    *
    */
    
    /**
    Use this method set the linked name for this GFileItem.
    @param linkedName Linked name
    */
    public  void    setLinkedName( final String linkedName )
    {
        if( linkedName == null )
        {
            throw new NullPointerException();
        }
        
        if( linkedName.length() == 0 )
        {
            throw new IllegalArgumentException();
        }
        
        fLinkPath = linkedName;

    }
    
    /**
    Use this method set owner for this GFileItem.
    @return Returns owner name for file item
    */
    public  String getLinkedName()
    {

        return fLinkPath;

    }
    
    /*
    *
    * Path
    *
    */
    
    /**
    Use this method set the complete path for this GFileItem.
    @param newPath Complete path
    */
    protected  void    setCompletePath( final String newPath )
    {
        if( newPath == null )
        {
            throw new NullPointerException();
        }
        
        if( newPath.length() == 0 )
        {
            throw new IllegalArgumentException();
        }
        
		
        fCompletePath = newPath;
        
    }
	
    /**
    Use this method to get the complete path of this GFileItem.
    @return Complete path
    */
    public  String getCompletePath()
    {

        return fCompletePath;

    }
    
    /**
    Use this method see if the folder file item has been enumerated.
    @return Bool - returns yes if the fils state is STATE_LISTED
    */
    public  boolean hasBeenListed()
    {
    
        if( fState == GFileItem.STATE_LISTED )
        {
            return true;
        }

        return false;

    }
    
   /*
    *
    * State
    *
    */
    
    /**
    Use this method set the file type for this GFileItem.
    @return File type code
    */
    public  int getFileType()
    {

        return fType;

    }
    
    
    public  String  getHumanReadableDescription()
    {
    
        String  type;
    
        switch( this.getFileType() )
        {
        
            case G_FOLDER:
                
                type = GLocalized.FOLDER;
            
            break;
        
            case G_FILE:
                
                String ext = this.getFileNameExtension();
                
                if( ext != null )
                {   
                    ext = ext.toUpperCase();
                    type = new String(ext + " " + GLocalized.FILE);
                }else{
                    type = GLocalized.FILE;
                }
                
            break;

            case G_LINK:
                
                type = GLocalized.LINK;
            
            break;
            
            case G_APPLICATION:
                
                type = GLocalized.APP;
            
            break;
            
            case G_HARD_DRIVE:
                
                type = GLocalized.HARD_DRIVE;
            
            break;
            
            case G_FLOPPY:
            case G_REMOVABLE_MEDIA:    
                type = GLocalized.REM_MEDIA;
            break;
            
            default:
            
                type = "!UNKNOWN TYPE!";
            
            break;
        
        }
        
        return type;
    
    }
    
    public  ImageIcon  getInfoIcon()
    {
    
        ImageIcon  type;
        
        if( fIsDesktop )
        {
            return GLocalized.DESKTOP_BIG_ICON;
        }
		
		if( getIsRoot() )
		{
			return GLocalized.FOLDER_BIG;
		}
    
        switch( getFileType() )
        {
        
            case G_FOLDER:
                
                type = GLocalized.FOLDER_BIG;
            
            break;
        
            case G_FILE:
                
				if( isGraphicsFile() )
				{
					type = GLocalized.FILE_IMG_BIG;
				}else if( isASCIIFile() )
				{
					type = GLocalized.FILE_TXT_BIG;
				}else{
					type = GLocalized.FILE_BIG;
				}

            break;

            case G_LINK:
                
                type = GLocalized.LINK_BIG;
            
            break;
            
            case G_APPLICATION:
                
                type = GLocalized.APP_BIG;
            
            break;
            
            case G_FLOPPY:
                
                type = GLocalized.FLOPPY_BIG;
            
            break;
            
            case G_REMOVABLE_MEDIA:
                
                type = GLocalized.REM_MED_BIG;
            
            break;
            
            case G_HARD_DRIVE:
                
                type = GLocalized.HD_BIG;
            
            break;

            default:
            
                type = GLocalized.UNKNOWN_BIG;
            
            break;
        
        }
        
        return type;
    
    }

    public  boolean getIsApplication()
    {
    
        if( this.getFileType() == G_APPLICATION ) return true;
        
        return false;
        
    }
    
    public  boolean getIsDir()
    {
    
        if( this.getFileType() == G_FOLDER ) return true;
        
        return false;
        
    }
    
    public  boolean getIsCDUP()
    {
    
        if( this.getFileType() == G_CDUP ) return true;
        
        return false;
        
    }
    
    public  boolean getIsFile()
    {
    
        if( this.getFileType() == G_FILE ) return true;
        return false;
        
    }
    
    public  boolean getIsLink()
    {
    
        if( this.getFileType() == G_LINK ) return true;
        
        return false;
        
    }
    
    public  boolean getIsHardDrive()
    {
    
        if( this.getFileType() == G_HARD_DRIVE ) return true;
        
        return false;
        
    }
    
    public  boolean getIsFloppy()
    {
    
        if( this.getFileType() == G_FLOPPY ) return true;
        
        return false;
        
    }
    
    public  boolean getIsRemovableMedia()
    {
    
        if( this.getFileType() == G_REMOVABLE_MEDIA ) return true;
        
        return false;
        
    }
    
    public  boolean getIsListable()
    {
        
        int type = this.getFileType();
        
        if( type == G_FOLDER ||
            type == G_LINK ||
            type == G_HARD_DRIVE ||
            type == G_FLOPPY ||
            type == G_REMOVABLE_MEDIA )
        {
            return true;
        }
        
        return false;
    
    }
    
    /**
    Use this method to set the file type - folder, file, link.
    @param s New type for the file
    */
    public  void    setFileType( int s )
    {

        fType = s;

    }
    
    /*
    *
    * State
    *
    */
    
    /**
    Use this method set the file source for this GFileItem.
    @return File state - see GFileItemListenerInterface for state definitions
    */
    public  int getFileState()
    {

        return fState;

    }
    
    /**
    Use this method to set the tiles state.
    @param s New state for file
    */
    public  void    setFileState( int s )
    {

        fState = s;
        
    }
    
    /*
    *
    * Drag and drop
    *
    */
    public Object   getTransferData(DataFlavor flavor)
    {
        return null;
    }
    
    public DataFlavor[] getTransferDataFlavors()
    {
        DataFlavor  df = null;
        
        try{
            df = new DataFlavor("com.greyhorsesoftware.gfileitem");
        }catch(ClassNotFoundException e)
        {
            
        }
        
        DataFlavor[] n = (DataFlavor[])Array.newInstance(DataFlavor.class, 1 );
        
        Array.set( n, 0, df );
        
        return n;
        
    }
    
    public boolean  isDataFlavorSupported( DataFlavor flavor )
    {
    
        if( flavor.getMimeType().compareTo("com.greyhorsesoftware.gfileitem") == 0 ) return true;
        
        return false;
    
    }
    
}
