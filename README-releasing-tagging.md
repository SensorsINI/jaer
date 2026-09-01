# Releasing jAER

Two URLs, two hosts. Do not follow install4j's "upload updates.xml and media to the same directory" hint.

| What | Where | Who writes it |
|------|--------|----------------|
| Update descriptor | `https://raw.githubusercontent.com/SensorsINI/jaer/master/updates.xml` | git: commit and push repo-root `updates.xml` |
| Installer binaries | `https://github.com/SensorsINI/jaer/releases/latest/download/<fileName>` | `scripts/upload-github-release-installers.ps1` / `.sh` |

`updates.xml` `baseUrl` must be `https://github.com/SensorsINI/jaer/releases/latest/download/`. `ant copy-updates-xml` sets that; do not edit it by hand. The in-app checker reads the raw GitHub file, then downloads `baseUrl` + `fileName` (for example `jAER_windows-x64_3_2_0.exe`).

`/releases/latest/` is whichever GitHub Release is marked Latest (usually the newest published non-prerelease). The 3.2.0 media must be attached to a published Release named `3.2.0` or `/latest/download/` still serves 3.1.0.

## Checklist (do in this order)

1. Set `VERSION.txt` (e.g. `3.2.0`).
2. `ant release` -- Enter accepts the default `y` (type `n` to cancel). Does **not** copy repo-root `updates.xml`.
   - Media lands in `currentInstallers/<VERSION.txt>/`. Historical Dropbox copies stay in `jaer-older-installers/` (same share URL as the old `installers/` folder).
3. Upload binaries (creates the GitHub Release for that tag if it is missing):

       powershell -File scripts/upload-github-release-installers.ps1
       bash scripts/upload-github-release-installers.sh

   Dry run: `-WhatIf` (PowerShell and bash) or `--what-if` (bash).
   Re-upload after a rebuild: same command (`--clobber`).
   Release body comes from `release-notes/jaer-<VERSION>-release-notes.md` (`--notes-file`).
   Put the download table and concise OS notes at the **top** (see 3.2.0 notes). GitHub
   always appends **Assets** at the bottom of the Release page — do not duplicate a long
   installer section there. Update version in filenames (`3_2_0` / tag `3.2.0`).
   Notes only:

       ant upload-release-notes
       gh release edit 3.2.0 --notes-file release-notes/jaer-3.2.0-release-notes.md
4. When the release is ready to publish: `ant copy-updates-xml` (overwrites repo-root `updates.xml` and sets `baseUrl` to GitHub `/latest/download/`). Commit and push `updates.xml`. Installed copies only see `master`.
5. Point git tag `<VERSION.txt>` at the commit you want and push it (`git tag` / `git push origin <tag>`). If the tag already exists on an older commit, delete and recreate it (see Tagging).
6. Optional later: SignPath-signed Windows exe, winget/Homebrew, prune old assets.
7. Download counts (GitHub per-asset `download_count`; needs `gh auth`):

       ant count-asset-downloads
       ant count-asset-downloads -Djaer.asset.downloads.tag=3.3.0
       powershell -File scripts/count-asset-downloads.ps1
       bash scripts/count-asset-downloads.sh --tag 3.3.0

   Default is installer media (`jAER_*.exe` / `.dmg` / `.sh`) on the newest 50 published releases.
   All files: `-Djaer.asset.downloads.all=true` or `--all`. Limit: `-Djaer.asset.downloads.limit=20`.

After a rebuild, hashes in `updates.xml` change. Repeat `ant copy-updates-xml`, upload, and push `updates.xml` or the updater will checksum-fail.

## Version (VERSION.txt)

`VERSION.txt` at the repo root is the single source of truth. It drives:

- install4j application version (synced into `install4j/jaer.install4j`; also `install4jc --release=...`)
- splash overlay text (full `VERSION.txt`, e.g. 3.2.0) on generated 1024 / 256 / 800 PNGs
- About / `BUILDVERSION.txt` first line on jar build

See https://github.com/SensorsINI/jaer/releases and https://github.com/SensorsINI/jaer/tags .

## Install4j build (`ant release`)

Prerequisites:

