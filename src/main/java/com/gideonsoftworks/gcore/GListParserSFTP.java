//
//  GListParserSFTP.java
//  Gideon
//
//  Created by David on 10/29/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//
//  Rewritten 2024: replaced abandoned j2ssh library with sshj (com.hierynomus:sshj)
//

package com.gideonsoftworks.gcore;

import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.FileMode;
import net.schmizz.sshj.sftp.RemoteResourceInfo;

import java.text.ParseException;
import java.util.Date;

/**
    SFTP listing parser.
*/
public class GListParserSFTP extends GListParser
{

/**
    This method returns a human readable string denoting parser type.
    @return Human readable string describing parser.
*/
public String getName() { return "SFTP Listing Parser"; }

/**
    This method returns a GFileItem for the sshj RemoteResourceInfo object passed in.
    @param raw RemoteResourceInfo entry from SFTPClient.ls()
    @return FileItem
*/
public GFileItem parseLine( final Object raw ) throws ParseException
{
    RemoteResourceInfo  info        = (RemoteResourceInfo) raw;
    FileAttributes      attrs       = info.getAttributes();
    String              abp         = info.getPath();
    String              fn          = info.getName();
    long                rawSize     = attrs.getSize();
    Date                mDate       = new Date(attrs.getMtime() * 1000L);
    int                 fileType;

    GFileItem nFileItem = new GFileItem(abp, fn, rawSize, mDate);

    if( info.isDirectory() )
    {
        fileType = GFileItem.G_FOLDER;
    } else if( attrs.getMode().getType() == FileMode.Type.SYMLINK ) {
        fileType = GFileItem.G_LINK;
    } else {
        fileType = GFileItem.G_FILE;
    }

    nFileItem.setFileType(fileType);
    nFileItem.setGroup(String.valueOf(attrs.getGID()));
    nFileItem.setOwner(String.valueOf(attrs.getUID()));
    nFileItem.setPermissions(new GPermissions(buildPermissionsString(attrs)));

    return nFileItem;
}

private static String buildPermissionsString( FileAttributes attrs )
{
    FileMode mode = attrs.getMode();
    int mask = mode.getMask() & 0777;

    char typeChar;
    switch( mode.getType() )
    {
        case DIRECTORY: typeChar = 'd'; break;
        case SYMLINK:   typeChar = 'l'; break;
        default:        typeChar = '-'; break;
    }

    char[] p = {
        typeChar,
        (mask & 0400) != 0 ? 'r' : '-',
        (mask & 0200) != 0 ? 'w' : '-',
        (mask & 0100) != 0 ? 'x' : '-',
        (mask & 0040) != 0 ? 'r' : '-',
        (mask & 0020) != 0 ? 'w' : '-',
        (mask & 0010) != 0 ? 'x' : '-',
        (mask & 0004) != 0 ? 'r' : '-',
        (mask & 0002) != 0 ? 'w' : '-',
        (mask & 0001) != 0 ? 'x' : '-',
    };
    return new String(p);
}

}
