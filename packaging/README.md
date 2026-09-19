# jAER distribution packaging

Installers are **GitHub Release assets** (`jAER_windows-x64_*.exe`, Intel `jAER_macos_*.dmg`, Apple Silicon `jAER_macos_aarch64_*.dmg` from media id 39, `jAER_unix_*.sh`). Each file is ~200 MB (bundled [Adoptium](https://adoptium.net/) Temurin 25; per-OS OpenCV), which is within GitHub’s 2 GiB-per-asset limit. Keep binaries for the latest 2–3 releases; prune older assets with `scripts/prune-old-release-assets.ps1`. The install4j project is [`../install4j/jaer.install4j`](../install4j/jaer.install4j). Release steps: [`../docs/README-releasing-tagging.md`](../docs/README-releasing-tagging.md). Windows Authenticode: [Azure Artifact Signing](azure-artifact-signing.md) (Public Trust, publisher **Tobias Delbruck**; `ant azure-sign-ci`). SignPath is a backup.

| Channel | Status | Details |
|---------|--------|---------|
| GitHub Releases + in-app updater | Primary | install4j standalone update downloader (`updater`) |
| [winget](winget/) | Templates for 3.5.2; SHA256 TBD | First `microsoft/winget-pkgs` PR only after GitHub **Latest** 3.5.2 (Azure-signed exe). Publisher **Tobias Delbruck**. Identifier `SensorsINI.jAER`. |
| [Homebrew cask](homebrew/) | Cask template for 3.5.2; SHA256 TBD | First public tap `SensorsINI/homebrew-jaer` only after Latest 3.5.2 (notarized DMGs). Confirm `.app` path on a Mac. Later: `homebrew/cask`. |
| Linux apt / `.deb` | Optional later | [deb/](deb/) — USB cameras need a normal OS process, not snap/flatpak |
| macOS notarization | GitHub Actions (`macos-latest`) | [macos-notarization.md](macos-notarization.md) — Environment `macos-notarize`; Mini is fallback |

Do **not** submit winget YAML or create `SensorsINI/homebrew-jaer` against a `-rc` tag. The public tag rebuilds media, so SHA256 changes.

Package-manager installs should drop a marker file named `.jaer-packaged-install` in the jAER installation directory so the in-app **Download and install** button is hidden. Homebrew `postflight` already writes it. Winget has no post-install script yet (follow-up: install4j switch). Those users run `winget upgrade SensorsINI.jAER` or `brew upgrade --cask jaer`.
