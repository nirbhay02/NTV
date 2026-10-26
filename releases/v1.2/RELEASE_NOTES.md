# NTV v1.2 Release Notes

## Overview
NTV v1.2 fixes dashboard UI issues, restores the Settings button, adjusts the Search button sizing, and introduces complete 24-hour auto-refreshing Electronic Program Guide (EPG) integration with live real-time synchronization.

## What's New & Fixed
1. **Dashboard TopBar Restored**:
   - Fixed issue where the Search button grew excessively wide and pushed the Settings button off-screen.
   - Restored the **Settings** button with gear icon, properly sized and positioned next to Search.
   - Balanced top bar layout: Logo + NTV LIVE + Server pill + Jio pill on the left; Search + Settings + Live Clock on the right.

2. **24-Hour Auto-Refreshing EPG Guide**:
   - Integrated full XMLTV EPG feed (`https://mitthu786.github.io/tvepg/jiotv/epg.xml.gz` with fallbacks) supporting 1,200+ channels.
   - Automatic 24-hour background sync timer: automatically refreshes data every 24 hours without UI lag.
   - Persistent disk cache (`epg_cache.xml.gz`) for instant boot and offline/cached viewing.
   - Manual "Refresh EPG" button in Settings showing live channel count.

3. **Real-Time Dashboard EPG**:
   - Pane 2 (Channel List): Displays the current program title beneath each channel name.
   - Pane 3 (Details Preview Card): Dynamic program title, start & end time range, remaining minutes (`Xm left`), show description, and live progress bar.
   - Instant real-time updates when navigating up/down across channels using D-pad.

4. **Player Screen & Quick Guide EPG**:
   - Bottom OSD strap displays current airing show title, description, time range, remaining minutes, and dynamic live progress bar.
   - Quick Channel Guide drawer (opened via D-pad Left) displays the live program airing for every channel.

5. **Back Navigation & Exit Confirmation Dialog (Bug Fix)**:
   - Fixed issue where pressing Back from the Main Dashboard reopened the Settings screen.
   - Added a TV-optimized confirmation dialog ("Exit NTV?") with "Yes, Exit" and "Cancel" buttons.
   - Fixed navigation backstack accumulation across top bar tabs.

6. **Instant Auto-Focus in Player Drawers (Bug Fix)**:
   - Fixed issue where opening Stream Settings (Right D-pad) or Quick Channel Guide (Left D-pad) required pressing the Center button once before focus appeared.
   - In Stream Settings drawer: Focus now immediately highlights the currently selected video resolution option (e.g., "Auto (Recommended)" or "1080p Full HD").
   - In Quick Channel Guide drawer: The channel list pre-scrolls and immediately focuses the active/playing channel with the inverted white focus plate.

7. **Massive APK Size Optimization (~79% to 87% Reduction)**:
   - **Previous Size:** ~90.6 MB (uncompressed binaries packaged twice in both `assets/bin` and `jniLibs`, unminified debug dex).
   - **Optimizations Applied:**
     - Removed redundant duplicate binaries from `assets/bin/` (saving ~31 MB).
     - Removed unused Intel PC emulator binaries (`x86` and `x86_64`, saving ~16 MB).
     - Retained only ARM native libraries: `armeabi-v7a` (32-bit Android TV/Fire TV) and `arm64-v8a` (64-bit Tablets/Modern TVs).
     - Enabled R8 full minification (`isMinifyEnabled = true`) and resource shrinking (`isShrinkResources = true`).
   - **New Output Sizes:**
     - **Universal APK (`NTV_v1.2.apk`):** **18.53 MB** (works on all Android TV & Tablet devices, ~80% smaller).
     - **Fire TV Stick APK (`NTV_v1.2_firetv_armv7.apk`):** **11.89 MB** (~87% smaller).
     - **Xiaomi Pad 6 APK (`NTV_v1.2_arm64.apk`):** **11.58 MB** (~87% smaller).
