package com.gideonsoftworks.gideon.preferences;

import com.gideonsoftworks.gcore.GProxyConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;

import static org.junit.jupiter.api.Assertions.*;

/** SOCKS5 tab: enable toggles field enablement, open/save round-trip, and keychain-write-only password. */
@Tag("ui")
class GSocksPreferencePanelTest
{
    private GSocksPreferencePanelController controller;

    @BeforeEach
    void setUp() throws Exception
    {
        SwingUtilities.invokeAndWait( () -> controller = new GSocksPreferencePanelController() );
    }

    @AfterEach
    void reset()
    {
        GProxyConfig.setSocksEnabled( false );
        GProxyConfig.setSocksHost( "" );
        GProxyConfig.setSocksPort( 0 );
        GProxyConfig.setSocksUser( "" );
        GProxyConfig.setSocksPassword( "" );
    }

    @Test
    void enableCheckbox_togglesFieldEnablement() throws Exception
    {
        JCheckBox enabled = find( JCheckBox.class, "socksEnabled" );
        SwingUtilities.invokeAndWait( () -> { enabled.setSelected( false ); controller.updateEnablement(); } );
        assertFalse( find( JTextField.class, "socksHost" ).isEnabled() );

        SwingUtilities.invokeAndWait( () -> { enabled.setSelected( true ); controller.updateEnablement(); } );
        assertTrue( find( JTextField.class, "socksHost" ).isEnabled() );
        assertTrue( find( JPasswordField.class, "socksPassword" ).isEnabled() );
    }

    @Test
    void open_loadsConfig_andSave_persistsEdits() throws Exception
    {
        GProxyConfig.setSocksEnabled( true );
        GProxyConfig.setSocksHost( "socks.example.com" );
        GProxyConfig.setSocksPort( 1080 );

        SwingUtilities.invokeAndWait( () -> controller.open() );
        assertTrue( find( JCheckBox.class, "socksEnabled" ).isSelected() );
        assertEquals( "socks.example.com", find( JTextField.class, "socksHost" ).getText() );
        assertEquals( "1080", find( JTextField.class, "socksPort" ).getText() );

        JTextField host = find( JTextField.class, "socksHost" );
        SwingUtilities.invokeAndWait( () -> { host.setText( "h2" ); controller.save(); } );
        assertEquals( "h2", GProxyConfig.getSocksHost() );
    }

    @Test
    void password_unchangedDoesNotOverwriteKeychain_changedDoes() throws Exception
    {
        GProxyConfig.setSocksEnabled( true );
        GProxyConfig.setSocksPassword( "stored" );

        SwingUtilities.invokeAndWait( () -> controller.open() );
        SwingUtilities.invokeAndWait( () -> controller.save() );
        assertEquals( "stored", GProxyConfig.getSocksPassword() );   // bubbles not written back

        JPasswordField pw = find( JPasswordField.class, "socksPassword" );
        SwingUtilities.invokeAndWait( () -> { pw.setText( "changed" ); controller.save(); } );
        assertEquals( "changed", GProxyConfig.getSocksPassword() );
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
