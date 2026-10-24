# Release 1.8.2 Plan

Version bump: `gradle.properties` `version=1.8.1` → `1.8.2`.

## 1. Press `.` to toggle invisible files

**Goal:** With a browser view (file table) focused, pressing `.` shows dotfiles. Pressing it again hides them.

**What exists today**
- The preference is already there: `GSFTPPreferences.showDotFiles()` / `setShowDotFiles()` (`GPREF_SHOW_DOT_FILES`), and it is exposed as the "Show files starting with "."" checkbox in `GGeneralPreferencePanelController`.
- Filtering happens when a listing is parsed, in `GListParser` (~line 107). It is not a view filter, so a change only takes effect after the listing is fetched again.
- `GBrowserPanelController.refresh(Object)` queues a `REFRESH_JOB_TYPE` job for the current directory.
- The file table is a `GTable`, which has no type-ahead or key bindings yet, and its model returns `isCellEditable == false`. So `.` is free and won't start a cell edit.
- Unused leftovers: `GPREF_SHOW_HIDDEN`, `PG_SHOWHIDDEN` and `GFileItem.STATE_HIDDEN`. Leave them alone.

**Decision:** the toggle is global (one setting for all views and windows, like Finder's ⌘⇧.). The setting moves out of Preferences and into the Special menu.

**Approach**
1. **New action.** Add `GUIAction.GA_TOGGLE_DOTFILES`. In `actionPerformed` it flips `GSFTPPreferences.setShowDotFiles(!showDotFiles())`, then calls `refresh(null)` on every open browser panel in every window, through the `GFrameManager` window list. In the enabled-state check it is always enabled, like `GA_TRANSFER_AUTO`, but disabled for `GEditorWindow`.
2. **Special menu.** In `GCommonPartsFactory.specialMenu()` (~line 307), add a `JCheckBoxMenuItem` "Show Hidden Files" after Refresh. Add a new `GLocalized` constant and string; the old unused `PG_SHOWHIDDEN=Show hidden files` key could be reused. Each window builds its own menu bar, so the check state has to be set from `GSFTPPreferences.showDotFiles()` when the menu opens, using a `MenuListener.menuSelected` on the Special menu. Otherwise other windows show a stale check after a toggle.
3. **Two shortcuts, same action:**
   - **⌘⇧. on macOS, Ctrl+Shift+. on Windows/Linux.** This matches Finder. Set it as the menu item's accelerator: `KeyStroke.getKeyStroke(KeyEvent.VK_PERIOD, shortcutKeyMask | SHIFT_DOWN_MASK)`, following the existing ⌘⇧L pattern in `GFrameManager`. It works from anywhere in a browser window, and the menu shows the shortcut.
   - **Bare `.` when a file table has focus.** In `GBrowserPanelController`, where `fivFileTable` is built (~line 299), bind the typed character `.` (`KeyStroke.getKeyStroke('.')`, which is layout-independent) in the table's `WHEN_FOCUSED` input map, pointing at the same action. Don't make bare `.` a menu accelerator, because it would also fire while typing in the find bar, rename fields and connection dialogs.
4. **Remove from Preferences.** Delete the `showDotFiles` checkbox and its open/save lines from `GGeneralPreferencePanelController`, and shrink its preferred height if the panel looks empty. Keep the `GPREF_SHOW_DOT_FILES` key, so a user's existing setting carries over.
5. Keep the current selection where possible. A refresh already re-selects the captured files (see ~line 494).

**Noticed, not in scope:** Cancel and Disconnect in the Special menu both use ⌘. (`VK_PERIOD` + shortcut). That's a pre-existing conflict worth a look in a later release.

**Verify**
- Unit test: the action flips the preference and asks each panel to refresh.
- Manual: SFTP, FTP, S3 and local panes each show and hide `.ssh`, `.bashrc` and similar files, from the Special menu, with ⌘⇧. / Ctrl+Shift+. from anywhere in the window, and with bare `.` in a file table. All three stay in sync.
- ⌘⇧. does not trigger Cancel/Disconnect (⌘.), and ⌘. does not toggle hidden files.
- With two browser windows open, toggling in one refreshes both, and both Special menus show the correct check.
- Typing `.` in the find bar, a rename field or a connection dialog does not toggle.
- The Preferences > General panel no longer has the checkbox. A setting saved before the upgrade is still honored.

## 2. Editor loading screen is whited out

**Problem:** Opening a remote file in the editor shows `GEditorWindow.showOverlay()` (~line 416). It paints a hardcoded `new Color(255, 255, 255, 180)` over the whole window. In the light themes it washes everything out, and in the dark themes (`FlatDarkLaf`, `FlatMacDarkLaf`) it is a glaring white sheet with the label floating on it.

**Fix**
1. Replace the hardcoded white with a theme color: `UIManager.getColor("Panel.background")` at reduced alpha (around 120 to 150), so it dims the window instead of bleaching it.
2. Put the label and progress bar on a small opaque, rounded "card" (also `Panel.background`, with a `Component.borderColor` border) so the message stays readable on any theme.
3. Only change `showOverlay()`. It is also used for "Uploading...", which gets the same fix.

**Verify**
- Manual: open a remote file under Light, Dark, macOS Light and macOS Dark. The overlay dims the window and the card is readable. Save, so the upload overlay shows, and check it the same way.

## 3. Switching protocol after a failed bookmark connects with the old protocol

**Problem (bug report):** a bookmark (SFTP, `localhost`) failed to connect. On the login panel the user switched the protocol to FTP, typed `ftp.gnu.org` and clicked Connect. The app tried to SSH to ftp.gnu.org with no password and failed with an NPE inside sshj. The log still labelled the connection "localhost (SFTP)".

**Cause**
- Opening a bookmark hands the pane a `GConnection` (`connectionToConnection()` → `setConnection(c)`), and the pane keeps it after the login fails.
- The Connect button (`GBPLoginPanelActionListener`) wrote the form into that same bookmark object (`currentBookmark`). `connectForBookmark()` then reused the existing connection, because it only builds a new one when the pane has none.
- A `GConnection` picks its protocol plugin once, in its constructor, from the bookmark's protocol. Changing the bookmark's protocol afterwards has no effect, so the SFTP plugin was used against an FTP host.
- Side effect: the form edits also changed the saved bookmark in memory. The next `GBookmarkManager.saveBookmarks()` would have written `ftp.gnu.org` over the "test" bookmark on disk.

**Fix** (`GBrowserPanelController.GBPLoginPanelActionListener`; the protocol layer is untouched)
1. When the pane already has a bookmark, apply the form to a copy (`new GBookmark(bm)`, plus local home path, never-save-password and max-concurrent-transfers, which the copy constructor doesn't carry).
2. If the copy still points at the same server (same protocol, host ignoring case, port and username, checked by `isSameServer()`), this is a retry: apply the form to the real bookmark and keep the existing connection, as before. Favorites and other edits still land on the saved bookmark.
3. Otherwise the user has typed a different server. Drop the pane's connection (`setConnection(null)`) so `connectForBookmark()` builds a new `GConnection` from the copy, with the right protocol plugin. The saved bookmark is left unchanged.

**Verify**
- Unit test `GBrowserPanelSameServerTest` covers `isSameServer()`: a protocol, host, port or user change counts as a different server; host case and password don't.
- Manual: open a bookmark that fails (e.g. a dead SFTP host), switch to FTP, enter `ftp.gnu.org`, click Connect. It connects anonymously over FTP and the log shows `ftp.gnu.org (FTP)`. The bookmark file on disk is unchanged.
- Manual: open a failing bookmark, fix only the password and click Connect. It retries the same bookmark.

## 4. Closing a tab must close its connection

**Check requested:** when a tab or window is closed, is its connection closed too?

**Window close: OK.** `GBrowserWindow.windowWillClose()` fires `bwl_browserClosing`, which stops each pane's ping timer, mtime poll and folder watcher. It then calls `reapAll()`, which queues a DISCONNECT for every tab's connection set.

**Tab close: broken.** `GBrowserWindow` declared `tabWillClose(HashMap)`, but `GFrame.closeKeyTab()` calls `tabWillClose(GTabBarItem)`. The window's version had no `@Override`, so it never ran:
- the closed tab's SFTP/FTP/S3 session stayed open until the whole window closed;
- the closed panes' ping timer and mtime poll kept running against it, and kept re-logging it in;
- the panes stayed registered as window listeners.

**Fix** (`GBrowserWindow.tabWillClose`)
1. Correct the signature to `tabWillClose(GTabBarItem)` with `@Override`. Read the connection set from the tab attribute.
2. Custom tabs (e.g. Jobs) have no connection set: return instead of throwing an NPE.
3. For each pane in the tab, stop its pollers and folder watcher (`bwl_browserClosing`) and unregister the window from the pane and the pane from the window.
4. Then remove and reap the connection set, as the old code meant to.

**Verify**
- `CloseTabDisconnectsUITest`: log in, open a second tab, close the connected tab. The connection disconnects within 15 s, leaves `GConnection.openConnections()`, the window stops listening to the pane, and the window stays open. With the fix disabled this test fails (timed out waiting for the disconnect).
- `CloseWindowDisconnectsUITest`: closing the window disconnects its connection.

## 5. Cancelling a window close must keep its connections

**Problem:** `GFrame.processWindowEvent` called `windowWillClose()` before the "jobs are running, are you sure?" prompt. For a browser window, `windowWillClose()` stops the panes' pollers, reaps every connection and nulls the connection-set manager. Answering No left the window open with its connections disconnected and its keep-alives stopped.

**Fix** (`GFrame.processWindowEvent`)
1. Ask first: if the window's job manager has running jobs, show the prompt. Anything but Yes (No, Esc, the dialog's close box) cancels the close with nothing torn down. Before, dismissing the dialog without clicking No went ahead and closed the window.
2. Then call `windowWillClose()`. It can still veto, and teardown happens only once the close is certain.
3. Then shut down the job manager and remove the window, as before.

All `windowWillClose()` overrides (browser, preferences, bookmark manager, preview) are cleanup-only and always return true, so running them after the prompt changes nothing else. Every close path (close box, Close Window menu via `GA_CLOSE_BROWSER`) goes through `processWindowEvent`.

**Verify**
- `CloseWindowCancelKeepsConnectionUITest`: the test SFTP server holds the read of `slow.bin`, so its download stays running. The test closes the window and answers No. The window stays open and the same connection is still connected. With the old order this test fails ("answering No must not disconnect the window's connection").
- `CloseWindowDisconnectsUITest` (no running jobs) still closes and disconnects.

## 6. Breadcrumb path bar and `r` to refresh

**Goal:** replace the row above each file list (Up, Reload and Favorites buttons plus the path drop-down) with a breadcrumb bar. The bar spans the full width above the list. When the path is too long, it starts scrolled to the end (current folder visible, leading crumbs off the left edge), and you can scroll it left and right.

**Design**
- New `GBreadcrumbBar` (in `gideon/browser`):
  - The first crumb is a home icon standing for the top of the path; its tooltip names it.
  - Then a `›` chevron and a rounded pill per folder. The current folder is bold.
  - Clicking a crumb goes to that folder, as picking it from the old drop-down did.
  - Pills are FlatLaf buttons (`arc: 999`, no border or focus ring) and the home icon is drawn in the theme's text colour, so it works in light and dark themes.
- **Look (revised after the first review):**
  - The root crumb stands for the top of the filesystem rather than home: a drawn drive outline in local panes, and a server (two stacked rack units) in remote panes (`getConnection().getIsRemote()`).
  - Separators are a thin drawn chevron in `Label.disabledForeground` instead of the `›` font glyph.
  - The bar has an 8px top border, matching the 2px border plus 6px layout gap below it, so the pills sit centred between the window toolbar and the list.
- **Favorites:** the Favorites button and its popup are removed. (Decision: the user first chose a star pinned at the right of the bar, then asked for it to be dropped.) Jumping to a saved favorite is no longer available in the browser. Favorites can still be added (File menu) and managed (Preferences).
- **Overflow and scrolling:** the crumb row sits in a viewport. Once the row is wider than the bar, it is right-aligned so the current folder shows and the leading crumbs are off the left. The mouse wheel and trackpad swipes (vertical, or horizontal/shift-wheel) scroll it left and right, 30 px per notch, clamped at both ends. A new path resets it to the end.
- **Up:** going up a folder is still ⌘⌫ (Special menu), or click the parent crumb.
- **Refresh:** ⌘R already refreshes from the Special menu. Bare `r` now refreshes the focused pane while its file list has focus. (Decision: no ⌘⇧R variant, since ⌘R already covers it.)

**Code**
- In `GBrowserPanelController`, `toolRow` is replaced with the bar. `updateButtonAndDropDown()` now builds the crumb path (still skipping the "Computer" node for remote panes). The crumb click handler replaces the drop-down handler.
- Removed what this made unused:
  - the `backButton`, `reloadButton` and `fivPathSelector` fields and their `actionPerformed` branches;
  - the `GPathComboBoxRenderer` class;
  - `showFavoritesPopup()` and the instance `buildLocalFileItemWithParents(File)` (the static two-argument version is still used by drag-and-drop);
  - `GLocalized.PWD_ICON`, `RELOAD_SM_ICON`, `FAVORITES_ICON` and `NOFAVORITES`, plus its properties entry.

  The image files `pwd.png`, `reload.png` and `favorites.png` are now unreferenced but still in resources.

**Verify**
- `BreadcrumbBarUITest` checks:
  - after login the bar starts with the home icon and ends with the current folder;
  - clicking the parent crumb navigates there and the bar updates;
  - in a narrowed window the row starts flush right with crumbs off the left, and a wheel scroll back brings the first crumb to the left edge;
  - pressing `r` in the file table runs a refresh job.
- Manual: light and dark themes; trackpad swipe and mouse wheel on a deep path.

## 7. Crash when double-clicking a folder that is already listed

**Found while testing #6:** the app's audit log recorded `ArrayIndexOutOfBoundsException` in `DefaultRowSorter.convertRowIndexToModel`, from `captureSelectedNames()` (on mouse release) and `mousePressed()`. It recurred during manual testing of the breadcrumb bar.

**Cause** (in older code; breadcrumbs made it common)
- `mousePressed()` opened a double-clicked folder straight away. If the folder was already listed (cached), `changeToFolder()` swapped in its listing and fired `tableChanged` synchronously, still inside the press.
- The file table has drag enabled. With drag enabled, `BasicTableUI` defers selection to mouse release and applies the row it recorded at press. That row belonged to the old, longer listing, so a row past the end was selected. Reading the selection then overran the row sorter. The stale selection also broke the next `mousePressed()`.
- Breadcrumbs made this common: jump back with a crumb, double-click the same folder again, and it opens from cache.

**Fix** (`GBrowserPanelController`): a double-click on press only records the item (`gbpPendingOpenItem`). `mouseReleased()` opens it (parent / folder / file, same branches as before) after the table UI has finished its own release handling.

**Verify**
- `DoubleClickCachedFolderUITest`: eight files plus a "sub" folder with one file. The test double-clicks "sub", goes back via the parent crumb, and repeats three times. It asserts no uncaught exceptions on the UI thread. With the old open-on-press timing restored it fails ("Index 8 out of bounds for length 1").

## 8. Mirror tables: Operation and Status always show their whole text

**Problem:** in the mirror workspace, the columns saying what is happening were capped (Operation at 130px on Review and 140px on Running; Status at 300px) and shared the remaining width with Path. Labels like "= unchanged", "↓ copy (changed)" and long "skipped · <reason>" were cut off with "…". In the UI-test window, Operation was squeezed to 75px.

**Decision:** both columns (user's choice: Operation and Status).

**Fix** (`GMirrorPanel`)
- `fitColumn(table, col)` fixes a column to the width of its widest cell or header, measured with the column's real renderer. Applied:
  - on Review, Operation, after the rows are set;
  - on Running, Operation and Status, after the run state is set.
- `growColumn(table, col, row)` widens Status (never narrows it) when a row's status changes and no longer fits, e.g. a running progress bar or a long skip reason.
- Path is the only flexible column and takes what's left. The checkbox (34px) and Size (90 / 80px) caps are unchanged.

**Verify**
- `UITestBase.assertColumnShowsWholeText` runs inside the shared mirror helpers, so every SFTP, S3 and running-panel mirror UI test checks that no Operation cell (Review and Running) or Status cell (Running) is wider than its column. With the old fixed widths, MirrorSftpUITest and MirrorRunningPanelUITest each fail 8 tests ("needs 82px but has 75px: = unchanged").

## Release checklist
- [ ] `./gradlew clean build` passes
- [ ] `./gradlew uiTest` passes
- [ ] Version bumped to 1.8.2
