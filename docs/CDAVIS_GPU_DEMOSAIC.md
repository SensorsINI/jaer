# CDAVIS GPU Demosaic — Session Handoff

Handoff document for continuing GPU demosaic work on another machine/agent session.

**Branch/worktree:** uncommitted local changes on `jaer` repo (not necessarily committed/pushed as of June 22, 2026).

---

## Goal

Move CDAVIS color demosaic + DVS event compositing to the GPU while preserving:

- All existing **ColorMode** event accumulation on CPU (`DavisColorRenderer.updateEventMaps`)
- DAVIS menu options: Monochrome, Separate Colors, Color Correction, Auto White Balance
- Live camera + `.aedat` playback

**Decision:** GPU-only path for normal CDAVIS display (no CPU demosaic fallback except on shader failure or Separate Colors mode).

---

## Architecture

```
APS events  → pixBuffer (raw CFA luminance in .r, updated during rolling readout)
DVS events  → dvsEventsMap (CPU updateEventMaps, all ColorModes)
endFrame    → AWB factor compute (GPU path); copy pixBuffer → pixmap snapshot; skip CPU demosaic
Display     → CDavisGpuDisplayMethod uploads pixmap + dvsEventsMap; fragment shader demosaic/AWB/CC + event overlay
              → annotation layer (CPU, same as before)
              → Davis overlays: exposure text, IMU, histogram (CPU)
```

**Reference implementation:** `SpaceTimeRollingEventDisplayMethod.java` (shader install pattern).

**Why pixmap snapshot matters:** `pixBuffer` changes line-by-line during rolling shutter. Display must read **`pixmap`**, updated only at `endFrame`. Uploading live `pixBuffer` caused tearing; skipping pixmap copy broke Separate Colors (CPU reads pixmap → blank gray).

---

## Files changed / added

### Core GPU path

| File | Role |
|------|------|
| `src/eu/seebetter/ini/chips/davis/cdavis_composite.vert` | Full-screen quad, GLSL 120 |
| `src/eu/seebetter/ini/chips/davis/cdavis_composite.frag` | CFA demosaic, AWB, color correction, DVS alpha overlay |
| `src/eu/seebetter/ini/chips/davis/DavisBaseCamera.java` | Inner class `CDavisGpuDisplayMethod extends DavisDisplayMethod`; split `display()` into `displayFrameLayers()` + `displayDavisOverlays()` |
| `src/eu/seebetter/ini/chips/davis/DavisColorRenderer.java` | CPU demosaic refactor; `gpuDemosaicEnabled`; `computeAutoWhiteBalanceFactors()`; GPU `endFrame()` |
| `src/eu/seebetter/ini/chips/davis/CDAVIS.java` | Wires `CDavisGpuDisplayMethod`, `setGpuDemosaicEnabled(true)` |
| `src/net/sf/jaer/graphics/DavisRenderer.java` | `endFrame(ts, copyPixmap)` overload |
| `src/net/sf/jaer/graphics/ChipRendererDisplayMethodRGBA.java` | `displayQuad` made `protected` |

### CPU benchmark / legacy comparison

| File | Role |
|------|------|
| `src/eu/seebetter/ini/chips/davis/DavisColorRendererLegacy.java` | Pre-refactor demosaic path for benchmark |
| `src/eu/seebetter/ini/chips/davis/DavisColorRendererBenchmark.java` | Headless legacy vs refactored timing |
| `davis-color-renderer-benchmark.sh` | Runs benchmark via `build/classes` + `dist/jAER.jar` |

### Deleted / not used

- `CDavisGpuDisplayMethod.java` (standalone) — **removed**; must be inner class of `DavisBaseCamera` because `DavisDisplayMethod` is a non-static inner class.

---

## Key implementation details

### `DavisColorRenderer.endFrame()`

```java
if (gpuDemosaicEnabled && !isSeparateAPSByColor()) {
    if (isAutoWhiteBalance() && !isMonochrome()) {
        computeAutoWhiteBalanceFactors();
    }
    super.endFrame(ts, true);  // snapshot raw CFA to pixmap; skip processColorFrame()
} else {
    processColorFrame();       // CPU demosaic (or no-op if Separate Colors)
    super.endFrame(ts);
}
```

- `processColorFrame()` returns immediately when `isSeparateAPSByColor()` (quadrant layout stays raw in buffer).
- GPU display uploads **`getGpuDisplayPixmap()`** (not live `pixBuffer`).

### `CDavisGpuDisplayMethod` (in `DavisBaseCamera.java`)

- Overrides `displayFrameLayers()`.
- Uses GPU path when `isGpuDemosaicEnabled() && !isSeparateAPSByColor()`.
- Falls back to `super.displayFrameLayers()` (CPU `displayQuad`) on shader failure (`IOException | GLException`) or Separate Colors.
- Texture upload synchronized on `colorRenderer` to avoid concurrent modification during upload.
- Shaders loaded from classpath: `DavisBaseCamera.class.getResourceAsStream("cdavis_composite.vert")` (same package as `.java` sources).

### Fragment shader (`cdavis_composite.frag`)

- GLSL **120** (matches jAER GL2 fixed-function profile).
- **Do not use bitwise `&`** on integers — requires `GL_EXT_gpu_shader4`. Monochrome even-pixel alignment uses `(p.x / 2) * 2` instead of `p.x & ~1`.
- Uniforms: CFA layout, AWB factors, 3×3 CC matrix + offset, display flags.
- Events: if `uEvents.a > 0`, replace frame color (matches CPU alpha-test overlay behavior).

