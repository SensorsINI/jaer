# winget (Windows)

Submit to [microsoft/winget-pkgs](https://github.com/microsoft/winget-pkgs) **after** the Windows installer is a GitHub Release asset and you have its SHA256.

Package identifier: `SensorsINI.jAER` (x64 only, install4j media id 26).

## First publish

1. Download `jAER_windows-x64_<version_with_underscores>.exe` from the GitHub Release (prefer the SignPath-signed build).
2. `Get-FileHash -Algorithm SHA256 path\to\jAER_windows-x64_3_2_0.exe`
3. Copy the YAML files in this folder; set `PackageVersion`, installer URL, and `InstallerSha256`.
4. Publisher:
   - Until SignPath **release-signing** is VALID: `Sensors Group, Inst. of Neuroinformatics, UZH-ETH Zurich`
   - After release-signing: `SignPath Foundation` (must match the Authenticode publisher)
5. Install with [wingetcreate](https://github.com/microsoft/winget-create):

```text
wingetcreate new https://github.com/SensorsINI/jaer/releases/download/3.2.0/jAER_windows-x64_3_2_0.exe
```

Or submit a PR under `manifests/s/SensorsINI/jAER/<version>/`.

install4j silent flags used in the template: `-q` (unattended). Progress: `-splash "Installing jAER"`.

## Each later release

```text
wingetcreate update SensorsINI.jAER --urls https://github.com/SensorsINI/jaer/releases/download/<ver>/jAER_windows-x64_<ver_underscores>.exe --version <ver>
```

Users:

```text
winget install SensorsINI.jAER
winget upgrade SensorsINI.jAER
```

Do not point winget at Dropbox. Do not invent a publisher name that disagrees with the signed exe.
