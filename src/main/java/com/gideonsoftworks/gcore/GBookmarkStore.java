package com.gideonsoftworks.gcore;

import com.gideonsoftworks.gdo.*;
import com.google.gson.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure, static-state-free persistence for bookmarks: one {@code .relay} file (JSON) per bookmark
 * under a given directory. No static initializer and no app singletons, so every method here is
 * directly unit-testable with a temp dir. {@link GBookmarkManager} owns the live app state and
 * delegates the file + keychain-key mechanics here.
 */
public final class GBookmarkStore
{
    private GBookmarkStore() {}

    /** Per-bookmark file extension. */
    public static final String EXT = ".relay";

    // ---- file persistence ----

    /** Read every {@code *.relay} (one JSON bookmark each) from {@code dir}, name-sorted; skips malformed files. */
    public static List<GBookmark> load( File dir )
    {
        List<GBookmark> out = new ArrayList<>();
        File[] files = dir.listFiles( (d, n) -> n.toLowerCase().endsWith( EXT ) );
        if( files == null ) return out;
        Arrays.sort( files, (a, b) -> a.getName().compareToIgnoreCase( b.getName() ) );

        for( File f : files )
        {
            try {
                JsonObject obj = JsonParser.parseString( java.nio.file.Files.readString(
                    f.toPath(), StandardCharsets.UTF_8 ) ).getAsJsonObject();
                if( !obj.has( "name" ) || obj.get( "name" ).isJsonNull() )
                {
                    GAuditLog.error( "", "bookmark-load-skip", "missing 'name' in " + f.getName(), null );
                    continue;
                }
                out.add( fromJson( obj ) );
            } catch( Exception ignore ) { /* skip a malformed .relay rather than failing the whole load */ }
        }
        return out;
    }

    /** Write one {@code .relay} per bookmark into {@code dir}, pruning files left from deleted/renamed bookmarks. */
    public static void save( File dir, List<GBookmark> bookmarks ) throws IOException
    {
        dir.mkdirs();

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Set<String> written   = new HashSet<>();
        Set<String> usedNames = new HashSet<>();

        for( GBookmark bm : bookmarks )
        {
            String fileName = fileNameFor( bm, usedNames );
            GAtomicFileWriter.writeUtf8( new File( dir, fileName ), gson.toJson( toJson( bm ) ) );
            written.add( fileName.toLowerCase() );
        }

        // prune orphans (deleted or renamed bookmarks)
        File[] existing = dir.listFiles( (d, n) -> n.toLowerCase().endsWith( EXT ) );
        if( existing != null )
            for( File f : existing )
                if( !written.contains( f.getName().toLowerCase() ) ) f.delete();
    }

    /** A filesystem-safe, unique {@code .relay} filename derived from the bookmark name. */
    public static String fileNameFor( GBookmark bm, Set<String> usedNames )
    {
        String base = bm.getName() != null ? bm.getName().trim() : "";
        base = base.replaceAll( "[^a-zA-Z0-9 ._-]", "_" ).trim();
        if( base.isEmpty() ) base = "bookmark";

        String name = base;
        int n = 2;
        while( usedNames.contains( name.toLowerCase() ) ) name = base + " " + (n++);
        usedNames.add( name.toLowerCase() );
        return name + EXT;
    }

    // ---- JSON mapping ----

