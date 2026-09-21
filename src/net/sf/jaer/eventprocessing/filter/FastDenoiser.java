/* FastDenoiser.java
 *
 * Cache-friendly SpatioTemporalCorrelationFilter: 8-bit quantized timestamps,
 * optional 2x2 map cells, packed 1D (optionally 8x8 tiled) storage. */
package net.sf.jaer.eventprocessing.filter;

import java.util.Arrays;
import java.util.Random;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.Help;
import net.sf.jaer.Preferred;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventio.AEInputStream;

/**
 * STCF-style BA denoiser that keeps the timestamp map in cache on megapixel
 * sensors. Differs from {@link QuantizedSTCF}, which still stores full 32-bit
 * {@code int[][]} maps.
 * <p>
 * Storage is a single {@code byte[]} (not {@code byte[][]}), with timestamp and
 * polarity interleaved per pixel so a 3&times;3 neighborhood is a few cache
 * lines. Optional 8&times;8 tiling keeps those neighbors even when the map is
 * larger than L2. Optional 2&times;2 cells store the latest event in each
 * neighborhood (same idea as {@code subsampleBy=1}).
 * <p>
 * InDevelopment: Davis346 and EVK4 playback showed at most ~20% throughput
 * gain vs STCF; map layout is not the dominant cost yet.
 */
@Description("WIP STCF variant: 8-bit timestamps, optional 2x2/tiled maps (InDevelopment; ~20% faster at best vs STCF)")
@Help("""
<html>
<body>
<h2>FastDenoiser</h2>
<p>Same correlation test as <code>SpatioTemporalCorrelationFilter</code>, but the
timestamp image is <b>8-bit</b> (right-shifted, default ~1&nbsp;ms/LSB) in a
<b>packed 1D</b> array so megapixel maps fit in cache. Optional <b>2&times;2</b>
cells shrink the map another 4&times;.</p>
<p>Unlike experimental <code>QuantizedSTCF</code>, this filter does <i>not</i>
keep a 32-bit Java 2D timestamp image.</p>
<p><b>Status:</b> InDevelopment / WIP. On Davis346 and Prophesee EVK4 recordings,
8-bit maps, 2&times;2 cells, and tiled layout were at best ~20% faster than STCF
(Measure filter processing time); not a dramatic cache win yet.</p>
<hr>
<h3>How to benchmark vs STCF</h3>
<ol>
<li>Open a high-rate Prophesee or NRV recording (for example under
<code>jaer/tmp/</code> test data).</li>
<li>Filters &rarr; <b>Measure filter processing time</b> (Ctrl+P). Stats go to
<b>System.out</b> (IDE console), not the built-in jAER log console.</li>
<li>Enable only <code>SpatioTemporalCorrelationFilter</code>, play, note
events/s. Disable it, enable only <code>FastDenoiser</code>, rewind, compare.
Try <code>downsample2x2</code> and <code>useTiledLayout</code> separately.</li>
</ol>
<h3>Controls</h3>
<ul>
<li><code>timestampRightShiftBits</code> &mdash; timestamp <code>&gt;&gt;&gt;</code>
this many bits before storing 8 bits. 10 &rarr; ~1&nbsp;ms LSB; wrap period is
256 LSBs (~262&nbsp;ms). Keep <code>correlationTimeS</code> well below wrap/2.</li>
<li><code>downsample2x2</code> &mdash; one map cell per 2&times;2 pixels (latest
event wins). Stacks with <code>subsampleBy</code>.</li>
<li><code>useTiledLayout</code> &mdash; 8&times;8 tiles in the packed array
(better 3&times;3 locality on large maps). Off = column-major flat packed.</li>
</ul>
<p>Correlation parameters (<code>numMustBeCorrelated</code>,
<code>polaritiesMustMatch</code>, <code>filterHotPixels</code>, shot-noise test)
match STCF.</p>
</body>
</html>
""")
@DevelopmentStatus(DevelopmentStatus.Status.InDevelopment)
public class FastDenoiser extends SpatioTemporalCorrelationFilter {

    /** Interleaved timestamp (unsigned) then polarity signum. */
    private static final int CELL = 2;
    private static final int TILE = 8;
    private static final int TILE_SHIFT = 3;
    private static final int TILE_MASK = 7;
    /** Bytes per 8x8 tile of CELL=2: 64*2 = 128 = 1&lt;&lt;7. */
    private static final int TILE_BYTES_SHIFT = 7;
    /** Unused cell (unsigned 255). Quantized 255 is stored as 254. */
    private static final int EMPTY = 0xFF;
    private static final String FAST_GROUP = "Fast map";

