Go to [install4j jAER installers on dropbox](https://www.dropbox.com/scl/fo/ibqmrztay51g7fg5d7mu3/h?rlkey=ulwos9lxmv38rrv5x1flic9z2&dl=0) to download installers.

See video [installing and updating jaer on YouTube](https://youtu.be/qQVt8_gwYVY).

This release adds live capture support for two new event-based vision cameras — the [NRV](https://nrv.kr/) DELTA01 (Samsung S5KRC1S, 960×720), and later the Infineon CX3 and FX10 cameras from NRV, and the [Prophesee](https://www.prophesee.ai/) EVK4 HD (Sony IMX636, 1280×720) — using cross-platform LibUSB with pipelined async bulk transfer. Other user-facing improvements include playback markers, SpaceTimeRolling 3D view improvements, and unified USB data-loss reporting.

The next planned release (3.0) will be a major refactoring of jAER to follow the DV processing model that an EventPacket contains only a single type of data (events/frames/imu samples/external events/other arbitrary data type), data files that use inivation AEDAT4 format, Google FlatBuffers, and a focus on efficiency and maintainability.

### Features

* **NRV DELTA01 (S5KRC1S) camera support** via LibUSB: pipelined async bulk readout, S5KRC1S packet parser, I2C biasing from NRV SDK `.txt` settings files, user-friendly bias panel (event threshold, ON/OFF balance, scan rate 100–2000 Hz), live slider updates, timestamp controls and diagnostics, and auto-apply settings on attach. See [src/nrv/README.md](https://github.com/SensorsINI/jaer/blob/master/src/nrv/README.md).
* **Prophesee EVK4 HD (IMX636) camera support** via LibUSB: EVT3 decoder, ISSD bring-up, bias UI with undo/redo and Revert-to-saved, export/import bias XML, serial number in device name, and pipelined async bulk readout with tuned defaults. See [src/prophesee/README.md](https://github.com/SensorsINI/jaer/blob/master/src/prophesee/README.md).
* **Playback markers** in _AEFileInputStream_: mark locations while reviewing a recording; export/import markers to CSV; marker _Actions_ with icons and accelerators in the _AEViewer Playback_ menu; mouse-wheel scrolling on the player slider; slider click jumps unconditionally to the clicked position (playback pauses).
* **_SpaceTimeRollingEventDisplayMethod_ 3D view** improvements: optional orthographic projection for contrast-maximization demos; fit-to-view on **Ctrl-0**; scrollable time window with status overlay. See the [SpaceTimeRolling introduction video](https://youtu.be/DK75rB8UFkA) from jaer-2.6.0.
* **Unified data-loss reporting** via _DroppedDataInfo_ in _AEViewer_ status (USB overrun, pool exhaustion, etc.).
* **Per-packet timestamp statistics** in the _Info_ filter overlay for live debugging of NRV and Prophesee streams.
* **Recording statistics** shown in the save confirmation dialog (event count, duration, start/end times).
* **EventFilter `@Description` annotation** to set property tooltips directly on fields (alternative to `setPropertyTooltip` in constructors).
* **_TargetLabeler_** moved to the tracking package; _DvsSliceAviWriter_ refactored; target-location export uses `-target-locations.txt` suffix.
* **USB pipeline tuning** in the _Control_ menu (scroll-wheel adjustment of FIFO size and buffer count for NRV and Prophesee); optional _UsbPipelineBench_ microbenchmarks and `scripts/run-jaer-usb-trace.bat` for comparing NRV vs EVK4 pipeline CSVs.
* **EngineeringFormat** for event-rate display in the status bar and renderer.
* **Ant `compile-fast` and `jar-fast` targets** for faster incremental developer builds.
* Removed obsolete `jaerappletviewer` package.

### Bug fixes and minor improvements

* Fixed NRV timestamp freeze at ~2147 s (32-bit µs wrap) and improved post-overrun parsing; fixed timestamp decode regression; press **`0`** to re-zero session origin.
* Fixed NRV startup crash from zero-capacity AE raw pool and shutdown hang; added OOM diagnostics.
* Fixed NRV/Prophesee event-only 2D DVS display and GrayLevel view background (mid-gray 0.5) on Windows.
* Fixed Prophesee EVK4 HD live streaming stall, EVT3 timestamps, hot-plug UX, and Windows launcher reliability; moved EVK4 to async USB transfer.
* Fixed pause/resume capture for NRV and EVK4 during file playback.
* Fixed live status bar stuck on “waiting for events” under high load; adaptive render skipping disabled during file playback by default; improved pure-DVS adaptive skipping.
* Fixed _AEViewer_ _ViewLoop_ NPE when `filterChain` is unset; graceful exit via `stopViewLoopForExit()` before cleanup on window close and _File → Exit_.
* Fixed _Info_ packet sniffing; clear enclosed _XYTypeFilter_ ROI on init.
* Fixed stray logging shortcut leaking into save-dialog filename; confirm-before-overwrite on save.
* Fixed NRV EVTH bias sliders and timestamp min-dt statistics; live bias updates from sliders.
* Fixed _PropheseeConfig_ Revert to restore last saved bias snapshot.
* Fixed ARS tuning, GrayMode startup, and NRV USB replug recovery.
* Fixed Ivy classpath rebuild after `ant clean` on Windows.
* Sped up _QuantizedSTCF_ denoising hot path; gated _SignalNoise_ statistics for lower overhead.
* Replaced broken _BackgroundActivityFilter_ with _QuantizedSTCF_ so denoising runs again.
* Improved CSV file open/parse error messages and comment-line handling.
* Truncated oversized status-message tooltips; added border spacing so dialog buttons stay visible.
* Fixed annoying timestamp in project.properties and cause pointless git conflicts on pulls between developers.

**Full Changelog**: https://github.com/SensorsINI/jaer/compare/2.7.2...2.8
