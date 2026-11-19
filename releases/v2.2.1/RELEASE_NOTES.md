# NTV Android TV — v2.2.1 Release Notes

**NTV v2.2.1** provides a critical hotfix for **Zee TV HD & Zee TV playback**, channel list visibility, and CloudFront live stream routing.

---

## 🌟 What's Fixed in v2.2.1

### 1. Zee TV HD & Zee TV SD Restored to Channel List
- **Fixed Channel Merging Issue**:
  - Previously, server channels with sequence indices collided with Zee channel numbers, causing **Zee TV HD (167)** to be omitted.
  - Curated third-party Zee Network channels are now prioritized first without sequence number collision, ensuring **Zee TV HD**, **Zee TV**, **Zee Cinema HD**, **Zee Cinema**, and all regional Zee channels appear prominently in the **✨ Zee Network** category.

### 2. Direct Live CloudFront HLS Routing
- **Instant Live Playback**:
  - Bound direct live CloudFront HLS endpoints (`chunks.m3u8`) to both **Zee TV HD** and **Zee TV (SD)** (`https://d1rc86nwwc9fag.cloudfront.net/260723/zeetvapachd_2500/chunks.m3u8`).
  - Added name-based fuzzy stream resolution so opening Zee TV directly connects to the verified, unencrypted CDN stream without DRM or 403 errors.
  - Stripped failed tokens from external feeds to avoid overwriting functional channels.

### 3. Release Signing Key Permanently Locked
- **Seamless Future Updates**:
  - Locked the release signing key (`app/release.keystore`) inside the project build configuration.
  - Eliminates "App not installed / Signature mismatch" errors on all future OTA auto-updates.

---

## 📦 APK Downloads & Architecture Guide

| Variant | Recommended For | File |
| :--- | :--- | :--- |
| **Universal APK** | Any Android TV, Google TV, Tablet, or Fire TV | [`NTV_v2.2.1.apk`](https://github.com/nirbhay02/NTV/releases/download/v2.2.1/NTV_v2.2.1.apk) |
| **ARM64 APK** | Modern 64-bit Android TVs & Tablets (e.g. Xiaomi Pad 6, Shield TV, Sony Bravia) | [`NTV_v2.2.1_arm64.apk`](https://github.com/nirbhay02/NTV/releases/download/v2.2.1/NTV_v2.2.1_arm64.apk) |
| **ARMv7 / Fire TV** | Amazon Fire TV Stick 4K / Lite, Older 32-bit Smart TVs | [`NTV_v2.2.1_firetv_armv7.apk`](https://github.com/nirbhay02/NTV/releases/download/v2.2.1/NTV_v2.2.1_firetv_armv7.apk) |
