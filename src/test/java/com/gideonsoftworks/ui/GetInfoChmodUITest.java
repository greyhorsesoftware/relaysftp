package com.gideonsoftworks.ui;

import com.gideonsoftworks.gideon.getinfo.GGetInfoWindow;
import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.fixture.JCheckBoxFixture;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;
import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui") class GetInfoChmodUITest extends UITestBase {
    @Test void test() throws Exception {
        // Create file with known permissions (644 = rw-r--r--)
        createServerFile("info_chmod.txt", "get info on me");
        new ProcessBuilder("chmod", "644",
            serverRoot.resolve("info_chmod.txt").toAbsolutePath().toString()).start().waitFor();
        loginToServer();

        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
        int row = findRowByName(table, "info_chmod.txt");
        assertTrue(row >= 0);

        // Open Get Info via context menu
        String menuLabel = System.getProperty("os.name").toLowerCase().contains("mac") ? "Get Info" : "Properties";
        rightClickAndSelectMenuItem(table, row, menuLabel);
        robot.waitForIdle();
        Thread.sleep(1000);

        // Find the Get Info window
        GGetInfoWindow infoWindow = null;
        for (Window w : Window.getWindows()) {
            if (w instanceof GGetInfoWindow && w.isVisible()) {
                infoWindow = (GGetInfoWindow) w;
                break;
            }
        }
        assertNotNull(infoWindow, "Get Info window should open");

        FrameFixture infoFixture = new FrameFixture(robot, infoWindow);

        // Click the "Permissions" tab
        JTabbedPane tabbedPane = findTabbedPane(infoWindow);
        assertNotNull(tabbedPane, "Should find tabbed pane");
        final JTabbedPane tp = tabbedPane;
        SwingUtilities.invokeAndWait(() -> tp.setSelectedIndex(1));
        robot.waitForIdle();
        Thread.sleep(500);

        // Verify initial permissions reflect 644 (rw-r--r--)
        JCheckBoxFixture ownerRead = infoFixture.checkBox("ownerRead");
        JCheckBoxFixture ownerWrite = infoFixture.checkBox("ownerWrite");
        JCheckBoxFixture ownerExecute = infoFixture.checkBox("ownerExecute");
        JCheckBoxFixture groupRead = infoFixture.checkBox("groupRead");
        JCheckBoxFixture groupWrite = infoFixture.checkBox("groupWrite");
        JCheckBoxFixture groupExecute = infoFixture.checkBox("groupExecute");
        JCheckBoxFixture worldRead = infoFixture.checkBox("worldRead");
        JCheckBoxFixture worldWrite = infoFixture.checkBox("worldWrite");
        JCheckBoxFixture worldExecute = infoFixture.checkBox("worldExecute");

        ownerRead.requireSelected();
        ownerWrite.requireSelected();
        ownerExecute.requireNotSelected();
        groupRead.requireSelected();
        groupWrite.requireNotSelected();
        groupExecute.requireNotSelected();
        worldRead.requireSelected();
        worldWrite.requireNotSelected();
        worldExecute.requireNotSelected();

        // Change to 755: check owner execute, group execute, world execute
        ownerExecute.click();
        robot.waitForIdle();
        groupExecute.click();
        robot.waitForIdle();
        worldExecute.click();
        robot.waitForIdle();

        // Verify changes
        ownerExecute.requireSelected();
        groupExecute.requireSelected();
        worldExecute.requireSelected();

        // Click Apply
        JobWaiter waiter = waitForJob(GJobDescription.CHMOD_JOB_TYPE);
        infoFixture.button("applyPermissions").click();
        waiter.await(10);

        // Verify on disk: should be 755 (rwxr-xr-x)
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(
            serverRoot.resolve("info_chmod.txt"));

        assertTrue(perms.contains(PosixFilePermission.OWNER_READ));
        assertTrue(perms.contains(PosixFilePermission.OWNER_WRITE));
        assertTrue(perms.contains(PosixFilePermission.OWNER_EXECUTE));
        assertTrue(perms.contains(PosixFilePermission.GROUP_READ));
        assertFalse(perms.contains(PosixFilePermission.GROUP_WRITE));
        assertTrue(perms.contains(PosixFilePermission.GROUP_EXECUTE));
        assertTrue(perms.contains(PosixFilePermission.OTHERS_READ));
        assertFalse(perms.contains(PosixFilePermission.OTHERS_WRITE));
        assertTrue(perms.contains(PosixFilePermission.OTHERS_EXECUTE));

        // Clean up
        final GGetInfoWindow win = infoWindow;
        SwingUtilities.invokeAndWait(() -> {
            win.setVisible(false);
            win.dispose();
        });
    }

    private JTabbedPane findTabbedPane(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof JTabbedPane) return (JTabbedPane) c;
            if (c instanceof Container) {
                JTabbedPane found = findTabbedPane((Container) c);
                if (found != null) return found;
            }
        }
        return null;
    }
}