1. install4j on PATH (`install4jc`) -- https://www.ej-technologies.com/resources/install4j/v/13.0/help/doc/cli/compiler.html
2. License (local: `install4j/license.txt`, gitignored; fallback `signpath/install4j-license.txt`)
3. `VERSION.txt` set
4. `images/SplashScreen.png` is the text-free 1024x1024 base art (`images/SplashScreen.pdf` when the art changes)

    ant release

On Enter / `y` / `yes` it: generates splash PNGs (`images/1024w`, `images/256h`, `images/800w`), syncs `install4j/jaer.install4j` version, `clean` + `jar`, then `install4jc --release=<VERSION.txt> install4j/jaer.install4j`. It does not copy repo-root `updates.xml`; run `ant copy-updates-xml` when the release is ready to publish.

Splash only: `ant generate-splash`. The install4j launcher splash is the **800×800** PNG (`images/800w`). Keep **256h** for Windows / wizard icons and **1024w** for macOS icns. Details: [`install4j/README.md`](install4j/README.md).

TensorFlow for MLPNoiseFilter (two layers):
- Ivy (lib/ for compile & ant release tree): tensorflow-core-api + unclassified
  tensorflow-core-native stub, plus org.bytedeco:javacpp:1.5.10 (TF requires this; do not
  leave javacpp-1.4 from hdf5 on the classpath). Not tensorflow-core-platform.
- install4j: still lists the large OS classifier jars under dirEntry excludes as a safety net
  so they never enter media even if present in lib/. On first MLPNoiseFilter use,
  TensorFlowNativeSupport downloads the current-OS jar into lib/ or ~/.jaer/lib/.
  Air-gapped: copy tensorflow-core-native-1.0.0-rc.2-<platform>.jar into lib/ manually.
  Also ensure lib/javacpp-1.5.10.jar is present (and javacpp-1.4.jar is not).
  Upgrading over an older install can leave javacpp-1.4.jar and OS TF native jars in
  lib/; install4j now deletes those leftovers after InstallFiles. Until then, delete
  lib/javacpp-1.4.jar manually (it sorts before 1.5.10 and breaks TensorFlow Loader).
- Media excludes: tmp/, src/, scripts/, logs/, bin/, tools/ (tmp alone can be hundreds of MB
  of local scratch and must not ship in installers).
- OpenCV: Ivy keeps the openpnp fat jar (`opencv-4.8.1-0.jar`, ~102MB, all OS natives) in
  `lib/` for compile and `ant run`. `ant release` runs `split-opencv-natives` and each
  install4j media fileset packs only that OS's slim jar (same filename under `lib/`).
  Newer openpnp 4.9.0-0 is still a fat jar; bytedeco classifiers are a different Java API.
  Standalone: `ant split-opencv-natives`. Slim output is `build/opencv-slim/<platform>/lib/`.

## Fallback: install4j GUI (config changes / dry run)

Use the install4j IDE when you change installer options other than version
(screens, file sets, JRE bundles, code signing, media types, etc.):

1. Open install4j/jaer.install4j in the install4j GUI
2. Confirm General Settings -> Application Info version matches VERSION.txt
   (ant release keeps this in sync; after manual GUI edits, re-check VERSION.txt)
3. Dry-run / test build from the GUI Build step (or CLI test mode) before a full media build:
       install4jc --test install4j/jaer.install4j
   --test does not write media files; use it to validate project config.
   For a faster platform-only smoke test you can also use the IDE "Build" selection
   or: install4jc --build-selected install4j/jaer.install4j
4. When config looks good, prefer ant release again so VERSION.txt, splash, clean jar,
   and install4jc --release stay consistent

## Tagging

The upload script creates a GitHub Release for tag `<VERSION.txt>` if that Release is missing. The git tag itself is separate: if `3.2.0` already points at an old commit, recreate it after the release source is on `master`.

    git tag <VERSION.txt>
    git push origin <VERSION.txt>

Tag already exists on the wrong commit:

    git tag -d 3.2.0
    git push --delete origin 3.2.0
    git tag 3.2.0
    git push origin 3.2.0

Edit release notes on the GitHub Release page (`jaer-3.2.0`).

## Prune old installer assets

