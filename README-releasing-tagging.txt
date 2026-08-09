To generate a new GitHub release

## Version (VERSION.txt)

VERSION.txt at the repo root is the single source of truth for the release version
(e.g. 3.0.0). It drives:

- install4j application version (synced into jaer.install4j; also passed as install4jc --release=...)
- splash overlay text (full VERSION.txt, e.g. 3.1.1)
- About / BUILDVERSION.txt first line on jar build

See latest releases at https://github.com/SensorsINI/jaer/releases and tags at
https://github.com/SensorsINI/jaer/tags . Decide the next version, edit VERSION.txt, then build.

## Install4j release (preferred: ant release)

Prerequisites:
1. Install install4j and put install4jc on PATH
   https://www.ej-technologies.com/resources/install4j/v/13.0/help/doc/cli/compiler.html
2. Install your license (tobi has his own by donation to jaer project)
3. Edit VERSION.txt to the release version (e.g. 3.0.0)
4. Ensure images/SplashScreen.png is the text-free 1024x1024 base art
   (edit images/SplashScreen.pdf in Illustrator/Photoshop when the background art changes, then export PNG)

Build media:

    ant release

You will be prompted to confirm the VERSION.txt value. On "y" / "yes", ant release:

- regenerates splash PNGs (ant generate-splash)
  - images/1024w/SplashScreen.png (1024x1024, JVM splash and install4j launcher splash)
  - images/256h/SplashScreen.png (256x256, Windows shell / installer wizard icons)
- syncs jaer.install4j application version from VERSION.txt
- runs clean + jar (clean build of classes and dist jar)
- runs: install4jc --release=<VERSION.txt> jaer.install4j

Then:
5. Installers land under installers/<version>/ (Dropbox installers folder path as configured in the project)
6. ant release asks whether to copy installers/<version>/updates.xml to the repo root
   (for install4j auto-update). Reply y/yes to copy. Standalone (no rebuild):

       ant offer-copy-updates-xml   # prompts
       ant copy-updates-xml         # copy without prompt

   Then commit/push updates.xml so auto-update can see the new build.
7. Push a git tag matching VERSION.txt and create/edit the GitHub release (see Tagging below)

Note: install4j launcher splash uses the 1024w PNG; keep 256h for Windows installer/shell
icon entries (install4j can reject oversized PNGs for the icon step).

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

Splash only (no installer build): ant generate-splash

## Fallback: install4j GUI (config changes / dry run)

Use the install4j IDE when you change installer options other than version
(screens, file sets, JRE bundles, code signing, media types, etc.):

1. Open jaer.install4j in the install4j GUI
2. Confirm General Settings -> Application Info version matches VERSION.txt
   (ant release keeps this in sync; after manual GUI edits, re-check VERSION.txt)
3. Dry-run / test build from the GUI Build step (or CLI test mode) before a full media build:
       install4jc --test jaer.install4j
   --test does not write media files; use it to validate project config.
   For a faster platform-only smoke test you can also use the IDE "Build" selection
   or: install4jc --build-selected jaer.install4j
4. When config looks good, prefer ant release again so VERSION.txt, splash, clean jar,
   and install4jc --release stay consistent

## Tagging for release

From the repo root, after installers are built and updates.xml is ready:

    git tag <VERSION.txt value, e.g. 3.0.0>
    git push origin <tag>

Example output:
Total 0 (delta 0), reused 0 (delta 0)
To https://github.com/SensorsINI/jaer.git
 * [new tag]             3.0.0 -> 3.0.0

Name the GitHub release e.g. jaer-3.0.0 and edit release notes on the GitHub web UI.

### Deleting a tag

Local only:

    git tag -d tag-name

Already pushed:

    git push --delete origin 3.0.0

## SignPath Windows CI (test-signing)

SignPath Foundation signs only artifacts built on GitHub-hosted runners. Local
`ant release` Windows media cannot be signed as-is. Use GitHub Actions to rebuild
Windows media and submit SignPath **test-signing** (switch to release-signing later).

Signed Windows comes from Actions; local `ant release` is still fine for unsigned
Mac/Unix media and for local Windows smoke tests.

### Local credentials (signpath/ — not in git)

Keep secrets in Dropbox under signpath/ (gitignored except signpath/README.txt).
Do not commit tokens or license keys.

  signpath/signpath-organization-id.txt   — org UUID (yours may already be filled)
  signpath/signpath-api-token.txt         — CI builds API token (stub; fill in)
  signpath/install4j-license.txt          — install4j license key (stub; fill in)
  signpath/signpath-project-slug.txt      — default jaer
  signpath/signpath-signing-policy-slug.txt — default test-signing

Recreate stubs if needed:

    powershell -File scripts/init-signpath-local.ps1

Local Ant reads install4j-license.txt when non-empty (`ant release` /
`ant release-windows-ci`) so you need not set session env vars.

### Push credentials to GitHub Actions (not to git)

Runners cannot see Dropbox. After filling signpath/*.txt, sync once with gh:

    powershell -File scripts/sync-signpath-secrets-to-github.ps1

That sets secrets INSTALL4J_LICENSE + SIGNPATH_API_TOKEN and variables
SIGNPATH_ORGANIZATION_ID (+ project/policy). Values never enter the repo.

Or paste the same values manually in GitHub → Settings → Secrets and variables → Actions.

SignPath UI (project "jaer" / "jaer [OSS]"):

  1. Install SignPath GitHub App on SensorsINI/jaer; link Trusted Build System GitHub.com
  2. Ensure "CI builds" has submitter role on policy test-signing
  3. Artifact configuration slug **windows-installer-2** (v1 inactivated; see
     .signpath/artifact-configurations/windows-installer-2.xml)
  4. Leave release-signing until its INVALID status is fixed

### Workflow

  File: .github/workflows/sign-windows-test.yml
  Triggers: workflow_dispatch, or push of tags matching 3.*
  Steps: JDK 21 + Ant + install4j 13.0.2 → ant release-windows-ci → upload unsigned
  PE → SignPath test-signing → upload signed artifact; on tag, attach to GitHub Release

### First dry run (recommended before tagging)

  1. Push the workflow + Ant target to master (or your working branch)
  2. Actions → "Sign Windows (SignPath test-signing)" → Run workflow
  3. If SignPath waits on approval, approve in the SignPath UI
  4. Download the jaer-windows-signed artifact; check Properties → Digital Signatures
     (publisher SignPath Foundation)
  5. For a tagged release, push tag matching VERSION.txt; workflow attaches the signed
     Windows exe to the GitHub Release

Non-interactive Windows-only local/CI Ant target (no confirm prompt):

    ant release-windows-ci

Output: installers/<VERSION.txt>/jAER_windows-x64_*.exe

## Build notes

Compile / jar / full multi-platform packaging is local Ant (`ant compile`, `ant jar`,
`ant release`), then install4j for installers. Windows SignPath signing uses
`.github/workflows/sign-windows-test.yml` and `ant release-windows-ci`.
