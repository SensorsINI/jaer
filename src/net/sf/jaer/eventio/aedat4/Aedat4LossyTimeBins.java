package net.sf.jaer.eventio.aedat4;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/**
 * Lossy AEDAT-4 polarity payloads. Microsecond timestamps are right-shifted
 * (default 10 bits, about 1 ms). Events that share that bin, pixel, and
 * polarity collapse to a count. Playback expands each count into that many
 * events at {@code (t >>> shift) << shift}, in first-seen order.
 * <p>
 * With {@link Accumulator#Accumulator(int, boolean) polarity collapsing}, On and
 * Off of the same pixel share one record and event order inside the bin is not
 * kept. Playback emits that pixel's On events, then its Off events. Each count
 * is one unsigned byte (0–255). A pixel that exceeds 255 of one polarity in the
 * bin continues in the next record.
 * <p>
 * The bytes are the uncompressed EVTS payload. The file's existing LZ4 or
 * ZSTD setting still wraps them. Frames and IMU stay FlatBuffers.
 * <p>
 * Layout, little-endian: magic {@code LBEV}, version u16, bin count u32,
 * then each bin as restored Unix µs u64, record count u32, and records.
 * Version 1 records are x u16, y u16, polarity u8, count u16 (a count above
 * 65535 splits into back-to-back records). Version 2 records are x u16, y u16,
 * on count u8, off count u8.
 */
public final class Aedat4LossyTimeBins {

    public static final int SHIFT_OFF = -1;
    public static final int SHIFT_MIN = 0;
    public static final int SHIFT_MAX = 24;
    public static final int SHIFT_DEFAULT = 10;
    public static final int VERSION = 1;
    /** On and Off counts packed in one record. Order inside the bin is not kept. */
    public static final int VERSION_PAIRS = 2;
    /** One version-1 record stores at most this many events. */
    public static final int COUNT_MAX = 65535;
    /** One side of a version-2 record. The next record continues a hotter pixel. */
    public static final int PAIR_COUNT_MAX = 255;
    private static final int RECORD_BYTES = 7;
    private static final int PAIR_RECORD_BYTES = 6;

    private Aedat4LossyTimeBins() {
    }

    public static int clampShift(int shift) {
        if (shift < SHIFT_MIN) {
            return SHIFT_MIN;
        }
        if (shift > SHIFT_MAX) {
            return SHIFT_MAX;
        }
        return shift;
    }

    /** Restored bin timestamp. Shift 0 leaves the microsecond time unchanged. */
    public static long quantizeUnixUs(long timestampUs, int shift) {
        if (shift <= 0) {
            return timestampUs;
        }
        int s = clampShift(shift);
        return (timestampUs >>> s) << s;
    }

    public static boolean isLossyPayload(ByteBuffer flat) {
        if (flat == null || flat.remaining() < 4) {
            return false;
        }
        int p = flat.position();
        return flat.get(p) == 'L' && flat.get(p + 1) == 'B'
                && flat.get(p + 2) == 'E' && flat.get(p + 3) == 'V';
    }

    /** One open time bin, plus bins already closed by a newer timestamp. */
    public static final class Accumulator {
        private final int shift;
        private final boolean collapsePolarities;
        private boolean open;
        private long openTs;
        private int[] xs = new int[16];
        private int[] ys = new int[16];
        private int[] types = new int[16];
        private int[] counts = new int[16];
        private int[] onCounts = new int[16];
        private int[] offCounts = new int[16];
        private int n;
        private OpenMap map = new OpenMap(32);
        private final ArrayList<Bin> completed = new ArrayList<>();

        public Accumulator(int shift) {
            this(shift, false);
        }

        /**
         * @param collapsePolarities when true, one record holds On and Off counts
         *                           for a pixel and playback emits On then Off
         */
        public Accumulator(int shift, boolean collapsePolarities) {
            this.shift = clampShift(shift);
            this.collapsePolarities = collapsePolarities;
        }

        public boolean hasOpenBin() {
            return open && n > 0;
        }