Keep binaries for the latest 2--3 releases. Notes and tags stay.

    powershell -File scripts/prune-old-release-assets.ps1
    powershell -File scripts/prune-old-release-assets.ps1 -Keep 3 -WhatIf

Dropbox is an optional historical archive, not the auto-update URL.

## SignPath Windows CI

SignPath Foundation signs only artifacts built on GitHub-hosted runners. Local
`ant release` Windows media cannot be signed as-is. Use GitHub Actions to rebuild
Windows media and submit SignPath (**test-signing2** now; **release-signing** after
that policy is ACTIVE and its certificate is VALID).

Signed Windows comes from Actions; local `ant release` is still fine for unsigned
Mac/Unix media and for local Windows smoke tests.

Remote trigger (does not run signing on your PC; starts the GitHub workflow):

    ant signpath-ci
    ant signpath-ci -Dsignpath.policy=release-signing
    gh workflow run sign-windows-test.yml -f signing_policy=test-signing2

Or GitHub → Actions → **Sign Windows (SignPath)** → Run workflow. Push the workflow
file first. Watch with `gh run watch`. Approve the SignPath request as yourself.

### Local credentials (signpath/ — not in git)

Keep secrets in Dropbox under signpath/ (gitignored except signpath/README.txt).
Do not commit tokens or license keys.

  signpath/signpath-organization-id.txt   — org UUID (yours may already be filled)
  signpath/signpath-api-token.txt         — API token of SignPath CI user "CI builds" (not your personal token)
  install4j/license.txt               — install4j license key (preferred; gitignored)
  signpath/install4j-license.txt      — same key (fallback for Ant / this sync script)
  signpath/signpath-project-slug.txt      — default jaer
  signpath/signpath-signing-policy-slug.txt — test-signing2 until release-signing is ACTIVE and VALID

Recreate stubs if needed:

    powershell -File scripts/init-signpath-local.ps1

Local Ant reads `install4j/license.txt` when non-empty, else `signpath/install4j-license.txt` (`ant release` /
`ant release-windows-ci`) so you need not set session env vars.

### Push credentials to GitHub Actions (not to git)

