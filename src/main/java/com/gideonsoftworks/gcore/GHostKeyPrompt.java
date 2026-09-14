//
//  GHostKeyPrompt.java
//  Gideon
//
//  Trust decision for SSH host key verification. The default implementation
//  (GHostKeyDialog) prompts the user with a Swing dialog; tests inject a
//  non-interactive implementation so the suite never pops a window.
//

package com.gideonsoftworks.gcore;

interface GHostKeyPrompt
{
    /** How the user (or an automated policy) chose to handle a host key. */
    enum Decision
    {
        /** Trust the key for this session and append it to the known_hosts file. */
        ACCEPT_AND_SAVE,
        /** Trust the key for this session only; do not persist it. */
        ACCEPT_ONCE,
        /** Refuse the key; the connection fails. */
        REJECT
    }

    /** The host key is not present in known_hosts (trust-on-first-use). */
    Decision promptUnknownKey( String host, String keyType, String fingerprint );

    /** A known host is now presenting a different key (possible MITM). */
    Decision promptChangedKey( String host, String keyType, String fingerprint );
}
