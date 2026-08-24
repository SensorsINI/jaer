# Active vs passive rendering

**View → View options → Active rendering enabled** (default **on**; pref
`AEViewer.activeRenderingEnabled`). The same checkbox is in AEViewer
Preferences.

This flag does **not** change monitor refresh, HiDPI, HDR, OLED, or
FreeSync/G-Sync. It only changes whether `AEViewer.ViewLoop` **waits** for the
OpenGL present or **posts** a paint and continues.

Recommend **on** for daily use. Use **off** with a high target FPS for
latency-sensitive applications (e.g. robots).

## What the code does

After each render, `ViewLoop.paintFrameAndAwaitVideoCapture` branches:

| Mode | Call | ViewLoop |
|------|------|----------|
| **On** (active) | `ChipCanvas.paintFrame()` → `glCanvas.display()` | Blocks until GL listeners finish and the back buffer is swapped (`GLCanvas.setAutoSwapBufferMode(true)`) |
| **Off** (passive) | `chipCanvas.repaint()` → `glCanvas.repaint()` | Returns immediately; AWT may coalesce several requests into one paint |

`ChipCanvas` javadoc still mentions AWT `BufferStrategy` page-flipping. The
`strategy` field is unused. Display is a double-buffered JOGL `GLCanvas`.
`gl.setSwapInterval(0)` is commented out, so the driver default applies
(usually vsync on).

AVI / video export **forces active rendering on**: frame capture runs inside
`JaerAviWriter.annotate` during `display()`. Passive `repaint()` is not
synchronized with the view loop.

The skip-chip overlay path (`setSkipChipRenderingOverlay`) always calls
`paintFrame()`, regardless of this flag.

## Historical origin

“Active rendering” is a Java 2D / AWT game-loop term from the early 2000s:
call `display()` / `BufferStrategy.show()` from the animation thread instead of
AWT `repaint()` → `paint()` on the EDT. The goal then was CRT tearing and slow
unsynchronized paints.

Windowed apps now go through a compositor (Windows DWM, macOS, Linux). The
monitor is already presented tear-free at its refresh rate. The remaining
differences are pipeline sync, not “does the panel tear.”

## What still changes on modern displays

**Who waits for the present.** On, `display()` typically stalls in
`SwapBuffers` until the next vsync (or DWM’s next compose). That wait is inside
ViewLoop, so `FrameRater.delayForDesiredFPS()` sleeps only the *remaining* time
after paint. On a 60 Hz panel that can be ~16 ms per frame. On 120/144/240 Hz
it is shorter, so active rendering is less of a cap.

Off, ViewLoop is paced only by processing + `FrameRater`. The screen still
updates at compositor/refresh rate; extra loop iterations are dropped when AWT
coalesces `repaint()`.

**Status-bar FPS vs what you see.** Off can make **XX/YYfps** look higher
because paint wait is not in the loop. The visible image does not update that
fast.

**Content races (not monitor tearing).** Renderer pixmaps/textures are filled
on ViewLoop. Off, the GL thread can draw while the next packet is already
overwriting those buffers. That shows as mixed old/new event maps (see the
CDAVIS GPU-path note in [`CDAVIS_GPU_DEMOSAIC.md`](CDAVIS_GPU_DEMOSAIC.md)).
The compositor cannot fix a half-updated texture.

**Latency and ordering.** On: the packet just rendered is the one swapped, in
order. Off: extra EDT delay and possible skipped frames; ViewLoop can start
the next acquire/filter cycle without waiting for GPU present.

**Windowed OpenGL on Windows.** Legacy WGL presents are still a DWM blit, not
DXGI flip-model. High-Hz and mixed-refresh setups can stutter or wait on the
wrong output. That is a driver/compositor issue; this checkbox does not select
a better present path.

## When to use which

| Use | Setting |
|-----|---------|
| Daily viewing, demos, teaching | **On** |
| Video / AVI export | **On** (forced if you start export while off) |
| Robots / closed-loop control, high target FPS | **Off**, and raise **View → View options → Set rendering rate** so ViewLoop is not sleeping ~16 ms per frame |

For load under active rendering, **Adaptive render skipping** is the intended
valve: it skips work without unsyncing paint.

## Code

- [`AEViewer.ViewLoop.paintFrameAndAwaitVideoCapture`](../src/net/sf/jaer/graphics/AEViewer.java)
- [`ChipCanvas.paintFrame`](../src/net/sf/jaer/graphics/ChipCanvas.java) / `repaint()`
- [`AEViewer.FrameRater.delayForDesiredFPS`](../src/net/sf/jaer/graphics/AEViewer.java)
- Pipeline overview: [`README-jaer3.md`](README-jaer3.md) (Rendering / OpenGL)
