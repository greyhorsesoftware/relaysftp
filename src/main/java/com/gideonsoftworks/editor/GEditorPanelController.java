//
//  GEditorPanelController.java
//  GSFTP
//
//  Created by David on 12/13/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.editor;

import com.gideonsoftworks.gideon.core.GGideonPanelController;
import com.gideonsoftworks.gideon.browser.GBrowserWindowCommandInterface;
import com.gideonsoftworks.gcore.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import java.io.*;
import java.util.HashMap;
import javax.swing.event.*;

public class GEditorPanelController extends GGideonPanelController implements GEditorPanelCommandInterface, MouseListener, DocumentListener
{

    private				RSyntaxTextArea                editorPane;
    private				GBrowserWindowCommandInterface epDelegate;
    private static		HashMap                        syntaxhighlighterMappings;
    private boolean		fIsDirty=false;

    // create a mapping of extensions to syntax style constants
    static{
        syntaxhighlighterMappings = new HashMap();

        // Web Languages
        syntaxhighlighterMappings.put( "java", SyntaxConstants.SYNTAX_STYLE_JAVA );
        syntaxhighlighterMappings.put( "js", SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT );
        syntaxhighlighterMappings.put( "xml", SyntaxConstants.SYNTAX_STYLE_XML );
        syntaxhighlighterMappings.put( "html", SyntaxConstants.SYNTAX_STYLE_HTML );
        syntaxhighlighterMappings.put( "htm", SyntaxConstants.SYNTAX_STYLE_HTML );
        syntaxhighlighterMappings.put( "css", SyntaxConstants.SYNTAX_STYLE_CSS );
        syntaxhighlighterMappings.put( "json", SyntaxConstants.SYNTAX_STYLE_JSON );
        syntaxhighlighterMappings.put( "php", SyntaxConstants.SYNTAX_STYLE_PHP );
        syntaxhighlighterMappings.put( "php3", SyntaxConstants.SYNTAX_STYLE_PHP );
        syntaxhighlighterMappings.put( "properties", SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE );
        syntaxhighlighterMappings.put( "jsp", SyntaxConstants.SYNTAX_STYLE_JSP );
        // Database
        syntaxhighlighterMappings.put( "sql", SyntaxConstants.SYNTAX_STYLE_SQL );

        // C/C++
        syntaxhighlighterMappings.put( "c", SyntaxConstants.SYNTAX_STYLE_C );
        syntaxhighlighterMappings.put( "h", SyntaxConstants.SYNTAX_STYLE_C );
        syntaxhighlighterMappings.put( "cpp", SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS );
        syntaxhighlighterMappings.put( "cc", SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS );
        syntaxhighlighterMappings.put( "c++", SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS );
        syntaxhighlighterMappings.put( "hpp", SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS );
        // Scripting
        syntaxhighlighterMappings.put( "py", SyntaxConstants.SYNTAX_STYLE_PYTHON );
        syntaxhighlighterMappings.put( "pl", SyntaxConstants.SYNTAX_STYLE_PERL );
        syntaxhighlighterMappings.put( "rb", SyntaxConstants.SYNTAX_STYLE_RUBY );
        syntaxhighlighterMappings.put( "bat", SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH );
        syntaxhighlighterMappings.put( "sh", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL );
        syntaxhighlighterMappings.put( "bash", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL );
        // Modern
        syntaxhighlighterMappings.put( "ts", SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT );
        syntaxhighlighterMappings.put( "go", SyntaxConstants.SYNTAX_STYLE_GO );
        syntaxhighlighterMappings.put( "rs", SyntaxConstants.SYNTAX_STYLE_RUST );
        syntaxhighlighterMappings.put( "yaml", SyntaxConstants.SYNTAX_STYLE_YAML );
        syntaxhighlighterMappings.put( "yml", SyntaxConstants.SYNTAX_STYLE_YAML );
        syntaxhighlighterMappings.put( "md", SyntaxConstants.SYNTAX_STYLE_MARKDOWN );
        syntaxhighlighterMappings.put( "groovy", SyntaxConstants.SYNTAX_STYLE_GROOVY );
        syntaxhighlighterMappings.put( "gradle", SyntaxConstants.SYNTAX_STYLE_GROOVY );
        syntaxhighlighterMappings.put( "tex", SyntaxConstants.SYNTAX_STYLE_LATEX );

    }

