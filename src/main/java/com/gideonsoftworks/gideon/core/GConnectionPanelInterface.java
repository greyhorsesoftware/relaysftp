package com.gideonsoftworks.gideon.core;

import com.gideonsoftworks.gcore.GBookmark;
import javax.swing.JPanel;

public interface GConnectionPanelInterface {
    JPanel getPanel();
    int getProtocolConstant();
    void populateFromBookmark(GBookmark bm);
    void applyToBookmark(GBookmark bm);
    String getCardName();
}
