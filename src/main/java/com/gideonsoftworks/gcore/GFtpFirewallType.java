package com.gideonsoftworks.gcore;

import java.util.List;

/**
 * One FTP firewall proxy definition — loaded from {@code I8LN/ftp_firewall_types.json} by
 * {@link GFtpFirewallTypes}. Carries the user-facing label/description, which credential fields it uses, and
 * the ordered command {@code steps} (with {@code %host %port %user %pass %fwuser %fwpass} tokens) that
 * {@link GFtpProxyLogin} substitutes and sends. {@link #NONE} is the built-in "no proxy" sentinel.
 */
public final class GFtpFirewallType
{
    public static final GFtpFirewallType NONE =
        new GFtpFirewallType( "NONE", "None", "No proxy server in use.", false, false, List.of() );

    private final String       id;
    private final String       label;
    private final String       description;
    private final boolean      usesUser;
    private final boolean      usesPassword;
    private final List<String> steps;

    GFtpFirewallType( String id, String label, String description, boolean usesUser, boolean usesPassword, List<String> steps )
    {
        this.id           = id;
        this.label        = label;
        this.description  = description != null ? description : "";
        this.usesUser     = usesUser;
        this.usesPassword = usesPassword;
        this.steps        = steps != null ? List.copyOf( steps ) : List.of();
    }

    public String       id()           { return id; }
    public String       label()        { return label; }
    public String       description()  { return description; }
    public boolean      usesUser()     { return usesUser; }
    public boolean      usesPassword() { return usesPassword; }
    public List<String> steps()        { return steps; }

    public boolean isNone()    { return "NONE".equals( id ); }
    public boolean usesServer(){ return !isNone(); }   // host/port apply to every real firewall

    @Override public boolean equals( Object o ) { return o instanceof GFtpFirewallType t && id.equals( t.id ); }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() { return label; }
}
