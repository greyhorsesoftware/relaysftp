package com.gideonsoftworks.ui;

import org.assertj.swing.fixture.JTableFixture;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/** Connect to S3 using temporary credentials (access key + secret + session token). */
@Tag("ui")
class S3SessionTokenLoginUITest extends S3UITestBase {

    @Test
    void test() throws Exception {
        authMethod = "session";
        sessionToken = "FwoGZXIvYXdzEXAMPLEsessiontoken";
        seedObject("temp.txt", "stored via temporary creds");

        loginToServer();

        assertTrue(remoteBrowser().getConnection().getIsConnected(),
            "should connect with session-token credentials");

        JTableFixture table = new JTableFixture(robot, remoteBrowser().getFileTable());
        assertTrue(findRowByName(table, "temp.txt") >= 0, "object should be listed");
    }
}
