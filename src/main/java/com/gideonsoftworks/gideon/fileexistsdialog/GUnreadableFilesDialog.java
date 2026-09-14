package com.gideonsoftworks.gideon.fileexistsdialog;

import com.gideonsoftworks.gja.GJobUserMessage;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Utility class for showing unreadable files warning via JOptionPane.
 */
public class GUnreadableFilesDialog {

    public static void showDialog(Frame owner, GJobUserMessage m) {
        List<String> paths = m.getProblemPaths();
        int count = paths.size();

        StringBuilder sb = new StringBuilder();
        sb.append("<html><b>Permission Denied</b><br><br>");
        sb.append(count).append(count == 1 ? " file" : " files");
        sb.append(" could not be read from the server.<br>");
        sb.append("The owner does not have read access.<br><br>");

        for (String path : paths) {
            String displayPath = path;
            String perms = "";
            int parenIdx = path.lastIndexOf("  (");
            if (parenIdx > 0) {
                displayPath = path.substring(0, parenIdx);
                perms = path.substring(parenIdx + 2);
            }

            int lastSlash = displayPath.lastIndexOf('/');
            String fileName = lastSlash >= 0 ? displayPath.substring(lastSlash + 1) : displayPath;
            String parentPath = lastSlash >= 0 ? displayPath.substring(0, lastSlash + 1) : "";

            sb.append("&nbsp;&nbsp;<b>").append(esc(fileName)).append("</b>");
            if (!perms.isEmpty()) {
                sb.append(" <font color='#999999'><i>").append(esc(perms)).append("</i></font>");
            }
            sb.append("<br>");
            if (!parentPath.isEmpty()) {
                sb.append("&nbsp;&nbsp;<font color='#aaaaaa' size='-2'>").append(esc(parentPath)).append("</font><br>");
            }
        }

        sb.append("<br>Skip ").append(count == 1 ? "this file" : "these files");
        sb.append(" and continue downloading?</html>");

        String[] options = { "Skip Files", "Cancel Transfer" };
        int result = JOptionPane.showOptionDialog(
            owner, sb.toString(), "Unreadable Files",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null, options, options[0]);

        m.setAction(result == 0
            ? GJobUserMessage.SKIP_ACTION
            : GJobUserMessage.CANCEL_ACTION);
        m.setUserThinking(false);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
