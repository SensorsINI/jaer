# install4j project and splash assets

Open [`jaer.install4j`](jaer.install4j) in the [install4j](https://www.ej-technologies.com/products/install4j/overview.html) IDE. Media output is `currentInstallers/<VERSION.txt>/`. Release steps: [`../docs/README-releasing-tagging.md`](../docs/README-releasing-tagging.md).

From the repo root: `ant release`, `ant install4j` (regenerates splash from `VERSION.txt` then compile media), or `ant generate-splash` for PNGs only.

| File | Role |
|------|------|
| `jaer.install4j` | Installer / launcher / updater project |
| `jaer.ico` | Windows launcher `iconFile` (legacy 64×64 1-bit ICO) |
| `install4j-custom-resources.utf8` | English localization overrides |
| `license.txt` | Compiler license key (gitignored; fallback `packaging/signpath/install4j-license.txt`) |

## macOS code signing / notarization

On the Mini, `scripts/run-install4jc.sh` (from `ant release` / `ant install4j`) reads gitignored `signpath/` files and passes App Store Connect compiler variables. Do not type issuer / key ID / `.p12` password into the project file (that would land in git). GitHub Mac `.dmg` assets must be the Mini-notarized files (`ant upload-installers` from this machine).

Media ids 38/39 use `installerName` / `volumeName` `jAER ${compiler:sys.version} Installer` so Finder shows a short installer name. That is Mac-only; do not change the global application name (SignPath Windows `product-name`).

If the install4j IDE has `jaer.install4j` open, reload it after a git pull. Prefer `ant` for signed Mac media. Windows `ant release` and CI use `--disable-signing` (SignPath signs the exe).

## Generated splash / icon PNGs

`ant generate-splash` overlays **jAER** and the full `VERSION.txt` string on the text-free base art and writes three squares. Those output folders are **gitignored build products** (`ant install4j`, `ant release`, `ant jar`, and `ant run` all run `generate-splash`). Track only `images/SplashScreen.png` (and the PDF). The 256h / 1024w files are compile-time icon sources; they are **not** copied into `C:\Program Files\jAER`. The 800 PNG is shipped as `SplashScreen.png` next to the launcher (and inside `jAER.jar`).

| Path | Size | Used for |
|------|------|----------|
| `images/SplashScreen.png` | 1024×1024 | **Source art only** (no version text). Re-export from `images/SplashScreen.pdf` when the illustration changes. Do not hand-paint version text here. |
| `images/800w/SplashScreen.png` | 800×800 | **Launcher splash** shown while the installed `jaer` executable starts. Also copied into `jAER.jar` and used by `java -splash:` for git/`ant run`. |
| `images/256h/SplashScreen.png` | 256×256 | **Windows / installer wizard icons** (ICO-class size). Compile-time only. |
| `images/1024w/SplashScreen.png` | 1024×1024 | **macOS icns** (Retina 512pt@2x). Compile-time only. |

The `1024w` / `256h` folder names are historical (commit `f5e97c584`, “256h for installer, 1024w for jar”). Both images are square; **w** / **h** do not mean different aspect ratios.

800×800 is intentionally not a power of two. A 1024×1024 splash filled typical laptop screens and clipped the **status line at the bottom** (the live log from `SplashScreen.writeMessage`). 800×800 leaves room below the bitmap on 1080p and still fits most 768p layouts.

Generator: [`scripts/GenerateSplashScreen.java`](../scripts/GenerateSplashScreen.java). It composites on a 1024 canvas, then scales to 800 and 256.

## Two different splashes (do not mix)

Installed copies and `ant run` / `java -splash:` are **not** the same mechanism.

### 1. install4j native splash (installed `jaer` launcher)

Configured on the `jaer` launcher in `jaer.install4j`:

```xml
<splashScreen show="true" width="800" height="800"
              bitmapFile="../images/800w/SplashScreen.png" textOverlay="true">
  <statusLine x="45" y="776" text="Press ESC to abort startup" />
</splashScreen>
```

This is **not** `java.awt.SplashScreen`. Status text is `com.install4j.api.launcher.SplashScreen.writeMessage`. [`SplashStartupAbort`](../src/net/sf/jaer/util/SplashStartupAbort.java) calls that via reflection (no `i4jruntime` at compile time). One line, coalesced (default 75 ms, `-Djaer.splashWriteMinIntervalMs`).

**Rules that were easy to break (3.3.0):**

- Do **not** pass `-splash:` in install4j `vmParameters`. Generated Windows launchers ignore most `-splash:` forms; if a Java splash *does* appear you get **two** splashes (native + Java), and `splash.update()` on every log line made startup much slower.
- install4j allows **only one** `SplashScreen.png` in the distribution tree (same destination name or same source path both fail). The 800×800 file is added as a `fileEntry` next to the exe (destination name `SplashScreen.png`). The 256 / 1024 PNGs are **not** shipped; they are compile-time `iconImageFiles` / wizard icon sources. `fileEntry.subDirectory` does not relocate a second splash.
- Do **not** open an AWT/Swing window while the native splash is up. The first window closes it. `SplashStartupAbort` skips its Swing fallback when `writeMessage` succeeds.
- The optional Java overlay (`JAERViewer.SplashHandler`) is **off** unless `-Djaer.splashLogOverlay=true`.
- ESC abort during splash uses Windows `GetAsyncKeyState` because the native splash does not receive key events and a blocked EDT does not pump AWT.

### 2. Java `java.awt.SplashScreen` (git / `ant run`)

`nbproject/project.properties` and `scripts/run-jaer-fast.*` pass `-splash:images/800w/SplashScreen.png`. That is a JVM filesystem path, not a classpath resource. After `ant generate-splash`, the 800 PNG must exist.

If Java splash is absent, `SplashStartupAbort` can show an undecorated PNG window (and, if overlay is enabled, scrolling log lines).

Classpath copy: `ant jar` target `jaer-copySplashImage` puts `images/800w/SplashScreen.png` at the root of `jAER.jar` (`SplashScreen.png`). `JaerConstants.SPLASH_SCREEN_IMAGE` (`/net/sf/jaer/images/SplashScreen.png`) is a fallback resource path; the generated file is not stored under `src/net/sf/jaer/images`.

## Icons (Windows vs macOS)

| Source | Platform | Notes |
|--------|----------|--------|
| `install4j/jaer.ico` | Windows `iconFile` | Old 64×64 1-bit ICO still referenced on the launcher. |
| `images/256h/SplashScreen.png` | Windows shell + installer wizard | Windows ICO useful max is 256. Also `customIconImageFiles` on the installer GUI. |
| `images/1024w/SplashScreen.png` | macOS icns | 1024×1024 was added specifically for Mac (commit `5feb2252e`). Listed in launcher `iconImageFiles` with the 256 PNG. |

`iconSet="true"` plus `iconImageFiles` is how install4j builds multi-resolution icons. The 256 and 1024 PNGs must stay **power-of-two** even though the splash is 800.

## Media fileset vs splash

The main `dirEntry` packs the repo root into `jaer/` and **excludes** `images/` (large art / demos), `sampleData/` recordings, repo-root `*.webp` (e.g. `jaer3.webp`), Dropbox `*conflicted copy*` files, and `.dropboxignore`. Splash is re-added as a single `fileEntry` so it sits next to the exe as `SplashScreen.png`. `sampleData/README.md` and `SIZE.txt` are fileEntries under `jaer/sampleData`. Do not add a second `SplashScreen.png` from `256h` or `1024w`.

Do **not** treat `.gitignore` as the media exclude list. Ivy `lib/`, `jars/`, and `dist/jAER.jar` are gitignored but required at runtime. The fileset lists VCS/IDE/docs/scratch excludes explicitly (including local `deviceSettings/olderSystemsAndExperimental`, `Benchmarking_7_9_2026`, `native`). Help → **Git update and build jAER (experimental)** is the only in-app git rebuild path; a normal install does not need `.git` or sources.

Welcome has an optional **Download sample recordings** checkbox (`downloadSampleData`), default off when the destination `sampleData` has no recordings. The label includes zip/unpacked size and an ETA at 10 MB/s Wi-Fi. After the Installation screen (rollback barrier), a **Sample recordings** form can **Skip** to Finish or **Download**. Cancel during the download does not uninstall jAER; use Help → Sample data later. See [`docs/README-sample-data.md`](../docs/README-sample-data.md).

The uninstaller deletes default `jaer/sampleData` (downloaded recordings are not in the install4j file inventory). The Welcome screen warns that extra files in that folder are removed too. Home-folder `jaerSampleData` is left alone. If anything remains in the install directory besides `.install4j` and the `uninstall` launcher, that folder is opened. `Util.showPath` is try/caught: on Linux the bundled JRE’s `jspawnhelper` is often already deleted, so opening the folder can throw `posix_spawn … /bin/sh` even though uninstall succeeded.

The `jaer` launcher uses **single instance** mode. Windows/Linux installers show a **File associations** screen with one optional checkbox for `.aedat` / `.aedat2` / `.aedatz` / `.aedat4` (checked by default). macOS associations are compile-time in the launcher `Info.plist` (`macStaticAssociations`) and cannot be optional without breaking the signed bundle. Double-click while jAER is running is handled by `Install4jFileOpen` (`StartupNotification` on Windows/macOS; on Linux a second JVM may still start, write `${java.io.tmpdir}/jaer/open-requests/`, and exit). The desktop `Exec` uses `%U`, so Linux may pass `file://` URLs; `Install4jFileOpen.parseLaunchArgument` accepts those.

## Related Ant targets

| Target | What it does |
|--------|----------------|
| `generate-splash` | Overlay `VERSION.txt` → `images/800w`, `1024w`, `256h` |
| `release` | Confirm version, splash, sync `jaer.install4j` version, `clean` + `jar`, pack sample data if present, `install4jc --release=…` |
| `pack-sample-data` | Zip `sampleData/` recordings → `currentInstallers/<version>/jaer-sample-data.zip`, write `SIZE.txt` |
| `install4j` | `generate-splash` then `install4jc` (needs existing `dist/jAER.jar` + `build/opencv-slim`) |
| `replace-installed-jar` | Copy `dist/jAER.jar` onto an existing install (does **not** refresh the native splash PNG) |

After a splash or `jaer.install4j` launcher change, rebuild media (`ant release` or `ant install4j`). Replacing only the jar (`ant replace-installed-jar`) leaves the old native splash PNG inside the installed tree.

## GUI / dry run

Use the install4j IDE for screens, file sets, JRE bundles, code signing, and media types. After GUI edits, confirm Application Info version still matches `VERSION.txt` (`ant release` keeps that in sync). `install4jc --test install4j/jaer.install4j` validates config without writing media.
