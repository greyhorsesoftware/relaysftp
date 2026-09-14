# RelaySFTP

Cross platform FTP/SFTP/S3 desktop client.

This project is a revival of a 20-year-old codebase (formerly GideonFTP), brought back mostly for fun. The original application was abandoned due to its reliance on now-defunct technologies such as Foam. At the time, a full rewrite wasn’t feasible given the effort involved.

With modern tooling—especially Claude Code—I was able to modernize the codebase and restore functionality in just a few hours. This repository represents that revived and updated version.

## Protocols

- **FTP/FTPS** 
- **SFTP** 
- **S3 / S3-compatible** (AWS S3, MinIO, Backblaze B2, DigitalOcean Spaces, Cloudflare R2)

## Features

- Dual-pane browser with drag-and-drop transfers
- Bookmark manager with favorite paths per bookmark
- Background job queue for file operations
- Transfer filters (skip files by name/type)
- One-way folder mirroring (local ↔ remote): preview the plan, then run it as a queued job with per-file progress, parallel copies and optional deletion of extras
- Built-in text editor with syntax highlighting
- Native OS keychain integration for password storage (macOS Keychain, Windows Credential Manager, Linux Secret Service)
- FlatLaf modern look-and-feel

## Download

Signed installers for macOS, Windows and Linux are on the
[RelaySFTP page](https://www.greyhorsesoftware.com/relaysftp.html). RelaySFTP is donationware.

The CI in this repository builds an unsigned jar from source; use that if you would rather
build it yourself.

## Build & Run

Requires Java 21+ (the build uses a Java 25 toolchain).

```sh
./gradlew clean build -x test
java -jar build/libs/RelaySFTP.jar
```

## Data Files

Stored in the app's data folder:

| Platform | Folder |
|----------|--------|
| macOS | `~/Library/Application Support/RelaySFTP/` |
| Windows | `%USERPROFILE%\RelaySFTP\` |
| Linux | `~/.relaysftp/` |

Override it for development with `-Drelaysftp.dataDir=<path>` or the `RELAYSFTP_DATA_DIR` environment variable (`./gradlew run` uses `build/dev-data`).

| File | Contents |
|------|----------|
| `Bookmarks/<name>.relay` | One JSON file per saved connection, with its nested favorite paths |
| `TransferFilters.json` | File transfer skip rules |
| `LocalFavoritePaths.json` | Local browser favorite directories |

Passwords and S3 secret keys are never written to these files. They are kept in the platform's native keychain (macOS Keychain, Windows Credential Manager, Linux Secret Service) under the `RelaySFTP` service entry, keyed by host, port and user. If the keychain is unavailable, passwords entered during a session are held in memory only and must be re-entered next time.

## License

Copyright Grey Horse Software. Licensed under the Apache License, Version 2.0; see
[LICENSE](LICENSE). Bundled third-party libraries are listed in
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
