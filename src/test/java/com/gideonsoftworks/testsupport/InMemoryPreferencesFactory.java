package com.gideonsoftworks.testsupport;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/**
 * Test-only java.util.prefs backend that lives entirely in the test JVM's memory.
 *
 * GSFTPPreferences is backed by Preferences.userNodeForPackage(...), which on macOS
 * writes to the REAL ~/Library/Preferences plist - shared with the installed app. A
 * test that flips a pref (use-exec, monitor-remote, theme...) was therefore
 * mutating the developer's live app settings.
 *
 * Wired in via build.gradle: systemProperty 'java.util.prefs.PreferencesFactory'.
 * With forkEvery = 1 every test class gets a fresh, empty pref store.
 */
public class InMemoryPreferencesFactory implements PreferencesFactory {

    private static final Preferences userRoot = new InMemoryPreferences(null, "");
    private static final Preferences systemRoot = new InMemoryPreferences(null, "");

    @Override public Preferences userRoot() { return userRoot; }
    @Override public Preferences systemRoot() { return systemRoot; }

    private static final class InMemoryPreferences extends AbstractPreferences {

        private final Map<String, String> values = new ConcurrentHashMap<>();
        private final Map<String, InMemoryPreferences> children = new ConcurrentHashMap<>();

        InMemoryPreferences(InMemoryPreferences parent, String name) {
            super(parent, name);
        }

        @Override protected void putSpi(String key, String value) { values.put(key, value); }
        @Override protected String getSpi(String key) { return values.get(key); }
        @Override protected void removeSpi(String key) { values.remove(key); }
        @Override protected void removeNodeSpi() { values.clear(); }
        @Override protected String[] keysSpi() { return values.keySet().toArray(new String[0]); }
        @Override protected String[] childrenNamesSpi() { return children.keySet().toArray(new String[0]); }
        @Override protected AbstractPreferences childSpi(String name) {
            return children.computeIfAbsent(name, n -> new InMemoryPreferences(this, n));
        }
        @Override protected void syncSpi() { }
        @Override protected void flushSpi() { }
    }
}