    @Preferred
    private int timestampRightShiftBits = getInt("timestampRightShiftBits", 10);
    @Preferred
    private boolean downsample2x2 = getBoolean("downsample2x2", false);
    @Preferred
    private boolean useTiledLayout = getBoolean("useTiledLayout", true);

    /**
     * Packed map: [ts, pol] per cell. {@code timestampImage}/{@code polImage}
     * from STCF are left as a 1x1 dummy so inherited code does not allocate a
     * megapixel {@code int[][]}.
     */
    private byte[] map;
    private int mapSx, mapSy;
    private int nTilesY;
    private int stride; // mapSy * CELL for flat column-major
    private int timestampResUs;
    private int wrapUs;
    private int dtQ;
    private int shotDtQ;

    public FastDenoiser(AEChip chip) {
        super(chip);
        setPropertyTooltip(FAST_GROUP, "timestampRightShiftBits",
                "Right-shift of microsecond timestamp before keeping 8 bits. 10 ≈ 1 ms/LSB.");
        setPropertyTooltip(FAST_GROUP, "downsample2x2",
                "Store latest timestamp per 2x2 neighborhood (adds one address bit shift).");
        setPropertyTooltip(FAST_GROUP, "useTiledLayout",
                "Pack the map as 8x8 tiles (better 3x3 cache locality). Off = column-major flat bytes.");
        computeQuantizationInfo();
        getSupport().addPropertyChangeListener(AEInputStream.EVENT_REWOUND, this);
    }

    private int spatialShift() {
        return subsampleBy + (downsample2x2 ? 1 : 0);
    }

    private int mapWidth() {
        return sxm1 < 0 ? 1 : (sxm1 >> spatialShift()) + 1;
    }

    private int mapHeight() {
        return sym1 < 0 ? 1 : (sym1 >> spatialShift()) + 1;
    }

    private void computeQuantizationInfo() {
        timestampResUs = 1 << timestampRightShiftBits;
        wrapUs = timestampResUs * 256;
        String info = String.format(
                "8-bit timestamps: LSB %,d us, wrap %,d us (~%s). Keep correlationTimeS << wrap/2.",
                timestampResUs, wrapUs, eng.format(wrapUs * 1e-6f));
        setPropertyTooltip(FAST_GROUP, "timestampRightShiftBits",
                "<html>Right-shift of microsecond timestamp before keeping 8 bits.<br>" + info);
        updateQuantizedDts();
    }

    private void updateQuantizedDts() {
        dtQ = ((int) Math.round(getCorrelationTimeS() * 1e6f)) >>> timestampRightShiftBits;
        if (dtQ < 1) {
            dtQ = 1;
        }
        if (dtQ > 127) {
            dtQ = 127;
        }
        shotDtQ = ((int) (shotNoiseCorrelationTimeS * 1e6f)) >>> timestampRightShiftBits;
        if (shotDtQ < 1) {
            shotDtQ = 1;
        }
    }

    /** 8-bit quantized timestamp; 0xFF is reserved as EMPTY. */
    private int quantizeTimestamp(int ts) {
        int q = (ts >>> timestampRightShiftBits) & 0xFF;
        if (q == EMPTY) {
            q = EMPTY - 1;
        }
        return q;
    }

    private int flatIndex(int x, int y) {
        return (x * mapSy + y) * CELL;
    }

    private int tiledIndex(int x, int y) {
        final int tx = x >> TILE_SHIFT;
        final int ty = y >> TILE_SHIFT;
        final int ox = x & TILE_MASK;
        final int oy = y & TILE_MASK;
        return ((tx * nTilesY + ty) << TILE_BYTES_SHIFT) + (((ox << TILE_SHIFT) + oy) * CELL);
    }

    @Override
    public String infoString() {
        int bytes = map == null ? 0 : map.length;
        return String.format("%s k=%d tau=%s 8b>>%d 2x2=%s tiled=%s map=%dx%d %dKiB",
                camelCaseClassname(), numMustBeCorrelated, eng.format(getCorrelationTimeS()),
                timestampRightShiftBits, downsample2x2, useTiledLayout,
                mapSx, mapSy, bytes / 1024);
    }

