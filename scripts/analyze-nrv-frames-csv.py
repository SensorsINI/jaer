#!/usr/bin/env python3
"""Quick analysis of jaer-nrv-frames.csv playback rows."""
import csv
import sys
from collections import Counter
from statistics import mean, median

path = sys.argv[1] if len(sys.argv) > 1 else r"C:\temp\jaer-nrv-frames.csv"
rows = list(csv.DictReader(open(path, newline="")))

by_kind = Counter(r["kind"] for r in rows)
print("Row counts by kind:")
for k, v in sorted(by_kind.items()):
    print(f"  {k}: {v}")

pb = [r for r in rows if r["kind"] == "playback_slice"]
skip = [r for r in rows if r["kind"] == "playback_skip_render"]
print(f"\nPlayback slices: {len(pb)}")
print(f"Playback skip_render: {len(skip)}")

if not pb:
    print("No playback rows found")
    sys.exit(0)

empty = [r for r in pb if int(r["field10"]) == 0]
nonempty = [r for r in pb if int(r["field10"]) > 0]
print(f"Empty slices (numEvents=0): {len(empty)} ({100 * len(empty) / len(pb):.1f}%)")
print(f"Non-empty slices: {len(nonempty)} ({100 * len(nonempty) / len(pb):.1f}%)")

slice_us = Counter(int(r["field9"]) for r in pb)
print("\nSlice interval (sliceUs) distribution:")
for us, c in sorted(slice_us.items()):
    sub = [r for r in pb if int(r["field9"]) == us]
    e = sum(1 for r in sub if int(r["field10"]) == 0)
    print(f"  {us:6d} us: {c:5d} slices, {e:5d} empty ({100 * e / c:5.1f}%)")

max_run = 0
cur = 0
runs = []
for r in pb:
    if int(r["field10"]) == 0:
        cur += 1
        max_run = max(max_run, cur)
    else:
        if cur:
            runs.append(cur)
        cur = 0
if cur:
    runs.append(cur)

print(f"\nConsecutive empty runs: {len(runs)}")
if runs:
    print(f"  max={max_run} avg={mean(runs):.2f} median={median(runs):.1f}")
    run_dist = Counter(runs)
    for n in range(1, 6):
        if n in run_dist:
            print(f"  run length {n}: {run_dist[n]}")
    ge3 = sum(v for k, v in run_dist.items() if k >= 3)
    print(f"  runs length >= 3: {ge3} ({100 * ge3 / len(runs):.1f}% of runs)")

if empty:
    print(f"\nskip_render / empty slices: {len(skip)}/{len(empty)} ({100 * len(skip) / len(empty):.1f}%)")

if nonempty:
    ev = [int(r["field10"]) for r in nonempty]
    span = [int(r["field12"]) for r in nonempty if int(r["field12"]) > 0]
    print(f"\nNon-empty slices: events avg={mean(ev):.1f} min={min(ev)} max={max(ev)}")
    if span:
        print(f"  ts span avg={mean(span):.0f} us min={min(span)} max={max(span)} us")

# Timeline: sliceUs changes (user stepped 20ms -> 800us -> 4ms)
print("\nSlice interval timeline (first row per interval change):")
prev = None
for i, r in enumerate(pb):
    us = int(r["field9"])
    if us != prev:
        ev = int(r["field10"])
        print(f"  index {i}: sliceUs={us} events={ev}")
        prev = us

# Example 3+ empty streak
cur = 0
start = -1
for i, r in enumerate(pb):
    if int(r["field10"]) == 0:
        if cur == 0:
            start = i
        cur += 1
    else:
        if cur >= 3:
            print(f"\nExample empty streak (index {start}-{i - 1}, len={cur}):")
            for rr in pb[start : min(i, start + 5)]:
                print(
                    f"  sliceUs={rr['field9']} events={rr['field10']} "
                    f"minTs={rr['field11']} maxTs={rr['field12']}"
                )
            if i < len(pb):
                rr = pb[i]
                print(
                    f"  then: sliceUs={rr['field9']} events={rr['field10']} "
                    f"minTs={rr['field11']} maxTs={rr['field12']}"
                )
            break
        cur = 0

    low = [r for r in pb if int(r["field10"]) < 100]
    tiny = [r for r in pb if int(r["field10"]) < 10]
    print(f"\nSparse slices: <100 events={len(low)}  <10 events={len(tiny)}")

    spans = [int(r["field12"]) for r in pb if int(r["field10"]) > 0 and int(r["field12"]) > 0]
    if spans:
        print(f"Non-empty ts span: avg={mean(spans):.0f} us max={max(spans)} us")
    bigspan = [r for r in pb if int(r["field10"]) > 0 and int(r["field12"]) > 50000]
    print(f"Slices with internal span > 50 ms: {len(bigspan)}")

    gaps = []
    prev_max = None
    for r in pb:
        n = int(r["field10"])
        us = int(r["field9"])
        if n == 0:
            continue
        mn = int(r["field11"])
        mx = int(r["field12"])
        if prev_max is not None:
            gaps.append((mn - prev_max, us, n))
        prev_max = mx

    if gaps:
        pos = [g for g in gaps if g[0] > 0]
        print(f"\nInter-slice gap (minTs - prevMaxTs): avg={mean([g[0] for g in pos]):.0f} us max={max(g[0] for g in pos)} us")
        big = [g for g in gaps if g[0] > g[1]]
        print(f"Gaps larger than sliceUs: {len(big)} / {len(gaps)} ({100 * len(big) / len(gaps):.1f}%)")
        print("Largest timestamp holes vs sliceUs:")
        for g in sorted(big, key=lambda x: -x[0])[:10]:
            print(f"  hole {g[0]:8d} us  sliceUs={g[1]:5d}  events={g[2]}")