### Separate Colors mode

- Display: CPU path via `super.displayFrameLayers()`.
- `endFrame`: `processColorFrame()` no-op + pixmap copy — works once pixmap snapshot fix is in place.

---

## Build and run

### Compile / jar

```bash
export JAVA_HOME=/usr/lib/apache-netbeans/jdk   # or local JDK 21+
cd /path/to/jaer
ant compile    # → build/classes
ant jar        # → dist/jAER.jar  (required for jAERViewer script)
```

**Important:** `./jAERViewer_linux.sh` uses **`dist/jAER.jar` only** (not `build/classes`):

```bash
-classpath "$DIR/dist/jAER.jar:$DIR/jars/*:$DIR/lib/*"
```

After code changes: **`ant jar`** before testing in AEViewer.

**Dev shortcut:** prepend `build/classes` to classpath in the launch script to avoid jar rebuild during iteration.

### Launch AEViewer

```bash
./jAERViewer_linux.sh
# or with recording:
./jAERViewer_linux.sh "/path/to/CDAVIS-recording.aedat"
```

Test recording used in session:

```
~/Downloads/CDAVIS-2025-02-21T10-20-49+0100-CDAV0002-0 tobi lab cdaviis red cam 1.aedat
```

### CPU demosaic benchmark (headless)

```bash
./davis-color-renderer-benchmark.sh [aedat-file] [iterations] [max-frames]
```

Uses `build/classes` — run `ant compile` first. Typical result on test recording: **~1.17–1.20×** speedup on `processColorFrame` (~12 ms → ~10 ms); `endFrame` barely improved when pixmap copy included.

---

## Bugs fixed during session

| Issue | Cause | Fix |
|-------|-------|-----|
| Compile: inner class | `CDavisGpuDisplayMethod` extended `DavisDisplayMethod` from separate file | Moved to `DavisBaseCamera.CDavisGpuDisplayMethod` |
| Shader compile: `&` operator | GLSL 120 lacks integer bitwise ops | `(p.x / 2) * 2` for monochrome CFA block alignment |
| Uncaught GLException on shader fail | Only caught `IOException` | Also catch `GLException`; fall back to CPU |
| Tearing during playback | GPU uploaded live `pixBuffer` mid rolling readout | Upload `pixmap` snapshot; always `endFrame(ts, true)` on GPU path |
| Separate Colors → blank gray | CPU reads stale `pixmap` (never copied during GPU mode) | Same pixmap snapshot fix |
| jAERViewer not picking up edits | Script runs `dist/jAER.jar` | Run `ant jar` after changes |

---

## Current status (June 22, 2026)

- **Compiles and runs** in AEViewer with CDAVIS `.aedat` playback.
- GPU demosaic + events + annotation + DAVIS overlays working after pixmap snapshot fix.
- Separate Colors and menu toggles should work (CPU fallback reads updated pixmap).
- **Not committed** to git in this session unless user committed separately.

---

## Remaining work (plan phase 3+)

1. **Performance**
   - R32F texture upload (mosaic only needs `.r`) instead of full RGBA float
   - PBO async upload
   - End-to-end display FPS benchmark (GPU vs legacy CPU path)
   - Re-benchmark after skipping CPU demosaic (not just `processColorFrame`)

2. **Correctness**
   - Golden-frame test: GPU shader output vs `DavisColorRendererLegacy` / CPU `processColorFrame` (pixel diff tolerance)
   - Verify all ColorModes visually with events overlaid
   - Monochrome, AWB, CC toggle regression tests

3. **Robustness**
   - Reset/invalidate GPU textures on chip size or Separate Colors toggle if artifacts appear
   - Consider `glFinish()` or double-buffered event map if event tearing remains under async `repaint()` (non-active rendering)

4. **Cleanup**
   - Optional: extract `CDavisGpuDisplayMethod` to its own file by making `DavisDisplayMethod` static nested (larger refactor)
   - Update `DavisColorRendererBenchmark` for GPU path timing if desired

---

## Useful code locations

```java
// GPU toggle
DavisColorRenderer.setGpuDemosaicEnabled(true)   // set in CDAVIS constructor

// Display routing
DavisBaseCamera.CDavisGpuDisplayMethod.displayFrameLayers()

// Buffer accessors
DavisColorRenderer.getGpuDisplayPixmap()      // stable APS snapshot
DavisColorRenderer.getGpuDisplayDvsEventsMap()
DavisColorRenderer.getGpuDisplayAnnotateMap()
DavisColorRenderer.computeAutoWhiteBalanceFactors()

// CPU demosaic (benchmark / fallback)
DavisColorRenderer.processColorFrame()
DavisColorRendererLegacy                          // pre-optimization reference
```

---

## Agent transcript

Full conversation context (including earlier CPU refactor and benchmark work):

`/home/tobi/.cursor/projects/home-tobi-Dropbox-GitHub-SensorsINI-jaer/agent-transcripts/1e1b5ef8-769e-488d-a549-dbfcd3235b67/1e1b5ef8-769e-488d-a549-dbfcd3235b67.jsonl`

Plan file (if present on original machine):

`~/.cursor/plans/cdavis_gpu_demosaic_c131bc2b.plan.md`

---

## Quick verification checklist (new machine)

1. `git status` — confirm same changed files present (or cherry-pick/copy branch).
2. `JAVA_HOME=... ant jar`
3. `./jAERViewer_linux.sh` + CDAVIS recording
4. Check: color video, DVS events, no tearing, Separate Colors toggle
5. Optional: `./davis-color-renderer-benchmark.sh`
