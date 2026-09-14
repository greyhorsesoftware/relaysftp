package com.gideonsoftworks.gideon.widgets;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

/**
 * Vector "download" glyph — a down arrow inside a circle. Scales cleanly and
 * follows the look-and-feel foreground color, so no image assets are needed.
 */
public class GDownloadCircleIcon implements Icon
{
    private final int size;

    public GDownloadCircleIcon( int size )
    {
        this.size = size;
    }

    public int getIconWidth()  { return size; }
    public int getIconHeight() { return size; }

    public void paintIcon( Component c, Graphics g, int x, int y )
    {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            Color color = ( c == null || c.isEnabled() )
                ? UIManager.getColor("Button.foreground")
                : UIManager.getColor("Button.disabledText");
            if( color == null ) color = Color.DARK_GRAY;
            g2.setColor(color);

            float s = size;
            float stroke = Math.max(1.5f, s / 12f);
            g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            // circle
            g2.draw(new Ellipse2D.Float(x + stroke, y + stroke, s - 2 * stroke, s - 2 * stroke));

            // down arrow: shaft + chevron
            float cx     = x + s / 2f;
            float top    = y + s * 0.30f;
            float bottom = y + s * 0.70f;
            float wing   = s * 0.16f;
            g2.draw(new Line2D.Float(cx, top, cx, bottom));
            g2.draw(new Line2D.Float(cx - wing, bottom - wing, cx, bottom));
            g2.draw(new Line2D.Float(cx + wing, bottom - wing, cx, bottom));
        } finally {
            g2.dispose();
        }
    }
}
