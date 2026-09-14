package com.gideonsoftworks.editor;

import com.gideonsoftworks.gideon.core.GFrame;
import com.gideonsoftworks.gideon.core.GFrameManager;
import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.find.GInlineFindBar;
import com.gideonsoftworks.gcore.GFileItem;
import com.gideonsoftworks.gcore.GTemporaryFileItem;
import com.gideonsoftworks.gja.GJobDescription;
import com.gideonsoftworks.gja.GJobManagerEvent;
import com.gideonsoftworks.gja.GAbstractJobManagerListener;

import javax.swing.*;
import javax.swing.undo.UndoManager;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import java.awt.*;
import java.io.*;

/**
 * Self-contained text editor window. Has its own job manager
 * for downloading/uploading files. No external dependencies.
 */
public class GEditorWindow extends GFrame implements UndoableEditListener {

    private GEditorPanelController editorPanelController;
    private GInlineFindBar inlineFindBar;
    private JPanel editorContentPanel;
    private UndoManager undoManager = new UndoManager();
    private GFileItem fileItem;
    private GFileItem remoteFileItem; // original remote file for save-back
    private GFileItem newFileDestDir; // destination folder for a New File... buffer
    private GEditorSaveListener saveListener;
    private GTemporaryFileItem tempFile;
    private boolean fileEdited = false;
    private JLabel statusLabel;
    private Timer statusResetTimer;
    private JPanel overlayPanel;

