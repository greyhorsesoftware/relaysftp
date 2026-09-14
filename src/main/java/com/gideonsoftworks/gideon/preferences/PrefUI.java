package com.gideonsoftworks.gideon.preferences;

import javax.swing.*;
import java.awt.*;

/** Small shared layout helpers for the preference panels (label/field rows, muted notes, port parsing). */
final class PrefUI
{
    private PrefUI() {}

    static <T extends JComponent> T named( T c, String name ) { c.setName( name ); return c; }

    static JLabel mutedLabel( String text )
    {
        JLabel l = new JLabel( text );
        l.setForeground( Color.GRAY );
        l.setFont( l.getFont().deriveFont( l.getFont().getSize2D() - 1f ) );
        return l;
    }

    /** A muted note that wraps to {@code width} px (JLabel doesn't wrap unless told via HTML). */
    static JLabel mutedWrappedNote( String text, int width )
    {
        JLabel l = mutedLabel( "<html><body style='width:" + width + "px'>" + text + "</body></html>" );
        l.setAlignmentX( Component.LEFT_ALIGNMENT );
        return l;
    }

    static int parsePort( String s )
    {
        try { return Integer.parseInt( s.trim() ); } catch( Exception e ) { return 0; }
    }

    /** A field with a trailing hint label (e.g. "(unused)") that doesn't stretch the field. */
    static JPanel fieldWithHint( JComponent field, JComponent hint )
    {
        JPanel p = new JPanel( new BorderLayout( 6, 0 ) );
        p.setOpaque( false );
        p.add( field, BorderLayout.CENTER );
        p.add( hint, BorderLayout.EAST );
        return p;
    }

    static void addRow( JPanel form, int row, String label, JComponent field )
    {
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets( 3, 4, 3, 6 );
        g.gridx = 0; g.gridy = row; g.anchor = GridBagConstraints.EAST;
        form.add( new JLabel( label ), g );
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL; g.anchor = GridBagConstraints.WEST;
        form.add( field, g );
    }

    /** Place a component spanning both columns (label + field), full width — for wrapping notes/descriptions. */
    static void addFullWidth( JPanel form, int row, JComponent c )
    {
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets( 2, 4, 6, 4 );
        g.gridx = 0; g.gridy = row; g.gridwidth = 2; g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL; g.anchor = GridBagConstraints.WEST;
        form.add( c, g );
    }

}
