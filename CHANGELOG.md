# Changelog

All notable changes to **FT Cloud** are documented in this file.

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
