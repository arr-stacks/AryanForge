# FolderForge

A small Android utility project for extracting a selected ZIP into a staging directory and replacing a user-selected folder. It uses Android's Storage Access Framework, so the user explicitly grants access to the ZIP and destination folder.

## Safety behavior
- Validates ZIP entry paths against absolute paths, `..` traversal, and colon-containing segments.
- Extracts to a temporary sibling folder first.
- Attempts to rename an existing destination to a timestamped backup before swapping in the staged folder.
- Attempts rollback if the final rename fails.
- Shows byte-based progress and reports errors.

## Build
Open this folder in Android Studio with Android SDK 35 installed, allow Gradle sync, then Build > Build APK(s).

## Important limitations
- Storage providers differ. Some SAF providers do not support reliable directory renaming; in that case the app stops rather than deleting the old destination.
- ZIPs with unusual structures may need the folder-name field to match the root directory in the ZIP.
- For very large ZIPs, extraction can be slow when the chosen destination is backed by a cloud or slow external provider.
- This project source has not been compiled or tested on a physical device in this environment. Test with a small disposable ZIP and folder before using important data.
