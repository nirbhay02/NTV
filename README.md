# NTV for Android TV (v1.2.2) 📺

A high-performance, native Android TV application built with **Kotlin** and **Jetpack Compose for TV (Material 3)**. It embeds the streaming server directly inside the Android application itself, completely eliminating any requirement for external PCs, Termux, Docker, or secondary network servers.

It automatically handles local process lifecycle management, broadcaster authentication token proxying (with special priority for **Zee Network channels** to prevent 403 Forbidden errors), and native streaming via **AndroidX Media3 ExoPlayer**.

---

## Embedded Architecture (100% Self-Contained)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       ANDROID TV DEVICE / SMART TV                          │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                    JioTV Android TV Application                       │  │
│  │                                                                       │  │
│  │  • Jetpack Compose for TV (D-Pad UI & Remote Navigation)              │  │
│  │  • Broadcaster & Zee Channel Categorizer (✨ Zee Network Row)          │  │
│  │  • Media3 ExoPlayer Engine (Proxy-aware HLS Playback)                 │  │
│  │  • On-Screen Remote OTP Login Dialog for Jio Accounts                 │  │
│  └───────────────────────────────────┬───────────────────────────────────┘  │
│                                      │                                      │
│                             HTTP (127.0.0.1:5001)                           │
│                                      ▼                                      │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │               JioTvServerService (Foreground Service)                 │  │
│  │                                                                       │  │
│  │  • EmbeddedServerManager: Spawns native jiotv_go Go binary            │  │
│  │  • Manages process lifecycle (Start / Stop / Auto-Restart / Monitor)  │  │
│  │  • EmbeddedFallbackServer: Pure Kotlin HTTP server safety net         │  │
│  │  • Local Storage: Credentials & tokens stored in context.filesDir     │  │
│  └───────────────────────────────────┬───────────────────────────────────┘  │
└──────────────────────────────────────┼──────────────────────────────────────┘
                                       │ Live CDN Request + Dynamic Headers
                                       ▼
                     ┌───────────────────────────────────┐
                     │          Jio TV Live CDN          │
                     │  (Zee, Sony, Star, Sports Feeds)  │
                     └───────────────────────────────────┘
