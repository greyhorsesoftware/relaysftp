package com.gideonsoftworks.gideon.browser;

/**
 * Connection-state notifications from a {@link GBrowserPanelController}; register with
 * {@link GBrowserPanelController#addBrowserPanelListener}. All methods are invoked on the EDT and
 * default to no-ops, so a listener implements only what it needs.
 */
public interface GBrowserPanelListenerInterface
{
    /** A login job has been queued for the pane. */
    default void bpl_connecting( GBrowserPanelController panel ) {}

    /** The pane's connection is up and its first listing is shown. */
    default void bpl_connected( GBrowserPanelController panel ) {}

    /** The pane is about to disconnect; the connection is still valid at this point. */
    default void bpl_disconnecting( GBrowserPanelController panel ) {}
}
