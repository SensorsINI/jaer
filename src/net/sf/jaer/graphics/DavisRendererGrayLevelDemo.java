package net.sf.jaer.graphics;

import ch.unizh.ini.jaer.chip.retina.DvsDisplayConfigInterface;
import java.beans.PropertyChangeSupport;
import java.nio.FloatBuffer;
import java.util.Observer;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.chip.Chip;
import net.sf.jaer.graphics.AEChipRenderer.ColorMode;
import org.objenesis.ObjenesisStd;

/**
 * Headless executable contract for DavisRenderer gray-buffer initialization.
 *
 * <p>The fixture bypasses ChipCanvas construction because that constructor initializes JOGL. It
 * deliberately exercises the real DavisRenderer buffer allocation and reset path instead of a
 * renderer stub.</p>
 */
@SuppressWarnings("deprecation")
public class DavisRendererGrayLevelDemo {

    private static final int SIZE_X = 112;
    private static final int SIZE_Y = 126;
    private static final int TEXTURE_SIZE = 128;
    private static final int BUFFER_SIZE = 4 * TEXTURE_SIZE * TEXTURE_SIZE;
    private static final float EPSILON = 1e-6f;

    /**
     * Objenesis skips Observable's field initialization. These overrides must therefore remain
     * stateless no-ops; this contract never sends chip notifications.
     */
    static final class HeadlessRendererChip extends AEChip {

        @Override
        public synchronized void addObserver(final Observer observer) {
            // No-op by design; see class comment.
        }

        @Override
        public synchronized void deleteObserver(final Observer observer) {
            // No-op by design; see class comment.
        }

        @Override
        public void notifyObservers() {
            // No-op by design; Chip2D size setters notify while assembling the fixture.
        }

        @Override
        public void notifyObservers(final Object argument) {
            // No-op by design; Observable's observer Vector was not initialized.
        }
    }

    /** Production configuration seam used by DavisRenderer.isDisplayFrames(). */
    static final class StubDisplayConfig extends Biasgen implements DvsDisplayConfigInterface {

        private boolean displayFrames;
        private boolean displayEvents = true;
        private boolean useAutoContrast;
        private float contrast = 1f;
        private float brightness;
        private float gamma = 1f;

        StubDisplayConfig(final Chip chip) {
            super(chip);
        }

        @Override
        public boolean isDisplayFrames() {
            return displayFrames;
        }

        @Override
        public void setDisplayFrames(final boolean displayFrames) {
            this.displayFrames = displayFrames;
        }

        @Override
        public boolean isDisplayEvents() {
            return displayEvents;
        }

        @Override
        public void setDisplayEvents(final boolean displayEvents) {
            this.displayEvents = displayEvents;
        }

        @Override
        public boolean isUseAutoContrast() {
            return useAutoContrast;
        }

        @Override
        public void setUseAutoContrast(final boolean useAutoContrast) {
            this.useAutoContrast = useAutoContrast;
        }

        @Override
        public float getContrast() {
            return contrast;
        }

        @Override
        public void setContrast(final float contrast) {
            this.contrast = contrast;
        }

        @Override
        public float getBrightness() {
            return brightness;
        }

        @Override
        public void setBrightness(final float brightness) {
            this.brightness = brightness;
        }

        @Override
        public float getGamma() {
            return gamma;
        }

        @Override
        public void setGamma(final float gamma) {
            this.gamma = gamma;
        }
    }

    /** Objenesis bypasses the sole ChipCanvas constructor, which initializes JOGL. */
    static final class Canvas2DStub extends ChipCanvas {

        private Canvas2DStub() {
            super(null);
        }

        @Override
        public boolean is3DEnabled() {
            return false;
        }
    }

    /** Objenesis bypasses the sole ChipCanvas constructor, which initializes JOGL. */
    static final class Canvas3DStub extends ChipCanvas {

        private Canvas3DStub() {
            super(null);
        }

        @Override
        public boolean is3DEnabled() {
            return true;
        }
    }

    private DavisRendererGrayLevelDemo() {
    }

