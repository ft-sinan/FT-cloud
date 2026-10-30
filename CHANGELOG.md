# Changelog

All notable changes to **FT Cloud** are documented in this file.

## [1.2.6] - 2026-10-30

### Fixed & Improved
- **Permanent Immutable APK Signing Keystore (`app/signing/ftcloud-signing.p12`)**:
  - Fixed the root cause of why `v1.2.4 → v1.2.5` still reported a signing key mismatch: Android Gradle Plugin 8.7+ on Linux generates its default `debug` keystore at `$XDG_CONFIG_HOME/.android/debug.keystore` (`~/.config/.android/debug.keystore`) instead of `~/.android/debug.keystore`, which caused every fresh GitHub Actions runner to sign releases with a brand-new ephemeral certificate even when `~/.android/debug.keystore` was cached.
  - Added a permanent, deterministic PKCS#12 signing keystore (`app/signing/ftcloud-signing.p12`, SHA-256 `59:1B:F0:DF:...`) wired directly into `app/build.gradle.kts` across all build types and verified by `apksigner verify --print-certs` in CI on every build — guaranteeing that once `v1.2.6` is installed, **every future FT Cloud update upgrades seamlessly in-place forever** across all branches, runners, and cache resets.
  - Includes all `v1.2.5` improvements: single unified app identity (`com.ftcloud.app` / `FT Cloud`), clean public-repo **App Updates** card (no `"Private Repo"` button), and the true **Floating Glass Tab Dock** (`FTCloudFloatingDock`) overlaid over edge-to-edge scrolling screens.

## [1.2.5] - 2026-10-30

### Fixed & Improved
- **Single Unified App Build & Seamless In-Place APK Updates**:
  - Removed the separate `.debug` `applicationIdSuffix` (`com.ftcloud.app.debug` / `FT Cloud (Debug)`) and separate CI debug APK artifact so FT Cloud ships a **single unified package (`com.ftcloud.app`, `FT Cloud`)** signed with one consistent certificate (`ftcloud-signing-keystore-v1`) across every build.
  - Added `<queries>` package visibility for `com.ftcloud.app` on Android 11+ and automatic unauthenticated public-release fallback if any previously stored GitHub token is stale, ensuring one-tap in-place upgrades from `v1.2.4` onward with zero uninstalls.
- **Clean Public-Repo Update Checker UI**:
  - Removed the `"Private Repo"` button from **Settings → App Updates** now that `ft-sinan/FT-cloud` is Public on GitHub.
- **True Floating Glass Bottom Tab Dock**:
  - Replaced the clipped `Scaffold(bottomBar = ...)` layout in `FTCloudAppShell` with an edge-to-edge full-screen `Box` + `FTCloudFloatingDock` overlay (`VaultFloatingDock` style) with scroll-hide/reveal spring physics, glass translucency, and elevated FAB/list bottom clearances so files, photos, transfers, backup cards, and settings scroll smoothly *underneath* the floating pill.

## [1.2.4] - 2026-10-30

### Fixed & Improved
- **True Parallel 3+ Bot Swarm Execution Across Uploads, Scans & Downloads**:
  - **Parallel Multi-File Uploads & Downloads (`TransferWorker`)**: Queued uploads and downloads now run concurrently across `botCount` parallel workers (`Semaphore(botCount)`) instead of 1-by-1, and single-file multi-chunk concurrency scales up to 6 bots.
  - **Parallel Multi-Bot Channel History Scanner (`ChannelMediaImporter`)**: Removed the `stepWidth = 1` single-bot bottleneck so all 3+ bots scan channel messages concurrently; automatically propagates your Telegram `user_id` across all bots once `/start` is detected on any bot and picks up new `/start` activations live mid-scan.
  - **Atomic Bot Slot Reservation & Parallel Download Offloading (`MultiBotSwarmManager`)**: Fixed a concurrency race in `acquireBestBot()` via atomic `acquireBotSlot()` reservation, fixed `"chat not found"` unstarted-DM fallback so channel messages are never skipped, and added automatic idle-peer offloading on concurrent gallery/file downloads.
  - **Live Per-Bot Swarm Telemetry & 1-Tap `⚡ Start` Deep Links**: Added live `ops` / `active` / `⚡ Turbo DM` / amber cooldown indicators, multi-token paste support, and 1-tap `⚡ Start` buttons in **Settings → Telegram Bot Swarm** (`https://t.me/<bot>?start=ftcloud`).

