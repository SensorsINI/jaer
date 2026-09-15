# Releasing jAER

GitHub Release **assets** (the installers) always sit on a **git tag**. There is no way to attach a DMG/exe to GitHub without creating that tag. Building media does **not** require a tag.

Two hosts for binaries vs updater XML; the public download page is GitHub Pages (`website/`). Do not follow install4j's "upload updates.xml and media to the same directory" hint.

| What | Where | Who writes it |
|------|--------|----------------|
| Update descriptor | `https://raw.githubusercontent.com/SensorsINI/jaer/master/updates.xml` | git: commit and push repo-root `updates.xml` |
| Installer binaries | `https://github.com/SensorsINI/jaer/releases/latest/download/<fileName>` | `ant upload-installers` (Mini for Mac DMGs) |
| Public download page | `https://jaerproject.org` | GitHub Pages from [`website/`](../website/); workflow [`.github/workflows/pages.yml`](../.github/workflows/pages.yml). DNS: [`website/README.md`](../website/README.md) |

`updates.xml` `baseUrl` must be `https://github.com/SensorsINI/jaer/releases/latest/download/`. `ant copy-updates-xml` sets that; do not edit it by hand. The in-app checker reads the raw GitHub file, then downloads `baseUrl` + `fileName` (for example `jAER_windows-x64_3_2_0.exe`).

`/releases/latest/` is whichever GitHub Release is marked Latest (usually the newest published non-prerelease). The 3.2.0 media must be attached to a published Release named `3.2.0` or `/latest/download/` still serves 3.1.0.

## Who does what (read this first)

