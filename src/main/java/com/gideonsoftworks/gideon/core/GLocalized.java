//
//  GLocalizedStrings.java
//  Gideon
//
//  Created by David on 10/8/04.
//  Copyright 2004 Grey Horse Software All rights reserved.
//

package com.gideonsoftworks.gideon.core;

import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.net.*;
import java.util.Locale;
import java.io.File;

/**  
 * A class to provide internationalized strings and resources.
 *
 */

public final class GLocalized {
    
        /** Localized image Paths */
        public  static  final   String  PLATFORM_IMAGE_PATH;
        public  static  final   String  GENERIC_PLATFORM_IMAGE_PATH;
        
	/** Localized String Constants */
        public static final String  OMP;

        /** Menu + Toolbar items */
        public static final String  FAVORITESTABTITLE;
        public static final String  CHOOSE;
        public static final String  FILE_MENU;
        public static final String  NEWBROWSERMENU;
        public static final String  NEWBROWSER;
        public static final String  NEWREMOTEONLY;
        public static final String  NEWCONNECTION;
        public static final String  NEWFOLDER;
        public static final String  NEWFILE;
        public static final String  CLOSE;
        public static final String  EDIT;
        public static final String  PREVIEW;
        public static final String  GETINFO;
        public static final String  SAVEMENU;
        public static final String  SETPERMISSIONS;
        public static final String  COPYURL;
        public static final String  COPY_TEMP_LINK;
        public static final String  DELETE;
        public static final String  RENAME;
        public static final String  DOWNLOAD;
        public static final String  UPLOAD;
        public static final String  TRANSFERMODE;
        public static final String  ASCII_TRANSFERMODE;
        public static final String  BINARY_TRANSFERMODE;
        public static final String  AUTO_TRANSFERMODE;
        public static final String  EXIT;
        public static final String  PERMISSIONS_COLUMN_TITLE;
        
        public static final String  EDIT_MENU;
        public static final String  CUT;
        public static final String  COPY;
        public static final String  PASTE;
        public static final String  SELECTALL;
        public static final String  UNDO;
        public static final String  REDO;
        public static final String  FIND;
        
        public static final String  BOOKMARK_MENU;
        public static final String  ADDBOOKMARK;
        public static final String  MANAGEBOOKMARK;
    
        public static final String  HELP_MENU;
        public static final String  MENU_EXPORT_DIAGNOSTICS;
        public static final String  DIAGNOSTICS_SAVED;
        public static final String  DIAGNOSTICS_FAILED;
        public static final String  RELAYHOME;
        public static final String  ABOUTRELAY;
        
        
        public static final String  SPECIAL_MENU;
        public static final String  CANCEL;
        public static final String  REFRESH;
        public static final String  DISCONNECT;
        public static final String  SHOW_HIDDEN_FILES;
        public static final String  OPTIONS;


        public static final String  WINDOW_MENU;
        public static final String  MINIMIZE;
        
        public static final String  CLEARHOMEPATH;
        public static final String  SETHOMEPATH;
        public static final String  PATH;
        
        /** Other Stuff */
        public static final String DEFAULTBROWSERNAME;
        public static final String BOOKMARKMANAGERNAME;
        
        /** Tooltips */
        public static final String SAVEACOPY_TOOLTIP;
		
        /** Task Manager */
        
        /** Bookmark Manager */
        public static final String NEWBM_TOOLTIP;
        public static final String DELBM_TOOLTIP;
        
        
        public static final String IDLE;
        
        /** Login panel */
        public static final String LP_HOSTNAME;
        public static final String LP_USERNAME;
        public static final String LP_PASSWORD;
        public static final String LP_REMOTEPATH;
        public static final String LP_PORT;
        public static final String LP_CONNECT;
        public static final String LP_BUCKET;
        public static final String LP_ACCESSKEY;
        public static final String LP_SECRETKEY;
        public static final String LP_REGION;
        public static final String LP_ENDPOINT;
        public static final String LP_PROVIDER;
        public static final String LP_AUTHMETHOD;
        public static final String LP_SESSIONTOKEN;

        /** Activity Panel */
        public static final String AP_CLEAR;

        /** Error panel */
        public static final String OK;
        
        /** Bookmark panel */
        public static final String BM_SETLOCALHOME;
        public static final String BM_NOPASSIVE;
        public static final String BM_LOCALFOLDER;
        
        /** Get Info */
        public static final String GI_TYPE;
        public static final String GI_HOST;
        public static final String GI_LOCATION;
        public static final String GI_SIZE;
        public static final String GI_MODIFIED;
        public static final String GI_OWNER;
        public static final String GI_GROUP;
        public static final String GI_ORIGINAL;
        
        /** Permission Panel */
        public static final String PP_APPLY;
        public static final String PP_APPLYTOCONTAINED;
        public static final String PP_OCTALVALUE;
        public static final String PP_SYMBOLICVALUE;
        public static final String PP_READ;
        public static final String PP_WRITE;
        public static final String PP_EXECUTE;
        public static final String PP_OWNER;
        public static final String PP_GROUP;
        public static final String PP_WORLD;
        
        /** General Preference Panel */
        public static final String PG_DEFAULTLOCALHOME;
        public static final String PG_KEEPALIVE;
        public static final String PG_SFTPDEFAULT;
        

        
        /** Saving files */
        public static final String SAVE;
        public static final String SAVEAS;
        public static final String CANCELBUTTON;
        
        /** Menus */
        public static final String	SAVEACOPYAS;
		public static final String	SAVELOCALCOPY;
		
        public static final String SHOWSIDEBAR;
		public static final String HIDESIDEBAR;
		
		public static final String PACKAGETYPE;
		
        /** Platform Specific Images */
        public static final ImageIcon CANCEL_ICON;
        public static final ImageIcon DELETE_ICON;
        public static final ImageIcon DISCONNECT_ICON;
        public static final ImageIcon GETINFO_ICON;
        public static final ImageIcon REFRESH_ICON;
        public static final ImageIcon MENUCHECK_ICON;
        public static final ImageIcon SIDEBAR_ICON;
        public static final ImageIcon DOWNLOAD_ICON;
        public static final ImageIcon UPLOAD_ICON;
        public static final ImageIcon PREVIEW_ICON;
        public static final ImageIcon EDIT_ICON;

        /** Vector toolbar icons (crisp at any DPI) */
        public static final FlatSVGIcon ACTIVITY_SVG_ICON;
        public static final FlatSVGIcon CANCEL_SVG_ICON;
        public static final FlatSVGIcon MIRROR_SVG_ICON;
        public static final FlatSVGIcon ADD_SVG_ICON;
        public static final FlatSVGIcon DELETE_SVG_ICON;

        /** selected icons */
        public static final ImageIcon NEWBM_ICON_SELECTED;
        public static final ImageIcon DELBM_ICON_SELECTED;
        
        /** other */
        
        /** Disabled icons */
        public static final ImageIcon NEWBM_ICON_DISABLED;
        public static final ImageIcon DELBM_ICON_DISABLED;
        
        /** Task Manager */
        
