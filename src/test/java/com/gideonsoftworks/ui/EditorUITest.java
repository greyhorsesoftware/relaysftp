package com.gideonsoftworks.ui;

import com.gideonsoftworks.editor.GEditorWindow;
import com.gideonsoftworks.gja.GAbstractJobManagerListener;
import com.gideonsoftworks.gja.GJobDescription;
import com.gideonsoftworks.gja.GJobManagerEvent;
import org.assertj.swing.fixture.JTableFixture;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.junit.jupiter.api.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui") class EditorUITest extends UITestBase {
    @Test void test() throws Exception {
        createServerFile("editable.txt", "original content\nline two\n");
        new ProcessBuilder("chmod", "666", serverRoot.resolve("editable.txt").toAbsolutePath().toString()).start().waitFor();
        loginToServer();

        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
        int row = findRowByName(table, "editable.txt");
        assertTrue(row >= 0);

        // Open editor via right-click context menu
        rightClickAndSelectMenuItem(table, row, "Edit...");

        // Wait for editor to open and file to download
        GEditorWindow foundEditor = null;
        for (int i = 0; i < 40; i++) {
            Thread.sleep(250);
            for (Window w : Window.getWindows()) {
                if (w instanceof GEditorWindow && w.isVisible()) {
                    foundEditor = (GEditorWindow) w;
                    break;
                }
            }
            if (foundEditor != null) break;
        }
        assertNotNull(foundEditor, "Editor window should open");
        final GEditorWindow editor = foundEditor;

        // Wait for the file to load (editor becomes editable)
        RSyntaxTextArea textArea = findRSyntaxTextArea(editor);
        assertNotNull(textArea, "Should find RSyntaxTextArea in editor");

        // Wait until editable (download complete)
        for (int i = 0; i < 40; i++) {
            Thread.sleep(250);
            if (textArea.isEditable()) break;
        }
        assertTrue(textArea.isEditable(), "Editor should be editable after download");

        // Verify original content loaded
        String loadedText = textArea.getText();
        assertTrue(loadedText.contains("original content"), "Should contain original text");

        // Type new content — click the text area, select all, type replacement
        robot.click(textArea);
        robot.waitForIdle();

        SwingUtilities.invokeAndWait(() -> {
            textArea.selectAll();
            textArea.replaceSelection("modified by test\nnew line\n");
        });
        robot.waitForIdle();

        // Verify text was changed
        String modifiedText = textArea.getText();
        assertTrue(modifiedText.contains("modified by test"), "Text should be modified");

        // Save via Cmd+S (or Ctrl+S)
        int shortcutMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        // Check that the overlay (spinner) appears during save. On loopback the upload finishes in a
        // few ms, so a polling check is racy; instead listen for the overlay being installed as the
        // glass pane (setGlassPane adds it to the root pane) and shown. Both events fire no matter
        // how quickly hideOverlay() follows.
        final boolean[] overlayShown = { false };
        final java.awt.event.ComponentAdapter shownListener = new java.awt.event.ComponentAdapter() {
            @Override public void componentShown(java.awt.event.ComponentEvent e) { overlayShown[0] = true; }
        };
        SwingUtilities.invokeAndWait(() -> editor.getRootPane().addContainerListener(new java.awt.event.ContainerAdapter() {
            @Override public void componentAdded(java.awt.event.ContainerEvent e) {
                if (e.getChild() == editor.getGlassPane()) e.getChild().addComponentListener(shownListener);
            }
        }));

        // The save uploads through the editor window's own job manager (not the browser's).
        CountDownLatch uploaded = new CountDownLatch(1);
        editor.getJobManager().addStatusListener(new GAbstractJobManagerListener() {
            @Override public void jm_jobCompleted(GJobManagerEvent e) { if (isUpload(e)) uploaded.countDown(); }
            @Override public void jm_jobFailed(GJobManagerEvent e)    { if (isUpload(e)) uploaded.countDown(); }
            private boolean isUpload(GJobManagerEvent e) {
                return e.getJobDescription() != null && e.getJobDescription().getJobType() == GJobDescription.UPLOAD_JOB_TYPE;
            }
        });

        // Press Cmd+S / Ctrl+S — only once the editor's text area owns the focus, so the global
        // shortcut can never land in whatever window happens to be frontmost (e.g. the IDE).
        // Press the modifier key explicitly: assertj's modifier-mask overloads only understand the
        // legacy InputEvent.*_MASK values, not the *_DOWN_MASK the toolkit reports.
        int modifierKey = (shortcutMask == KeyEvent.META_DOWN_MASK) ? KeyEvent.VK_META : KeyEvent.VK_CONTROL;
        robot.focusAndWaitForFocusGain(textArea);
        robot.pressKey(modifierKey);
        try {
            robot.pressAndReleaseKeys(KeyEvent.VK_S);
        } finally {
            robot.releaseKey(modifierKey);
        }
        robot.waitForIdle();

        // Wait for the upload job to finish, then let the EDT deliver the completion + shown events.
        assertTrue(uploaded.await(20, TimeUnit.SECONDS), "Save upload job should complete");
        robot.waitForIdle();
        assertFalse(editor.getGlassPane().isVisible(), "Overlay should be hidden after the save completes");

        assertTrue(overlayShown[0], "Spinner overlay should have appeared during save");

        // Verify the file on the server has been updated
        String serverContent = Files.readString(serverRoot.resolve("editable.txt"));
        assertTrue(serverContent.contains("modified by test"),
            "Server file should contain modified text, got: " + serverContent);

        // Clean up
        SwingUtilities.invokeAndWait(() -> {
            editor.setVisible(false);
            editor.dispose();
        });
    }

    private RSyntaxTextArea findRSyntaxTextArea(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof RSyntaxTextArea) return (RSyntaxTextArea) c;
            if (c instanceof Container) {
                RSyntaxTextArea found = findRSyntaxTextArea((Container) c);
                if (found != null) return found;
            }
        }
        return null;
    }
}
