//
//  GFileExistsDialog.java
//  GSFTP
//
//  Created by David J. Clark on 1/25/05.
//  Copyright 2005 Grey Horse Software All rights reserved.
//

package	com.gideonsoftworks.gideon.fileexistsdialog;

import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gja.*;
import com.gideonsoftworks.gcore.*;

import java.text.DateFormat;
import java.util.Date;
import java.awt.Frame;
import java.awt.Font;
import java.awt.Dimension;
import java.awt.BorderLayout;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;

public class GFileExistsDialog extends JDialog implements ActionListener {
    // Declare beans.
    private JButton cancelButton;
    private JButton skipButton;
    private JButton replaceButton;
    private JCheckBox applyToAllSwitch;
    private JLabel localFileName;
    private JLabel localSize;
    private JLabel localModified;
    private JLabel remoteName;
    private JLabel remoteSize;
    private JLabel remoteModified;
    private JPanel localFileImageView;
    private JPanel remoteFileImageView;
	private	GJobUserMessage	jobUserMessage;
	private JTextArea fileExistsBlurb;
	private JLabel actionFileBlurb;

    // Constructor.
    public GFileExistsDialog(Frame owner) {
        // This dialog is modal.
        super(owner, "GFileExistsDialog", true);

        // Build the panel by hand (replaces GFileExistsPanel.gui_xml)
        Font boldFont13 = new Font("Lucida Grande", Font.BOLD, 13);
        Font boldFont11 = new Font("Lucida Grande", Font.BOLD, 11);
        Font plainFont11 = new Font("Lucida Grande", Font.PLAIN, 11);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        panel.setPreferredSize(new Dimension(498, 312));

        // Top: blurb text area
        fileExistsBlurb = new JTextArea("The item you are about to transfer already exists at the destination. What would you like to do ?");
        fileExistsBlurb.setFont(boldFont13);
        fileExistsBlurb.setEditable(false);
        fileExistsBlurb.setFocusable(false);
        fileExistsBlurb.setLineWrap(true);
        fileExistsBlurb.setWrapStyleWord(true);
        fileExistsBlurb.setOpaque(false);

        // Action file label
        actionFileBlurb = new JLabel("File being <action>");
        actionFileBlurb.setFont(boldFont11);

        JPanel topPanel = new JPanel(new BorderLayout(0, 4));
        topPanel.add(fileExistsBlurb, BorderLayout.NORTH);
        topPanel.add(actionFileBlurb, BorderLayout.SOUTH);

        // File info sections
        JPanel fileInfoPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;

        // Local file section
        localFileImageView = new JPanel();
        localFileImageView.setOpaque(false);
        localFileImageView.setPreferredSize(new Dimension(69, 69));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridheight = 3;
        gbc.fill = GridBagConstraints.BOTH;
        fileInfoPanel.add(localFileImageView, gbc);
        gbc.gridheight = 1; gbc.fill = GridBagConstraints.HORIZONTAL;

        localFileName = new JLabel("filename");
        localFileName.setFont(plainFont11);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0;
        fileInfoPanel.add(localFileName, gbc);

        localSize = new JLabel("777 Bytes");
        localSize.setFont(plainFont11);
        gbc.gridx = 1; gbc.gridy = 1;
        fileInfoPanel.add(localSize, gbc);

        localModified = new JLabel("April 4th, 2005");
        localModified.setFont(plainFont11);
        gbc.gridx = 1; gbc.gridy = 2;
        fileInfoPanel.add(localModified, gbc);

        // Separator label: "File at destination"
        JLabel destLabel = new JLabel("File at destination");
        destLabel.setFont(boldFont11);
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.weightx = 0;
        gbc.insets = new Insets(8, 4, 2, 4);
        fileInfoPanel.add(destLabel, gbc);
        gbc.gridwidth = 1;
        gbc.insets = new Insets(2, 4, 2, 4);

        // Remote file section
        remoteFileImageView = new JPanel();
        remoteFileImageView.setOpaque(false);
        remoteFileImageView.setPreferredSize(new Dimension(69, 69));
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridheight = 3;
        gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 0;
        fileInfoPanel.add(remoteFileImageView, gbc);
        gbc.gridheight = 1; gbc.fill = GridBagConstraints.HORIZONTAL;

        remoteName = new JLabel("File name");
        remoteName.setFont(plainFont11);
        gbc.gridx = 1; gbc.gridy = 4; gbc.weightx = 1.0;
        fileInfoPanel.add(remoteName, gbc);

        remoteSize = new JLabel("777 Bytes");
        remoteSize.setFont(plainFont11);
        gbc.gridx = 1; gbc.gridy = 5;
        fileInfoPanel.add(remoteSize, gbc);

        remoteModified = new JLabel("April 5th, 2006");
        remoteModified.setFont(plainFont11);
        gbc.gridx = 1; gbc.gridy = 6;
        fileInfoPanel.add(remoteModified, gbc);

        // Bottom: buttons
        cancelButton = new JButton("Cancel");
        skipButton = new JButton("Skip");
        replaceButton = new JButton("Replace");
        applyToAllSwitch = new JCheckBox("Apply to all");

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        buttonPanel.add(applyToAllSwitch);
        buttonPanel.add(cancelButton);
        buttonPanel.add(skipButton);
        buttonPanel.add(replaceButton);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(fileInfoPanel, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(panel);

        // Add dialog as ActionListener.
        cancelButton.addActionListener(this);
        skipButton.addActionListener(this);
        replaceButton.addActionListener(this);
        applyToAllSwitch.addActionListener(this);

		setTitle(GLocalized.FILEEXISTSTITLE);

        pack();
        setLocationRelativeTo(owner);
        // Set Enter and Escape keys as default keys.
        getRootPane().setDefaultButton(replaceButton);
    }

    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source == cancelButton) {
            oncancelButton();
        } else if (source == skipButton) {
            onskipButton();
        } else if (source == replaceButton) {
            onreplaceButton();
        } else if (source == applyToAllSwitch) {
            onapplyToAllSwitch();
        }
    }

	public void show( GJobUserMessage m )
	{

		ImageIcon	iImage;

		if( m==null )
		{
			throw new NullPointerException("GFileExistsDialog : show - GJobUserMessage was null");
		}

		// keep a copy
		jobUserMessage = m;

		GFileItem	localFI = m.getLocalItem();
		GFileItem	remoteFI = m.getRemoteItem();

		// update text areas
		fileExistsBlurb.setText(GLocalized.FILEEXISTSBLURB1 + m.getActionTitleString() + " " + GLocalized.FILEEXISTSBLURB2);
		actionFileBlurb.setText(GLocalized.FILEACTIONBLURB + m.getActionFileString() );

		// fill in ui
		localFileName.setText(localFI.getCompletePath());
		localSize.setText(localFI.getHumanReadableSize());

		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);

		Date	lmDate = localFI.getDateModified();
		if( lmDate != null )
		{
			localModified.setText(df.format(lmDate));
		}else{
			localModified.setText("-");
		}

		iImage = localFI.getInfoIcon();
        localFileImageView.add( new JLabel(iImage, SwingConstants.CENTER) );

		remoteName.setText(remoteFI.getCompletePath());
		remoteSize.setText(remoteFI.getHumanReadableSize());

		Date	rmDate = remoteFI.getDateModified();
		if( rmDate != null )
		{
			remoteModified.setText(df.format(rmDate));
		}else{
			remoteModified.setText("-");
		}

		iImage = remoteFI.getInfoIcon();
        remoteFileImageView.add( new JLabel(iImage, SwingConstants.CENTER) );

		super.setVisible(true);

	}

    public void oncancelButton()
	{

		jobUserMessage.setAction(GJobUserMessage.CANCEL_ACTION);

		_hideAndSignal(jobUserMessage);

	}

    public void onskipButton()
	{

		jobUserMessage.setAction(GJobUserMessage.SKIP_ACTION);

		_hideAndSignal(jobUserMessage);

	}

    public void onreplaceButton()
	{

		jobUserMessage.setAction(GJobUserMessage.REPLACE_ACTION);

		_hideAndSignal(jobUserMessage);

	}

    public void onapplyToAllSwitch()
	{

		jobUserMessage.setApplysToAll(applyToAllSwitch.isSelected());

	}

	private	void	_hideAndSignal( GJobUserMessage jm )
	{
		setVisible(false);
		jm.setUserThinking(false);
	}

}