        public void add(long unixUs, int x, int y, boolean on) {
            long q = quantizeUnixUs(unixUs, shift);
            if (!open || q != openTs) {
                closeOpen();
                open = true;
                openTs = q;
                n = 0;
                map.clear();
            }
            int type = on ? 1 : 0;
            int x16 = x & 0xffff;
            int y16 = y & 0xffff;
            if (collapsePolarities) {
                addCollapsed(x16, y16, on);
                return;
            }
            long key = key(x16, y16, type);
            int slot = map.get(key);
            if (slot < 0) {
                append(x16, y16, type, 1);
                map.put(key, n - 1);
                return;
            }
            if (counts[slot] >= COUNT_MAX) {
                insertAfter(slot, x16, y16, type, 1);
                map.put(key, slot + 1);
                return;
            }
            counts[slot]++;
        }

        /** Bins closed so far, not the bin still accepting events. */
        public Encoded pollCompleted() {
            if (completed.isEmpty()) {
                return null;
            }
            Encoded encoded = encode(completed);
            completed.clear();
            return encoded;
        }

        /** Close the open bin and return every bin not yet polled. */
        public Encoded finish() {
            closeOpen();
            return pollCompleted();
        }

        private void closeOpen() {
            if (!open || n == 0) {
                open = false;
                n = 0;
                return;
            }
            completed.add(collapsePolarities
                    ? Bin.copyPairs(openTs, xs, ys, onCounts, offCounts, n)
                    : Bin.copyOf(openTs, xs, ys, types, counts, n));
            open = false;
            n = 0;
            map.clear();
        }

        private void append(int x, int y, int type, int count) {
            ensure(n + 1);
            xs[n] = x;
            ys[n] = y;
            types[n] = type;
            counts[n] = count;
            n++;
        }

        /** Overflow record sits immediately after the full one (first-seen order). */
        private void insertAfter(int slot, int x, int y, int type, int count) {
            ensure(n + 1);
            int tail = n - slot - 1;
            if (tail > 0) {
                System.arraycopy(xs, slot + 1, xs, slot + 2, tail);
                System.arraycopy(ys, slot + 1, ys, slot + 2, tail);
                System.arraycopy(types, slot + 1, types, slot + 2, tail);
                System.arraycopy(counts, slot + 1, counts, slot + 2, tail);
            }
            xs[slot + 1] = x;
            ys[slot + 1] = y;
            types[slot + 1] = type;
            counts[slot + 1] = count;
            n++;
            map.shiftSlotsAbove(slot);
        }

        private void addCollapsed(int x, int y, boolean on) {
            long key = ((long) x << 16) | y;
            int slot = map.get(key);
            if (slot < 0) {
                appendPair(x, y, on ? 1 : 0, on ? 0 : 1);
                map.put(key, n - 1);
                return;
            }
            int[] side = on ? onCounts : offCounts;
            if (side[slot] >= PAIR_COUNT_MAX) {
                insertPairAfter(slot, x, y, on ? 1 : 0, on ? 0 : 1);
                map.put(key, slot + 1);
                return;
            }
            side[slot]++;
        }

        private void appendPair(int x, int y, int on, int off) {
            ensure(n + 1);
            xs[n] = x;
            ys[n] = y;
            onCounts[n] = on;
            offCounts[n] = off;
            n++;
        }

        private void insertPairAfter(int slot, int x, int y, int on, int off) {
            ensure(n + 1);
            int tail = n - slot - 1;
            if (tail > 0) {
                System.arraycopy(xs, slot + 1, xs, slot + 2, tail);
                System.arraycopy(ys, slot + 1, ys, slot + 2, tail);
                System.arraycopy(onCounts, slot + 1, onCounts, slot + 2, tail);
                System.arraycopy(offCounts, slot + 1, offCounts, slot + 2, tail);
            }
            xs[slot + 1] = x;
            ys[slot + 1] = y;
            onCounts[slot + 1] = on;
            offCounts[slot + 1] = off;
            n++;
            map.shiftSlotsAbove(slot);
        }