        /** Bookmark Manager */
        public static final ImageIcon NEWBM_ICON;
        public static final ImageIcon DELBM_ICON;

        /** Disabled Icons */
        /** File Item Icons */
        public static final ImageIcon SMALL_FOLDER_ICON;
        
        public static final ImageIcon SMALL_FILE_ICON;
        
        public static final ImageIcon SMALL_APP_ICON;
        
        public static final ImageIcon SMALL_LINK_ICON;
        
        public static final ImageIcon REM_MED_BIG;
        public static final ImageIcon HD_BIG;
        public static final ImageIcon FLOPPY_BIG;
        
        public static final ImageIcon CDUP_ICON;
        
        /** Status Area */
        public static final ImageIcon TASK_QUEUED_ICON;
        public static final ImageIcon TASK_PREPARING_ICON;
        public static final ImageIcon TASK_RUNNING_ICON;
        public static final ImageIcon TASK_ERROR_ICON;
        
        public static final ImageIcon SECURE_ICON;
        public static final ImageIcon INSECURE_ICON;
        public static final ImageIcon RELAYICON;
        public static final ImageIcon RELAY_SMALL_ICON;
        public static final ImageIcon APP_ICON;
        
        public static final ImageIcon SMALL_MYCOMPUTER_ICON;
        
        /** Tasks/Connections Remove buttons */
        
        /** Drag and Drop */
        
        public static final ImageIcon DESKTOP_BIG_ICON;
        public static final ImageIcon DESKTOP_SMALL_ICON;

        /** No-access composite icons (folder/file with prohibition badge) */
        public static final ImageIcon SMALL_FOLDER_NOACCESS_ICON;
        public static final ImageIcon SMALL_FILE_NOACCESS_ICON;
        
        
        
        /** Reg Stuff */
        
        /** Toolbar Titles */
        /** Browser */
        public static final String TB_SIDEBAR;  
        public static final String TB_NEWFOLDER;
        public static final String TB_GETINFO;
        public static final String TB_DELETE;
        public static final String TB_REFRESH;
        public static final String TB_CANCEL;
        public static final String TB_DISCONNECT;
        public static final String TB_ACTIVITY;
        public static final String TB_PREVIEW;
        public static final String TB_EDIT;
        public static final String TB_UPLOAD;
        public static final String TB_DOWNLOAD;
        
        /** Task Manager */
        public static final String TB_REMOVE;
        
        /** Bookmark Manager */
        public static final String TB_NEWBOOKMARK;
        public static final String TB_DELETEBOOKMARK;
        
        /** Editor */
        public static final String TB_SAVE;
        
        /** Get Info panel */
        public static final String GENERALTABTITLE;
        public static final String PERMISSIONTABTITLE;
        public static final String GETINFONAME;
        
        public static final String DEFAULTCONNECTIONNAME;
        public static final String SFTP;
        public static final String FTP;
        public static final String FTPS;
        public static final String S3;
        
        public static final String UNKNOWN_JOB_TYPE;
        public static final String DOWNLOAD_JOB_TYPE;
        public static final String UPLOAD_JOB_TYPE;
        public static final String DELETE_JOB_TYPE;
        public static final String CHMOD_JOB_TYPE;
        public static final String MOVE_JOB_TYPE;
        public static final String LIST_JOB_TYPE;
        public static final String RENAME_JOB_TYPE;
        public static final String LOGIN_JOB_TYPE;
        public static final String NEWFOLDER_JOB_TYPE;
        public static final String REFRESH_JOB_TYPE;
        public static final String DISCONNECT_JOB_TYPE;
        public static final String PING_JOB_TYPE;
        public static final String MTIME_JOB_TYPE;
        
        public static final String DATE_COLUMN_TITLE;
        public static final String SIZE_COLUMN_TITLE;
        public static final String NAME_COLUMN_TITLE;
        public static final String ITEMS_STRING;
        public static final String FOLDER;
        public static final String FILE;
        public static final String LINK;
        public static final String APP;
        public static final String HARD_DRIVE;
        public static final String REM_MEDIA;
        public static final String GETINFOTITLE;
        
        // Connection Exception Strings
        public static final String GCE_CONNECTION_LOST;
        public static final String GCE_OPERATION_NOT_SUPPORTED;
        public static final String GCE_PROTOCOL_ERROR;
        public static final String GCE_SERVER_NEEDS_ATTENTION;
        public static final String GCE_LISTING_PARSER_ERROR;
        public static final String GCE_BAD_LOGIN;
        public static final String GCE_BAD_FOLDER;
        public static final String GCE_NETWORK_ERROR;
        public static final String NOWINDOWSOPEN;
        
        // Icons
        public static final ImageIcon FOLDER_BIG;
        public static final ImageIcon FILE_BIG;
        public static final ImageIcon FILE_TXT_BIG;
        public static final ImageIcon FILE_IMG_BIG;
		
        public static final ImageIcon LINK_BIG;
        public static final ImageIcon APP_BIG;
        public static final ImageIcon UNKNOWN_BIG;
        public static final ImageIcon SMALL_HD_ICON;
        public static final ImageIcon SMALL_RM_ICON;
        public static final ImageIcon SMALL_FLOPPY_ICON;
        
        // Preview toolbar items
        public static final ImageIcon SAVE_ICON;
        
        // favorite folder icon

        // find
        public static final ImageIcon FIND_ICON;
	
        // Action Icon	
                
        // Sorting
        

        // Browser toolbar icons

        // Activity panel icons
        public static final FlatSVGIcon ACTIVITY_CANCEL_ICON;
        public static final FlatSVGIcon ACTIVITY_LOG_ICON;
        public static final FlatSVGIcon ACTIVITY_FIND_ICON;

        // dialogs
        public static final String NAME_FOLDER_STRING;
        public static final String NAME_FILE_STRING;
        public static final String NO_SELECTION;
        public static final String SET_PERMISSIONS;
        public static final String SURE_YOU_WANT_TO_DELETE_TITLE;
        public static final String ERROR_GETTINGCONNECTIONSTATUS;
        public static final String FTP_ENTERPASSANDUN;
        public static final String SFTP_ENTERPASSANDUN;
        public static final String ENTERHOSTNAME;
        
        // paths
        public static final String DATA_STORE_INACCESSIBLE;
        public static final String DATA_FOLDER_PATH;
        public static final String TEMP_FILE_PATH;
        public static final String HOME_FOLDER;
        public static final String TRYING_PW_AUTH;
        public static final String PK_AUTH;
        public static final String USING_SSH_KEY;
        public static final String USING_PORT;
        
        public static final String PLEASEENTERBOOKMARKNAME;
        
        // status
        public static final String UPLOADING;
        public static final String DOWNLOADING;
        public static final String PREPARING;
        public static final String CANCELLING;
        
        // queue names
        public static final String TASKS;
        public static final String TRANSFERS;
		public static final String TRANSFERFILTERS;
		public static final String TRANSFERFILTERTITLE;
		
        // something else
        public static final String SURE_YOU_WANT_TO_CLOSE_TITLE; 
        public static final String SURE_YOU_WANT_TO_CLOSE_MSG;
        
