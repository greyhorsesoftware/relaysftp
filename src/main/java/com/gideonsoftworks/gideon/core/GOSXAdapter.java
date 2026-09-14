/*

File:		GOSXAdapter.java

Abstract:   Hooks About, Preferences, Quit, and OpenFile functionality
			into the platform application menu using java.awt.Desktop (Java 9+).
*/

package com.gideonsoftworks.gideon.core;

import com.gideonsoftworks.gcore.*;
import java.awt.Desktop;
import java.io.File;
import java.util.List;

public class GOSXAdapter {

	// The main entry-point for this functionality. Registers handlers
	// for About, Preferences, Quit, and OpenFile using java.awt.Desktop.
	public static void registerMacOSXApplication(Object inApp, GFrameManager fm) {
		Desktop desktop = Desktop.getDesktop();

		desktop.setAboutHandler(e -> GCommonEvents.doAbout());

		desktop.setPreferencesHandler(e -> GCommonEvents.doPreferences());

		desktop.setQuitHandler((e, response) -> {
			if (GCommonEvents.doQuit()) {
				response.performQuit();
			} else {
				response.cancelQuit();
			}
		});

		desktop.setOpenFileHandler(e -> {
			List<File> files = e.getFiles();
			if (files != null) {
				for (File f : files) {
					System.out.println("handleOpenFile: " + f);
					GCommonEvents.doOpenFile(f);
				}
			}
		});
	}

	// Enables the "Preferences..." menu item in the application menu.
	// With java.awt.Desktop, preferences are enabled by setting the handler above.
	// This method is kept for API compatibility but is now a no-op.
	public static void enablePrefs(boolean enabled) {
		// No-op: preferences handler is set in registerMacOSXApplication
	}
}
