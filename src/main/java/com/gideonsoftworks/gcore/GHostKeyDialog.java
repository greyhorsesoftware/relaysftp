//
//  GHostKeyDialog.java
//  Gideon
//
//  Default (interactive) host key prompt. gpp_connect runs on a background job
//  worker thread, and sshj's HostKeyVerifier callback fires there too, so the
//  dialog is marshalled onto the EDT with invokeAndWait and the user's choice
//  is returned to the verifier.
//

package com.gideonsoftworks.gcore;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Supplier;

class GHostKeyDialog implements GHostKeyPrompt
{
    @Override
    public Decision promptUnknownKey( String host, String keyType, String fingerprint )
    {
        String message =
            "The authenticity of host '" + host + "' can't be established.\n\n" +
            keyType + " key fingerprint is\n" + fingerprint + "\n\n" +
            "This host is not in your known_hosts file. Do you want to trust it?";

        String[] options = { "Accept & Save", "Accept Once", "Reject" };
        return onEdt(() -> {
            int choice = JOptionPane.showOptionDialog(
                null, message, "Unknown SSH Host Key",
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                null, options, options[2] /* default: Reject */);
            switch( choice )
            {
                case 0:  return Decision.ACCEPT_AND_SAVE;
                case 1:  return Decision.ACCEPT_ONCE;
                default: return Decision.REJECT;
            }
        });
    }

    @Override
    public Decision promptChangedKey( String host, String keyType, String fingerprint )
    {
        String message =
            "WARNING: REMOTE HOST IDENTIFICATION HAS CHANGED!\n\n" +
            "The " + keyType + " host key for '" + host + "' does not match the key\n" +
            "stored in your known_hosts file. This could mean a man-in-the-middle\n" +
            "attack, or the server's key may have been changed legitimately.\n\n" +
            "New fingerprint:\n" + fingerprint + "\n\n" +
            "Only accept if you are certain this change is expected.";

        String[] options = { "Reject", "Accept Once", "Accept & Replace" };
        return onEdt(() -> {
            int choice = JOptionPane.showOptionDialog(
                null, message, "SSH Host Key Changed",
                JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE,
                null, options, options[0] /* default/emphasized: Reject */);
            switch( choice )
            {
                case 2:  return Decision.ACCEPT_AND_SAVE;
                case 1:  return Decision.ACCEPT_ONCE;
                default: return Decision.REJECT;
            }
        });
    }

    /** Run the dialog on the EDT and return its result; reject on interruption/failure. */
    private static Decision onEdt( Supplier<Decision> body )
    {
        if( SwingUtilities.isEventDispatchThread() )
        {
            return body.get();
        }
        final Decision[] result = { Decision.REJECT };
        try {
            SwingUtilities.invokeAndWait(() -> result[0] = body.get());
        } catch( InterruptedException ie ) {
            Thread.currentThread().interrupt();
            return Decision.REJECT;
        } catch( InvocationTargetException e ) {
            return Decision.REJECT;
        }
        return result[0];
    }
}