```

---

## Key Features

### 1. Embedded `jiotv_go` Server (No External Dependencies)
- **Direct On-Device Execution**: The native Go backend binary is managed and executed directly on the Android TV using [`EmbeddedServerManager`](file:///C:/Users/Nirbhay/.gemini/antigravity/scratch/jiotv_android_tv/app/src/main/java/com/jiotv/androidtv/server/EmbeddedServerManager.kt) and [`JioTvServerService`](file:///C:/Users/Nirbhay/.gemini/antigravity/scratch/jiotv_android_tv/app/src/main/java/com/jiotv/androidtv/server/JioTvServerService.kt).
- **Foreground Service Protection**: An ongoing Android TV Foreground Service protects the streaming server process from being terminated by OS battery optimization or task cleanup while switching apps.
- **Dynamic Binary Packaging**: Detects target CPU architecture (`arm64`, `arm`, `amd64`, `386`), extracts pre-bundled binaries from app assets or downloads on first run, and configures Linux executable permissions (`chmod 755`).
- **Live Diagnostics Console**: Real-time stdout/stderr log stream viewer in the TV Settings screen.

### 2. On-Screen Jio OTP Login Flow
- **Remote-Friendly Numpad**: With [`LoginOtpDialog`](file:///C:/Users/Nirbhay/.gemini/antigravity/scratch/jiotv_android_tv/app/src/main/java/com/jiotv/androidtv/ui/components/LoginOtpDialog.kt), users enter their Jio mobile number and 6-digit OTP directly using their TV remote control.
- **Local Credential Storage**: Authentication cookies and tokens are written straight to the device's internal storage directory, automatically authorizing broadcaster feeds.

### 3. Anti-403 Forbidden Engine & Zee Network Isolation
- **Dynamic Proxying**: Routes segment requests through `http://127.0.0.1:5001/render.m3u8` with origin and referer injection, ensuring Zee Network streams do not drop due to missing tokens.
- **[`ChannelFilter`](file:///C:/Users/Nirbhay/.gemini/antigravity/scratch/jiotv_android_tv/app/src/main/java/com/jiotv/androidtv/data/parser/ChannelFilter.kt)**: Programmatically matches Zee keywords (`Zee`, `&pictures`, `&tv`, `&flix`, `&xplor`, `&prive`, `Zing`, `Big Magic`) and pins a dedicated **"✨ Zee Network"** row at the top of the dashboard.
- **Cleartext Traffic**: Enabled via `android:usesCleartextTraffic="true"` and `network_security_config.xml`.

### 4. TV D-Pad Remote Navigation
- **Jetpack Compose for TV**: Built using `androidx.tv:tv-material:1.0.0` and `androidx.tv:tv-foundation:1.0.0-alpha11`.
- **D-Pad Focus States**: High-contrast golden glow focus rings, 1.08x scale animations, and smooth horizontal scrolling.
- **Hero Banner Preview**: Dynamically previews channel details, logos, and live indicators as the user highlights cards.

### 5. Media3 ExoPlayer Native Playback
- **Full-Screen Custom Player**: Wrapped using `AndroidView` and `PlayerView` with custom Compose TV OSD overlay.
- **Remote D-Pad Controls**:
  - **▲ / ▼ DPAD UP / DOWN**: Switch to Previous / Next channel.
  - **◄ / ► DPAD LEFT / RIGHT**: Toggle Quick Channel Guide Drawer Overlay.
  - **OK / CENTER / MEDIA PLAY/PAUSE**: Toggle OSD controls / Play-Pause.
  - **BACK**: Dismiss OSD / Quick Guide, or return to Dashboard.

---

## File Tree

```
jiotv_android_tv/
├── app/
│   ├── build.gradle.kts                      # Module dependencies & TV compile options
│   ├── proguard-rules.pro                   # Media3, OkHttp & Gson ProGuard rules
│   └── src/main/
│       ├── AndroidManifest.xml               # Foreground service & cleartext permissions
│       ├── res/
│       │   ├── drawable/                     # TV banners (320x180), logos & launcher icons
│       │   ├── values/                       # TV dark palette, Leanback theme & strings
│       │   └── xml/network_security_config.xml# Cleartext domain permissions for localhost & LAN
│       └── java/com/jiotv/androidtv/
│           ├── JioTvApp.kt                   # Application singleton & repository providers
│           ├── MainActivity.kt               # Fullscreen TV Activity & server service launcher
│           ├── server/
│           │   ├── EmbeddedServerManager.kt  # Spawns & manages native jiotv_go process
│           │   ├── EmbeddedServerState.kt    # State machine for the embedded backend
│           │   ├── JioTvServerService.kt     # Android Foreground Service keeping backend alive
│           │   └── EmbeddedFallbackServer.kt # Kotlin HTTP server fallback & testing proxy
│           ├── data/
│           │   ├── model/                    # Channel, Category, ServerConfig, EpgProgram
│           │   ├── parser/                   # M3uParser, ChannelFilter, EpgParser
│           │   ├── network/                  # ProxyStreamHandler, JioTvApiService
│           │   ├── local/                    # AppPreferences (DataStore)
│           │   └── repository/               # ChannelRepository, ServerConfigRepository
│           ├── player/
│           │   └── TvPlayerManager.kt        # Media3 ExoPlayer wrapper with proxy headers
│           └── ui/
│               ├── theme/                    # TV typography, color scheme & MaterialTheme
│               ├── components/
│               │   ├── ChannelCard.kt        # Focusable TV card with scale & badges
│               │   ├── CategoryRow.kt        # Horizontal TV lazy row
│               │   ├── HeroBanner.kt         # Top dynamic channel preview banner
│               │   ├── TvTopBar.kt           # Top navigation pills
│               │   ├── LoginOtpDialog.kt     # TV remote on-screen OTP login dialog
│               │   └── ErrorBanner.kt        # Connection error banner & retry actions
│               ├── screens/
│               │   ├── MainDashboardScreen.kt# Main TV UI with categorized rows
│               │   ├── ZeeChannelsScreen.kt  # Dedicated Zee channels view
│               │   ├── PlayerScreen.kt       # Fullscreen player with OSD & quick guide
│               │   ├── SearchScreen.kt       # TV search & filter tags
│               │   └── SettingsScreen.kt     # Server config, test ping & quality
│               ├── viewmodel/
│               │   ├── MainViewModel.kt
│               │   ├── PlayerViewModel.kt
│               │   └── SettingsViewModel.kt
│               └── navigation/
│                   ├── Screen.kt
│                   └── TvNavigation.kt
├── download_embedded_binaries.ps1           # Bundles jiotv_go native Android binaries
├── build.gradle.kts                          # Project level Gradle script
├── settings.gradle.kts                       # Repository management
├── gradlew.bat / gradlew
└── README.md
```

---

## How to Build & Run

1. Open `C:\Users\Nirbhay\.gemini\antigravity\scratch\jiotv_android_tv` in **Android Studio**.
2. Run `download_embedded_binaries.ps1` to pre-bundle the native binaries into `assets/bin/` if desired, or let the app download it on first launch.
3. Deploy to your Android TV, Fire TV Stick, or Google TV device/emulator.
4. On launch, the embedded server will start automatically in a foreground service on `http://127.0.0.1:5001`.
5. Open **Settings -> Jio Account Login (OTP)** to authenticate once with your Jio number. Channels and Zee streams will load automatically.
