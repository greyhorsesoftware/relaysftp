package com.gideonsoftworks.gideon.core;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import com.formdev.flatlaf.util.SystemInfo;
import com.gideonsoftworks.gideon.preferences.GSFTPPreferences;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.UIManager;

/**
 * Catalog of the FlatLaf <b>base</b> themes (all Apache-2.0, bundled in flatlaf core — no third-party
 * theme licensing), plus apply/persist and a "Theme" submenu for the Window menu. Switching is live
 * (no restart) and the choice is remembered across launches via {@link GSFTPPreferences}.
 *
 * The {@code auto} entry (macOS only, and the default there) follows the system light/dark appearance
 * and switches live. On macOS the native title bar is matched to the theme via the app appearance.
 */
public final class GTheme
{
    private GTheme() {}

    public static final class Entry
    {
        public final String  id;
        public final String  name;
        public final String  lafClass;   // null for "auto"
        public final boolean dark;
        Entry( String id, String name, String lafClass, boolean dark )
        { this.id = id; this.name = name; this.lafClass = lafClass; this.dark = dark; }
    }

    public static final String AUTO_ID = "auto";

    public static final Entry[] THEMES = {
        new Entry( "light",     "Light",          "com.formdev.flatlaf.FlatLightLaf",           false ),
        new Entry( "dark",      "Dark",           "com.formdev.flatlaf.FlatDarkLaf",            true  ),
        new Entry( "intellij",  "IntelliJ Light", "com.formdev.flatlaf.FlatIntelliJLaf",        false ),
        new Entry( "darcula",   "Darcula",        "com.formdev.flatlaf.FlatDarculaLaf",         true  ),
        new Entry( "mac-light", "macOS Light",    "com.formdev.flatlaf.themes.FlatMacLightLaf", false ),
        new Entry( "mac-dark",  "macOS Dark",     "com.formdev.flatlaf.themes.FlatMacDarkLaf",  true  ),
        new Entry( AUTO_ID,     "Auto (follow system)", null,                                   false ),
    };


    public static Entry byId( String id )
    {
        if( id != null )
            for( Entry e : THEMES ) if( e.id.equals( id ) ) return e;
        return THEMES[0];   // Light
    }

    /** The active theme: the saved choice, or the platform default (Auto on macOS, Light elsewhere). */
    public static Entry current()
    {
        String id = GSFTPPreferences.getTheme();
        if( id == null ) id = SystemInfo.isMacOS ? AUTO_ID : "light";
        return byId( id );
    }

    /** Apply the saved theme at startup — call before any window is created (replaces FlatLightLaf.setup()). */
    public static void applyStartup()
    {
        Entry e = current();
        if( AUTO_ID.equals( e.id ) ) applyAuto();
        else                         setLaf( e );
    }

    /** Switch theme at runtime: persist the choice and re-skin every open window (FlatLaf crossfade). */
    public static void apply( Entry e )
    {
        GSFTPPreferences.setTheme( e.id );
        FlatAnimatedLafChange.showSnapshot();
        if( AUTO_ID.equals( e.id ) ) applyAuto();
        else                         setLaf( e );
        FlatLaf.updateUI();
        FlatAnimatedLafChange.hideSnapshotWithAnimation();
    }

    // The native macOS title bar follows the system appearance via the launcher's
    // -Dapple.awt.application.appearance=system (set at JVM init, where it actually takes effect).

    // --- Auto: match the macOS system appearance at launch ---
    // Resolved once at startup. The native title bar follows the system live (via the launcher's
    // -Dapple.awt.application.appearance=system); a later system light/dark switch is reflected in the
    // app content on the next launch.

    private static void applyAuto()
    {
        if( SystemInfo.isMacOS )
        {
            System.setProperty( "apple.awt.application.appearance", "system" );   // native title bar follows system
            setLaf( byId( systemIsDark() ? "mac-dark" : "mac-light" ) );
        }
        else
        {
            setLaf( byId( "light" ) );   // no system-appearance hook off macOS
        }
    }

    private static boolean systemIsDark()
    {
        try {
            Process p = new ProcessBuilder( "defaults", "read", "-g", "AppleInterfaceStyle" ).start();
            String out = new String( p.getInputStream().readAllBytes() ).trim();
            p.waitFor();
            return out.equalsIgnoreCase( "Dark" );
        } catch( Exception e ) {
            return false;
        }
    }

    private static void setLaf( Entry e )
    {
        try { UIManager.setLookAndFeel( e.lafClass ); }
        catch( Exception ex ) { FlatLightLaf.setup(); }
    }

    /** A "Theme" submenu: one radio item per theme, the active one checked; selecting applies live. */
    public static JMenu buildMenu()
    {
        JMenu menu = new JMenu( "Theme" );
        ButtonGroup group = new ButtonGroup();
        String currentId = current().id;
        for( Entry e : THEMES )
        {
            if( AUTO_ID.equals( e.id ) && !SystemInfo.isMacOS ) continue;   // Auto follows macOS appearance only
            JRadioButtonMenuItem item = new JRadioButtonMenuItem( e.name, e.id.equals( currentId ) );
            item.addActionListener( ev -> apply( e ) );
            group.add( item );
            menu.add( item );
        }
        return menu;
    }
}
