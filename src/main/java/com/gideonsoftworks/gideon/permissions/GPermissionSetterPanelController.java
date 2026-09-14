//
//  GPermissionSetterPanelController.java
//  Gideon
//
//  Created by David on 10/18/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.permissions;

import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.core.GideonConfiguration;
import com.gideonsoftworks.gideon.core.GGideonPanelController;
import com.gideonsoftworks.gcore.*;
import javax.swing.*;
import java.awt.*;
import  java.awt.event.*;

public class GPermissionSetterPanelController extends GGideonPanelController implements ActionListener  {

    private JButton apply;
    private JLabel octalValueField;
    private JLabel symbolicField;
    private JCheckBox or;
    private JCheckBox ow;
    private JCheckBox ox;
    private JCheckBox gr;
    private JCheckBox gw;
    private JCheckBox gx;
    private JCheckBox wr;
    private JCheckBox ww;
    private JCheckBox wx;
    private JCheckBox                               applyToContained;
    private GPermissions                            tempPerms;
    private int                                     originalValue=0;
    private GPermissionSetterDelegateInterface      gpsDelegate = null;

    /** titles */
    private JLabel octalValueTitle;
    private JLabel symbolicTitle;
    private JLabel readTitle;
    private JLabel writeTitle;
    private JLabel executeTitle;
    private JLabel ownerTitle;
    private JLabel groupTitle;
    private JLabel worldTitle;

    public  GPermissionSetterPanelController( GPermissionSetterDelegateInterface delegate )
    {

        super();

        gpsDelegate = delegate;

        // Build the panel by hand (replaces GPermissionSettingPanel.gui_xml)
        JPanel inner = new JPanel(new GridBagLayout());

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints wgbc = new GridBagConstraints();
        wgbc.anchor = GridBagConstraints.CENTER;
        panel.add(inner, wgbc);
        panel.setPreferredSize(new Dimension(284, 255));
        panel.setMinimumSize(new Dimension(284, 255));

        Font titleFont = new Font("Lucida Grande", Font.BOLD, 11);
        Font valueFont = new Font("Lucida Grande", Font.PLAIN, 11);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);

        // Column headers row: [blank] Read  Write  Execute
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.NONE;

        gbc.gridx = 0; gbc.weightx = 0.4;
        inner.add(new JLabel(""), gbc); // spacer for row labels column

        readTitle = new JLabel("Read");
        readTitle.setFont(valueFont);
        gbc.gridx = 1; gbc.weightx = 0.2;
        inner.add(readTitle, gbc);

        writeTitle = new JLabel("Write");
        writeTitle.setFont(valueFont);
        gbc.gridx = 2; gbc.weightx = 0.2;
        inner.add(writeTitle, gbc);

        executeTitle = new JLabel("Execute");
        executeTitle.setFont(valueFont);
        gbc.gridx = 3; gbc.weightx = 0.2;
        inner.add(executeTitle, gbc);

        // Owner row
        gbc.gridy = 1;
        ownerTitle = new JLabel("Owner :");
        ownerTitle.setFont(valueFont);
        ownerTitle.setHorizontalAlignment(SwingConstants.RIGHT);
        gbc.gridx = 0; gbc.anchor = GridBagConstraints.EAST;
        inner.add(ownerTitle, gbc);

        or = new JCheckBox(); or.setFocusable(false); or.setName("ownerRead");
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.CENTER;
        inner.add(or, gbc);

        ow = new JCheckBox(); ow.setFocusable(false); ow.setName("ownerWrite");
        gbc.gridx = 2;
        inner.add(ow, gbc);

        ox = new JCheckBox(); ox.setFocusable(false); ox.setName("ownerExecute");
        gbc.gridx = 3;
        inner.add(ox, gbc);

        // Group row
        gbc.gridy = 2;
        groupTitle = new JLabel("Group :");
        groupTitle.setFont(valueFont);
        groupTitle.setHorizontalAlignment(SwingConstants.RIGHT);
        gbc.gridx = 0; gbc.anchor = GridBagConstraints.EAST;
        inner.add(groupTitle, gbc);

        gr = new JCheckBox(); gr.setFocusable(false); gr.setName("groupRead");
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.CENTER;
        inner.add(gr, gbc);

        gw = new JCheckBox(); gw.setFocusable(false); gw.setName("groupWrite");
        gbc.gridx = 2;
        inner.add(gw, gbc);

        gx = new JCheckBox(); gx.setFocusable(false); gx.setName("groupExecute");
        gbc.gridx = 3;
        inner.add(gx, gbc);