        // other
        public static final String COPYRIGHT;
        public static final String PREVIEWING;
        public static final String VERSION;
        public static final String MYCOMPUTER;

        // Data files
        public static final String ASCII_TYPES_FILE;
        public static final String BINARY_TYPES_FILE;
        public static final String PACKAGE_TYPES_FILE;
		
        public static final String UNTITLED;
        public static final String NOBOOKMARKS;
        public static final String TBFIND;
        
        /** Preferences window */
        public static final String PREFERENCESTITLE;
        public static final String NEW_FOLDER_TITLE;
        public static final String NEW_FILE_TITLE;
        public static final String RENAMING;
        public static final String FILEEXISTSTITLE;
        
        public static final String FILEEXISTSBLURB1;
        public static final String FILEEXISTSBLURB2;
        public static final String FILEACTIONBLURB;
        public static final String DOWNLOADED;
        public static final String UPLOADED;
        public static final String MOVED;
        public static final String DOWNLOAD_FE;
        public static final String UPLOAD_FE;
        public static final String MOVE;
        
        
        public static final String ADD_TF;
        public static final String ADD_TF_TOOLTIP;
        public static final String DEL_TF;
        public static final String DEL_TF_TOOLTIP;
        public static final String FORUM;
        
        public static final String ENABLEFOLLOWME;
        public static final String DISABLEFOLLOWME;
        public static final String MIRROR;
        
        public static final String LOCALFILESLABEL;
        public static final String REMOTEFILESLABEL;
        public static final String ENTERCOMPLETEPATH;
        public static final String ADD;
        public static final String ADDFAVORITE_LOCAL;
        public static final String SET_HOME_FOLDER;
        public static final String ADDFAVORITE_BOOKMARK;        
        public static final String FAVORITEFOLDERTABTITLE;
        public static final String CLOSE_TAB;
        
        public static final boolean	onMacOSX;
        public static final boolean	onWindows;
		
	static {
		final String BUNDLE_NAME = "I8LN/GideonStrings";
		final ResourceBundle STRING_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);
                
                String os = System.getProperty("os.name");
                if ( os != null && os.toLowerCase().contains("mac") )
                {
                    onMacOSX = true;
                    onWindows = false;
                }
                else if ( os != null && os.startsWith("Windows") )
                {
                    onMacOSX = false;
                    onWindows = true;
                }
                else
                {
                    onMacOSX = false;
                    onWindows = false;
                }
                
                GENERIC_PLATFORM_IMAGE_PATH = "/I8LN/images/generic/";
                
                if( onMacOSX )
                {
                    PLATFORM_IMAGE_PATH = "/I8LN/images/osx/";
                }else if(onWindows)
                {
                    PLATFORM_IMAGE_PATH = "/I8LN/images/win/";
                }else{
                    PLATFORM_IMAGE_PATH = "/I8LN/images/unix/";
                }
                
                CLOSE_TAB = getString(STRING_BUNDLE, "CLOSE_TAB");
                ADD = getString(STRING_BUNDLE, "ADD");
                ENTERCOMPLETEPATH = getString(STRING_BUNDLE, "ENTERCOMPLETEPATH");
                PATH = getString(STRING_BUNDLE, "PATH");
                OMP = getString(STRING_BUNDLE, "OMP");
                NOBOOKMARKS = getString(STRING_BUNDLE, "NOBOOKMARKS"); 
                NOWINDOWSOPEN = getString(STRING_BUNDLE, "NOWINDOWSOPEN");
                /** something */
                ADDFAVORITE_LOCAL = getString(STRING_BUNDLE, "ADDFAVORITE_LOCAL");  
                SET_HOME_FOLDER = getString(STRING_BUNDLE, "SET_HOME_FOLDER");
                ADDFAVORITE_BOOKMARK = getString(STRING_BUNDLE, "ADDFAVORITE_BOOKMARK"); 
                
                CHOOSE = getString(STRING_BUNDLE, "CHOOSE");
                FAVORITESTABTITLE = getString(STRING_BUNDLE, "FAVORITESTABTITLE");
                
                /** Login Panel */
                LP_HOSTNAME = getString(STRING_BUNDLE, "LP_HOSTNAME");
                LP_USERNAME = getString(STRING_BUNDLE, "LP_USERNAME");
                LP_PASSWORD = getString(STRING_BUNDLE, "LP_PASSWORD");
                LP_REMOTEPATH = getString(STRING_BUNDLE, "LP_REMOTEPATH");
                LP_PORT = getString(STRING_BUNDLE, "LP_PORT");
                LP_CONNECT = getString(STRING_BUNDLE, "LP_CONNECT");
                LP_BUCKET = getString(STRING_BUNDLE, "LP_BUCKET");
                LP_ACCESSKEY = getString(STRING_BUNDLE, "LP_ACCESSKEY");
                LP_SECRETKEY = getString(STRING_BUNDLE, "LP_SECRETKEY");
                LP_REGION = getString(STRING_BUNDLE, "LP_REGION");
                LP_ENDPOINT = getString(STRING_BUNDLE, "LP_ENDPOINT");
                LP_PROVIDER = getString(STRING_BUNDLE, "LP_PROVIDER");
                LP_AUTHMETHOD = getString(STRING_BUNDLE, "LP_AUTHMETHOD");
                LP_SESSIONTOKEN = getString(STRING_BUNDLE, "LP_SESSIONTOKEN");

                /** Activity Panel */
                AP_CLEAR = getString(STRING_BUNDLE, "AP_CLEAR");

                /** Permissions Panel */
                PP_APPLY = getString(STRING_BUNDLE, "PP_APPLY");
                PP_APPLYTOCONTAINED = getString(STRING_BUNDLE, "PP_APPLYTOCONTAINED");
                PP_OCTALVALUE = getString(STRING_BUNDLE, "PP_OCTALVALUE");
                PP_SYMBOLICVALUE = getString(STRING_BUNDLE, "PP_SYMBOLICVALUE");
                PP_READ = getString(STRING_BUNDLE, "PP_READ");
                PP_WRITE = getString(STRING_BUNDLE, "PP_WRITE");
                PP_EXECUTE = getString(STRING_BUNDLE, "PP_EXECUTE");
                PP_OWNER = getString(STRING_BUNDLE, "PP_OWNER");
                PP_GROUP = getString(STRING_BUNDLE, "PP_GROUP");
                PP_WORLD = getString(STRING_BUNDLE, "PP_WORLD");
                
                
                /** Error panel */
                OK = getString(STRING_BUNDLE, "OK");
                
                /** Bookmark manager */
                BM_SETLOCALHOME = getString(STRING_BUNDLE, "BM_SETLOCALHOME");
                BM_NOPASSIVE = getString(STRING_BUNDLE, "BM_NOPASSIVE");
                BM_LOCALFOLDER = getString(STRING_BUNDLE, "BM_LOCALFOLDER");
                 
				MIRROR = getString(STRING_BUNDLE, "MIRROR");
				 
