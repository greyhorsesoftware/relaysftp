package com.gideonsoftworks.gideon.preferences;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * A password field that never reads the keychain to display. When a secret already exists it shows a fixed
 * row of bullets (a placeholder) and stays "clean"; only when the user actually edits it does it become
 * "dirty". Callers write the keychain only when {@link #isDirty()} — so an unchanged field never touches it.
 */
public class GSecretField extends JPasswordField
{
    /** Shown (as echo dots) to indicate "a secret is stored" without revealing or reading it. */
    private static final String PLACEHOLDER = "••••••••";

    private boolean dirty   = false;
    private boolean loading = false;

    public GSecretField()
    {
        setColumns( 16 );
        getDocument().addDocumentListener( new DocumentListener() {
            @Override public void insertUpdate( DocumentEvent e )  { mark(); }
            @Override public void removeUpdate( DocumentEvent e )  { mark(); }
            @Override public void changedUpdate( DocumentEvent e ) { mark(); }
            private void mark() { if( !loading ) dirty = true; }
        } );
    }

    /** Show bubbles iff a secret is stored — without reading it. Resets the dirty flag. */
    public void showPlaceholder( boolean hasSecret )
    {
        loading = true;
        setText( hasSecret ? PLACEHOLDER : "" );
        loading = false;
        dirty = false;
    }

    /** True once the user has edited the field since the last {@link #showPlaceholder}. */
    public boolean isDirty() { return dirty; }

    /** The typed secret (only meaningful when {@link #isDirty()}). */
    public String typed() { return new String( getPassword() ); }
}
