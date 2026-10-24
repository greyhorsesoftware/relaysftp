package com.gideonsoftworks.ui;

import org.assertj.swing.data.TableCell;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.JTableFixture;
import org.assertj.swing.timing.Condition;
import org.assertj.swing.timing.Pause;
import org.junit.jupiter.api.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Double-clicking a folder that is already listed (cached) opened it on mouse press, which
 * swapped in the shorter listing before the table UI applied the press-time row on release:
 * the stale row ran past the row sorter's end (ArrayIndexOutOfBoundsException on the EDT).
 */
@Tag("ui") class DoubleClickCachedFolderUITest extends UITestBase {

    @Test void reopeningACachedFolderByDoubleClickDoesNotThrow() throws Exception {
        List<Throwable> edtErrors = new CopyOnWriteArrayList<>();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, ex) -> edtErrors.add(ex));
        try {
            // "sub" sorts after the files, so its row is past the end of its own one-row listing
            for (int i = 0; i < 8; i++) createServerFile("f" + i + ".txt", "x");
            createServerDir("sub");
            createServerFileInDir("sub", "a.txt", "x");
            loginToServer();
            String parent = serverRoot.getFileName().toString();
            JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());

            for (int round = 0; round < 3; round++) {   // round 1 lists "sub"; later rounds reopen it from cache
                table.cell(TableCell.row(findRowByName(table, "sub")).column(0)).doubleClick();
                awaitFolder("sub");

                JButton parentCrumb = GuiActionRunner.execute(() -> crumbNamed(parent));
                SwingUtilities.invokeAndWait(parentCrumb::doClick);
                awaitFolder(parent);
            }
            robot.waitForIdle();
            assertTrue(edtErrors.isEmpty(), "EDT exceptions: " + edtErrors);
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous);
        }
    }

    private void awaitFolder(String name) {
        Pause.pause(new Condition("in folder " + name) {
            @Override public boolean test() {
                return GuiActionRunner.execute(() -> name.equals(remoteBrowser().getVirtualCWD().getName()));
            }
        }, 15_000);
        robot.waitForIdle();
    }

    private JButton crumbNamed(String name) {
        for (Component c : getAllComponents(remoteBrowser().getPanel()))
            if (c instanceof JButton b && name.equals(b.getText()) && SwingUtilities.getAncestorNamed("breadcrumbBar", b) != null) return b;
        throw new AssertionError("no crumb named " + name);
    }
}