                /** Get Info */
                GI_TYPE = getString(STRING_BUNDLE, "GI_TYPE");
                GI_HOST = getString(STRING_BUNDLE, "GI_HOST");
                GI_LOCATION = getString(STRING_BUNDLE, "GI_LOCATION");
                GI_SIZE = getString(STRING_BUNDLE, "GI_SIZE");
                GI_MODIFIED = getString(STRING_BUNDLE, "GI_MODIFIED");
                GI_OWNER = getString(STRING_BUNDLE, "GI_OWNER");
                GI_GROUP = getString(STRING_BUNDLE, "GI_GROUP");
                GI_ORIGINAL = getString(STRING_BUNDLE, "GI_ORIGINAL");
                
                COPYRIGHT = getString(STRING_BUNDLE, "copyright");
                MYCOMPUTER = getString(STRING_BUNDLE, "MYCOMPUTER");
                          
                GCE_CONNECTION_LOST = getString(STRING_BUNDLE, "GCE_CONNECTION_LOST");
                GCE_OPERATION_NOT_SUPPORTED = getString(STRING_BUNDLE, "GCE_OPERATION_NOT_SUPPORTED");
                GCE_SERVER_NEEDS_ATTENTION = getString(STRING_BUNDLE, "GCE_SERVER_NEEDS_ATTENTION");
                GCE_LISTING_PARSER_ERROR = getString(STRING_BUNDLE, "GCE_LISTING_PARSER_ERROR");
                GCE_BAD_LOGIN = getString(STRING_BUNDLE, "GCE_BAD_LOGIN");
                GCE_BAD_FOLDER = getString(STRING_BUNDLE, "GCE_BAD_FOLDER");
                GCE_PROTOCOL_ERROR = getString(STRING_BUNDLE, "GCE_PROTOCOL_ERROR");
                GCE_NETWORK_ERROR = getString(STRING_BUNDLE, "GCE_NETWORK_ERROR"); 
                   
                PERMISSIONS_COLUMN_TITLE = getString(STRING_BUNDLE, "PERMISSIONS_COLUMN_TITLE");  
                   
                // Load Tooltip strings
				SAVEACOPY_TOOLTIP = getString(STRING_BUNDLE, "SAVEACOPY_TOOLTIP"); 
				
				ENABLEFOLLOWME = getString(STRING_BUNDLE, "ENABLEFOLLOWME"); 
				DISABLEFOLLOWME = getString(STRING_BUNDLE, "DISABLEFOLLOWME"); 
				
                if( onMacOSX )
                {
                }else{
                }
 
                
                // task manager tooltips
                
                /** Bookmark manager tooltips */
                NEWBM_TOOLTIP = getString(STRING_BUNDLE, "newbmTooltip");
                DELBM_TOOLTIP = getString(STRING_BUNDLE, "deletebmTooltip");
                
                /** Editor Tooltips */
                
                /** Save File Dialog + Error friends */
                NEW_FOLDER_TITLE = getString(STRING_BUNDLE, "NEW_FOLDER_TITLE");
                NEW_FILE_TITLE = getString(STRING_BUNDLE, "NEW_FILE_TITLE");
				RENAMING = getString(STRING_BUNDLE, "RENAMING");
				FILEEXISTSTITLE = getString(STRING_BUNDLE, "FILEEXISTSTITLE");
				TRANSFERFILTERTITLE = getString(STRING_BUNDLE, "TRANSFERFILTERTITLE");
				
                if( onWindows )
                {
                }else{
                }
                
                if( onWindows )
                {
                    SAVE = getString(STRING_BUNDLE, "SAVE_win");
                }else{
                    SAVE = getString(STRING_BUNDLE, "SAVE");
                }
                
                SAVEAS = getString(STRING_BUNDLE, "SAVEAS");
                SAVEACOPYAS = getString(STRING_BUNDLE, "SAVEACOPYAS");
                SAVELOCALCOPY = getString(STRING_BUNDLE, "SAVELOCALCOPY");
				PACKAGETYPE = getString(STRING_BUNDLE, "PACKAGETYPE");
				
                if( onMacOSX )
                {
                }else{
                }
            
                UNTITLED = getString(STRING_BUNDLE, "UNTITLED");
			
                CANCELBUTTON = getString(STRING_BUNDLE, "CANCEL");
   
                // Load File Menu Items
                FILE_MENU = getString(STRING_BUNDLE, "fileMenu");
                NEWBROWSERMENU = getString(STRING_BUNDLE, "fmNewBrowserMenu");
                NEWBROWSER = getString(STRING_BUNDLE, "fmNewBrowser");
                NEWREMOTEONLY = getString(STRING_BUNDLE, "fmNewRemoteOnly");
                NEWCONNECTION = getString(STRING_BUNDLE, "fmNewConnection");
                NEWFOLDER = getString(STRING_BUNDLE, "fmNewFolder");
                NEWFILE = getString(STRING_BUNDLE, "fmNewFile");
                CLOSE = getString(STRING_BUNDLE, "fmClose");
                EDIT = getString(STRING_BUNDLE, "fmEdit");
                SAVEMENU = getString(STRING_BUNDLE, "fmSave");
                PREVIEW = getString(STRING_BUNDLE, "fmPreview");
                EXIT = getString(STRING_BUNDLE, "fmExit");
                
                if( onMacOSX )
                {
                    GETINFO = getString(STRING_BUNDLE, "fmGetInfo");
                }else{
                    GETINFO = getString(STRING_BUNDLE, "fmGetInfo_win");
                }
                
                UPLOADING = getString(STRING_BUNDLE, "UPLOADING");
                DOWNLOADING = getString(STRING_BUNDLE, "DOWNLOADING");
                PREPARING = getString(STRING_BUNDLE, "PREPARING");
                
                SETPERMISSIONS = getString(STRING_BUNDLE, "fmSetPermissions");
                COPYURL = getString(STRING_BUNDLE, "fmCopyURLToClipboard");
                COPY_TEMP_LINK = getString(STRING_BUNDLE, "fmCopyTempLink");
                DELETE = getString(STRING_BUNDLE, "fmDelete");
                RENAME = getString(STRING_BUNDLE, "fmRename");
                DOWNLOAD = getString(STRING_BUNDLE, "fmDownload");
                UPLOAD = getString(STRING_BUNDLE, "fmUpload");
                TRANSFERMODE = getString(STRING_BUNDLE, "fmTransferMode");
                ASCII_TRANSFERMODE = getString(STRING_BUNDLE, "fmTMASCII");
                BINARY_TRANSFERMODE = getString(STRING_BUNDLE, "fmTMBinary");
                AUTO_TRANSFERMODE = getString(STRING_BUNDLE, "fmTMAutomatic");
                
