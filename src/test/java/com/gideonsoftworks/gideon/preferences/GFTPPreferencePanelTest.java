package com.gideonsoftworks.gideon.preferences;

import com.gideonsoftworks.gcore.GFtpFirewallType;
import com.gideonsoftworks.gcore.GFtpFirewallTypes;
import com.gideonsoftworks.gcore.GProxyConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;

import static org.junit.jupiter.api.Assertions.*;

/** FTP tab: firewall type combo, per-type field enablement, open/save, and keychain-write-only password. */
@Tag("ui")
class GFTPPreferencePanelTest
{
    private GFTPPreferencePanelController controller;

    @BeforeEach
    void setUp() throws Exception
    {
        SwingUtilities.invokeAndWait( () -> controller = new GFTPPreferencePanelController() );
    }

    @AfterEach
    void reset()
    {
        GProxyConfig.setType( GFtpFirewallType.NONE );
        GProxyConfig.setHost( "" );
        GProxyConfig.setPort( 0 );
        GProxyConfig.setUser( "" );
        GProxyConfig.setPassword( "" );
    }

    @Test
    void combo_listsNoneAndAllTenTypes()
    {
        JComboBox<?> combo = find( JComboBox.class, "ftpProxyTypeCombo" );
        assertEquals( GFtpFirewallTypes.list().size(), combo.getItemCount() );
        assertEquals( 11, combo.getItemCount() );   // None + Type 1..10
    }

    @Test
    void selectingType_updatesDescriptionAndFieldEnablement() throws Exception
    {
        select( GFtpFirewallTypes.byId( "FTP_1" ) );
        assertTrue ( find( JLabel.class, "ftpProxyDescription" ).getText().contains( "USER user@real.hostname" ) );
        assertTrue ( find( JTextField.class, "ftpProxyHost" ).isEnabled() );
        assertFalse( find( JPasswordField.class, "ftpProxyPassword" ).isEnabled() );
        assertTrue ( find( JLabel.class, "ftpProxyPasswordUnused" ).isVisible() );

        select( GFtpFirewallTypes.byId( "FTP_2" ) );
        assertTrue ( find( JTextField.class, "ftpProxyUser" ).isEnabled() );
        assertTrue ( find( JPasswordField.class, "ftpProxyPassword" ).isEnabled() );
        assertFalse( find( JLabel.class, "ftpProxyPasswordUnused" ).isVisible() );

        select( GFtpFirewallType.NONE );
        assertFalse( find( JTextField.class, "ftpProxyHost" ).isEnabled() );
    }

    @Test
    void open_loadsConfig_andSave_persistsEdits() throws Exception
    {
        GProxyConfig.setType( GFtpFirewallTypes.byId( "FTP_2" ) );
        GProxyConfig.setHost( "fw.example.com" );
        GProxyConfig.setPort( 21 );
        GProxyConfig.setUser( "fwuser" );

        SwingUtilities.invokeAndWait( () -> controller.open() );
        assertEquals( "FTP_2", ( (GFtpFirewallType) find( JComboBox.class, "ftpProxyTypeCombo" ).getSelectedItem() ).id() );
        assertEquals( "fw.example.com", find( JTextField.class, "ftpProxyHost" ).getText() );
        assertEquals( "21", find( JTextField.class, "ftpProxyPort" ).getText() );

        JTextField host = find( JTextField.class, "ftpProxyHost" );
        SwingUtilities.invokeAndWait( () -> { host.setText( "other.example.com" ); controller.save(); } );
        assertEquals( "other.example.com", GProxyConfig.getHost() );
    }

    @Test
    void password_unchangedDoesNotOverwriteKeychain_changedDoes() throws Exception
    {
        GProxyConfig.setType( GFtpFirewallTypes.byId( "FTP_2" ) );
        GProxyConfig.setPassword( "stored-secret" );

        // open shows bubbles (no read needed) and leaves the field clean
        SwingUtilities.invokeAndWait( () -> controller.open() );
        assertFalse( new String( find( JPasswordField.class, "ftpProxyPassword" ).getPassword() ).isEmpty(), "bubbles shown" );

        // save without touching the field -> the placeholder bullets are NOT written back
        SwingUtilities.invokeAndWait( () -> controller.save() );
        assertEquals( "stored-secret", GProxyConfig.getPassword() );

        // edit -> save writes the new secret
        JPasswordField pw = find( JPasswordField.class, "ftpProxyPassword" );
        SwingUtilities.invokeAndWait( () -> { pw.setText( "new-secret" ); controller.save(); } );
        assertEquals( "new-secret", GProxyConfig.getPassword() );
    }

    private void select( GFtpFirewallType t ) throws Exception
    {
        JComboBox<?> combo = find( JComboBox.class, "ftpProxyTypeCombo" );
        SwingUtilities.invokeAndWait( () -> combo.setSelectedItem( t ) );
    }

    private <T> T find( Class<T> type, String name )
    {
        T f = find( controller.getPanel(), type, name );
        assertNotNull( f, "component not found: " + name );
        return f;
    }

    private <T> T find( Container root, Class<T> type, String name )
    {
        for( Component c : root.getComponents() )
        {
            if( type.isInstance( c ) && name.equals( c.getName() ) ) return type.cast( c );
            if( c instanceof Container ct ) { T f = find( ct, type, name ); if( f != null ) return f; }
        }
        return null;
    }
}
