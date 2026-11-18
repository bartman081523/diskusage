# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [5.0.0] - 2026-11-18

Complete modernization of the Android build and the app itself.

### Changed

* Build toolchain: AGP 9.5.1, Gradle 9.9.1, built-in Kotlin, JDK 17, version
  catalog, R8 with resource shrinking, configuration cache.
* `compileSdk`/`targetSdk` 37 (Android 17), `minSdk` 24 (Android 7.0).
* NDK r28c, native scanner rebuilt for all four ABIs with 16 KB page-size
  alignment.
* UI migrated to AppCompat activities with real toolbars, DayNight theme and
  edge-to-edge insets (required from Android 15/16 on).
* Volume discovery uses `StorageManager.getStorageVolumes()`; SD cards and USB
  drives are listed with their user-visible description.
* App storage list is built from the package manager instead of
  `UsageStatsManager`; cache sizes are reported again.
* Delete confirmation lists content on a worker thread instead of the UI thread.
* `PermissionRequestActivity` re-checks permissions in `onResume` and only
  requests what the running Android version needs.
* Timestamps and version information come from `version.properties`.

### Added

* Automatic GitHub releases (tag + APK + SHA256) and a daily nightly prerelease.
* CI with unit tests, lint, debug APK artifact and a 16 KB page-size check.
* F-Droid metadata (`fastlane/`), icon, feature graphic, screenshots and
  changelogs in English and German; submission guide in `docs/fdroid`.
* Adaptive, round and monochrome (themed) launcher icons, TV banner and leanback
  launcher entry, per-app language configuration.
* Used/free space is shown with the occupied percentage.
* Double tap opens files and folders.
* Unit tests for volume path handling.

### Fixed

* The native scanner never worked on modern Android: the shared library was
  executed as a child process (segfault), the protocol had a stale leading NUL
  byte and the build was missing the JNI entry point. It now runs in-process via
  JNI, so scanning is fast again; failures still fall back to the Java scanner
  (`Error: no more data`, `Cannot run program …libscan.so`).
* "No viewer found" and wrong MIME types when opening files; content URIs now
  carry explicit read grants.
* Scan worker could leave the progress dialog on screen forever.
* Missing internal storage / SD card / USB volumes in the chooser.
* Apps without recent usage statistics disappeared from the app list.
* NPE when the option menu updated without a selection.
* Automatic selection when only one volume is available.

[Unreleased]: https://github.com/bartman081523/diskusage/compare/v5.0.0...HEAD
[5.0.0]: https://github.com/bartman081523/diskusage/releases/tag/v5.0.0