Runners cannot see Dropbox. After filling signpath/*.txt, sync once with gh:

    powershell -File scripts/sync-signpath-secrets-to-github.ps1

That sets secrets INSTALL4J_LICENSE + SIGNPATH_API_TOKEN and variables
SIGNPATH_ORGANIZATION_ID (+ project/policy). Values never enter the repo.

Or paste the same values manually in GitHub → Settings → Secrets and variables → Actions.
GitHub Actions has **no submitter field**. SignPath treats whoever owns
`SIGNPATH_API_TOKEN` as the submitter.

### SignPath UI: CI user submits, you approve

GitHub itself is not a SignPath user. The GitHub App is **trusted build system /
origin verification**. The submitter is the SignPath **CI user** whose API token
is in `SIGNPATH_API_TOKEN`.

| Place | Role |
|--------|------|
| SignPath policy **Submitters** | CI user **CI builds** (not your personal account) |
| GitHub secret `SIGNPATH_API_TOKEN` | API token of **CI builds** |
| SignPath policy **Approvers** | you (interactive login, e.g. Tobi Delbruck) |

Open-source SignPath requires trusted build system verification. With that on,
interactive users cannot submit even if listed under Submitters. Putting yourself
in Submitters and using your personal token produces:

  The user does not have sufficient privileges to submit the signing request
  ... signing policy "test-signing2"

Create or reuse the CI user (https://app.signpath.io):

  1. Users and Groups → CI users (not Invite user)
  2. Open **CI builds**, or Create CI user with that name
  3. Generate token (shown once). Put it in `signpath/signpath-api-token.txt`
     and re-run `scripts/sync-signpath-secrets-to-github.ps1`

Then edit project **jaer** → signing policy **test-signing2** (same roles on
**release-signing** when that policy is ready):

  1. Install SignPath GitHub App on SensorsINI/jaer; link Trusted Build System GitHub.com
  2. **Submitters:** **CI builds** only (remove your personal user)
  3. **Approvers:** your interactive user; **Use approval process**, required approvals 1
  4. Certificate: test-signing cert (e.g. Test certificate 2026)
  5. Artifact configuration slug **windows-installer-2** (v1 inactivated; see
     .signpath/artifact-configurations/windows-installer-2.xml)

### release-signing certificate (CSR → ACTIVE → CI)

Do **not** Activate **release-signing** and do **not** submit that policy while
the certificate shows **CSR PENDING**. SignPath has the private key on its HSM;
the CSR in `signpath/release_certificate_2026.csr` (gitignored) is only the
public request. SignPath Foundation issues the production cert and it must be
imported so the certificate becomes **VALID**. Then click **Activate** on the
policy (submitter **CI builds**, approver you).

  1. Wait until SignPath shows the release certificate as **VALID** (not CSR PENDING)
  2. Activate policy **release-signing**
  3. Set `signpath/signpath-signing-policy-slug.txt` to `release-signing`
  4. Re-run `scripts/sync-signpath-secrets-to-github.ps1` (updates the GitHub
     variable used on tag pushes). Do not paste the API token into chat.
  5. Push the workflow if needed, then trigger **release-signing**:

         ant signpath-ci -Dsignpath.policy=release-signing

If CSR stays PENDING, ask SignPath Foundation to issue/import the production
certificate. Do not buy a commercial CA cert unless they tell you to.

### Workflow

  File: .github/workflows/sign-windows-test.yml
  Name: Sign Windows (SignPath)
  Triggers: workflow_dispatch (policy choice), or push of tags matching 3.*
  Policy: dispatch input `signing_policy`, else GitHub variable
  SIGNPATH_SIGNING_POLICY_SLUG, else `test-signing2`.
  Steps: JDK 25 + Ant + install4j 13.0.2 → ant release-windows-ci → upload unsigned
  PE → SignPath → job waits up to 1 hour for your SignPath approval
  → upload signed artifact; on tag, attach to GitHub Release

### First dry run (recommended before tagging)

  1. Confirm SignPath submitter/approver and GitHub `SIGNPATH_API_TOKEN` as above
  2. Actions → **Sign Windows (SignPath)** → Run workflow → policy **test-signing2**
     (or `ant signpath-ci`). The Actions page does not set the submitter.
  3. When the job waits on SignPath, open the signing-request URL from the job
     summary / SignPath email and **Approve** (as yourself, not as CI builds)
  4. Download the jaer-windows-signed artifact; check Properties → Digital Signatures
     (test-signing publisher is the test certificate, not yet SignPath Foundation)
  5. For a tagged release, push tag matching VERSION.txt; workflow attaches the signed
     Windows exe to the GitHub Release. Use **release-signing** on that tag only
     after the policy is ACTIVE and VALID (set SIGNPATH_SIGNING_POLICY_SLUG first).

Non-interactive Windows-only local/CI Ant target (no confirm prompt):

    ant release-windows-ci

Output: currentInstallers/<VERSION.txt>/jAER_windows-x64_*.exe

## OS package managers (winget / Homebrew)

3.2.0 GitHub assets exist. YAML/cask SHA256s live in `packaging/`. Remaining work:

- Windows: YAML is `packaging/winget/3.2.0/` (`winget validate` that folder). Hold `wingetcreate submit` until 3.2.0 (or a later signed build) is the public winget package. Publisher stays Sensors Group until SignPath **release-signing**. See packaging/winget/README.md.
- macOS: confirm DMG installer `.app` path on a Mac, then publish `packaging/homebrew/Casks/jaer.rb` to SensorsINI/homebrew-jaer when ready. See packaging/homebrew/README.md.
- Linux: keep the `.sh` installer. Optional later: packaging/deb/README.md.

Package-manager trees should include a `.jaer-packaged-install` marker file so Help → Check for release updates does not offer Download and install.

## macOS notarization

Unsigned DMGs and user-folder installs remain the supported Mac path until membership is Active and install4j is wired. Individual Apple Developer Program (not org): [packaging/macos-notarization.md](packaging/macos-notarization.md).

## Build notes

Compile / jar / full multi-platform packaging is local Ant (`ant compile`, `ant jar`,
`ant release`), then install4j for installers. Windows SignPath signing uses
`.github/workflows/sign-windows-test.yml`, `ant release-windows-ci`, and
`ant signpath-ci`.
