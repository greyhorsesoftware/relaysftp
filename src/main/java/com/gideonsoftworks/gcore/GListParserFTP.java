//
//  GListParserFTP.java
//  Gideon
//
//  Converts Commons Net FTPFile[] to GFileItem children.
//

package com.gideonsoftworks.gcore;

import org.apache.commons.net.ftp.FTPFile;
import java.text.ParseException;
import java.util.Calendar;

public class GListParserFTP extends GListParser
{

public String getName(){ return "Commons Net FTP Listing Parser"; }

public GFileItem parseLine( final Object theData ) throws ParseException
{
    FTPFile f = (FTPFile) theData;

    if( f == null || f.getName() == null ) return null;

    String name = f.getName();
    long size = f.getSize();
    Calendar cal = f.getTimestamp();
    java.util.Date lastModified = (cal != null) ? cal.getTime() : null;

    GFileItem nFile = new GFileItem( f.getRawListing(), name, size, lastModified );

    if( f.isDirectory() )
    {
        nFile.setFileType(GFileItem.G_FOLDER);
    }else if( f.isSymbolicLink() )
    {
        nFile.setFileType(GFileItem.G_LINK);
        nFile.setLinkedName(f.getLink());
    }else{
        nFile.setFileType(GFileItem.G_FILE);
    }

    nFile.setOwner(f.getUser());
    nFile.setGroup(f.getGroup());

    // Build permissions string from FTPFile permission bits
    StringBuilder perm = new StringBuilder(10);
    perm.append(f.isDirectory() ? 'd' : (f.isSymbolicLink() ? 'l' : '-'));
    perm.append(f.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION) ? 'r' : '-');
    perm.append(f.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION) ? 'w' : '-');
    perm.append(f.hasPermission(FTPFile.USER_ACCESS, FTPFile.EXECUTE_PERMISSION) ? 'x' : '-');
    perm.append(f.hasPermission(FTPFile.GROUP_ACCESS, FTPFile.READ_PERMISSION) ? 'r' : '-');
    perm.append(f.hasPermission(FTPFile.GROUP_ACCESS, FTPFile.WRITE_PERMISSION) ? 'w' : '-');
    perm.append(f.hasPermission(FTPFile.GROUP_ACCESS, FTPFile.EXECUTE_PERMISSION) ? 'x' : '-');
    perm.append(f.hasPermission(FTPFile.WORLD_ACCESS, FTPFile.READ_PERMISSION) ? 'r' : '-');
    perm.append(f.hasPermission(FTPFile.WORLD_ACCESS, FTPFile.WRITE_PERMISSION) ? 'w' : '-');
    perm.append(f.hasPermission(FTPFile.WORLD_ACCESS, FTPFile.EXECUTE_PERMISSION) ? 'x' : '-');

    nFile.setPermissions(new GPermissions(perm.toString()));

    return nFile;
}

}