        private void ensure(int need) {
            if (need <= xs.length) {
                return;
            }
            int cap = xs.length;
            while (cap < need) {
                cap *= 2;
            }
            xs = java.util.Arrays.copyOf(xs, cap);
            ys = java.util.Arrays.copyOf(ys, cap);
            types = java.util.Arrays.copyOf(types, cap);
            counts = java.util.Arrays.copyOf(counts, cap);
            onCounts = java.util.Arrays.copyOf(onCounts, cap);
            offCounts = java.util.Arrays.copyOf(offCounts, cap);
        }
    }

    public static final class Encoded {
        public final byte[] payload;
        public final long expanded;
        public final long tMin;
        public final long tMax;

        Encoded(byte[] payload, long expanded, long tMin, long tMax) {
            this.payload = payload;
            this.expanded = expanded;
            this.tMin = tMin;
            this.tMax = tMax;
        }
    }

    /**
     * Random access by expanded event index. Prefix sums of counts, not one
     * stored timestamp per event.
     */
    public static final class View {
        private final int records;
        private final long expanded;
        private final long[] recEnd;
        private final long[] recTs;
        private final int[] recX;
        private final int[] recY;
        private final int[] recType;

        private View(int records, long expanded, long[] recEnd, long[] recTs, int[] recX, int[] recY, int[] recType) {
            this.records = records;
            this.expanded = expanded;
            this.recEnd = recEnd;
            this.recTs = recTs;
            this.recX = recX;
            this.recY = recY;
            this.recType = recType;
        }

        public int expandedLength() {
            return expanded > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) expanded;
        }

        public long expandedLengthLong() {
            return expanded;
        }

        public long timestamp(int expandedIndex) {
            int r = recordAt(expandedIndex);
            return recTs[r];
        }

        public int x(int expandedIndex) {
            return recX[recordAt(expandedIndex)];
        }

        public int y(int expandedIndex) {
            return recY[recordAt(expandedIndex)];
        }

        /** 1 = On, 0 = Off. */
        public int type(int expandedIndex) {
            return recType[recordAt(expandedIndex)];
        }

