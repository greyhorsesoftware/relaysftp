package com.gideonsoftworks.gideon.core;

import com.gideonsoftworks.gcore.GAuditLog;
import com.gideonsoftworks.gcore.GBookmarkManager;
import com.gideonsoftworks.gja.GTransferFilterManager;

public class GAppLifecycleHandler implements GAppLifecycleListenerInterface {

    @Override
    public void al_appWillQuit() {
        GAuditLog.lifecycle("stop");
        GBookmarkManager.saveBookmarks();
        GTransferFilterManager.saveTransferFilters();
    }
}
