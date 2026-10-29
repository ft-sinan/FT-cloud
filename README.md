# FT Cloud ☁️

**FT Cloud** is a modern, serverless Android application built with **Kotlin** and **Jetpack Compose (Material 3 Expressive)** that transforms a private **Telegram Channel** into an unlimited personal cloud drive, photo/video gallery, streaming media player, and automatic backup vault — crafted in the signature **FT / Orion** design language and powered by a resilient **Multi-Bot Swarm**.

Zero third-party servers. Zero storage quotas. Every file, folder, and pinned index snapshot lives strictly inside your own Telegram channel.

---

## ✨ Key Capabilities

- **🎨 Signature FT / Orion Material 3 Expressive Design System**
  - **Space Grotesk** geometric typography across display, headline, title, and label hierarchies (`10dp`–`36dp` expressive shapes).
  - **9 Curated Brand Palettes**: Default **Orion** (`#4C4ECD` / `#C0C1FF` indigo-violet + `#CCFE82` / `#CAFB80` electric lime accents) plus `Teal`, `Navy`, `Emerald`, `Indigo`, `Ocean`, `Grape`, `Cocoa`, and `Slate`, alongside `System`, `Light`, `Dark`, **OLED Pure Black**, and optional **Material You** wallpaper colors.
  - Tactile `springPress` micro-interactions with haptic feedback, morphing hero headers (`MorphingShapeBox`), floating glass pill bottom dock, **Compact Density**, and **Reduce Motion** controls.
- **🔄 Built-In GitHub Releases Updater & Seamless In-Place Installer**
  - **Settings → App Updates** (plus automatic once-per-24-hour foreground checks) queries GitHub Releases (`ft-sinan/FT-cloud`), compares semantic versions, downloads `FTCloud-vX.Y.Z.apk` with live progress, verifies its **SHA-256** checksum sidecar (`.sha256`), guides Android unknown-sources permission if needed, and launches the native package installer via `FileProvider`.
  - Consistent APK signing across CI & release builds ensures updates install **seamlessly in-place** over existing installs without wiping credentials or your local index.
- **🤖 Multi-Bot Swarm Engine (`MultiBotSwarmManager`)**
  - Connect **1 or multiple Telegram Bot Tokens** (`@BotFather`) to a private Telegram Channel.
  - Parallel chunk transfers with token-bucket pacing and automatic **`HTTP 429 retry_after` (`FloodWait`)** load-balancing across healthy bots.
  - **Cross-Bot Recovery**: Even if an older bot token is deleted and replaced with a brand-new bot in the same channel, FT Cloud automatically re-mints bot-scoped `file_id`s via message forwarding so your historical files always remain downloadable.
- **📦 Unlimited File Sizes (`19 MiB` Safe Auto-Chunking)**
  - Streams files of any size (multi-GB 4K videos, backups, ISOs) in **19 MiB** (`19,922,944` byte) chunks — safely below Telegram Bot API's `20 MB` `getFile` download ceiling.
  - Resumable per-chunk persistence and end-to-end **SHA-256** integrity verification + deduplication.
- **🎬 Zero-Wait Media3 Streaming (`TelegramStreamProxy`)**
  - Built-in local `127.0.0.1` loopback HTTP proxy implementing **RFC 7233 `HTTP 206 Partial Content` (`Range: bytes=start-end`)** across chunk boundaries, backed by an LRU disk chunk cache and background next-chunk prefetching.
  - Scrub and stream videos and music immediately via **AndroidX Media3 ExoPlayer**, view zoomable photos, render multi-page **PDFs** natively (`PdfRenderer`), and read code/Markdown inline.
- **📸 Photos Timeline & Automatic Background Backup**
  - Dedicated **Photos & Videos** timeline grouped by capture date (`Today`, `Yesterday`, `October 2026`…) with EXIF metadata and grid zoom (`2×`, `3×`, `4×`).
  - **WorkManager Auto-Backup** (`AutoBackupWorker`) backs up your Camera Roll and custom device folders (`OpenDocumentTree` SAF) in original quality with **Wi-Fi Only** and **Charging Only** rules.
- **📌 Serverless Cross-Device Recovery (`CloudManifestSync`)**
  - Compresses (`GZIP`) and pins a lightweight `ftcloud-manifest-v1.json` snapshot directly in your Telegram channel.
  - Install FT Cloud on a new phone, enter your Bot Token + Channel ID, and restore your entire virtual folder hierarchy, stars, and file catalog in one tap.
- **🔒 Optional Client-Side Encryption & Device Security**
  - Optional **AES-256-GCM** chunk & manifest encryption derived via `PBKDF2-HMAC-SHA256` (`210,000` iterations) with chunk-index AAD binding (preventing chunk-swap tampering).
  - **Android Keystore (`EncryptedSharedPreferences`)** credential storage, **Biometric / Screen-Lock App Gate**, and **Block Screenshots (`FLAG_SECURE`)**.

---

## 🚀 Quick Setup (60 Seconds)

