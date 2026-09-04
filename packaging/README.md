# jAER distribution packaging

Installers are **GitHub Release assets** (`jAER_windows-x64_*.exe`, Intel `jAER_macos_*.dmg`, Apple Silicon `jAER_macos_aarch64_*.dmg` from media id 39, `jAER_unix_*.sh`). Each file is ~220–230 MB for 3.3.0 (bundled [Adoptium](https://adoptium.net/) Temurin 25; per-OS OpenCV), which is within GitHub’s 2 GiB-per-asset limit. Keep binaries for the latest 2–3 releases; prune older assets with `scripts/prune-old-release-assets.ps1`. The install4j project is [`../install4j/jaer.install4j`](../install4j/jaer.install4j). Release steps: [`../docs/README-releasing-tagging.md`](../docs/README-releasing-tagging.md). Local SignPath credentials and the tracked artifact XML live in [`signpath/`](signpath/).

| Channel | Status | Details |
|---------|--------|---------|
| GitHub Releases + in-app updater | Primary | install4j standalone update downloader (`updater`) |
| [winget](winget/) | YAML ready for 3.2.0; hold winget-pkgs PR | `SensorsINI.jAER` — SHA256 filled; first public package is signed **3.3.0**; `wingetcreate submit` when that media is on GitHub |
| [Homebrew cask](homebrew/) | Cask ready (Intel + Apple Silicon) | Confirm DMG `.app` path on a Mac; publish `SensorsINI/homebrew-jaer` when ready; later `homebrew/cask` |
| Linux apt / `.deb` | Optional later | [deb/](deb/) — USB cameras need a normal OS process, not snap/flatpak |
| macOS notarization | Individual enrollment in progress | [macos-notarization.md](macos-notarization.md) |

Package-manager installs should drop a marker file named `.jaer-packaged-install` in the jAER installation directory so the in-app **Download and install** button is hidden. Those users run `winget upgrade SensorsINI.jAER` or `brew upgrade --cask jaer`.
