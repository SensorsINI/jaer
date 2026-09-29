<!--
  Paste-ready for GitHub Releases. Image links use raw.githubusercontent.com so they
  render in the Release body. Relative paths work in the repo, not in a Release description.

  Public 3.5.3 notes. Packaging point release: unattended installers.
  The application is the 3.5.2 line. Do not paste the OS how-to; link the install guide.
-->

**jAER 3.5.3** is a packaging point release after **[3.5.2](https://github.com/SensorsINI/jaer/releases/tag/3.5.2)**. The application is the 3.5.2 line. This release adds unattended installers: a Linux `.deb`, and the files that winget and Homebrew will install once those catalogs are published.

## Download

| You have | CPU | Download |
|---|---|---|
| Windows 10 / 11 | x64 | [jAER_windows-x64_3_5_3.exe](https://github.com/SensorsINI/jaer/releases/download/3.5.3/jAER_windows-x64_3_5_3.exe) |
| macOS | Apple Silicon (M1–M4) | [jAER_macos_aarch64_3_5_3.dmg](https://github.com/SensorsINI/jaer/releases/download/3.5.3/jAER_macos_aarch64_3_5_3.dmg) |
| macOS | Intel | [jAER_macos_3_5_3.dmg](https://github.com/SensorsINI/jaer/releases/download/3.5.3/jAER_macos_3_5_3.dmg) |
| Linux | x64 | [jAER_unix_3_5_3.sh](https://github.com/SensorsINI/jaer/releases/download/3.5.3/jAER_unix_3_5_3.sh) |
| Linux | x64 `.deb` | [jAER_linux-amd64_3_5_3.deb](https://github.com/SensorsINI/jaer/releases/download/3.5.3/jAER_linux-amd64_3_5_3.deb) |
| Any OS | Sample data (~995 MB) | [jaer-sample-data.zip](https://github.com/SensorsINI/jaer/releases/download/3.5.3/jaer-sample-data.zip) ([README](https://github.com/SensorsINI/jaer/blob/master/jaerSampleData/README.md)) |

Each installer includes a bundled [Eclipse Temurin](https://adoptium.net/) JDK 25. You do not install Java yourself. GitHub lists the same files again under **Assets**.

Steps for Windows, macOS, and Linux are on the **[Install Guide](https://jaerproject.org/install/)**. The homepage Download button stays the usual installer (the Linux button is still the `.sh`).

The `.deb` installs into `/opt/jAER` and adds a GNOME menu entry named **jAER**. Start it from that menu or with `/opt/jAER/jaer`. There is no `apt install jaer` by name.

`winget install SensorsINI.jAER` and `brew install --cask jaer` work after the winget catalog PR is merged and the Homebrew tap `sensorsini/jaer` exists. Until then, use the downloads above.

jAER can self-update from the `.exe`, `.dmg`, and `.sh` (Help → Check for release updates… → **Download and install**). A `.deb`, winget, or Homebrew install hides that button; upgrade those with a newer `.deb`, `winget upgrade SensorsINI.jAER`, or `brew upgrade --cask jaer`.
