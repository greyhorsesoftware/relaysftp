//
//  GValidationInterface.java
//  Gideon
//
//  Created by David on 10/13/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.core;

import javax.swing.*;

public interface GValidationInterface 
{
/**
    Method that is implemented by GUIAction and subclasses to implement the menu validation architecture.
*/
public boolean validateItem();
}
