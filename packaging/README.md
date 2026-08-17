# jAER distribution packaging

Installers are **GitHub Release assets** (`jAER_windows-x64_*.exe`, Intel `jAER_macos_*.dmg`, Apple Silicon `jAER_macos_aarch64_*.dmg` from media id 39, `jAER_unix_*.sh`). Each file is ~300 MB (bundled Temurin 21), which is within GitHub’s 2 GiB-per-asset limit. Keep binaries for the latest 2–3 releases; prune older assets with `scripts/prune-old-release-assets.ps1`.

| Channel | Status | Details |
|---------|--------|---------|
| GitHub Releases + in-app updater | Primary | install4j standalone update downloader (`updater`) |
| [winget](winget/) | Submit after assets exist | `microsoft/winget-pkgs` manifests |
| [Homebrew cask](homebrew/) | Own tap first | `SensorsINI/homebrew-jaer`; later `homebrew/cask` |
| Linux apt / `.deb` | Optional later | [deb/](deb/) — USB cameras need a normal OS process, not snap/flatpak |
| macOS notarization | Optional later | [macos-notarization.md](macos-notarization.md) |

Package-manager installs should drop a marker file named `.jaer-packaged-install` in the jAER installation directory so the in-app **Download and install** button is hidden. Those users run `winget upgrade SensorsINI.jAER` or `brew upgrade --cask jaer`.
