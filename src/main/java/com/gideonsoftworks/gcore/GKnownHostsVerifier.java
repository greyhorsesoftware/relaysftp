//
//  GKnownHostsVerifier.java
//  Gideon
//
//  SSH host key verification backed by an OpenSSH-format known_hosts file in
//  the app data directory. Replaces the old PromiscuousVerifier (which trusted
//  any key, leaving every SFTP session open to a man-in-the-middle).
//
//  Trust-on-first-use: an unknown key is offered to a GHostKeyPrompt, which may
//  accept-and-save (append to known_hosts), accept-once, or reject. A *changed*
//  key for a known host raises a strong prompt and is rejected unless the user
//  explicitly accepts and replaces it.
//

package com.gideonsoftworks.gcore;

import com.gideonsoftworks.gideon.core.GLocalized;
import net.schmizz.sshj.common.Buffer;
import net.schmizz.sshj.common.KeyType;
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Iterator;

class GKnownHostsVerifier extends OpenSSHKnownHosts
{
    /** Selects a fixed, non-interactive policy without a UI. */
    static final String POLICY_PROPERTY = "relaysftp.sftp.hostkey.policy";
    /** Overrides the known_hosts file location (used by tests). */
    static final String KNOWN_HOSTS_PROPERTY = "relaysftp.sftp.knownHosts";

    // Test seams: when set, take precedence over the property/data-dir defaults.
    private static volatile GHostKeyPrompt promptOverride;
    private static volatile File           knownHostsFileOverride;

    private final GHostKeyPrompt prompt;

    private GKnownHostsVerifier( File khFile, GHostKeyPrompt prompt ) throws IOException
    {
        super(khFile);
        this.prompt = prompt;
    }

    /** Build a verifier using the configured known_hosts file and prompt handler. */
    static GKnownHostsVerifier create() throws IOException
    {
        File khFile = knownHostsFile();
        File parent = khFile.getParentFile();
        if( parent != null && !parent.exists() )
        {
            parent.mkdirs();
        }
        return new GKnownHostsVerifier(khFile, resolvePrompt());
    }

    @Override
    protected boolean hostKeyUnverifiableAction( String hostname, PublicKey key )
    {
        GHostKeyPrompt.Decision d = prompt.promptUnknownKey(
            hostname, KeyType.fromKey(key).toString(), fingerprint(key));
        switch( d )
        {
            case ACCEPT_AND_SAVE:
                appendEntry(hostname, key);
                return true;
            case ACCEPT_ONCE:
                return true;
            case REJECT:
            default:
                return false;
        }
    }

    @Override
    protected boolean hostKeyChangedAction( String hostname, PublicKey key )
    {
        GHostKeyPrompt.Decision d = prompt.promptChangedKey(
            hostname, KeyType.fromKey(key).toString(), fingerprint(key));
        switch( d )
        {
            case ACCEPT_AND_SAVE:
                replaceEntry(hostname, key);
                return true;
            case ACCEPT_ONCE:
                return true;
            case REJECT:
            default:
                return false;
        }
    }

    /** Append a new known-hosts entry and persist the file. Best-effort. */
    private void appendEntry( String hostname, PublicKey key )
    {
        try {
            entries.add(new HostEntry(null, hostname, KeyType.fromKey(key), key));
            write();
        } catch( IOException e ) {
            // Trust for this session even if persisting failed; surface via the log.
        }
    }

    /** Drop any prior entry for this host+type, add the new key, and persist. */
    private void replaceEntry( String hostname, PublicKey key )
    {
        try {
            KeyType type = KeyType.fromKey(key);
            for( Iterator<KnownHostEntry> it = entries.iterator(); it.hasNext(); )
            {
                KnownHostEntry e = it.next();
                try {
                    if( e.appliesTo(type, hostname) ) it.remove();
                } catch( IOException ignore ) {}
            }
            entries.add(new HostEntry(null, hostname, type, key));
            write();
        } catch( IOException e ) {
            // Trust for this session even if persisting failed.
        }
    }

    /** Standard OpenSSH SHA-256 host key fingerprint (e.g. "SHA256:abc..."). */
    static String fingerprint( PublicKey key )
    {
        try {
            byte[] wire   = new Buffer.PlainBuffer().putPublicKey(key).getCompactData();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(wire);
            return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest);
        } catch( NoSuchAlgorithmException e ) {
            return "(fingerprint unavailable)";
        }
    }

    private static File knownHostsFile()
    {
        File override = knownHostsFileOverride;
        if( override != null ) return override;

        String prop = System.getProperty(KNOWN_HOSTS_PROPERTY);
        if( prop != null && !prop.isEmpty() ) return new File(prop);

        return new File(GLocalized.DATA_FOLDER_PATH + "known_hosts");
    }

    private static GHostKeyPrompt resolvePrompt()
    {
        GHostKeyPrompt override = promptOverride;
        if( override != null ) return override;

        String policy = System.getProperty(POLICY_PROPERTY);
        if( policy != null && !policy.isEmpty() ) return fixedPolicy(policy);

        return new GHostKeyDialog();
    }

    /** Non-interactive prompt driven by a policy name. */
    private static GHostKeyPrompt fixedPolicy( String policy )
    {
        final GHostKeyPrompt.Decision decision;
        switch( policy.trim().toLowerCase() )
        {
            case "accept":      decision = GHostKeyPrompt.Decision.ACCEPT_AND_SAVE; break;
            case "accept-once": decision = GHostKeyPrompt.Decision.ACCEPT_ONCE;     break;
            default:            decision = GHostKeyPrompt.Decision.REJECT;          break;
        }
        return new GHostKeyPrompt() {
            public Decision promptUnknownKey( String h, String t, String f ) { return decision; }
            public Decision promptChangedKey( String h, String t, String f ) { return decision; }
        };
    }

    // ---- test seams -------------------------------------------------------

    static void setPromptForTesting( GHostKeyPrompt p )       { promptOverride = p; }
    static void setKnownHostsFileForTesting( File f )         { knownHostsFileOverride = f; }
}
