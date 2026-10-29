# FT-Cloud ☁️

**FT-Cloud** is a modern, serverless Android application built with **Kotlin** and **Jetpack Compose (Material 3)** that transforms a private **Telegram Channel** into an unlimited personal cloud drive, photo/video gallery, streaming media player, and automatic backup vault — powered by a resilient **Multi-Bot Swarm**.

Zero third-party servers. Zero storage quotas. Every file, folder, and pinned index snapshot lives strictly inside your own Telegram channel.

---

## ✨ Key Capabilities

- **🤖 Multi-Bot Swarm Engine (`MultiBotSwarmManager`)**
  - Connect **1 or multiple Telegram Bot Tokens** (`@BotFather`) to a private Telegram Channel.
  - Parallel chunk transfers with token-bucket pacing and automatic **`HTTP 429 retry_after` (`FloodWait`)** load-balancing across healthy bots.
  - **Cross-Bot Recovery**: Even if an older bot token is deleted and replaced with a brand-new bot in the same channel, FT-Cloud automatically re-mints bot-scoped `file_id`s via message forwarding so your historical files always remain downloadable.
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
  - Install FT-Cloud on a new phone, enter your Bot Token + Channel ID, and restore your entire virtual folder hierarchy, stars, and file catalog in one tap.
- **🔒 Optional Client-Side Encryption & Device Security**
  - Optional **AES-256-GCM** chunk & manifest encryption derived via `PBKDF2-HMAC-SHA256` (`210,000` iterations) with chunk-index AAD binding (preventing chunk-swap tampering).
  - **Android Keystore (`EncryptedSharedPreferences`)** credential storage, **Biometric / Screen-Lock App Gate**, and **Block Screenshots (`FLAG_SECURE`)**.

---

## 🚀 Quick Setup (60 Seconds)

1. Open **[@BotFather](https://t.me/BotFather)** in Telegram, send `/newbot`, and copy your **Bot Token** (create 2–3 bots if you want parallel Swarm throughput).
2. Create a **Private Telegram Channel** and add your bot(s) as **Administrators** (with permissions to *Post*, *Edit*, *Delete*, and *Pin* messages).
3. Find your **Channel ID** (e.g., `-1001234567890` — forward any message from the channel to `@userinfobot` or copy the ID from Telegram Desktop/Web).
4. Launch **FT-Cloud**, paste your Channel ID and Bot Token(s), and tap **Connect Telegram Cloud**.

---

## 🏗️ Project Architecture

```text
app/src/main/java/com/ftcloud/app/
├── core/
│   ├── crypto/        # AES-256-GCM chunk cipher, PBKDF2-SHA256 KDF, Android Keystore store
│   ├── database/      # Room SQLite entities & DAOs (folders, files, chunks, transfers, backup log)
│   ├── media/         # TelegramStreamProxy (RFC 7233 HTTP 206) & LRU ChunkDiskCache
│   ├── model/         # Domain models, formatters & GZIP/encrypted CloudManifest codec
│   ├── sync/          # CloudManifestSync (pinned index) & WorkManager AutoBackupWorker
│   ├── telegram/      # OkHttp Bot API client, TokenBucketRateLimiter & MultiBotSwarmManager
│   └── transfer/      # 19 MiB ChunkedUploadEngine, ChunkedDownloadEngine & TransferWorker
├── data/              # DriveRepository & DataStore SettingsRepository
├── di/                # FTCloudContainer dependency graph & Coil video-frame ImageLoader
└── ui/
    ├── components/    # Material 3 file/folder cards, breadcrumbs, sheets & dialogs
    ├── navigation/    # Adaptive NavigationBar shell & Android Share Sheet handler
    ├── screens/       # Onboarding, Drive, Gallery, Preview (Media3/PDF/Zoom), Transfers, Backup, Settings
    └── theme/         # Light, Dark, AMOLED Pure Black & Material You Dynamic Color themes
```

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

# Build installable Debug APK
./gradlew assembleDebug
```

GitHub Actions automatically builds both Debug and Release APKs and uploads them as workflow artifacts on every push via [`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml).

---

## 📄 License

Released under the [MIT License](LICENSE). Independent open-source project; not affiliated with Telegram FZ-LLC.
