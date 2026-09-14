//
//  GFindResult.java
//  GSFTP
//
//  Created by David J. Clark on 1/20/05.
//  Copyright 2005 Grey Horse Software All rights reserved.
//

package	com.gideonsoftworks.editor;

import com.gideonsoftworks.editor.*;

public class GFindResult 
{
	private	int		gfrStartIndex;
	private	int		gfrEndIndex;
	private String	gfrValue;
	
	public GFindResult( int startIndex, int endIndex, String findString )
	{
		super();
		
		gfrStartIndex = startIndex;
		gfrEndIndex = endIndex;
		gfrValue=findString;
	}
	
	public int	getStartIndex()
	{
		return gfrStartIndex;
	}
	
	public int getEndIndex()
	{
		return gfrEndIndex;
	}
	
	public String	getFindString()
	{
		return gfrValue;
	}
	
}
