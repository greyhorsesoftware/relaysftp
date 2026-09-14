package com.gideonsoftworks.ui;

import org.assertj.swing.data.TableCell;
import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Connect to an S3 bucket with access key + secret and confirm the browser lists
 * the seeded objects and common-prefix folders.
 */
@Tag("ui")
class S3LoginUITest extends S3UITestBase {

    @Test
    void test() throws Exception {
        seedObject("hello.txt", "hello world");
        seedObject("notes.txt", "some notes");
        seedObject("docs/readme.md", "# readme");   // creates a "docs/" common-prefix folder

        loginToServer();

        assertTrue(remoteBrowser().getConnection().getIsConnected(), "should be connected");

        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
        table.requireVisible();

        assertTrue(findRowByName(table, "hello.txt") >= 0, "hello.txt should be listed");
        assertTrue(findRowByName(table, "notes.txt") >= 0, "notes.txt should be listed");
        assertTrue(findRowByName(table, "docs") >= 0, "docs/ prefix should show as a folder");
    }
}