                // Load EDIT Menu Items
                EDIT_MENU = getString(STRING_BUNDLE, "editMenu");
                CUT = getString(STRING_BUNDLE, "emCut");
                COPY = getString(STRING_BUNDLE, "emCopy");
                PASTE = getString(STRING_BUNDLE, "emPaste");
                SELECTALL = getString(STRING_BUNDLE, "emSelectAll");
                UNDO = getString(STRING_BUNDLE, "UNDO");
                REDO = getString(STRING_BUNDLE, "REDO");
                FIND = getString(STRING_BUNDLE, "emFind");
                
                
                // Bookmark Menu
                BOOKMARK_MENU = getString(STRING_BUNDLE, "bookmarkMenu");
                ADDBOOKMARK = getString(STRING_BUNDLE, "bmAddBookmark");  
                MANAGEBOOKMARK = getString(STRING_BUNDLE, "bmManageBookmarks"); 
                PLEASEENTERBOOKMARKNAME = getString(STRING_BUNDLE, "PLEASEENTERBOOKMARKNAME");
                
                
                // Special Menu
                if( onMacOSX )
                {
                    SPECIAL_MENU = getString(STRING_BUNDLE, "specialMenu");
                }else{
                    SPECIAL_MENU = getString(STRING_BUNDLE, "specialMenu_win");
                }

                CANCEL = getString(STRING_BUNDLE, "smCancel");  
                REFRESH = getString(STRING_BUNDLE, "smRefresh"); 
                DISCONNECT = getString(STRING_BUNDLE, "smDisconnect"); 
                SHOW_HIDDEN_FILES = getString(STRING_BUNDLE, "smShowHiddenFiles");
                
                OPTIONS = getString(STRING_BUNDLE, "smOptions"); 
                
                // Window Menu
                WINDOW_MENU = getString(STRING_BUNDLE, "windowMenu");
                MINIMIZE = getString(STRING_BUNDLE, "wmMinimize");
                
                // Help Menu
                HELP_MENU = getString(STRING_BUNDLE, "helpMenu");
                MENU_EXPORT_DIAGNOSTICS = getString(STRING_BUNDLE, "hmExportDiagnostics");
                DIAGNOSTICS_SAVED = getString(STRING_BUNDLE, "diagnosticsSaved");
                DIAGNOSTICS_FAILED = getString(STRING_BUNDLE, "diagnosticsFailed");
                RELAYHOME = getString(STRING_BUNDLE, "hmGideonHome");
                ABOUTRELAY = getString(STRING_BUNDLE, "hmAbout");
                
                // Other
                DEFAULTBROWSERNAME = getString(STRING_BUNDLE, "defaultBrowserName" );
                
                CLEARHOMEPATH = getString(STRING_BUNDLE, "clearHomePath" );
                SETHOMEPATH = getString(STRING_BUNDLE, "setHomePath" );
                IDLE = getString(STRING_BUNDLE, "idle" );
                CANCELLING = getString(STRING_BUNDLE, "CANCELLING" );
                TASKS = getString(STRING_BUNDLE, "TASKS" );
                TRANSFERS = getString(STRING_BUNDLE, "TRANSFERS" );
                TBFIND = getString(STRING_BUNDLE, "TBFIND" );
                FORUM = getString(STRING_BUNDLE, "FORUM" );
                
                
                ADD_TF = getString(STRING_BUNDLE, "ADD_TF");
                ADD_TF_TOOLTIP = getString(STRING_BUNDLE, "ADD_TF_TOOLTIP");
                DEL_TF = getString(STRING_BUNDLE, "DEL_TF");
                DEL_TF_TOOLTIP = getString(STRING_BUNDLE, "DEL_TF_TOOLTIP");
				
                // Bookmark manager
                String username = System.getProperty("user.name");
                
                if( username != null )
                {
                    String firstLetter = username.substring(0, 1);
                    firstLetter = firstLetter.toUpperCase(Locale.getDefault());
                    BOOKMARKMANAGERNAME = new String( firstLetter + ( username.substring( 1, username.length() ) ) + getString(STRING_BUNDLE, "defaultBMEnding" ) );
                }else{
                    BOOKMARKMANAGERNAME =  getString(STRING_BUNDLE, "defaultBMName" );
                }
    
                SET_PERMISSIONS = getString(STRING_BUNDLE, "SET_PERMISSIONS" );
                
                if( onMacOSX )
                {
                    GETINFONAME = getString(STRING_BUNDLE, "defaultGIName");
                }else{
                    GETINFONAME = getString(STRING_BUNDLE, "defaultGIName_win");
                }
                
                if( onMacOSX )
                {
                    GETINFOTITLE = getString(STRING_BUNDLE, "GETINFOTITLE");
                }else{
                    GETINFOTITLE = getString(STRING_BUNDLE, "GETINFOTITLE_win");
                }
                
                
                /** Preferences window */
                PREFERENCESTITLE = getString(STRING_BUNDLE, "PREFERENCESTITLE"); 
                
                /**
                *
                * Close box warning
                *
                */
                SURE_YOU_WANT_TO_CLOSE_TITLE = getString(STRING_BUNDLE, "SURE_YOU_WANT_TO_CLOSE_TITLE"); 
                SURE_YOU_WANT_TO_CLOSE_MSG = getString(STRING_BUNDLE, "SURE_YOU_WANT_TO_CLOSE_MSG"); 
                
                TRANSFERFILTERS = getString(STRING_BUNDLE, "TB_TRANSFERFILTERS");
				
                // File Exists Panel
                FILEEXISTSBLURB1 = getString(STRING_BUNDLE, "FILEEXISTSBLURB1");
                FILEEXISTSBLURB2 = getString(STRING_BUNDLE, "FILEEXISTSBLURB2");
                FILEACTIONBLURB = getString(STRING_BUNDLE, "FILEACTIONBLURB"); 
                DOWNLOADED = getString(STRING_BUNDLE, "DOWNLOADED"); 
                UPLOADED = getString(STRING_BUNDLE, "UPLOADED"); 
                MOVED = getString(STRING_BUNDLE, "MOVED"); 
                DOWNLOAD_FE = getString(STRING_BUNDLE, "DOWNLOAD_FE"); 
                UPLOAD_FE = getString(STRING_BUNDLE, "UPLOAD_FE"); 
                MOVE = getString(STRING_BUNDLE, "MOVE"); 
				
				// Load normal Toolbar icons
                SIDEBAR_ICON = getIcon("sidebar.png");
                GETINFO_ICON = getIcon("info.png");
                CANCEL_ICON = getIcon("cancel.png");
                ACTIVITY_SVG_ICON = new FlatSVGIcon("I8LN/images/svg/activity.svg", 32, 32);
                CANCEL_SVG_ICON   = new FlatSVGIcon("I8LN/images/svg/cancel.svg", 32, 32);
                MIRROR_SVG_ICON   = new FlatSVGIcon("I8LN/images/svg/mirror.svg", 32, 32);
                ADD_SVG_ICON      = new FlatSVGIcon("I8LN/images/svg/add.svg", 24, 24);
                DELETE_SVG_ICON   = new FlatSVGIcon("I8LN/images/svg/delete.svg", 24, 24);
                // The raw SVGs are a faint light grey, so they barely show on the toolbar and
                // their auto-disabled (greyed) state is indistinguishable from enabled. Tint to
                // the theme foreground (resolved per-paint, so it tracks light/dark themes) for
                // real contrast and a clear enabled-vs-disabled difference.
                FlatSVGIcon.ColorFilter svgTint = new FlatSVGIcon.ColorFilter( c -> {
                    Color fg = UIManager.getColor("Button.foreground");
                    return fg != null ? fg : c;
                });
                ACTIVITY_SVG_ICON.setColorFilter(svgTint);
                CANCEL_SVG_ICON.setColorFilter(svgTint);
                MIRROR_SVG_ICON.setColorFilter(svgTint);
                ADD_SVG_ICON.setColorFilter(svgTint);
                DELETE_SVG_ICON.setColorFilter(svgTint);
                DELETE_ICON = getIcon("delete.png");
                DISCONNECT_ICON = getIcon("disconnect.png");
                REFRESH_ICON = getIcon("refresh.png");
                DOWNLOAD_ICON = getIcon("download.png");
                UPLOAD_ICON = getIcon("upload.png");
                