    @Override
    synchronized public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in) {
        // AbstractNoiseFilter preamble only — do not call STCF.filterPacket (32-bit maps).
        resetCountsAndNegativeEvents();
        signalNoiseClassificationEnabled = (in instanceof SignalNoisePacket)
                || (in != null && in.getEventPrototype() instanceof SignalNoiseEvent);
        in = getEnclosedFilterChain().filterPacket(in);
        final int shift = spatialShift();
        final int needSx = mapWidth();
        final int needSy = mapHeight();
        if (map == null || mapSx != needSx || mapSy != needSy) {
            allocateMaps(chip);
        }
        updateQuantizedDts();
        ssx = sxm1 >> shift;
        ssy = sym1 >> shift;
        final boolean fhp = filterHotPixels;
        final NnbRange nnbRange = new NnbRange();
        final boolean checkPolarity = polaritiesMustMatch && (in.getEventPrototype() instanceof PolarityEvent);
        final int kNeed = numMustBeCorrelated;
        final boolean shotNoiseEnabled = isFilterAlternativePolarityShotNoiseEnabled();
        final int dt = dtQ;
        final boolean tiled = useTiledLayout;
        final int tilesY = nTilesY;
        final int sy = mapSy;

        for (BasicEvent e : in) {
            if (e == null) {
                continue;
            }
            totalEventCount++;
            final int tsQ = quantizeTimestamp(e.timestamp);
            final int x = e.x >> shift;
            final int y = e.y >> shift;
            if ((x < 0) || (x > ssx) || (y < 0) || (y > ssy)) {
                filterOut(e);
                continue;
            }
            final int selfIdx = tiled ? tiledIndex(x, y) : (x * sy + y) * CELL;
            final int selfTs = map[selfIdx] & 0xFF;
            if (selfTs == EMPTY) {
                storeAt(selfIdx, tsQ, e);
                if (letFirstEventThrough) {
                    filterIn(e);
                } else {
                    filterOut(e);
                }
                continue;
            }

            int ncorrelated = 0;
            nnbRange.compute(x, y, ssx, ssy);
            final byte eventPol = checkPolarity ? (byte) ((PolarityEvent) e).getPolaritySignum() : 0;
            outerloop:
            for (int xx = nnbRange.x0; xx <= nnbRange.x1; xx++) {
                final int tx = xx >> TILE_SHIFT;
                final int ox = xx & TILE_MASK;
                final int flatCol = xx * sy;
                for (int yy = nnbRange.y0; yy <= nnbRange.y1; yy++) {
                    if (fhp && xx == x && yy == y) {
                        continue;
                    }
                    final int i = tiled
                            ? ((tx * tilesY + (yy >> TILE_SHIFT)) << TILE_BYTES_SHIFT)
                            + ((((ox << TILE_SHIFT) + (yy & TILE_MASK))) * CELL)
                            : (flatCol + yy) * CELL;
                    final int lastT = map[i] & 0xFF;
                    final int age = (tsQ - lastT) & 0xFF;
                    if (lastT != EMPTY && age < dt) {
                        if (!checkPolarity || eventPol == map[i + 1]) {
                            if (++ncorrelated >= kNeed) {
                                break outerloop;
                            }
                        }
                    }
                }
            }
            if (ncorrelated < kNeed) {
                filterOut(e);
            } else if (shotNoiseEnabled && testShotNoiseAt(selfIdx, tsQ, e)) {
                filterOut(e);
            } else {
                filterIn(e);
            }
            storeAt(selfIdx, tsQ, e);
        }
        getNoiseFilterControl().maybePerformControl(in);
        return in;
    }

    private void storeAt(int idx, int tsQ, BasicEvent e) {
        map[idx] = (byte) tsQ;
        if (e instanceof PolarityEvent) {
            map[idx + 1] = (byte) ((PolarityEvent) e).getPolaritySignum();
        } else {
            map[idx + 1] = 1;
        }
    }

    private boolean testShotNoiseAt(int selfIdx, int tsQ, BasicEvent e) {
        if (!(e instanceof PolarityEvent)) {
            return false;
        }
        numShotNoiseTests++;
        PolarityEvent p = (PolarityEvent) e;
        if (p.getPolaritySignum() == map[selfIdx + 1]) {
            return false;
        }
        int prevT = map[selfIdx] & 0xFF;
        if (prevT == EMPTY) {
            return false;
        }
        int age = (tsQ - prevT) & 0xFF;
        if (age > shotDtQ) {
            return false;
        }
        numAlternatingPolarityShotNoiseEventsFilteredOut++;
        return true;
    }

    @Override
    protected boolean testIsShotNoiseOppositePolarity(int x, int y, BasicEvent e) {
        if (!isFilterAlternativePolarityShotNoiseEnabled() || map == null) {
            return false;
        }
        int idx = useTiledLayout ? tiledIndex(x, y) : flatIndex(x, y);
        return testShotNoiseAt(idx, quantizeTimestamp(e.timestamp), e);
    }

    @Override
    protected void storeTimestampPolarity(final int x, final int y, BasicEvent e) {
        if (map == null) {
            return;
        }
        storeAt(useTiledLayout ? tiledIndex(x, y) : flatIndex(x, y), quantizeTimestamp(e.timestamp), e);
    }

    @Override
    public synchronized void resetFilter() {
        super.resetFilter();
        if (map != null) {
            Arrays.fill(map, (byte) EMPTY);
        }
        resetShotNoiseTestStats();
    }

    @Override
    public void initFilter() {
        super.initFilter();
        allocateMaps(chip);
        resetFilter();
    }

    @Override
    protected void allocateMaps(AEChip chip) {
        if (chip == null || chip.getNumCells() <= 0) {
            return;
        }
        sxm1 = chip.getSizeX() - 1;
        sym1 = chip.getSizeY() - 1;
        mapSx = mapWidth();
        mapSy = mapHeight();
        ssx = sxm1 >> spatialShift();
        ssy = sym1 >> spatialShift();
        stride = mapSy * CELL;
        nTilesY = (mapSy + TILE - 1) >> TILE_SHIFT;
        int nTilesX = (mapSx + TILE - 1) >> TILE_SHIFT;
        int need = useTiledLayout
                ? nTilesX * nTilesY * (1 << TILE_BYTES_SHIFT)
                : mapSx * mapSy * CELL;
        if (map == null || map.length != need) {
            map = new byte[need];
        }
        // Do not allocate megapixel int[][] / byte[][] from STCF.
        if (timestampImage == null || timestampImage.length != 1) {
            timestampImage = new int[1][1];
            polImage = new byte[1][1];
        }
        timestampImage[0][0] = DEFAULT_TIMESTAMP;
    }

    @Override
    public void initializeLastTimesMapForNoiseRate(float noiseRateHz, int lastTimestampUs) {
        if (map == null || noiseRateHz <= 0) {
            return;
        }
        Random random = new Random();
        final double noiseIntvlS = 1 / noiseRateHz;
        final boolean tiled = useTiledLayout;
        for (int x = 0; x < mapSx; x++) {
            for (int y = 0; y < mapSy; y++) {
                final double p = random.nextDouble();
                final double t = -noiseIntvlS * Math.log(1 - p);
                final int tUs = lastTimestampUs - (int) (1_000_000 * t);
                int i = tiled ? tiledIndex(x, y) : flatIndex(x, y);
                map[i] = (byte) quantizeTimestamp(tUs);
                map[i + 1] = random.nextBoolean() ? (byte) 1 : (byte) -1;
            }
        }
    }

    public int getTimestampRightShiftBits() {
        return timestampRightShiftBits;
    }

    public void setTimestampRightShiftBits(int timestampRightShiftBits) {
        if (timestampRightShiftBits > 16) {
            timestampRightShiftBits = 16;
        } else if (timestampRightShiftBits < 0) {
            timestampRightShiftBits = 0;
        }
        this.timestampRightShiftBits = timestampRightShiftBits;
        putInt("timestampRightShiftBits", timestampRightShiftBits);
        computeQuantizationInfo();
        resetFilter();
    }

    public boolean isDownsample2x2() {
        return downsample2x2;
    }

    public synchronized void setDownsample2x2(boolean downsample2x2) {
        boolean old = this.downsample2x2;
        this.downsample2x2 = downsample2x2;
        putBoolean("downsample2x2", downsample2x2);
        allocateMaps(chip);
        resetFilter();
        getSupport().firePropertyChange("downsample2x2", old, this.downsample2x2);
    }

    public boolean isUseTiledLayout() {
        return useTiledLayout;
    }

    public synchronized void setUseTiledLayout(boolean useTiledLayout) {
        boolean old = this.useTiledLayout;
        this.useTiledLayout = useTiledLayout;
        putBoolean("useTiledLayout", useTiledLayout);
        allocateMaps(chip);
        resetFilter();
        getSupport().firePropertyChange("useTiledLayout", old, this.useTiledLayout);
    }

    /**
     * Map storage in bytes (packed ts+pol), for logging / tests.
     */
    public int getMapSizeBytes() {
        return map == null ? 0 : map.length;
    }
}
