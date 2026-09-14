//
//  GEditorUIAction.java
//  GSFTP
//
//  Created by David on 12/13/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.editor;

import com.gideonsoftworks.gideon.core.GFrame;
import com.gideonsoftworks.gideon.core.GFrameManager;
import com.gideonsoftworks.gideon.actions.GUIAction;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
*
*   Used for all editor menu/toolbar items etc...
*   Handles filtering out non-editor window sources.
*
*/

public class GEditorUIAction extends GUIAction 
{

    public  GEditorUIAction( String name, int gaCode )
    {
        super(name,gaCode);
    }
    
    public  GEditorUIAction( String name, Icon icon, int gaCode )
    {
        super(name, icon, gaCode);
    }

    public boolean shouldValidateItem()
    {
    
        int actCode = getGideonActionCode();
        
        if( ( actCode == GA_UNDO ) ||
            ( actCode == GA_REDO ) )
        {
            return false;
        }
    
        return true;
    
    }

    public boolean validateItem()
    {
        
        GFrameManager                   theManager      = GUIAction.getSharedGFrameManager();
        GFrame                          keyWindow       = null;
        GEditorPanelCommandInterface    ed              = null;
        
        if( theManager != null )
        {
            keyWindow = theManager.getKeyWindow();
        }
        
        if( keyWindow==null) return false;
        
        if( !(keyWindow instanceof GEditorWindow))
        {
            return false;
        }
        
        ed = ((GEditorWindow)keyWindow).getFocusedEditor();
        
        if( ed == null )
        {
            return false;
        }
        
        return true;
        
    }
    
    public void  actionPerformed( ActionEvent e )
    {
        
        GFrameManager                   theManager      = GUIAction.getSharedGFrameManager();
        GEditorWindow                   editorWindow    = (GEditorWindow)theManager.getKeyWindow();
        GEditorPanelCommandInterface    ed              = null;
        
        ed = editorWindow.getFocusedEditor();
        
        if( ed == null )
        {
            return;
        }
    
        switch( this.getGideonActionCode() )
        {

            case GA_COPY:
                ed.copy();
            break;
            
            case GA_PASTE:
                ed.paste();
            break;
            
            case GA_CUT:
                ed.cut();
            break;
            
            case GA_UNDO:
                editorWindow.undo();
            break;
            
            case GA_SELECTALL:
                ed.selectAll();
            break;
            
            case GA_REDO:
                editorWindow.redo();
            break;
            
            case GA_SAVEAS:
            case GA_SAVEACOPYAS:
                // Save As not supported in simplified editor
            break;

            case GA_SAVE:
                editorWindow.save();
            break;
            
        }

    }
    
}
