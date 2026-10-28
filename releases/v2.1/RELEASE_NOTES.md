# NTV Android TV — v2.1 Release Notes

**NTV v2.1** brings a major accuracy overhaul to live TV schedules with **Official Native JioTV EPG Integration**, alongside critical **Dashboard UI fixes**, responsive action buttons, and overall streaming stability improvements.

---

## 🌟 What's New in v2.1

### 1. Accurate Native JioTV EPG Guide
- **Direct Official EPG Source**: Replaced third-party / misaligned XMLTV schedules with official daily GZ EPG archives (`jiotvstatic.akamaized.net`) and real-time live playback EPG endpoints (`api.jiotv.com`).
- **100% Accurate Movie & Show Metadata**:
  - Movie channels (e.g. *Zee Cinema HD*, *Star Gold HD*, *And Pictures HD*, *Sony MAX HD*, *Colors Cineplex*) now accurately show the exact movie currently on air and upcoming next.
  - Entertainment and regional channels display synchronized episode titles and descriptions.
- **Instant In-Memory Caching & Smooth Sync**:
  - Daily schedules are loaded and cached in memory with fast background prefetching.
  - Zero lag when browsing channels in the dashboard or bringing up the player quick-info overlay.

---

### 2. Dashboard Detail Pane & Action Buttons Fix
- **Fixed Button Shrinking Bug**:
  - Solved an issue where "Watch Live" and "Add Fav" buttons were squeezed and flattened on channels with rich metadata, multiple badges, or long descriptions.
  - Enforced a strict minimum height of `44.dp` with Compose layout weighting (`Modifier.weight(1f)` on scrollable detail content), guaranteeing action buttons always receive full height and proper proportions.
- **Enhanced Focus Aesthetics**:
  - High-contrast electric cyan and glowing white focus states with bold typography and clear iconography.
  - Seamless 2D directional D-pad navigation across categories, channel lists, and bottom action buttons.
- **Optimized 1080p Viewport Balance**:
  - Refined showcase logo dimensions (`135x82dp`) and tightened card paddings to ensure Now Playing, Up Next, and metadata badges all display comfortably on standard TV displays.

---

### 3. Player Quick-Info & DRM Playback Improvements
- Seamlessly synced live playback OSD (On-Screen Display) with native EPG data for instant "Now Playing" and "Up Next" glance during channel zapping.
- Rock-solid ClearKey DRM key acquisition and zero-buffering playback for premium Zee Network channels.

---

## 📦 APK Downloads & Architecture Guide

| Variant | Recommended For | File |
| :--- | :--- | :--- |
| **Universal APK** | Any Android TV, Google TV, Tablet, or Fire TV | [`NTV_v2.1.apk`](https://github.com/nirbhay02/NTV/releases/download/v2.1/NTV_v2.1.apk) |
| **ARM64 APK** | Modern 64-bit Android TVs & Tablets (e.g. Xiaomi Pad 6, Shield TV, Sony Bravia) | [`NTV_v2.1_arm64.apk`](https://github.com/nirbhay02/NTV/releases/download/v2.1/NTV_v2.1_arm64.apk) |
| **ARMv7 / Fire TV** | Amazon Fire TV Stick 4K / Lite, Older 32-bit Smart TVs | [`NTV_v2.1_firetv_armv7.apk`](https://github.com/nirbhay02/NTV/releases/download/v2.1/NTV_v2.1_firetv_armv7.apk) |

---

## 🛠️ Verification & Testing
- Tested live on connected Android TV testbed and **Xiaomi Pad 6** (Android 14 / HyperOS).
- 100% smooth D-pad remote navigation, hardware-accelerated H.264/HEVC decoding, and auto-updating support.
