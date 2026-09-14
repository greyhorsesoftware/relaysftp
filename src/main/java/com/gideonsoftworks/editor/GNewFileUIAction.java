package com.gideonsoftworks.editor;

import com.gideonsoftworks.gideon.core.GFrame;
import com.gideonsoftworks.gideon.core.GFrameManager;
import com.gideonsoftworks.gideon.actions.GUIAction;
import com.gideonsoftworks.gideon.browser.GBrowserPanelController;
import com.gideonsoftworks.gideon.browser.GBrowserWindow;
import com.gideonsoftworks.gcore.GFileItem;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class GNewFileUIAction extends GUIAction {

    public GNewFileUIAction(String name, Icon icon) {
        super(name, icon, GUIAction.GA_NEW_FILE);
    }

    public void actionPerformed(ActionEvent e) {
        GFrameManager theManager = GUIAction.getSharedGFrameManager();
        GFrame keyWindow = theManager.getKeyWindow();

        if (!(keyWindow instanceof GBrowserWindow)) return;

        GBrowserPanelController brw = (GBrowserPanelController)
            ((GBrowserWindow) keyWindow).getKeyBrowser();
        if (brw == null) return;

        final GFileItem destDir = brw.getVirtualCWD();
        if (destDir == null) return;

        GEditorWindow editor = new GEditorWindow(theManager);
        editor.showNewFile(destDir, savedFile -> {
            // refresh the pane so the saved file appears — but only if it is
            // still showing the destination folder
            GFileItem cwd = brw.getVirtualCWD();
            if (cwd != null && cwd.getCompletePath() != null
                && cwd.getCompletePath().equals(destDir.getCompletePath())) {
                brw.refresh(null);
            }
        });
    }
}
