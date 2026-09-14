//
//  GListParserFTP.java
//  Gideon
//
//  Created by David on 8/25/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gcore; 

import  com.gideonsoftworks.gcore.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
    UNIX listing parser.
*/

public class GListParserUNIX extends GListParser
{
    /**
     * Maximum number of fields in raw string
     */
    /**
     * Duff line start word
     */
    private final static String TOTAL = "total";
    
    /**
     * Symbolic link symbol
     */
    private final static String SYMLINK_ARROW = "->";
    
    /**
     * Date formatter type 1
     */
    private final static SimpleDateFormat formatter1 =
        new SimpleDateFormat("MMM-dd-yyyy", Locale.US);
    
    /**
     * Date formatter type 2
     */
    private final static SimpleDateFormat formatter2 =
        new SimpleDateFormat("MMM-dd-yyyy-HH:mm", Locale.US);
    
    /**
     * Minimum number of expected fields
     */
    private final static int MIN_FIELD_COUNT = 8;
    
    /**
     * Standard number of expected fields
     */


/**
    This method returns a human readable string denoting parser type. 
    @return Human readable string describing parser.
*/
public  String  getName(){ return "UNIX Standard Listing Parser"; };

/**
    This method allows parsers subclasses to specify the eol char used. For instance, most unix sites use 0x0D0x0A or \r\n. 
*/
public  String  eolString(){ return "\r\n"; }

/**
 * Splits a listing line into whitespace-separated fields.
 */
protected String[] split(String str) {
    return str.trim().split("\\s+");
}

/**
 * Parse server supplied string, e.g.:
 * 
 * lrwxrwxrwx   1 wuftpd   wuftpd         14 Jul 22  2002 MIRRORS -> README-MIRRORS
 * -rw-r--r--   1 b173771  users         431 Mar 31 20:04 .htaccess
 * 
 * @param theData   raw data to parse - can be string or object
 */
public  GFileItem  parseLine( final Object theData ) throws ParseException 
{ 

        String  raw = (String)theData;

        GPermissions    permissionsObject=null;
        String[]        fields = split(raw);

        // duff line starting with "total 342522"
        if (fields.length == 2 && TOTAL.equalsIgnoreCase(fields[0])) 
            return null;
        
        if (fields.length < MIN_FIELD_COUNT) {
            StringBuilder listing = new StringBuilder("Unexpected number of fields in listing '");
            listing.append(raw).append("' - expected minimum ").append(MIN_FIELD_COUNT). 
                    append(" fields but found ").append(fields.length).append(" fields");
            throw new ParseException(listing.toString(), 0);
        }
        
        // field pos
        int index = 0;
        
        // first field is perms
        String permissions = fields[index++];
        char ch = permissions.charAt(0);
        boolean isDir = false;
        boolean isLink = false;
        if (ch == 'd')
            isDir = true;
        else if (ch == 'l')
            isLink = true;
        
        // some servers don't supply the link count
        int linkCount = 0;
        if (fields.length > MIN_FIELD_COUNT) {
            try {
                linkCount = Integer.parseInt(fields[index++]);
            }
            catch (NumberFormatException ignore) {}
        }
        
        // owner and group
        String owner = fields[index++];
        String group = fields[index++];
        
        // size
        long rawSize = 0L;
        String sizeStr = fields[index++];
        try {
            rawSize = Long.parseLong(sizeStr);
        }
        catch (NumberFormatException ex) {
            throw new ParseException("Failed to parse size: " + sizeStr, 0);
        }
        
        // next 3 are the date time
        int dateTimePos = index;
        Date lastModified = null;
        StringBuilder stamp = new StringBuilder(fields[index++]);
        stamp.append('-').append(fields[index++]).append('-');
        
        String field = fields[index++];
        if (field.indexOf(':') < 0) {
            stamp.append(field); // year
            lastModified = formatter1.parse(stamp.toString());
        }
        else { // add the year ourselves as not present
            Calendar cal = Calendar.getInstance();
            int year = cal.get(Calendar.YEAR);
            stamp.append(year).append('-').append(field);
            lastModified = formatter2.parse(stamp.toString());
            
            // can't be in the future - must be the previous year
            if (lastModified.after(cal.getTime())) {
                cal.setTime(lastModified);
                cal.add(Calendar.YEAR, -1);
                lastModified = cal.getTime();
            }
        }
	
        // name of file or dir. Extract symlink if possible
        String name = null;
        String linkedname = null;
        
        // we've got to find the starting point of the name. We
        // do this by finding the pos of all the date/time fields, then
        // the name - to ensure we don't get tricked up by a userid the
        // same as the filename,for example
        int pos = 0;
        boolean ok = true;
        for (int i = dateTimePos; i < dateTimePos+4; i++) {
            pos = raw.indexOf(fields[i], pos);
            if (pos < 0) {
                ok = false;
                break;
            }
        }
        if (ok) {
            String remainder = raw.substring(pos);
            if (!isLink) 
                name = remainder;
            else { // symlink, try to extract it
                pos = remainder.indexOf(SYMLINK_ARROW);
                if (pos <= 0) { // couldn't find symlink, give up & just assign as name
                    name = remainder;
                }
                else { 
                    int len = SYMLINK_ARROW.length();
                    name = remainder.substring(0, pos).trim();
                    if (pos+len < remainder.length())
                        linkedname = remainder.substring(pos+len);
                }
            }
        }
        else {
            throw new ParseException("Failed to retrieve name: " + raw, 0);  
        }
		
				
        GFileItem   nFile = new GFileItem( raw, name, rawSize, lastModified );
        
        if( isDir )
        {
            nFile.setFileType(GFileItem.G_FOLDER);
        }else if( isLink )
        {
            nFile.setFileType(GFileItem.G_LINK);
            nFile.setLinkedName(linkedname);
        }else
        {
            nFile.setFileType(GFileItem.G_FILE);
        }

        nFile.setGroup(group);
        nFile.setOwner(owner);
        
        /*
		System.out.println( "Raw : " + raw );
                System.out.println( "Name : " + name );
		System.out.println( "Raw Size : " + rawSize );
		System.out.println( "Last Modified : " + lastModified );
		System.out.println( "Owner : " + owner );
		System.out.println( "Group : " + group );
	*/	
        permissionsObject = new GPermissions(permissions);
        nFile.setPermissions(permissionsObject); 

        return nFile;
        
    }


/**
 * Parse a single ls -ld line (with full path) into a GFileItem.
 * Sets completePath to the full path from the listing.
 * Returns null if the line can't be parsed.
 */
public static GFileItem parseOneLine(String line, GConnection conn)
{
    if (line == null || line.trim().isEmpty()) return null;
    if (line.startsWith("total ")) return null;

    try {
        GListParserUNIX parser = new GListParserUNIX();
        GFileItem item = parser.parseLine(line);
        if (item == null) return null;

        // parseLine extracts the name as whatever follows the date fields.
        // For ls -ld output, that's the full path. Set completePath and fix name.
        String fullPath = item.getName();
        if (fullPath != null) {
            item.setCompletePath(fullPath);
            // Extract just the filename
            int lastSlash = fullPath.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < fullPath.length() - 1) {
                item.setName(fullPath.substring(lastSlash + 1));
            }
        }

        if (conn != null) {
            item.setConnection(conn);
        }

        return item;
    } catch (Exception e) {
        return null;
    }
}

}