| Asset | Where it **must** be built | Ant / command | Signing |
|-------|---------------------------|---------------|---------|
| macOS Intel + Apple Silicon `.dmg` | **Mini only** | `ant release-macos` | Developer ID + Apple notarization (`scripts/run-install4jc.sh`) |
| Windows `.exe` (production) | **GitHub Actions** (any box can *trigger*) | `ant azure-sign-ci` or `gh workflow run sign-windows-azure.yml` | Azure Artifact Signing, publisher **Tobias Delbruck** |
| Linux `.sh` | Any OS with install4j | `ant release -Dskip.github.draft=true` (or full `ant release`) | none |
| Git tag + draft Release | Any box with `gh` | `ant create-draft-release` | n/a |
| Attach media to that tag | Mini for DMGs; any box for `.sh`; **never** overwrite Azure exe | `ant upload-installers` | n/a |
| Same Mac + upload, from another box | **SSH into the Mini** (ZeroTier). Cursor Cloud Agents cannot notarize. | See [Remote Mini (SSH / Cursor CLI)](#remote-mini-ssh--cursor-cli) | same as Mini row |

Do **not** use `ant release` when you only want Mac. On the Mini that command builds Windows+Linux too, waits on Apple notarization, then **tags `VERSION.txt`**. Use `ant release-macos` for Mac-only with no tag.

Do **not** `ant upload-installers-clobber-windows` after Azure has signed the exe. Default `ant upload-installers` skips the local unsigned `.exe`.

Do **not** upload Mac DMGs built on Windows/Linux (`--disable-signing`). That clobbers a stapled Mini build.

SignPath (`ant signpath-ci`, workflow **Sign Windows (SignPath)**) is a **backup**. Production Windows is Azure.

Existing jar only, Mac DMGs (dev compression): `ant install4j-macos`. Latest source + production compression: `ant release-macos`.

## Release candidate (typical)

1. Set `VERSION.txt` (e.g. `3.5.0`) and push `master`.
2. **Mini:** `ant release-macos`. Output: `currentInstallers/<VERSION>/jAER_macos_*.dmg` and `jAER_macos_aarch64_*.dmg`. Proof:

       xcrun stapler validate currentInstallers/<ver>/jAER_macos_aarch64_*.dmg
       spctl -a -t open --context context:primary-signature -vv currentInstallers/<ver>/jAER_macos_aarch64_*.dmg

   Expect `source=Notarized Developer ID`. Repeat for `jAER_macos_*.dmg` (Intel).
3. **Any box:** `ant azure-sign-ci` then `gh run watch`. Download artifact `jaer-windows-azure-signed`.
4. When the source on `master` is the release: `ant create-draft-release` (this **creates and pushes tag `VERSION.txt`** and a GitHub **draft**).
5. **Mini:** `ant upload-installers` (DMGs + Linux `.sh` if present + sample zip; skips Windows exe).
6. Attach the Azure-signed exe on the draft (`gh release upload <tag> path/to/exe --clobber`) if Actions did not already.
7. Notes: `ant upload-release-notes`. Publish: `gh release edit <VERSION.txt> --draft=false`. Then `ant copy-updates-xml`, commit and push `updates.xml`.

Media-only without tagging: stop after step 2 or 3. `ant upload-installers` will create tag `VERSION.txt` if it does not exist — do not run it until you intend to tag.

`ant release` (all OS + tag) is the old all-in-one. Prefer the table above. Skip its tag/draft with `-Dskip.github.draft=true`.

Upload extras: dry run `ant "-Djaer.upload.whatif=true" upload-installers`. Other tag: `ant "-Djaer.upload.tag=3.4.1" upload-installers`. PowerShell: **quote** `-Dname=value`. Also uploads `jaer-sample-data.zip` when present. Does **not** overwrite the GitHub body on an existing draft.

Download counts: `ant count-asset-downloads`. After a rebuild, hashes in `updates.xml` change — repeat `copy-updates-xml`, upload, and push `updates.xml` or the updater checksum-fails.

## Remote Mini (SSH / Cursor CLI)

Mac DMGs must run on this Mini (`~/jaer`): Developer ID, App Store Connect `.p8`, and install4j live here. A normal **SSH session is enough**. You do not need Cursor on the Mini for a build or `gh` upload.

Cursor **Cloud Agents** (cursor.com/agents, or CLI `&` handoff) run on a Cursor VM. They cannot see `signpath/`, the login keychain, or `install4j/license.txt`. Do not send notarization there.

### SSH (preferred)

From Windows PowerShell on the ZeroTier `jaer` network (Mini address from `zerotier-cli listnetworks` on the Mini; currently `10.144.5.238`):

```powershell
ssh tobidelbruck@10.144.5.238
```

On the Mini, use bash. Notarization can sit for hours — use `tmux` (or `screen`) so a dropped SSH session does not kill install4j:

```bash
tmux new -s jaer-release   # later: tmux attach -t jaer-release
cd ~/jaer
git pull
gh auth status             # needs repo scope for SensorsINI/jaer
```

Pick the GitHub Release to attach to: the **newest Release object**, including **draft** and **prerelease** (candidate), not only `/releases/latest/` (that is the published Latest flag):

```bash
gh release list --limit 15
TAG=$(gh release list --limit 30 --json tagName,createdAt \
  --jq 'sort_by(.createdAt) | reverse | .[0].tagName')
gh release view "$TAG" --json tagName,isDraft,isPrerelease,isLatest,url
```

`VERSION.txt` and `currentInstallers/<tag>/` must match `$TAG`. If they do not, set `VERSION.txt`, push, then build. Confirm, then:

```bash
ant release-macos
xcrun stapler validate currentInstallers/"$TAG"/jAER_macos_aarch64_*.dmg
spctl -a -t open --context context:primary-signature -vv \
  currentInstallers/"$TAG"/jAER_macos_aarch64_*.dmg
# Expect source=Notarized Developer ID. Repeat for jAER_macos_*.dmg (Intel).

ant "-Djaer.upload.whatif=true" "-Djaer.upload.tag=$TAG" upload-installers
ant "-Djaer.upload.tag=$TAG" upload-installers
```

Default `upload-installers` skips the local Windows `.exe` (keeps the Azure-signed GitHub asset). Do not `upload-installers-clobber-windows` after Azure has signed. Do not upload Mac DMGs built on Windows/Linux.

The `createdAt` picker only sees Release objects that already exist. For a brand-new `VERSION.txt` with no GitHub Release yet, skip the picker: `ant create-draft-release` then `ant upload-installers`. Bare `upload-installers` (no `-Djaer.upload.tag`) still **creates the git tag and a draft** if they are missing — do not run it until you intend that.

### Optional: Cursor CLI on the Mini

Same machine, same Ant/`gh` commands. The CLI is only a terminal agent in that SSH session.

```bash
curl https://cursor.com/install -fsS | bash
export PATH="$HOME/.local/bin:$PATH"   # add to ~/.bashrc if needed
cd ~/jaer
agent                                  # login once; then prompt from repo root
```

Docs: [CLI overview](https://cursor.com/docs/cli/overview). Useful: `agent "…"` one-shot, `agent ls` / `agent resume`, `/sandbox` → **disabled** (install4j and Apple tools need keychain + network). Do **not** prefix a message with `&` (Cloud Agent handoff). Do not ask the agent to print `signpath/` or `install4j/license.txt`.

Windows can also run `agent` locally; that still cannot notarize. SSH to the Mini and run Ant there.

## Version (VERSION.txt)

`VERSION.txt` at the repo root is the single source of truth. It drives:

- install4j application version (synced into `install4j/jaer.install4j`; also `install4jc --release=...`)
- splash overlay text (full `VERSION.txt`, e.g. 3.2.0) on generated 1024 / 256 / 800 PNGs
- About / `BUILDVERSION.txt` first line on jar build (also git commit SHA, `git describe`, subject, and a copy next to `VERSION.txt` in the installer)

See https://github.com/SensorsINI/jaer/releases and https://github.com/SensorsINI/jaer/tags .

## Install4j build (`ant release`)

Prerequisites:

1. install4j on PATH (`install4jc`) -- https://www.ej-technologies.com/resources/install4j/v/13.0/help/doc/cli/compiler.html
2. License (local: `install4j/license.txt`, gitignored; fallback `packaging/signpath/install4j-license.txt`)
3. `VERSION.txt` set
4. `images/SplashScreen.png` is the text-free 1024x1024 base art (`images/SplashScreen.pdf` when the art changes)

    ant release

On Enter / `y` / `yes` it: generates splash PNGs (`images/1024w`, `images/256h`, `images/800w`), syncs `install4j/jaer.install4j` version, `clean` + `jar`, then `install4jc --release=<VERSION.txt> install4j/jaer.install4j`, then tags `HEAD` and creates a GitHub **draft** Release. It does not copy repo-root `updates.xml`; publish the GitHub draft, then run `ant copy-updates-xml`.

Splash: `ant generate-splash` writes gitignored `images/800w`, `256h`, and `1024w` from `images/SplashScreen.png` + `VERSION.txt`. `ant install4j` and `ant release` run that first. The launcher splash is the **800×800** PNG; **256h** / **1024w** are compile-time icons only. Details: [`install4j/README.md`](../install4j/README.md).

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
- Media excludes: tmp/, src/, scripts/, logs/, bin/, tools/, native/,
  deviceSettings/olderSystemsAndExperimental/, Benchmarking_7_9_2026/ (tmp alone can be
  hundreds of MB of local scratch and must not ship). Repo-root `*.webp` is excluded via
  `excludeSuffixes`. Do not copy `.gitignore` wholesale into the fileset: `lib/` and
  `dist/jAER.jar` are gitignored but required at runtime.
- Sample recordings: see [`README-sample-data.md`](README-sample-data.md). `sampleData/` is excluded from media except `README.md` and `SIZE.txt`.
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

`ant release` (and `ant create-draft-release`) create an annotated git tag `<VERSION.txt>` on `HEAD`, push it, and open a **draft** GitHub Release. Drafts are not Latest and are not the public release page until you publish. The upload script also creates a **draft** if the Release is missing.

If the tag already points at an older commit, delete and recreate it after the release source is on `master` (do not `--force` from Ant):

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

## Azure Artifact Signing (Windows production)

Public Trust identity **Tobias Delbruck** is Completed. Profile **`jaer-public`** is Active
(account **jAER**, West US 2). Publisher on signed exes is that CN, not SignPath
Foundation. Workflow: `.github/workflows/sign-windows-azure.yml` (**Sign Windows
(Azure)**). Trigger from any box: `ant azure-sign-ci`. Setup (Entra
OIDC, GitHub environment `azure-signing`, secrets):
[`packaging/azure-artifact-signing.md`](../packaging/azure-artifact-signing.md).

    gh workflow run sign-windows-azure.yml

Keep SignPath **test-signing2** as a backup. Do not switch that policy to
**release-signing** while the Foundation cert is CSR PENDING. Do not
`wingetcreate submit` until an Azure-signed exe is the GitHub Windows asset.

## SignPath Windows CI

SignPath Foundation signs only artifacts built on GitHub-hosted runners. Local
`ant release` Windows media cannot be signed as-is. Use GitHub Actions to rebuild
Windows media and submit SignPath (**test-signing2** now; **release-signing** after
that policy is ACTIVE and its certificate is VALID).

Signed Windows comes from Actions; local `ant release` is still fine for unsigned
Mac/Unix media and for local Windows smoke tests. Include the Windows `.exe` with:

    ant upload-installers-clobber-windows

Remote trigger (does not run signing on your PC; starts the GitHub workflow):

    ant signpath-ci
    ant signpath-ci -Dsignpath.policy=release-signing
    gh workflow run sign-windows-test.yml -f signing_policy=test-signing2

Or GitHub → Actions → **Sign Windows (SignPath)** → Run workflow. Push the workflow
file first. Watch with `gh run watch`. Approve the SignPath request as yourself.

### Local credentials (`packaging/signpath/` — not in git)

Keep secrets in Dropbox under `packaging/signpath/` (gitignored except `README.txt` and the artifact XML).
Do not commit tokens or license keys.

  packaging/signpath/signpath-organization-id.txt   — org UUID (yours may already be filled)
  packaging/signpath/signpath-api-token.txt         — API token of SignPath CI user "CI builds" (not your personal token)
  install4j/license.txt                             — install4j license key (preferred; gitignored)
  packaging/signpath/install4j-license.txt          — same key (fallback for Ant / this sync script)
  packaging/signpath/signpath-project-slug.txt      — default jaer
  packaging/signpath/signpath-signing-policy-slug.txt — test-signing2 until release-signing is ACTIVE and VALID

Recreate stubs if needed:

    powershell -File scripts/init-signpath-local.ps1

Local Ant reads `install4j/license.txt` when non-empty, else `packaging/signpath/install4j-license.txt` (`ant release` /
`ant release-windows-ci`) so you need not set session env vars.

### Push credentials to GitHub Actions (not to git)

Runners cannot see Dropbox. After filling `packaging/signpath/*.txt`, sync once with gh:

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
  3. Generate token (shown once). Put it in `packaging/signpath/signpath-api-token.txt`
     and re-run `scripts/sync-signpath-secrets-to-github.ps1`

Then edit project **jaer** → signing policy **test-signing2** (same roles on
**release-signing** when that policy is ready):

  1. Install SignPath GitHub App on SensorsINI/jaer; link Trusted Build System GitHub.com
  2. **Submitters:** **CI builds** only (remove your personal user)
  3. **Approvers:** your interactive user; **Use approval process**, required approvals 1
  4. Certificate: test-signing cert (e.g. Test certificate 2026)
  5. Artifact configuration slug **windows-installer-2** (v1 slug
     `windows-installer` was inactivated; that slug cannot be reused).
     `product-name` in the XML must match install4j
     `<application name="...">` exactly (currently
     `jAER - Desktop Application for Event Sensors`). If SignPath reports
     "unexpected product name", you are on the old string
     (`jaer - Java Tools for Address Event Representation Sensors and Processing`)
     or on slug `windows-installer`. Edit **windows-installer-2** in the
     SignPath UI and paste
     `packaging/signpath/artifact-configurations/windows-installer-2.xml`.
     Git does not update the cloud config. If SignPath refuses the
     product-name change on that slug, add a new config (e.g.
     `windows-installer-3`), point both policies at it, and set
     `artifact-configuration-slug` in `.github/workflows/sign-windows-test.yml`.

### release-signing certificate (CSR → ACTIVE → CI)

Do **not** Activate **release-signing** and do **not** submit that policy while
the certificate shows **CSR PENDING**. SignPath has the private key on its HSM;
the CSR in `packaging/signpath/release_certificate_2026.csr` (gitignored) is only the
public request. SignPath Foundation issues the production cert and it must be
imported so the certificate becomes **VALID**. Then click **Activate** on the
policy (submitter **CI builds**, approver you).

  1. Wait until SignPath shows the release certificate as **VALID** (not CSR PENDING)
  2. Activate policy **release-signing**
  3. Set `packaging/signpath/signpath-signing-policy-slug.txt` to `release-signing`
  4. Re-run `scripts/sync-signpath-secrets-to-github.ps1` (updates the GitHub
     variable used on tag pushes). Do not paste the API token into chat.
  5. Push the workflow if needed, then trigger **release-signing**:

         ant signpath-ci -Dsignpath.policy=release-signing

If CSR stays PENDING, ask SignPath Foundation to issue/import the production
certificate. Do not buy a commercial CA cert unless they tell you to.

### Workflow

  File: .github/workflows/sign-windows-test.yml
  Name: Sign Windows (SignPath)
  Triggers: workflow_dispatch only (no tag push). Production Windows is Azure.
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
  5. Do **not** attach SignPath-signed exes to GitHub Releases. Production Windows
     is Azure (`ant azure-sign-ci`). SignPath stays a manual backup.

Non-interactive Windows-only local/CI Ant target (no confirm prompt):

    ant release-windows-ci

Output: currentInstallers/<VERSION.txt>/jAER_windows-x64_*.exe

## Release notes WebP (looping previews)

Installer binaries are GitHub Release **assets**. Looping clips in the notes body
are GitHub **user-attachments** (`https://github.com/user-attachments/assets/<uuid>`),
the same store as drag-drop on github.com. Do not `gh release upload` those WebPs.

In `release-notes/jaer-<VERSION>-release-notes.md`, path relative to `release-notes/`:

```html
<!-- webp: 3.4.0/8-cams-startup.webp -->
```

`ant upload-release-notes` (or `ant upload-release-notes-media`) uploads any tag
whose next non-empty line is not already a user-attachments URL, inserts

```html
<img src="https://github.com/user-attachments/assets/<uuid>" alt="…" width="80%" />
```

and then `upload-release-notes` pushes the markdown with `gh release edit`.
Local `.webp` stays gitignored (Dropbox); git tracks the comment plus the URL.
Replace a clip: delete the `<img>` line, leave the tag, replace the local file.
GitHub free image limit is **10 MB**. Encode with `ffmpeg -loop 0` (WebP default
does not loop). The upload endpoint is unofficial (`uploads.github.com` +
`gh auth token` with push access).

## OS package managers (winget / Homebrew)

3.2.0 GitHub assets exist. YAML/cask SHA256s live in `packaging/`. Remaining work:

- Windows: YAML is `packaging/winget/3.2.0/` (`winget validate` that folder). Hold `wingetcreate submit` until 3.2.0 (or a later signed build) is the public winget package. Publisher stays Sensors Group until SignPath **release-signing**. See packaging/winget/README.md.
- macOS: confirm DMG installer `.app` path on a Mac, then publish `packaging/homebrew/Casks/jaer.rb` to SensorsINI/homebrew-jaer when ready. See packaging/homebrew/README.md.
- Linux: keep the `.sh` installer. Optional later: packaging/deb/README.md.

Package-manager trees should include a `.jaer-packaged-install` marker file so Help → Check for release updates does not offer Download and install.

## macOS notarized installers

Notarized Intel + Apple Silicon DMGs are a **Mini-only** pipeline. Windows GitHub
Actions / SignPath signs the `.exe` only. `ant` on Windows passes
`--disable-signing` / `--disable-notarization`. install4j can emit unsigned Mac
DMGs on any OS; those are not the GitHub Mac assets.

Apple secrets live in gitignored repo-root `signpath/` on this Mini only (not
Dropbox). Details: [packaging/macos-notarization.md](../packaging/macos-notarization.md).

### Maintainer: build and upload (Mini)

From another machine, SSH into this Mini and run the same commands ([Remote Mini (SSH / Cursor CLI)](#remote-mini-ssh--cursor-cli)). Do not use a Cursor Cloud Agent.

1. Confirm `signpath/` has the `.p12`, `AuthKey.p8`, issuer/key txt, and
   `macos-p12-password.txt` (or `JAER_MAC_KEYSTORE_PASSWORD`).
2. `ant release-macos` (latest source, no tag) or `ant install4j-macos` (existing jar).
   First-account notarization can sit on `Waiting for notarization result` for hours.
3. Proof (not Finder Get Info, not a stale `*.dmg.notarization.log`):

       xcrun stapler validate currentInstallers/<ver>/jAER_macos_aarch64_*.dmg
       spctl -a -t open --context context:primary-signature -vv currentInstallers/<ver>/jAER_macos_aarch64_*.dmg

   Expect `source=Notarized Developer ID`. Repeat for `jAER_macos_*.dmg` (Intel).
4. When you are ready to tag `VERSION.txt`: `ant create-draft-release` then from this Mini
   `ant upload-installers`. Do not overwrite stapled DMGs later from a Windows `ant release`.

Finder: double-click the `.dmg` (mounts a volume). Then double-click
**`jAER <VERSION.txt> Installer`**. That name is `installerName` / `volumeName` on
media ids 38 and 39. Changing it requires another Mini sign+notarize.

### User: open a notarized DMG

1. Download the Apple Silicon or Intel DMG from the GitHub Release.
2. Double-click the `.dmg`. Finder opens a disk named `jAER <version> Installer`.
3. Double-click **`jAER <version> Installer`**. Do not use Archive Utility.
4. Prefer a user folder unless you are installing a notarized build into
   `/Applications`.

Unsigned DMGs and Homebrew casks are unchanged (right-click Open / cask recipe).

Individual Apple Developer Program. Gatekeeper shows the personal legal name.

## Build notes

Compile / jar is local Ant. Mac notarized DMGs: Mini `ant release-macos`. Windows Authenticode:
`.github/workflows/sign-windows-azure.yml` / `ant azure-sign-ci`. SignPath
(`.github/workflows/sign-windows-test.yml`, `ant signpath-ci`) is a backup.
