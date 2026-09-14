package com.gideonsoftworks.gcore;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Listing memory / parse-time benchmark (docs/1.8.0/LISTING_STRING_POOL_PLAN.md step 0).
 * Excluded from the default run (tag "perf"); run with
 * {@code ./gradlew test --tests '*GListingMemoryBench*' -PincludePerf}. Prints, per size:
 * parse time per entry, retained heap per {@link GFileItem}, and how much of the retained
 * string data is duplicated (owner / group / human-readable size / permissions).
 *
 * Not an assertion-bearing test: numbers are for comparison before/after a change, so the only
 * assertion is that parsing produced the expected number of items.
 */
@Tag("perf")
class GListingMemoryBench
{
    static final String[] OWNERS = { "root", "www-data", "david", "deploy", "backup" };
    static final String[] GROUPS = { "root", "www-data", "staff", "users" };
    static final String[] PERMS  = { "-rw-r--r--", "-rw-rw-r--", "drwxr-xr-x", "-rwxr-xr-x", "drwxrwxr-x", "-rw-------" };
    static final String[] MONTHS = { "Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec" };

    static List<String> lines( int n )
    {
        Random r = new Random( 42 );
        List<String> out = new ArrayList<>( n );
        for( int i = 0; i < n; i++ )
        {
            String perm  = PERMS[ r.nextInt( PERMS.length ) ];
            String owner = OWNERS[ r.nextInt( OWNERS.length ) ];
            String group = GROUPS[ r.nextInt( GROUPS.length ) ];
            long size = perm.charAt( 0 ) == 'd' ? 4096 : ( r.nextInt( 100 ) < 70 ? r.nextInt( 50_000 ) : r.nextInt( 200_000_000 ) );
            String date = MONTHS[ r.nextInt( 12 ) ] + " " + String.format( "%2d", 1 + r.nextInt( 28 ) ) + ( r.nextBoolean() ? "  2025" : " 14:2" + r.nextInt( 10 ) );
            String name = "/srv/site/assets/dir" + ( i % 300 ) + "/file_" + i + ( perm.charAt( 0 ) == 'd' ? "" : ".jpg" );
            out.add( String.format( "%s %3d %-8s %-8s %10d %s %s", perm, 1 + r.nextInt( 3 ), owner, group, size, date, name ) );
        }
        return out;
    }

    static long usedHeap()
    {
        for( int i = 0; i < 4; i++ ) { System.gc(); try { Thread.sleep( 60 ); } catch( InterruptedException ignore ) {} }
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    /** Approximate retained bytes of one Latin-1 String (object + byte[]), 8-byte aligned. */
    static long strBytes( String s )
    {
        if( s == null ) return 0;
        long arr = ( 16 + s.length() + 7 ) & ~7L;
        return 24 + arr;
    }

    static List<GFileItem> parseAll( List<String> lines ) throws Exception
    {
        GListParserUNIX p = new GListParserUNIX();
        List<GFileItem> items = new ArrayList<>( lines.size() );
        for( String l : lines )
        {
            GFileItem fi = p.parseLine( l );
            if( fi != null ) items.add( fi );
        }
        return items;
    }

    @Test
    void measure() throws Exception
    {
        PrintStream realOut = System.out;
        StringBuilder report = new StringBuilder( "\n=== Listing memory benchmark ===\n" );
        for( int n : new int[]{ 2_000, 50_000, 200_000 } )
        {
            List<String> lines = lines( n );

            // parser output is discarded during measurement (a debug print would dominate the timing)
            System.setOut( new PrintStream( OutputStream.nullOutputStream() ) );
            try
            {
                for( int i = 0; i < 2; i++ ) parseAll( lines.subList( 0, Math.min( n, 10_000 ) ) );   // JIT warm-up

                long t0 = System.nanoTime();
                List<GFileItem> items = parseAll( lines );
                long tParse = System.nanoTime() - t0;
                if( items.size() != n ) throw new AssertionError( "parsed " + items.size() + " of " + n );

                items = null;
                long base = usedHeap();
                items = parseAll( lines );
                long heap = usedHeap() - base;

                long ownerB = 0, groupB = 0, sizeB = 0, permB = 0, nameB = 0, rawB = 0;
                Set<String> owners = new HashSet<>(), groups = new HashSet<>(), sizes = new HashSet<>(), perms = new HashSet<>();
                for( GFileItem fi : items )
                {
                    ownerB += strBytes( fi.getOwner() );            owners.add( fi.getOwner() );
                    groupB += strBytes( fi.getGroup() );            groups.add( fi.getGroup() );
                    sizeB  += strBytes( fi.getHumanReadableSize() ); sizes.add( fi.getHumanReadableSize() );
                    GPermissions gp = fi.getPermissions();
                    permB  += 24 + strBytes( gp.getStartChar() );    perms.add( gp.getStartChar() + gp.getBitField() );
                    nameB  += strBytes( fi.getName() );
                    rawB   += strBytes( fi.getRaw() );
                }
                // identity sharing: how many distinct String instances back the owner field?
                Set<String> ownerInstances = java.util.Collections.newSetFromMap( new java.util.IdentityHashMap<>() );
                Set<GPermissions> permInstances = java.util.Collections.newSetFromMap( new java.util.IdentityHashMap<>() );
                for( GFileItem fi : items ) { ownerInstances.add( fi.getOwner() ); permInstances.add( fi.getPermissions() ); }

                report.append( String.format( "N=%,d: parse %.1f us/item | retained %,d KB (%d B/item)%n",
                    n, tParse / 1000.0 / n, heap / 1024, heap / n ) );
                report.append( String.format( "   owner %,d KB (%d distinct, %d instances) | group %,d KB (%d distinct) | size %,d KB (%d distinct) | perms %,d KB (%d distinct, %d instances)%n",
                    ownerB / 1024, owners.size(), ownerInstances.size(), groupB / 1024, groups.size(), sizeB / 1024, sizes.size(),
                    permB / 1024, perms.size(), permInstances.size() ) );
                report.append( String.format( "   not pool-able: name %,d KB | raw line %,d KB%n", nameB / 1024, rawB / 1024 ) );

                // What GListParser.parseList / GConnection.fastListRecursive do to every item: run the
                // same items through one connection-scoped pool and measure again.
                // Drop every reference to the pre-pool instances first, or the identity sets above
                // keep the old strings and permissions alive and the pooled figure is meaningless.
                ownerInstances.clear(); permInstances.clear(); owners.clear(); groups.clear(); sizes.clear(); perms.clear();
                GListingPool pool = new GListingPool();
                long t1 = System.nanoTime();
                for( GFileItem fi : items ) pool.canonicalize( fi );
                long tPool = System.nanoTime() - t1;
                long heapPooled = usedHeap() - base;
                for( GFileItem fi : items ) { ownerInstances.add( fi.getOwner() ); permInstances.add( fi.getPermissions() ); }
                report.append( String.format( "   pooled (as listings are): retained %,d KB (%d B/item), pool pass %.2f us/item, owner instances %d, perms instances %d%n",
                    heapPooled / 1024, heapPooled / n, tPool / 1000.0 / n, ownerInstances.size(), permInstances.size() ) );
                items = null;
            }
            finally
            {
                System.setOut( realOut );
            }
        }
        realOut.print( report );
    }
}