                // favorite folder icon

   
                RELAYICON = getIcon("relay.png");
                RELAY_SMALL_ICON = getIcon("gsftp_small.png");
                APP_ICON = new ImageIcon(GLocalized.class.getResource("/I8LN/relaysftp.png"));
                
                // big icons
                FOLDER_BIG = getIcon("folder_64.png");
                FILE_BIG = getIcon("file-big.png");
                FILE_TXT_BIG = getIcon("file-txt-big.png");
                FILE_IMG_BIG = getIcon("file-image-big.png");
				
                LINK_BIG = compositeIcon(FOLDER_BIG, FILE_BIG);
                APP_BIG = getIcon("application-big.png");
                REM_MED_BIG = getIcon("removablemedia-big.png");
                HD_BIG = getIcon("hardrive-big.png");
                FLOPPY_BIG = getIcon("floppy-big.png");
                UNKNOWN_BIG = getIcon("unknown-big.png");
                
                SMALL_MYCOMPUTER_ICON = getIcon("mycomputer_small.png");
                
                // File Item Icons
                SMALL_FILE_ICON = getIcon("file_small.png");
            
                SMALL_FOLDER_ICON = getIcon("folder_small.png");
                
                SMALL_LINK_ICON = compositeIcon(SMALL_FOLDER_ICON, SMALL_FILE_ICON);
                
                SMALL_APP_ICON = getIcon("app_small.png");
                
                SMALL_HD_ICON = getIcon("hardrive-small.png");
                SMALL_RM_ICON = getIcon("removablemedia-small.png");
                SMALL_FLOPPY_ICON = getIcon("floppy-small.png");
                
                DESKTOP_BIG_ICON = getIcon("desktop-big.png");
                DESKTOP_SMALL_ICON = getIcon("desktop-small.png");

                // No-access composite icons
                SMALL_FOLDER_NOACCESS_ICON = compositeIcon(SMALL_FOLDER_ICON, createProhibitionBadge(SMALL_FOLDER_ICON));
                SMALL_FILE_NOACCESS_ICON = compositeIcon(SMALL_FILE_ICON, createProhibitionBadge(SMALL_FILE_ICON));

                CDUP_ICON = getIcon("uptoparent.png");
								
                SECURE_ICON     = getIcon("secure.png");
                INSECURE_ICON   = getIcon("insecure.png");
                
                PREVIEW_ICON    = getIcon("preview.png");
                EDIT_ICON       = getIcon("edit.png");
                
                /** Previewer Toolbar */
                SAVE_ICON = getIcon("save.png");
                FIND_ICON = getIcon("find.png");
				
                // sort icons
                

                // Browser toolbar icons

                // Activity panel icons
                // Theme-aware (tinted to Button.foreground) so they stay visible in dark mode
                ACTIVITY_CANCEL_ICON = new FlatSVGIcon("I8LN/images/svg/activity_cancel.svg", 16, 16);
                ACTIVITY_LOG_ICON = new FlatSVGIcon("I8LN/images/svg/activity_log.svg", 16, 16);
                ACTIVITY_FIND_ICON = new FlatSVGIcon("I8LN/images/svg/activity_find.svg", 16, 16);
                ACTIVITY_CANCEL_ICON.setColorFilter(svgTint);
                ACTIVITY_LOG_ICON.setColorFilter(svgTint);
                ACTIVITY_FIND_ICON.setColorFilter(svgTint);

                // for mac os x load our "selected images" so the toolbar looks like a X toolbar
                if( onMacOSX )
                {
                    /** Browser Toolbar */
                    
                    /** Bookmark Manager Toolbar */
                    NEWBM_ICON_SELECTED = getIcon("bookmark-add_selected.png");
                    DELBM_ICON_SELECTED = getIcon("bookmark-delete_selected.png");
                    
                    /** Browser Toolbar */
                    NEWBM_ICON_DISABLED = getIcon("bookmark-add_disabled.png");
                    DELBM_ICON_DISABLED = getIcon("bookmark-delete_disabled.png");
						
                }else{
                                        
					
                    NEWBM_ICON_SELECTED     = null;
                    DELBM_ICON_SELECTED     = null;
                    
                    /** Disabled icons */
                    NEWBM_ICON_DISABLED     = null;
                    DELBM_ICON_DISABLED     = null;
                    
                }
                    
                MENUCHECK_ICON = getIcon("menuitemcheck.png");
                
                // task/connection area removal
                
                // Task Manager Toolbar icons
                
                // Bookmark Manager Toolbar icons
                NEWBM_ICON = getIcon("bookmark-add.png");
                DELBM_ICON = getIcon("bookmark-delete.png");

                /** Status Area */
                TASK_QUEUED_ICON = getIcon("task_queued_icon.png"); 
                TASK_PREPARING_ICON = getIcon("task_running_icon.png"); 
                TASK_RUNNING_ICON = getIcon("task_running_icon.png"); 
                TASK_ERROR_ICON = getIcon("task_error_icon.png"); 
                
                /**Rende*/
                
                // Toolbar Strings
                TB_SIDEBAR = getString(STRING_BUNDLE, "tbSidebar");  
                TB_NEWFOLDER = getString(STRING_BUNDLE, "tbNewFolder");
                
                if( onMacOSX )
                {
                    TB_GETINFO = getString(STRING_BUNDLE, "tbGetinfo");
                }else{
                    TB_GETINFO = getString(STRING_BUNDLE, "tbGetinfo_win");
                }

                TB_DELETE = getString(STRING_BUNDLE, "tbDelete");
                TB_REFRESH = getString(STRING_BUNDLE, "tbRefresh"); 
                TB_CANCEL = getString(STRING_BUNDLE, "tbCancel"); 
                TB_DISCONNECT = getString(STRING_BUNDLE, "tbDisconnect");
                TB_ACTIVITY = getString(STRING_BUNDLE, "tbActivity");
                TB_PREVIEW = getString(STRING_BUNDLE, "tbPreview"); 
                TB_EDIT = getString(STRING_BUNDLE, "tbEdit"); 
                TB_UPLOAD = getString(STRING_BUNDLE, "tbUpload"); 
                TB_DOWNLOAD = getString(STRING_BUNDLE, "tbDownload");
                
                // Task Manager Toolbar Strings
                TB_REMOVE = getString(STRING_BUNDLE, "tmtbRemove");
                
