package com.gideonsoftworks.gideon.actions;

import com.gideonsoftworks.gcore.GFileItem;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/** {@link GUIAction#homeFolderTarget} — what "Set as Home Folder" acts on. */
class GUIActionHomeFolderTest
{
    private static GFileItem dir( String name )  { GFileItem f = new GFileItem( null, name, 0, new Date() ); f.setFileType( GFileItem.G_FOLDER ); return f; }
    private static GFileItem file( String name ) { GFileItem f = new GFileItem( null, name, 5, new Date() ); f.setFileType( GFileItem.G_FILE );   return f; }

    @Test void noSelection_usesCurrentFolder()
    {
        GFileItem cwd = dir( "cwd" );
        assertSame( cwd, GUIAction.homeFolderTarget( null, cwd ) );
        assertSame( cwd, GUIAction.homeFolderTarget( new GFileItem[0], cwd ) );
    }

    @Test void singleFolder_isTheTarget()
    {
        GFileItem sub = dir( "sub" );
        assertSame( sub, GUIAction.homeFolderTarget( new GFileItem[]{ sub }, dir( "cwd" ) ) );
    }

    @Test void singleFile_isNotATarget()
    {
        assertNull( GUIAction.homeFolderTarget( new GFileItem[]{ file( "a.txt" ) }, dir( "cwd" ) ) );
    }

    @Test void parentFolderRow_isNotATarget()
    {
        GFileItem up = new GFileItem( null, "..", 0, new Date() );
        up.setFileType( GFileItem.G_CDUP );
        assertNull( GUIAction.homeFolderTarget( new GFileItem[]{ up }, dir( "cwd" ) ) );
    }

    @Test void multipleFolders_areNotATarget()
    {
        assertNull( GUIAction.homeFolderTarget( new GFileItem[]{ dir( "a" ), dir( "b" ) }, dir( "cwd" ) ) );
    }
}
