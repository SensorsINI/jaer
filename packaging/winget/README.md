# winget (Windows)

[winget](https://learn.microsoft.com/en-us/windows/package-manager/winget/) is Microsoft’s Windows package manager. The public catalog is [microsoft/winget-pkgs](https://github.com/microsoft/winget-pkgs). You do not upload the ~200 MB exe there. You open a PR with three small YAML files in this folder. Microsoft’s bots download the `InstallerUrl` and check SHA256; reviewers merge. Only then can anyone run:

```text
winget install SensorsINI.jAER
winget upgrade SensorsINI.jAER
```

Package identifier: `SensorsINI.jAER` (x64 only, install4j media id 26). Folder in winget-pkgs: `manifests/s/SensorsINI/jAER/<version>/`.

## Schema 1.28.0, first catalog version is Latest

`winget validate` on the Windows client still accepts manifest schema **1.6.0**. The winget-pkgs repository does not. Review of [PR 442111](https://github.com/microsoft/winget-pkgs/pull/442111) requires `ManifestVersion: 1.28.0`. WingetCreate 1.12.13 still writes 1.6.0, so set 1.28.0 by hand and run `winget validate --manifest` before updating the PR.

The open PR started at **3.5.3**. GitHub Latest is now a later public tag. Point the PR at that Latest exe (not an rc, and not `VERSION.txt` if it is ahead of Latest). `InstallerSha256` in `3.5.2/` stays 64 zeros.

The public tag **rebuilds** the exe ([`docs/README-releasing-tagging.md`](../../docs/README-releasing-tagging.md)). A PR that hashes an rc will fail the day Latest is published.

Gate: [GitHub Latest](https://github.com/SensorsINI/jaer/releases/latest) is the public **3.5.3** tag, and the exe Properties → Digital Signatures → **Tobias Delbruck** ([Azure Artifact Signing](../azure-artifact-signing.md)).

## Publisher

YAML `Publisher` is **Tobias Delbruck**, matching Azure Public Trust on the signed exe. Do not use Sensors Group or SignPath Foundation.

That string may differ from install4j’s company field (`Sensors Group - Inst. of Neuroinformatics…` in `install4j/jaer.install4j`), which is what Apps & Features often shows. After one real install of the Latest exe, if ARP Publisher or DisplayName disagree with the YAML, add `AppsAndFeaturesEntries` so `winget upgrade` still matches. Do not invent a publisher that disagrees with the signed exe.

## Fill SHA256 (after the public Latest tag)

```text
gh release download <tag> -p "jAER_windows-x64_<underscores>.exe"
Get-FileHash -Algorithm SHA256 .\jAER_windows-x64_<underscores>.exe
```

Copy `3.5.2/` to a `<version>/` folder, set `ManifestVersion: 1.28.0` in all three YAML files, and paste the hash into `SensorsINI.jAER.installer.yaml`. Commit in this repo first. Pin the URL at `/releases/download/<tag>/…`, not `/releases/latest/download/` and not Dropbox.

## Validate on this machine (no GitHub PR)

```text
winget validate --manifest packaging\winget\3.6.2
```

That checks YAML against the client schema. It does **not** download the exe, and it still accepts deprecated `1.6.0`. The winget-pkgs policy check is stricter: `ManifestVersion` must be `1.28.0`. Placeholder SHA256 is schema-valid; do not submit until the hash is from the public Latest tag.

`winget install --manifest packaging\winget\3.5.2` fails while SHA256 is zeros.

## Update the open pull request

[PR 442111](https://github.com/microsoft/winget-pkgs/pull/442111) is the first package. Push schema and version fixes to that branch. Do not open a second new-package PR. `wingetcreate submit` writes `ManifestVersion: 1.6.0` (WingetCreate 1.12.13); do not let it replace a 1.28.0 manifest.

Do not replace the GitHub release assets named in the PR after the bots have hashed them, without a new SHA256 and a new commit.

## Later public versions

`wingetcreate update` still emits schema 1.6.0. After it writes the files, set `ManifestVersion: 1.28.0` in each YAML file and `winget validate --manifest` before pushing.

Silent and silent-with-progress both pass install4j `-q`. `-splash` is only valid together with `-q`; a splash title alone leaves the GUI waiting, which fails the winget unattended check. An unattended install (`context.isUnattended()`, including winget `--silent`) writes `.jaer-packaged-install` in the install directory. Homebrew `postflight` writes the same marker. Path heuristics will not match `C:\Program Files\jAER`.
