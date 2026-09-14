package com.gideonsoftworks.gideon.core;

import java.awt.Desktop;
import java.net.URI;

/**
 * Indirection around Desktop.browse / Desktop.mail so tests can capture link
 * clicks without actually launching the user's browser or mail client.
 */
public interface GLinkOpener {

    void openUrl(String url);

    void openMail(String mailtoUri);

    GLinkOpener DESKTOP = new GLinkOpener() {
        @Override public void openUrl(String url) {
            try { Desktop.getDesktop().browse(URI.create(url)); }
            catch (Exception ignore) {}
        }
        @Override public void openMail(String mailtoUri) {
            try { Desktop.getDesktop().mail(URI.create(mailtoUri)); }
            catch (Exception ignore) {}
        }
    };

    static GLinkOpener shared() {
        return Holder.current;
    }

    static void setForTesting(GLinkOpener opener) {
        Holder.current = opener != null ? opener : DESKTOP;
    }

    final class Holder {
        static GLinkOpener current = DESKTOP;
        private Holder() {}
    }
}