    public static void main(final String[] args) {
        try {
            final AEChip matrixChip = newChip();
            matrixChip.setCanvas(newCanvas(Canvas2DStub.class));
            final DavisRenderer matrixRenderer = new DavisRenderer(matrixChip);
            requireFixture(matrixRenderer.colorMode != null,
                    "DavisRenderer construction left colorMode null");
            requireFixture(matrixRenderer.grayBuffer != null && matrixRenderer.pixmap != null
                    && matrixRenderer.pixBuffer != null,
                    "DavisRenderer construction did not allocate all gray buffers");

            final StubDisplayConfig displayConfig = (StubDisplayConfig) matrixChip.getBiasgen();
            int matrixFailures = 0;
            matrixFailures += runCase("plain", matrixChip, matrixRenderer, displayConfig,
                    false, ColorMode.GrayLevel, newCanvas(Canvas2DStub.class), 1f);
            matrixFailures += runCase("frames", matrixChip, matrixRenderer, displayConfig,
                    true, ColorMode.GrayLevel, newCanvas(Canvas2DStub.class), 0f);
            matrixFailures += runCase("hot-code", matrixChip, matrixRenderer, displayConfig,
                    false, ColorMode.HotCode, newCanvas(Canvas2DStub.class), 0f);
            matrixFailures += runCase("3d", matrixChip, matrixRenderer, displayConfig,
                    false, ColorMode.GrayLevel, newCanvas(Canvas3DStub.class), 0f);

            final AEChip nullCanvasChip = newChip();
            requireFixture(nullCanvasChip.getCanvas() == null,
                    "headless chip canvas must be null before renderer construction");
            try {
                final DavisRenderer nullCanvasRenderer = new DavisRenderer(nullCanvasChip);
                requireFixture(nullCanvasRenderer.colorMode != null,
                        "null-canvas construction left colorMode null and skipped the reset path");
                final StubDisplayConfig nullDisplayConfig =
                        (StubDisplayConfig) nullCanvasChip.getBiasgen();
                nullDisplayConfig.setDisplayFrames(false);
                nullCanvasRenderer.colorMode = ColorMode.GrayLevel;
                nullCanvasRenderer.resetPixmapGrayLevel(ColorMode.GrayLevel.getBackgroundGrayLevel());
                final int nullCanvasFailures = checkBuffers("null-canvas", nullCanvasRenderer,
                        ColorMode.GrayLevel.getBackgroundGrayLevel(), 1f);
                final int failures = matrixFailures + nullCanvasFailures;
                System.out.println("[U3] TOTAL_CONTRACT_FAILURES=" + failures);
                if (failures == 0) {
                    System.out.println("[U3] STATUS: GREEN");
                    System.exit(0);
                }
                System.out.println("[U3] STATUS: RED");
                System.exit(1);
            } catch (final NullPointerException expectedBeforeGuard) {
                final boolean intended = hasFrame(expectedBeforeGuard,
                        "net.sf.jaer.graphics.DavisRenderer", "resetPixmapGrayLevel")
                        && hasFrame(expectedBeforeGuard,
                                "net.sf.jaer.graphics.Chip2DRenderer", "setGrayValue")
                        && hasFrame(expectedBeforeGuard,
                                "net.sf.jaer.graphics.AEChipRenderer",
                                "initializeGrayLevelFromColorMode");
                expectedBeforeGuard.printStackTrace(System.out);
                if (!intended || matrixFailures != 0) {
                    System.out.println("[U3] STATUS: INVALID");
                    System.exit(2);
                }
                System.out.println("[U3] intended null-canvas failure reached the real reset path");
                System.out.println("[U3] STATUS: RED");
                System.exit(1);
            }
        } catch (final Throwable fixtureFailure) {
            fixtureFailure.printStackTrace(System.out);
            System.out.println("[U3] STATUS: INVALID");
            System.exit(2);
        }
    }

    private static AEChip newChip() {
        final AEChip chip = new ObjenesisStd().newInstance(HeadlessRendererChip.class);
        chip.setPrefs(new AEViewerSnapshotProbe.MapBackedPreferences(null, ""));
        chip.setSupport(new PropertyChangeSupport(chip));
        chip.setSizeX(SIZE_X);
        chip.setSizeY(SIZE_Y);
        chip.setBiasgen(new StubDisplayConfig(chip));
        return chip;
    }

    private static <T extends ChipCanvas> T newCanvas(final Class<T> type) {
        return new ObjenesisStd().newInstance(type);
    }

    private static int runCase(final String name, final AEChip chip,
            final DavisRenderer renderer, final StubDisplayConfig displayConfig,
            final boolean displayFrames, final ColorMode colorMode,
            final ChipCanvas canvas, final float expectedAlpha) {
        chip.setCanvas(canvas);
        displayConfig.setDisplayFrames(displayFrames);
        renderer.colorMode = colorMode;
        final float background = colorMode.getBackgroundGrayLevel();
        renderer.resetPixmapGrayLevel(background);
        final int failures = checkBuffers(name, renderer, background, expectedAlpha);
        System.out.println("[U3.matrix] " + name + " displayFrames=" + displayFrames
                + " colorMode=" + colorMode + " is3D=" + canvas.is3DEnabled()
                + " expectedAlpha=" + expectedAlpha + " failures=" + failures);
        return failures;
    }

