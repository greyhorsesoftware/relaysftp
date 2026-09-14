package com.gideonsoftworks.gcore;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Canonicalises the strings and permissions objects that repeat across a directory listing —
 * owner, group, human-readable size, {@link GPermissions} — so every {@link GFileItem} on a
 * connection shares one instance per distinct value instead of carrying its own copy
 * (docs/1.8.0/LISTING_STRING_POOL_PLAN.md). Name, path and raw listing line are unique per
 * item and are deliberately not pooled.
 *
 * <p>One pool per {@link GConnection}; bounded (oldest entry evicted past {@link #CAPACITY}) so a
 * pathological listing cannot grow it without limit. Methods are synchronized: listings on a
 * connection are already serialised, so contention is nil and the lock costs nothing.
 *
 * <p>Sharing {@link GPermissions} instances is safe because the class is immutable (fields are
 * assigned only in its constructors; the permissions editor copies before editing). Keep it so.
 */
public final class GListingPool
{
    /** Distinct values kept per map. Owners, groups, size strings and permission sets are all far below this. */
    static final int CAPACITY = 4096;

    private final Map<String, String> strings = new LinkedHashMap<>( 256, 0.75f, true )
    {
        @Override protected boolean removeEldestEntry( Map.Entry<String, String> e ) { return size() > CAPACITY; }
    };
    private final Map<String, GPermissions> permissions = new LinkedHashMap<>( 32, 0.75f, true )
    {
        @Override protected boolean removeEldestEntry( Map.Entry<String, GPermissions> e ) { return size() > CAPACITY; }
    };

    /** @return the pooled instance equal to {@code s} (storing {@code s} if it is new); null stays null. */
    public synchronized String canonical( String s )
    {
        if( s == null ) return null;
        String existing = strings.get( s );
        if( existing != null ) return existing;
        strings.put( s, s );
        return s;
    }

    /** @return the pooled instance with the same start character and bit field as {@code p}; null stays null. */
    public synchronized GPermissions canonical( GPermissions p )
    {
        if( p == null ) return null;
        String key = p.getStartChar() + '|' + p.getBitField();
        GPermissions existing = permissions.get( key );
        if( existing != null ) return existing;
        permissions.put( key, p );
        return p;
    }

    /** Replace the item's owner, group, human-readable size and permissions with pooled instances. */
    public void canonicalize( GFileItem fi )
    {
        if( fi == null ) return;
        String owner = fi.getOwner();
        if( owner != null ) fi.setOwner( canonical( owner ) );
        String group = fi.getGroup();
        if( group != null ) fi.setGroup( canonical( group ) );
        String size = fi.getHumanReadableSize();
        if( size != null && !size.isEmpty() ) fi.setHumanReadableSize( canonical( size ) );
        GPermissions perms = fi.getPermissions();
        if( perms != null ) fi.setPermissions( canonical( perms ) );
    }

    /** Number of distinct strings currently pooled (for tests and diagnostics). */
    synchronized int stringCount() { return strings.size(); }

    /** Number of distinct permission sets currently pooled (for tests and diagnostics). */
    synchronized int permissionCount() { return permissions.size(); }
}
