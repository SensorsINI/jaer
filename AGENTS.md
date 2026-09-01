# jAER agent notes

jAER 3 (`master`) is an Ant + Ivy Java desktop app for event cameras. Not Maven/Gradle.

## Pipeline

USB / file / network → typed `PacketBundle` (or legacy `AEPacketRaw` + `extractBundle`) → `AEViewer.ViewLoop` → `FilterChain.filterBundle` → optional AEDAT-4/2 record → `AEChipRenderer` / `ChipCanvas`.

Canonical architecture: `docs/README-jaer3.md`. Cursor attaches that file when Java under `src/` is in context (see `.cursor/rules/jaer3-architecture.mdc`). Read it before changing the live path, filters, or rendering. USB enumeration / EDT / Interface menu: `docs/README-usb.md`.

## Where to look

| Area | Path |
|------|------|
| Viewer loop | `src/net/sf/jaer/graphics/AEViewer.java` (`ViewLoop`) |
| Typed packets | `src/net/sf/jaer/event/PacketBundle*.java`, `PacketType` |
| Filters | `src/net/sf/jaer/eventprocessing/` (`FilterChain`, `EventFilter2D`) |
| USB / Interface | `src/net/sf/jaer/hardwareinterface/` |
| Chips | `src/ch/unizh/ini/jaer/chip/`, `src/nrv/`, `src/prophesee/` |
| Render | `ChipCanvas`, `AEChipRenderer`, `DavisRenderer` |

## Logs

`ant run` / console is **INFO** (`conf/Logging.properties`). USB and ViewLoop detail is **FINE** in the rotating file `%t/jaer/jAER-%g.log` (`java.io.tmpdir/jaer/`; on Windows `%TEMP%\jaer\`). If the console cannot explain a hang, missed hotplug, or USB open failure, read the newest **`jAER-0.log`** there (not `jaer/logs/` in the repo). For USB open/close/multicamera failures, check tmpdir usb-open-trace.log.  For issues relaeted to human interface, check for possible interaction log stored in the tmpdir/jaer/interactions log.

## Build

`ant compile` is the source of truth (JDK **25**, `javac.source`/`target` 25; `ant check-jdk` if the JVM is too old). New `AEChip` / `EventFilter2D` types appear in Customize only after `ant compile` (allowlist written into `jAER.jar`). IDE compile-on-save is not enough. Packaged installers load only types from that list.

Plans for this repo: `.cursor/plans/` (CreatePlan’s user-global file must be copied there the same turn). Shell: bash on Linux/WSL, PowerShell 5.1 on native Windows (`.cursor/rules/shell-by-platform.mdc`).
