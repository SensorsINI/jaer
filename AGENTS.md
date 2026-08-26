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

## Build

`ant compile` is the source of truth. New `AEChip` / `EventFilter2D` types appear in Customize only after `ant compile` (allowlist written into `jAER.jar`). IDE compile-on-save is not enough. Packaged installers load only types from that list.

Plans for this repo: `.cursor/plans/`. Shell: bash on Linux/WSL, PowerShell 5.1 on native Windows (`.cursor/rules/shell-by-platform.mdc`).