    private static int checkBuffers(final String name, final DavisRenderer renderer,
            final float expectedBackground, final float expectedAlpha) {
        int failures = 0;
        if (renderer.textureWidth != TEXTURE_SIZE || renderer.textureHeight != TEXTURE_SIZE) {
            System.out.println("[U3." + name + "] FAIL: texture size=" + renderer.textureWidth
                    + "x" + renderer.textureHeight + " expected=128x128");
            failures++;
        }
        failures += checkShape(name, "grayBuffer", renderer.grayBuffer, true);
        failures += checkShape(name, "pixmap", renderer.pixmap, false);
        failures += checkShape(name, "pixBuffer", renderer.pixBuffer, false);

        if (renderer.grayBuffer == null || renderer.pixmap == null || renderer.pixBuffer == null
                || renderer.grayBuffer.capacity() < BUFFER_SIZE
                || renderer.pixmap.capacity() < BUFFER_SIZE
                || renderer.pixBuffer.capacity() < BUFFER_SIZE) {
            return failures;
        }

        int firstMismatch = -1;
        String mismatch = null;
        for (int i = 0; i < BUFFER_SIZE; i += 4) {
            for (int channel = 0; channel < 3; channel++) {
                final int index = i + channel;
                final float gray = renderer.grayBuffer.get(index);
                final float pixmap = renderer.pixmap.get(index);
                final float pixBuffer = renderer.pixBuffer.get(index);
                if (!equal(gray, expectedBackground)
                        || !equal(pixmap, gray) || !equal(pixBuffer, gray)) {
                    firstMismatch = index;
                    mismatch = "RGB expected=" + expectedBackground + " gray=" + gray
                            + " pixmap=" + pixmap + " pixBuffer=" + pixBuffer;
                    break;
                }
            }
            if (firstMismatch >= 0) {
                break;
            }
            final int alphaIndex = i + 3;
            final float grayAlpha = renderer.grayBuffer.get(alphaIndex);
            final float pixmapAlpha = renderer.pixmap.get(alphaIndex);
            final float pixBufferAlpha = renderer.pixBuffer.get(alphaIndex);
            if (!equal(grayAlpha, expectedAlpha)
                    || !equal(pixmapAlpha, grayAlpha) || !equal(pixBufferAlpha, grayAlpha)) {
                firstMismatch = alphaIndex;
                mismatch = "alpha expected=" + expectedAlpha + " gray=" + grayAlpha
                        + " pixmap=" + pixmapAlpha + " pixBuffer=" + pixBufferAlpha;
                break;
            }
        }
        if (firstMismatch >= 0) {
            System.out.println("[U3." + name + "] FAIL: buffer mismatch index="
                    + firstMismatch + " " + mismatch);
            failures++;
        } else {
            System.out.println("[U3." + name + "] PASS: RGB=" + expectedBackground
                    + " alpha=" + expectedAlpha + " across " + (BUFFER_SIZE / 4)
                    + " pixels in all three buffers");
        }
        return failures;
    }

    private static int checkShape(final String caseName, final String bufferName,
            final FloatBuffer buffer, final boolean exactCapacity) {
        if (buffer == null) {
            System.out.println("[U3." + caseName + "] FAIL: " + bufferName + " is null");
            return 1;
        }
        final boolean capacityOk = exactCapacity
                ? buffer.capacity() == BUFFER_SIZE : buffer.capacity() >= BUFFER_SIZE;
        if (!capacityOk || buffer.limit() != BUFFER_SIZE || buffer.position() != 0) {
            System.out.println("[U3." + caseName + "] FAIL: " + bufferName
                    + " capacity=" + buffer.capacity() + " limit=" + buffer.limit()
                    + " position=" + buffer.position() + " expected capacity"
                    + (exactCapacity ? "=" : ">=") + BUFFER_SIZE + " limit=" + BUFFER_SIZE
                    + " position=0");
            return 1;
        }
        return 0;
    }

    private static boolean equal(final float actual, final float expected) {
        return Math.abs(actual - expected) <= EPSILON;
    }

    private static boolean hasFrame(final Throwable throwable,
            final String className, final String methodName) {
        for (final StackTraceElement frame : throwable.getStackTrace()) {
            if (className.equals(frame.getClassName()) && methodName.equals(frame.getMethodName())) {
                return true;
            }
        }
        return false;
    }

    private static void requireFixture(final boolean condition, final String message) {
        if (!condition) {
            throw new IllegalStateException("FIXTURE_FAILURE: " + message);
        }
    }
}
