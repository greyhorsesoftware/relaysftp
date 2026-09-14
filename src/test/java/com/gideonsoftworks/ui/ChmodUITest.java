package com.gideonsoftworks.ui;

import com.gideonsoftworks.gideon.permissions.GBatchPermissionsWindow;
import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.core.MouseButton;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.fixture.JTableFixture;
import org.assertj.swing.data.TableCell;
import org.junit.jupiter.api.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui") class ChmodUITest extends UITestBase {
    @Test void test() throws Exception {
        // Create 5 files on the server
        createServerFile("chmod1.txt", "one");
        createServerFile("chmod2.txt", "two");
        createServerFile("chmod3.txt", "three");
        createServerFile("chmod4.txt", "four");
        createServerFile("chmod5.txt", "five");
        loginToServer();

        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());

        // Select all 5 files using Cmd/Ctrl+click
        int shortcutMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        int keyCode = (shortcutMask == KeyEvent.META_DOWN_MASK)
            ? KeyEvent.VK_META : KeyEvent.VK_CONTROL;

        int row1 = findRowByName(table, "chmod1.txt");
        int row2 = findRowByName(table, "chmod2.txt");
        int row3 = findRowByName(table, "chmod3.txt");
        int row4 = findRowByName(table, "chmod4.txt");
        int row5 = findRowByName(table, "chmod5.txt");
        assertTrue(row1 >= 0 && row2 >= 0 && row3 >= 0 && row4 >= 0 && row5 >= 0,
            "Should find all 5 chmod files");

        // Click first file
        table.cell(TableCell.row(row1).column(0)).click(MouseButton.LEFT_BUTTON);
        robot.waitForIdle();

        // Cmd/Ctrl+click remaining files to add to selection
        // Hold modifier key down for all clicks
        robot.pressKey(keyCode);
        try {
            for (int row : new int[]{ row2, row3, row4, row5 }) {
                clickRow(table, row, MouseButton.LEFT_BUTTON);   // assertj robot, active window, waits for idle
            }
        } finally {
            robot.releaseKey(keyCode);
        }
        robot.waitForIdle();

        // Verify 5 rows selected
        int[] selected = table.target().getSelectedRows();
        assertEquals(5, selected.length, "Should have 5 files selected");

        // Right-click on selection to open context menu
        table.cell(TableCell.row(row1).column(0)).click(MouseButton.RIGHT_BUTTON);
        robot.waitForIdle();
        Thread.sleep(500);

        JMenuItem item = findPopupMenuItem("Set Permissions...");
        assertNotNull(item, "Should find Set Permissions...");
        robot.click(item);
        robot.waitForIdle();
        Thread.sleep(1000);

        // Find permissions window
        GBatchPermissionsWindow permsWindow = null;
        for (Window w : Window.getWindows()) {
            if (w instanceof GBatchPermissionsWindow && w.isVisible()) {
                permsWindow = (GBatchPermissionsWindow) w;
                break;
            }
        }
        assertNotNull(permsWindow, "Permissions window should open");

        FrameFixture permsFixture = new FrameFixture(robot, permsWindow);

        // Uncheck owner write to make files read-only
        permsFixture.checkBox("ownerWrite").click();
        robot.waitForIdle();

        // Click Apply — queues chmod for all 5 files
        JobWaiter waiter1 = waitForJob(GJobDescription.CHMOD_JOB_TYPE);
        permsFixture.button("applyPermissions").click();

        waiter1.await(15);
        Thread.sleep(2000); // Let remaining jobs complete

        boolean onWindows = System.getProperty("os.name").toLowerCase().contains("win");

        for (String name : new String[]{ "chmod1.txt", "chmod2.txt", "chmod3.txt",
                                         "chmod4.txt", "chmod5.txt" }) {
            if (onWindows) {
                // NTFS doesn't support POSIX permissions — Apache SSHD chmod is a no-op.
                // Just verify the file still exists (the chmod didn't error out).
                assertTrue(Files.exists(serverRoot.resolve(name)),
                    name + ": should still exist after chmod");
            } else {
                Set<PosixFilePermission> perms = Files.getPosixFilePermissions(
                    serverRoot.resolve(name));
                assertFalse(perms.contains(PosixFilePermission.OWNER_WRITE),
                    name + ": owner should NOT have write");
                assertTrue(perms.contains(PosixFilePermission.OWNER_READ),
                    name + ": owner should have read");
            }
        }
    }
}
