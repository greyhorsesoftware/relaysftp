//
//  GTransferFilterManager.java
//  GSFTP
//
//  Created by David J. Clark on 2/22/05.
//  Copyright 2005 Grey Horse Software All rights reserved.
//

package	com.gideonsoftworks.gja;

import com.gideonsoftworks.gideon.core.GLocalized;
import com.gideonsoftworks.gideon.core.GideonConfiguration;
import com.gideonsoftworks.gcore.*;
import com.gideonsoftworks.gja.*;
import java.util.*;
import java.io.*;
import com.google.gson.*;

public class GTransferFilterManager
{
	private static	File								filtersFile		= null;
	private	static	ArrayList							tfmFilters		= null;

	static{

		try{

                tfmFilters = new ArrayList();

                filtersFile = new File( GLocalized.DATA_FOLDER_PATH + "TransferFilters.json");

                if( filtersFile.exists() )
                {
                    if( GideonConfiguration.TRANSFERFILTERSAVEDEBUG ) System.out.println("Loading TransferFilters.json");
                    loadFiltersFromJson(filtersFile);
                }

        }catch( Exception e )
        {
            throw new IllegalArgumentException(e.toString());
        }

    }

    private static void loadFiltersFromJson(File jsonFile)
    {
        try {
            JsonArray arr = JsonParser.parseString(java.nio.file.Files.readString(
                jsonFile.toPath(), java.nio.charset.Charset.defaultCharset())).getAsJsonArray();

            for( int i = 0; i < arr.size(); i++ )
            {
                JsonObject obj = arr.get(i).getAsJsonObject();
                boolean enabled = obj.has("enabled") && obj.get("enabled").getAsBoolean();
                String name     = obj.get("name").getAsString();
                int type        = obj.has("type") ? obj.get("type").getAsInt() : GTransferFilter.TF_CONTAINS;
                String value    = obj.get("value").getAsString();
                addFilter(new GTransferFilter(Boolean.valueOf(enabled), name, Integer.valueOf(type), value));
            }
        } catch( Exception e )
        {
            System.out.println("Error loading filters JSON: " + e.toString());
        }
    }


	public GTransferFilterManager()
	{

	}

	synchronized	public static	void addFilter( GTransferFilter f )
	{

		tfmFilters.add(f);

	}

	synchronized	public static	void removeFilter( GTransferFilter f )
	{

		tfmFilters.remove(f);

	}

	synchronized	public static	int getChildCount()
	{

		return tfmFilters.size();

	}

	synchronized	public static	GTransferFilter getChild(int i )
	{

		return (GTransferFilter)tfmFilters.get(i);

	}

	public static void saveTransferFilters()
    {

        int bCount = tfmFilters.size();

        if( bCount == 0 )
        {
            if( filtersFile.exists() )
            {
                filtersFile.delete();
            }
            return;
        }

        try{

            JsonArray arr = new JsonArray();

            for( int i = 0 ; i < bCount ; i++ )
            {
                GTransferFilter cBM = (GTransferFilter)tfmFilters.get(i);

                JsonObject obj = new JsonObject();
                obj.addProperty("enabled", cBM.getEnabled().booleanValue());
                obj.addProperty("name", cBM.getName());
                obj.addProperty("type", cBM.getType().intValue());
                obj.addProperty("value", cBM.getMask());
                arr.add(obj);
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            GAtomicFileWriter.writeUtf8(filtersFile, gson.toJson(arr));

        }catch( IOException e )
        {
            GAuditLog.error( "", "transfer-filters-save", null, e );
            GAtomicFileWriter.showSaveError( "Failed to save transfer filters: " + e.getMessage() );
        }

    }

	/**
	*
	* Returns true if the passed in file item passes all filters.
	*
	*/
	synchronized public static boolean matchesFilter( GFileItem fi )
	{
		boolean	retValue = false;
		int		i = 0;
		int		count = tfmFilters.size();

		for( i = 0 ; i < count ; i++ )
		{
			GTransferFilter	tf = (GTransferFilter)tfmFilters.get(i);

			// if the item is enabled return false
			if( !tf.getEnabled().booleanValue() )
			{
				if( GideonConfiguration.FILTERINGDEBUG ) System.out.println("Filter not enabled");
				continue;
			}

			int		fType		= tf.getType().intValue();
			String	value		= tf.getMask();
			String  fileName	= fi.getName();

			if( GideonConfiguration.FILTERINGDEBUG ) System.out.println("Testing : \"" + fileName + "\" against \"" + value + "\"" + fType );

			switch(fType)
			{
				case GTransferFilter.TF_CONTAINS:
					if( GideonConfiguration.FILTERINGDEBUG ) System.out.println("Contains");
					if( fileName.indexOf(value) >=0 )
					{
						retValue = true;
					}
				break;

				case GTransferFilter.TF_MATCHES:
					if( GideonConfiguration.FILTERINGDEBUG ) System.out.println("Matches");
					if( fileName.compareToIgnoreCase(value) == 0 )
					{
						retValue = true;
					}
				break;

				case GTransferFilter.TF_ENDSWITH:
					if( GideonConfiguration.FILTERINGDEBUG ) System.out.println("Ends With");
					retValue = fileName.endsWith(value);
				break;

				case GTransferFilter.TF_STARTSWITH:
					if( GideonConfiguration.FILTERINGDEBUG ) System.out.println("Starts With");
					retValue = fileName.startsWith(value);
				break;

			}


		}

		if( GideonConfiguration.FILTERINGDEBUG ) System.out.println("Returning " + retValue );

		return retValue;

	}


}
