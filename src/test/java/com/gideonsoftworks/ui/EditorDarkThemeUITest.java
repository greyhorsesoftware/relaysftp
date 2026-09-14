package com.gideonsoftworks.ui;

import com.gideonsoftworks.editor.GEditorPanelController;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.junit.jupiter.api.*;
import javax.swing.*;
import java.awt.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Under a dark LaF the editor applies RSyntaxTextArea's bundled dark theme
 * instead of glowing bright white.
 */
@Tag("ui") class EditorDarkThemeUITest {

    @Test void darkLafGetsDarkEditorBackground() throws Exception {
        final Color[] background = new Color[1];
        SwingUtilities.invokeAndWait(() -> {
            com.formdev.flatlaf.FlatDarkLaf.setup();
            GEditorPanelController controller = new GEditorPanelController();
            RSyntaxTextArea textArea = findTextArea(controller.getPanel());
            assertNotNull(textArea, "Should find the RSyntaxTextArea");
            background[0] = textArea.getBackground();

            // current-line highlight is on and distinguishable from the background
            assertTrue(textArea.getHighlightCurrentLine(), "Current-line highlight should be enabled");
            assertNotEquals(textArea.getBackground(), textArea.getCurrentLineHighlightColor(),
                "Current-line highlight color should differ from the background");
        });

        Color bg = background[0];
        int luminance = (bg.getRed() + bg.getGreen() + bg.getBlue()) / 3;
        assertTrue(luminance < 100,
            "Editor background should be dark under a dark LaF, got " + bg);
    }

    private static RSyntaxTextArea findTextArea(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof RSyntaxTextArea) return (RSyntaxTextArea) c;
            if (c instanceof Container) {
                RSyntaxTextArea found = findTextArea((Container) c);
                if (found != null) return found;
            }
        }
        return null;
    }
}
