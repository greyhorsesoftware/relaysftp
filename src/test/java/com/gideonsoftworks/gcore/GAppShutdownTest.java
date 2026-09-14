package com.gideonsoftworks.gcore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GAppShutdownTest
{
    @AfterEach void reset() { GAppShutdown.reset(); }

    @Test
    void beginFlipsTheAppWideFlag()
    {
        assertFalse( GAppShutdown.isShuttingDown() );
        GAppShutdown.begin();
        assertTrue( GAppShutdown.isShuttingDown() );
    }

    @Test
    void watchdogFiresAfterTheDeadline_notBefore() throws Exception
    {
        CountDownLatch fired = new CountDownLatch( 1 );
        long t0 = System.currentTimeMillis();
        Thread t = GAppShutdown.startWatchdog( 150, fired::countDown );
        assertTrue( t.isDaemon(), "must not keep the JVM alive" );
        assertFalse( fired.await( 60, TimeUnit.MILLISECONDS ), "fired too early" );
        assertTrue( fired.await( 2, TimeUnit.SECONDS ), "never fired" );
        assertTrue( System.currentTimeMillis() - t0 >= 140 );
    }

    @Test
    void disconnectAllWithNothingOpenReturnsImmediately()
    {
        long t0 = System.currentTimeMillis();
        assertEquals( 0, GAppShutdown.disconnectAll( 2_000 ) );
        assertTrue( System.currentTimeMillis() - t0 < 500 );
    }
}
