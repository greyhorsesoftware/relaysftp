package com.gideonsoftworks.gcore;

import com.google.gson.Gson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the FTP firewall proxy definitions from {@code I8LN/ftp_firewall_types.json} (a bundled resource —
 * editing it adds/changes a firewall type with no code change). The list is {@link GFtpFirewallType#NONE}
 * followed by the file's entries, cached after the first read. A missing/malformed file degrades to just NONE.
 */
public final class GFtpFirewallTypes
{
    private GFtpFirewallTypes() {}

    private static final String RESOURCE = "/I8LN/ftp_firewall_types.json";
    private static volatile List<GFtpFirewallType> cache;

    public static List<GFtpFirewallType> list()
    {
        List<GFtpFirewallType> c = cache;
        if( c == null ) cache = c = load();
        return c;
    }

    /** Look up a type by its persisted id; unknown ids (incl. the default "NONE") resolve to {@link GFtpFirewallType#NONE}. */
    public static GFtpFirewallType byId( String id )
    {
        if( id != null )
            for( GFtpFirewallType t : list() ) if( t.id().equals( id ) ) return t;
        return GFtpFirewallType.NONE;
    }

    private static List<GFtpFirewallType> load()
    {
        List<GFtpFirewallType> out = new ArrayList<>();
        out.add( GFtpFirewallType.NONE );
        try( InputStream in = GFtpFirewallTypes.class.getResourceAsStream( RESOURCE ) )
        {
            if( in != null )
            {
                Dto[] dtos = new Gson().fromJson( new InputStreamReader( in, StandardCharsets.UTF_8 ), Dto[].class );
                if( dtos != null )
                    for( Dto d : dtos )
                        out.add( new GFtpFirewallType( d.id, d.label, d.description, d.usesUser, d.usesPassword, d.steps ) );
            }
        }
        catch( Exception e )
        {
            // Malformed/absent file: fall back to just NONE rather than breaking the app.
            GAuditLog.lifecycle( "ftp-firewall-types load failed: " + e );
        }
        return List.copyOf( out );
    }

    /** Gson shape of one JSON entry. */
    private static final class Dto
    {
        String id;
        String label;
        String description;
        boolean usesUser;
        boolean usesPassword;
        List<String> steps;
    }
}
