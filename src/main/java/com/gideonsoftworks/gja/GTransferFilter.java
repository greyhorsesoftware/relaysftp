//
//  GTransferFilter.java
//  GSFTP
//
//  Created by David J. Clark on 2/22/05.
//  Copyright 2005 Grey Horse Software All rights reserved.
//

package	com.gideonsoftworks.gja;

import com.gideonsoftworks.gja.*;
import java.util.*;

public class GTransferFilter 
{
	public	final	static	int	TF_NONE			= 0;
	public	final	static	int	TF_CONTAINS		= 1;
	public	final	static	int	TF_MATCHES		= 2;
	public	final	static	int	TF_STARTSWITH	= 3;
	public	final	static	int	TF_ENDSWITH		= 4;
	
	
	private	Boolean	tfEnabled	= Boolean.valueOf(false);
	private	String	tfName		= null;
	private	Integer	tfType		= Integer.valueOf(TF_NONE);
	private String	tfCompare	= null;
	
	public	GTransferFilter( Boolean enabled, String name, Integer type, String value )
	{
		super();
		
		tfEnabled = enabled;
		tfName = name;
		tfType = type;
		tfCompare = value;
	
	}
	
	public void	setEnabled( Boolean val )
	{
		
		tfEnabled = val;
	
	}
	
	public void	setName( String val )
	{
	
		tfName = val;
	
	}
	
	public	void	setType( Integer val )
	{
		
		tfType = val;
	
	}
	
	public void	setMask( String val )
	{
	
		tfCompare = val;
	
	}
	
	
	public Boolean	getEnabled()
	{
		
		return tfEnabled;
	
	}
	
	public String	getName()
	{
	
		return tfName;
	
	}
	
	public	Integer	getType()
	{
		
		return tfType;
	
	}
	
	public String	getMask()
	{
	
		return tfCompare;
	
	}
	
	
}
