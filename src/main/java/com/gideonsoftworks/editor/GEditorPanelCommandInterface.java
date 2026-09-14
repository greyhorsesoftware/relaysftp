//
//  GEditorPanelCommandInterface.java
//  GSFTP
//
//  Created by David on 12/13/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.editor;

public interface GEditorPanelCommandInterface 
{

public void copy();
public void paste();
public void cut();
public void selectAll();
public void replaceSelection(String newString);
public void select(int selectStart, int selectionEnd);

}
