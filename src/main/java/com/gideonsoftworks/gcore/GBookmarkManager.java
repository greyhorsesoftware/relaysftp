//
//  GBookmarkManager.java
//  Gideon
//
//  Created by David on 8/26/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gcore;

import com.gideonsoftworks.gideon.core.GLocalized;
import java.io.*;
import com.gideonsoftworks.gcore.*;
import com.gideonsoftworks.gdo.*;
import java.util.ArrayList;
import java.util.*;

public class GBookmarkManager
{
    private static ArrayList                listeners               = new ArrayList();
    private static ArrayList               gbmBookmarks            = null;
    private static File                     bookmarksDir            = null;   // one .relay file per bookmark
    private static File                     localFavoritesFile      = null;
    private static GDataArray               sharedLocalFavoritePaths     = null;

    // load the bookmarks from file on disk
    static{

        try{

                gbmBookmarks = new ArrayList();

                // --- Bookmarks: one .relay file (JSON) per bookmark under Bookmarks/ ---
                bookmarksDir = new File( GLocalized.DATA_FOLDER_PATH + "Bookmarks" );

                if( bookmarksDir.isDirectory() )
                {
                    for( GBookmark bm : GBookmarkStore.load( bookmarksDir ) ) addBookmark( bm );
                    GAuditLog.lifecycle( "bookmarks-loaded count=" + gbmBookmarks.size() );
                }

                // --- Local Favorites ---
                localFavoritesFile = new File( GLocalized.DATA_FOLDER_PATH + "LocalFavoritePaths.json");

                if( localFavoritesFile.exists() )
                {
                    sharedLocalFavoritePaths = new GDataArray( localFavoritesFile,
                                                        GBookmarkFavoritePath.class.getName() );
                }
                else
                {
                    sharedLocalFavoritePaths = new GDataArray();
                }

        }catch( Exception e )
        {
            // Never brick the bookmark subsystem for the JVM lifetime (ExceptionInInitializerError):
            // log and degrade to an empty, usable state instead.
            GAuditLog.error( "", "bookmarks-load", null, e );
            if( gbmBookmarks == null )            gbmBookmarks = new ArrayList();
            if( sharedLocalFavoritePaths == null ) sharedLocalFavoritePaths = new GDataArray();
        }

    }

    // --- Password management (centralised, keyring-aware) ---

    private static boolean passwordsLoaded = false;
    private static java.util.HashSet<String> storedPasswordKeys = new java.util.HashSet<>();

    /**
     * Load all passwords from keyring (single prompt) and set them on bookmarks.
     * Called on first remote bookmark use, not at startup.
     */
    public static void loadPasswords()
    {
        if (passwordsLoaded) return;
        passwordsLoaded = true;

        GKeyringManager.loadAll();

        for (int i = 0; i < gbmBookmarks.size(); i++)
        {
            GBookmark bm = (GBookmark) gbmBookmarks.get(i);
            if (!canStoreSecret(bm)) continue;   // anonymous (blank user): no keychain secret
            String key = keyFor(bm);
            String pw = GKeyringManager.getPassword(key);
            if (pw != null)
            {
                bm.setPassword(pw);
                storedPasswordKeys.add(key);
            }
        }
    }

    /**
     * Get a bookmark's password, ensuring keyring passwords are loaded first. Skips the keyring read
     * (and its auth prompt) for local bookmarks and for anonymous ones — a blank username (e.g. entering
     * just a host for FTP) is treated as an anonymous login, which has no stored secret.
     */
    public static String getPassword(GBookmark bm)
    {
        if (!bm.isLocal() && canStoreSecret(bm)) loadPasswords();
        return bm.getPassword();
    }

    /**
     * Set a bookmark's password in memory (does not persist to keyring).
     */
    public static void setPassword(GBookmark bm, String pw)
    {
        bm.setPassword(pw);
    }

    /**
     * Returns true if this bookmark's password was loaded from the keyring.
     */
    public static boolean hasStoredPassword(GBookmark bm)
    {
        if (!canStoreSecret(bm)) return false;
        return storedPasswordKeys.contains(keyFor(bm));
    }

    private static String  keyFor(GBookmark bm)         { return GBookmarkStore.passwordKey(bm); }
    private static boolean canStoreSecret(GBookmark bm) { return !GBookmarkStore.isAnonymous(bm); }

    /**
     * Save a bookmark's password to the keyring immediately.
     */
    public static void savePassword(GBookmark bm)
    {
        String pw = getPassword(bm);
        if (pw == null || pw.isEmpty()) return;
        if (!canStoreSecret(bm)) return;   // anonymous (blank user): no keychain secret
        String key = keyFor(bm);
        GKeyringManager.setPassword(key, pw);
        storedPasswordKeys.add(key);
    }

    /**
     * Remove a bookmark's password from the keyring.
     */
    public static void deletePassword(GBookmark bm)
    {
        String key = keyFor(bm);
        GKeyringManager.deletePassword(key);
        storedPasswordKeys.remove(key);
    }

    public static GDataArray getSharedLocalFavoritePaths()
    {
        return sharedLocalFavoritePaths;
    }

    public static boolean hasBookmarks()
    {

        if( gbmBookmarks == null ) return false;

        if( gbmBookmarks.size() > 0 ) return true;

        return false;

    }

    public  static int getBookmarkCount()
    {

        if( gbmBookmarks == null ) return 0;

        return gbmBookmarks.size();

    }

    public  static GBookmark getBookmark( int i )
    {

        return (GBookmark)gbmBookmarks.get(i);

    }

    /** True if this exact bookmark instance is a saved (managed) bookmark — identity, not equality. */
    public  static boolean contains( GBookmark bm )
    {
        if( bm == null || gbmBookmarks == null ) return false;
        for( Object o : gbmBookmarks ) if( o == bm ) return true;
        return false;
    }

    public  static int addBookmark( GBookmark bm )
    {

        gbmBookmarks.add(bm);

        notifyListeners();

        return gbmBookmarks.indexOf(bm);

    }

    public  static void removeBookmark( GBookmark bm )
    {

        gbmBookmarks.remove(bm);

        // clean up keyring entry
        deletePassword(bm);

        notifyListeners();

    }


    public static void saveBookmarks()
    {
        // Local favorite paths — an empty list now writes an empty (versioned) array rather than
        // deleting the file, so a save is never a silent delete.
        sharedLocalFavoritePaths.writeToJsonFile(localFavoritesFile);

        try { GBookmarkStore.save( bookmarksDir, gbmBookmarks ); }
        catch( IOException e )
        {
            GAuditLog.error( "", "bookmarks-save", null, e );
            GAtomicFileWriter.showSaveError( "Failed to save bookmarks: " + e.getMessage() );
        }
    }

public static void addStatusListener(Object ml) {
   listeners.add(ml);
}

public static void removeStatusListener(Object ml) {
   listeners.remove(ml);
}

private static void notifyListeners()
{
     Iterator en = listeners.iterator();

      while (en.hasNext())
      {
            GBookmarkChangeListenerInterface listener = (GBookmarkChangeListenerInterface)en.next();
            listener.bm_bookmarksChanged();
      }
}

}
