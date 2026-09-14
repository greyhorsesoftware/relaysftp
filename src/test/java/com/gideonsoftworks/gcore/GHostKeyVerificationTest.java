package com.gideonsoftworks.gcore;

import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.schmizz.sshj.common.Buffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration coverage for item 1.1 of the remediation plan: SSH host key
 * verification. Runs against the in-process Apache MINA SSHD server (as the
 * other SFTP tests do), driving GProtocolPluginSFTP through the real sshj
 * verifier with a non-interactive prompt injected in place of the dialog.
 */
@Tag("integration")
class GHostKeyVerificationTest {

    private static SshServer sshd;
    private static int port;

    @TempDir
    static Path tempDir;

    private Path workDir;
    private Path knownHosts;
    private GBookmark bookmark;
    private GConnection conn;
    private GProtocolPluginSFTP plugin;

    @BeforeAll
    static void startServer() throws IOException {
        sshd = SshServer.setUpDefaultServer();
        sshd.setPort(0);
        SimpleGeneratorHostKeyProvider hostKeys =
                new SimpleGeneratorHostKeyProvider(tempDir.resolve("hostkey.ser"));
        hostKeys.setAlgorithm("RSA");
        hostKeys.setKeySize(2048);
        sshd.setKeyPairProvider(hostKeys);
        sshd.setPasswordAuthenticator((u, p, s) -> "testuser".equals(u) && "testpass".equals(p));
        sshd.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
        sshd.start();
        port = sshd.getPort();
    }

    @AfterAll
    static void stopServer() throws IOException {
        if (sshd != null) sshd.stop(true);
    }

    @BeforeEach
    void setUp() throws Exception {
        workDir = Files.createTempDirectory(tempDir, "kh-").toRealPath();
        Files.writeString(workDir.resolve("hello.txt"), "Hello, World!");

        // Isolated, empty known_hosts per test — never touch the user's real file.
        knownHosts = Files.createTempFile(tempDir, "known_hosts", "");
        Files.delete(knownHosts); // start absent; verifier creates it on save
        GKnownHostsVerifier.setKnownHostsFileForTesting(knownHosts.toFile());

        bookmark = new GBookmark("test", "localhost",
                workDir.toAbsolutePath().toString(),
                "testuser", "testpass", port, GBookmark.G_SFTP_PROTOCOL);
        conn = new GConnection(bookmark);
        plugin = new GProtocolPluginSFTP(conn);
    }

    @AfterEach
    void tearDown() {
        try { if (plugin != null) plugin.gpp_disconnect(conn); } catch (Exception ignored) {}
        GKnownHostsVerifier.setPromptForTesting(null);
        GKnownHostsVerifier.setKnownHostsFileForTesting(null);
    }

    private static final class CountingPrompt implements GHostKeyPrompt {
        final Decision decision;
        final AtomicInteger unknownCalls = new AtomicInteger();
        final AtomicInteger changedCalls = new AtomicInteger();
        CountingPrompt(Decision d) { this.decision = d; }
        public Decision promptUnknownKey(String h, String t, String f) { unknownCalls.incrementAndGet(); return decision; }
        public Decision promptChangedKey(String h, String t, String f) { changedCalls.incrementAndGet(); return decision; }
    }

    // (a) First connect with an auto-accept policy succeeds and records the key.
    @Test
    void firstConnect_autoAccept_recordsKey() throws Exception {
        CountingPrompt prompt = new CountingPrompt(GHostKeyPrompt.Decision.ACCEPT_AND_SAVE);
        GKnownHostsVerifier.setPromptForTesting(prompt);

        plugin.gpp_connect(bookmark);

        assertTrue(plugin.getIsConnected(), "connect should succeed on trust-on-first-use accept");
        assertEquals(1, prompt.unknownCalls.get(), "unknown-key prompt should fire exactly once");
        assertEquals(0, prompt.changedCalls.get());
        assertTrue(Files.exists(knownHosts), "known_hosts should be written on accept & save");
        assertFalse(Files.readString(knownHosts).isBlank(), "known_hosts should contain the recorded key");
    }

    // (b) Reconnect with the same key succeeds without prompting.
    @Test
    void reconnect_sameKey_noPrompt() throws Exception {
        // First connection records the key.
        GKnownHostsVerifier.setPromptForTesting(new CountingPrompt(GHostKeyPrompt.Decision.ACCEPT_AND_SAVE));
        plugin.gpp_connect(bookmark);
        assertTrue(plugin.getIsConnected());
        plugin.gpp_disconnect(conn);

        // Second connection must not consult the prompt at all.
        CountingPrompt prompt = new CountingPrompt(GHostKeyPrompt.Decision.REJECT);
        GKnownHostsVerifier.setPromptForTesting(prompt);
        GConnection conn2 = new GConnection(bookmark);
        GProtocolPluginSFTP plugin2 = new GProtocolPluginSFTP(conn2);
        try {
            plugin2.gpp_connect(bookmark);
            assertTrue(plugin2.getIsConnected(), "reconnect with the known key should succeed silently");
            assertEquals(0, prompt.unknownCalls.get(), "no unknown-key prompt on a known host");
            assertEquals(0, prompt.changedCalls.get(), "no changed-key prompt on a matching key");
        } finally {
            try { plugin2.gpp_disconnect(conn2); } catch (Exception ignored) {}
        }
    }

    // (c) A server presenting a different host key is refused.
    @Test
    void changedKey_isRefused() throws Exception {
        // Record the real key first.
        GKnownHostsVerifier.setPromptForTesting(new CountingPrompt(GHostKeyPrompt.Decision.ACCEPT_AND_SAVE));
        plugin.gpp_connect(bookmark);
        assertTrue(plugin.getIsConnected());
        plugin.gpp_disconnect(conn);
        plugin = null;

        // Corrupt the stored key: keep the exact host + key-type tokens sshj wrote,
        // but swap in a different (bogus) RSA key so the host is *known* yet mismatched.
        rewriteStoredKeyWithBogusRsa(knownHosts);

        CountingPrompt prompt = new CountingPrompt(GHostKeyPrompt.Decision.REJECT);
        GKnownHostsVerifier.setPromptForTesting(prompt);

        GConnection conn2 = new GConnection(bookmark);
        GProtocolPluginSFTP plugin2 = new GProtocolPluginSFTP(conn2);
        try {
            assertThrows(GConnectionException.class, () -> plugin2.gpp_connect(bookmark),
                    "a changed host key that the user rejects must fail the connection");
            assertFalse(plugin2.getIsConnected());
            assertEquals(1, prompt.changedCalls.get(), "changed-key prompt should fire on mismatch");
        } finally {
            try { plugin2.gpp_disconnect(conn2); } catch (Exception ignored) {}
        }
    }

    /** Replace the base64 key field of the (single) stored line with a fresh RSA key. */
    private static void rewriteStoredKeyWithBogusRsa(Path khFile) throws Exception {
        List<String> lines = Files.readAllLines(khFile);
        String line = null;
        for (String l : lines) {
            if (!l.isBlank() && !l.startsWith("#")) { line = l; break; }
        }
        assertNotNull(line, "expected a recorded known_hosts entry");
        String[] tok = line.split("\\s+");
        assertTrue(tok.length >= 3, "unexpected known_hosts line: " + line);

        KeyPair kp = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        byte[] wire = new Buffer.PlainBuffer().putPublicKey(kp.getPublic()).getCompactData();
        tok[2] = Base64.getEncoder().encodeToString(wire);

        Files.writeString(khFile, String.join(" ", tok) + "\n");
    }
}
