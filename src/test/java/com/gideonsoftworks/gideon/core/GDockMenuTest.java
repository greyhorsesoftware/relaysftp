package com.gideonsoftworks.gideon.core;

import com.gideonsoftworks.gcore.GBookmark;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Pure and engine-level tests for {@link GDockMenu}; never touches Taskbar, PopupMenu or the static manager. */
class GDockMenuTest
{
    private static GBookmark bm( String name, String host )
    {
        return new GBookmark( name, host, "/", "user", "pw", 22, GBookmark.G_SFTP_PROTOCOL );
    }

    private static List<String> labels( List<GDockMenu.Entry> entries )
    {
        List<String> out = new ArrayList<>();
        for( GDockMenu.Entry e : entries ) out.add( e.label() );
        return out;
    }

    @Test
    void entries_preservesOrderAndLabels()
    {
        List<GDockMenu.Entry> e = GDockMenu.entries( List.of( bm( "Prod", "a.example" ), bm( "Staging", "b.example" ), bm( "Home NAS", "nas.local" ) ) );
        assertEquals( List.of( "Prod", "Staging", "Home NAS" ), labels( e ) );
        assertEquals( "a.example", e.get( 0 ).bookmark().getHostname() );
    }

    @Test
    void entries_disambiguatesDuplicateNames()
    {
        List<GDockMenu.Entry> e = GDockMenu.entries( List.of( bm( "Prod", "a.example" ), bm( "Prod", "b.example" ), bm( "Other", "c.example" ) ) );
        assertEquals( List.of( "Prod — a.example", "Prod — b.example", "Other" ), labels( e ) );
    }

    @Test
    void entries_fallsBackToUserHostPortWhenHostAlsoCollides()
    {
        GBookmark p1 = new GBookmark( "Prod", "a.example", "/", "alice", "pw", 22, GBookmark.G_SFTP_PROTOCOL );
        GBookmark p2 = new GBookmark( "Prod", "a.example", "/", "bob",   "pw", 2222, GBookmark.G_SFTP_PROTOCOL );
        assertEquals( List.of( "Prod — alice@a.example:22", "Prod — bob@a.example:2222" ), labels( GDockMenu.entries( List.of( p1, p2 ) ) ) );
    }

    @Test
    void entries_capsAtMax()
    {
        List<GBookmark> many = new ArrayList<>();
        for( int i = 0; i < GDockMenu.MAX_ENTRIES + 5; i++ ) many.add( bm( "bm" + i, "h" + i ) );
        List<GDockMenu.Entry> e = GDockMenu.entries( many );
        assertEquals( GDockMenu.MAX_ENTRIES, e.size() );
        assertEquals( "bm0", e.get( 0 ).label() );
        assertEquals( "bm" + ( GDockMenu.MAX_ENTRIES - 1 ), e.get( e.size() - 1 ).label() );
    }

    @Test
    void entries_emptyGivesDisabledPlaceholder()
    {
        List<GDockMenu.Entry> e = GDockMenu.entries( List.of() );
        assertEquals( 1, e.size() );
        assertNull( e.get( 0 ).bookmark() );
        assertEquals( GLocalized.NOBOOKMARKS, e.get( 0 ).label() );
    }

    @Test
    void bookmarksChanged_rebuildsFromSource() throws Exception
    {
        List<GBookmark> source = new ArrayList<>( List.of( bm( "One", "h1" ) ) );
        AtomicReference<List<GDockMenu.Entry>> seen = new AtomicReference<>();
        GDockMenu dock = new GDockMenu( () -> source, seen::set );

        SwingUtilities.invokeAndWait( dock::bm_bookmarksChanged );
        assertEquals( List.of( "One" ), labels( seen.get() ) );

        source.add( bm( "Two", "h2" ) );
        SwingUtilities.invokeAndWait( dock::bm_bookmarksChanged );
        assertEquals( List.of( "One", "Two" ), labels( seen.get() ) );
    }

    @Test
    void rename_rebuildsViaBookmarkListener_withoutConcurrentModification() throws Exception
    {
        GBookmark b = bm( "Old", "h1" );
        List<GBookmark> source = List.of( b );
        AtomicInteger sinkCalls = new AtomicInteger();
        AtomicReference<List<GDockMenu.Entry>> seen = new AtomicReference<>();
        GDockMenu dock = new GDockMenu( () -> source, e -> { seen.set( e ); sinkCalls.incrementAndGet(); } );

        SwingUtilities.invokeAndWait( dock::bm_bookmarksChanged );
        assertEquals( 1, sinkCalls.get() );

        // setName fires the bookmark's own listeners; the dock is one of them and must only rebuild
        SwingUtilities.invokeAndWait( () -> b.setName( "New" ) );
        assertEquals( 2, sinkCalls.get() );
        assertEquals( List.of( "New" ), labels( seen.get() ) );
    }

    @Test
    void bookmarksChanged_twice_registersOnce() throws Exception
    {
        GBookmark b = bm( "Only", "h1" );
        List<GBookmark> source = List.of( b );
        AtomicInteger sinkCalls = new AtomicInteger();
        GDockMenu dock = new GDockMenu( () -> source, e -> sinkCalls.incrementAndGet() );

        SwingUtilities.invokeAndWait( dock::bm_bookmarksChanged );
        SwingUtilities.invokeAndWait( dock::bm_bookmarksChanged );
        int before = sinkCalls.get();
        SwingUtilities.invokeAndWait( () -> b.setName( "Renamed" ) );
        assertEquals( before + 1, sinkCalls.get(), "a rename must reach the dock exactly once, not once per registration" );
    }

    @Test
    void rebuild_offEdt_hopsToEdt() throws Exception
    {
        AtomicReference<Boolean> onEdt = new AtomicReference<>();
        GDockMenu dock = new GDockMenu( () -> List.of( bm( "X", "h" ) ), e -> onEdt.set( SwingUtilities.isEventDispatchThread() ) );
        dock.rebuild();                                   // from the test thread
        SwingUtilities.invokeAndWait( () -> {} );         // drain the EDT
        assertEquals( Boolean.TRUE, onEdt.get() );
    }
}
