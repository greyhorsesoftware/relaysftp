//
//  GJobUserMessage.java
//  GSFTP
//
//  Created by David J. Clark on 1/25/05.
//  Copyright 2005 Grey Horse Software All rights reserved.
//

package	com.gideonsoftworks.gja;

import com.gideonsoftworks.gcore.*;
import java.util.*;

public class GJobUserMessage 
{

	public	static	int		NO_ACTION		=-1;
	public	static	int		CANCEL_ACTION	= 0;
	public	static	int		SKIP_ACTION		= 1;
	public	static	int		REPLACE_ACTION	= 2;

	public	static	int		TYPE_COLLISION		= 0;
	public	static	int		TYPE_UNREADABLE		= 1;

	private	GFileItem	gcLocalItem		= null;
	private	GFileItem	gcRemoteItem	= null;
	private	GJobPlugin	gcPlugin		= null; // used to "signal" the job thread
	private int			gcAction		= 0;
	private int			gcType			= TYPE_COLLISION;
	private boolean		gcApplysToAll	= false;
    private boolean		gjuUserThinking = true;
	private String		gjuActionTitleString = null;
	private String		gjuActionFileString = null;
	private List<String> gjuProblemPaths = null;

	public GJobUserMessage( GFileItem localItem, GFileItem remoteItem, String actionTitleString, String actionFileString )
	{
		super();

		gcLocalItem = localItem;
		gcRemoteItem = remoteItem;
		gjuActionTitleString = actionTitleString;
		gjuActionFileString = actionFileString;

	}

	public GJobUserMessage( int type, List<String> problemPaths )
	{
		super();
		gcType = type;
		gjuProblemPaths = problemPaths;
	}

	public int getType() { return gcType; }
	public List<String> getProblemPaths() { return gjuProblemPaths; }
	
	public String getActionTitleString()
	{
		return gjuActionTitleString;
	}
	
	public String getActionFileString()
	{
		return gjuActionFileString;
	}
	
	public GFileItem getLocalItem()
	{
		return gcLocalItem;
	}
		
	public GFileItem getRemoteItem()
	{
		return gcRemoteItem;
	}
	
	public int getAction()
	{
		return gcAction;
	}
	
	public void setAction( int action )
	{
		gcAction = action;
		
	}
	
	public void  setUserThinking( boolean uT )
	{
		gjuUserThinking = uT;
	}
	
	public boolean	getUserThinking()
	{
		
		return gjuUserThinking;
	
	}
	
	public void setApplysToAll( boolean applysToAll )
	{
		gcApplysToAll = applysToAll;
	}
	
	public boolean getApplysToAll()
	{
		return gcApplysToAll;
	}
	
	public boolean isReplace()
	{	
		if( gcAction == REPLACE_ACTION ) return true;
		return false;
	}
	
	public boolean isSkip()
	{	
		if( gcAction == SKIP_ACTION ) return true;
		return false;
	}
	
	public boolean isCancellation()
	{	
		if( gcAction == CANCEL_ACTION ) return true;
		return false;
	}
	
}
