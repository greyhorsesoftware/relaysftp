package com.gideonsoftworks.gideon.preferences;

import com.gideonsoftworks.gcore.GFtpFirewallType;
import com.gideonsoftworks.gcore.GFtpFirewallTypes;
import com.gideonsoftworks.gcore.GProxyConfig;
import com.gideonsoftworks.gideon.core.GGideonPanelController;
import com.gideonsoftworks.gideon.core.GLocalized;

import javax.swing.*;
import java.awt.*;

/**
 * Preferences → FTP. The FTP firewall proxy (None / Types 1–10) with a live description and
 * server/port/user/password fields whose enablement follows the selected type, plus the general FTP options
 * (passive mode, keep-alive) at the bottom. The password shows bubbles for a stored secret without reading the
 * keychain, and is written only when changed. Persists via {@link GProxyConfig} / {@link GSFTPPreferences}.
 */
public class GFTPPreferencePanelController extends GGideonPanelController
{
    private final JComboBox<GFtpFirewallType> typeCombo;
    private final JLabel        description;
    private final JTextField    host;
    private final JTextField    port;
    private final JTextField    user;
    private final GSecretField  password;
    private final JLabel        passwordUnused;
    private final JCheckBox     dontUsePASV;
    private final JCheckBox     keepAlive;

    public GFTPPreferencePanelController()
    {
        typeCombo = new JComboBox<>( GFtpFirewallTypes.list().toArray( new GFtpFirewallType[0] ) );
        typeCombo.setName( "ftpProxyTypeCombo" );
        typeCombo.setRenderer( new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent( JList<?> list, Object value, int index, boolean sel, boolean focus ) {
                super.getListCellRendererComponent( list, value, index, sel, focus );
                if( value instanceof GFtpFirewallType t ) setText( t.label() );
                return this;
            }
        } );

        description = PrefUI.mutedLabel( "" );
        description.setName( "ftpProxyDescription" );
        description.setAlignmentX( Component.LEFT_ALIGNMENT );

        host = PrefUI.named( new JTextField( 16 ), "ftpProxyHost" );
        port = PrefUI.named( new JTextField( 5 ), "ftpProxyPort" );
        user = PrefUI.named( new JTextField( 16 ), "ftpProxyUser" );
        password = PrefUI.named( new GSecretField(), "ftpProxyPassword" );
        passwordUnused = PrefUI.mutedLabel( "(unused)" );
        passwordUnused.setName( "ftpProxyPasswordUnused" );

        dontUsePASV = new JCheckBox( GLocalized.BM_NOPASSIVE );
        dontUsePASV.setName( "ftpDontUsePASV" );
        keepAlive   = new JCheckBox( GLocalized.PG_KEEPALIVE );
        keepAlive.setName( "ftpKeepAlive" );

        JPanel proxyForm = new JPanel( new GridBagLayout() );
        proxyForm.setBorder( BorderFactory.createTitledBorder( "FTP Firewall Proxy" ) );
        int row = 0;
        PrefUI.addRow( proxyForm, row++, "Proxy Server Type:", typeCombo );
        PrefUI.addFullWidth( proxyForm, row++, description );
        PrefUI.addRow( proxyForm, row++, "Proxy Server:", host );
        PrefUI.addRow( proxyForm, row++, "Proxy Port:", port );
        PrefUI.addRow( proxyForm, row++, "Proxy User Name:", user );
        PrefUI.addRow( proxyForm, row++, "Proxy Password:", PrefUI.fieldWithHint( password, passwordUnused ) );

        JPanel options = new JPanel();
        options.setLayout( new BoxLayout( options, BoxLayout.Y_AXIS ) );
        options.add( dontUsePASV );
        options.add( Box.createVerticalStrut( 2 ) );
        options.add( keepAlive );

        JPanel north = new JPanel();
        north.setLayout( new BoxLayout( north, BoxLayout.Y_AXIS ) );
        proxyForm.setAlignmentX( Component.LEFT_ALIGNMENT );
        options.setAlignmentX( Component.LEFT_ALIGNMENT );
        north.add( proxyForm );
        north.add( Box.createVerticalStrut( 8 ) );
        north.add( options );

        typeCombo.addActionListener( e -> updateForType() );

        JPanel panel = new JPanel( new BorderLayout() );
        panel.setBorder( BorderFactory.createEmptyBorder( 8, 15, 10, 15 ) );
        panel.setPreferredSize( new Dimension( 420, 280 ) );
        panel.add( north, BorderLayout.NORTH );
        setPanel( panel );
    }

    void updateForType()
    {
        GFtpFirewallType t = selectedType();
        description.setText( "<html><body style='width:380px'>" + t.description() + "</body></html>" );
        host.setEnabled( t.usesServer() );
        port.setEnabled( t.usesServer() );
        user.setEnabled( t.usesUser() );
        password.setEnabled( t.usesPassword() );
        passwordUnused.setVisible( !t.isNone() && !t.usesPassword() );
    }

    GFtpFirewallType selectedType() { return (GFtpFirewallType) typeCombo.getSelectedItem(); }

    public void open()
    {
        typeCombo.setSelectedItem( GProxyConfig.getType() );
        host.setText( GProxyConfig.getHost() );
        port.setText( GProxyConfig.getPort() > 0 ? String.valueOf( GProxyConfig.getPort() ) : "" );
        user.setText( GProxyConfig.getUser() );
        password.showPlaceholder( GProxyConfig.hasPassword() );   // bubbles if set — no keychain read
        dontUsePASV.setSelected( GSFTPPreferences.dontUsePASV() );
        keepAlive.setSelected( GSFTPPreferences.serverKeepAliveOn() );
        updateForType();
    }

    public void save()
    {
        GProxyConfig.setType( selectedType() );
        GProxyConfig.setHost( host.getText().trim() );
        GProxyConfig.setPort( PrefUI.parsePort( port.getText() ) );
        GProxyConfig.setUser( user.getText().trim() );
        if( password.isDirty() ) GProxyConfig.setPassword( password.typed() );   // keychain write only on change
        GSFTPPreferences.setDontUsePASV( dontUsePASV.isSelected() );
        GSFTPPreferences.setServerKeepAliveOn( keepAlive.isSelected() );
    }
}
