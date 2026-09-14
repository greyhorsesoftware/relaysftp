package com.gideonsoftworks.ui;

import com.gideonsoftworks.editor.GEditorWindow;
import org.assertj.swing.core.MouseButton;
import org.assertj.swing.data.TableCell;
import org.assertj.swing.fixture.JTableFixture;
import org.assertj.swing.timing.Condition;
import org.assertj.swing.timing.Pause;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.junit.jupiter.api.*;
import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/**
 * File > New File... opens an empty editor buffer; the first save prompts for
 * a name and writes to the key pane's current directory (remote or local).
 */
@Tag("ui") class NewFileUITest extends UITestBase {
    @Test void test() throws Exception {
        createServerFile("seed.txt", "seed\n");
        loginToServer();

        // --- Remote pane: New File... via the File menu ---

        // real click on a remote row so the remote pane is the key browser
        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
        int row = findRowByName(table, "seed.txt");
        assertTrue(row >= 0);
        table.click(TableCell.row(row).column(0), MouseButton.LEFT_BUTTON);
        robot.waitForIdle();

        JMenuItem newFileItem = findMenuBarItem("New File...");
        assertNotNull(newFileItem, "File menu should contain New File...");

        // the browser context menu carries the same shared action
        SwingUtilities.invokeAndWait(() -> {
            JPopupMenu popup = browserWindow.popupMenu(new com.gideonsoftworks.gcore.GFileItem[0]);
            boolean found = false;
            for (Component c : popup.getComponents()) {
                if (c instanceof JMenuItem && "New File...".equals(((JMenuItem) c).getText())) found = true;
            }
            assertTrue(found, "Context menu should contain New File...");
        });
        SwingUtilities.invokeAndWait(newFileItem::doClick);
        robot.waitForIdle();

        GEditorWindow editor = waitForEditorWindow();
        RSyntaxTextArea textArea = findRSyntaxTextArea(editor);
        assertNotNull(textArea, "Should find RSyntaxTextArea in editor");
        assertTrue(textArea.isEditable(), "New file buffer should be editable immediately");
        assertEquals("", textArea.getText(), "New file buffer should start empty");

        SwingUtilities.invokeAndWait(() -> textArea.setText("remote new file body"));
        robot.waitForIdle();

        // save() blocks in the modal name dialog, so fire it with invokeLater
        SwingUtilities.invokeLater(editor::save);
        enterNameInDialogAndConfirm("fresh.txt");

        // the upload runs on the editor's own job manager; sync on the server file
        Path serverFile = serverRoot.resolve("fresh.txt");
        Pause.pause(new Condition("new file uploaded to server") {
            @Override public boolean test() {
                try {
                    return Files.exists(serverFile)
                        && "remote new file body".equals(Files.readString(serverFile));
                } catch (java.io.IOException e) { return false; }
            }
        }, 15_000);
        assertEquals("remote new file body", Files.readString(serverFile));
        assertEquals("fresh.txt", editor.getTitle());

        // the save callback refreshes the destination pane — the new row appears
        waitForRowPresent(table, "fresh.txt", 15);

        SwingUtilities.invokeAndWait(() -> { editor.setVisible(false); editor.dispose(); });

        // --- Local pane: name prompt saves directly to the local CWD ---

        final GEditorWindow[] holder = new GEditorWindow[1];
        final com.gideonsoftworks.gcore.GFileItem[] savedContext = new com.gideonsoftworks.gcore.GFileItem[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new GEditorWindow(frameManager);
            holder[0].showNewFile(localBrowser().getVirtualCWD(), saved -> savedContext[0] = saved);
        });
        robot.waitForIdle();
        GEditorWindow localEditor = holder[0];

        RSyntaxTextArea localTextArea = findRSyntaxTextArea(localEditor);
        assertNotNull(localTextArea);
        assertTrue(localTextArea.isEditable());

        SwingUtilities.invokeAndWait(() -> localTextArea.setText("local new file body"));
        robot.waitForIdle();

        SwingUtilities.invokeLater(localEditor::save);
        enterNameInDialogAndConfirm("local.txt");

        Path localFile = localRoot.resolve("local.txt");
        Pause.pause(new Condition("new file written locally") {
            @Override public boolean test() { return Files.exists(localFile); }
        }, 10_000);
        assertEquals("local new file body", Files.readString(localFile));
        assertEquals("local.txt", localEditor.getTitle());

        // save listener fired with the saved item as context
        assertNotNull(savedContext[0], "Save listener should have fired");
        assertEquals(localFile.toAbsolutePath().toString(), savedContext[0].getCompletePath());

        SwingUtilities.invokeAndWait(() -> { localEditor.setVisible(false); localEditor.dispose(); });
    }

    /** Wait for the name dialog, type the name, and click OK. */
    private void enterNameInDialogAndConfirm(String name) throws Exception {
        Pause.pause(new Condition("name dialog visible") {
            @Override public boolean test() { return findDialogTextField() != null; }
        }, 10_000);

        JTextField field = findDialogTextField();
        SwingUtilities.invokeAndWait(() -> field.setText(name));
        robot.waitForIdle();
        clickButtonInDialog("OK");
        robot.waitForIdle();
    }

    private static JTextField findDialogTextField() {
        for (Window w : Window.getWindows()) {
            if (w instanceof JDialog && w.isVisible()) {
                for (Component c : getAllComponents((Container) w)) {
                    if (c instanceof JTextField) return (JTextField) c;
                }
            }
        }
        return null;
    }

    private JMenuItem findMenuBarItem(String text) {
        JMenuBar mb = browserWindow.getJMenuBar();
        if (mb == null) return null;
        for (int i = 0; i < mb.getMenuCount(); i++) {
            JMenu menu = mb.getMenu(i);
            if (menu == null) continue;
            JMenuItem found = findInMenu(menu, text);
            if (found != null) return found;
        }
        return null;
    }

    private JMenuItem findInMenu(JMenu menu, String text) {
        for (Component c : menu.getMenuComponents()) {
            if (c instanceof JMenu) {
                JMenuItem found = findInMenu((JMenu) c, text);
                if (found != null) return found;
            } else if (c instanceof JMenuItem && text.equals(((JMenuItem) c).getText())) {
                return (JMenuItem) c;
            }
        }
        return null;
    }

    private GEditorWindow waitForEditorWindow() throws Exception {
        GEditorWindow found = null;
        for (int i = 0; i < 40 && found == null; i++) {
            Thread.sleep(250);
            for (Window w : Window.getWindows()) {
                if (w instanceof GEditorWindow && w.isVisible()) {
                    found = (GEditorWindow) w;
                    break;
                }
            }
        }
        assertNotNull(found, "Editor window should open");
        return found;
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
