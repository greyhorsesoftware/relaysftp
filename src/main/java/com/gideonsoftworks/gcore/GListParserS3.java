//
//  GListParserS3.java
//  Gideon
//
//  S3 listing parser — handles S3Object (files) and CommonPrefix (folders).
//

package com.gideonsoftworks.gcore;

import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.text.ParseException;
import java.util.Date;

public class GListParserS3 extends GListParser
{

public String getName() { return "S3 Listing Parser"; }

public GFileItem parseLine( final Object raw ) throws ParseException
{
    if( raw instanceof S3Object )
    {
        S3Object obj = (S3Object) raw;
        String key = obj.key();

        // Extract leaf name: strip everything up to and including the last '/'
        String name = key;
        if( name.endsWith("/") ) name = name.substring(0, name.length() - 1);
        int lastSlash = name.lastIndexOf('/');
        if( lastSlash >= 0 ) name = name.substring(lastSlash + 1);

        long size = obj.size() != null ? obj.size() : 0;
        Date mDate = obj.lastModified() != null
            ? Date.from(obj.lastModified())
            : null;

        GFileItem nFileItem = new GFileItem(key, name, size, mDate);
        nFileItem.setFileType(GFileItem.G_FILE);
        // ETag enables the S3 change detectors (single-part ETag == MD5). Strip the quotes the
        // SDK leaves on; multipart ETags keep their "-N" suffix so detectors can spot them.
        if( obj.eTag() != null ) nFileItem.setDigest( obj.eTag().replace("\"", "") );
        return nFileItem;

    } else if( raw instanceof CommonPrefix ) {
        CommonPrefix cp = (CommonPrefix) raw;
        String prefix = cp.prefix();

        // Strip trailing slash and extract folder name
        String name = prefix;
        if( name.endsWith("/") ) name = name.substring(0, name.length() - 1);
        int lastSlash = name.lastIndexOf('/');
        if( lastSlash >= 0 ) name = name.substring(lastSlash + 1);

        GFileItem nFileItem = new GFileItem(prefix, name, 0, null);
        nFileItem.setFileType(GFileItem.G_FOLDER);
        return nFileItem;
    }

    return null;
}

}
