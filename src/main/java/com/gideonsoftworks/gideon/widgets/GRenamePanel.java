//
//  GRenamePanel.java
//  GSFTP
//
//  Created by David J. Clark on 1/27/05.
//  Copyright 2005 Grey Horse Software All rights reserved.
//

package	com.gideonsoftworks.gideon.widgets;

import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.browser.GBrowserPanelCommandInterface;
import com.gideonsoftworks.gcore.*;

import java.awt.*;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class GRenamePanel extends JDialog implements ActionListener {
    // Declare beans.
    private JButton							ok;
    private JButton							cancel;
    private JTextArea						parentPathField;
    private JLabel							hostnameField;
    private JPanel							fileImageView;
    private JTextField						filenameField;
	private	GBrowserPanelCommandInterface	cmdBrw;
	private	GFileItem						cmdFile;

    // Constructor.
    public GRenamePanel(Frame owner) {
        // This dialog is modal.
        super(owner, GLocalized.RENAMING, true);

        // Build the panel layout by hand (replaces GRenamePanel.gui_xml)
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 10, 15));
        panel.setPreferredSize(new Dimension(383, 150));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);

        // File icon (left side, spanning rows)
        fileImageView = new JPanel();
        fileImageView.setOpaque(false);
        fileImageView.setPreferredSize(new Dimension(70, 69));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridheight = 3;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(fileImageView, gbc);
        gbc.gridheight = 1;

        // Filename text field
        filenameField = new JTextField("filename");
        gbc.gridx = 1; gbc.gridy = 0; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(filenameField, gbc);
        gbc.gridwidth = 1;

        // Host label + value
        JLabel hostLabel = new JLabel("Host :");
        hostLabel.setFont(new Font("Lucida Grande", Font.BOLD, 12));
        hostLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        gbc.gridx = 1; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(hostLabel, gbc);

        hostnameField = new JLabel("hostname");
        hostnameField.setFont(new Font("Lucida Grande", Font.PLAIN, 12));
        hostnameField.setHorizontalAlignment(SwingConstants.LEFT);
        gbc.gridx = 2; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(hostnameField, gbc);

        // Parent label + value
        JLabel parentLabel = new JLabel("Parent :");
        parentLabel.setFont(new Font("Lucida Grande", Font.BOLD, 12));
        parentLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        gbc.gridx = 1; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(parentLabel, gbc);

        parentPathField = new JTextArea();
        parentPathField.setEditable(false);
        parentPathField.setLineWrap(true);
        parentPathField.setWrapStyleWord(true);
        parentPathField.setOpaque(false);
        gbc.gridx = 2; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 1.0;
        panel.add(parentPathField, gbc);
        gbc.weighty = 0;

        // Buttons row
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        cancel = new JButton("Cancel");
        ok = new JButton("OK");
        buttonPanel.add(cancel);
        buttonPanel.add(ok);
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(buttonPanel, gbc);

        setContentPane(panel);

        // Wire action listeners directly
        ok.addActionListener(e -> onOK());
        cancel.addActionListener(e -> onCancel());

		setResizable(false);

        pack();
        setLocationRelativeTo(owner);
        // Set Enter and Escape keys as default keys.
        getRootPane().setDefaultButton(ok);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    /**
     * Invoke the onXxx() action handlers.
     * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
     */
    public void actionPerformed(ActionEvent e) {
        // No longer using DlgUtils — listeners wired directly
    }

	public void show( GFileItem fi, GBrowserPanelCommandInterface brw )
	{

		cmdBrw = brw;
		cmdFile = fi;

		filenameField.setText( fi.getName() );
		hostnameField.setText( fi.getServerName() );
		parentPathField.setText( fi.getParentFileItem().getCompletePath() );

		ImageIcon iImage = fi.getInfoIcon();
        fileImageView.add( new JLabel(iImage, SwingConstants.RIGHT) );

		setVisible(true);

	}

    public void onOK()
	{

		String	result = filenameField.getText();

		if( ( result.compareTo("") == 0 ) ||
			( result.length() == 0 ) ||
			( result.compareTo(cmdFile.getName()) == 0 ) )
		{
			System.out.println("fix name");
			return;
		}

		if( cmdBrw == null )
		{
			throw new NullPointerException("GRenamePanel : - onOK() - Browser was null");
		}

		cmdBrw.setName(result, cmdFile, null );

		setVisible(false);

	}

    public void onCancel()
	{

		setVisible(false);
    }

}