                // Bookmark Manager Toolbar Strings
                TB_NEWBOOKMARK = getString(STRING_BUNDLE, "bmtbNewBookmark");
                TB_DELETEBOOKMARK = getString(STRING_BUNDLE, "bmtbDeleteBookmark");
                
                // Editor Toolbar Strings
                TB_SAVE = getString(STRING_BUNDLE, "edtbSave");

                /** General Preferences */
                PG_DEFAULTLOCALHOME = getString(STRING_BUNDLE, "PG_DEFAULTLOCALHOME");
                PG_KEEPALIVE = getString(STRING_BUNDLE, "PG_KEEPALIVE"); 
                PG_SFTPDEFAULT = getString(STRING_BUNDLE, "PG_SFTPDEFAULT");
                
                FAVORITEFOLDERTABTITLE = getString(STRING_BUNDLE, "FAVORITEFOLDERTABTITLE");
                GENERALTABTITLE = getString(STRING_BUNDLE, "giGeneralTabTitle");
                PERMISSIONTABTITLE = getString(STRING_BUNDLE, "giPermissionTabTitle"); 
            
                DEFAULTCONNECTIONNAME = getString(STRING_BUNDLE, "DEFAULTCONNECTIONNAME"); 
                FTP = getString(STRING_BUNDLE, "FTP");
                SFTP = getString(STRING_BUNDLE, "SFTP");
                FTPS = "FTPS";
                S3 = "S3";
                    
                VERSION = getString(STRING_BUNDLE, "appVersion");
				
				/**
				*
				*
				*
				*/
				SHOWSIDEBAR = getString(STRING_BUNDLE, "SHOWSIDEBAR");
				HIDESIDEBAR = getString(STRING_BUNDLE, "HIDESIDEBAR");
   
                /**
                * Job Type Strings
                *
                */
                UNKNOWN_JOB_TYPE = getString(STRING_BUNDLE, "UNKNOWN_JOB_TYPE");
                DOWNLOAD_JOB_TYPE = getString(STRING_BUNDLE, "DOWNLOAD_JOB_TYPE");
                UPLOAD_JOB_TYPE = getString(STRING_BUNDLE, "UPLOAD_JOB_TYPE");
                DELETE_JOB_TYPE = getString(STRING_BUNDLE, "DELETE_JOB_TYPE");
                CHMOD_JOB_TYPE = getString(STRING_BUNDLE, "CHMOD_JOB_TYPE");
                MOVE_JOB_TYPE = getString(STRING_BUNDLE, "MOVE_JOB_TYPE");
                LIST_JOB_TYPE = getString(STRING_BUNDLE, "LIST_JOB_TYPE");
                RENAME_JOB_TYPE = getString(STRING_BUNDLE, "RENAME_JOB_TYPE");
                LOGIN_JOB_TYPE = getString(STRING_BUNDLE, "LOGIN_JOB_TYPE");
                NEWFOLDER_JOB_TYPE = getString(STRING_BUNDLE, "NEWFOLDER_JOB_TYPE");
                REFRESH_JOB_TYPE = getString(STRING_BUNDLE, "REFRESH_JOB_TYPE");
                DISCONNECT_JOB_TYPE = getString(STRING_BUNDLE, "DISCONNECT_JOB_TYPE"); 
                PING_JOB_TYPE = getString(STRING_BUNDLE, "PING_JOB_TYPE");
                MTIME_JOB_TYPE = getString(STRING_BUNDLE, "MTIME_JOB_TYPE");
                
                NAME_COLUMN_TITLE = getString(STRING_BUNDLE, "NAME_COLUMN_TITLE");
                SIZE_COLUMN_TITLE = getString(STRING_BUNDLE, "SIZE_COLUMN_TITLE");
                DATE_COLUMN_TITLE = getString(STRING_BUNDLE, "DATE_COLUMN_TITLE");
                
                ITEMS_STRING = getString(STRING_BUNDLE, "ITEMS_STRING");
                PREVIEWING = getString(STRING_BUNDLE, "PREVIEWING");
	
                FOLDER = getString(STRING_BUNDLE, "FOLDER");
                FILE = getString(STRING_BUNDLE, "FILE");
                LINK = getString(STRING_BUNDLE, "LINK");
                HARD_DRIVE = getString(STRING_BUNDLE, "HARD_DRIVE");
                REM_MEDIA = getString(STRING_BUNDLE, "REM_MEDIA");
                
                if( onMacOSX )
                {
                    APP = getString(STRING_BUNDLE, "APP");
                }else{
                    APP = getString(STRING_BUNDLE, "APP_win");
                }
                
                NAME_FOLDER_STRING = getString(STRING_BUNDLE, "NAME_FOLDER_STRING");
                NAME_FILE_STRING = getString(STRING_BUNDLE, "NAME_FILE_STRING");
                NO_SELECTION = getString(STRING_BUNDLE, "NO_SELECTION");  
                DATA_STORE_INACCESSIBLE = getString(STRING_BUNDLE, "DATA_STORE_INACCESSIBLE");
                SURE_YOU_WANT_TO_DELETE_TITLE = getString(STRING_BUNDLE,"SURE_YOU_WANT_TO_DELETE_TITLE");
                ERROR_GETTINGCONNECTIONSTATUS = getString(STRING_BUNDLE,"ERROR_GETTINGCONNECTIONSTATUS");
                FTP_ENTERPASSANDUN = getString(STRING_BUNDLE,"FTP_ENTERPASSANDUN");
                SFTP_ENTERPASSANDUN = getString(STRING_BUNDLE,"SFTP_ENTERPASSANDUN");
                ENTERHOSTNAME = getString(STRING_BUNDLE,"ENTERHOSTNAME");
                TRYING_PW_AUTH = getString(STRING_BUNDLE,"TRYING_PW_AUTH");
                PK_AUTH = getString(STRING_BUNDLE,"PK_AUTH");
                USING_SSH_KEY = getString(STRING_BUNDLE,"USING_SSH_KEY");
                USING_PORT = getString(STRING_BUNDLE,"USING_PORT");
                
                if( onMacOSX )
                {
                    LOCALFILESLABEL = getString(STRING_BUNDLE,"LOCALFILESLABEL");
                }else{
                    LOCALFILESLABEL = getString(STRING_BUNDLE,"LOCALFILESLABEL_win");
                }
                REMOTEFILESLABEL = getString(STRING_BUNDLE,"REMOTEFILESLABEL");
				
                // Data file names
                ASCII_TYPES_FILE = "AsciiTypes.txt";
                BINARY_TYPES_FILE = "ImageTypes.txt";
                PACKAGE_TYPES_FILE = "PackageTypes.txt";
				
                //
                //
                // Create localized data folder path    
                //
                //            
                // Development override: set -Drelaysftp.dataDir=... (or env RELAYSFTP_DATA_DIR)
                // to point dev/Xcode runs at a throwaway folder instead of the real
                // ~/Library/Application Support/RelaySFTP. Default behavior is unchanged.
                String dataDirOverride = System.getProperty("relaysftp.dataDir");
                if( dataDirOverride == null || dataDirOverride.isEmpty() )
                {
                    dataDirOverride = System.getenv("RELAYSFTP_DATA_DIR");
                }

