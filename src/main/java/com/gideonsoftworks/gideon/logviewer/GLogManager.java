package com.gideonsoftworks.gideon.logviewer;

import com.gideonsoftworks.gcore.GLog;
import java.util.*;

/**
 * Central registry for connection logs. Notifies listeners when logs
 * are added or removed so the log window can update its tabs.
 */
public class GLogManager {

    public interface Listener {
        void logAdded(String key, GLog log);
    }

    private static final GLogManager instance = new GLogManager();

    private final LinkedHashMap<String, GLog> logs = new LinkedHashMap<>();
    private final List<Listener> listeners = new ArrayList<>();

    private GLogManager() {}

    public static GLogManager shared() { return instance; }

    public synchronized void registerLog(String key, GLog log) {
        logs.put(key, log);
        for (Listener l : listeners) l.logAdded(key, log);
    }

    public synchronized Map<String, GLog> getLogs() {
        return new LinkedHashMap<>(logs);
    }

    public synchronized void addListener(Listener l) {
        listeners.add(l);
    }

    public synchronized void removeListener(Listener l) {
        listeners.remove(l);
    }
}
