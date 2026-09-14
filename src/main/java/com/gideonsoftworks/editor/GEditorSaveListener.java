package com.gideonsoftworks.editor;

import com.gideonsoftworks.gcore.GFileItem;

/**
 * Callback for a successful editor save. Fired on the EDT.
 */
public interface GEditorSaveListener {

    /**
     * @param savedFile the item written at the destination — the remote file
     *                  item for uploads, the local file item for local saves.
     */
    void fileSaved(GFileItem savedFile);

}
