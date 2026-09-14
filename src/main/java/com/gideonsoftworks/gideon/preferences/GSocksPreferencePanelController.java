package com.gideonsoftworks.gideon.preferences;

import com.gideonsoftworks.gcore.GProxyConfig;
import com.gideonsoftworks.gideon.core.GGideonPanelController;

import javax.swing.*;
import java.awt.*;

/**
 * Preferences → SOCKS5. A SOCKS5 proxy (used by FTP + SFTP) — an enable toggle plus server/port/user/password.
 * The password shows bubbles for a stored secret without reading the keychain, and is written only when changed.
 * A note clarifies that HTTP/S3 use the system proxy, not this config. Persists via {@link GProxyConfig}.
 */
public class GSocksPreferencePanelController extends GGideonPanelController
{
    private final JCheckBox    enabled;
    private final JTextField   host;
    private final JTextField   port;
    private final JTextField   user;
    private final GSecretField password;

    public GSocksPreferencePanelController()
    {
        enabled = new JCheckBox( "Use a SOCKS 5 proxy server" );
        enabled.setName( "socksEnabled" );

        host = PrefUI.named( new JTextField( 16 ), "socksHost" );
        port = PrefUI.named( new JTextField( 5 ), "socksPort" );
        user = PrefUI.named( new JTextField( 16 ), "socksUser" );
        password = PrefUI.named( new GSecretField(), "socksPassword" );

        JPanel form = new JPanel( new GridBagLayout() );
        int row = 0;
        PrefUI.addRow( form, row++, "Proxy Server:", host );
        PrefUI.addRow( form, row++, "Proxy Port:", port );
        PrefUI.addRow( form, row++, "Proxy User Name:", user );
        PrefUI.addRow( form, row++, "Proxy Password:", password );

        enabled.addActionListener( e -> updateEnablement() );

        JPanel north = new JPanel();
        north.setLayout( new BoxLayout( north, BoxLayout.Y_AXIS ) );
        enabled.setAlignmentX( Component.LEFT_ALIGNMENT );
        form.setAlignmentX( Component.LEFT_ALIGNMENT );
        north.add( enabled );
        north.add( Box.createVerticalStrut( 6 ) );
        north.add( form );
        north.add( Box.createVerticalStrut( 8 ) );
        north.add( PrefUI.mutedWrappedNote( "ⓘ HTTP proxies (including S3) use your system's HTTP proxy settings, not the proxy above.", 380 ) );

        JPanel panel = new JPanel( new BorderLayout() );
        panel.setBorder( BorderFactory.createEmptyBorder( 8, 15, 10, 15 ) );
        panel.setPreferredSize( new Dimension( 420, 210 ) );
        panel.add( north, BorderLayout.NORTH );
        setPanel( panel );
    }

    void updateEnablement()
    {
        boolean on = enabled.isSelected();
        host.setEnabled( on );
        port.setEnabled( on );
        user.setEnabled( on );
        password.setEnabled( on );
    }

    public void open()
    {
        enabled.setSelected( GProxyConfig.isSocksEnabled() );
        host.setText( GProxyConfig.getSocksHost() );
        port.setText( GProxyConfig.getSocksPort() > 0 ? String.valueOf( GProxyConfig.getSocksPort() ) : "" );
        user.setText( GProxyConfig.getSocksUser() );
        password.showPlaceholder( GProxyConfig.hasSocksPassword() );   // bubbles if set — no keychain read
        updateEnablement();
    }

    public void save()
    {
        GProxyConfig.setSocksEnabled( enabled.isSelected() );
        GProxyConfig.setSocksHost( host.getText().trim() );
        GProxyConfig.setSocksPort( PrefUI.parsePort( port.getText() ) );
        GProxyConfig.setSocksUser( user.getText().trim() );
        if( password.isDirty() ) GProxyConfig.setSocksPassword( password.typed() );   // keychain write only on change
    }
}