                if( dataDirOverride != null && !dataDirOverride.isEmpty() )
                {
                    String sep = System.getProperty("file.separator");
                    DATA_FOLDER_PATH = dataDirOverride.endsWith(sep) ? dataDirOverride : dataDirOverride + sep;
                }else{

                    StringBuilder dataFolderPath = new StringBuilder(System.getProperty("user.home"));

                    if( onMacOSX )
                    {
                        dataFolderPath.append("/Library/Application Support/RelaySFTP");
                    }else{
                        // home+sep+Gideon
                        dataFolderPath.append(System.getProperty("file.separator"));

                        if(onWindows)
                        {
                            dataFolderPath.append("RelaySFTP");
                        }else{
                            dataFolderPath.append(".relaysftp");
                        }

                    }

                    dataFolderPath.append(System.getProperty("file.separator"));

                    DATA_FOLDER_PATH = dataFolderPath.toString();
                }
                    
				String tmpDir = System.getProperty("java.io.tmpdir");
				if( !tmpDir.endsWith(System.getProperty("file.separator")) )
				{
					tmpDir = tmpDir + System.getProperty("file.separator");
				}
				TEMP_FILE_PATH = tmpDir;
					
                //TEMP_FILE_PATH = dataFolderPath.append("Temporary/").toString();

                if( onMacOSX || 
                    onWindows )
                {
                   HOME_FOLDER = new String(System.getProperty("user.home") + System.getProperty("file.separator") + "Downloads" + System.getProperty("file.separator") );
                }else{
                   HOME_FOLDER = new String(System.getProperty("user.home") + System.getProperty("file.separator") );
                }
                
                // home folder
                                                                                          
        }   

	/** 
	 * Blocks creation of an GLocalizedStrings object.
	 */
	private GLocalized() {
	}

	/** 
	 * Get a string from the resources.
	 *
	 * @param bundle The resource bundle to fetch from.
	 * @param key The key of the string to get.
	 *
	 * @return The value for that string; if not found returns !key!.
	 */
	private static String getString(final ResourceBundle bundle, final String key) {
		try {
			return bundle.getString(key);
		} catch (final MissingResourceException ex) {
			return '!' + key + '!';
		}
	}
        
        /**
        *
        *   @param iconName name of icon to grab
        *   @returns ImageIcon for iconName
        *
        *   This getIcon method first checks for a platform specific icon in the 
        *   respective icon folders. If a platform specific icon is not found, the generic 
        *   image set is consulted.
        *
        */
         
	private static ImageIcon getIcon(final String iconName) {
                URL     imgURL      = null;
                String  imagePath   = null;

                try {
                                    
                    // generate our platform specific image path
                    imagePath = PLATFORM_IMAGE_PATH + iconName; 

                    // check platform specific folder for icon
                    imgURL = GLocalized.class.getResource(imagePath);
                    
                    // if we didn't find a platform specific image
                    // check the generic folder
                    if( imgURL == null )
                    {
                        imagePath = GENERIC_PLATFORM_IMAGE_PATH + iconName;

                        imgURL = GLocalized.class.getResource(imagePath);
                        
                        // if we didn't find anything - load the question mark icon
                        // and return the question mark icon 
                        if( imgURL == null )
                        {
                            if( GideonConfiguration.ICON_DEBUG )
                                System.out.println("GSFTP NOTICE - Could not find generic image : \"" + iconName + "\" ; platform folder is : \"" + PLATFORM_IMAGE_PATH + "\"");
                            imgURL = GLocalized.class.getResource(GENERIC_PLATFORM_IMAGE_PATH + "missingicon.png");
                        }
                        
                    }

                    if (imgURL != null) {
                        ImageIcon   ic = new ImageIcon(imgURL, iconName);
                        return ic;
                    }
                    
		} catch (final MissingResourceException ex) {
			return null;
		}
                
                return null;
	}

	private static ImageIcon getScaledIcon(final String iconName, int width, int height) {
		ImageIcon raw = getIcon(iconName);
		if (raw == null) return null;
		java.awt.Image scaled = raw.getImage().getScaledInstance(width, height, java.awt.Image.SCALE_SMOOTH);
		return new ImageIcon(scaled, iconName);
	}

	/**
	 * Create a red prohibition (no-entry) badge sized to overlay on the given base icon.
	 * The badge is drawn at half the base icon size.
	 */
	private static ImageIcon createProhibitionBadge(ImageIcon base) {
		int size = base.getIconWidth() / 2;
		if (size < 8) size = 8;
		java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = bi.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

		int pad = 1;
		int d = size - pad * 2;
		float strokeWidth = Math.max(2f, d / 5f);

		// Red circle
		g.setColor(new java.awt.Color(220, 40, 40));
		g.setStroke(new java.awt.BasicStroke(strokeWidth, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
		g.drawOval(pad, pad, d, d);

		// Diagonal slash
		int inset = (int)(strokeWidth * 0.8);
		g.drawLine(pad + inset, pad + d - inset, pad + d - inset, pad + inset);

		g.dispose();
		return new ImageIcon(bi);
	}

	/**
	 * Generate a link/shortcut icon by compositing an overlay in the lower-right corner of a base icon.
	 */
	public static ImageIcon compositeIcon(ImageIcon base, ImageIcon overlay) {
		if (base == null) return overlay;
		if (overlay == null) return base;
		int w = base.getIconWidth();
		int h = base.getIconHeight();
		java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = bi.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(base.getImage(), 0, 0, null);
		// draw overlay at half size in the lower-right corner
		int ow = w / 2;
		int oh = h / 2;
		g.drawImage(overlay.getImage(), w - ow, h - oh, ow, oh, null);
		g.dispose();
		return new ImageIcon(bi);
	}

	private static final java.util.HashMap<ImageIcon, ImageIcon> darkenedCache = new java.util.HashMap<>();
	private static final java.util.HashMap<ImageIcon, ImageIcon> fadedCache = new java.util.HashMap<>();

	/**
	 * Generate a darkened (pressed) variant of an icon. Cached.
	 */
	public static ImageIcon darkenedIcon(ImageIcon icon) {
		if (icon == null) return null;
		return darkenedCache.computeIfAbsent(icon, k -> {
			java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(
				k.getIconWidth(), k.getIconHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D g = bi.createGraphics();
			g.drawImage(k.getImage(), 0, 0, null);
			g.setComposite(java.awt.AlphaComposite.SrcAtop);
			g.setColor(new java.awt.Color(0, 0, 0, 80));
			g.fillRect(0, 0, bi.getWidth(), bi.getHeight());
			g.dispose();
			return new ImageIcon(bi);
		});
	}

	/**
	 * Generate a faded (disabled) variant of an icon. Cached.
	 */
	public static ImageIcon fadedIcon(ImageIcon icon) {
		if (icon == null) return null;
		return fadedCache.computeIfAbsent(icon, k -> {
			java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(
				k.getIconWidth(), k.getIconHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D g = bi.createGraphics();
			g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.35f));
			g.drawImage(k.getImage(), 0, 0, null);
			g.dispose();
			return new ImageIcon(bi);
		});
	}

}