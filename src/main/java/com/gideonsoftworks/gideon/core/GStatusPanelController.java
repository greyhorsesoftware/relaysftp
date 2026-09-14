//
//  GStatusAreaPanel.java
//  Gideon
//
//  Created by David on 10/5/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.core;

// Gideon Parts
import com.gideonsoftworks.gideon.fileexistsdialog.GFileExistsDialog;
import com.gideonsoftworks.gideon.fileexistsdialog.GUnreadableFilesDialog;
import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.core.GideonConfiguration;
import com.gideonsoftworks.gideon.core.GGideonPanelController;
import com.gideonsoftworks.gcore.*;
import com.gideonsoftworks.gja.*;

// Java
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class GStatusPanelController extends GGideonPanelController implements GJobManagerListenerInterface, ActionListener
{

    private JLabel          statusField     =   null;
    private JProgressBar    progressBar     =   null;
    private Timer           gscUpdateTimer  =   null;
    private GJobManager     gscJobManager   =   null;
    private boolean         isSecureRemote  =   false;

    public  GStatusPanelController( GJobManager jobManagerToWatch )
    {

        JPanel  thePanel=null;
        /*
        *
        * Load the UI parts
        *
        */

        if( jobManagerToWatch == null )
        {
            throw new NullPointerException("GStatusPanelController : null job manager provided in constructor");
        }

        gscJobManager = jobManagerToWatch;

        JPanel pPanel = new JPanel( new CardLayout() );
        if( GideonConfiguration.LAYOUT_DEBUG ) pPanel.setBorder(BorderFactory.createLineBorder(Color.black));

        // Build status panel by hand (replaces GStatusAreaPanel.gui_xml)
        thePanel = new JPanel(new BorderLayout(8, 0));
        thePanel.setBorder(BorderFactory.createEmptyBorder(2, 7, 2, 8));
        thePanel.setPreferredSize(new Dimension(545, 24));
        thePanel.setMinimumSize(new Dimension(545, 24));

        statusField = new JLabel("Downloading folder ");
        statusField.setFont(new Font("Lucida Grande", Font.PLAIN, 12));

        progressBar = new JProgressBar();
        progressBar.putClientProperty("JComponent.sizeVariant", "mini");
        progressBar.setFocusable(false);
        progressBar.setPreferredSize(new Dimension(126, 6));
        progressBar.setMaximumSize(new Dimension(126, 6));

        JPanel progressWrapper = new JPanel(new GridBagLayout());
        progressWrapper.setOpaque(false);
        progressWrapper.add(progressBar);

        thePanel.add(statusField, BorderLayout.CENTER);
        thePanel.add(progressWrapper, BorderLayout.EAST);

        pPanel.add(thePanel, "status");

        gscJobManager.addStatusListener((GJobManagerListenerInterface)this);

        progressBar.setMinimum(0);

        setPanel(pPanel);

        resetToDefaults();

        // start off showing the status area
        // have to call AFTER setPanel()
        switchToStatus();

    }

    private void switchToStatus()
    {
        CardLayout  cLay = (CardLayout)getPanel().getLayout();
        cLay.show( getPanel(), "status" );
    }

    public  void    jm_keyJobChanged( final GJobManagerEvent e )
    {

        // XXX - BUG - if you add a status panel to a window BEFORE you do an initial queue up
        // this is never called and gscUpdateTimer etc... is never called...

        // Transfer (upload/download) progress lives in the Activity panel now. The bottom
        // status bar is only for navigation/listing status, so ignore transfer-queue jobs.
        if( e.getJobDescription().getParentQueue() == gscJobManager.getTransferQueue() )
            return;

        // Keep-alive / monitoring jobs (ping every 150 s, mtime poll every 15 s) would otherwise
        // flash the status bar on every tick; they are not something the user started.
        int keyType = e.getJobDescription().getJobType();
        if( keyType == GJobDescription.PING_JOB_TYPE || keyType == GJobDescription.MTIME_JOB_TYPE )
            return;

        GJobDescription jd = e.getJobDescription().getParentQueue().getKeyJob();

        // reset my niggas to their defaults
        statusField.setText(jd.getName());
        progressBar.setIndeterminate(true);
        statusField.setIcon(GLocalized.TASK_RUNNING_ICON);
        progressBar.setValue( 0 );

        // create the job timer object
        gscUpdateTimer = new Timer(1000, (ActionListener)this);
        gscUpdateTimer.setRepeats(true);
        gscUpdateTimer.start();

    }

    public  void    jm_jobCompleted( final GJobManagerEvent e )
    {

        // check and see if we have jobs in the queues, if we don't
        // opst he timer and reset the ui to defaults.
        if( ( gscJobManager.getTaskQueue().getKeyJob() == null ) &&
            ( gscJobManager.getTransferQueue().getKeyJob() == null ) )
        {
            if( gscUpdateTimer != null ) gscUpdateTimer.stop();
            resetToDefaults();
        }else{
            actionPerformed(null);
        }

    }

    public  void    jm_jobStopped( final GJobManagerEvent e )
    {
        resetToDefaults();
    }

    public  void    jm_jobFailed( final GJobManagerEvent e )
    {
        if( gscUpdateTimer != null ) gscUpdateTimer.stop();

        GJobDescription jd = e.getJobDescription();
        GFileItem targetFile = jd.getTargetFile();
        int jType = jd.getJobType();

        // Transfer (upload/download) failures are surfaced only in the Activity panel — no
        // bottom-bar error and no error dialog for them. Other job failures still report.
        boolean isTransfer = ( jType == GJobDescription.UPLOAD_JOB_TYPE || jType == GJobDescription.DOWNLOAD_JOB_TYPE );

        if( !isTransfer )
        {
            statusField.setIcon(GLocalized.TASK_ERROR_ICON);
            progressBar.setIndeterminate(false);

            switch(jType)
            {
                case GJobDescription.LOGIN_JOB_TYPE:
                    statusField.setText("Login has failed.");
                break;
                case GJobDescription.DELETE_JOB_TYPE:
                    statusField.setText("Deletion of \"" + (targetFile != null ? targetFile.getName() : "") + "\" has failed.");
                break;
                case GJobDescription.CHMOD_JOB_TYPE:
                    statusField.setText("Permission change has failed.");
                break;
                default:
                    statusField.setText("Operation failed.");
                break;
            }
        }

        gscJobManager.clearErroredJob();
        resetToDefaults();

        if( !isTransfer )
        {
            String errorMsg = "An error occurred.";
            if (jd.getJobStatus() != null && jd.getJobStatus().getException() != null) {
                errorMsg = jd.getJobStatus().getException().getMessage();
            }
            JOptionPane.showMessageDialog(
                (JFrame) getPanel().getTopLevelAncestor(),
                errorMsg, "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public  void    jm_jobCancelling( final GJobManagerEvent e )
    {

        gscUpdateTimer.stop();

        statusField.setText( GLocalized.CANCELLING );
        progressBar.setIndeterminate(true);
        progressBar.setValue( 0 );
        progressBar.setString( "" );
        progressBar.setStringPainted(false);

    }

    // job manager is shutting down
    public  void    jm_shuttingDown( final GJobManagerEvent e )
    {
        if( gscUpdateTimer!=null ) gscUpdateTimer.stop();
    }

    public  void    actionPerformed( ActionEvent e )
    {
        String          actionString = null;
        GJobDescription jd = null;

        if( gscJobManager.isShuttingDown() )
        {
            return;
        }

        // Only the task queue (navigation/listing/login/etc.) drives the bottom status bar;
        // transfer progress is shown in the Activity panel, not here.
        if( ( jd = gscJobManager.getTaskQueue().getKeyJob() ) == null )
        {
            return;
        }

        int             jobType = jd.getJobType();
        GFileItem       targetFile = jd.getTargetFile();
        GJobStatus      so = jd.getJobStatus();
        int             state = so.getState();

        if( so == null ) throw new NullPointerException("GStatusPanelController : Status Object is Null");

        if( state == GJobStatus.STATE_COMPLETE )
        {
            // reset status ui to default
            resetToDefaults();
            // stop the timer
            if( gscUpdateTimer != null ) gscUpdateTimer.stop();
            return;
        }

        if( state == GJobStatus.STATE_RUNNING )
        {
            statusField.setIcon(GLocalized.TASK_RUNNING_ICON);
        }else if( state == GJobStatus.STATE_PREPARING )
        {
            statusField.setIcon(GLocalized.TASK_PREPARING_ICON);
        }else if( state == GJobStatus.STATE_CANCELLING )
        {
            statusField.setIcon(GLocalized.TASK_PREPARING_ICON);
            statusField.setText("Cancelling...");
            return;
        }

        switch(jobType)
        {

            case GJobDescription.DELETE_JOB_TYPE:

                if(  jd.getHasPrerequisites() )
                {
                    if( state == GJobStatus.STATE_PREPARING )
                    {
                        statusField.setText("Preparing to " + GLocalized.DELETE_JOB_TYPE + " \"" + targetFile.getName() + "\" ( "+ so.getTotalFolderCount() + " folders ; " + so.getTotalFileCount() + " files )");
                    }else if( state == GJobStatus.STATE_RUNNING ){
                        statusField.setText("Deleting \"" + targetFile.getName() + "\" ( "+ so.getTotalFolderCount() + " folders ; " + so.getTotalFileCount() + " files )");
                        progressBar.setIndeterminate(false);
                        progressBar.setMaximum((int)so.getTotalCount());
                        progressBar.setValue((int)so.getTotalProcessedCount());
                    }
                }

            break;

            case GJobDescription.CHMOD_JOB_TYPE:

                if(  jd.getHasPrerequisites() )
                {
                    if( state == GJobStatus.STATE_PREPARING )
                    {
                        statusField.setText("Preparing to " + GLocalized.CHMOD_JOB_TYPE + " \"" + targetFile.getName() + "\" ( "+ so.getTotalFolderCount() + " folders ; " + so.getTotalFileCount() + " files )");
                    }else if( state == GJobStatus.STATE_RUNNING ){
                        statusField.setText("Changing Permissions of \"" + targetFile.getName() + "\" ( "+ so.getTotalFolderCount() + " folders ; " + so.getTotalFileCount() + " files )");
                        progressBar.setIndeterminate(false);
                        progressBar.setMaximum((int)so.getTotalCount());
                        progressBar.setValue((int)so.getTotalProcessedCount());
                    }
                }

            break;

            case GJobDescription.UPLOAD_JOB_TYPE:
            case GJobDescription.DOWNLOAD_JOB_TYPE:

                String  descString;

                if( jobType == GJobDescription.UPLOAD_JOB_TYPE )
                {
                    actionString = GLocalized.UPLOADING;
                    descString = "Upload";
                }else{
                    actionString = GLocalized.DOWNLOADING;
                    descString = "Download";
                }

                if(  jd.getHasPrerequisites() )
                {
                    if( so.getState() == GJobStatus.STATE_PREPARING )
                    {
                        statusField.setText(GLocalized.PREPARING + " " + descString + " \"" + targetFile.getName() + "\" ( "+ so.getTotalFolderCount() + " folders ; " + so.getTotalFileCount() + " files )");
                    }else if( so.getState() == GJobStatus.STATE_RUNNING ){

                        GJobDescription kJob = so.getActivePrerequisite();

                        if( kJob != null )
                        {
                            this.populateStatusForTransfer(kJob.getTargetFile(), so, actionString, jd);
                        }else{
                            statusField.setText(actionString + " \"" + targetFile.getName() + "\" ( "+ so.getTotalFolderCount() + " folders ; " + so.getTotalFileCount() + " files )");
                            progressBar.setIndeterminate(false);
                            progressBar.setMaximum((int)so.getTotalCount());
                            progressBar.setValue((int)so.getTotalProcessedCount());
                        }

                    }
                }else{
                        this.populateStatusForTransfer(targetFile, so, actionString, jd);
                }

            break;

        }

    }

public void setIsSecureRemoteHost( boolean secureRemote )
{

    isSecureRemote = secureRemote;

}

private void resetToDefaults()
{


        // reset my niggas to their defaults
        statusField.setText(GLocalized.IDLE);

        if( isSecureRemote )
        {
            statusField.setIcon(GLocalized.SECURE_ICON);
        }else{
            statusField.setIcon(GLocalized.TASK_QUEUED_ICON);
        }

        progressBar.setIndeterminate(false);
        progressBar.setString( "" );
        progressBar.setStringPainted(false);
        progressBar.setValue(0);

}

private void    populateStatusForTransfer( GFileItem tFile, GJobStatus so, String opString, GJobDescription pJD )
{
    long        bytesSecond         = 0;
    int         rawFileSize         = 0;
    int         bytesTransferred    = 0;
    boolean     secureTransfer      = false;

    // see if this job involves a secure connection
    secureTransfer = pJD.isSecuredJob();

    // sftp transfers do not have any stats - so we show the
    // progress bar as a barber pole
    if( secureTransfer )
    {
        progressBar.setIndeterminate(true);
    }else{
        rawFileSize = (int)tFile.getRawSize();
        bytesTransferred = (int)so.getBytesTransferred();
        progressBar.setMaximum(rawFileSize);
        progressBar.setValue(bytesTransferred);
        progressBar.setStringPainted(true);
    }

    // calculate throughput
    long secondsPassed = so.getSecondsSinceStart();

    if( secondsPassed == 0 )
    {
        bytesSecond = 0;
    }else{
        bytesSecond = so.getBytesTransferred()/secondsPassed;
    }

    double kPerSecond =  (double)bytesSecond/1000;

    StringBuilder theBuffer = new StringBuilder(opString + " \"" + tFile.getName() + "\"");

    if( pJD.getPrerequisiteCount() > 1 )
    {
        int index = 0;
        int total = pJD.getPrerequisiteCount();
        String pName = pJD.getTargetFile().getName();

        theBuffer.append( " (contained in \"" + pName + "\")");
    }

    if( !secureTransfer )
    {
        theBuffer.append(" at " + kPerSecond + " k/s.");
    }

    statusField.setText(theBuffer.toString());

    if( !secureTransfer )
    {
        double pComplete = progressBar.getPercentComplete() * 100;
        progressBar.setString( Integer.valueOf((int)pComplete).toString() + "%" );
    }

}

public  void    jm_jobStarted( final GJobManagerEvent e ){}

public  void    jm_jobNeedsInput( final GJobManagerEvent e, final GJobUserMessage m )
{

	System.out.println("GStatusPanelController : Job Needs Input");

	if( m.getType() == GJobUserMessage.TYPE_UNREADABLE )
	{
		GUnreadableFilesDialog.showDialog(
			(JFrame)getPanel().getTopLevelAncestor(), m);
	} else {
		GFileExistsDialog fileExistsDialog = new GFileExistsDialog((JFrame)getPanel().getTopLevelAncestor());
		fileExistsDialog.show(m);
	}

	System.out.println("GStatusPanelController : Job Needs Input Returning");

}

public  void    jm_jobHasMessage( final GJobManagerEvent e ){}

}