    /** Serialize one bookmark to its JSON object (the per-{@code .relay}-file payload). */
    public static JsonObject toJson( GBookmark cBM )
    {
        JsonObject obj = new JsonObject();

        obj.addProperty( "schemaVersion", 1 );
        obj.addProperty( "name", cBM.getName() );
        obj.addProperty( "protocol", cBM.getProtocol() );
        obj.addProperty( "hostname", cBM.getHostname() );
        obj.addProperty( "port", cBM.getPort() );
        obj.addProperty( "hfolder", cBM.getHomePath() != null ? cBM.getHomePath() : "" );
        obj.addProperty( "lfolder", cBM.getLocalHomePath() != null ? cBM.getLocalHomePath() : "" );
        obj.addProperty( "un", cBM.getUsername() != null ? cBM.getUsername() : "" );
        if( cBM.getNeverSavePassword() )
            obj.addProperty( "never_save_pw", true );

        if( cBM.getProtocol() == GBookmark.G_S3_PROTOCOL )
        {
            obj.addProperty( "s3ak", cBM.getS3AccessKey() != null ? cBM.getS3AccessKey() : "" );
            obj.addProperty( "s3reg", cBM.getS3Region() != null ? cBM.getS3Region() : "" );
            obj.addProperty( "s3ep", cBM.getS3EndpointUrl() != null ? cBM.getS3EndpointUrl() : "" );
            obj.addProperty( "s3am", cBM.getS3AuthMethod() != null ? cBM.getS3AuthMethod() : "" );
        }

        GDataArray fPaths = cBM.getFavoritePaths();
        if( fPaths.getItemCount() > 0 )
            obj.add( "favorites", fPaths.getJsonArray() );

        return obj;
    }

    /** Build a bookmark from one JSON object. (The password is loaded later from the keyring, not here.) */
    public static GBookmark fromJson( JsonObject obj )
    {
        String name     = obj.has( "name" ) && !obj.get( "name" ).isJsonNull() ? obj.get( "name" ).getAsString() : "";
        String host     = obj.has( "hostname" ) ? obj.get( "hostname" ).getAsString() : "";
        String hfolder  = obj.has( "hfolder" ) ? obj.get( "hfolder" ).getAsString() : null;
        String lfolder  = obj.has( "lfolder" ) ? obj.get( "lfolder" ).getAsString() : null;
        String un       = obj.has( "un" ) ? obj.get( "un" ).getAsString() : null;
        int port        = obj.has( "port" ) ? obj.get( "port" ).getAsInt() : 0;
        int protocol    = obj.has( "protocol" ) ? obj.get( "protocol" ).getAsInt() : 0;

        if( hfolder != null && hfolder.isEmpty() ) hfolder = null;
        if( lfolder != null && lfolder.isEmpty() ) lfolder = null;
        if( un != null && un.isEmpty() ) un = null;

        GBookmark nBM = new GBookmark( name, host, hfolder, un, null, port, protocol );
        nBM.setLocalHomePath( lfolder );

        if( obj.has( "s3ak" ) )  nBM.setS3AccessKey( obj.get( "s3ak" ).getAsString() );
        if( obj.has( "s3reg" ) ) nBM.setS3Region( obj.get( "s3reg" ).getAsString() );
        if( obj.has( "s3ep" ) )  nBM.setS3EndpointUrl( obj.get( "s3ep" ).getAsString() );
        if( obj.has( "s3am" ) )  nBM.setS3AuthMethod( obj.get( "s3am" ).getAsString() );

        if( obj.has( "never_save_pw" ) && obj.get( "never_save_pw" ).getAsBoolean() )
            nBM.setNeverSavePassword( true );

        if( obj.has( "favorites" ) )
        {
            JsonArray favArr = obj.getAsJsonArray( "favorites" );
            for( int j = 0; j < favArr.size(); j++ )
            {
                JsonObject favObj = favArr.get( j ).getAsJsonObject();
                nBM.addFavoritePath( new GBookmarkFavoritePath(
                    favObj.get( "FAVORITE_NAME" ).getAsString(),
                    favObj.get( "FAVORITE_PATH" ).getAsString() ) );
            }
        }

        return nBM;
    }

    // ---- keychain key identity ----

    /** The keychain key for a bookmark's credential: {@code pw:host:port:username}. */
    public static String passwordKey( GBookmark bm )
    {
        return GKeyringManager.passwordKey( bm.getHostname(), bm.getPort(), bm.getUsername() );
    }

    /** Anonymous connections (blank username) don't get a stored secret. */
    public static boolean isAnonymous( GBookmark bm )
    {
        String u = bm.getUsername();
        return u == null || u.isBlank();
    }
}
