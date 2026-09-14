package com.gideonsoftworks.ui;

import com.gideonsoftworks.gideon.widgets.GPreviewWindow;
import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Previewing a remote image queues the download on the BROWSER's job manager
 * (so it shows in the Activity panel) and opens the preview window only after
 * the download completes.
 */
@Tag("ui") class PreviewUITest extends UITestBase {
    @Test void test() throws Exception {
        // a real PNG on the server
        BufferedImage img = new BufferedImage(32, 24, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, 32, 24);
        g.dispose();
        ImageIO.write(img, "PNG", serverRoot.resolve("pic.png").toFile());

        loginToServer();

        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
        int row = findRowByName(table, "pic.png");
        assertTrue(row >= 0);

        // the download must run on the browser window's job manager — the one the
        // Activity panel watches (previously it ran on the preview window's own)
        JobWaiter downloadWaiter = waitForJob(GJobDescription.DOWNLOAD_JOB_TYPE);

        rightClickAndSelectMenuItem(table, row, "Preview");

        downloadWaiter.await(15);

        // window opens only after the download, straight onto the loaded image
        GPreviewWindow preview = null;
        for (int i = 0; i < 40 && preview == null; i++) {
            Thread.sleep(250);
            for (Window w : Window.getWindows()) {
                if (w instanceof GPreviewWindow && w.isVisible()) {
                    preview = (GPreviewWindow) w;
                    break;
                }
            }
        }
        assertNotNull(preview, "Preview window should open after the download completes");
        assertEquals("Previewing \"pic.png\"", preview.getTitle());
        assertNotNull(preview.getFileItem());
        assertEquals("pic.png", preview.getFileItem().getName());

        final GPreviewWindow toClose = preview;
        SwingUtilities.invokeAndWait(() -> { toClose.setVisible(false); toClose.dispose(); });
    }
}
