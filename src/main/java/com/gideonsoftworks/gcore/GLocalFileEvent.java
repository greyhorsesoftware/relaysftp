package com.gideonsoftworks.gcore;

/**
 * Event fired when local filesystem changes are detected in a watched folder.
 */
public class GLocalFileEvent {

    public static final String LOCAL_FOLDER_CHANGED = "com.gideonsoftworks.gcore.LOCAL_FOLDER_CHANGED";

    private final String eventType;
    private final String changedPath;

    public GLocalFileEvent(String eventType, String changedPath) {
        this.eventType = eventType;
        this.changedPath = changedPath;
    }

    public String getEventType() { return eventType; }
    public String getChangedPath() { return changedPath; }
}
