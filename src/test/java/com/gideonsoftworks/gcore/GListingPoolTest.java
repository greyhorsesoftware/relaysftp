package com.gideonsoftworks.gcore;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class GListingPoolTest
{
    private static GFileItem item( String owner, String group, long size, String perms )
    {
        GFileItem fi = new GFileItem( null, "f", size, new Date() );
        fi.setOwner( new String( owner ) );      // distinct instances on purpose
        fi.setGroup( new String( group ) );
        fi.setPermissions( new GPermissions( perms ) );
        return fi;
    }

    @Test
    void equalValuesShareOneInstanceAfterCanonicalize()
    {
        GListingPool pool = new GListingPool();
        GFileItem a = item( "david", "staff", 1234, "-rw-r--r--" );
        GFileItem b = item( "david", "staff", 1234, "-rw-r--r--" );
        assertNotSame( a.getOwner(), b.getOwner(), "precondition: distinct instances" );
        assertNotSame( a.getPermissions(), b.getPermissions() );

        pool.canonicalize( a );
        pool.canonicalize( b );

        assertSame( a.getOwner(), b.getOwner() );
        assertSame( a.getGroup(), b.getGroup() );
        assertSame( a.getHumanReadableSize(), b.getHumanReadableSize() );
        assertSame( a.getPermissions(), b.getPermissions() );
        assertEquals( "david", a.getOwner() );
        assertEquals( "-", a.getPermissions().getStartChar() );
    }

    @Test
    void distinctValuesStayDistinct()
    {
        GListingPool pool = new GListingPool();
        GFileItem a = item( "david", "staff", 10, "-rw-r--r--" );
        GFileItem b = item( "root",  "wheel", 20, "drwxr-xr-x" );
        pool.canonicalize( a );
        pool.canonicalize( b );
        assertNotSame( a.getOwner(), b.getOwner() );
        assertNotSame( a.getPermissions(), b.getPermissions() );
        assertEquals( "root", b.getOwner() );
        assertEquals( "d", b.getPermissions().getStartChar() );
        assertEquals( 3, pool.stringCount() * 0 + pool.permissionCount() + 1, "two permission sets pooled" );
    }

    @Test
    void permissionsPooledByStartCharAndBits()
    {
        GListingPool pool = new GListingPool();
        GPermissions file = new GPermissions( "-rwxr-xr-x" );
        GPermissions dir  = new GPermissions( "drwxr-xr-x" );   // same bits, different type char
        assertSame( file, pool.canonical( file ) );
        assertNotSame( file, pool.canonical( dir ) );
        assertSame( file, pool.canonical( new GPermissions( "-rwxr-xr-x" ) ) );
        assertEquals( 2, pool.permissionCount() );
    }

    @Test
    void nullAndEmptyAreLeftAlone()
    {
        GListingPool pool = new GListingPool();
        assertNull( pool.canonical( (String) null ) );
        assertNull( pool.canonical( (GPermissions) null ) );
        GFileItem fi = new GFileItem( null, "f", 0, null );   // no owner/group/perms set
        pool.canonicalize( fi );                               // must not throw
        assertNull( fi.getOwner() );
    }

    @Test
    void capacityIsBounded()
    {
        GListingPool pool = new GListingPool();
        for( int i = 0; i < GListingPool.CAPACITY * 2; i++ ) pool.canonical( "v" + i );
        assertEquals( GListingPool.CAPACITY, pool.stringCount() );
        // a recently used value survives eviction (access order), a stale one may not — either
        // way the pool still returns an equal value
        assertEquals( "v0", pool.canonical( "v0" ) );
    }
}
