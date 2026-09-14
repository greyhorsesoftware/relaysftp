package com.gideonsoftworks.gideon.mirroring;

import com.gideonsoftworks.gcore.GBookmark;
import com.gideonsoftworks.gcore.GConnection;
import com.gideonsoftworks.gcore.GConnectionException;
import com.gideonsoftworks.gcore.GFileItem;
import com.gideonsoftworks.gideon.browser.GBrowserPanelController;
import com.gideonsoftworks.gideon.browser.GBrowserWindow;
import com.gideonsoftworks.gja.GAbstractJobManagerListener;
import com.gideonsoftworks.gja.GJobDescription;
import com.gideonsoftworks.gja.GJobManager;
import com.gideonsoftworks.gja.GJobManagerEvent;
import com.gideonsoftworks.gja.GMirrorJobPlugin;
import com.gideonsoftworks.gja.GQueue;
import com.gideonsoftworks.gideon.core.GCommonEvents;
import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.mirroring.detectors.GMirrorChangeDetector;
import com.gideonsoftworks.gideon.mirroring.detectors.GMirrorDetectorSelector;
import com.gideonsoftworks.gideon.mirroring.detectors.S3EtagChangeDetector;
import com.gideonsoftworks.gideon.mirroring.detectors.SizeMtimeChangeDetector;
import com.gideonsoftworks.gideon.mirroring.detectors.SizeOnlyChangeDetector;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The mirror workspace (design §11): Configure → Preflight → Review over {@link GMirrorEngine}.
 * Opened from the Mirror toolbar button. Sources its two roots from the dual-pane browser's
 * current local + remote folders; scan/execute run off the EDT on {@link SwingWorker}s.
 */
public class GMirrorPanel extends JPanel
{
    private final GBrowserWindow browser;
    private final GMirrorEngine  engine = new GMirrorEngine();

    private final CardLayout cards   = new CardLayout();
    private final JPanel     cardRoot = new JPanel( cards );

    // Configure
    private GMirrorSpec.Direction direction = GMirrorSpec.Direction.UPLOAD;
    private JToggleButton      upArrowBtn, downArrowBtn;
    private JComboBox<String>  detectorCombo;
    private JCheckBox          deleteExtrasBox, skipFiltersBox;
    private JLabel             planSentence;

    private JCheckBox          onlyRecentBox;
    private JSpinner           recentAmount;
    private JComboBox<String>  recentUnits;
    private JLabel             clockOffsetLabel, offsetTitle;
    private JButton            timeOffsetBtn;
    private boolean            clockOffsetAuto    = true;
    private long               manualOffsetMillis = 0;
    private JDialog            detectorPopup;
    private boolean            detectorArrowLeft = true;
    private int                detectorArrowY = 24;
    private long               detectorHiddenAt = 0L;

    // Preflight
    private JLabel            preflightLabel;
    private SwingWorker<?, ?> preflightWorker;

    // Review
    private MirrorTableModel           tableModel;
    private JTable                     reviewTable;
    private TableRowSorter<MirrorTableModel> sorter;
    private JLabel                     reviewSummary;
    private final ButtonGroup          filterGroup = new ButtonGroup();

    private JButton     preflightButton, mirrorButton;
    private String      currentCard = "configure";

    private GMirrorSpec activeSpec;   // the spec preflight ran with — reused by execute
    private GMirrorPlan plan;

    // Running / Done card — tracks the submitted mirror job (design: MIRROR_QUEUE_PLAN §Phase 3)
    private JLabel       runTitle, runDetail, runStats, runFooter;
    private JProgressBar runBar;
    private JButton      runCancelBtn, runCloseBtn, runSaveBtn;
    private JTable       runTable;
    private MirrorRunTableModel runModel;
    private GMirrorRunState     runState;     // per-action progress the engine writes and the 400 ms poll reads
    private GJobDescription mirrorJob;
    private Timer pollTimer;
    private GAbstractJobManagerListener jobListener;

    public GMirrorPanel( GBrowserWindow browser )
    {
        super( new BorderLayout() );
        this.browser = browser;

        add( buildHeader(), BorderLayout.NORTH );

        cardRoot.add( buildConfigureMain(), "configure" );
        cardRoot.add( buildPreflightMain(), "preflight" );
        cardRoot.add( buildReviewMain(),    "review" );
        cardRoot.add( buildRunningMain(),   "running" );
        add( cardRoot, BorderLayout.CENTER );

        showCard( "configure" );
        refreshConfigure();
        applyProtocolCapabilities();   // grey out options the remote protocol can't use
    }

    /** Switch the main area; the persistent header stays. Direction arrows lock outside Configure. */
    private void showCard( String name )
    {
        currentCard = name;
        boolean configuring = "configure".equals( name );
        upArrowBtn.setEnabled( configuring );
        downArrowBtn.setEnabled( configuring );
        applyDefaultButton();
        cards.show( cardRoot, name );
    }

    /** Make the primary action of the visible card the window's default (Enter) button. */
    private void applyDefaultButton()
    {
        JRootPane rp = getRootPane();
        if( rp == null ) return;
        if( "configure".equals( currentCard ) )   rp.setDefaultButton( preflightButton );
        else if( "review".equals( currentCard ) ) rp.setDefaultButton( mirrorButton );
        else                                      rp.setDefaultButton( null );
    }

    @Override public void addNotify()
    {
        super.addNotify();
        applyDefaultButton();   // the root pane exists once we're in the window
    }

    @Override public void removeNotify()
    {
        stopTracking();   // never leave a timer/listener attached to a dismissed workspace
        if( detectorPopup != null ) { detectorPopup.dispose(); detectorPopup = null; }   // #8: don't leak the popover window
        JRootPane rp = getRootPane();
        if( rp != null && ( rp.getDefaultButton() == preflightButton || rp.getDefaultButton() == mirrorButton ) )
            rp.setDefaultButton( null );
        super.removeNotify();
    }

    /** Leave the mirror workspace — restores the dual-pane browser in place. */
    private void close()
    {
        browser.exitMirrorView();
    }

    // ---- sources from the browser ----

    private GConnection localConn()  { return browser.getLocalBrowser()  != null ? browser.getLocalBrowser().getConnection()  : null; }
    private GConnection remoteConn() { return browser.getRemoteBrowser() != null ? browser.getRemoteBrowser().getConnection() : null; }
    private GFileItem   localCwd()   { return browser.getLocalBrowser()  != null ? browser.getLocalBrowser().getVirtualCWD()  : null; }
    private GFileItem   remoteCwd()  { return browser.getRemoteBrowser() != null ? browser.getRemoteBrowser().getVirtualCWD() : null; }

    // ---- persistent header (shown on all three screens) ----

    private JComponent buildHeader()
    {
        // Left = local, right = remote (fixed). The two standalone arrow glyphs choose the flow;
        // the selected one is accent-tinted, the other dimmed. They lock outside Configure.
        JPanel localCell  = sideCell( GLocalized.FOLDER_BIG, GLocalized.LOCALFILESLABEL, pathOf( localCwd() ) );
        JPanel remoteCell = sideCell( remoteFolderIcon(), remoteHost(), pathOf( remoteCwd() ) );

        FlatSVGIcon arrowDim = new FlatSVGIcon( "I8LN/images/svg/arrow.svg", 30, 30 );
        arrowDim.setColorFilter( new FlatSVGIcon.ColorFilter( c -> dimColor() ) );
        FlatSVGIcon arrowSel = new FlatSVGIcon( "I8LN/images/svg/arrow.svg", 30, 30 );
        arrowSel.setColorFilter( new FlatSVGIcon.ColorFilter( c -> accentColor() ) );

        downArrowBtn = arrowToggle( arrowDim, arrowSel, "Download — remote to local" );               // ← as-is
        upArrowBtn   = arrowToggle( hflip( arrowDim ), hflip( arrowSel ), "Upload — local to remote" ); // → flipped
        downArrowBtn.setName( "mirrorDownArrow" );
        upArrowBtn.setName( "mirrorUpArrow" );
        ButtonGroup grp = new ButtonGroup();
        grp.add( downArrowBtn );
        grp.add( upArrowBtn );
        upArrowBtn.setSelected( direction == GMirrorSpec.Direction.UPLOAD );
        downArrowBtn.setSelected( direction == GMirrorSpec.Direction.DOWNLOAD );
        upArrowBtn.addActionListener(   e -> { direction = GMirrorSpec.Direction.UPLOAD;   refreshConfigure(); } );
        downArrowBtn.addActionListener( e -> { direction = GMirrorSpec.Direction.DOWNLOAD; refreshConfigure(); } );
        JPanel arrows = new JPanel( new FlowLayout( FlowLayout.CENTER, 2, 0 ) );
        arrows.add( downArrowBtn );   // ← left
        arrows.add( upArrowBtn );     // → right

        JPanel header = new JPanel( new FlowLayout( FlowLayout.CENTER, 18, 10 ) );
        header.add( localCell );
        header.add( arrows );
        header.add( remoteCell );

        JPanel wrap = new JPanel( new BorderLayout() );
        wrap.add( header, BorderLayout.CENTER );
        wrap.add( new JSeparator(), BorderLayout.SOUTH );
        return wrap;
    }

    // ---- Screen A: Configure ----