    public GEditorWindow(GFrameManager m) {
        super(m);

        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        java.awt.Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(
            GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration());
        int height = (int)(dim.getHeight()) - insets.top - insets.bottom - 60;
        setSize(880, height);
        setLocation(10, 10);

        setShowsMenu(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        // Listen for job completions
        getJobManager().addStatusListener(new GAbstractJobManagerListener() {
            @Override
            public void jm_jobCompleted(GJobManagerEvent e) {
                GJobDescription jd = e.getJobDescription();
                if (jd.getOriginator() != GEditorWindow.this) return;

                if (jd.isDownloadJob()) {
                    loadFileItem(tempFile);
                } else if (jd.isUploadJob()) {
                    hideOverlay();
                    clearDirtyFlags();
                    editorPanelController.setEditable(true);
                    setStatus("Saved");
                    notifySaved(remoteFileItem);
                }
            }

            @Override
            public void jm_jobFailed(GJobManagerEvent e) {
                GJobDescription jd = e.getJobDescription();
                if (jd.getOriginator() != GEditorWindow.this) return;

                String error = "An error occurred.";
                if (jd.getJobStatus() != null && jd.getJobStatus().getException() != null) {
                    error = jd.getJobStatus().getException().getMessage();
                }

                if (jd.isDownloadJob()) {
                    hideOverlay();
                    setStatus("Download failed");
                    JOptionPane.showMessageDialog(GEditorWindow.this,
                        error, "Download Failed", JOptionPane.ERROR_MESSAGE);
                    dispose();
                } else if (jd.isUploadJob()) {
                    hideOverlay();
                    editorPanelController.setEditable(true);
                    setStatus("Save failed");
                    JOptionPane.showMessageDialog(GEditorWindow.this,
                        error, "Save Failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    @Override
    public boolean hasToolbar() { return false; }

    @Override
    public boolean needsJobManager() { return true; }

    @Override
    public JComponent getContentView(com.gideonsoftworks.tabkit.GTabBarItem tbi) {
        editorPanelController = new GEditorPanelController();
        editorPanelController.setEditable(false);
        editorPanelController.setUndoRedoListener(this);

        inlineFindBar = new GInlineFindBar();
        inlineFindBar.setFindTarget(new GInlineFindBar.FindTarget() {
            private GFindResult lastResult = null;

            public GFindResult find(String text) {
                lastResult = doFind(text, lastResult != null ? lastResult.getEndIndex() : 0);
                if (lastResult == null) lastResult = doFind(text, 0);
                return lastResult;
            }

            public GFindResult findPrevious(String text) {
                int curPos = lastResult != null ? lastResult.getStartIndex() : 0;
                GFindResult prev = null;
                GFindResult r = doFind(text, 0);
                while (r != null && r.getStartIndex() < curPos) {
                    prev = r;
                    r = doFind(text, r.getEndIndex());
                }
                if (prev != null) lastResult = prev;
                return prev;
            }

            public void highlightFindResult(GFindResult result) {
                editorPanelController.select(result.getStartIndex(), result.getEndIndex());
            }

            public void replace(String findText, String replaceText) {
                editorPanelController.replaceSelection(replaceText);
                lastResult = null;
            }
        });

        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")),
            BorderFactory.createEmptyBorder(2, 8, 2, 8)));

        statusResetTimer = new Timer(3000, e -> {
            if (fileItem != null) {
                statusLabel.setText(fileItem.getCompletePath());
            } else {
                statusLabel.setText(" ");
            }
        });
        statusResetTimer.setRepeats(false);

        editorContentPanel = new JPanel(new BorderLayout());
        editorContentPanel.add(inlineFindBar, BorderLayout.NORTH);
        editorContentPanel.add(editorPanelController.getPanel(), BorderLayout.CENTER);
        editorContentPanel.add(statusLabel, BorderLayout.SOUTH);
        return editorContentPanel;
    }

    // --- Show / open file ---

    /**
     * Open a file for editing. Handles both local and remote files.
     * Remote files are downloaded to a temp file first.
     */
    public void show(GFileItem fItem) {
        if (fItem == null) throw new NullPointerException("show() - file item was null");

        super.show();
        toFront();
        requestFocus();

        if (fItem.getIsFileLocal()) {
            loadFileItem(fItem);
        } else {
            // Remote file — download to temp first
            remoteFileItem = fItem;
            String ext = fItem.getFileNameExtension();
            tempFile = new GTemporaryFileItem(ext);

            setTitle(fItem.getName());
            showOverlay("Downloading " + fItem.getName() + "...");

            GJobDescription downloadJob = new GJobDescription(
                GJobDescription.DOWNLOAD_JOB_TYPE, fItem.getConnection(),
                GLocalized.DOWNLOAD_JOB_TYPE + " \"" + fItem.getName() + "\"");
            downloadJob.setTargetFile(fItem);
            downloadJob.setSourceFile(tempFile);
            downloadJob.setOriginator(this);
            downloadJob.addProperty(GJobDescription.TRANSFERMODE_JOB_PROP, 1);

            getJobManager().getTransferQueue().addJobToQueue(downloadJob);
            getJobManager().startNextJobForQueue(getJobManager().getTransferQueue());
        }
    }

    /**
     * Open the editor with an empty, unsaved buffer. On first save the user
     * is prompted for a name and the file is written to destDir (local or remote).
     * The listener (may be null) is notified after each successful save, with
     * the saved destination item as context — the caller decides what to do
     * with it (e.g. refresh the pane that is showing the destination folder).
     */
    public void showNewFile(GFileItem destDir, GEditorSaveListener listener) {
        if (destDir == null) throw new NullPointerException("showNewFile() - destination was null");

        newFileDestDir = destDir;
        saveListener = listener;

        super.show();
        toFront();
        requestFocus();

        setTitle("Untitled");
        editorPanelController.setEditable(true);
        editorPanelController.grabFocus();
        clearDirtyFlags();
    }

    private void loadFileItem(GFileItem fItem) {
        this.fileItem = fItem;
        editorPanelController.loadFile(fItem);

        fItem.setEOLCharacters(System.getProperty("line.separator"));

        if (remoteFileItem != null) {
            setTitle(remoteFileItem.getName());
        } else {
            setTitle(fItem.getName());
        }

        if (GLocalized.onMacOSX) {
            getRootPane().putClientProperty("Window.documentFile",
                new File(fItem.getCompletePath()));
        }

        hideOverlay();
        editorPanelController.setEditable(true);
        editorPanelController.grabFocus();
        clearDirtyFlags();
        setStatus("Loaded");
    }

    // --- Save ---

    public void save() {
        if (fileItem == null && newFileDestDir != null && !nameNewFile()) return;
        if (fileItem == null) return;

        editorPanelController.setEditable(false);

        try {
            saveTextToFile(fileItem);
        } catch (IOException e) {
            editorPanelController.setEditable(true);
            JOptionPane.showMessageDialog(this,
                e.getMessage(), "Save Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (remoteFileItem != null) {
            // Remote file — upload back
            showOverlay("Uploading...");

            GJobDescription uploadJob = new GJobDescription(
                GJobDescription.UPLOAD_JOB_TYPE, remoteFileItem.getConnection(),
                GLocalized.UPLOAD_JOB_TYPE + " \"" + remoteFileItem.getName() + "\"");
            uploadJob.setTargetFile(fileItem);
            uploadJob.setSourceFile(remoteFileItem);
            uploadJob.setOriginator(this);
            uploadJob.addProperty(GJobDescription.TRANSFERMODE_JOB_PROP, 1);
            uploadJob.addProperty(GJobDescription.IGNORE_COLLISIONS_PROP, true);

            getJobManager().getTransferQueue().addJobToQueue(uploadJob);
            getJobManager().startNextJobForQueue(getJobManager().getTransferQueue());
        } else {
            // Local file — already saved
            editorPanelController.setEditable(true);
            clearDirtyFlags();
            setStatus("Saved");
            notifySaved(fileItem);
        }
    }

    private void notifySaved(GFileItem savedFile) {
        if (saveListener != null && savedFile != null) {
            saveListener.fileSaved(savedFile);
        }
    }

    /**
     * First save of a New File... buffer: ask for a name and set up the file
     * items so the normal save path can run. Returns false if cancelled.
     */
    private boolean nameNewFile() {
        String name = JOptionPane.showInputDialog(this, GLocalized.NAME_FILE_STRING,
            GLocalized.NEW_FILE_TITLE, JOptionPane.QUESTION_MESSAGE);
        if (name == null) return false;
        name = name.trim();
        if (name.length() == 0) return false;

        if (newFileDestDir.getIsFileLocal()) {
            File newFile = new File(newFileDestDir.getCompletePath(), name);
            if (newFile.exists()) {
                int rep = JOptionPane.showConfirmDialog(this,
                    "\"" + name + "\" already exists. Replace it?",
                    GLocalized.NEW_FILE_TITLE, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (rep != JOptionPane.YES_OPTION) return false;
            }
            fileItem = new GFileItem(newFile);
        } else {
            int dot = name.lastIndexOf('.');
            String ext = (dot > 0 && dot < name.length() - 1) ? name.substring(dot + 1) : null;
            tempFile = new GTemporaryFileItem(ext);
            fileItem = tempFile;

            // fileType defaults to G_FOLDER; the upload plugin would mkdir without this
            remoteFileItem = new GFileItem(name,
                GFileItem.completePathToChild(newFileDestDir, name), newFileDestDir.getConnection());
            remoteFileItem.setFileType(GFileItem.G_FILE);
        }

        setTitle(name);
        return true;
    }

    private void saveTextToFile(GFileItem fItem) throws IOException {
        String text = editorPanelController.getText();
        String eol = fItem.getEOLCharacters();
        if (eol == null) eol = System.getProperty("line.separator");

        try (FileWriter fw = new FileWriter(fItem.getCompletePath())) {
            String[] lines = text.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                fw.write(lines[i]);
                if (i < lines.length - 1) fw.write(eol);
            }
        }
    }

    // --- Find ---

    public void find() {
        if (inlineFindBar != null) {
            if (inlineFindBar.isOpen()) {
                inlineFindBar.close();
            } else {
                inlineFindBar.open();
            }
        }
    }

    private GFindResult doFind(String findString, int fromIndex) {
        String text = editorPanelController.getText();
        if (text == null) return null;

        String searchText = text.toUpperCase();
        String searchFor = findString.toUpperCase();

        int index = searchText.indexOf(searchFor, fromIndex);
        if (index == -1) return null;

        return new GFindResult(index, index + searchFor.length(), findString);
    }

    // --- Dirty state ---

    public void clearDirtyFlags() {
        if (GLocalized.onMacOSX) {
            getRootPane().putClientProperty("Window.documentModified", Boolean.FALSE);
            getRootPane().putClientProperty("windowModified", Boolean.FALSE);
        }
        fileEdited = false;
    }

    private void activateDirtyFlags() {
        if (GLocalized.onMacOSX) {
            getRootPane().putClientProperty("Window.documentModified", Boolean.TRUE);
            getRootPane().putClientProperty("windowModified", Boolean.TRUE);
        }
        fileEdited = true;
    }

    // --- Undo/Redo ---

    @Override
    public void undoableEditHappened(UndoableEditEvent e) {
        undoManager.addEdit(e.getEdit());
        activateDirtyFlags();
    }

    public void undo() {
        if (undoManager.canUndo()) undoManager.undo();
    }

    public void redo() {
        if (undoManager.canRedo()) undoManager.redo();
    }

    // --- Status bar ---

    private void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusResetTimer.restart();
        }
    }

    // --- Overlay spinner ---

    private void showOverlay(String message) {
        if (overlayPanel == null) {
            overlayPanel = new JPanel(new GridBagLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    g.setColor(new Color(255, 255, 255, 180));
                    g.fillRect(0, 0, getWidth(), getHeight());
                    super.paintComponent(g);
                }
            };
            overlayPanel.setOpaque(false);
        }
        overlayPanel.removeAll();

        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setOpaque(false);

        JProgressBar spinner = new JProgressBar();
        spinner.setIndeterminate(true);
        spinner.setPreferredSize(new Dimension(200, 5));
        spinner.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel label = new JLabel(message);
        label.setFont(label.getFont().deriveFont(13f));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);

        inner.add(label);
        inner.add(Box.createVerticalStrut(8));
        inner.add(spinner);

        overlayPanel.add(inner);

        setGlassPane(overlayPanel);
        overlayPanel.setVisible(true);
    }

    private void hideOverlay() {
        if (overlayPanel != null) {
            overlayPanel.setVisible(false);
        }
    }

    // --- Accessors ---

    public GFileItem getFileItem() { return fileItem; }
    public GEditorPanelController getFocusedEditor() { return editorPanelController; }

    // --- Window close ---

    @Override
    public void windowClosing(java.awt.event.WindowEvent e) {
        if (fileEdited) {
            int result = JOptionPane.showConfirmDialog(this,
                "Save changes before closing?", "Unsaved Changes",
                JOptionPane.YES_NO_CANCEL_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                save();
                return; // save will close when upload completes
            } else if (result == JOptionPane.CANCEL_OPTION) {
                return;
            }
        }

        cleanup();
        super.windowClosing(e);
    }

    private void cleanup() {
        if (statusResetTimer != null) statusResetTimer.stop();
        if (tempFile != null) {
            tempFile.delete();
            tempFile = null;
        }
    }
}