## [1.2.3] - 2026-10-30

### Fixed & Improved
- **First-Time Login Cloud Index Restore & Multi-Bot Manifest Sync**:
  - Fixed a multi-bot swarm `file_id` mismatch where `fetchChatWithPinnedMessage()` could query the pinned `#ftcloud_manifest` with Bot B while `CloudManifestSync` downloaded its bot-scoped `file_id` with Bot A (`fetchChatWithBot()` now guarantees the exact same bot downloads the manifest).
  - Fixed `OnboardingScreen` unmounting before `finishConnectionWithAutoSync()` finished restoring the cloud index on initial login.
  - Added automatic recent-channel-tail manifest discovery (`findManifestInChannelTail`) + multi-bot pin fallback so existing `#ftcloud_manifest` snapshots are found even if a bot lacked `"Pin messages"` admin permission or another post was pinned later, plus `pinnedManifestMessageId` transfer in QR/PIN pairing payloads.
- **One-Tap Channel Scan & Live Progress Card on Drive & Photos**:
  - Added a **Quick Channel Import & Live Scan Card** directly on empty `Drive` and `Photos` screens so first-time channels with pre-existing Telegram media can start the one-time newest-first scan, import `result.json`, or restore an existing index in one tap without hunting in Settings.

## [1.2.2] - 2026-10-29

### Improved
- **Certificate Conflict Pre-Check & One-Time Migration Helper**:
  - `AppUpdateManager.inspectInstallCompatibility()` inspects the X.509 signing certificate of any installed `com.ftcloud.app` against the downloaded release APK *before* launching Android's package installer, catching `v1.0` / Android Studio / `.debug` signature conflicts cleanly instead of failing with Android's generic *"package conflicts with an existing package"* screen.
  - Automatically syncs your Pinned Cloud Manifest, saves the verified APK to `Downloads`, and provides a one-tap **Remove Old Package** action; separated launcher labels (`FT Cloud` vs `FT Cloud (Debug)`) and added `scripts/generate-release-keystore.sh` for permanent secret setup.

## [1.2.1] - 2026-10-29

### Fixed & Improved
- **In-App Updater Support for Private & Public GitHub Repositories**:
  - Explained why GitHub returns `HTTP 404` when `ft-sinan/FT-cloud` is set to **Private** (unauthenticated clients cannot view private repository releases).
  - Added optional **Private Repo Token** support (`Settings → App Updates → Private Repo`) stored in Android Keystore (and synced via QR/PIN device pairing) using GitHub REST API `Accept: application/octet-stream` asset downloads + safe cross-host redirect handling, so updates work seamlessly whether `ft-sinan/FT-cloud` is kept **Private** or switched to **Public**.

## [1.2.0] - 2026-10-29

### Added
- **Multi-Phone Cloud Sync (`CloudManifestSync` v2)**:
  - Use the same FT Cloud channel seamlessly across multiple phones and tablets without overwriting changes made on another device.
  - Automatic foreground delta-check & CRDT merge (`updatedAt` conflict resolution + deletion tombstones) plus a one-tap **Sync Across Devices** action in the Drive top bar and Settings.
  - **Zero Re-Scanning on New Phones / Reinstalls**: All imported channel media (`Imported from Channel`), folders, and stars are stored in your channel's **Pinned Cloud Manifest** and restored **automatically in ~1 second** during login — you never have to re-run the Background Bot Swarm History Scanner again.
