package com.gideonsoftworks.gideon.core;

import com.gideonsoftworks.gcore.GAuditLog;
import com.gideonsoftworks.gcore.GBookmark;
import com.gideonsoftworks.gcore.GBookmarkChangeListenerInterface;
import com.gideonsoftworks.gcore.GBookmarkManager;
import com.gideonsoftworks.gideon.bookmarks.GBookmarkUIAction;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.Taskbar;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The user's bookmarks in the macOS Dock icon menu (docs/1.8.0/DOCK_MENU_BOOKMARKS_PLAN.md).
 * Selecting one connects it exactly as the menu-bar Bookmarks item does. Rebuilt whenever a
 * bookmark is added, removed or renamed.
 *
 * <p>Three layers so the logic is testable without AWT or macOS: {@link #entries(List)} is pure;
 * the {@code (source, sink)} constructor is the rebuild engine over any source and any sink; and
 * {@link #install()} is the only code that touches {@link Taskbar} / {@link PopupMenu}.
 */
public final class GDockMenu implements GBookmarkChangeListenerInterface
{
    /** Bookmarks past this many are still reachable from the menu bar; the Dock menu stays short. */
    public static final int MAX_ENTRIES = 30;

    /** One Dock menu line: {@code bookmark} is null for the disabled "no bookmarks" placeholder. */
    public record Entry( String label, GBookmark bookmark ) {}

    private final Supplier<List<GBookmark>> source;
    private final Consumer<List<Entry>>     sink;
    private final List<GBookmark>           registered = new ArrayList<>();

    public GDockMenu( Supplier<List<GBookmark>> source, Consumer<List<Entry>> sink )
    {
        this.source = source;
        this.sink   = sink;
    }

    // ---- pure: bookmarks -> menu entries ----

    /**
     * Labels in list order, capped at {@link #MAX_ENTRIES}. Bookmarks that share a name are told
     * apart by host, and if still equal by user@host:port. An empty list yields the single disabled
     * placeholder.
     */
    public static List<Entry> entries( List<GBookmark> bookmarks )
    {
        List<Entry> out = new ArrayList<>();
        if( bookmarks == null || bookmarks.isEmpty() )
        {
            out.add( new Entry( GLocalized.NOBOOKMARKS, null ) );
            return out;
        }
        List<GBookmark> shown = bookmarks.size() > MAX_ENTRIES ? bookmarks.subList( 0, MAX_ENTRIES ) : bookmarks;

        Map<String, Integer> nameCount = new HashMap<>();
        for( GBookmark bm : shown ) nameCount.merge( nameOf( bm ), 1, Integer::sum );

        Map<String, Integer> hostLabelCount = new HashMap<>();
        for( GBookmark bm : shown )
            if( nameCount.get( nameOf( bm ) ) > 1 ) hostLabelCount.merge( nameOf( bm ) + " — " + bm.getHostname(), 1, Integer::sum );

        for( GBookmark bm : shown )
        {
            String name = nameOf( bm );
            String label = name;
            if( nameCount.get( name ) > 1 )
            {
                label = name + " — " + bm.getHostname();
                if( hostLabelCount.getOrDefault( label, 0 ) > 1 )
                    label = name + " — " + bm.getUsername() + "@" + bm.getHostname() + ":" + bm.getPort();
            }
            out.add( new Entry( label, bm ) );
        }
        return out;
    }

    private static String nameOf( GBookmark bm )
    {
        String n = bm.getName();
        return ( n == null || n.isEmpty() ) ? String.valueOf( bm.getHostname() ) : n;
    }

    // ---- rebuild engine ----

    /** Push the current entries to the sink, on the EDT. */
    public void rebuild()
    {
        if( SwingUtilities.isEventDispatchThread() ) sink.accept( entries( source.get() ) );
        else SwingUtilities.invokeLater( () -> sink.accept( entries( source.get() ) ) );
    }

    /** Add/remove: (re)register on every bookmark for rename notifications, then rebuild. */
    @Override
    public void bm_bookmarksChanged()
    {
        // GListenableItem is a plain list: remove-then-add keeps this listener registered once.
        for( GBookmark bm : registered ) bm.removeStatusListener( this );
        registered.clear();
        for( GBookmark bm : source.get() )
        {
            bm.addStatusListener( this );
            registered.add( bm );
        }
        rebuild();
    }

    /**
     * Rename (or any other setter): rebuild only. This is invoked from inside the bookmark's own
     * listener iteration, so it must never touch that listener list.
     */
    @Override
    public void bm_bookmarkChanged( GBookmark bm )
    {
        rebuild();
    }

    // ---- macOS / Taskbar layer ----

    /**
     * Install the Dock menu if this platform supports it. Returns false, with no side effects and
     * no dialog, on non-macOS platforms, in headless mode, or when the Taskbar API refuses.
     */
    public static boolean install()
    {
        if( !GLocalized.onMacOSX || GraphicsEnvironment.isHeadless() ) return false;
        try
        {
            if( !Taskbar.isTaskbarSupported() ) return false;
            Taskbar taskbar = Taskbar.getTaskbar();
            if( !taskbar.isSupported( Taskbar.Feature.MENU ) ) return false;

            Supplier<List<GBookmark>> source = () -> {
                List<GBookmark> all = new ArrayList<>();
                int n = GBookmarkManager.getBookmarkCount();
                for( int i = 0; i < n; i++ ) all.add( GBookmarkManager.getBookmark( i ) );
                return all;
            };
            Consumer<List<Entry>> sink = entries -> {
                PopupMenu menu = new PopupMenu();
                for( Entry e : entries )
                {
                    MenuItem item = new MenuItem( e.label() );
                    if( e.bookmark() == null ) item.setEnabled( false );
                    else item.addActionListener( ev -> GBookmarkUIAction.openBookmark( e.bookmark() ) );
                    menu.add( item );
                }
                taskbar.setMenu( menu );
            };

            GDockMenu dock = new GDockMenu( source, sink );
            GBookmarkManager.addStatusListener( dock );
            dock.bm_bookmarksChanged();
            return true;
        }
        catch( UnsupportedOperationException | SecurityException e )   // HeadlessException is an UnsupportedOperationException
        {
            GAuditLog.error( "", "dock-menu", null, e );
            return false;
        }
    }
}
