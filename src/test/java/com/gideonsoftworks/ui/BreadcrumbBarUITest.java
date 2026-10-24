package com.gideonsoftworks.ui;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.JTableFixture;
import org.assertj.swing.timing.Condition;
import org.assertj.swing.timing.Pause;
import org.junit.jupiter.api.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The path bar shows the current folder as breadcrumbs; clicking one navigates, the wheel scrolls it, "r" refreshes. */
@Tag("ui") class BreadcrumbBarUITest extends UITestBase {

    @Test void breadcrumbsNavigateAndRKeyRefreshes() throws Exception {
        loginToServer();
        String here   = serverRoot.getFileName().toString();
        String parent = serverRoot.getParent().getFileName().toString();

        List<JButton> buttons = GuiActionRunner.execute(this::barButtons);
        assertEquals(here, buttons.get(buttons.size() - 1).getText(), "last crumb is the current folder");
        assertNotNull(buttons.get(0).getIcon(), "first crumb is the home icon");

        // click the parent folder's crumb
        JButton parentCrumb = buttons.stream().filter(b -> parent.equals(b.getText())).findFirst().orElseThrow();
        SwingUtilities.invokeAndWait(parentCrumb::doClick);
        Pause.pause(new Condition("moved to parent folder") {
            @Override public boolean test() {
                return GuiActionRunner.execute(() -> parent.equals(remoteBrowser().getVirtualCWD().getName()));
            }
        }, 15_000);
        List<JButton> after = GuiActionRunner.execute(this::barButtons);
        assertEquals(parent, after.get(after.size() - 1).getText(), "bar now ends at the parent folder");

        // narrow the window so the path overflows: it starts scrolled to the end, the wheel scrolls it back
        Dimension size = browserWindow.getSize();
        SwingUtilities.invokeAndWait(() -> { browserWindow.setSize(420, size.height); browserWindow.validate(); });
        robot.waitForIdle();
        try {
            JComponent row = GuiActionRunner.execute(() -> (JComponent) barButtons().get(0).getParent());
            Container viewport = row.getParent();
            assertTrue(GuiActionRunner.execute(() -> row.getX() < 0), "long path starts with the leading crumbs off the left");
            assertEquals(GuiActionRunner.execute(() -> viewport.getWidth()), GuiActionRunner.execute(() -> row.getX() + row.getWidth()),
                "current folder is flush with the right edge");

            SwingUtilities.invokeAndWait(() -> viewport.dispatchEvent(new MouseWheelEvent(viewport, MouseEvent.MOUSE_WHEEL,
                System.currentTimeMillis(), 0, 5, 5, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, -100)));
            assertEquals(0, GuiActionRunner.execute(row::getX), "scrolling back far enough shows the first crumb");
        } finally {
            SwingUtilities.invokeAndWait(() -> { browserWindow.setSize(size); browserWindow.validate(); });
            robot.waitForIdle();
        }

        // "r" in the file table refreshes the pane
        RefreshCounter refreshes = countRefreshJobs();
        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
        table.focus();
        table.pressAndReleaseKeys(KeyEvent.VK_R);
        Pause.pause(new Condition("refresh job completed") {
            @Override public boolean test() { return refreshes.completed.get() >= 1; }
        }, 15_000);
    }

    private List<JButton> barButtons() {
        Component bar = find(remoteBrowser().getPanel(), "breadcrumbBar");
        assertNotNull(bar, "remote pane should have a breadcrumb bar");
        List<JButton> out = new ArrayList<>();
        for (Component c : getAllComponents((Container) bar)) if (c instanceof JButton b) out.add(b);
        return out;
    }

    private static Component find(Container c, String name) {
        if (name.equals(c.getName())) return c;
        for (Component child : c.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container cc) { Component f = find(cc, name); if (f != null) return f; }
        }
        return null;
    }
}