- **Instant Second-Phone Login (QR Code & Encrypted 6-Digit PIN Pairing Key)**:
  - On Phone 1, open **Settings → Multi-Device & Second-Phone Login → Link a Second Phone** to display a scannable **QR Code** or copy/share an **AES-256-GCM encrypted Pairing Key (`FTCLOUD1:...`)** protected by a 6-digit PIN (safe to send via Telegram Saved Messages without exposing plaintext bot tokens).
  - On Phone 2's Onboarding screen, tap **Scan QR**, **Import QR Screenshot**, or **Pairing Key** to transfer your Channel ID, entire Bot Swarm, Turbo DM chat IDs, and Vault key in seconds.

### Changed
- **Restored Classic FT Cloud Launcher Icon & Emblem**: Brought back the original `#0A2540` navy + `#133B6E` wave + white cloud & `#0B6EFD` upload arrow adaptive icon while keeping the full Orion Material 3 Expressive UI theme.

## [1.1.0] - 2026-10-29

### Added
- **Signature FT / Orion Material 3 Expressive Design System**:
  - Custom **Space Grotesk** geometric display, headline, title, and label typography paired with expressive rounded shapes (`10dp`–`36dp`).
  - **Orion** signature brand palette (`#4C4ECD` / `#C0C1FF` indigo-violet + `#CCFE82` / `#CAFB80` electric lime accents) plus 8 curated brand palettes (`Teal`, `Navy`, `Emerald`, `Indigo`, `Ocean`, `Grape`, `Cocoa`, `Slate`), **OLED Pure Black** mode, and optional **Material You** wallpaper colors.
  - Tactile spring-physics interactions (`springPress` squash + haptic tap), animated `MorphingShapeBox` hero headers, `FTCloudShieldMark` brand emblem, and the floating glass pill bottom navigation dock.
  - Full look-and-feel customization in Settings: **Brand Palette** swatches, **Floating Glass Dock**, **Tactile Haptics**, **Compact Density**, and **Reduce Motion**.
- **In-App Updater & Seamless APK Installer**:
  - Built-in GitHub Releases update checker (`Settings → App Updates` + automatic once-per-24-hour foreground check) with semantic version comparison.
  - Live progress APK download, **SHA-256** checksum sidecar verification (`FTCloud-vX.Y.Z.apk.sha256`), Android unknown-sources permission guidance, and one-tap `FileProvider` package installation.
  - Persistent APK signing pipeline (`FTCLOUD_RELEASE_*` / `MYVAULT_RELEASE_*` keystore support with cached 10,000-day keystore fallback) so updates install cleanly in-place over existing installs without losing settings or local cloud index data.
- **FT Franchise Adaptive Launcher Icon**:
  - Updated launcher mark matching the FT Vault design family (`#173F35` deep forest shield, `#F2EFE3` crest, `FT` geometric monogram, and `#C8F169` electric lime cloud accent).

## [1.0.0] - 2026-10-29

### Added
- **Multi-Bot Swarm Storage Engine**: Parallel `19 MiB` auto-chunking, `FloodWait` bot rotation, SHA-256 deduplication, and cross-bot `file_id` recovery.
- **Clean Cloud Drive, Gallery & Media3 Streaming**: Virtual folder hierarchy, date-grouped Photos & Videos timeline, loopback `HTTP 206` `TelegramStreamProxy` for instant ExoPlayer video/audio scrubbing, native PDF viewer, and text/Markdown preview.
- **WorkManager Auto-Backup & Pinned Cloud Manifest**: Automatic Camera Roll & SAF folder backups plus GZIP/AES-256-GCM encrypted manifest snapshots pinned directly in your Telegram channel.
- **7,000+ Pre-Existing Channel Media Importer**: Instant Telegram Desktop `result.json` metadata import, forwarded-batch `getUpdates` sync, and resumable newest-first `WorkManager` history scanner.
