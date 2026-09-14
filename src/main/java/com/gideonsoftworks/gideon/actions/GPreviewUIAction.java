//
//  GPreviewUIAction.java
//  Gideon
//
//  Created by David on 10/12/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.actions;

import com.gideonsoftworks.gideon.widgets.GPreviewWindow;
import com.gideonsoftworks.gideon.core.GFrame;
import com.gideonsoftworks.gideon.core.GFrameManager;
import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.core.GValidationInterface;
import com.gideonsoftworks.gideon.actions.GUIAction;
import com.gideonsoftworks.gideon.browser.GBrowserPanelController;
import com.gideonsoftworks.gideon.browser.GBrowserWindow;
import  com.gideonsoftworks.editor.*;
import  com.gideonsoftworks.gcore.*;
import  com.gideonsoftworks.gja.*;
import  javax.swing.*;
import  java.awt.event.ActionEvent;
import  java.lang.reflect.*;

public class GPreviewUIAction extends GUIAction implements GValidationInterface 
{

    public  GPreviewUIAction( String name, Icon icon )
    {
    
         super( name, icon, GUIAction.GA_PREVIEW );
    
    }
    
    public void  actionPerformed( ActionEvent e )
    {
        GFrameManager           theManager      = GUIAction.getSharedGFrameManager();
        GFrame                  keyWindow       = theManager.getKeyWindow();
        // get the key browser before we show the option panel
        GBrowserPanelController brw             = (GBrowserPanelController)((GBrowserWindow)keyWindow).getKeyBrowser();
        GFileItem[]             selectedFiles   = null;
        GEditorWindow           nEditorWindow = null;
        int                     length = 0;
		
        selectedFiles = brw.getSelection();

        if( Array.getLength(selectedFiles) == 0 )
        {
            JOptionPane.showMessageDialog( null,GLocalized.NO_SELECTION);
            return;
        }
        
        length = Array.getLength(selectedFiles);
		
        for( int i=0 ; i<length;i++)
        {
            GFileItem   cFI =  (GFileItem)Array.get(selectedFiles, i);
            
            // ignore folders
            if( cFI.getIsDir() ) continue;
            
            // show the sexy...
            if( cFI.isGraphicsFile() )
            {
                if( cFI.getIsFileLocal() )
                {
                    GPreviewWindow  nPreviewWindow = new GPreviewWindow( getSharedGFrameManager(), brw );
                    nPreviewWindow.show( cFI );
                }else{
                    downloadAndPreview( cFI, brw );
                }
            // preview any other files in the editor
            }else
            {
                nEditorWindow = new GEditorWindow(getSharedGFrameManager());
                nEditorWindow.show(cFI);
            }

        }

    }

    /**
     * Queue the download on the browser's job manager — so it shows in the Activity
     * panel like any other transfer — and open the preview window only once the
     * file has arrived. On failure the temp file is discarded and no window opens.
     */
    private void downloadAndPreview( final GFileItem fItem, final GBrowserPanelController brw )
    {
        final GTemporaryFileItem tempFile = new GTemporaryFileItem( fItem.getFileNameExtension() );

        String name = GLocalized.DOWNLOAD_JOB_TYPE + " \"" + fItem.getName() + "\"";
        final GJobDescription downloadJob = new GJobDescription(
            GJobDescription.DOWNLOAD_JOB_TYPE, fItem.getConnection(), name );
        downloadJob.setTargetFile(fItem);
        downloadJob.setSourceFile(tempFile);
        downloadJob.addProperty(GJobDescription.TRANSFERMODE_JOB_PROP, Integer.valueOf(1));

        final GJobManager jm = brw.getJobManager();

        GAbstractJobManagerListener listener = new GAbstractJobManagerListener() {
            @Override public void jm_jobCompleted( GJobManagerEvent e )
            {
                if( e.getJobDescription() != downloadJob ) return;
                jm.removeStatusListener(this);
                GPreviewWindow  nPreviewWindow = new GPreviewWindow( getSharedGFrameManager(), brw );
                nPreviewWindow.showDownloaded( fItem, tempFile );
            }
            @Override public void jm_jobFailed( GJobManagerEvent e )
            {
                if( e.getJobDescription() != downloadJob ) return;
                jm.removeStatusListener(this);
                tempFile.delete();
            }
        };

        // originator must not be the browser panel or it would react to the completion
        downloadJob.setOriginator(listener);
        jm.addStatusListener(listener);
        brw.queueAndStartJob(downloadJob);
    }

}
