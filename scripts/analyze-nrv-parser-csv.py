#!/usr/bin/env python3
"""Summarize jaer-nrv-parser.csv from NRVParserTrace (live USB decode)."""

import csv
import sys
from collections import Counter
from pathlib import Path


def main() -> int:
    path = Path(sys.argv[1] if len(sys.argv) > 1 else "C:/temp/jaer-nrv-parser.csv")
    if not path.is_file():
        print(f"File not found: {path}")
        return 1

    ms_buckets: list[int] = []
    frame_events: list[int] = []
    delta_refs: list[int] = []
    ts_gaps: list[int] = []
    prev_ts: int | None = None

    with path.open(newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            kind = row.get("kind", "")
            if kind == "ms_bucket":
                events = int(row["eventCount"])
                output_ts = int(row["outputTsUs"])
                ms_buckets.append(events)
                if prev_ts is not None:
                    ts_gaps.append(output_ts - prev_ts)
                prev_ts = output_ts
            elif kind == "frame_end":
                frame_events.append(int(row["eventCount"]))
                dr = int(row["deltaRefMs"])
                if dr >= 0:
                    delta_refs.append(dr)

    print(f"Analyzing: {path}\n")
    if not ms_buckets and not frame_events:
        print("No ms_bucket or frame_end rows found.")
        return 0

    if ms_buckets:
        ms_buckets.sort()
        n = len(ms_buckets)
        tiny = sum(1 for e in ms_buckets if e < 10)
        sparse = sum(1 for e in ms_buckets if 10 <= e < 100)
        burst = sum(1 for e in ms_buckets if e >= 1000)
        print(f"ms_bucket rows: {n}")
        print(f"  events/bucket: min={ms_buckets[0]} max={ms_buckets[-1]} "
              f"median={ms_buckets[n // 2]} avg={sum(ms_buckets) / n:.1f}")
        print(f"  tiny(<10): {tiny} ({100 * tiny / n:.1f}%)")
        print(f"  sparse(10-99): {sparse} ({100 * sparse / n:.1f}%)")
        print(f"  burst(>=1000): {burst} ({100 * burst / n:.1f}%)")
        if ts_gaps:
            gap_hist = Counter(ts_gaps)
            print("\n  outputTs step (us) top:")
            for gap, cnt in gap_hist.most_common(8):
                print(f"    {gap:6d} us: {cnt:5d}")

    if frame_events:
        frame_events.sort()
        fn = len(frame_events)
        print(f"\nframe_end rows: {fn}")
        print(f"  events/frame: min={frame_events[0]} max={frame_events[-1]} "
              f"median={frame_events[fn // 2]} avg={sum(frame_events) / fn:.1f}")
        if delta_refs:
            dr = Counter(delta_refs)
            print(f"  deltaRefMs top: {dr.most_common(5)}")

    print("\nInterpretation:")
    print("  - If ms_bucket tiny% is high LIVE but events/frame is steady → timestamp decode")
    print("    (many events share one outputTs; 1 ms quantization).")
    print("  - If events/frame is also bursty (wide min/max) → sensor or USB/event stream.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
