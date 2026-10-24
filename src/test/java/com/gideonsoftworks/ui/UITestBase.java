package com.gideonsoftworks.ui;

import com.gideonsoftworks.gcore.GBookmark;
import com.gideonsoftworks.gideon.actions.GUIAction;
import com.gideonsoftworks.gideon.browser.GBrowserPanelController;
import com.gideonsoftworks.gideon.browser.GBrowserWindow;
import com.gideonsoftworks.gideon.core.GFrameManager;
import com.gideonsoftworks.gideon.preferences.GSFTPPreferences;
import com.gideonsoftworks.gja.*;
import com.gideonsoftworks.gcore.*;

import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.shell.ProcessShellCommandFactory;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.MouseButton;
import org.assertj.swing.core.Robot;
import org.assertj.swing.data.TableCell;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.fixture.JTableFixture;
import org.assertj.swing.timing.Condition;
import org.assertj.swing.timing.Pause;
import org.junit.jupiter.api.*;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Tag("ui")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class UITestBase {

    SshServer sshd;
    int sftpPort;
    Path tempDir;
    Path serverRoot;
    Path localRoot;
    GFrameManager frameManager;
    GBrowserWindow browserWindow;
    FrameFixture window;
    Robot robot;

    @BeforeAll
    void startServerAndApp() throws Exception {
        // Use a concrete temp base to avoid macOS symlink issues with embedded SSHD
        Path tmpBase = System.getProperty("os.name").toLowerCase().contains("win")
            ? Path.of(System.getProperty("java.io.tmpdir"))
            : Path.of("/tmp");
        tempDir = Files.createTempDirectory(tmpBase, "relaysftp-ui-test");
        serverRoot = Files.createDirectory(tempDir.resolve("server")).toRealPath();
        localRoot = Files.createDirectory(tempDir.resolve("local")).toRealPath();

        startBackend();

        SwingUtilities.invokeAndWait(() -> {
            com.formdev.flatlaf.FlatLightLaf.setup();
            frameManager = new GFrameManager();
            GUIAction.setSharedGFrameManager(frameManager);
            browserWindow = createBrowserWindow();
            browserWindow.setSize(900, 600);
            browserWindow.setVisible(true);
        });

        robot = BasicRobot.robotWithCurrentAwtHierarchy();
        window = new FrameFixture(robot, browserWindow);
        ensureWindowActive();
    }

    /**
     * Make the browser window the active (key) window and wait until the OS agrees. A window
     * shown from a Gradle-launched JVM is not necessarily active; a robot click on an inactive
     * window is consumed as "activate" and never reaches the table (seen as an empty selection
     * after a real click — UI_TEST_HARDENING_PLAN §4.1). Fails fast with a clear message rather
     * than letting every later click go astray.
     */
    void ensureWindowActive() throws Exception {
        // Re-issue the activation request about once a second while waiting: macOS can drop a
        // toFront() sent while the previous fork's window is still being torn down, and a single
        // request that was lost never resolves on its own. Seen once in ~80 classes as a 5 s timeout.
        long deadline = System.currentTimeMillis() + 10_000;
        long nextNudge = 0;
        while (true) {
            if (System.currentTimeMillis() >= nextNudge) {
                SwingUtilities.invokeAndWait(() -> {
                    browserWindow.toFront();
                    browserWindow.requestFocus();
                });
                window.moveToFront();
                nextNudge = System.currentTimeMillis() + 1_000;
            }
            boolean active = GuiActionRunner.execute(() -> browserWindow.isActive() || browserWindow.isFocused());
            if (active) break;
            if (System.currentTimeMillis() >= deadline) {
                throw new AssertionError("UI tests need an active desktop session (the browser window never became "
                    + "the active window within 10 s). Unlock the screen / stay on this desktop, or use -PexcludeUI.");
            }
            Thread.sleep(50);
        }
        robot.waitForIdle();
    }

    /** Screen-independent click on the centre of column 0 of {@code row}, through the assertj robot. */
    void clickRow(JTableFixture table, int row, MouseButton button) throws Exception {
        ensureWindowActive();
        JTable jtable = table.target();
        java.awt.Point p = GuiActionRunner.execute(() -> {
            java.awt.Rectangle r = jtable.getCellRect(row, 0, true);
            return new java.awt.Point(r.x + r.width / 2, r.y + r.height / 2);
        });
        robot.click(jtable, p, button, 1);
        robot.waitForIdle();
    }

    /** Real left-click on a row: selects it and gives the pane focus, like a user. */
    void clickRow(JTableFixture table, int row) throws Exception {
        clickRow(table, row, MouseButton.LEFT_BUTTON);
    }

    /** Wait (up to 5 s) for a popup menu item with this exact text to be showing, then return it. */
    JMenuItem awaitPopupMenuItem(String text) {
        final JMenuItem[] found = { null };
        Pause.pause(new Condition("popup menu item '" + text + "' showing") {
            @Override public boolean test() {
                found[0] = GuiActionRunner.execute(() -> findPopupMenuItem(text));
                return found[0] != null;
            }
        }, 5_000);
        return found[0];
    }

    @AfterAll
    void tearDown() throws Exception {
        if (window != null) window.cleanUp();
        if (browserWindow != null) {
            SwingUtilities.invokeAndWait(() -> {
                browserWindow.setVisible(false);
                browserWindow.dispose();
            });
        }
        if (sshd != null) sshd.stop(true);
        if (tempDir != null) {
            try {
                Files.walk(tempDir).sorted(Comparator.reverseOrder())
                    .forEach(p -> { try { Files.delete(p); } catch (IOException ignore) {} });
            } catch (IOException ignore) {}
        }
    }

    /** Start the protocol backend under test. SFTP by default; S3 base overrides. */
    void startBackend() throws Exception {
        sshd = SshServer.setUpDefaultServer();
        sshd.setPort(0);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(tempDir.resolve("hostkey.ser")));
        sshd.setPasswordAuthenticator((u, p, s) -> "test".equals(u) && "test".equals(p));
        sshd.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
        sshd.setCommandFactory(ProcessShellCommandFactory.INSTANCE);
        sshd.start();
        sftpPort = sshd.getPort();

        GSFTPPreferences.setUseExec(true);
    }

    /** Creates the browser window under test. Subclasses override for remote-only, etc. */
    GBrowserWindow createBrowserWindow() {
        return new GBrowserWindow(frameManager);
    }

    GBrowserPanelController remoteBrowser() {
        return (GBrowserPanelController) browserWindow.getRemoteBrowser();
    }

    GBrowserPanelController localBrowser() {
        return (GBrowserPanelController) browserWindow.getLocalBrowser();
    }

    GJobManager jobManager() {
        return browserWindow.getJobManager();
    }

    /** Convert a local path to SFTP format (e.g. C:\foo\bar -> /C:/foo/bar) */
    static String toSftpPath(Path path) {
        String abs = path.toAbsolutePath().toString();
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            abs = "/" + abs.replace('\\', '/');
        }
        return abs;
    }

    void loginToServer() throws Exception {

        SwingUtilities.invokeAndWait(() -> {
            GBookmark bm = new GBookmark("test", "localhost",
                toSftpPath(serverRoot),
                "test", "test", sftpPort, GBookmark.G_SFTP_PROTOCOL);
            bm.setNeverSavePassword(true);
            bm.setLocalHomePath(localRoot.toAbsolutePath().toString());
            remoteBrowser().connectForBookmark(bm);
        });
        robot.waitForIdle();

        JobWaiter waiter = waitForJob(GJobDescription.LOGIN_JOB_TYPE);
        window.button("connectButton").click();
        waiter.await(15);

        // Remote-only windows have no local pane to navigate.
        if (localBrowser() != null) {
            navigateLocalTo(localRoot);
        }
    }

    void navigateLocalTo(Path dir) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GFileItem folder = new GFileItem(new java.io.File(dir.toAbsolutePath().toString()));
            localBrowser().changeToFolder(folder);
        });
        robot.waitForIdle();
    }

    /**
     * Select a table row by clicking, then right-click to open context menu,
     * and click the specified menu item.
     */
    void rightClickAndSelectMenuItem(JTableFixture table, int row, String menuText) throws Exception {
        // A real left-click selects the row AND gives the pane focus (the app's mouse handler sets
        // the key-pane state that menu actions resolve the selection from); the right-click opens
        // the context menu. Both go through the assertj robot on an active window (see clickRow).
        clickRow(table, row, MouseButton.LEFT_BUTTON);
        clickRow(table, row, MouseButton.RIGHT_BUTTON);

        JMenuItem item;
        try {
            item = awaitPopupMenuItem(menuText);
        } catch (org.assertj.swing.exception.WaitTimedOutError timeout) {
            item = null;
        }
        if (item != null) {
            robot.click(item);
            return;
        }

        // Debug
        JTable jt = table.target();
        System.out.println("[RIGHT-CLICK] Selection: " + java.util.Arrays.toString(jt.getSelectedRows()));
        System.out.println("[RIGHT-CLICK] Table showing=" + jt.isShowing() + " focused=" + jt.hasFocus());
        System.out.println("[RIGHT-CLICK] Table bounds=" + jt.getBounds());
        System.out.println("[RIGHT-CLICK] Table parent=" + jt.getParent().getClass().getName() + " visible=" + jt.getParent().isVisible());
        try { System.out.println("[RIGHT-CLICK] Table screenLoc=" + jt.getLocationOnScreen()); } catch (Exception ex) { System.out.println("[RIGHT-CLICK] Can't get screen loc"); }
        // Check if remote panel is showing browser vs login
        System.out.println("[RIGHT-CLICK] Remote panel visible cards:");
        java.awt.Container remotePanel = remoteBrowser().getPanel();
        for (java.awt.Component c : remotePanel.getComponents()) {
            System.out.println("[RIGHT-CLICK]   " + c.getClass().getSimpleName() + " visible=" + c.isVisible() + " bounds=" + c.getBounds());
        }

        throw new AssertionError("Could not find popup menu item: " + menuText);
    }

    JMenuItem findPopupMenuItem(String text) {
        for (MenuElement me : MenuSelectionManager.defaultManager().getSelectedPath()) {
            if (me instanceof JPopupMenu) {
                for (java.awt.Component c : ((JPopupMenu) me).getComponents()) {
                    if (c instanceof JMenuItem && text.equals(((JMenuItem) c).getText())) {
                        return (JMenuItem) c;
                    }
                }
            }
        }
        // Fallback: search all windows
        for (java.awt.Window w : java.awt.Window.getWindows()) {
            if (w.isVisible()) {
                JMenuItem found = findMenuItemRecursive(w, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JMenuItem findMenuItemRecursive(java.awt.Container container, String text) {
        for (java.awt.Component c : container.getComponents()) {
            if (c instanceof JMenuItem && text.equals(((JMenuItem) c).getText())) {
                return (JMenuItem) c;
            }
            if (c instanceof java.awt.Container) {
                JMenuItem found = findMenuItemRecursive((java.awt.Container) c, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    void createServerFile(String name, String content) throws IOException {
        Files.writeString(serverRoot.resolve(name), content);
    }

    void createServerDir(String name) throws IOException {
        Files.createDirectories(serverRoot.resolve(name));
    }

    void createServerFileInDir(String dir, String name, String content) throws IOException {
        Path dirPath = serverRoot.resolve(dir);
        Files.createDirectories(dirPath);
        Files.writeString(dirPath.resolve(name), content);
    }

    void createServerFileWithPerms(String name, String content, String perms) throws IOException {
        Path file = serverRoot.resolve(name);
        Files.writeString(file, content);
        new ProcessBuilder("chmod", perms, file.toAbsolutePath().toString()).start();
    }

    void createLocalFile(String name, String content) throws IOException {
        Files.writeString(localRoot.resolve(name), content);
    }

    JobWaiter waitForJob(int jobType) {
        return waitForJobs(jobType, 1);
    }

    /** Wait for N completions of a job type (e.g. a multi-select delete queues N jobs). */
    JobWaiter waitForJobs(int jobType, int expectedCount) {
        JobWaiter waiter = new JobWaiter(jobType, this, expectedCount);
        jobManager().addStatusListener(waiter);
        return waiter;
    }

    /** Counts completed REFRESH jobs. Tests asserting on this must run with the
     *  remote-change monitor OFF, or a poller refresh pollutes the count. */
    static class RefreshCounter extends GAbstractJobManagerListener {
        final java.util.concurrent.atomic.AtomicInteger completed =
            new java.util.concurrent.atomic.AtomicInteger();
        @Override public void jm_jobCompleted(GJobManagerEvent e) {
            if (e.getJobDescription() != null
                && e.getJobDescription().getJobType() == GJobDescription.REFRESH_JOB_TYPE) {
                completed.incrementAndGet();
            }
        }
    }

    RefreshCounter countRefreshJobs() {
        RefreshCounter rc = new RefreshCounter();
        jobManager().addStatusListener(rc);
        return rc;
    }

    /** Multi-select rows and pick a context-menu item. A REAL left-click lands on the
     *  first row (the app's mouse handler sets the pane's focus state, which menu
     *  actions use to resolve the selection), the remaining rows are added
     *  programmatically, then a right-click on the first row opens the menu -
     *  evaluatePopup preserves a multi-selection. */
    void rightClickSelectionAndSelectMenuItem(JTableFixture table, int[] rows, String menuText) throws Exception {
        JTable jtable = table.target();

        // real left-click: selects rows[0] AND gives the pane focus
        clickRow(table, rows[0], MouseButton.LEFT_BUTTON);

        // extend the selection to the remaining rows (same end state as ctrl-clicks)
        SwingUtilities.invokeAndWait(() -> {
            for (int i = 1; i < rows.length; i++) {
                jtable.addRowSelectionInterval(rows[i], rows[i]);
            }
        });
        robot.waitForIdle();

        // right-click on the (still selected) first row to open the context menu
        clickRow(table, rows[0], MouseButton.RIGHT_BUTTON);

        JMenuItem item = awaitPopupMenuItem(menuText);   // throws WaitTimedOutError with the item name
        robot.click(item);
    }

    /** Poll until the named row disappears from the table (in-place removal, no re-list). */
    void waitForRowAbsent(JTableFixture table, String name, int timeoutSeconds) {
        Pause.pause(new Condition("row \"" + name + "\" absent") {
            @Override public boolean test() {
                return findRowByName(table, name) < 0;
            }
        }, timeoutSeconds * 1000L);
    }

    /** Poll until the named row appears in the table. */
    void waitForRowPresent(JTableFixture table, String name, int timeoutSeconds) {
        Pause.pause(new Condition("row \"" + name + "\" present") {
            @Override public boolean test() {
                return findRowByName(table, name) >= 0;
            }
        }, timeoutSeconds * 1000L);
    }

    static int findRowByName(JTableFixture table, String name) {
        for (int i = 0; i < table.rowCount(); i++) {
            String val = table.valueAt(TableCell.row(i).column(0));
            if (name.equals(val)) return i;
        }
        return -1;
    }

    static void clickButtonInDialog(String... buttonTexts) {
        for (java.awt.Window w : java.awt.Window.getWindows()) {
            if (w instanceof JDialog && w.isVisible()) {
                for (java.awt.Component c : getAllComponents((java.awt.Container) w)) {
                    if (c instanceof JButton) {
                        String text = ((JButton) c).getText();
                        for (String bt : buttonTexts) {
                            if (bt.equals(text)) {
                                ((JButton) c).doClick();
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    static java.util.List<java.awt.Component> getAllComponents(java.awt.Container container) {
        java.util.List<java.awt.Component> result = new java.util.ArrayList<>();
        for (java.awt.Component c : container.getComponents()) {
            result.add(c);
            if (c instanceof java.awt.Container) {
                result.addAll(getAllComponents((java.awt.Container) c));
            }
        }
        return result;
    }

    // ===== Mirror workspace helpers (shared by the SFTP + S3 mirror UI tests) =====
    //
    // Drive the real GMirrorPanel through Configure -> Preflight -> Review -> Mirror.
    // Controls are located by the setName() hooks added to GMirrorPanel. Components are
    // found via our own recursive search (not AssertJ's requireShowing finder) because the
    // three cards live in a CardLayout and only the visible one is "showing".

    /** Detector dropdown indices, matching GMirrorPanel's combo order. */
    static final int DETECTOR_AUTOMATIC = 0, DETECTOR_SIZE_MTIME = 1, DETECTOR_SIZE_ONLY = 2, DETECTOR_S3_ETAG = 3;

    /** Swap the dual-pane browser for the mirror workspace (on the EDT). */
    void enterMirrorView() throws Exception {
        SwingUtilities.invokeAndWait(() -> browserWindow.enterMirrorView());
        robot.waitForIdle();
    }

    /** Restore the dual-pane browser — safe no-op if not currently in the mirror view. */
    void exitMirrorView() throws Exception {
        SwingUtilities.invokeAndWait(() -> browserWindow.exitMirrorView());
        robot.waitForIdle();
    }

    /** Pick direction: true = Upload (local -> remote), false = Download (remote -> local). */
    void mirrorSetDirection(boolean upload) throws Exception {
        JToggleButton b = findByName(JToggleButton.class, upload ? "mirrorUpArrow" : "mirrorDownArrow");
        SwingUtilities.invokeAndWait(b::doClick);
        robot.waitForIdle();
    }

    void mirrorSelectDetector(int index) throws Exception {
        JComboBox<?> c = findByName(JComboBox.class, "mirrorDetectorCombo");
        SwingUtilities.invokeAndWait(() -> c.setSelectedIndex(index));
        robot.waitForIdle();
    }

    void mirrorSetDeleteExtras(boolean on) throws Exception { mirrorSetCheckBox("mirrorDeleteExtras", on); }
    void mirrorSetSkipFilters(boolean on) throws Exception { mirrorSetCheckBox("mirrorSkipFilters", on); }

    /** Enable "Only mirror files modified in the last N [units]" and set the window. */
    void mirrorSetOnlyRecent(int amount, String units) throws Exception {
        JCheckBox box = findByName(JCheckBox.class, "mirrorOnlyRecent");
        JSpinner amt = findByName(JSpinner.class, "mirrorRecentAmount");
        JComboBox<?> u = findByName(JComboBox.class, "mirrorRecentUnits");
        SwingUtilities.invokeAndWait(() -> {
            if (!box.isSelected()) box.doClick();   // fires the listener that enables amt/units
            amt.setValue(amount);
            u.setSelectedItem(units);
        });
        robot.waitForIdle();
    }

    private void mirrorSetCheckBox(String name, boolean on) throws Exception {
        JCheckBox cb = findByName(JCheckBox.class, name);
        SwingUtilities.invokeAndWait(() -> { if (cb.isSelected() != on) cb.doClick(); });
        robot.waitForIdle();
    }

    /** Click Preflight and block until the Review card (the scan result) is on screen. */
    void mirrorPreflightAndWaitForReview() throws Exception {
        JButton pf = findByName(JButton.class, "mirrorPreflightButton");
        SwingUtilities.invokeAndWait(pf::doClick);
        Pause.pause(new Condition("mirror review card visible") {
            @Override public boolean test() {
                JTable t = findByName(JTable.class, "mirrorReviewTable");
                return t != null && t.isShowing();
            }
        }, 20_000);
        robot.waitForIdle();
        assertColumnShowsWholeText(findByName(JTable.class, "mirrorReviewTable"), 1);   // Operation
    }

    /** Return to the Configure card from Review (so a detector/option can be changed and re-run). */
    void mirrorBackToConfigure() throws Exception {
        JButton back = findShowingButton("Back");
        SwingUtilities.invokeAndWait(back::doClick);
        robot.waitForIdle();
    }

    /** The Review table rendered as rows of {checkbox, Operation, Path, Size}. */
    String[][] mirrorReviewRows() {
        return new JTableFixture(robot, findByName(JTable.class, "mirrorReviewTable")).contents();
    }

    /** Count Review rows whose Operation label contains {@code opFragment} (e.g. "copy (new)"). */
    long mirrorCountOp(String opFragment) {
        return Arrays.stream(mirrorReviewRows()).filter(r -> r[1].contains(opFragment)).count();
    }

    String mirrorSummary() {
        return findByName(JLabel.class, "mirrorReviewSummary").getText();
    }

    /**
     * Click Mirror (which queues a MIRROR_JOB_TYPE job and switches to the Running card), wait for
     * that job to complete, and return the Running card's Done summary (e.g. "2 copied · 0 deleted …").
     * The mirror runs on a background virtual thread, so we sync on the job-manager completion event.
     */
    /** Every cell in the column fits its width (no "…" truncation). */
    static void assertColumnShowsWholeText(JTable t, int col) {
        GuiActionRunner.execute(() -> {
            int width = t.getColumnModel().getColumn(col).getWidth();
            for (int r = 0; r < t.getRowCount(); r++) {
                int need = t.prepareRenderer(t.getCellRenderer(r, col), r, col).getPreferredSize().width;
                Assertions.assertTrue(need <= width, t.getName() + " column " + col + " row " + r
                    + " needs " + need + "px but has " + width + "px: " + t.getValueAt(r, col));
            }
            return null;
        });
    }

    String mirrorExecuteAndWaitForCompletion() throws Exception {
        JButton mb = findByName(JButton.class, "mirrorButton");
        JobWaiter waiter = waitForJob(GJobDescription.MIRROR_JOB_TYPE);
        SwingUtilities.invokeAndWait(mb::doClick);
        waiter.await(30);
        robot.waitForIdle();   // let the EDT process the completion event + update the card
        JTable run = findByName(JTable.class, "mirrorRunTable");
        assertColumnShowsWholeText(run, 0);   // Operation
        assertColumnShowsWholeText(run, 3);   // Status
        JLabel detail = findByName(JLabel.class, "mirrorRunDetail");
        return detail != null ? detail.getText() : "";
    }

    // ---- Running card (docs/1.8.0/MIRROR_RUNNING_PANEL_PLAN.md §5.1) ----

    /** Click Mirror and return the waiter, without waiting — for tests that observe the run in flight. */
    JobWaiter mirrorExecuteWithoutWaiting() throws Exception {
        JButton mb = findByName(JButton.class, "mirrorButton");
        JobWaiter waiter = waitForJob(GJobDescription.MIRROR_JOB_TYPE);
        SwingUtilities.invokeAndWait(mb::doClick);
        return waiter;
    }

    /** The Running card's table: Operation / Path / Size / Status as rendered text. */
    String[][] mirrorRunRows() {
        return new JTableFixture(robot, findByName(JTable.class, "mirrorRunTable")).contents();
    }

    /** Status text of the run row whose Path equals {@code path}, or null when there is no such row. */
    String mirrorRunStatus(String path) {
        for (String[] r : mirrorRunRows()) if (path.equals(r[1])) return r[3];
        return null;
    }

    String mirrorRunStats()  { return findByName(JLabel.class, "mirrorRunStats").getText(); }
    String mirrorRunFooter() { return findByName(JLabel.class, "mirrorRunFooter").getText(); }
    String mirrorRunTitle()  { return findByName(JLabel.class, "mirrorRunTitle").getText(); }

    void mirrorCancelRunning() throws Exception {
        JButton b = findByName(JButton.class, "mirrorRunCancel");
        SwingUtilities.invokeAndWait(b::doClick);
    }

    /** Wait until some run row (or the row for {@code path}, when non-null) has a Status starting with {@code statusPrefix}. */
    void waitForRunRow(String path, String statusPrefix, long timeoutMs) {
        Pause.pause(new Condition("run row " + (path != null ? path : "(any)") + " reads '" + statusPrefix + "…'") {
            @Override public boolean test() {
                for (String[] r : mirrorRunRows())
                    if ((path == null || path.equals(r[1])) && r[3].startsWith(statusPrefix)) return true;
                return false;
            }
        }, timeoutMs);
    }

    /** Open this browser window's Activity popover (its toolbar item toggles it) and let it render. */
    void openActivityPopover() throws Exception {
        AbstractButton item = null;
        for (Component c : getAllComponents(browserWindow))
            if (c.getClass().getSimpleName().equals("GActivityToolbarItem") && c.isShowing()) item = (AbstractButton) c;
        if (item == null) throw new AssertionError("No showing activity toolbar item in the browser window");
        AbstractButton finalItem = item;
        SwingUtilities.invokeAndWait(finalItem::doClick);
        robot.waitForIdle();
    }

    /** A named component inside the popover dialogs owned by this browser window (the popover may already be hidden again). */
    <T extends Component> T findInPopover(Class<T> type, String name) {
        for (Window w : browserWindow.getOwnedWindows()) {
            T f = findByNameIn(w, type, name);
            if (f != null) return f;
        }
        return null;
    }

    // ---- raw component lookup by name, regardless of showing state ----

    <T extends Component> T findByName(Class<T> type, String name) {
        for (Window w : Window.getWindows()) {
            T f = findByNameIn(w, type, name);
            if (f != null) return f;
        }
        return null;
    }

    private <T extends Component> T findByNameIn(Container root, Class<T> type, String name) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c) && name.equals(c.getName())) return type.cast(c);
            if (c instanceof Container) {
                T f = findByNameIn((Container) c, type, name);
                if (f != null) return f;
            }
        }
        return null;
    }

    private JButton findShowingButton(String text) {
        for (Window w : Window.getWindows()) {
            JButton b = findShowingButtonIn(w, text);
            if (b != null) return b;
        }
        throw new AssertionError("No showing button with text: " + text);
    }

    private JButton findShowingButtonIn(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton && c.isShowing() && text.equals(((JButton) c).getText())) return (JButton) c;
            if (c instanceof Container) {
                JButton b = findShowingButtonIn((Container) c, text);
                if (b != null) return b;
            }
        }
        return null;
    }

    static class JobWaiter extends GAbstractJobManagerListener {
        private final int targetJobType;
        private final UITestBase test;
        private final CountDownLatch latch;
        private volatile boolean failed = false;
        private volatile boolean stopped = false;   // resolved by jm_jobStopped (a cancel)
        private volatile String failMessage;

        JobWaiter(int targetJobType, UITestBase test) {
            this(targetJobType, test, 1);
        }

        JobWaiter(int targetJobType, UITestBase test, int expectedCount) {
            this.targetJobType = targetJobType;
            this.test = test;
            this.latch = new CountDownLatch(expectedCount);
        }

        @Override public void jm_jobCompleted(GJobManagerEvent e) {
            if (matches(e)) {
                latch.countDown();
                if (latch.getCount() == 0) test.jobManager().removeStatusListener(this);
            }
        }

        @Override public void jm_jobStopped(GJobManagerEvent e) {
            if (matches(e)) {
                stopped = true;
                while (latch.getCount() > 0) latch.countDown();
                test.jobManager().removeStatusListener(this);
            }
        }

        boolean wasStopped() { return stopped; }

        @Override public void jm_jobFailed(GJobManagerEvent e) {
            if (matches(e)) {
                failed = true;
                GJobStatus s = e.getJobDescription().getJobStatus();
                failMessage = s.getException() != null ? s.getException().getMessage() : "Job failed";
                // release the waiter regardless of how many completions were still expected
                while (latch.getCount() > 0) latch.countDown();
                test.jobManager().removeStatusListener(this);
            }
        }

        private boolean matches(GJobManagerEvent e) {
            return targetJobType == -1 || (e.getJobDescription() != null
                && e.getJobDescription().getJobType() == targetJobType);
        }

        void await(long timeoutSeconds) throws Exception {
            if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
                test.jobManager().removeStatusListener(this);
                throw new AssertionError("Timed out waiting for job type " + targetJobType);
            }
            if (failed) throw new AssertionError("Job failed: " + failMessage);
        }
    }
}
