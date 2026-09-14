package com.gideonsoftworks.ui;

import com.gideonsoftworks.gideon.actions.GHelpAction;
import com.gideonsoftworks.gideon.actions.GUIAction;
import com.gideonsoftworks.gideon.core.GLinkOpener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Help-menu link actions open the right URL, captured through the GLinkOpener seam. */
@Tag("ui")
class HelpMenuUITest {

    private CapturingLinkOpener links;

    @BeforeEach
    void installLinkCapture() {
        links = new CapturingLinkOpener();
        GLinkOpener.setForTesting(links);
    }

    @AfterEach
    void restoreLinkOpener() {
        GLinkOpener.setForTesting(null);
    }

    @Test void helpMenu_homeAction_opensHomeUrl() {
        new GHelpAction("Home", GUIAction.GA_RELAYHOME).actionPerformed(null);
        assertEquals(1, links.urls.size(), "Home should open exactly one URL");
        assertEquals(GHelpAction.HOME_URL, links.urls.get(0));
        assertEquals(0, links.mails.size(), "Home should not open a mailto");
    }

    static class CapturingLinkOpener implements GLinkOpener {
        final List<String> urls = new ArrayList<>();
        final List<String> mails = new ArrayList<>();
        @Override public void openUrl(String url) { urls.add(url); }
        @Override public void openMail(String mailtoUri) { mails.add(mailtoUri); }
    }
}
