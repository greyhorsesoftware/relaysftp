package com.gideonsoftworks.editor;

import com.gideonsoftworks.gideon.core.GFrame;
import com.gideonsoftworks.gideon.core.GFrameManager;
import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.actions.GUIAction;
import com.gideonsoftworks.gideon.browser.GBrowserPanelController;
import com.gideonsoftworks.gideon.browser.GBrowserWindow;
import com.gideonsoftworks.gcore.GFileItem;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.lang.reflect.Array;

public class GEditUIAction extends GUIAction {

    public GEditUIAction(String name, Icon icon) {
        super(name, icon, GUIAction.GA_EDIT);
    }

    public void actionPerformed(ActionEvent e) {
        GFrameManager theManager = GUIAction.getSharedGFrameManager();
        GFrame keyWindow = theManager.getKeyWindow();

        if (!(keyWindow instanceof GBrowserWindow)) return;

        GBrowserPanelController brw = (GBrowserPanelController)
            ((GBrowserWindow) keyWindow).getKeyBrowser();
        if (brw == null) return;

        GFileItem[] selectedFiles = brw.getSelection();
        if (selectedFiles == null || Array.getLength(selectedFiles) == 0) {
            JOptionPane.showMessageDialog(null, GLocalized.NO_SELECTION);
            return;
        }

        for (int i = 0; i < Array.getLength(selectedFiles); i++) {
            GFileItem cFI = (GFileItem) Array.get(selectedFiles, i);
            if (cFI.getIsDir()) continue;

            GEditorWindow editor = new GEditorWindow(theManager);
            editor.show(cFI);
        }
    }
}
