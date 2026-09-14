package com.gideonsoftworks.gideon.find;

import com.gideonsoftworks.editor.GFindResult;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

/**
 * Inline find/replace bar that sits between toolbar and editor content.
 * Styled like modern editors (VS Code, Chrome).
 */
public class GInlineFindBar extends JPanel {

    public interface FindTarget {
        GFindResult find(String text);
        GFindResult findPrevious(String text);
        void highlightFindResult(GFindResult result);
        void replace(String findText, String replaceText);
    }

    private final JTextField findField;
    private final JTextField replaceField;
    private JPanel replaceRow;
    private final JLabel matchLabel;
    private FindTarget target;
    private boolean replaceVisible = false;

    public GInlineFindBar() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")),
            new EmptyBorder(4, 8, 4, 8)
        ));

        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));

        // Find row
        JPanel findRow = new JPanel(new BorderLayout(4, 0));
        findRow.setOpaque(false);

        JButton toggleReplace = new JButton("\u25B6");
        toggleReplace.setFont(toggleReplace.getFont().deriveFont(9f));
        toggleReplace.setFocusable(false);
        toggleReplace.setBorderPainted(false);
        toggleReplace.setContentAreaFilled(false);
        toggleReplace.setPreferredSize(new Dimension(16, 24));
        toggleReplace.setToolTipText("Toggle Replace");
        toggleReplace.addActionListener(e -> {
            replaceVisible = !replaceVisible;
            toggleReplace.setText(replaceVisible ? "\u25BC" : "\u25B6");
            replaceRow.setVisible(replaceVisible);
            revalidate();
        });

        findField = new JTextField();
        findField.setPreferredSize(new Dimension(220, 24));
        findField.putClientProperty("JTextField.placeholderText", "Find");
        findField.putClientProperty("JComponent.roundRect", true);

        JButton prevBtn = createSmallButton("\u25C0", "Previous (Shift+Enter)");
        JButton nextBtn = createSmallButton("\u25B6", "Next (Enter)");
        JButton closeBtn = createSmallButton("\u00D7", "Close (Escape)");

        matchLabel = new JLabel("");
        matchLabel.setFont(matchLabel.getFont().deriveFont(11f));
        matchLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        JPanel findButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        findButtons.setOpaque(false);
        findButtons.add(prevBtn);
        findButtons.add(nextBtn);
        findButtons.add(matchLabel);

        findRow.add(toggleReplace, BorderLayout.WEST);
        findRow.add(findField, BorderLayout.CENTER);
        findRow.add(findButtons, BorderLayout.EAST);

        // Replace row
        replaceRow = new JPanel(new BorderLayout(4, 0));
        replaceRow.setOpaque(false);
        replaceRow.setBorder(new EmptyBorder(4, 0, 0, 0));
        replaceRow.setVisible(false);

        // Spacer to align with find field (same width as toggle button)
        replaceRow.add(Box.createRigidArea(new Dimension(16, 0)), BorderLayout.WEST);

        replaceField = new JTextField();
        replaceField.setPreferredSize(new Dimension(220, 24));
        replaceField.putClientProperty("JTextField.placeholderText", "Replace");
        replaceField.putClientProperty("JComponent.roundRect", true);

        JButton replaceBtn = createSmallButton("Replace", null);
        JButton replaceAllBtn = createSmallButton("All", null);

        JPanel replaceButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        replaceButtons.setOpaque(false);
        replaceButtons.add(replaceBtn);
        replaceButtons.add(replaceAllBtn);

        replaceRow.add(replaceField, BorderLayout.CENTER);
        replaceRow.add(replaceButtons, BorderLayout.EAST);

        rows.add(findRow);
        rows.add(replaceRow);
        add(rows, BorderLayout.CENTER);
        add(closeBtn, BorderLayout.EAST);

        // Actions
        findField.addActionListener(e -> doFind());
        findField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "findPrev");
        findField.getActionMap().put("findPrev", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { doFindPrevious(); }
        });

        nextBtn.addActionListener(e -> doFind());
        prevBtn.addActionListener(e -> doFindPrevious());
        replaceBtn.addActionListener(e -> doReplace());
        replaceAllBtn.addActionListener(e -> doReplaceAll());
        closeBtn.addActionListener(e -> close());

        // Escape closes
        InputMap im = getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        getActionMap().put("close", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { close(); }
        });

        setVisible(false);
    }

    public void setFindTarget(FindTarget target) {
        this.target = target;
    }

    public void open() {
        setVisible(true);
        findField.requestFocusInWindow();
        findField.selectAll();
    }

    public void close() {
        setVisible(false);
        matchLabel.setText("");
    }

    public boolean isOpen() {
        return isVisible();
    }

    private void doFind() {
        if (target == null) return;
        String text = findField.getText();
        if (text.isEmpty()) { matchLabel.setText(""); return; }

        GFindResult result = target.find(text);
        if (result != null) {
            target.highlightFindResult(result);
            matchLabel.setText("");
        } else {
            matchLabel.setText("No results");
        }
    }

    private void doFindPrevious() {
        if (target == null) return;
        String text = findField.getText();
        if (text.isEmpty()) return;

        GFindResult result = target.findPrevious(text);
        if (result != null) {
            target.highlightFindResult(result);
            matchLabel.setText("");
        } else {
            matchLabel.setText("No results");
        }
    }

    private void doReplace() {
        if (target == null) return;
        String findText = findField.getText();
        String replaceText = replaceField.getText();
        if (findText.isEmpty()) return;

        target.replace(findText, replaceText);
        doFind(); // Move to next match
    }

    private void doReplaceAll() {
        if (target == null) return;
        String findText = findField.getText();
        String replaceText = replaceField.getText();
        if (findText.isEmpty()) return;

        int count = 0;
        GFindResult result = target.find(findText);
        while (result != null) {
            target.highlightFindResult(result);
            target.replace(findText, replaceText);
            count++;
            result = target.find(findText);
        }
        matchLabel.setText(count > 0 ? count + " replaced" : "No results");
    }

    private JButton createSmallButton(String text, String tooltip) {
        JButton btn = new JButton(text);
        btn.setFont(btn.getFont().deriveFont(11f));
        btn.setFocusable(false);
        btn.putClientProperty("JButton.buttonType", "toolBarButton");
        btn.setMargin(new Insets(2, 6, 2, 6));
        if (tooltip != null) btn.setToolTipText(tooltip);
        return btn;
    }
}
