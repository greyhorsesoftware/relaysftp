package com.gideonsoftworks.ui;

import com.gideonsoftworks.gcore.*;
import com.gideonsoftworks.gja.GJobDescription;
import org.assertj.swing.data.TableCell;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("ui") class LoginUITest extends UITestBase {
    @Test void test() throws Exception {
        createServerFile("hello.txt", "hello world");
        createServerDir("subfolder");

        SwingUtilities.invokeAndWait(() -> {
            GBookmark bm = new GBookmark("test", "localhost",
                toSftpPath(serverRoot), "test", "test",
                sftpPort, GBookmark.G_SFTP_PROTOCOL);
            bm.setNeverSavePassword(true);
            bm.setLocalHomePath(localRoot.toAbsolutePath().toString());
            remoteBrowser().connectForBookmark(bm);
        });
        robot.waitForIdle();

        window.textBox("hostnameField").requireVisible().requireText("localhost");
        window.textBox("usernameField").requireText("test");

        JobWaiter waiter = waitForJob(GJobDescription.LOGIN_JOB_TYPE);
        window.button("connectButton").click();
        waiter.await(15);

        assertTrue(remoteBrowser().getConnection().getIsConnected());
        GFileItem cwd = remoteBrowser().getVirtualCWD();
        assertNotNull(cwd);
        assertTrue(cwd.getChildCount() > 0);

        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
        table.requireVisible();
        table.cell(TableCell.row(0).column(0)).click();
        robot.waitForIdle();
    }
}
