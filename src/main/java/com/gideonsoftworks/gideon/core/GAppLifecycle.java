package com.gideonsoftworks.gideon.core;

import com.gideonsoftworks.gcore.GListenerList;

/** The app-wide lifecycle registry (replaces the shared event center's APP_WILL_QUIT). */
public final class GAppLifecycle
{
    private static final GListenerList<GAppLifecycleListenerInterface> listeners = new GListenerList<>();

    private GAppLifecycle() {}

    public static void addListener( GAppLifecycleListenerInterface l )    { listeners.add( l ); }
    public static void removeListener( GAppLifecycleListenerInterface l ) { listeners.remove( l ); }

    public static void fireAppWillQuit()
    {
        listeners.dispatch( "app-will-quit", GAppLifecycleListenerInterface::al_appWillQuit );
    }
}
