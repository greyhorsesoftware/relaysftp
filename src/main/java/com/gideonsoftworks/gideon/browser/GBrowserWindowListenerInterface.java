package com.gideonsoftworks.gideon.browser;

/**
 * Window-level notifications from a {@link GBrowserWindow}; register with
 * {@link GBrowserWindow#addBrowserWindowListener}. All methods are invoked on the EDT and default
 * to no-ops.
 */
public interface GBrowserWindowListenerInterface
{
    /** The window has just been shown. */
    default void bwl_browserOpening( GBrowserWindow window ) {}

    /** The window is closing; stop timers and watchers that reference it. */
    default void bwl_browserClosing( GBrowserWindow window ) {}

    /** Keyboard focus moved to another pane; {@code keyPanel} is the pane that now has it. */
    default void bwl_keyPanelChanged( GBrowserWindow window, GBrowserPanelCommandInterface keyPanel ) {}
}
