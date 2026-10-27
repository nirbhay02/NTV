# NTV Android TV — v2.0 Release Notes

**NTV v2.0** is a major milestone release introducing a ground-up **Glassmorphic UI overhaul**, dynamic ambient genre lighting, comprehensive **All-In-One OTT EPG guide** integration via `mitthu786/tvepg`, streamlined streaming stability, and snappy TV navigation.

---

## 🌟 What's New in v2.0

### 1. Modern Glassmorphic Dashboard & Aesthetics
- **Dynamic Ambient Lighting Canvas**: Fluid ambient background that seamlessly tints in real-time according to the selected channel's genre (e.g. Electric Purple for Zee Network, Vivid Emerald for Sports, Amber Gold for Movies, Crimson for News, and Cyan for Entertainment).
- **Glassmorphic Top Bar**:
  - High-contrast **NTV** brand logo with glowing electric cyan **LIVE** badge.
  - Frosted glass **Server Status Pill** with animated dual-ring breathing halo (`Server: Online (5001)`).
  - Glassmorphic **JIO Connection Status** badge with one-touch login modal.
  - Integrated quick action buttons (`Search`, `Settings`) and real-time clock.
- **Upgraded Category Rail**:
  - Vertical neon cyan indicator bar on focused and active categories.
  - Channel count pill badges (`Recent`, `Zee Network`, `Entertainment`, `Sports`, etc.).
- **Interactive Channel Cards**:
  - Glass cards with dynamic focus glow & 1.03x scale animation.
  - Genre-colored accent bar on active focus, high-contrast logo plate, glossy cyan `HD` badge, and clean typography.
- **Hero Detail Showcase**:
  - Floating channel logo showcase plate.
  - Prominent channel number watermark badge in the top right.
  - Clear metadata badges (`1080p Full HD`, `Genre`, `Language`, `ClearKey DRM`).
  - **Now Playing Card**: Pulsing live broadcast dot, current show title, detailed description, and live progress bar with remaining time.
  - **Up Next Card**: Next scheduled show summary and air time.
  - Electric Cyan gradient **"Watch Live"** action button & **"Add Fav"** button.
- **Quick Navigation Bar**: Frosted pill bar with cyan key badges explaining TV remote controls.

---

### 2. All-In-One OTT EPG Guide Integration (`mitthu786/tvepg`)
- **Direct Integration**: Switched primary EPG source to the comprehensive All-In-One OTT XMLTV feed from `https://github.com/mitthu786/tvepg` (`epg.xml.gz` covering over 2,200+ channels across JioTV, Tata Play, ZEE5, SonyLIV, and Sun NXT with 52,000+ programmes).
- **Multi-Tier Robust Fallback Pipeline**:
  - Primary: Fast raw GitHub CDN (`https://raw.githubusercontent.com/mitthu786/tvepg/main/epg.xml.gz`)
  - Secondary: GitHub Pages CDN mirror (`https://mitthu786.github.io/tvepg/epg.xml.gz`)
  - Short link mirror: `https://avkb.short.gy/epg.xml.gz`
  - Dedicated JioTV 2-day fallbacks (`jiotv/epg.xml.gz`)
- **Smart Filtering & Alias Resolution**:
  - Filters out blank/dummy program titles to ensure only genuine schedule data is displayed.
  - Intelligent alias matching prioritized standard numeric JioTV IDs while resolving Tata Play / SonyLIV display names for channels like Star Sports and Sony Sports Ten.
- **24/7 Schedule Projection**: 72-hour rolling retention window and integer 24h schedule projection guarantee that channels display full Now/Next program info 24/7 even during off-cycle feed updates.

---

### 3. Streamlined & Optimized Playback Engine
- Standardized on the ultra-stable, high-performance ExoPlayer / Media3 streaming pipeline with hardware-accelerated decoding.
- Removed faulty/deprecated experimental playback engines to ensure zero black-screen issues on HD channels and rock-solid zapping speeds.
- Seamless ClearKey DRM playback for Zee Network channels and direct MPD/HLS streaming with zero buffering regressions.

---

## 📦 APK Downloads & Architecture Guide

| Variant | Recommended For | File |
| :--- | :--- | :--- |
| **Universal APK** | Any Android TV, Google TV, Tablet, or Fire TV | [`NTV_v2.0.apk`](https://github.com/nirbhay02/NTV/releases/download/v2.0/NTV_v2.0.apk) |
| **ARM64 APK** | Modern 64-bit Android TVs, Tablets (e.g. Xiaomi Pad 6, Shield TV) | [`NTV_v2.0_arm64.apk`](https://github.com/nirbhay02/NTV/releases/download/v2.0/NTV_v2.0_arm64.apk) |
| **ARMv7 / Fire TV** | Amazon Fire TV Stick 4K / Lite, Older 32-bit Smart TVs | [`NTV_v2.0_firetv_armv7.apk`](https://github.com/nirbhay02/NTV/releases/download/v2.0/NTV_v2.0_firetv_armv7.apk) |

---

## 🛠️ Verification & Compatibility
- Verified live on **Xiaomi Pad 6** (Android 14 / HyperOS) and Android TV 11+ testbeds.
- 100% smooth D-pad remote navigation and touch support.