        private int recordAt(int expandedIndex) {
            if (expandedIndex < 0 || expandedIndex >= expanded) {
                throw new IndexOutOfBoundsException(expandedIndex + " of " + expanded);
            }
            int lo = 0;
            int hi = records - 1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                long start = mid == 0 ? 0L : recEnd[mid - 1];
                long end = recEnd[mid];
                if (expandedIndex < start) {
                    hi = mid - 1;
                } else if (expandedIndex >= end) {
                    lo = mid + 1;
                } else {
                    return mid;
                }
            }
            throw new IndexOutOfBoundsException(expandedIndex + " of " + expanded);
        }
    }

    public static View decode(ByteBuffer src) throws IOException {
        if (src == null) {
            throw new IOException("missing lossy EVTS payload");
        }
        ByteBuffer buf = src.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        if (!isLossyPayload(buf)) {
            throw new IOException("EVTS payload is not LBEV");
        }
        buf.position(buf.position() + 4);
        if (buf.remaining() < 6) {
            throw new IOException("truncated LBEV header");
        }
        int version = buf.getShort() & 0xffff;
        if (version != VERSION && version != VERSION_PAIRS) {
            throw new IOException("unsupported LBEV version " + version);
        }
        boolean pairs = version == VERSION_PAIRS;
        int recordBytes = pairs ? PAIR_RECORD_BYTES : RECORD_BYTES;
        int nBins = buf.getInt();
        if (nBins < 0) {
            throw new IOException("negative LBEV bin count " + nBins);
        }
        int[] xs = new int[16];
        int[] ys = new int[16];
        int[] types = new int[16];
        long[] ends = new long[16];
        long[] tss = new long[16];
        int n = 0;
        long expanded = 0;
        for (int b = 0; b < nBins; b++) {
            if (buf.remaining() < 12) {
                throw new IOException("truncated LBEV bin " + b);
            }
            long ts = buf.getLong();
            int nRecs = buf.getInt();
            if (nRecs < 0) {
                throw new IOException("negative LBEV record count");
            }
            long need = (long) nRecs * recordBytes;
            if (need > buf.remaining()) {
                throw new IOException("truncated LBEV records in bin " + b);
            }
            for (int r = 0; r < nRecs; r++) {
                int x = buf.getShort() & 0xffff;
                int y = buf.getShort() & 0xffff;
                if (pairs) {
                    int on = buf.get() & 0xff;
                    int off = buf.get() & 0xff;
                    if (on == 0 && off == 0) {
                        throw new IOException("LBEV pair counts are 0");
                    }
                    if (on > 0) {
                        xs = grown(xs, n);
                        ys = grown(ys, n);
                        types = grown(types, n);
                        ends = grownLong(ends, n);
                        tss = grownLong(tss, n);
                        n = appendView(xs, ys, types, ends, tss, n, x, y, 1, on, ts);
                        expanded += on;
                    }
                    if (off > 0) {
                        xs = grown(xs, n);
                        ys = grown(ys, n);
                        types = grown(types, n);
                        ends = grownLong(ends, n);
                        tss = grownLong(tss, n);
                        n = appendView(xs, ys, types, ends, tss, n, x, y, 0, off, ts);
                        expanded += off;
                    }
                    continue;
                }
                int type = buf.get() & 0xff;
                int count = buf.getShort() & 0xffff;
                if (count == 0) {
                    throw new IOException("LBEV record count is 0");
                }
                xs = grown(xs, n);
                ys = grown(ys, n);
                types = grown(types, n);
                ends = grownLong(ends, n);
                tss = grownLong(tss, n);
                n = appendView(xs, ys, types, ends, tss, n, x, y, type == 0 ? 0 : 1, count, ts);
                expanded += count;
            }
        }
        return new View(n, expanded,
                java.util.Arrays.copyOf(ends, n),
                java.util.Arrays.copyOf(tss, n),
                java.util.Arrays.copyOf(xs, n),
                java.util.Arrays.copyOf(ys, n),
                java.util.Arrays.copyOf(types, n));
    }

    private static Encoded encode(ArrayList<Bin> bins) {
        int records = 0;
        long expanded = 0;
        long tMin = Long.MAX_VALUE;
        long tMax = Long.MIN_VALUE;
        boolean pairs = !bins.isEmpty() && bins.get(0).pairs;
        int recordBytes = pairs ? PAIR_RECORD_BYTES : RECORD_BYTES;
        for (int i = 0; i < bins.size(); i++) {
            Bin bin = bins.get(i);
            records += bin.n;
            for (int r = 0; r < bin.n; r++) {
                expanded += pairs ? (bin.onCounts[r] + bin.offCounts[r]) : bin.counts[r];
            }
            if (bin.ts < tMin) {
                tMin = bin.ts;
            }
            if (bin.ts > tMax) {
                tMax = bin.ts;
            }
        }
        int size = 4 + 2 + 4 + bins.size() * (8 + 4) + records * recordBytes;
        ByteBuffer buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 'L');
        buf.put((byte) 'B');
        buf.put((byte) 'E');
        buf.put((byte) 'V');
        buf.putShort((short) (pairs ? VERSION_PAIRS : VERSION));
        buf.putInt(bins.size());
        for (int i = 0; i < bins.size(); i++) {
            Bin bin = bins.get(i);
            buf.putLong(bin.ts);
            buf.putInt(bin.n);
            for (int r = 0; r < bin.n; r++) {
                buf.putShort((short) bin.xs[r]);
                buf.putShort((short) bin.ys[r]);
                if (pairs) {
                    buf.put((byte) bin.onCounts[r]);
                    buf.put((byte) bin.offCounts[r]);
                } else {
                    buf.put((byte) bin.types[r]);
                    buf.putShort((short) bin.counts[r]);
                }
            }
        }
        return new Encoded(buf.array(), expanded, tMin, tMax);
    }

    private static int appendView(int[] xs, int[] ys, int[] types, long[] ends, long[] tss,
            int n, int x, int y, int type, int count, long ts) {
        xs[n] = x;
        ys[n] = y;
        types[n] = type;
        long expanded = (n == 0 ? 0L : ends[n - 1]) + count;
        ends[n] = expanded;
        tss[n] = ts;
        return n + 1;
    }

    private static int[] grown(int[] a, int used) {
        if (used < a.length) {
            return a;
        }
        return java.util.Arrays.copyOf(a, a.length * 2);
    }

    private static long[] grownLong(long[] a, int used) {
        if (used < a.length) {
            return a;
        }
        return java.util.Arrays.copyOf(a, a.length * 2);
    }

    private static long key(int x, int y, int type) {
        return ((long) type << 32) | ((long) x << 16) | y;
    }

    private static final class Bin {
        final long ts;
        final int n;
        final int[] xs;
        final int[] ys;
        final int[] types;
        final int[] counts;
        final boolean pairs;
        final int[] onCounts;
        final int[] offCounts;

        Bin(long ts, int n, int[] xs, int[] ys, int[] types, int[] counts) {
            this(ts, n, xs, ys, types, counts, false, null, null);
        }

        Bin(long ts, int n, int[] xs, int[] ys, int[] types, int[] counts,
                boolean pairs, int[] onCounts, int[] offCounts) {
            this.ts = ts;
            this.n = n;
            this.xs = xs;
            this.ys = ys;
            this.types = types;
            this.counts = counts;
            this.pairs = pairs;
            this.onCounts = onCounts;
            this.offCounts = offCounts;
        }

        static Bin copyOf(long ts, int[] xs, int[] ys, int[] types, int[] counts, int n) {
            return new Bin(ts, n,
                    java.util.Arrays.copyOf(xs, n),
                    java.util.Arrays.copyOf(ys, n),
                    java.util.Arrays.copyOf(types, n),
                    java.util.Arrays.copyOf(counts, n));
        }

        static Bin copyPairs(long ts, int[] xs, int[] ys, int[] onCounts, int[] offCounts, int n) {
            return new Bin(ts, n,
                    java.util.Arrays.copyOf(xs, n),
                    java.util.Arrays.copyOf(ys, n),
                    null, null, true,
                    java.util.Arrays.copyOf(onCounts, n),
                    java.util.Arrays.copyOf(offCounts, n));
        }
    }

    /** Open-address map from pixel key to record slot. Missing keys return -1. */
    private static final class OpenMap {
        private long[] keys;
        private int[] vals;
        private boolean[] used;
        private int mask;
        private int size;

        OpenMap(int cap) {
            cap = Integer.highestOneBit(Math.max(16, cap - 1)) << 1;
            keys = new long[cap];
            vals = new int[cap];
            used = new boolean[cap];
            mask = cap - 1;
        }

        void clear() {
            if (size == 0) {
                return;
            }
            java.util.Arrays.fill(used, false);
            size = 0;
        }

        int get(long key) {
            int i = mix(key) & mask;
            while (used[i]) {
                if (keys[i] == key) {
                    return vals[i];
                }
                i = (i + 1) & mask;
            }
            return -1;
        }

        void put(long key, int slot) {
            if (size * 10 >= keys.length * 7) {
                rehash(keys.length << 1);
            }
            int i = mix(key) & mask;
            while (used[i]) {
                if (keys[i] == key) {
                    vals[i] = slot;
                    return;
                }
                i = (i + 1) & mask;
            }
            used[i] = true;
            keys[i] = key;
            vals[i] = slot;
            size++;
        }

        void shiftSlotsAbove(int slot) {
            for (int i = 0; i < used.length; i++) {
                if (used[i] && vals[i] > slot) {
                    vals[i]++;
                }
            }
        }

        private void rehash(int cap) {
            long[] oldKeys = keys;
            int[] oldVals = vals;
            boolean[] oldUsed = used;
            keys = new long[cap];
            vals = new int[cap];
            used = new boolean[cap];
            mask = cap - 1;
            size = 0;
            for (int i = 0; i < oldUsed.length; i++) {
                if (oldUsed[i]) {
                    put(oldKeys[i], oldVals[i]);
                }
            }
        }

        private static int mix(long key) {
            long z = key * 0x9E3779B97F4A7C15L;
            return (int) (z ^ (z >>> 32));
        }
    }
}