        // World row
        gbc.gridy = 3;
        worldTitle = new JLabel("World :");
        worldTitle.setFont(valueFont);
        worldTitle.setHorizontalAlignment(SwingConstants.RIGHT);
        gbc.gridx = 0; gbc.anchor = GridBagConstraints.EAST;
        inner.add(worldTitle, gbc);

        wr = new JCheckBox(); wr.setFocusable(false); wr.setName("worldRead");
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.CENTER;
        inner.add(wr, gbc);

        ww = new JCheckBox(); ww.setFocusable(false); ww.setName("worldWrite");
        gbc.gridx = 2;
        inner.add(ww, gbc);

        wx = new JCheckBox(); wx.setFocusable(false); wx.setName("worldExecute");
        gbc.gridx = 3;
        inner.add(wx, gbc);

        // Octal value row
        gbc.gridy = 4;
        octalValueTitle = new JLabel("Octal Value :");
        octalValueTitle.setFont(titleFont);
        octalValueTitle.setHorizontalAlignment(SwingConstants.RIGHT);
        gbc.gridx = 0; gbc.anchor = GridBagConstraints.EAST;
        inner.add(octalValueTitle, gbc);

        octalValueField = new JLabel(" ");
        octalValueField.setFont(valueFont);
        gbc.gridx = 1; gbc.gridwidth = 3; gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        inner.add(octalValueField, gbc);
        gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;

        // Symbolic value row
        gbc.gridy = 5;
        symbolicTitle = new JLabel("Symbolic :");
        symbolicTitle.setFont(titleFont);
        symbolicTitle.setHorizontalAlignment(SwingConstants.RIGHT);
        gbc.gridx = 0; gbc.anchor = GridBagConstraints.EAST;
        inner.add(symbolicTitle, gbc);

        symbolicField = new JLabel(" ");
        symbolicField.setFont(valueFont);
        gbc.gridx = 1; gbc.gridwidth = 3; gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        inner.add(symbolicField, gbc);
        gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;

        // Apply to contained checkbox
        gbc.gridy = 6;
        applyToContained = new JCheckBox("Apply to contained items");
        applyToContained.setName("applyToContained");
        gbc.gridx = 0; gbc.gridwidth = 4; gbc.anchor = GridBagConstraints.CENTER;
        inner.add(applyToContained, gbc);
        gbc.gridwidth = 1;

        // Apply button
        gbc.gridy = 7;
        apply = new JButton("Apply");
        apply.setName("applyPermissions");
        gbc.gridx = 0; gbc.gridwidth = 4; gbc.anchor = GridBagConstraints.CENTER;
        inner.add(apply, gbc);
        gbc.gridwidth = 1;

        // localize the ui
        apply.setText(GLocalized.PP_APPLY);
        applyToContained.setText(GLocalized.PP_APPLYTOCONTAINED);
        octalValueTitle.setText(GLocalized.PP_OCTALVALUE);
        symbolicTitle.setText(GLocalized.PP_SYMBOLICVALUE);
        readTitle.setText(GLocalized.PP_READ);
        writeTitle.setText(GLocalized.PP_WRITE);
        executeTitle.setText(GLocalized.PP_EXECUTE);
        ownerTitle.setText(GLocalized.PP_OWNER);
        groupTitle.setText(GLocalized.PP_GROUP);
        worldTitle.setText(GLocalized.PP_WORLD);

        // wire listeners
        or.addActionListener(this);
        ow.addActionListener(this);
        ox.addActionListener(this);
        gr.addActionListener(this);
        gw.addActionListener(this);
        gx.addActionListener(this);
        wr.addActionListener(this);
        ww.addActionListener(this);
        wx.addActionListener(this);

        apply.setEnabled(false);
        apply.addActionListener(this);

