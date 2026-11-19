# NTV Android TV — v2.2 Release Notes

**NTV v2.2** is a critical stability update that completely resolves **Zee Network live TV playback issues** with **official JioTV server prioritization**, **resilient CloudFront live HLS fallbacks**, and **smart real-time DRM/403 recovery** in the playback engine.

---

## 🌟 What's New in v2.2

### 1. Complete Zee Network Playback Fix
- **Official Server Stream Prioritization**:
  - Restored proper routing so official high-bitrate JioTV streams take precedence over third-party M3U entries for all Zee Network channels.
  - Correctly categorizes and tags all Zee channels under the dedicated **Zee Network** genre.
  - Automatically preserves unique third-party channels not in the server list.
- **Embedded Live CloudFront HLS Fallbacks**:
  - Integrated high-speed CDN CloudFront live HLS streams for 18+ high-priority Zee channels:
    - **Entertainment**: Zee TV HD, Zee Marathi HD, Zee Telugu HD, Zee Bangla HD, Zee Keralam HD, Zee Kannada HD, Zee Tamil HD, &TV HD, Zee Yuva, Zing.
    - **Movies**: Zee Cinema HD, Zee Talkies HD, Zee Bollywood, &pictures HD, &flix HD.
    - **Infotainment & News**: Zee News, Zee 24 Taas, Zee Cafe HD.
- **Auto-Token Expiry Detection**:
  - Filters out expired M3U session tokens (`__hdnea__` expiry check) on channel parse and automatically routes playback to verified CloudFront HLS streams.

---

### 2. Smart Player Recovery & DRM Fallback
- **Instant Fallback on HTTP 403 / DRM Errors**:
  - In `TvPlayerManager`, if a DRM-protected or tokenized MPD stream encounters an HTTP 403 or DRM key acquisition failure, the player seamlessly switches to the unencrypted CloudFront HLS fallback stream in real-time.
  - Eliminates black screens, infinite buffering spinners, and playback crashes when watching Zee channels.
- **Cleaned Channel ID Resolution**:
  - Strips internal prefixes when requesting fallback streams from the local JioTV Go server backend.

---

### 3. Architecture & Testing
- **JVM & Android TV API 21+ Compatibility**:
  - Modernized Base64 URL-safe encoding for ClearKey JWK token transformation across all Android TV architectures.
  - 100% passed unit test coverage for channel merging, token validation, and fallback mechanisms.

---

## 📦 APK Downloads & Architecture Guide

| Variant | Recommended For | File |
| :--- | :--- | :--- |
| **Universal APK** | Any Android TV, Google TV, Tablet, or Fire TV | [`NTV_v2.2.apk`](https://github.com/nirbhay02/NTV/releases/download/v2.2/NTV_v2.2.apk) |
| **ARM64 APK** | Modern 64-bit Android TVs & Tablets (e.g. Xiaomi Pad 6, Shield TV, Sony Bravia) | [`NTV_v2.2_arm64.apk`](https://github.com/nirbhay02/NTV/releases/download/v2.2/NTV_v2.2_arm64.apk) |
| **ARMv7 / Fire TV** | Amazon Fire TV Stick 4K / Lite, Older 32-bit Smart TVs | [`NTV_v2.2_firetv_armv7.apk`](https://github.com/nirbhay02/NTV/releases/download/v2.2/NTV_v2.2_firetv_armv7.apk) |

---

## 🛠️ Verification & Testing
- Verified live stream HLS manifest decoding and ClearKey fallback logic.
- 100% smooth D-pad remote navigation, hardware-accelerated playback, and in-app OTA auto-update compatibility.
