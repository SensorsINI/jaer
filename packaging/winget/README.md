# winget (Windows)

[winget](https://learn.microsoft.com/en-us/windows/package-manager/winget/) is Microsoft’s Windows package manager. The public catalog is [microsoft/winget-pkgs](https://github.com/microsoft/winget-pkgs). You do not upload the ~200 MB exe there. You open a PR with three small YAML files in this folder. Microsoft’s bots download the `InstallerUrl` and check SHA256; reviewers merge. Only then can anyone run:

```text
winget install SensorsINI.jAER
winget upgrade SensorsINI.jAER
```

Package identifier: `SensorsINI.jAER` (x64 only, install4j media id 26). Folder in winget-pkgs: `manifests/s/SensorsINI/jAER/<version>/`.

## Do not submit until Latest 3.5.2

YAML here is a **template**. `InstallerSha256` is a placeholder of 64 zeros. `InstallerUrl` points at the **public** tag `3.5.2`, not `3.5.2-rc.0`.

The public tag **rebuilds** the exe ([`docs/README-releasing-tagging.md`](../../docs/README-releasing-tagging.md)). Filenames stay `jAER_windows-x64_3_5_2.exe` (`VERSION.txt` is `3.5.2`), but the bytes and SHA256 change. A PR that hashes the rc will fail the day Latest is published.

Gate: [GitHub Latest](https://github.com/SensorsINI/jaer/releases/latest) is `3.5.2`, and the exe Properties → Digital Signatures → **Tobias Delbruck** ([Azure Artifact Signing](../azure-artifact-signing.md)).

## Publisher

YAML `Publisher` is **Tobias Delbruck**, matching Azure Public Trust on the signed exe. Do not use Sensors Group or SignPath Foundation.

That string may differ from install4j’s company field (`Sensors Group - Inst. of Neuroinformatics…` in `install4j/jaer.install4j`), which is what Apps & Features often shows. After one real install of the Latest exe, if ARP Publisher or DisplayName disagree with the YAML, add `AppsAndFeaturesEntries` so `winget upgrade` still matches. Do not invent a publisher that disagrees with the signed exe.

## Fill SHA256 (after Latest)

```text
gh release download 3.5.2 -p "jAER_windows-x64_3_5_2.exe"
Get-FileHash -Algorithm SHA256 .\jAER_windows-x64_3_5_2.exe
```

Paste the hash into `3.5.2/SensorsINI.jAER.installer.yaml`. Commit in this repo first. Pin the URL at `/releases/download/3.5.2/…`, not `/releases/latest/download/` and not Dropbox.

Optional `ReleaseDate` (`YYYY-MM-DD`) can be added then.

## Validate on this machine (no GitHub PR)

```text
winget validate packaging\winget\3.5.2
```

That only checks YAML schema. It does **not** download the exe. Placeholder SHA256 is schema-valid; do not `wingetcreate submit` until the hash is from Latest.

`winget install --manifest packaging\winget\3.5.2` downloads the URL, verifies SHA256, and launches install4j. That will fail while SHA256 is zeros, or if Latest is not 3.5.2 yet.

Optional rc smoke test: copy the folder aside, point `InstallerUrl` at `…/download/3.5.2-rc.0/jAER_windows-x64_3_5_2.exe`, fill **that** file’s SHA256, then `winget install --manifest …`. Throw the copy away. Do not commit rc URLs.

## First publish to microsoft/winget-pkgs

`wingetcreate submit` opens a PR; it does not merge. You need a Microsoft CLA. Bots fail the PR if SHA256 ≠ downloaded bytes. Reviewers can take days. After merge: `winget source update` then `winget install SensorsINI.jAER`.

```text
winget install Microsoft.WingetCreate
wingetcreate submit packaging\winget\3.5.2
```

Or a manual PR:

1. Fork https://github.com/microsoft/winget-pkgs
2. Copy the three YAML files to `manifests/s/SensorsINI/jAER/3.5.2/`
3. Open a PR against `microsoft/winget-pkgs` (`master`)

Do not replace GitHub 3.5.2 assets after submit without a new SHA256 and a new PR.

## Later public versions

```text
wingetcreate update SensorsINI.jAER --urls https://github.com/SensorsINI/jaer/releases/download/<ver>/jAER_windows-x64_<ver_underscores>.exe --version <ver> --submit
```

Silent switches are install4j `-q` / splash. The first 3.5.2 winget install does not write `.jaer-packaged-install` (no winget post-install script). Homebrew does. Follow-up: an install4j flag, which needs a later public media build.
