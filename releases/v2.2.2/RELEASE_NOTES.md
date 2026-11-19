# NTV Android TV — v2.2.2 Release Notes

**NTV v2.2.2** is a critical update that restores the **official Indian domestic broadcast feeds** for Zee Network channels using the exact architecture and parameters from F-e-n-y-x JioTV.

---

## 🌟 What's Changed in v2.2.2

### 1. Official Indian Domestic Telecast Feeds (Fenyx Parity)
- **Removed International / APAC Feeds**:
  - Completely stripped foreign CloudFront feeds (`zeetvapachd`, `zeecinemaapachd`) that broadcast APAC/international programming.
- **Native Jio CDN Indian Streams with ClearKey DRM**:
  - Restored verified domestic Indian feeds (`ZeeTVHD_BTS`, `ZeeTV_BTS`, `ZeeCinemaHD_BTS`, `ZeeCinema_BTS`, `ZeeTalkies_BTS`, `ZeeMarathiHD_BTS`, `AndPicturesHD_BTS`, `AndTVHD_BTS`, etc.) from `jiotvpllive.cdn.jio.com`.
  - Configured matching ClearKey JWK license keys and broadcaster spoofing headers (`User-Agent: @shoebbro`, `Referer: https://www.jiotv.com/`, `Origin: https://www.jiotv.com`).

### 2. Stream Transport & Segment Auth Cookie Caching
- **ProxyStreamHandler Segment Cookie Injection**:
  - Cached live `__hdnea__` Akamai session tokens for `jiotvpllive.cdn.jio.com` so ExoPlayer media segments inherit authentication cookies even when relative segment URLs lack query parameters.
  - Automatically routes expired third-party feeds to Jio's own local server stream (`/live/<channelNumber>.m3u8`) without playback freezes.

### 3. News Channels Direct Indian CloudFront Feeds
- **24x7 Domestic News Streams**:
  - Zee News (504), Zee 24 Taas (442), Zee Business (657), Zee Bharat (652), and state news channels connect directly to 24x7 unencrypted Indian CloudFront feeds.

### 4. Release Signing Certificate Preserved
- Signed with permanent key (`app/release.keystore`, SHA256 fingerprint `...57:44:73`), ensuring seamless in-app OTA upgrades without signature mismatch errors.

---

## 📦 APK Downloads & Architecture Guide

| Variant | Recommended For | File |
| :--- | :--- | :--- |
| **Universal APK** | Any Android TV, Google TV, Tablet, or Fire TV | [`NTV_v2.2.2.apk`](https://github.com/nirbhay02/NTV/releases/download/v2.2.2/NTV_v2.2.2.apk) |
| **ARM64 APK** | Modern 64-bit Android TVs & Tablets (Xiaomi Pad, Shield TV, Sony Bravia) | [`NTV_v2.2.2_arm64.apk`](https://github.com/nirbhay02/NTV/releases/download/v2.2.2/NTV_v2.2.2_arm64.apk) |
| **ARMv7 / Fire TV** | Amazon Fire TV Stick 4K / Lite, 32-bit Smart TVs | [`NTV_v2.2.2_firetv_armv7.apk`](https://github.com/nirbhay02/NTV/releases/download/v2.2.2/NTV_v2.2.2_firetv_armv7.apk) |
