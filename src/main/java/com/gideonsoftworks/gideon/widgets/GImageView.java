//
//  GImageVIew.java
//  GSFTP
//
//  Created by David on 12/2/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.widgets;

import com.gideonsoftworks.gideon.core.GLocalized;
import javax.swing.*;
import java.awt.*;

public class GImageView extends JPanel
{
    Image           img=null;
	private	JLabel	statusLabel;
    
    public GImageView()
    {
        
		super();
		
		setLayout(new GridBagLayout());
		statusLabel = new JLabel(GLocalized.OMP);
		statusLabel.setIcon(GLocalized.PREVIEW_ICON);
		add( statusLabel, new GridBagConstraints() );

    }
      
    public void setImage( Image i )
    {

        img = i;
        
    }
	
    public void update()
    {
		hideStatus();
		repaint();
	}
	
	public void hideStatus()
	{
		remove(statusLabel);
	}
	
    public void paintComponent(Graphics g) {

		super.paintComponent(g);
        
        if(img!=null)
        {

            int vW = getWidth();
            int vH = getHeight();
            
            int iW = img.getWidth(null);
            int iH = img.getHeight(null);
            
            int fW = 0;
            int fH = 0;
            int fX = 0;
            int fY = 0;
            
            if( iW < vW ||
                iH < vH )
            {
                
                fW = img.getWidth(null);
                fH = img.getHeight(null);
                fX = (vW-fW)/2;
                fY = (vH-fH)/2;
                
            }else{

                fW = getWidth();
                fH = getHeight();
                fX = 0;
                fY = 0;

            }
            
            g.drawImage(img, fX, fY, fW, fH, null);
								
        }

    }

}