    private JComponent buildConfigureMain()
    {
        JPanel p = new JPanel( new BorderLayout() );

        // options form, in a fixed-width column (every row fills the column width so the
        // mixed left/centre alignments line up cleanly)
        final int W = 540;
        JPanel column = new JPanel();
        column.setLayout( new BoxLayout( column, BoxLayout.Y_AXIS ) );

        JLabel detLabel = new JLabel( "Detect changes by:" );
        detectorCombo = new JComboBox<>( new String[]{
            "Automatic (recommended)", "Size + modification date", "Size only", "S3 ETag" } );
        detectorCombo.setName( "mirrorDetectorCombo" );
        detLabel.setAlignmentY( Component.CENTER_ALIGNMENT );
        detectorCombo.setAlignmentY( Component.CENTER_ALIGNMENT );
        JPanel detRow = new JPanel();
        detRow.setLayout( new BoxLayout( detRow, BoxLayout.X_AXIS ) );   // no FlowLayout leading indent
        detRow.add( detLabel );
        detRow.add( Box.createHorizontalStrut( 6 ) );
        detRow.add( detectorCombo );
        detRow.add( Box.createHorizontalStrut( 4 ) );
        detRow.add( buildDetectorInfoButton() );
        column.add( leftRow( detRow ) );
        column.add( Box.createVerticalStrut( 6 ) );

        deleteExtrasBox = new JCheckBox( "Delete files on the destination that aren't on the source" );
        deleteExtrasBox.setName( "mirrorDeleteExtras" );
        deleteExtrasBox.addActionListener( e -> refreshConfigure() );
        column.add( leftRow( deleteExtrasBox ) );

        skipFiltersBox = new JCheckBox( "Skip items matching the transfer-filter rules" );
        skipFiltersBox.setName( "mirrorSkipFilters" );
        skipFiltersBox.setSelected( true );   // on by default — honor the user's transfer-filter rules
        skipFiltersBox.addActionListener( e -> refreshConfigure() );
        JButton editRules = new JButton( "Edit Rules…" );
        editRules.addActionListener( e -> GCommonEvents.doTransferFilter() );
        JPanel filterRow = new JPanel();
        filterRow.setLayout( new BoxLayout( filterRow, BoxLayout.X_AXIS ) );  // no FlowLayout leading indent
        skipFiltersBox.setAlignmentY( Component.CENTER_ALIGNMENT );
        editRules.setAlignmentY( Component.CENTER_ALIGNMENT );
        filterRow.add( skipFiltersBox );
        filterRow.add( Box.createHorizontalStrut( 10 ) );
        filterRow.add( editRules );
        column.add( leftRow( filterRow ) );

        // "Only mirror files modified in the last N [units]" — gates copies (§4 modified-within window)
        onlyRecentBox = new JCheckBox( "Only mirror files modified in the last" );
        onlyRecentBox.setName( "mirrorOnlyRecent" );
        recentAmount = new JSpinner( new SpinnerNumberModel( 7, 0, 9999, 1 ) );
        recentAmount.setName( "mirrorRecentAmount" );
        recentAmount.setPreferredSize( new Dimension( 64, recentAmount.getPreferredSize().height ) );
        recentUnits  = new JComboBox<>( new String[]{ "Hours", "Days", "Weeks" } );
        recentUnits.setName( "mirrorRecentUnits" );
        recentUnits.setSelectedItem( "Days" );
        recentAmount.setEnabled( false );
        recentUnits.setEnabled( false );
        onlyRecentBox.addActionListener( e -> {
            recentAmount.setEnabled( onlyRecentBox.isSelected() );
            recentUnits.setEnabled( onlyRecentBox.isSelected() );
        } );
        onlyRecentBox.setAlignmentY( Component.CENTER_ALIGNMENT );
        recentAmount.setAlignmentY( Component.CENTER_ALIGNMENT );
        recentUnits.setAlignmentY( Component.CENTER_ALIGNMENT );
        recentAmount.setMaximumSize( recentAmount.getPreferredSize() );
        recentUnits.setMaximumSize( recentUnits.getPreferredSize() );
        JPanel recentRow = new JPanel();
        recentRow.setLayout( new BoxLayout( recentRow, BoxLayout.X_AXIS ) );   // align left edge with the checkboxes above
        recentRow.add( onlyRecentBox );
        recentRow.add( Box.createHorizontalStrut( 6 ) );
        recentRow.add( recentAmount );
        recentRow.add( Box.createHorizontalStrut( 6 ) );
        recentRow.add( recentUnits );
        column.add( leftRow( recentRow ) );

        // Modification-date (clock) offset: auto by default, with a manual "Time Offset…" override (§4)
        clockOffsetLabel = new JLabel();
        clockOffsetLabel.setForeground( Color.GRAY );
        clockOffsetLabel.setAlignmentX( Component.LEFT_ALIGNMENT );
        offsetTitle = new JLabel( "Modification Date Offset" );
        offsetTitle.setAlignmentX( Component.LEFT_ALIGNMENT );
        JPanel offsetLeft = new JPanel();
        offsetLeft.setLayout( new BoxLayout( offsetLeft, BoxLayout.Y_AXIS ) );
        offsetLeft.add( offsetTitle );
        offsetLeft.add( clockOffsetLabel );
        timeOffsetBtn = new JButton( "Time Offset…" );
        timeOffsetBtn.addActionListener( e -> onTimeOffset() );
        JPanel offsetRow = new JPanel( new BorderLayout() );
        offsetRow.add( offsetLeft, BorderLayout.WEST );
        offsetRow.add( timeOffsetBtn, BorderLayout.EAST );
        offsetRow.setAlignmentX( Component.LEFT_ALIGNMENT );
        offsetRow.setMaximumSize( new Dimension( W, offsetRow.getPreferredSize().height ) );
        column.add( Box.createVerticalStrut( 10 ) );
        column.add( offsetRow );
        updateClockOffsetLabel();

        column.add( Box.createVerticalStrut( 18 ) );

        // The plan as a polished callout: rounded accent-tinted fill, a left accent stripe, and a
        // small "PLAN" eyebrow above the wrapping description.
        final Color tint   = blend( accentColor(), UIManager.getColor( "Panel.background" ), 0.10 );
        final Color stripe = accentColor();
        final Color edge   = blend( accentColor(), UIManager.getColor( "Panel.background" ), 0.35 );

        planSentence = new JLabel();
        planSentence.setAlignmentX( Component.LEFT_ALIGNMENT );
        JLabel eyebrow = new JLabel( "PLAN" );
        eyebrow.setFont( eyebrow.getFont().deriveFont( Font.BOLD, 10.5f ) );
        eyebrow.setForeground( stripe );
        eyebrow.setAlignmentX( Component.LEFT_ALIGNMENT );

        JPanel planText = new JPanel();
        planText.setOpaque( false );
        planText.setLayout( new BoxLayout( planText, BoxLayout.Y_AXIS ) );
        planText.add( eyebrow );
        planText.add( Box.createVerticalStrut( 5 ) );
        planText.add( planSentence );

        JPanel planBox = new JPanel( new BorderLayout() )
        {
            @Override protected void paintComponent( Graphics g )
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
                int arc = 14;
                java.awt.geom.RoundRectangle2D r =
                    new java.awt.geom.RoundRectangle2D.Float( 0.5f, 0.5f, getWidth() - 1.5f, getHeight() - 1.5f, arc, arc );
                g2.setColor( tint );
                g2.fill( r );
                java.awt.Shape oldClip = g2.getClip();
                g2.clip( r );
                g2.setColor( stripe );
                g2.fillRect( 0, 0, 4, getHeight() );          // left accent stripe
                g2.setClip( oldClip );
                g2.setColor( edge );
                g2.draw( r );
                g2.dispose();
            }
        };
        planBox.setOpaque( false );
        planBox.setBorder( BorderFactory.createEmptyBorder( 12, 18, 12, 14 ) );
        planBox.add( planText, BorderLayout.CENTER );
        planBox.setAlignmentX( Component.LEFT_ALIGNMENT );
        planBox.setMaximumSize( new Dimension( W, Integer.MAX_VALUE ) );
        column.add( planBox );

        // Pin the column to a fixed width (a zero-height strut); height stays natural so the
        // wrapping plan line is never clipped. fill=NONE keeps it at that size, anchored top-centre.
        JComponent widthStrut = (JComponent) Box.createRigidArea( new Dimension( W, 0 ) );
        widthStrut.setAlignmentX( Component.LEFT_ALIGNMENT );
        column.add( widthStrut );

        JPanel centre = new JPanel( new GridBagLayout() );
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor  = GridBagConstraints.PAGE_START;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.insets  = new Insets( 28, 0, 0, 0 );
        centre.add( column, gbc );

        // --- bottom button bar: Cancel on the left, Preflight on the right ---
        JButton cancel   = new JButton( "Cancel" );
        cancel.addActionListener( e -> close() );
        preflightButton = new JButton( "Preflight" );
        preflightButton.setName( "mirrorPreflightButton" );
        preflightButton.addActionListener( e -> onPreflight() );
        JPanel left  = new JPanel( new FlowLayout( FlowLayout.LEFT,  8, 8 ) );
        left.add( cancel );
        JPanel right = new JPanel( new FlowLayout( FlowLayout.RIGHT, 8, 8 ) );
        right.add( preflightButton );
        JPanel buttonBar = new JPanel( new BorderLayout() );
        buttonBar.add( new JSeparator(), BorderLayout.NORTH );
        buttonBar.add( left,  BorderLayout.WEST );
        buttonBar.add( right, BorderLayout.EAST );

