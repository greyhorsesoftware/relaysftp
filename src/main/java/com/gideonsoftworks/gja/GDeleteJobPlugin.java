package com.gideonsoftworks.gja;

import com.gideonsoftworks.gcore.*;
import com.gideonsoftworks.gcore.GLogMessage;

/**
 * Delete plugin. Supports fast delete via SSH exec (rm -rf) on SFTP
 * servers that support exec. Falls back to per-file SFTP delete.
 *
 * Target File Item is the file/folder to be deleted.
 * Source File Item is unused.
 */
public class GDeleteJobPlugin extends GRecursiveJobPlugin {

    public GDeleteJobPlugin(final GJobStatus js) {
        super(js);
    }

    // Delete deepest items first so folders are empty before removal
    public boolean processDeeperItemsFirst() {
        return true;
    }

    @Override
    public boolean requiresPreprocessing(final GJobDescription d) {
        GFileItem target = d.getTargetFile();
        if (target == null || !target.getIsDir()) return false;

        // If fast delete is available, skip preprocessing entirely
        GConnection c = d.getConnectionToUse();
        if (c != null && c.hasCapability(GConnectionStats.CAP_WARP_LIST)) {
            return false;
        }

        return true;
    }

    @Override
    public void processJobDescription(final GJobDescription d, final GConnection c) throws GConnectionException {
        GFileItem target = d.getTargetFile();
        if (target == null) {
            signalError("Delete target was null");
            return;
        }

        // Fast delete for folders on SFTP with exec support — handle before super
        if (target.getIsDir() && c.hasCapability(GConnectionStats.CAP_WARP_LIST)) {
            c.getLog().logMessage(new GLogMessage(GLogMessage.GLOG_INFORMATIONAL,
                "Using fast delete (SSH exec) for " + target.getCompletePath()));
            long startTime = System.currentTimeMillis();
            if (c.fastDelete(target.getCompletePath())) {
                long elapsed = System.currentTimeMillis() - startTime;
                c.getLog().logMessage(new GLogMessage(GLogMessage.GLOG_INFORMATIONAL,
                    "Fast delete complete — " + elapsed + "ms"));
                getJobStatus().incrementProcessedFoldersBy(1);
                // unlink from the model like GConnection.delete() does - the browser
                // updates the listing in place from the model, no refresh follows
                if (target.getParentFileItem() != null) {
                    target.getParentFileItem().removeChild(target);
                }
                return;
            }
            signalError("Fast delete failed for " + target.getCompletePath());
            return;
        }

        // Standard per-file SFTP delete
        if (target.getIsDir()) {
            c.getLog().logMessage(new GLogMessage(GLogMessage.GLOG_INFORMATIONAL,
                "Using standard delete (per-file SFTP) for " + target.getCompletePath()));
        }

        super.processJobDescription(d, c);
    }

    @Override
    public boolean recursiveProcessJobDescription(final GJobDescription d, final GConnection c, final GFileItem tFI) throws GConnectionException {
        if (!super.recursiveProcessJobDescription(d, c, tFI)) {
            return false;
        }

        c.delete(tFI);
        return true;
    }
}
