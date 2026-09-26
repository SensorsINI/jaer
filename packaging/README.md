# jAER distribution packaging

Installers are **GitHub Release assets** (`jAER_windows-x64_*.exe`, Intel `jAER_macos_*.dmg`, Apple Silicon `jAER_macos_aarch64_*.dmg` from media id 39, `jAER_unix_*.sh`, and from 3.6.0 `jAER_linux-amd64_*.deb`). Each file is ~200 MB (bundled [Adoptium](https://adoptium.net/) Temurin 25; per-OS OpenCV), which is within GitHub’s 2 GiB-per-asset limit. Keep binaries for the latest 2–3 releases; prune older assets with `scripts/prune-old-release-assets.ps1`. The install4j project is [`../install4j/jaer.install4j`](../install4j/jaer.install4j). Release steps: [`../docs/README-releasing-tagging.md`](../docs/README-releasing-tagging.md). Windows Authenticode: [Azure Artifact Signing](azure-artifact-signing.md) (Public Trust, publisher **Tobias Delbruck**; `ant azure-sign-ci`). SignPath is a backup.

| Channel | Status | Details |
|---------|--------|---------|
| GitHub Releases + in-app updater | Primary | install4j standalone update downloader (`updater`) |
| [winget](winget/) | Templates only (3.5.2 YAML, SHA256 TBD) | First `microsoft/winget-pkgs` PR after GitHub **Latest 3.6.0** (Azure-signed exe). Not for 3.5.2. Publisher **Tobias Delbruck**. Identifier `SensorsINI.jAER`. |
| [Homebrew cask](homebrew/) | Cask template only | First public tap `SensorsINI/homebrew-jaer` after Latest **3.6.0** (notarized DMGs). Not for 3.5.2. Confirm `.app` path on a Mac. Later: `homebrew/cask`. |
| Linux `.deb` | CI media; first release asset **3.6.0** | [deb/](deb/) — `wget` + `sudo apt install ./file.deb`. Keep the Unix `.sh`. No apt repo. |
| macOS notarization | GitHub Actions (`macos-latest`) | [macos-notarization.md](macos-notarization.md) — Environment `macos-notarize`; Mini is fallback |

Do **not** submit winget YAML or create `SensorsINI/homebrew-jaer` for 3.5.2 or against any `-rc` tag. First catalog submit is public **3.6.0**. The public tag rebuilds media, so SHA256 from an rc would break the day Latest is published.

Package-manager installs drop a marker file named `.jaer-packaged-install` in the jAER installation directory so the in-app **Download and install** button is hidden. Homebrew `postflight` writes it. An unattended install4j run (winget `--silent`) writes it too. The `.deb` postinst writes it under `/opt/jAER`. Those users run `winget upgrade SensorsINI.jAER`, `brew upgrade --cask jaer`, or install a newer `.deb`.