    public  GEditorPanelController()
    {
        JPanel  np = new JPanel(new BorderLayout());

        editorPane = new RSyntaxTextArea();
        editorPane.setCodeFoldingEnabled(true);
        editorPane.setAntiAliasingEnabled(true);
        editorPane.addMouseListener(this);

        RTextScrollPane sp = new RTextScrollPane(editorPane);
        sp.setLineNumbersEnabled(true);

        // RSyntaxTextArea ships its own light colors regardless of LaF; in a dark
        // theme apply its bundled dark theme so the editor isn't bright white.
        // (After the scroll pane exists, so the line-number gutter is themed too.)
        if( com.formdev.flatlaf.FlatLaf.isLafDark() )
        {
            try{
                org.fife.ui.rsyntaxtextarea.Theme.load(getClass().getResourceAsStream(
                    "/org/fife/ui/rsyntaxtextarea/themes/dark.xml")).apply(editorPane);
            }catch( IOException ignored )
            {
                // fall back to the default light theme
            }
        }else{
            // the default light current-line tint is nearly invisible — use a soft
            // blue-gray so the caret line actually reads (dark.xml tunes its own)
            editorPane.setCurrentLineHighlightColor(new Color(0xF0F3F8));
        }
        editorPane.setHighlightCurrentLine(true);

        np.add(sp, BorderLayout.CENTER);

        setPanel(np);
    }

	/**
	*
	*
	*
	*/
	public void grabFocus()
	{

		// focus editor
		editorPane.requestFocus();

		// fake a click in our text view
		setHasFocus(true);
        if( getBuddy()!=null ) getBuddy().setHasFocus(false);

	}

    /*
    *
    * Document Listener Interface
    *
    */
    public  void changedUpdate(DocumentEvent e)
    {

    }

    public void insertUpdate(DocumentEvent e)
    {
    }

    public void removeUpdate(DocumentEvent e)
    {
    }

    /*
    *
    * end dli
    */

    public  void setUndoRedoListener(GEditorWindow e)
    {
        editorPane.getDocument().addUndoableEditListener(e);
    }

    public void setDelegate( GBrowserWindowCommandInterface del )
    {

        epDelegate = del;

    }

    /*
    *
    *
    *
    */

    public void clear()
    {
        editorPane.setText("");
    }

    public void setEditable( boolean editable )
    {
        editorPane.setEditable(editable);

		if( editable )
		{
			editorPane.setCaretPosition(0);
		}

    }

    public String getText()
    {
        return editorPane.getText();
    }

    /*
    *
    * GEditorPanelCommandInterface methods
    *
    */
    public void copy(){ editorPane.copy(); }
    public void paste(){ editorPane.paste(); }
    public void cut(){ editorPane.cut(); }
    public void selectAll(){ editorPane.selectAll(); }

	public void replaceSelection(String newString)
	{
		editorPane.replaceSelection(newString);
	}

	public void select(int selectStart, int selectEnd)
	{
		editorPane.select(selectStart,selectEnd);
	}

    /**
    *
    *
    *
    *
    */

public void loadFile( GFileItem f )
{
    // configure the ui for syntax highlighting based on text file extension
    String ext = f.getFileNameExtension();

    if( ext != null )
    {
        String syntaxStyle = (String)syntaxhighlighterMappings.get(ext);

        if( syntaxStyle != null )
        {
            editorPane.setSyntaxEditingStyle(syntaxStyle);
        }else{
            editorPane.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        }
    }else{
        editorPane.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
    }

    // read the file (default charset, preserving prior line-by-line behavior)
    try{
        java.util.List<String> lines = java.nio.file.Files.readAllLines(
            java.nio.file.Path.of(f.getCompletePath()), java.nio.charset.Charset.defaultCharset());
        editorPane.setText( lines.isEmpty() ? "" : String.join("\n", lines) + "\n" );
    }catch( java.io.IOException e )
    {
        // TODO: report read error to the user
    }

    // place the cursor at the beginning of the document
    editorPane.setCaretPosition(0);

    // register for change notifications - we do it AFTER the intial load so the dirty state does
    // not trigger from the intial load.
    editorPane.getDocument().addDocumentListener((DocumentListener)this);

}

/*
*
* Mouse Listener
*
*/

public  void    mousePressed( MouseEvent e )
{
    if( e.getClickCount() == 1 )
    {
        setHasFocus(true);
        if( getBuddy()!=null ) getBuddy().setHasFocus(false);
        ((GBrowserWindowCommandInterface)epDelegate).validateToolbar();
    }
}

public  void    mouseClicked( MouseEvent e ){}
public  void    mouseReleased( MouseEvent e ){}
public  void    mouseEntered( MouseEvent e ){}
public  void    mouseExited( MouseEvent e ){}

}
