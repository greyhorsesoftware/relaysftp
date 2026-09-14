package com.gideonsoftworks.gideon.actions;

import com.gideonsoftworks.gcore.GDiagnostics;
import com.gideonsoftworks.gideon.core.GLinkOpener;
import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.core.GValidationInterface;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;

public class GHelpAction extends GUIAction implements GValidationInterface {

    /** Target of Help > Home. */
    public static final String HOME_URL = "https://www.greyhorsesoftware.com/relaysftp.html";

    public GHelpAction(String name, int code) {
        super(name, code);
    }

    public void actionPerformed(ActionEvent e) {
        switch (this.getGideonActionCode()) {
            case GUIAction.GA_RELAYHOME:
                GLinkOpener.shared().openUrl(HOME_URL);
                break;

            case GUIAction.GA_EXPORT_DIAGNOSTICS:
                exportDiagnostics();
                break;
        }
    }

    private void exportDiagnostics() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("RelaySFTP-diagnostics.zip"));
        if (fc.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            GDiagnostics.exportTo(fc.getSelectedFile());
            JOptionPane.showMessageDialog(null,
                GLocalized.DIAGNOSTICS_SAVED + "\n" + fc.getSelectedFile().getAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null,
                GLocalized.DIAGNOSTICS_FAILED + " " + ex.getMessage());
        }
    }

    public boolean validateItem() {
        return true;
    }
}
