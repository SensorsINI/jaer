# install4j project and splash assets

Open [`jaer.install4j`](jaer.install4j) in the [install4j](https://www.ej-technologies.com/products/install4j/overview.html) IDE. Media output is `currentInstallers/<VERSION.txt>/`. Release steps: [`../docs/README-releasing-tagging.md`](../docs/README-releasing-tagging.md).

From the repo root: `ant release` (or `ant generate-splash` for PNGs only).

| File | Role |
|------|------|
| `jaer.install4j` | Installer / launcher / updater project |
| `jaer.ico` | Windows launcher `iconFile` (legacy 64×64 1-bit ICO) |
| `install4j-custom-resources.utf8` | English localization overrides |
| `license.txt` | Compiler license key (gitignored; fallback `packaging/signpath/install4j-license.txt`) |

## Generated splash / icon PNGs

`ant generate-splash` (also the first step of `ant release`) overlays **jAER** and the full `VERSION.txt` string on the text-free base art and writes three squares:

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

The main `dirEntry` packs the repo root into `jaer/` and **excludes** `images/` (large art / demos) and `sampleData/` recordings. Splash is re-added as a single `fileEntry` so it sits next to the exe as `SplashScreen.png`. `sampleData/README.md` and `SIZE.txt` are fileEntries under `jaer/sampleData`. Do not add a second `SplashScreen.png` from `256h` or `1024w`.

Welcome has an optional **Download sample recordings** checkbox (`downloadSampleData`), default on when the destination `sampleData` has no recordings. After InstallFiles the installer downloads `https://github.com/SensorsINI/jaer/releases/latest/download/jaer-sample-data.zip` and unpacks it. Failure does not abort the install. Sizes on the checkbox come from `ant pack-sample-data` (`-Djaer.sampleDataZipMiB` / `jaer.sampleDataUnpackedMiB`).

## Related Ant targets

| Target | What it does |
|--------|----------------|
| `generate-splash` | Overlay `VERSION.txt` → `images/800w`, `1024w`, `256h` |
| `release` | Confirm version, splash, sync `jaer.install4j` version, `clean` + `jar`, pack sample data if present, `install4jc --release=…` |
| `pack-sample-data` | Zip `sampleData/` recordings → `currentInstallers/<version>/jaer-sample-data.zip`, write `SIZE.txt` |
| `install4j` | `install4jc` only (needs existing `dist/jAER.jar` + `build/opencv-slim`) |
| `replace-installed-jar` | Copy `dist/jAER.jar` onto an existing install (does **not** refresh the native splash PNG) |

After a splash or `jaer.install4j` launcher change, rebuild media (`ant release` or at least `ant generate-splash` then `ant install4j`). Replacing only the jar leaves the old 800/1024 splash inside the installed tree.

## GUI / dry run

Use the install4j IDE for screens, file sets, JRE bundles, code signing, and media types. After GUI edits, confirm Application Info version still matches `VERSION.txt` (`ant release` keeps that in sync). `install4jc --test install4j/jaer.install4j` validates config without writing media.
