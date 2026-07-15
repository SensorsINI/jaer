#!/usr/bin/env python3
"""
Analyze jAER text export of NRV recording events (timestamp in seconds).

Usage:
  python scripts/analyze-nrv-recording-events.py NRV-recording-events.txt [slice_us]

Reports timestamp clustering and simulated readPacketByTime slice occupancy.
"""
import sys
from collections import Counter


def parse_args():
    path = sys.argv[1] if len(sys.argv) > 1 else "NRV-recording-events.txt"
    slice_us = int(sys.argv[2]) if len(sys.argv) > 2 else 3000
    return path, slice_us


def load_events(path):
    ts_us = []
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split()
            if len(parts) < 4:
                continue
            try:
                t_s = float(parts[0])
            except ValueError:
                continue
            ts_us.append(int(round(t_s * 1_000_000)))
    return ts_us


def analyze(ts_us, slice_us):
    if not ts_us:
        print("No events found")
        return

    n = len(ts_us)
    t_min, t_max = ts_us[0], ts_us[-1]
    unique = len(set(ts_us))
    span_us = t_max - t_min

    print(f"Events: {n:,}")
    print(f"Time span: {span_us / 1e6:.3f} s ({span_us:,} us)")
    print(f"Unique timestamps: {unique:,} ({100 * unique / n:.2f}% of events)")
    print(f"Avg events per unique timestamp: {n / unique:.1f}")
    print(f"Slice simulation: {slice_us} us ({slice_us / 1000:.3f} ms)")
    print()

    # Inter-event gaps on sorted unique timestamps
    uniq_sorted = sorted(set(ts_us))
    if len(uniq_sorted) > 1:
        gaps = [uniq_sorted[i + 1] - uniq_sorted[i] for i in range(len(uniq_sorted) - 1)]
        gap_c = Counter(gaps)
        print("Top gaps between unique timestamps (us):")
        for g, c in gap_c.most_common(12):
            print(f"  {g:8d} us: {c:6d} occurrences")
        print(f"  gap=0 (duplicate ts bucket): N/A at unique level")
        print(f"  median gap: {sorted(gaps)[len(gaps) // 2]} us")
        print()

    # Simulate readPacketByTime: windows [start, start+slice_us)
    # jAER uses int us timestamps; file export uses float seconds
    start = ts_us[0]
    end = t_max + 1
    slice_counts = []
    idx = 0
    window = start
    while window < end:
        w_end = window + slice_us
        count = 0
        while idx < n and ts_us[idx] < window:
            idx += 1
        j = idx
        while j < n and ts_us[j] < w_end:
            count += 1
            j += 1
        slice_counts.append(count)
        window = w_end

    total_slices = len(slice_counts)
    empty = sum(1 for c in slice_counts if c == 0)
    tiny = sum(1 for c in slice_counts if 0 < c < 10)
    sparse = sum(1 for c in slice_counts if 0 < c < 100)
    big = sum(1 for c in slice_counts if c >= 10000)

    print(f"Simulated slices: {total_slices:,}")
    print(f"  empty:     {empty:6d} ({100 * empty / total_slices:.1f}%)")
    print(f"  tiny(<10): {tiny:6d} ({100 * tiny / total_slices:.1f}%)")
    print(f"  sparse(<100): {sparse:6d} ({100 * sparse / total_slices:.1f}%)")
    print(f"  >=10k:     {big:6d} ({100 * big / total_slices:.1f}%)")
    if slice_counts:
        non_zero = [c for c in slice_counts if c > 0]
        print(f"  events/slice when non-empty: min={min(non_zero)} max={max(slice_counts)} "
              f"avg={sum(slice_counts) / total_slices:.0f} median={sorted(slice_counts)[total_slices // 2]}")
    print()

    # Histogram
    bins = [
        ("0", 0),
        ("1-9", 0),
        ("10-99", 0),
        ("100-999", 0),
        ("1k-9k", 0),
        ("10k+", 0),
    ]
    for c in slice_counts:
        if c == 0:
            bins[0] = (bins[0][0], bins[0][1] + 1)
        elif c < 10:
            bins[1] = (bins[1][0], bins[1][1] + 1)
        elif c < 100:
            bins[2] = (bins[2][0], bins[2][1] + 1)
        elif c < 1000:
            bins[3] = (bins[3][0], bins[3][1] + 1)
        elif c < 10000:
            bins[4] = (bins[4][0], bins[4][1] + 1)
        else:
            bins[5] = (bins[5][0], bins[5][1] + 1)
    print("Slice occupancy histogram:")
    for label, count in bins:
        bar = "#" * int(50 * count / max(1, total_slices))
        print(f"  {label:8s} {count:6d} ({100 * count / total_slices:5.1f}%) {bar}")
    print()

    # Longest run of tiny/empty slices
    def max_run(pred):
        best = cur = 0
        for c in slice_counts:
            if pred(c):
                cur += 1
                best = max(best, cur)
            else:
                cur = 0
        return best

    print(f"Max consecutive empty slices: {max_run(lambda c: c == 0)}")
    print(f"Max consecutive tiny(<10) slices: {max_run(lambda c: 0 < c < 10)}")
    print()

    # Example tiny slice windows
    print("Sample tiny(<10) slice windows (start_us, count, unique_ts in window):")
    shown = 0
    window = start
    idx = 0
    while window < end and shown < 8:
        w_end = window + slice_us
        count = 0
        ts_in_window = []
        while idx < n and ts_us[idx] < window:
            idx += 1
        j = idx
        while j < n and ts_us[j] < w_end:
            ts_in_window.append(ts_us[j])
            count += 1
            j += 1
        if 0 < count < 10:
            u = len(set(ts_in_window))
            print(f"  [{window}, {w_end}) us  events={count} uniqueTs={u} "
                  f"ts_values={sorted(set(ts_in_window))[:5]}")
            shown += 1
        window = w_end


def main():
    path, slice_us = parse_args()
    print(f"Analyzing: {path}")
    print()
    ts_us = load_events(path)
    ts_us.sort()
    analyze(ts_us, slice_us)


if __name__ == "__main__":
    main()