        this.setPanel( panel );
    }

    public  void    setAllowsPermissionChanges( boolean allow )
    {

        if( allow )
        {
            or.setEnabled(true);
            ow.setEnabled(true);
            ox.setEnabled(true);
            gr.setEnabled(true);
            gw.setEnabled(true);
            gx.setEnabled(true);
            wr.setEnabled(true);
            ww.setEnabled(true);
            wx.setEnabled(true);
            applyToContained.setEnabled(true);
        }else{
            or.setEnabled(false);
            ow.setEnabled(false);
            ox.setEnabled(false);
            gr.setEnabled(false);
            gw.setEnabled(false);
            gx.setEnabled(false);
            wr.setEnabled(false);
            ww.setEnabled(false);
            wx.setEnabled(false);
            applyToContained.setEnabled(false);
        }

    }

    public  void    actionPerformed(ActionEvent e )
    {

        if( e.getSource().equals((Object)apply) )
        {
            apply.setEnabled(false);
            if( gpsDelegate != null )
            {
                gpsDelegate.bp_setPermissions(tempPerms, applyToContained.isSelected() );
            }
            return;
        }

        if( e.getSource().equals((Object)or) )
        {
            if( GideonConfiguration.PERMISSION_UI_DEBUG ) System.out.println("Owner Read Toggled");
            tempPerms.twiddleOwnerRead();
        }else if( e.getSource().equals((Object)ow) )
        {
            if( GideonConfiguration.PERMISSION_UI_DEBUG ) System.out.println("Owner Write Toggled");
            tempPerms.twiddleOwnerWrite();
        }else if( e.getSource().equals((Object)ox) )
        {
            if( GideonConfiguration.PERMISSION_UI_DEBUG ) System.out.println("Owner Execute Toggled");
            tempPerms.twiddleOwnerExecute();
        }else if( e.getSource().equals((Object)gr) )
        {
            if( GideonConfiguration.PERMISSION_UI_DEBUG ) System.out.println("Group Read Toggled");
            tempPerms.twiddleGroupRead();
        }else if( e.getSource().equals((Object)gw) )
        {
            if( GideonConfiguration.PERMISSION_UI_DEBUG ) System.out.println("Group Write Toggled");
            tempPerms.twiddleGroupWrite();
        }else if( e.getSource().equals((Object)gx) )
        {
            if( GideonConfiguration.PERMISSION_UI_DEBUG ) System.out.println("Group Execute Toggled");
            tempPerms.twiddleGroupExecute();
        }else if( e.getSource().equals((Object)wr) )
        {
            if( GideonConfiguration.PERMISSION_UI_DEBUG ) System.out.println("World Read Toggled");
            tempPerms.twiddleWorldRead();
        }else if( e.getSource().equals((Object)ww) )
        {
            if( GideonConfiguration.PERMISSION_UI_DEBUG ) System.out.println("World Write Toggled");
            tempPerms.twiddleWorldWrite();
        }else if( e.getSource().equals((Object)wx) )
        {
            if( GideonConfiguration.PERMISSION_UI_DEBUG ) System.out.println("World Execute Toggled");
            tempPerms.twiddleWorldExecute();
        }

        this.updateForPermissions();

        if( originalValue != tempPerms.getNumericValue() )
        {
            apply.setEnabled(true);
        }else{
            apply.setEnabled(false);
        }

    }

    public  void    populateForFile( GFileItem fi )
    {

        if( fi.getIsFileLocal() )
        {
            this.setAllowsPermissionChanges(false);
            octalValueField.setText("--");
            symbolicField.setText("--");
            return;
        }

        this.setAllowsPermissionChanges(true);

        if( fi.getIsDir() )
        {
            this.setEnableApplyToContents(true);
        }else{
            this.setEnableApplyToContents(false);
        }

        originalValue = fi.getPermissions().getNumericValue();

        this.setPermissions( fi.getPermissions() );

    }

    public void setPermissions(GPermissions p )
    {

        tempPerms = new GPermissions(p);

        this.updateForPermissions();

    }

    private  void updateForPermissions()
    {

        octalValueField.setText(tempPerms.getNumericValueAsString());
        symbolicField.setText(tempPerms.getSymbolicValue());

        // Owner permissions
        if( tempPerms.getOwnerHasRead() )
        {
            or.setSelected(true);
        }else{
            or.setSelected(false);
        }

        if( tempPerms.getOwnerHasWrite() )
        {
            ow.setSelected(true);
        }else{
            ow.setSelected(false);
        }

        if( tempPerms.getOwnerHasExecute() )
        {
            ox.setSelected(true);
        }else{
            ox.setSelected(false);
        }

        // Group permissions
        if( tempPerms.getGroupHasRead() )
        {
            gr.setSelected(true);
        }else{
            gr.setSelected(false);
        }

        if( tempPerms.getGroupHasWrite() )
        {
            gw.setSelected(true);
        }else{
            gw.setSelected(false);
        }

        if( tempPerms.getGroupHasExecute() )
        {
            gx.setSelected(true);
        }else{
            gx.setSelected(false);
        }

        // World permissions
        if( tempPerms.getWorldHasRead() )
        {
            wr.setSelected(true);
        }else{
            wr.setSelected(false);
        }

        if( tempPerms.getWorldHasWrite() )
        {
            ww.setSelected(true);
        }else{
            ww.setSelected(false);
        }

        if( tempPerms.getWorldHasExecute() )
        {
            wx.setSelected(true);
        }else{
            wx.setSelected(false);
        }

    }

    public  void    setEnableApplyToContents( boolean applyOn )
    {
        applyToContained.setEnabled(applyOn);
    }

}