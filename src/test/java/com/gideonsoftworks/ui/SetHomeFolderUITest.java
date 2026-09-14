package com.gideonsoftworks.ui;

import com.gideonsoftworks.gcore.GBookmark;
import com.gideonsoftworks.gcore.GBookmarkManager;
import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.preferences.GSFTPPreferences;
import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * "Set as Home Folder" (docs/1.8.0/SET_HOME_FOLDER_PLAN.md): remote pane -> the saved bookmark's
 * remote path, persisted at once, and honoured on the next connect; local pane -> the global
 * default local folder preference; disabled for a file selection and for an unsaved bookmark.
 *
 * Each case clicks a row at most once before opening the menu: two clicks on the same folder row
 * inside the double-click interval navigate into it, and the right-click then lands on "..".
 */
@Tag("ui")
class SetHomeFolderUITest extends UITestBase {

    private GBookmark added;   // the live bookmark once registered with the manager, for cleanup

    @BeforeAll
    void connect() throws Exception {
        loginToServer();
    }

    @AfterEach
    void cleanup() throws Exception {
        if (added != null) { GBookmarkManager.removeBookmark(added); added = null; }
        clear(serverRoot);
        clear(localRoot);
    }

    private static void clear(Path root) throws IOException {
        if (root == null || !Files.isDirectory(root)) return;
        try (var s = Files.walk(root)) {
            s.sorted(Comparator.reverseOrder()).filter(p -> !p.equals(root))
             .forEach(p -> { try { Files.delete(p); } catch (IOException ignore) {} });
        }
    }

    private void refreshRemote() throws Exception {
        JobWaiter w = waitForJob(GJobDescription.REFRESH_JOB_TYPE);
        SwingUtilities.invokeLater(() -> remoteBrowser().refresh(null));
        w.await(15);
        robot.waitForIdle();
    }

    private static JMenuItem itemIn(JPopupMenu menu, String text) {
        for (java.awt.Component c : menu.getComponents())
            if (c instanceof JMenuItem mi && text.equals(mi.getText())) return mi;
        return null;
    }

    /**
     * One real click on the row: selects it and makes the pane key, which is what validation
     * evaluates against. Goes through clickRow so the window is active first — under Xvfb a
     * click on an inactive window is swallowed and the selection stays empty (CI flake).
     */
    private void selectRow(JTableFixture table, int row) throws Exception {
        assertTrue(row >= 0, "row to select");
        clickRow(table, row);
    }

    /** The remote pane's context menu for the current selection; fails clearly when nothing is selected. */
    private JPopupMenu remoteMenuForSelection() {
        JPopupMenu menu = remoteBrowser().getPopupMenuForSelection();
        assertNotNull(menu, "no context menu: the click did not select a row (selected rows: "
            + java.util.Arrays.toString(remoteBrowser().getFileTable().getSelectedRows()) + ")");
        return menu;
    }

    @Test
    void remoteFolder_becomesBookmarkHome_andNextConnectLandsThere() throws Exception {
        createServerDir("sub");
        refreshRemote();
        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
        waitForRowPresent(table, "sub", 10);
        int row = findRowByName(table, "sub");
        assertTrue(row >= 0);

        // Save the login bookmark (what Add Bookmark does: the same instance joins the manager).
        GBookmark bm = remoteBrowser().getBookmark();
        assertNotNull(bm);
        GBookmarkManager.addBookmark(bm);
        added = bm;

        rightClickAndSelectMenuItem(table, row, GLocalized.SET_HOME_FOLDER);
        robot.waitForIdle();

        assertTrue(bm.getHomePath() != null && bm.getHomePath().endsWith("/sub"), "home path: " + bm.getHomePath());

        // Persisted immediately: some .relay file under the data dir names the new path.
        Path dataDir = Path.of(System.getProperty("relaysftp.dataDir"));
        boolean persisted;
        try (var s = Files.walk(dataDir)) {
            persisted = s.filter(p -> p.toString().endsWith(".relay")).anyMatch(p -> {
                try { return Files.readString(p).contains("/sub"); } catch (IOException e) { return false; }
            });
        }
        assertTrue(persisted, "bookmark file should already contain the new home path");

        // Reconnect: the pane lands in the new home folder. Disconnect is a queued job; wait for it
        // so the pane is back on its login card before the bookmark is offered again.
        JobWaiter disconnected = waitForJob(GJobDescription.DISCONNECT_JOB_TYPE);
        SwingUtilities.invokeAndWait(() -> browserWindow.disconnectBrowser(remoteBrowser()));
        disconnected.await(15);
        robot.waitForIdle();
        JobWaiter login = waitForJob(GJobDescription.LOGIN_JOB_TYPE);
        SwingUtilities.invokeAndWait(() -> remoteBrowser().connectForBookmark(bm));
        robot.waitForIdle();
        try {
            window.button("connectButton").click();          // login card shown: confirm it
        } catch (org.assertj.swing.exception.ComponentLookupException alreadyConnecting) {
            // a saved bookmark may connect without the login card
        }
        login.await(15);
        robot.waitForIdle();
        String cwd = remoteBrowser().getVirtualCWD().getCompletePath();
        assertTrue(cwd.endsWith("/sub"), "expected to land in /sub, got " + cwd);

        // Leave the pane at the server root for the other tests.
        bm.setHomePath(null);
    }

    @Test
    void unsavedBookmark_disablesTheItem() throws Exception {
        createServerDir("sub2");
        refreshRemote();
        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
        waitForRowPresent(table, "sub2", 10);
        GBookmark bm = remoteBrowser().getBookmark();
        assertFalse(GBookmarkManager.contains(bm), "precondition: login bookmark is not managed");
        selectRow(table, findRowByName(table, "sub2"));
        JMenuItem item = itemIn(remoteMenuForSelection(), GLocalized.SET_HOME_FOLDER);
        assertNotNull(item, "context menu should carry the item");
        assertFalse(item.isEnabled(), "unsaved bookmark: nowhere to persist a home path");
    }

    @Test
    void fileSelection_disablesTheItem() throws Exception {
        createServerFile("plain.txt", "x");
        refreshRemote();
        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
        waitForRowPresent(table, "plain.txt", 10);
        GBookmark bm = remoteBrowser().getBookmark();
        GBookmarkManager.addBookmark(bm);
        added = bm;
        selectRow(table, findRowByName(table, "plain.txt"));
        JMenuItem item = itemIn(remoteMenuForSelection(), GLocalized.SET_HOME_FOLDER);
        assertNotNull(item);
        assertFalse(item.isEnabled(), "a file is not a home folder");
    }

    @Test
    void localFolder_setsDefaultLocalFolderPreference() throws Exception {
        String before = GSFTPPreferences.defaultLocalHomeFolder();
        try {
            Path lsub = Files.createDirectory(localRoot.resolve("lsub"));
            navigateLocalTo(localRoot);
            JTableFixture table = new JTableFixture(robot, localBrowser().getFileTable());
            waitForRowPresent(table, "lsub", 10);
            rightClickAndSelectMenuItem(table, findRowByName(table, "lsub"), GLocalized.SET_HOME_FOLDER);
            robot.waitForIdle();
            assertEquals(lsub.toRealPath().toString(),
                Path.of(GSFTPPreferences.defaultLocalHomeFolder()).toRealPath().toString());
        } finally {
            GSFTPPreferences.setDefaultLocalHomeFolder(before);
        }
    }
}
