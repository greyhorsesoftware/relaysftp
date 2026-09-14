package com.gideonsoftworks.gcore;

/**
 * Tracks runtime capabilities and stats for a connection.
 */
public class GConnectionStats {

    // Capability names
    public static final String CAP_MTIME = "mtime";
    public static final String CAP_WARP_LIST = "warp-list";

    // Capabilities are written by the probe on the login-worker thread and read on the EDT
    // (e.g. the mtime-timer start decision), so they must be volatile to be visible.
    private volatile boolean mtimeSupported = false;
    private volatile boolean mtimeChecked = false;
    private volatile String lastMtimeResponse = "";

    private volatile boolean warpListSupported = false;
    private volatile boolean warpListChecked = false;

    private String hostname = "";
    private String protocol = "";
    private int port = 0;
    private long connectedSince = 0;

    // --- Mtime ---

    public boolean isMtimeSupported() { return mtimeSupported; }
    public void setMtimeSupported(boolean supported) {
        mtimeSupported = supported;
        mtimeChecked = true;
    }
    public boolean isMtimeChecked() { return mtimeChecked; }

    public String getLastMtimeResponse() { return lastMtimeResponse; }
    public void setLastMtimeResponse(String response) { lastMtimeResponse = response; }

    // --- Warp List ---

    public boolean isWarpListSupported() { return warpListSupported; }
    public void setWarpListSupported(boolean supported) {
        warpListSupported = supported;
        warpListChecked = true;
    }
    public boolean isWarpListChecked() { return warpListChecked; }

    // --- Connection Info ---

    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public long getConnectedSince() { return connectedSince; }
    public void setConnectedSince(long time) { this.connectedSince = time; }

    // --- Unified capability check ---

    /**
     * Check if a capability is supported by this connection.
     * Returns false if the capability hasn't been tested yet.
     */
    public boolean hasCapability(String capability) {
        return switch (capability) {
            case CAP_MTIME -> mtimeChecked && mtimeSupported;
            case CAP_WARP_LIST -> warpListChecked && warpListSupported;
            default -> false;
        };
    }
}
