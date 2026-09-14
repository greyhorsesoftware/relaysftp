package com.gideonsoftworks.ui;

import com.gideonsoftworks.gideon.permissions.GBatchPermissionsWindow;
import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;
import javax.swing.*;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The "Apply to contained items" switch must be honored (roadmap 3.6 fix: the
 * browser used to hardcode RECURSIVE_JOB_PROP to true). OFF -> folder chmods,
 * child untouched. ON -> folder AND child chmod.
 *
 * Toggles worldRead (not owner bits) so the temp tree stays deletable at teardown.
 */
@Tag("ui") class ChmodRecursiveUITest extends UITestBase {

    @Test void test() throws Exception {
        Assumptions.assumeFalse(
            System.getProperty("os.name").toLowerCase().contains("win"),
            "NTFS has no POSIX permissions - Apache SSHD chmod is a no-op there");

        createServerFileInDir("perms_a", "child.txt", "child a");
        createServerFileInDir("perms_b", "child.txt", "child b");
        loginToServer();

        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());

        // --- pass 1: switch OFF -> only the folder changes ---
        chmodFolderRemovingWorldRead(table, "perms_a", false);

        assertFalse(worldCanRead("perms_a"), "perms_a: folder chmod must apply");
        assertTrue(worldCanRead("perms_a/child.txt"),
            "perms_a/child.txt: child must be UNTOUCHED with the switch off");

        // --- pass 2: switch ON -> folder and child change ---
        chmodFolderRemovingWorldRead(table, "perms_b", true);

        assertFalse(worldCanRead("perms_b"), "perms_b: folder chmod must apply");
        assertFalse(worldCanRead("perms_b/child.txt"),
            "perms_b/child.txt: child must change with the switch on");
    }

    private void chmodFolderRemovingWorldRead(JTableFixture table, String folderName,
                                              boolean applyToContained) throws Exception {
        int row = findRowByName(table, folderName);
        assertTrue(row >= 0, folderName + " should be listed");

        rightClickSelectionAndSelectMenuItem(table, new int[]{ row }, "Set Permissions...");
        robot.waitForIdle();
        Thread.sleep(1000);

        GBatchPermissionsWindow permsWindow = null;
        for (Window w : Window.getWindows()) {
            if (w instanceof GBatchPermissionsWindow && w.isVisible()) {
                permsWindow = (GBatchPermissionsWindow) w;
                break;
            }
        }
        assertNotNull(permsWindow, "Permissions window should open");
        FrameFixture permsFixture = new FrameFixture(robot, permsWindow);

        permsFixture.checkBox("worldRead").click();
        robot.waitForIdle();
        if (applyToContained) {
            permsFixture.checkBox("applyToContained").click();
            robot.waitForIdle();
        }

        JobWaiter waiter = waitForJob(GJobDescription.CHMOD_JOB_TYPE);
        permsFixture.button("applyPermissions").click();
        waiter.await(15);
        Thread.sleep(1500); // recursive children chmod inside the same job

        // close the window so the next pass finds a fresh one
        final GBatchPermissionsWindow toClose = permsWindow;
        SwingUtilities.invokeAndWait(() -> { toClose.setVisible(false); toClose.dispose(); });
        robot.waitForIdle();
    }

    private boolean worldCanRead(String relPath) throws Exception {
        Set<PosixFilePermission> perms =
            Files.getPosixFilePermissions(serverRoot.resolve(relPath));
        return perms.contains(PosixFilePermission.OTHERS_READ);
    }
}
