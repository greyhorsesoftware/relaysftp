package com.gideonsoftworks.gcore;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Dispatch policy of {@link GListenerList}: snapshot iteration, exceptions logged not propagated, real removal. */
class GListenerListTest
{
    interface L { void hit( String tag ); }

    private static void onEdt( Runnable r ) throws Exception { SwingUtilities.invokeAndWait( r ); }

    @Test
    void throwingListenerDoesNotStopTheOthers() throws Exception
    {
        GListenerList<L> list = new GListenerList<>();
        List<String> hits = new ArrayList<>();
        list.add( tag -> { throw new IllegalStateException( "boom" ); } );
        list.add( tag -> hits.add( "second:" + tag ) );
        onEdt( () -> list.dispatch( "test", l -> l.hit( "x" ) ) );
        assertEquals( List.of( "second:x" ), hits, "the listener after the throwing one must still run" );
    }

    @Test
    void selfRemovingListenerDoesNotSkipTheNext() throws Exception
    {
        GListenerList<L> list = new GListenerList<>();
        List<String> hits = new ArrayList<>();
        L[] self = new L[1];
        self[0] = tag -> { hits.add( "first" ); list.remove( self[0] ); };
        list.add( self[0] );
        list.add( tag -> hits.add( "second" ) );
        onEdt( () -> list.dispatch( "test", l -> l.hit( "x" ) ) );
        assertEquals( List.of( "first", "second" ), hits );
        assertEquals( 1, list.size(), "removal during dispatch really removes" );
    }

    @Test
    void listenerAddedDuringDispatchIsNotInvokedThatRound() throws Exception
    {
        GListenerList<L> list = new GListenerList<>();
        List<String> hits = new ArrayList<>();
        list.add( tag -> { hits.add( "first" ); list.add( t -> hits.add( "late" ) ); } );
        onEdt( () -> list.dispatch( "test", l -> l.hit( "x" ) ) );
        assertEquals( List.of( "first" ), hits );
        assertEquals( 2, list.size() );
    }

    @Test
    void addIsIdempotent_andRemoveOfUnknownIsNoop()
    {
        GListenerList<L> list = new GListenerList<>();
        L l = tag -> {};
        list.add( l );
        list.add( l );
        assertEquals( 1, list.size() );
        list.remove( tag -> {} );
        assertEquals( 1, list.size() );
        list.remove( l );
        assertEquals( 0, list.size() );
        assertThrows( NullPointerException.class, () -> list.add( null ) );
    }

    @Test
    void offEdtDispatchStillDelivers() throws Exception
    {
        // contract says EDT; a violation is logged, not fatal — listeners must still be called
        GListenerList<L> list = new GListenerList<>();
        List<String> hits = new ArrayList<>();
        list.add( tag -> hits.add( tag ) );
        list.dispatch( "test", l -> l.hit( "off-edt" ) );   // test thread
        assertEquals( List.of( "off-edt" ), hits );
    }
}
