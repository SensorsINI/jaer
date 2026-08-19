# winget (Windows)

Package identifier: `SensorsINI.jAER` (x64 only, install4j media id 26).

3.2.0 YAML in this folder already has the GitHub Release URL and SHA256 from `updates.xml`. Publisher stays **Sensors Group, Inst. of Neuroinformatics, UZH-ETH Zurich** until SignPath **release-signing** is VALID, then change it to **SignPath Foundation**.

## Validate on this machine (no GitHub PR yet)

```text
winget validate packaging\winget\3.2.0
winget install --manifest packaging\winget\3.2.0
```

`winget validate` only checks YAML schema. `--manifest` install is optional: it downloads the ~300 MB GitHub exe, verifies SHA256, and launches install4j. Cancelled install → winget exit code 1. SmartScreen **Unknown publisher** until SignPath.

## First publish to microsoft/winget-pkgs

Hold this until **3.3.0** signed media is the intended public winget package. `wingetcreate submit` opens a PR; it does not merge `microsoft/winget-pkgs`. Do not replace GitHub 3.2.0 assets after submit without a new SHA256.

`gh` and `wingetcreate` are installed on the Windows machine that prepared these YAML files.

When ready:

```text
winget install Microsoft.WingetCreate
wingetcreate submit packaging\winget\3.2.0
```

or a manual PR:

1. Fork https://github.com/microsoft/winget-pkgs
2. Copy the three YAML files to `manifests/s/sensorsini/jAER/3.2.0/` (publisher folder is lowercase `s` + `SensorsINI` — check current winget-pkgs convention: `manifests/s/SensorsINI/jAER/3.2.0/`)
3. Open a PR against `microsoft/winget-pkgs` (`master`)

Each later release:

```text
wingetcreate update SensorsINI.jAER --urls https://github.com/SensorsINI/jaer/releases/download/<ver>/jAER_windows-x64_<ver_underscores>.exe --version <ver>
```

Users (after the PR merges):

```text
winget install SensorsINI.jAER
winget upgrade SensorsINI.jAER
```

Do not point winget at Dropbox. Do not invent a publisher name that disagrees with the signed exe.