1. Download **`FTCloud-v1.1.0.apk`** from [**GitHub Releases**](https://github.com/ft-sinan/FT-cloud/releases/latest) and install it on your Android device.
2. Open **[@BotFather](https://t.me/BotFather)** in Telegram, send `/newbot`, and copy your **Bot Token** (create 2–3 bots if you want parallel Swarm throughput).
3. Create a **Private Telegram Channel** and add your bot(s) as **Administrators** (with permissions to *Post*, *Edit*, *Delete*, and *Pin* messages).
4. Find your **Channel ID** (e.g., `-1001234567890` — forward any message from the channel to `@userinfobot` or copy the ID from Telegram Desktop/Web).
5. Launch **FT Cloud**, paste your Channel ID and Bot Token(s), and tap **Connect Telegram Cloud**.

---

## 🏗️ Project Architecture

```text
app/src/main/java/com/ftcloud/app/
├── core/
│   ├── crypto/        # AES-256-GCM chunk cipher, PBKDF2-SHA256 KDF, Android Keystore store
│   ├── database/      # Room SQLite entities & DAOs (folders, files, chunks, transfers, backup log)
│   ├── media/         # TelegramStreamProxy (RFC 7233 HTTP 206) & LRU ChunkDiskCache
│   ├── model/         # Domain models, formatters & GZIP/encrypted CloudManifest codec
│   ├── sync/          # CloudManifestSync, ChannelMediaImporter & WorkManager workers
│   ├── telegram/      # OkHttp Bot API client, TokenBucketRateLimiter & MultiBotSwarmManager
│   ├── transfer/      # 19 MiB ChunkedUploadEngine, ChunkedDownloadEngine & TransferWorker
│   └── update/        # GitHub Releases AppUpdateManager, SemVer & SHA-256 sidecar verifier
├── data/              # DriveRepository & DataStore SettingsRepository
├── di/                # FTCloudContainer dependency graph & Coil video-frame ImageLoader
└── ui/
    ├── components/    # Expressive headers, morphing shapes, springPress & file/folder cards
    ├── navigation/    # Floating glass pill dock shell, 24h update check & Share Sheet handler
    ├── screens/       # Onboarding, Drive, Gallery, Preview (Media3/PDF/Zoom), Transfers, Backup, Settings
    └── theme/         # Orion + 8 brand palettes, Space Grotesk typography, OLED Black & Dynamic Color
```

---

## 📥 Importing Pre-Existing Channel Media (7,000+ Items)

Already used your private Telegram channel with another cloud app or uploaded thousands of photos/videos directly in Telegram? Open **Settings → Import Pre-Existing Channel Media**:

1. **Instant `result.json` Import (~2 seconds for 7,000+ items, zero rate limits):**
   - In **Telegram Desktop**, open your channel → `⋮` → **Export chat history** → select **Machine-readable JSON**, and **uncheck** all media download boxes (exports only metadata in ~3 seconds).
   - Pick `result.json` in FT Cloud. All 7,000+ photos, videos, audio tracks, and documents appear immediately in **`Imported from Channel`** and your **Gallery** timeline; bot `file_id`s resolve lazily on demand when opened.
2. **Batch Forward & Live Channel Sync (`getUpdates`):**
   - New posts made directly in your channel sync automatically. On mobile without a PC, you can also forward up to 100 messages at a time to any bot in your swarm and tap **Sync Forwarded Batches**.
3. **Background Bot Swarm History Scanner (No PC Needed):**
   - Scans your channel **newest-first** in the background via `WorkManager` (pause/resume anytime). Tip: send `/start` to your bots in Telegram first to enable **Turbo DM Scanning**, avoiding Telegram's 20 msg/min channel forward limit.
4. **Legacy `> 20 MB` Single-Post Videos:**
   - Photos, audio, documents, and videos `<= 20 MB` (plus larger videos where Telegram generated an `<= 20 MB` rendition) stream inline inside FT Cloud. Any older un-chunked post `> 20 MB` includes a one-tap **Open & Play in Telegram** (`t.me/c/...`) deep link.

---

## 🔐 Seamless In-Place Updates & Release Signing

Android requires every update APK (`FTCloud-vX.Y.Z.apk`) to be signed with the **same X.509 keystore** as the installed app so upgrades install seamlessly in-place.

- Out of the box, GitHub Actions caches a persistent 10,000-day keystore across builds and publishes `FTCloud-vX.Y.Z.apk` + `FTCloud-vX.Y.Z.apk.sha256` to GitHub Releases.
- For long-term production ownership (identical to **FT Vault**), you can configure your own permanent release keystore in **Settings → Secrets and variables → Actions** using either `FTCLOUD_RELEASE_*` or your shared `MYVAULT_RELEASE_*` secrets:
  - `FTCLOUD_RELEASE_KEYSTORE_BASE64` (base64-encoded `.jks` file)
  - `FTCLOUD_RELEASE_STORE_PASSWORD`
  - `FTCLOUD_RELEASE_KEY_ALIAS`
  - `FTCLOUD_RELEASE_KEY_PASSWORD`

---

## 🛠️ Building & Testing

### Prerequisites
- **JDK 17**
- **Android SDK 35** (Android 8.0 `minSdk = 26`, `targetSdk = 35`)

### Commands
```bash
# Run unit & MockWebServer integration tests
./gradlew testDebugUnitTest

# Run Android Lint checks
./gradlew lintDebug

# Build installable Debug & Release APKs
./gradlew assembleDebug assembleRelease
```

---

## 📄 License

Released under the [MIT License](LICENSE). Includes [Space Grotesk](app/src/main/assets/licenses/SpaceGrotesk-OFL.txt) under the SIL Open Font License 1.1. Independent open-source project; not affiliated with Telegram FZ-LLC.
