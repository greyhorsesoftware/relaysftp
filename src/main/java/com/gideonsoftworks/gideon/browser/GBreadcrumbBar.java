//
//  GBreadcrumbBar.java
//  Gideon
//

package com.gideonsoftworks.gideon.browser;

import com.gideonsoftworks.gcore.GFileItem;
import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Path bar above a file list: a drive (local) or server (remote) crumb for the top of the path, then a pill per folder with
 * the current folder in bold. When the crumbs are wider than the bar they start scrolled to the
 * end (current folder visible, leading crumbs off the left edge); the mouse wheel or a trackpad
 * swipe scrolls them left and right.
 */
class GBreadcrumbBar extends JPanel
{
    private static final String PILL_STYLE =
        "arc: 999; borderWidth: 0; focusWidth: 0; innerFocusWidth: 0; minimumWidth: 0";

    private static final int    SCROLL_STEP = 30;   // pixels per wheel notch

    private final JPanel                crumbs = new JPanel();
    private final JPanel                viewport;
    private final Consumer<GFileItem>   onSelect;
    private int                         scrollBack = 0;   // pixels scrolled left from the end of the row

    /**
        @param onSelect  called with the folder whose crumb was clicked
    */
    GBreadcrumbBar( final Consumer<GFileItem> onSelect )
    {
        super(new BorderLayout());
        this.onSelect = onSelect;
        setName("breadcrumbBar");

        crumbs.setLayout(new BoxLayout(crumbs, BoxLayout.X_AXIS));
        crumbs.setOpaque(false);

        // right-aligns the crumb row once it's too wide (less however far it's been scrolled back)
        viewport = new JPanel(null)
        {
            @Override public void doLayout()
            {
                Dimension p = crumbs.getPreferredSize();
                int overflow = Math.max(0, p.width - getWidth());
                scrollBack = Math.max(0, Math.min(scrollBack, overflow));
                crumbs.setBounds(scrollBack - overflow, (getHeight() - p.height) / 2, p.width, p.height);
            }
            @Override public Dimension getPreferredSize() { return new Dimension(0, crumbs.getPreferredSize().height); }
            @Override public Dimension getMinimumSize()   { return getPreferredSize(); }
        };
        viewport.setOpaque(false);
        viewport.add(crumbs);
        add(viewport, BorderLayout.CENTER);

        // vertical wheel and horizontal swipes (shift-wheel) both scroll the row; down/right moves toward the end
        viewport.addMouseWheelListener(e -> {
            scrollBack -= (int)Math.round(e.getPreciseWheelRotation() * SCROLL_STEP);
            viewport.doLayout();
        });
    }

    /**
        Show a path.
        @param path    folders from the top of the path down to the current folder
        @param remote  true to show the top of the path as a server rather than a drive
    */
    void setPath( final List<GFileItem> path, final boolean remote )
    {
        crumbs.removeAll();

        for( int i = 0 ; i < path.size() ; i++ )
        {
            final GFileItem item    = path.get(i);
            boolean         current = ( i == path.size() - 1 );

            if( i > 0 )
            {
                crumbs.add(Box.createHorizontalStrut(4));
                crumbs.add(new JLabel(new ChevronIcon()));
                crumbs.add(Box.createHorizontalStrut(4));
            }

            JButton crumb = new JButton();
            stylePill(crumb);
            if( i == 0 )
            {
                crumb.setIcon(remote ? new ServerIcon() : new RootIcon());
                crumb.setToolTipText(item.toString());
                crumb.setMargin(new Insets(4, 6, 4, 6));
            }else{
                crumb.setText(item.toString());
                crumb.setMargin(new Insets(3, 10, 3, 10));
            }
            if( current && i > 0 ) crumb.setFont(crumb.getFont().deriveFont(Font.BOLD));
            crumb.addActionListener(e -> onSelect.accept(item));
            crumbs.add(crumb);
        }

        // a new path starts scrolled to the end, showing the current folder
        scrollBack = 0;
        crumbs.revalidate();
        viewport.doLayout();
        repaint();
    }

    private static void stylePill( final JButton b )
    {
        b.setFocusable(false);
        b.putClientProperty(FlatClientProperties.STYLE, PILL_STYLE);
    }

    private static Graphics2D strokeGraphics( final Graphics g, final Color color, final float width )
    {
        Graphics2D g2 = (Graphics2D)g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        return g2;
    }

    /** Drive outline for the top of the path, drawn in the button's text colour so it follows the theme. */
    private static class RootIcon implements Icon
    {
        public int getIconWidth()  { return 16; }
        public int getIconHeight() { return 14; }

        public void paintIcon( Component c, Graphics g, int x, int y )
        {
            Graphics2D g2 = strokeGraphics(g, c.getForeground(), 1.5f);
            g2.translate(x, y);
            g2.draw(new java.awt.geom.RoundRectangle2D.Float(1.5f, 3.5f, 13f, 7.5f, 3f, 3f));   // body
            g2.fill(new java.awt.geom.Ellipse2D.Float(10.6f, 6.2f, 2f, 2f));                    // activity light
            g2.dispose();
        }
    }

    /** Two stacked rack units for the top of a remote path, drawn in the button's text colour. */
    private static class ServerIcon implements Icon
    {
        public int getIconWidth()  { return 16; }
        public int getIconHeight() { return 14; }

        public void paintIcon( Component c, Graphics g, int x, int y )
        {
            Graphics2D g2 = strokeGraphics(g, c.getForeground(), 1.5f);
            g2.translate(x, y);
            for( float top : new float[]{ 1.5f, 7.5f } )
            {
                g2.draw(new java.awt.geom.RoundRectangle2D.Float(1.5f, top, 13f, 5f, 2.5f, 2.5f));
                g2.fill(new java.awt.geom.Ellipse2D.Float(10.8f, top + 1.6f, 1.8f, 1.8f));   // activity light
            }
            g2.dispose();
        }
    }

    /** Thin muted chevron between crumbs. */
    private static class ChevronIcon implements Icon
    {
        public int getIconWidth()  { return 8; }
        public int getIconHeight() { return 12; }

        public void paintIcon( Component c, Graphics g, int x, int y )
        {
            Graphics2D g2 = strokeGraphics(g, UIManager.getColor("Label.disabledForeground"), 1.3f);
            g2.translate(x, y);
            g2.draw(new java.awt.geom.Path2D.Float() {{ moveTo(2.5f, 2.5f); lineTo(5.5f, 6f); lineTo(2.5f, 9.5f); }});
            g2.dispose();
        }
    }
}
