package com.gideonsoftworks.gcore;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A small typed listener registry with the dispatch policy the old reflection-based event
 * center lacked (docs/1.8.0/EVENT_CENTER_REPLACEMENT_PLAN.md):
 * <ul>
 *   <li>dispatch iterates a <em>snapshot</em>, so a listener that removes itself mid-dispatch
 *       never causes the next one to be skipped (the same fix {@code GJobManager.notifyModified}
 *       got in 1.7.2);</li>
 *   <li>a listener that throws is logged through {@link GAuditLog} and does not stop the
 *       remaining listeners (the event center returned from inside its loop on any exception);</li>
 *   <li>listeners are invoked on the EDT by contract; a dispatch from another thread is logged
 *       once as an error rather than thrown, so a future off-EDT caller is visible, not fatal.</li>
 * </ul>
 * {@code add}/{@code remove} are synchronized; {@code remove} really removes.
 */
public final class GListenerList<T>
{
    private final List<T> listeners = new ArrayList<>();

    public synchronized void add( T listener )
    {
        if( listener == null ) throw new NullPointerException( "GListenerList: null listener" );
        if( !listeners.contains( listener ) ) listeners.add( listener );
    }

    public synchronized void remove( T listener )
    {
        listeners.remove( listener );
    }

    public synchronized int size()
    {
        return listeners.size();
    }

    /**
     * Invoke {@code call} on every listener registered at the moment of the call.
     * @param context short label for the audit log when a listener throws, e.g. "browser-panel connected"
     */
    public void dispatch( String context, Consumer<T> call )
    {
        List<T> snapshot;
        synchronized( this ) { snapshot = new ArrayList<>( listeners ); }

        if( !SwingUtilities.isEventDispatchThread() )
            GAuditLog.error( "", context, null, new IllegalStateException( "listener dispatch off the EDT" ) );

        for( T l : snapshot )
        {
            try { call.accept( l ); }
            catch( RuntimeException t ) { GAuditLog.error( "", context, null, t ); }
        }
    }
}
