package com.gideonsoftworks.gcore;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * First-run application-support defaults — pure and side-effect-scoped (no static app state,
 * no {@code System.exit}, no Swing dialogs) so it is directly unit-testable against a temp dir.
 * {@code Gideon.createDataFolder()} delegates here; the UI/exit handling stays in the caller.
 */
public final class GAppDataDefaults
{
    private GAppDataDefaults() {}

    /** Default Local Favorites: Home / Desktop / Documents under {@code homePath}. */
    public static String localFavoritesJson( String homePath )
    {
        JsonArray arr = new JsonArray();
        arr.add( favorite( "Home",      homePath ) );
        arr.add( favorite( "Desktop",   homePath + "/Desktop" ) );
        arr.add( favorite( "Documents", homePath + "/Documents" ) );
        return pretty( arr );
    }

    /** Default transfer filters: skip dotfiles (starts-with ".", type 3) and ".DS_Store" (matches, type 2). */
    public static String transferFiltersJson()
    {
        JsonArray arr = new JsonArray();
        arr.add( filter( ". File Filter",      3, "." ) );          // TF_STARTSWITH
        arr.add( filter( "Mac OS X Databases", 2, ".DS_Store" ) );  // TF_MATCHES
        return pretty( arr );
    }

    /**
     * Create the data + temp directories and write the default JSON files into {@code dataDir} when
     * they are absent. Existing files are left untouched. Throws on an unusable directory or I/O error.
     */
    public static void ensureDefaults( File dataDir, File tempDir, String homePath ) throws IOException
    {
        ensureDir( dataDir );
        ensureDir( tempDir );
        writeIfAbsent( new File( dataDir, "LocalFavoritePaths.json" ), localFavoritesJson( homePath ) );
        writeIfAbsent( new File( dataDir, "TransferFilters.json" ),    transferFiltersJson() );
    }

    private static void ensureDir( File d ) throws IOException
    {
        if( !d.exists() && !d.mkdirs() ) throw new IOException( "could not create " + d );
        if( !d.canRead() || !d.canWrite() ) throw new IOException( "not readable/writable: " + d );
    }

    private static void writeIfAbsent( File f, String content ) throws IOException
    {
        if( f.exists() ) return;
        try( FileWriter w = new FileWriter( f ) ) { w.write( content ); }
    }

    private static JsonObject favorite( String name, String path )
    {
        JsonObject o = new JsonObject();
        o.addProperty( "FAVORITE_NAME", name );
        o.addProperty( "FAVORITE_PATH", path );
        return o;
    }

    private static JsonObject filter( String name, int type, String value )
    {
        JsonObject o = new JsonObject();
        o.addProperty( "enabled", true );
        o.addProperty( "name",  name );
        o.addProperty( "type",  type );
        o.addProperty( "value", value );
        return o;
    }

    private static String pretty( JsonArray a )
    {
        return new GsonBuilder().setPrettyPrinting().create().toJson( a );
    }
}