        p.add( centre, BorderLayout.CENTER );
        p.add( buttonBar, BorderLayout.SOUTH );
        return p;
    }

    /** Wrap a component in a full-width, fixed-height left-aligned row (BoxLayout-safe). */
    private static JComponent leftRow( JComponent c )
    {
        JPanel row = new JPanel( new BorderLayout() );
        row.add( c, BorderLayout.WEST );
        row.setAlignmentX( Component.LEFT_ALIGNMENT );
        row.setMaximumSize( new Dimension( Integer.MAX_VALUE, c.getPreferredSize().height + 6 ) );
        return row;
    }

    private void refreshConfigure()
    {
        String masterSide  = ( direction == GMirrorSpec.Direction.UPLOAD ) ? "local" : "remote";
        String replicaSide = ( direction == GMirrorSpec.Direction.UPLOAD ) ? "remote" : "local";
        String tail = deleteExtrasBox.isSelected()
            ? " New/changed files are copied; <b>extras on the destination are deleted.</b>"
            : " New/changed files are copied; nothing is deleted.";
        String filterClause = skipFiltersBox.isSelected()
            ? " Files matching your transfer-filter rules are ignored."
            : "";
        // Table-width hack: Swing's HTML honours a table's width far more reliably than a div's,
        // so the sentence wraps instead of being clipped.
        planSentence.setText( "<html><table width='480' cellpadding='0' cellspacing='0'><tr><td>Mirror the <b>"
            + masterSide + "</b> folder to the <b>" + replicaSide + "</b> folder." + tail + filterClause + "</td></tr></table></html>" );
    }

    /** The remote endpoint's folder icon with mars.png centered on top (marks it as the remote side). */
    private static Icon remoteFolderIcon()
    {
        ImageIcon folder = GLocalized.FOLDER_BIG;
        java.net.URL url = GMirrorPanel.class.getResource( "/I8LN/images/generic/mars.png" );
        if( folder == null || url == null ) return folder;
        int w = folder.getIconWidth(), h = folder.getIconHeight();
        java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage( w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB );
        Graphics2D g = bi.createGraphics();
        g.setRenderingHint( RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR );
        g.drawImage( folder.getImage(), 0, 0, null );
        int ow = Math.round( w * 0.42f ), oh = Math.round( h * 0.42f );    // smaller, centered in the folder's face
        int ox = ( w - ow ) / 2, oy = Math.round( h * 0.6f ) - oh / 2;     // nudged down onto the front face
        g.drawImage( new ImageIcon( url ).getImage(), ox, oy, ow, oh, null );
        g.dispose();
        return new ImageIcon( bi );
    }

    /** Both side cells are pinned to this width so a long path can't push the arrows / remote side off-centre. */
    private static final int MIRROR_CELL_W = 210;

    private JPanel sideCell( Icon icon, String host, String path )
    {
        JPanel cell = new JPanel();
        cell.setLayout( new BoxLayout( cell, BoxLayout.Y_AXIS ) );
        JLabel ic = new JLabel( icon );
        JLabel h  = new JLabel();
        h.setFont( h.getFont().deriveFont( Font.BOLD, 12f ) );
        h.setText( ellipsize( h, host != null ? host : "", MIRROR_CELL_W - 8, false ) );
        if( host != null ) h.setToolTipText( host );
        JLabel p  = new JLabel();
        p.setFont( p.getFont().deriveFont( Font.PLAIN, 11f ) );
        p.setForeground( Color.GRAY );
        p.setText( ellipsize( p, path != null ? path : "", MIRROR_CELL_W - 8, true ) );   // keep the tail (current folder)
        if( path != null ) p.setToolTipText( path );
        for( JComponent c : new JComponent[]{ ic, h, p } ) c.setAlignmentX( Component.CENTER_ALIGNMENT );
        cell.add( ic );
        cell.add( Box.createVerticalStrut( 3 ) );
        cell.add( h );
        cell.add( p );
        int hgt = cell.getPreferredSize().height;
        Dimension fixed = new Dimension( MIRROR_CELL_W, hgt );
        cell.setPreferredSize( fixed );
        cell.setMinimumSize( fixed );
        cell.setMaximumSize( fixed );
        return cell;
    }

    /** Truncate {@code s} with an ellipsis to fit {@code maxPx}; {@code keepTail} drops from the front (for paths). */
    private static String ellipsize( JComponent c, String s, int maxPx, boolean keepTail )
    {
        FontMetrics fm = c.getFontMetrics( c.getFont() );
        if( fm.stringWidth( s ) <= maxPx ) return s;
        final String ell = "…";
        if( keepTail )
        {
            int i = 0;
            while( i < s.length() && fm.stringWidth( ell + s.substring( i ) ) > maxPx ) i++;
            return ell + s.substring( i );
        }
        int end = s.length();
        while( end > 0 && fm.stringWidth( s.substring( 0, end ) + ell ) > maxPx ) end--;
        return s.substring( 0, end ) + ell;
    }

    /** A standalone arrow glyph (no button box): dimmed normally, accent-tinted when selected. */
    private JToggleButton arrowToggle( Icon normal, Icon selected, String tip )
    {
        JToggleButton b = new JToggleButton( normal );
        b.setSelectedIcon( selected );
        // When locked (disabled) outside Configure, keep the full-colour icons instead of Swing's
        // washed-out disabled rendering — so the arrows read the same on every screen.
        b.setDisabledIcon( normal );
        b.setDisabledSelectedIcon( selected );
        b.setToolTipText( tip );
        b.setFocusable( false );
        b.setBorderPainted( false );
        b.setContentAreaFilled( false );
        b.setFocusPainted( false );
        b.setBorder( BorderFactory.createEmptyBorder( 2, 6, 2, 6 ) );
        b.setCursor( Cursor.getPredefinedCursor( Cursor.HAND_CURSOR ) );
        return b;
    }

    private static Color accentColor()
    {
        Color c = UIManager.getColor( "Component.accentColor" );
        if( c == null ) c = UIManager.getColor( "Component.focusColor" );
        if( c == null ) c = UIManager.getColor( "Button.foreground" );
        return c != null ? c : new Color( 60, 120, 220 );
    }

    private static Color dimColor()
    {
        Color c = UIManager.getColor( "Label.disabledForeground" );
        return c != null ? c : Color.GRAY;
    }

    /** Mix {@code a} into {@code b} by {@code ratio} (0..1) — for subtle theme-aware tints. */
    private static Color blend( Color a, Color b, double ratio )
    {
        if( a == null ) a = Color.GRAY;
        if( b == null ) b = Color.WHITE;
        return new Color(
            (int) ( a.getRed()   * ratio + b.getRed()   * ( 1 - ratio ) ),
            (int) ( a.getGreen() * ratio + b.getGreen() * ( 1 - ratio ) ),
            (int) ( a.getBlue()  * ratio + b.getBlue()  * ( 1 - ratio ) ) );
    }

    /** Horizontally mirror an icon (arrow.svg points left → flip for the right-pointing arrow). */
    private static Icon hflip( final Icon base )
    {
        return new Icon()
        {
            public int getIconWidth()  { return base.getIconWidth(); }
            public int getIconHeight() { return base.getIconHeight(); }
            public void paintIcon( Component c, Graphics g, int x, int y )
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.translate( x + base.getIconWidth(), y );
                g2.scale( -1, 1 );
                base.paintIcon( c, g2, 0, 0 );
                g2.dispose();
            }
        };
    }

    private static String pathOf( GFileItem f ) { return f != null && f.getCompletePath() != null ? f.getCompletePath() : "(none)"; }

    // ---- Screen B: Preflight ----

    private JComponent buildPreflightMain()
    {
        JPanel p = new JPanel( new BorderLayout() );

        JPanel col = new JPanel();
        col.setLayout( new BoxLayout( col, BoxLayout.Y_AXIS ) );
        preflightLabel = new JLabel( "Comparing…" );
        preflightLabel.setAlignmentX( Component.CENTER_ALIGNMENT );
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate( true );
        bar.setMaximumSize( new Dimension( 320, 10 ) );
        bar.setAlignmentX( Component.CENTER_ALIGNMENT );
        col.add( preflightLabel );
        col.add( Box.createVerticalStrut( 12 ) );
        col.add( bar );
        JPanel centre = new JPanel( new GridBagLayout() );
        centre.add( col );

        JButton back = new JButton( "Back" );
        back.addActionListener( e -> cancelPreflight() );
        JPanel backLeft = new JPanel( new FlowLayout( FlowLayout.LEFT, 8, 8 ) );
        backLeft.add( back );
        JPanel backBar = new JPanel( new BorderLayout() );
        backBar.add( new JSeparator(), BorderLayout.NORTH );
        backBar.add( backLeft, BorderLayout.WEST );

        p.add( centre, BorderLayout.CENTER );
        p.add( backBar, BorderLayout.SOUTH );
        return p;
    }

    /** Abandon the in-progress scan and return to Configure. */
    private void cancelPreflight()
    {
        if( preflightWorker != null ) preflightWorker.cancel( true );
        showCard( "configure" );
    }

    private void onPreflight()
    {
        if( localConn() == null || remoteConn() == null )
        {
            JOptionPane.showMessageDialog( this, "Both a local folder and a connected remote folder are required.",
                "Mirror", JOptionPane.WARNING_MESSAGE );
            return;
        }
        final GMirrorSpec spec = buildSpec();

        preflightLabel.setText( "Comparing “" + shortName( spec.masterRoot ) + "”…" );
        showCard( "preflight" );

        preflightWorker = new SwingWorker<GMirrorPlan, Void>()
        {
            protected GMirrorPlan doInBackground() throws Exception { return engine.scan( spec ); }
            protected void done()
            {
                if( isCancelled() ) return;   // user pressed Back
                try {
                    plan = get();
                    activeSpec = spec;
                    populateReview( plan );
                    showCard( "review" );
                } catch( Exception e ) {
                    showError( "Preflight failed", e );
                    showCard( "configure" );
                }
            }
        };
        preflightWorker.execute();
    }

    private String remoteHost()
    {
        return ( remoteConn() != null && remoteConn().getBookmark() != null )
            ? remoteConn().getBookmark().getHostname() : "(not connected)";
    }

    // ---- Screen C: Review ----

    private JComponent buildReviewMain()
    {
        JPanel p = new JPanel( new BorderLayout( 0, 8 ) );
        p.setBorder( BorderFactory.createEmptyBorder( 10, 12, 10, 12 ) );

        JPanel tabs = new JPanel( new FlowLayout( FlowLayout.LEFT, 2, 0 ) );
        JToggleButton allTab = filterButton( "All", null );
        allTab.setSelected( true );
        tabs.add( allTab );
        tabs.add( filterButton( "New", GMirrorAction.Reason.NEW ) );
        tabs.add( filterButton( "Changed", GMirrorAction.Reason.CHANGED ) );
        tabs.add( filterButton( "Unchanged", GMirrorAction.Reason.EQUAL ) );
        tabs.add( filterButton( "Delete", GMirrorAction.Reason.EXTRA ) );

        tableModel = new MirrorTableModel();
        reviewTable = new JTable( tableModel );
        reviewTable.setName( "mirrorReviewTable" );
        sorter = new TableRowSorter<>( tableModel );
        reviewTable.setRowSorter( sorter );
        reviewTable.getColumnModel().getColumn( 0 ).setMaxWidth( 34 );
        reviewTable.getColumnModel().getColumn( 1 ).setMaxWidth( 130 );
        reviewTable.getColumnModel().getColumn( 3 ).setMaxWidth( 90 );

        reviewSummary = new JLabel( "", JLabel.CENTER );
        reviewSummary.setName( "mirrorReviewSummary" );
        JButton back = new JButton( "Back" );
        back.addActionListener( e -> showCard( "configure" ) );
        JPanel footL = new JPanel( new FlowLayout( FlowLayout.LEFT ) );
        footL.add( back );

        JButton cancel = new JButton( "Cancel" );
        cancel.addActionListener( e -> close() );
        JButton save = new JButton( "Save Report…" );
        save.addActionListener( e -> onSaveReport() );
        mirrorButton = new JButton( "Mirror" );
        mirrorButton.setName( "mirrorButton" );
        mirrorButton.addActionListener( e -> onMirror() );
        JPanel footR = new JPanel( new FlowLayout( FlowLayout.RIGHT ) );
        footR.add( cancel ); footR.add( save ); footR.add( mirrorButton );

        JPanel foot = new JPanel( new BorderLayout() );
        foot.add( footL, BorderLayout.WEST );
        foot.add( reviewSummary, BorderLayout.CENTER );
        foot.add( footR, BorderLayout.EAST );

        p.add( tabs, BorderLayout.NORTH );
        p.add( new JScrollPane( reviewTable ), BorderLayout.CENTER );
        p.add( foot, BorderLayout.SOUTH );
        return p;
    }

    private JToggleButton filterButton( String label, GMirrorAction.Reason reason )
    {
        JToggleButton b = new JToggleButton( label );
        b.setName( "mirrorTab" + label );
        b.putClientProperty( "JButton.buttonType", "tab" );   // FlatLaf flat tab look, underline when selected
        b.setFocusable( false );
        filterGroup.add( b );
        b.addActionListener( e -> sorter.setRowFilter( reason == null ? null : new RowFilter<MirrorTableModel, Integer>()
        {
            public boolean include( Entry<? extends MirrorTableModel, ? extends Integer> entry )
            {
                return tableModel.rows.get( entry.getIdentifier() ).action.reason == reason;
            }
        } ) );
        return b;
    }

    private void populateReview( GMirrorPlan plan )
    {
        List<MirrorRow> rows = new ArrayList<>();
        for( GMirrorAction a : plan.actions() )
            rows.add( new MirrorRow( a, a.type != GMirrorAction.Type.SKIP ) );  // actionable rows selected by default
        tableModel.setRows( rows );
        sorter.setRowFilter( null );
        updateSummary();
    }

    private void updateSummary()
    {
        int copy = 0, del = 0; long bytes = 0;
        for( MirrorRow r : tableModel.rows )
        {
            if( !r.selected ) continue;
            if( r.action.type == GMirrorAction.Type.COPY ) { copy++; bytes += r.action.bytes; }
            else if( r.action.type == GMirrorAction.Type.DELETE || r.action.type == GMirrorAction.Type.RMDIR ) del++;
        }
        String text = copy + " copy · " + del + " delete · " + formatBytes( bytes ) + " to transfer";
        if( plan != null && plan.filteredCount() > 0 )
            text += "  ·  " + plan.filteredCount() + " filtered out";
        reviewSummary.setText( text );
    }

    private void onMirror()
    {
        List<GMirrorAction> selected = new ArrayList<>();
        for( MirrorRow r : tableModel.rows )
            if( r.selected && r.action.type != GMirrorAction.Type.SKIP ) selected.add( r.action );

        if( selected.isEmpty() ) { close(); return; }

        GMirrorPlan toRun = new GMirrorPlan( selected, plan.replicaEntryCount() );

        // Review IS the confirmation: every delete was listed and left ticked, so Mirror satisfies
        // the engine's §5.4 blast-radius guard without a second prompt. (The guard still protects
        // callers that never showed a Review.)
        activeSpec.confirmedLargeDelete = true;

        submitMirrorJob( toRun );
    }

    /** Queue the confirmed plan as a MIRROR job and switch to the Running card to track it. */
    private void submitMirrorJob( GMirrorPlan toRun )
    {
        boolean upload = ( activeSpec.direction == GMirrorSpec.Direction.UPLOAD );
        GConnection remote = upload ? activeSpec.replicaConn : activeSpec.masterConn;

        GJobDescription job = new GJobDescription( GJobDescription.MIRROR_JOB_TYPE, remote, mirrorTitle() );
        job.setOriginator( this );   // browser panels gate their job handlers on originator.equals(this)
        job.setTargetFile( upload ? activeSpec.replicaRoot : activeSpec.masterRoot );
        job.setSourceFile( upload ? activeSpec.masterRoot  : activeSpec.replicaRoot );
        job.addProperty( GMirrorJobPlugin.MIRROR_SPEC_PROP, activeSpec );
        job.addProperty( GMirrorJobPlugin.MIRROR_PLAN_PROP, toRun );
        // Fixed for the run: up to three copies in flight, fewer if the bookmark's transfer limit is lower (plan §3.4).
        runState = new GMirrorRunState( toRun, maxInFlight( remote.getBookmark() ) );
        job.addProperty( GMirrorJobPlugin.MIRROR_RUN_STATE_PROP, runState );
        mirrorJob = job;

        GJobManager jm = browser.getJobManager();
        startTracking( jm );

        runTitle.setText( mirrorTitle() );
        runBar.setIndeterminate( true );
        runDetail.setText( "Starting…" );
        runModel.setState( runState );
        updateRunningProgress();
        setRunButtons( true );
        showCard( "running" );

        GQueue q = jm.getTransferQueue();
        q.addJobToQueue( job );
        jm.startNextJobForQueue( q );
    }

    /** How many copies a run may have in flight: {@code min(3, bookmark max-concurrent-transfers)} — no UI control. */
    static int maxInFlight( GBookmark remote )
    {
        return Math.max( 1, Math.min( 3, remote != null ? remote.getMaxConcurrentTransfers() : 3 ) );
    }

    private String mirrorTitle()
    {
        return ( activeSpec.direction == GMirrorSpec.Direction.UPLOAD )
            ? "Mirroring local → remote" : "Mirroring remote → local";
    }

    // ---- Screen D: Running / Done ----

    private JComponent buildRunningMain()
    {
        JPanel p = new JPanel( new BorderLayout( 0, 8 ) );
        p.setBorder( BorderFactory.createEmptyBorder( 10, 12, 10, 12 ) );

        // header: title / stats / overall bar
        JPanel head = new JPanel();
        head.setLayout( new BoxLayout( head, BoxLayout.Y_AXIS ) );
        runTitle = new JLabel( "Mirroring…" );
        runTitle.setName( "mirrorRunTitle" );
        runTitle.setAlignmentX( Component.CENTER_ALIGNMENT );
        runTitle.setFont( runTitle.getFont().deriveFont( Font.BOLD, 14f ) );
        runStats = new JLabel( " " );
        runStats.setName( "mirrorRunStats" );
        runStats.setAlignmentX( Component.CENTER_ALIGNMENT );
        runBar = new JProgressBar();
        runBar.setName( "mirrorRunBar" );
        runBar.setIndeterminate( true );
        runBar.setMaximumSize( new Dimension( 360, 12 ) );
        runBar.setAlignmentX( Component.CENTER_ALIGNMENT );
        runDetail = new JLabel( " " );
        runDetail.setName( "mirrorRunDetail" );
        runDetail.setAlignmentX( Component.CENTER_ALIGNMENT );
        runDetail.setForeground( Color.GRAY );
        head.add( runTitle );
        head.add( Box.createVerticalStrut( 4 ) );
        head.add( runStats );
        head.add( Box.createVerticalStrut( 6 ) );
        head.add( runBar );
        head.add( Box.createVerticalStrut( 4 ) );
        head.add( runDetail );

        // table: one row per action, Status updated by the poll
        runModel = new MirrorRunTableModel();
        runTable = new JTable( runModel );
        runTable.setName( "mirrorRunTable" );
        runTable.setRowSelectionAllowed( false );
        runTable.setCellSelectionEnabled( false );
        runTable.setFocusable( false );          // a read-only progress view: no selection or focus highlight
        runTable.setShowGrid( false );
        runTable.setRowHeight( runTable.getRowHeight() + 6 );
        runTable.getColumnModel().getColumn( 0 ).setMaxWidth( 140 );
        runTable.getColumnModel().getColumn( 2 ).setMaxWidth( 80 );
        runTable.getColumnModel().getColumn( 3 ).setPreferredWidth( 240 );
        runTable.getColumnModel().getColumn( 3 ).setMaxWidth( 300 );
        RunTextRenderer text = new RunTextRenderer( runModel );
        for( int c = 0; c < 3; c++ ) runTable.getColumnModel().getColumn( c ).setCellRenderer( text );
        runTable.getColumnModel().getColumn( 2 ).setCellRenderer( new RunTextRenderer( runModel ) { { setHorizontalAlignment( RIGHT ); } } );
        runTable.getColumnModel().getColumn( 3 ).setCellRenderer( new RunStatusRenderer() );

        // footer: skipped-so-far + the not-undoable note, Cancel while running / Close + Save Report when done
        runFooter = new JLabel( " " );
        runFooter.setName( "mirrorRunFooter" );
        runFooter.setForeground( Color.GRAY );
        runCancelBtn = new JButton( "Cancel" );
        runCancelBtn.setName( "mirrorRunCancel" );
        runCancelBtn.addActionListener( e -> onCancelMirror() );
        runSaveBtn = new JButton( "Save Report…" );
        runSaveBtn.setName( "mirrorRunSaveReport" );
        runSaveBtn.addActionListener( e -> onSaveReport() );
        runCloseBtn = new JButton( "Close" );
        runCloseBtn.setName( "mirrorRunClose" );
        runCloseBtn.addActionListener( e -> close() );
        JPanel right = new JPanel( new FlowLayout( FlowLayout.RIGHT, 8, 0 ) );
        right.add( runCancelBtn );
        right.add( runSaveBtn );
        right.add( runCloseBtn );
        JPanel foot = new JPanel( new BorderLayout() );
        foot.add( runFooter, BorderLayout.WEST );
        foot.add( right, BorderLayout.EAST );

        p.add( head, BorderLayout.NORTH );
        p.add( new JScrollPane( runTable ), BorderLayout.CENTER );
        p.add( foot, BorderLayout.SOUTH );
        return p;
    }

    /** Subscribe to this job's lifecycle and start the 1s status poll (progress is polled, not pushed). */
    private void startTracking( GJobManager jm )
    {
        jobListener = new GAbstractJobManagerListener()
        {
            @Override public void jm_jobCompleted( GJobManagerEvent e ) { if( isOurs( e ) ) onMirrorFinished( true ); }
            @Override public void jm_jobFailed( GJobManagerEvent e )    { if( isOurs( e ) ) onMirrorFinished( false ); }
            @Override public void jm_jobStopped( GJobManagerEvent e )   { if( isOurs( e ) ) onMirrorCancelled(); }
        };
        jm.addStatusListener( jobListener );
        pollTimer = new Timer( 400, e -> updateRunningProgress() );
        pollTimer.start();
    }

    private void stopTracking()
    {
        if( pollTimer != null ) { pollTimer.stop(); pollTimer = null; }
        // #7: don't leave a preflight scan running (remote LISTs) after the workspace is dismissed.
        if( preflightWorker != null ) { preflightWorker.cancel( true ); preflightWorker = null; }
        if( jobListener != null )
        {
            browser.getJobManager().removeStatusListener( jobListener );
            jobListener = null;
        }
    }

    private boolean isOurs( GJobManagerEvent e )
    {
        return mirrorJob != null && e.getJobDescription() == mirrorJob;
    }

    private void updateRunningProgress()
    {
        if( mirrorJob == null || runState == null ) return;
        int  total = runState.size();
        int  done  = runState.doneCount() + runState.skippedCount();
        long bytes = runState.bytesSoFar();
        long elapsedMs = Math.max( 1, System.currentTimeMillis() - runState.startMillis() );

        runBar.setIndeterminate( false );
        runBar.setMaximum( Math.max( 1, total ) );
        runBar.setValue( done );
        runStats.setText( done + " of " + total + " items · " + formatBytes( bytes ) + " of " + formatBytes( runState.totalBytes() )
            + " · " + formatBytes( bytes * 1000 / elapsedMs ) + "/s" );
        runFooter.setText( runState.skippedCount() + " skipped so far · a mirror run is not undoable; re-run to converge" );
        runDetail.setText( elapsedMs / 1000 + " s elapsed" );   // the finish/cancel handlers overwrite this with the summary

        runModel.refresh();
        int first = runState.firstRunning();
        if( first >= 0 && !userScrolledRunTable() ) runTable.scrollRectToVisible( runTable.getCellRect( first, 0, true ) );
    }

    /** True while the user has dragged the run table's scrollbar away from where the poll left it. */
    private boolean userScrolledRunTable()
    {
        JScrollBar sb = ( (JScrollPane) runTable.getParent().getParent() ).getVerticalScrollBar();
        return sb.getValueIsAdjusting();
    }

    private void onMirrorFinished( boolean success )
    {
        stopTracking();
        updateRunningProgress();   // final statuses into the table, stats and footer
        runBar.setIndeterminate( false );
        if( success )
        {
            GMirrorResult r = (GMirrorResult) mirrorJob.getProperty( GMirrorJobPlugin.MIRROR_RESULT_PROP );
            runBar.setMaximum( 1 );
            runBar.setValue( 1 );
            runTitle.setText( "Mirror complete" );
            runDetail.setText( summaryText( r ) );
        }
        else
        {
            runTitle.setText( "Mirror failed" );
            GConnectionException ex = mirrorJob.getJobStatus().getException();
            runDetail.setText( ex != null && ex.getMessage() != null ? ex.getMessage() : "The mirror did not finish." );
        }
        setRunButtons( false );
        refreshBrowserPanes();   // the mirror wrote to one side — re-list so the browser isn't stale
    }

    private void onMirrorCancelled()
    {
        stopTracking();
        if( runState != null ) runState.markRemainingCancelled();   // in case the worker died before it could
        updateRunningProgress();
        runBar.setIndeterminate( false );
        runBar.setValue( 0 );
        runTitle.setText( "Mirror cancelled" );
        runDetail.setText( "Stopped before completion." );
        setRunButtons( false );
        refreshBrowserPanes();   // partial writes may have landed; re-list so the browser isn't stale/dead
    }

    /** Re-list both browser panes after a mirror so they don't show a stale (or dead) listing. */
    private void refreshBrowserPanes()
    {
        refreshPane( browser.getLocalBrowser() );
        refreshPane( browser.getRemoteBrowser() );
    }

    private void refreshPane( Object pane )
    {
        if( pane instanceof GBrowserPanelController c )
        {
            try { c.refresh( null ); } catch( Exception ignore ) {}   // null CWD / not connected → skip
        }
    }

    private String summaryText( GMirrorResult r )
    {
        if( r == null ) return "Done.";
        String s = r.copies + " copied · " + r.deletes + " deleted · " + formatBytes( r.bytesCopied );
        if( r.skipped > 0 ) s += " · " + r.skipped + " skipped";
        return s;
    }

    private void onCancelMirror()
    {
        runCancelBtn.setEnabled( false );
        if( mirrorJob != null ) browser.getJobManager().stopJob( mirrorJob );
    }

    private void setRunButtons( boolean running )
    {
        runCancelBtn.setVisible( running );
        runCancelBtn.setEnabled( running );
        runSaveBtn.setVisible( !running );
        runCloseBtn.setVisible( !running );
    }

    private void onSaveReport()
    {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile( new File( "mirror-report.txt" ) );
        if( fc.showSaveDialog( this ) != JFileChooser.APPROVE_OPTION ) return;
        // from the Running/Done card the report also carries each action's outcome
        GMirrorRunState outcome = "running".equals( currentCard ) ? runState : null;
        try { Files.writeString( fc.getSelectedFile().toPath(), GMirrorReport.build( activeSpec, plan, outcome ) ); }
        catch( Exception e ) { showError( "Could not save report", e ); }
    }

    // ---- spec assembly ----

    private GMirrorSpec buildSpec()
    {
        GMirrorSpec s = new GMirrorSpec();
        s.direction = direction;

        GConnection lc = localConn(), rc = remoteConn();
        GFileItem localRoot  = new GFileItem( new File( localCwd().getCompletePath() ) );
        localRoot.setConnection( lc );
        GFileItem remoteRoot = new GFileItem( remoteCwd().getName(), remoteCwd().getCompletePath(), rc );
        remoteRoot.setFileType( GFileItem.G_FOLDER );

        if( direction == GMirrorSpec.Direction.UPLOAD )
        {
            s.masterConn = lc; s.masterRoot = localRoot;  s.replicaConn = rc; s.replicaRoot = remoteRoot;
        }
        else
        {
            s.masterConn = rc; s.masterRoot = remoteRoot; s.replicaConn = lc; s.replicaRoot = localRoot;
        }

        s.deleteExtras         = deleteExtrasBox.isSelected();
        s.applyTransferFilters = skipFiltersBox.isSelected();
        s.detector             = chosenDetector( s );
        s.modifiedWithin       = onlyRecentBox.isSelected() ? recentWindow() : null;
        s.clockOffsetAuto      = clockOffsetAuto;
        s.clockOffsetMillis    = clockOffsetAuto ? 0 : manualOffsetMillis;   // scan() overwrites when auto
        return s;
    }

    /** The "only mirror files modified in the last N [units]" window from the Configure controls. */
    private java.time.Duration recentWindow()
    {
        int n = (Integer) recentAmount.getValue();
        return switch( (String) recentUnits.getSelectedItem() )
        {
            case "Hours" -> java.time.Duration.ofHours( n );
            case "Weeks" -> java.time.Duration.ofDays( 7L * n );
            default      -> java.time.Duration.ofDays( n );
        };
    }

    private void updateClockOffsetLabel()
    {
        if( clockOffsetAuto )
            clockOffsetLabel.setText( "Offset is determined automatically" );
        else
        {
            long h = manualOffsetMillis / 3_600_000L;
            clockOffsetLabel.setText( "Manual offset: " + ( h >= 0 ? "+" : "" ) + h + "h (server vs local)" );
        }
    }

    /** Small dialog: pick automatic clock-offset detection, or a manual hours offset (server vs local). */
    private void onTimeOffset()
    {
        JComboBox<String> mode = new JComboBox<>( new String[]{ "Determined automatically", "Manual" } );
        mode.setSelectedIndex( clockOffsetAuto ? 0 : 1 );
        JSpinner hours = new JSpinner( new SpinnerNumberModel( (int)( manualOffsetMillis / 3_600_000L ), -24, 24, 1 ) );
        hours.setEnabled( !clockOffsetAuto );
        mode.addActionListener( e -> hours.setEnabled( mode.getSelectedIndex() == 1 ) );

        JPanel body = new JPanel();
        body.setLayout( new BoxLayout( body, BoxLayout.Y_AXIS ) );
        body.add( new JLabel( "Server clock vs your local clock — corrects timezone-skewed mtimes:" ) );
        body.add( Box.createVerticalStrut( 8 ) );
        body.add( mode );
        body.add( Box.createVerticalStrut( 8 ) );
        JPanel hrow = new JPanel( new FlowLayout( FlowLayout.LEFT, 6, 0 ) );
        hrow.add( new JLabel( "Server is ahead by (hours):" ) );
        hrow.add( hours );
        body.add( hrow );

        if( JOptionPane.showConfirmDialog( this, body, "Time Offset",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE ) != JOptionPane.OK_OPTION ) return;

        clockOffsetAuto    = ( mode.getSelectedIndex() == 0 );
        manualOffsetMillis = clockOffsetAuto ? 0 : (Integer) hours.getValue() * 3_600_000L;
        updateClockOffsetLabel();
    }

    private GMirrorChangeDetector chosenDetector( GMirrorSpec s )
    {
        switch( detectorCombo.getSelectedIndex() )
        {
            case 1:  return new SizeMtimeChangeDetector();
            case 2:  return new SizeOnlyChangeDetector();
            case 3:  return new S3EtagChangeDetector();
            default: return GMirrorDetectorSelector.selectFor( s );
        }
    }

    /** Enable only the options the remote protocol can actually use. */
    private void applyProtocolCapabilities()
    {
        GConnection rc = remoteConn();
        int proto = ( rc != null && rc.getBookmark() != null ) ? rc.getBookmark().getProtocol() : -1;
        boolean isS3 = ( proto == GBookmark.G_S3_PROTOCOL );

        // "S3 ETag" detection exists only for S3 — offer it only there.
        rebuildDetectorCombo( isS3 );

        // The clock offset corrects local↔server timezone/clock skew on mtime. S3 has no such skew
        // (lastModified is UTC PUT time) and compares by ETag, so the offset is meaningless there.
        timeOffsetBtn.setEnabled( !isS3 );
        offsetTitle.setEnabled( !isS3 );
        if( isS3 )
        {
            clockOffsetAuto = false;          // don't auto-measure a meaningless offset from PUT times
            manualOffsetMillis = 0;
            clockOffsetLabel.setText( "Not used for S3 (compares by ETag, not time)" );
        }
        else updateClockOffsetLabel();
    }

    /** Small ⓘ button next to the detector dropdown; opens the change-detection breakdown. */
    private JButton buildDetectorInfoButton()
    {
        JButton info = new JButton( new InfoOutlineIcon( 15 ) );
        info.setName( "mirrorDetectorInfo" );
        info.setToolTipText( "Compare the change-detection options" );
        info.setFocusable( false );
        info.setBorderPainted( false );
        info.setContentAreaFilled( false );
        info.setMargin( new Insets( 0, 0, 0, 0 ) );
        info.setCursor( Cursor.getPredefinedCursor( Cursor.HAND_CURSOR ) );
        info.setAlignmentY( Component.CENTER_ALIGNMENT );
        info.setMaximumSize( info.getPreferredSize() );
        info.addActionListener( e -> showDetectorInfo( info ) );
        return info;
    }

    /** A black, outline-style ⓘ glyph: a ring with a dot + stem, antialiased. */
    private static final class InfoOutlineIcon implements Icon
    {
        private final int size;
        InfoOutlineIcon( int size ) { this.size = size; }
        @Override public int getIconWidth()  { return size; }
        @Override public int getIconHeight() { return size; }
        @Override public void paintIcon( Component c, Graphics g, int x, int y )
        {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
            g2.setColor( c.isEnabled() ? c.getForeground() : UIManager.getColor( "Label.disabledForeground" ) );
            g2.setStroke( new BasicStroke( Math.max( 1f, size / 12f ),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND ) );
            g2.drawOval( x + 1, y + 1, size - 3, size - 3 );          // outline ring
            float cx = x + size / 2f;
            float dot = Math.max( 1.4f, size / 7f );
            g2.fill( new java.awt.geom.Ellipse2D.Float( cx - dot / 2f, y + size * 0.26f, dot, dot ) );  // tittle
            g2.draw( new java.awt.geom.Line2D.Float( cx, y + size * 0.44f, cx, y + size * 0.72f ) );    // stem
            g2.dispose();
        }
    }

    private static final int INFO_ARROW_W = 10, INFO_ARROW_HH = 10, INFO_ARC = 12;

    private void showDetectorInfo( JComponent anchor )
    {
        if( detectorPopup != null && detectorPopup.isVisible() ) { detectorPopup.setVisible( false ); return; }
        if( System.currentTimeMillis() - detectorHiddenAt < 250 ) return;   // reopen guard (matches Activity popover)

        if( detectorPopup == null ) detectorPopup = buildDetectorPopover( anchor );
        JPanel main = (JPanel) detectorPopup.getContentPane();

        // Default: popover to the RIGHT of the button, caret on its left edge pointing back at the button.
        detectorArrowLeft = true;
        main.setBorder( BorderFactory.createEmptyBorder( 0, INFO_ARROW_W, 0, 0 ) );
        detectorPopup.pack();
        int w = detectorPopup.getWidth(), h = detectorPopup.getHeight();

        Point p = anchor.getLocationOnScreen();
        Rectangle screen = anchor.getGraphicsConfiguration().getBounds();
        int anchorCenterY = p.y + anchor.getHeight() / 2;

        int x = p.x + anchor.getWidth() + 1;
        if( x + w > screen.x + screen.width )           // no room on the right → flip to the LEFT of the button
        {
            detectorArrowLeft = false;
            main.setBorder( BorderFactory.createEmptyBorder( 0, 0, 0, INFO_ARROW_W ) );
            detectorPopup.pack();
            w = detectorPopup.getWidth();
            x = p.x - w - 1;
        }
        if( x < screen.x ) x = screen.x;

        int y = anchorCenterY - 24;                     // caret near the top, aligned to the button centre
        if( y + h > screen.y + screen.height ) y = screen.y + screen.height - h;
        if( y < screen.y ) y = screen.y;
        detectorArrowY = anchorCenterY - y;
        detectorArrowY = Math.max( INFO_ARC + INFO_ARROW_HH, Math.min( detectorArrowY, h - INFO_ARC - INFO_ARROW_HH ) );

        detectorPopup.setLocation( x, y );
        main.repaint();
        detectorPopup.setVisible( true );
    }

    private JDialog buildDetectorPopover( JComponent anchor )
    {
        Window owner = SwingUtilities.getWindowAncestor( anchor );
        JDialog dlg = new JDialog( owner );
        dlg.setUndecorated( true );
        dlg.setFocusableWindowState( true );
        dlg.setAlwaysOnTop( true );
        dlg.setBackground( new Color( 0, 0, 0, 0 ) );

        JPanel main = new JPanel( new BorderLayout() ) {
            @Override protected void paintComponent( Graphics g )
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
                int w = getWidth(), h = getHeight();
                float ay = detectorArrowY;
                RoundRectangle2D body;
                Path2D arrow = new Path2D.Float();
                if( detectorArrowLeft )
                {
                    // body's left edge and the arrow base share x = INFO_ARROW_W (no seam line under the caret)
                    body = new RoundRectangle2D.Float( INFO_ARROW_W, 0.5f, w - INFO_ARROW_W - 0.5f, h - 1, INFO_ARC, INFO_ARC );
                    arrow.moveTo( INFO_ARROW_W, ay - INFO_ARROW_HH );
                    arrow.lineTo( 0.5f, ay );
                    arrow.lineTo( INFO_ARROW_W, ay + INFO_ARROW_HH );
                }
                else
                {
                    body = new RoundRectangle2D.Float( 0.5f, 0.5f, w - INFO_ARROW_W - 0.5f, h - 1, INFO_ARC, INFO_ARC );
                    arrow.moveTo( w - INFO_ARROW_W, ay - INFO_ARROW_HH );
                    arrow.lineTo( w - 0.5f, ay );
                    arrow.lineTo( w - INFO_ARROW_W, ay + INFO_ARROW_HH );
                }
                arrow.closePath();
                Area shape = new Area( body );
                shape.add( new Area( arrow ) );
                g2.setColor( infoBg() );
                g2.fill( shape );
                g2.setColor( infoBorder() );
                g2.setStroke( new BasicStroke( 1f ) );
                g2.draw( shape );
                g2.dispose();
            }
        };
        main.setName( "mirrorDetectorInfoPopup" );
        main.setOpaque( false );

        JComponent content = buildDetectorInfoContent();
        content.setOpaque( false );
        main.add( content, BorderLayout.CENTER );

        dlg.setContentPane( main );
        dlg.addWindowFocusListener( new java.awt.event.WindowAdapter() {
            @Override public void windowLostFocus( java.awt.event.WindowEvent e ) {
                detectorHiddenAt = System.currentTimeMillis();
                dlg.setVisible( false );
            }
        } );
        return dlg;
    }

    // name, recommended, what-it-does, protocols
    private static final String[][] DETECTOR_INFO = {
        { "Automatic", "1",
          "Picks the best method for the server and transfer direction, so you never have to weigh the trade-offs yourself.",
          "All protocols" },
        { "Size + mod date", "0",
          "Changed if the size <b>or</b> modification time differs (after correcting clock skew). Catches almost every edit, and both signals are free in the directory listing.",
          "SFTP · FTP download · S3 multipart fallback" },
        { "Size only", "0",
          "Changed only if the byte size differs. Stable when modification times can't be trusted or set — but misses edits that keep the same size.",
          "All · required for FTP upload" },
        { "S3 ETag", "0",
          "Compares the object's ETag (a content hash) with the local file — a true content check. Large multipart objects fall back to size + mod date.",
          "S3 only" },
    };

    // Theme-aware colors for the detector popover — follow the active FlatLaf theme (light + dark).
    private static Color uiColor( String key, int fallback ) { Color c = UIManager.getColor( key ); return c != null ? c : new Color( fallback ); }
    private static Color infoBg()     { return uiColor( "PopupMenu.background", 0xFFFFFF ); }
    private static Color infoBorder() { return uiColor( "Component.borderColor", 0xD0D0D0 ); }
    private static Color infoInk()    { return uiColor( "Label.foreground", 0x1A1A1A ); }
    private static Color infoMuted()  { return uiColor( "Label.disabledForeground", 0x8A8A8A ); }
    private static Color infoBody()   { return blend( infoInk(), infoBg(), 0.78 ); }   // softened ink for descriptions
    private static Color infoLine()   { return blend( infoBorder(), infoBg(), 0.55 ); } // faint divider

    private JComponent buildDetectorInfoContent()
    {
        JPanel card = new JPanel();
        card.setLayout( new BoxLayout( card, BoxLayout.Y_AXIS ) );
        card.setBackground( infoBg() );
        card.setBorder( BorderFactory.createEmptyBorder( 16, 18, 16, 18 ) );

        JPanel titleRow = new JPanel();
        titleRow.setLayout( new BoxLayout( titleRow, BoxLayout.X_AXIS ) );   // no FlowLayout leading indent
        titleRow.setOpaque( false );
        titleRow.setAlignmentX( Component.LEFT_ALIGNMENT );
        JLabel icon = new JLabel( new InfoOutlineIcon( 14 ) );
        icon.setAlignmentY( Component.CENTER_ALIGNMENT );
        JLabel title = new JLabel( "How changes are detected" );
        title.setFont( title.getFont().deriveFont( Font.BOLD, 14f ) );
        title.setForeground( infoInk() );
        title.setAlignmentY( Component.CENTER_ALIGNMENT );
        titleRow.add( icon );
        titleRow.add( Box.createHorizontalStrut( 7 ) );
        titleRow.add( title );
        titleRow.setMaximumSize( titleRow.getPreferredSize() );
        card.add( titleRow );

        JLabel sub = new JLabel( "A file is copied only when its destination copy is out of date." );
        sub.setForeground( infoMuted() );
        sub.setAlignmentX( Component.LEFT_ALIGNMENT );
        sub.setBorder( BorderFactory.createEmptyBorder( 4, 1, 0, 0 ) );
        card.add( sub );

        card.add( Box.createVerticalStrut( 13 ) );
        card.add( infoSeparator() );

        for( int i = 0; i < DETECTOR_INFO.length; i++ )
        {
            card.add( Box.createVerticalStrut( 12 ) );
            card.add( buildDetectorRow( DETECTOR_INFO[ i ] ) );
            if( i < DETECTOR_INFO.length - 1 )
            {
                card.add( Box.createVerticalStrut( 12 ) );
                card.add( infoSeparator() );
            }
        }
        return card;
    }

    private JComponent buildDetectorRow( String[] opt )
    {
        JPanel row = new JPanel();
        row.setLayout( new BoxLayout( row, BoxLayout.Y_AXIS ) );
        row.setOpaque( false );
        row.setAlignmentX( Component.LEFT_ALIGNMENT );

        JPanel nameRow = new JPanel();
        nameRow.setLayout( new BoxLayout( nameRow, BoxLayout.X_AXIS ) );   // no FlowLayout leading indent
        nameRow.setOpaque( false );
        nameRow.setAlignmentX( Component.LEFT_ALIGNMENT );
        JLabel name = new JLabel( opt[ 0 ] );
        name.setFont( name.getFont().deriveFont( Font.BOLD, 12.5f ) );
        name.setForeground( infoInk() );
        name.setAlignmentY( Component.CENTER_ALIGNMENT );
        nameRow.add( name );
        if( "1".equals( opt[ 1 ] ) )
        {
            JLabel chip = recommendedChip();
            chip.setAlignmentY( Component.CENTER_ALIGNMENT );
            nameRow.add( Box.createHorizontalStrut( 8 ) );
            nameRow.add( chip );
        }
        nameRow.setMaximumSize( nameRow.getPreferredSize() );

        JLabel desc = new JLabel( "<html><body style='width:330px;'>" + opt[ 2 ] + "</body></html>" );
        desc.setForeground( infoBody() );
        desc.setAlignmentX( Component.LEFT_ALIGNMENT );
        desc.setBorder( BorderFactory.createEmptyBorder( 3, 1, 4, 0 ) );

        JLabel protos = new JLabel( "Works with   " + opt[ 3 ] );
        protos.setForeground( infoMuted() );
        protos.setFont( protos.getFont().deriveFont( 10.5f ) );
        protos.setAlignmentX( Component.LEFT_ALIGNMENT );

        row.add( nameRow );
        row.add( desc );
        row.add( protos );
        row.setMaximumSize( new Dimension( Integer.MAX_VALUE, row.getPreferredSize().height ) );
        return row;
    }

    private static JComponent infoSeparator()
    {
        JPanel line = new JPanel();
        line.setBackground( infoLine() );
        line.setAlignmentX( Component.LEFT_ALIGNMENT );
        line.setPreferredSize( new Dimension( 10, 1 ) );
        line.setMaximumSize( new Dimension( Integer.MAX_VALUE, 1 ) );
        return line;
    }

    private static JLabel recommendedChip()
    {
        JLabel chip = new JLabel( "Recommended" );
        chip.setOpaque( true );
        chip.setBackground( blend( accentColor(), infoBg(), 0.16 ) );
        chip.setForeground( accentColor() );
        chip.setFont( chip.getFont().deriveFont( Font.PLAIN, 10f ) );
        chip.setBorder( BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder( blend( accentColor(), infoBg(), 0.40 ) ),
            BorderFactory.createEmptyBorder( 0, 6, 0, 6 ) ) );
        return chip;
    }

    private void rebuildDetectorCombo( boolean includeS3Etag )
    {
        Object sel = detectorCombo.getSelectedItem();
        String[] items = includeS3Etag
            ? new String[]{ "Automatic (recommended)", "Size + modification date", "Size only", "S3 ETag" }
            : new String[]{ "Automatic (recommended)", "Size + modification date", "Size only" };
        detectorCombo.setModel( new DefaultComboBoxModel<>( items ) );
        detectorCombo.setSelectedItem( java.util.Arrays.asList( items ).contains( sel ) ? sel : items[0] );
    }

    // ---- helpers ----

    private void showError( String title, Throwable t )
    {
        JOptionPane.showMessageDialog( this, t.getMessage() != null ? t.getMessage() : t.toString(),
            "Mirror — " + title, JOptionPane.ERROR_MESSAGE );
    }

    private static String shortName( GFileItem f ) { return f != null && f.getName() != null ? f.getName() : "folder"; }

    private static String formatBytes( long b )
    {
        if( b < 1024 ) return b + " B";
        if( b < 1024 * 1024 ) return String.format( "%.1f KB", b / 1024.0 );
        if( b < 1024L * 1024 * 1024 ) return String.format( "%.1f MB", b / ( 1024.0 * 1024 ) );
        return String.format( "%.1f GB", b / ( 1024.0 * 1024 * 1024 ) );
    }

    // ---- table model ----

    private static final class MirrorRow
    {
        final GMirrorAction action;
        boolean selected;
        MirrorRow( GMirrorAction action, boolean selected ) { this.action = action; this.selected = selected; }
    }

    private final class MirrorTableModel extends AbstractTableModel
    {
        private final String[] cols = { "", "Operation", "Path", "Size" };
        final List<MirrorRow> rows = new ArrayList<>();

        void setRows( List<MirrorRow> r ) { rows.clear(); rows.addAll( r ); fireTableDataChanged(); }

        public int getRowCount() { return rows.size(); }
        public int getColumnCount() { return cols.length; }
        public String getColumnName( int c ) { return cols[c]; }
        public Class<?> getColumnClass( int c ) { return c == 0 ? Boolean.class : String.class; }
        public boolean isCellEditable( int r, int c ) { return c == 0 && rows.get( r ).action.type != GMirrorAction.Type.SKIP; }

        public Object getValueAt( int r, int c )
        {
            MirrorRow row = rows.get( r );
            GMirrorAction a = row.action;
            switch( c )
            {
                case 0:  return row.selected;
                case 1:  return opLabel( a );
                case 2:  return a.relPath;
                default: return a.type == GMirrorAction.Type.COPY ? formatBytes( a.bytes ) : "";
            }
        }

        public void setValueAt( Object v, int r, int c )
        {
            if( c == 0 ) { rows.get( r ).selected = (Boolean) v; updateSummary(); }
        }

    }

    private String opLabel( GMirrorAction a )
    {
        String to = ( direction == GMirrorSpec.Direction.UPLOAD ) ? "↑" : "↓";   // copy goes master → replica
        switch( a.type )
        {
            case COPY:   return to + " copy (" + a.reason.name().toLowerCase() + ")";
            case MKDIR:  return to + " mkdir";
            case DELETE: return "✕ delete";
            case RMDIR:  return "✕ rmdir";
            default:     return "= unchanged";
        }
    }

    // ---- Running card: table over the run state (plan §3.5) ----

    /**
     * One Status cell. {@code toString()} is the stable text the UI tests read through the table
     * fixture ({@code done}, {@code skipped · reason}, {@code running · 42%}, {@code queued},
     * {@code cancelled}); the renderer draws the bar from the same object.
     */
    static final class RunStatus
    {
        final GMirrorRunState.Status status;
        final int    percent;   // RUNNING copies only; -1 = indeterminate
        final String reason;    // SKIPPED only

        RunStatus( GMirrorRunState.Status status, int percent, String reason )
        {
            this.status = status; this.percent = percent; this.reason = reason;
        }

        static RunStatus of( GMirrorRunState st, int i )
        {
            GMirrorRunState.Status s = st.status( i );
            GMirrorAction a = st.action( i );
            int pct = -1;
            if( s == GMirrorRunState.Status.RUNNING && a.type == GMirrorAction.Type.COPY && a.bytes > 0 )
                pct = (int) Math.min( 100, st.bytesDone( i ) * 100 / a.bytes );
            return new RunStatus( s, pct, s == GMirrorRunState.Status.SKIPPED ? st.reason( i ) : null );
        }

        @Override public boolean equals( Object o )
        {
            return o instanceof RunStatus r && r.status == status && r.percent == percent
                && ( reason == null ? r.reason == null : reason.equals( r.reason ) );
        }
        @Override public int hashCode() { return status.hashCode() * 31 + percent; }

        @Override public String toString()
        {
            switch( status )
            {
                case DONE:      return "done";
                case SKIPPED:   return "skipped · " + ( reason != null ? reason : "failed" );
                case RUNNING:   return percent >= 0 ? "running · " + percent + "%" : "running";
                case CANCELLED: return "cancelled";
                default:        return "queued";
            }
        }
    }

    private final class MirrorRunTableModel extends AbstractTableModel
    {
        private final String[] cols = { "Operation", "Path", "Size", "Status" };
        private GMirrorRunState state;
        private RunStatus[]     shown;   // what the table last rendered, so the poll only fires for changed rows

        void setState( GMirrorRunState s )
        {
            state = s;
            shown = new RunStatus[s.size()];
            for( int i = 0; i < shown.length; i++ ) shown[i] = RunStatus.of( s, i );
            fireTableDataChanged();
        }

        /** Re-read every row's status; notify only the rows that changed since the last tick. */
        void refresh()
        {
            if( state == null ) return;
            for( int i = 0; i < shown.length; i++ )
            {
                RunStatus now = RunStatus.of( state, i );
                if( !now.equals( shown[i] ) ) { shown[i] = now; fireTableRowsUpdated( i, i ); }
            }
        }

        public int getRowCount() { return shown == null ? 0 : shown.length; }
        public int getColumnCount() { return cols.length; }
        public String getColumnName( int c ) { return cols[c]; }
        public Class<?> getColumnClass( int c ) { return c == 3 ? RunStatus.class : String.class; }
        public boolean isCellEditable( int r, int c ) { return false; }

        public Object getValueAt( int r, int c )
        {
            GMirrorAction a = state.action( r );
            switch( c )
            {
                case 0:  return opLabel( a );
                case 1:  return a.relPath;
                case 2:  return a.type == GMirrorAction.Type.COPY ? formatBytes( a.bytes ) : "—";
                default: return shown[r];
            }
        }

        boolean isQueued( int r ) { return shown != null && r < shown.length && shown[r].status == GMirrorRunState.Status.QUEUED; }
    }

    static final Color RUN_GREEN = new Color( 0x2E9E4F ), RUN_AMBER = new Color( 0xC9871B ), RUN_RED = new Color( 0xC62828 );

    private static Color dimmed( Color fg, Color bg )
    {
        return new Color( ( fg.getRed() + bg.getRed() ) / 2, ( fg.getGreen() + bg.getGreen() ) / 2, ( fg.getBlue() + bg.getBlue() ) / 2 );
    }

    /** Operation / Path / Size: normal text, dimmed for a row that has not started yet. */
    private static class RunTextRenderer extends DefaultTableCellRenderer
    {
        private final MirrorRunTableModel model;
        RunTextRenderer( MirrorRunTableModel model ) { this.model = model; }

        @Override public Component getTableCellRendererComponent( JTable t, Object v, boolean sel, boolean focus, int row, int col )
        {
            super.getTableCellRendererComponent( t, v, false, false, row, col );
            setForeground( model.isQueued( t.convertRowIndexToModel( row ) ) ? dimmed( t.getForeground(), t.getBackground() ) : t.getForeground() );
            return this;
        }
    }

    /** A small painted glyph: a check mark or a warning triangle, in the row's status colour. */
    private static final class GlyphIcon implements Icon
    {
        private final boolean check;
        private final Color color;
        GlyphIcon( boolean check, Color color ) { this.check = check; this.color = color; }
        public int getIconWidth()  { return 14; }
        public int getIconHeight() { return 14; }
        public void paintIcon( Component c, Graphics g, int x, int y )
        {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
            g2.setColor( color );
            g2.setStroke( new BasicStroke( 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND ) );
            if( check )
            {
                g2.drawLine( x + 2, y + 8, x + 6, y + 12 );
                g2.drawLine( x + 6, y + 12, x + 12, y + 3 );
            }
            else
            {
                Path2D tri = new Path2D.Float();
                tri.moveTo( x + 7, y + 1 ); tri.lineTo( x + 13, y + 12 ); tri.lineTo( x + 1, y + 12 ); tri.closePath();
                g2.setStroke( new BasicStroke( 1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND ) );
                g2.draw( tri );
                g2.drawLine( x + 7, y + 5, x + 7, y + 8 );
                g2.fillOval( x + 6, y + 9, 2, 2 );
            }
            g2.dispose();
        }
    }

    /**
     * Status column: green check + "done", amber warning + "skipped · reason", a thin bar with the
     * percentage to its right for a running copy (indeterminate for a running mkdir/delete),
     * dimmed "queued", red "cancelled". The text stays the {@link RunStatus} text.
     */
    private static final class RunStatusRenderer implements TableCellRenderer
    {
        private final DefaultTableCellRenderer label = new DefaultTableCellRenderer();
        private final JPanel running = new JPanel( new BorderLayout( 8, 0 ) );
        private final JProgressBar bar = new JProgressBar( 0, 100 );
        private final JLabel percent = new JLabel( "", JLabel.RIGHT );
        private final Icon check = new GlyphIcon( true, RUN_GREEN ), warn = new GlyphIcon( false, RUN_AMBER );

        RunStatusRenderer()
        {
            bar.setBorderPainted( false );
            bar.setStringPainted( false );
            bar.setPreferredSize( new Dimension( 120, 6 ) );
            JPanel barBox = new JPanel( new GridBagLayout() );   // centres the thin bar vertically
            barBox.setOpaque( false );
            barBox.add( bar, new GridBagConstraints( 0, 0, 1, 1, 1, 0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets( 0, 0, 0, 0 ), 0, 0 ) );
            percent.setPreferredSize( new Dimension( 36, 14 ) );
            running.setBorder( BorderFactory.createEmptyBorder( 0, 2, 0, 6 ) );
            running.add( barBox, BorderLayout.CENTER );
            running.add( percent, BorderLayout.EAST );
            label.setIconTextGap( 6 );
        }

        public Component getTableCellRendererComponent( JTable t, Object v, boolean sel, boolean focus, int row, int col )
        {
            RunStatus rs = (RunStatus) v;
            if( rs != null && rs.status == GMirrorRunState.Status.RUNNING )
            {
                bar.setIndeterminate( rs.percent < 0 );
                bar.setValue( Math.max( 0, rs.percent ) );
                percent.setText( rs.percent >= 0 ? rs.percent + "%" : "" );
                percent.setForeground( t.getForeground() );
                running.setBackground( t.getBackground() );
                return running;
            }
            label.getTableCellRendererComponent( t, rs == null ? "" : rs.toString(), false, false, row, col );
            Color c = t.getForeground();
            Icon icon = null;
            if( rs != null )
                switch( rs.status )
                {
                    case DONE:      c = RUN_GREEN; icon = check; break;
                    case SKIPPED:   c = RUN_AMBER; icon = warn;  break;
                    case CANCELLED: c = RUN_RED;                 break;
                    default:        c = dimmed( t.getForeground(), t.getBackground() ); break;
                }
            label.setIcon( icon );
            label.setForeground( c );
            return label;
        }
    }
}
