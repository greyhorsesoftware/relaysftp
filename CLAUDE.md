# RelaySFTP - Project Rules

## Build & Run

- **Build**: `./gradlew clean build` (unit + integration tests, headless)
- **UI tests**: `./gradlew uiTest` (needs a desktop)
- **Run**: `./gradlew run` (throwaway data folder in `build/dev-data`); `java -jar build/libs/RelaySFTP.jar` uses the real app data folder
- **Java**: 25 (toolchain target)
- **Gradle**: 9.3.1
- **Entry point**: `com.gideonsoftworks.Gideon`

## Project Structure

```
src/main/java/com/gideonsoftworks/
  gideon/          # Main application packages
    actions/       # Menu and toolbar actions
    bookmarks/     # Bookmark management
    browser/       # Dual-pane file browser UI
    core/          # App core: GFrame, GLocalized, connection panels, preferences access
    fileexistsdialog/ # File conflict resolution dialogs
    find/          # Editor find bar and find actions
    getinfo/       # File info panels
    logviewer/     # Connection log viewer
    mirroring/     # Directory mirroring/sync
    permissions/   # Unix permission editor
    preferences/   # App preferences panels
    toolbar/       # Toolbar items
    widgets/       # Reusable UI components (GTable, GAboutBox, etc.)
  editor/          # Built-in text editor window
  gcore/           # Connections, protocol plugins (FTP/SFTP/S3/FILE), GFileItem, utilities
  gdo/             # Data objects (GDataArray, GBookmark, etc.)
  gja/             # Job manager, queues and job plugins
  tabkit/          # Tab bar item state
src/main/resources/I8LN/
  images/          # Icons and images
  GideonStrings.properties  # Localized strings
```

## Code Conventions

- Package prefix: `com.gideonsoftworks`
- UI panels extend `GGideonPanelController` (which provides `setPanel()`, `getFolderPath()`)
- Localized strings are constants in `GLocalized` (e.g., `GLocalized.LP_HOSTNAME`)
- Preferences stored via `GSFTPPreferences` static methods
- Data persistence is JSON via Gson (`GDataArray`, `GDataItemInterface`); bookmarks are one `.relay` file each
- All UI is hand-coded Swing (`GridBagLayout`, `BorderLayout`, `BoxLayout`) with FlatLaf look-and-feel

## Do Not

- Do not add new dependencies without asking
- Do not modify the protocol layer (`GProtocolPlugin` subclasses) without understanding the threading model
