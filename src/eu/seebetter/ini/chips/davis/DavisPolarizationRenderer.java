package eu.seebetter.ini.chips.davis;

import java.util.Random;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEvent.PolarizationFilter;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;
import net.sf.jaer.event.orientation.OrientationEventInterface;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.DavisRenderer;
import net.sf.jaer.graphics.ChipRendererDisplayMethod;
import net.sf.jaer.util.histogram.SimpleHistogram;

/**
 * Class adapted from DavisRenderer to render the polarization output.
 *
 * The frame buffer is RGBA so four bytes per pixel. The rendering uses a
 * texture which is a power of two multiple of image size, so watch out for
 * getWidth and getHeight; they return this value and not the number of pixels
 * being rendered from the chip.
 *
 * @author damien, tobi
 * @see ChipRendererDisplayMethod
 */
public class DavisPolarizationRenderer extends DavisRenderer {
	// Special pixel arrangement, where DVS is only found once every four pixels, as in Chenghan Li CDAVIS
	private final boolean isDVSQuarterOfAPS;

	// Color filter pattern arrangement.
	// First lower left, then lower right, then upper right, then upper left.
	private final PolarizationFilter[] polarizationFilterSequence;

	// Whether the APS readout follows normal procedure (reset then signal read), or
	// the special readout: signal then readout mix.
	private final boolean isAPSSpecialReadout;

	// Given a pixel being at positions 0, 1, 2, 3 in the same arrangement as in PolarizationFilterFilterSequence,
	// what is its polarization and the polarization of all its neighbors? This four tables pre-compute that for
	// fast lookup later on.
	private final static int NEIGHBORHOOD_SIZE = 9;

	private final PolarizationFilter[] polarization0 = new PolarizationFilter[DavisPolarizationRenderer.NEIGHBORHOOD_SIZE];
	private final PolarizationFilter[] polarization45 = new PolarizationFilter[DavisPolarizationRenderer.NEIGHBORHOOD_SIZE];
	private final PolarizationFilter[] polarization90 = new PolarizationFilter[DavisPolarizationRenderer.NEIGHBORHOOD_SIZE];
	private final PolarizationFilter[] polarization135 = new PolarizationFilter[DavisPolarizationRenderer.NEIGHBORHOOD_SIZE];

	// Given a pixel, what is its polarization value, as well as the corresponding color values
	// of all its neighbors? Held in this array for fast lookup and to have the same structure as
	// the color information above.
	private final float[] DoP = new float[DavisPolarizationRenderer.NEIGHBORHOOD_SIZE];
	private final float[] AoP = new float[DavisPolarizationRenderer.NEIGHBORHOOD_SIZE];

	public DavisPolarizationRenderer(final AEChip chip, final boolean isDVSQuarterOfAPS, final PolarizationFilter[] polarizationFilterSequence,
		final boolean isAPSSpecialReadout) {
		super(chip);

		// Input array length check.
		if (polarizationFilterSequence.length != 4) {
			throw new RuntimeException("polarizationFilterSequence must have 4 elements (2x2 box).");
		}


		this.isDVSQuarterOfAPS = isDVSQuarterOfAPS;
		this.polarizationFilterSequence = polarizationFilterSequence;
		this.isAPSSpecialReadout = isAPSSpecialReadout;

		// Pre-compute all the possible color patterns of the neighbors.
		polarization0[0] = polarizationFilterSequence[2];
		polarization0[1] = polarizationFilterSequence[3];
		polarization0[2] = polarizationFilterSequence[2];
		polarization0[3] = polarizationFilterSequence[1];
		polarization0[4] = polarizationFilterSequence[0];
		polarization0[5] = polarizationFilterSequence[1];
		polarization0[6] = polarizationFilterSequence[2];
		polarization0[7] = polarizationFilterSequence[3];
		polarization0[8] = polarizationFilterSequence[2];

		polarization45[0] = polarizationFilterSequence[3];
		polarization45[1] = polarizationFilterSequence[2];
		polarization45[2] = polarizationFilterSequence[3];
		polarization45[3] = polarizationFilterSequence[0];
		polarization45[4] = polarizationFilterSequence[1];
		polarization45[5] = polarizationFilterSequence[0];
		polarization45[6] = polarizationFilterSequence[3];
		polarization45[7] = polarizationFilterSequence[2];
		polarization45[8] = polarizationFilterSequence[3];

		polarization90[0] = polarizationFilterSequence[0];
		polarization90[1] = polarizationFilterSequence[1];
		polarization90[2] = polarizationFilterSequence[0];
		polarization90[3] = polarizationFilterSequence[3];
		polarization90[4] = polarizationFilterSequence[2];
		polarization90[5] = polarizationFilterSequence[3];
		polarization90[6] = polarizationFilterSequence[0];
		polarization90[7] = polarizationFilterSequence[1];
		polarization90[8] = polarizationFilterSequence[0];

		polarization135[0] = polarizationFilterSequence[1];
		polarization135[1] = polarizationFilterSequence[0];
		polarization135[2] = polarizationFilterSequence[1];
		polarization135[3] = polarizationFilterSequence[2];
		polarization135[4] = polarizationFilterSequence[3];
		polarization135[5] = polarizationFilterSequence[2];
		polarization135[6] = polarizationFilterSequence[1];
		polarization135[7] = polarizationFilterSequence[0];
		polarization135[8] = polarizationFilterSequence[1];
	}

	@Override
	protected void updateEventMaps(final PolarityEvent e) {
		float[] map;
		map = dvsEventsMap.array();


		final int index = getIndex(e);
		if ((index < 0) || (index >= map.length)) {
			return;
		}

		// Support expanding one DVS event to cover a four pixel box, resulting in
		// an expansion to four pixels, for visualization without holes.
		final boolean expandToFour = isDVSQuarterOfAPS && !isSeparateAPSByColor();
		int idx1 = 0, idx2 = 0, idx3 = 0;

		if (expandToFour) {
			idx1 = getPixMapIndex(e.x + 1, e.y);
			idx2 = getPixMapIndex(e.x, e.y + 1);
			idx3 = getPixMapIndex(e.x + 1, e.y + 1);
		}

		// Change colors of DVS if SeparatyAPSByColor is selected: instead of Red/Green
		// for all, each quarter has its own color based on the pixel color.
		if (!isDVSQuarterOfAPS /*&& isSeparateAPSByColor()*/) {
			switch (((ApsDvsEvent) e).getColorFilter()) {
				case R:
					// Red
					onColor[0] = 1.0f;
					onColor[1] = 0.0f;
					onColor[2] = 0.0f;

					// Lighter Red
					offColor[0] = 0.0f;
					offColor[1] = 0.0f;
					offColor[2] = 0.0f;

					break;

				case G:
					// Green
					onColor[0] = 0.0f;
					onColor[1] = 1.0f;
					onColor[2] = 0.0f;

					// Lighter Green
					offColor[0] = 0.0f;
					offColor[1] = 0.0f;
					offColor[2] = 0.0f;

					break;

				case B:
					// Blue
					onColor[0] = 0.0f;
					onColor[1] = 0.0f;
					onColor[2] = 1.0f;

					// Lighter Blue
					offColor[0] = 0.0f;
					offColor[1] = 0.0f;
					offColor[2] = 0.0f;

					break;

				case W:
					// White
					onColor[0] = 1.0f;
					onColor[1] = 1.0f;
					onColor[2] = 1.0f;

					// Grey
					offColor[0] = 0.0f;
					offColor[1] = 0.0f;
					offColor[2] = 0.0f;

					break;
			}
		}

		if (packet.getNumCellTypes() > 2) {
			checkTypeColors(packet.getNumCellTypes());

			if ((e instanceof OrientationEventInterface) && (((OrientationEventInterface) e).isHasOrientation() == false)) {
				// if event is orientation event but orientation was not set, just draw as gray level
				map[index] = 1.0f; // if(f[0]>1f) f[0]=1f;
				map[index + 1] = 1.0f; // if(f[1]>1f) f[1]=1f;
				map[index + 2] = 1.0f; // if(f[2]>1f) f[2]=1f;

				if (expandToFour) {
					map[idx1] = 1.0f;
					map[idx1 + 1] = 1.0f;
					map[idx1 + 2] = 1.0f;
					map[idx2] = 1.0f;
					map[idx2 + 1] = 1.0f;
					map[idx2 + 2] = 1.0f;
					map[idx3] = 1.0f;
					map[idx3 + 1] = 1.0f;
					map[idx3 + 2] = 1.0f;
				}
			}
			else {
				// if color scale is 1, then last value is used as the pixel value, which quantizes the color to full
				// scale.
				final float[] c = typeColorRGBComponents[e.getType()];
				map[index] = c[0]; // if(f[0]>1f) f[0]=1f;
				map[index + 1] = c[1]; // if(f[1]>1f) f[1]=1f;
				map[index + 2] = c[2]; // if(f[2]>1f) f[2]=1f;

				if (expandToFour) {
					map[idx1] = c[0];
					map[idx1 + 1] = c[1];
					map[idx1 + 2] = c[2];
					map[idx2] = c[0];
					map[idx2 + 1] = c[1];
					map[idx2 + 2] = c[2];
					map[idx3] = c[0];
					map[idx3 + 1] = c[1];
					map[idx3 + 2] = c[2];
				}
			}

			final float alpha = map[index + 3] + (1.0f / colorScale);
			map[index + 3] += normalizeEvent(alpha);

			if (expandToFour) {
				map[idx1 + 3] += alpha;
				map[idx2 + 3] += alpha;
				map[idx3 + 3] += alpha;
			}
		}
		else if (colorMode == ColorMode.ColorTime) {
			final int ts0 = packet.getFirstTimestamp();
			final float dt = packet.getDurationUs();

			int ind = (int) Math.floor(((AEChipRenderer.NUM_TIME_COLORS - 1) * (e.timestamp - ts0)) / dt);
			if (ind < 0) {
				ind = 0;
			}
			else if (ind >= timeColors.length) {
				ind = timeColors.length - 1;
			}

			map[index] = timeColors[ind][0];
			map[index + 1] = timeColors[ind][1];
			map[index + 2] = timeColors[ind][2];
			map[index + 3] = 0.5f;

			if (expandToFour) {
				map[idx1] = timeColors[ind][0];
				map[idx1 + 1] = timeColors[ind][1];
				map[idx1 + 2] = timeColors[ind][2];
				map[idx2] = timeColors[ind][0];
				map[idx2 + 1] = timeColors[ind][1];
				map[idx2 + 2] = timeColors[ind][2];
				map[idx3] = timeColors[ind][0];
				map[idx3 + 1] = timeColors[ind][1];
				map[idx3 + 2] = timeColors[ind][2];

				map[idx1 + 3] = 0.5f;
				map[idx2 + 3] = 0.5f;
				map[idx3 + 3] = 0.5f;
			}
		}
		else if (colorMode == ColorMode.GrayTime) {
			final int ts0 = packet.getFirstTimestamp();
			final float dt = packet.getDurationUs();
			final float v = 0.95f - (0.95f * ((e.timestamp - ts0) / dt));

			map[index] = v;
			map[index + 1] = v;
			map[index + 2] = v;
			map[index + 3] = 1.0f;

			if (expandToFour) {
				map[idx1] = v;
				map[idx1 + 1] = v;
				map[idx1 + 2] = v;
				map[idx2] = v;
				map[idx2 + 1] = v;
				map[idx2 + 2] = v;
				map[idx3] = v;
				map[idx3 + 1] = v;
				map[idx3 + 2] = v;

				map[idx1 + 3] = 1.0f;
				map[idx2 + 3] = 1.0f;
				map[idx3 + 3] = 1.0f;
			}
		}
		else {

			if ((e.polarity == PolarityEvent.Polarity.On) || ignorePolarityEnabled) {
				map[index] = onColor[0];
				map[index + 1] = onColor[1];
				map[index + 2] = onColor[2];

				if (expandToFour) {
					map[idx1] = onColor[0];
					map[idx1 + 1] = onColor[1];
					map[idx1 + 2] = onColor[2];
					map[idx2] = onColor[0];
					map[idx2 + 1] = onColor[1];
					map[idx2 + 2] = onColor[2];
					map[idx3] = onColor[0];
					map[idx3 + 1] = onColor[1];
					map[idx3 + 2] = onColor[2];
				}
			}
			else {
				map[index] = offColor[0];
				map[index + 1] = offColor[1];
				map[index + 2] = offColor[2];

				if (expandToFour) {
					map[idx1] = offColor[0];
					map[idx1 + 1] = offColor[1];
					map[idx1 + 2] = offColor[2];
					map[idx2] = offColor[0];
					map[idx2 + 1] = offColor[1];
					map[idx2 + 2] = offColor[2];
					map[idx3] = offColor[0];
					map[idx3 + 1] = offColor[1];
					map[idx3 + 2] = offColor[2];
				}
			}

			final float alpha = map[index + 3] + (1.0f / colorScale);
			map[index + 3] = normalizeEvent(alpha);

			if (expandToFour) {
				map[idx1 + 3] = alpha;
				map[idx2 + 3] = alpha;
				map[idx3 + 3] = alpha;
			}
		}
	}

	private final Random random = new Random();

	@Override
	protected void updateFrameBuffer(final ApsDvsEvent e) {
		final float[] buf = pixBuffer.array();
		// TODO if playing backwards, then frame will come out white because B sample comes before A

		if (e.isStartOfFrame()) {
			startFrame(e.timestamp);
		}
		else if ((!isAPSSpecialReadout && e.isResetRead()) || (isAPSSpecialReadout && e.isResetRead() && !isGlobalShutter())
			|| (isAPSSpecialReadout && e.isSignalRead() && isGlobalShutter())) {
			final int index = getIndex(e);
			if ((index < 0) || (index >= buf.length)) {
				return;
			}

			final float val = e.getAdcSample();
			buf[index] = val;
		}
		else if ((!isAPSSpecialReadout && e.isSignalRead()) || (isAPSSpecialReadout && e.isSignalRead() && !isGlobalShutter())
			|| (isAPSSpecialReadout && e.isResetRead() && isGlobalShutter())) {
			final int index = getIndex(e);
			if ((index < 0) || (index >= buf.length)) {
				return;
			}

			int val = 0;

			if (isAPSSpecialReadout && isGlobalShutter()) {
				// The second read in GS mode is the reset read, so we have to invert this.
				val = (e.getAdcSample() - (int) buf[index]);
			}
			else {
				val = ((int) buf[index] - e.getAdcSample());
			}

			if (val < 0) 
				val = 0;
                        else {
                            if (val < minValue) 
				minValue = val;
                            else if (val > maxValue) 
				maxValue = val;
			}

			// right here sample-reset value of this pixel is in val
			if (computeHistograms) {
				if (!((DavisChip) chip).getAutoExposureController().isCenterWeighted()) {
					nextHist.add(val);
				}
				else {
					// randomly appendCopy histogram values to histogram depending on distance from center of image
					// to implement a simple form of center weighting of the histogram
					float d = (1 - Math.abs(((float) e.x - (sizeX / 2)) / sizeX)) + Math.abs(((float) e.y - (sizeY / 2)) / sizeY);
					// d is zero at center, 1 at corners
					d *= d;

					final float r = random.nextFloat();
					if (r > d) {
						nextHist.add(val);
					}
				}
			}

			final float fval = normalizeFramePixel(val);
			buf[index] = fval;
			buf[index + 1] = fval;
			buf[index + 2] = fval;
			buf[index + 3] = 1;
		}
		else if (e.isEndOfFrame()) {
			endFrame(e.timestamp);

			final SimpleHistogram tmp = currentHist;
			if (computeHistograms) {
				currentHist = nextHist;
				nextHist = tmp;
				nextHist.reset();
			}

			((DavisChip) chip).controlExposure();
		}
	}

	/**
	 * Returns index into pixmap according to separateAPSByColor flag
	 *
	 * @param x
	 * @param y
	 * @param color
	 * @return the index
	 */
	@Override
	protected int getIndex(final BasicEvent e) {
		int x = e.x, y = e.y;

		if ((x < 0) || (y < 0) || (x >= sizeX) || (y >= sizeY)) {
			if ((System.currentTimeMillis() - lastWarningPrintedTimeMs) > INTERVAL_BETWEEEN_OUT_OF_BOUNDS_EXCEPTIONS_PRINTED_MS) {
				log.warning(String.format(
					"Event %s out of bounds and cannot be rendered in bounds sizeX=%d sizeY=%d - delaying next warning for %dms",
					e.toString(), sizeX, sizeY, INTERVAL_BETWEEEN_OUT_OF_BOUNDS_EXCEPTIONS_PRINTED_MS));
				lastWarningPrintedTimeMs = System.currentTimeMillis();
			}

			return -1;
		}

		if (isSeparateAPSByColor()) {
			// Separate by using X/Y position, and not color, because some colors
			// might appear twice (think G), and would then be mapped to same quadrant.
			if ((y % 2) == 0) {
				if ((x % 2) == 0) {
					// Lower left.
					x = x / 2;
					y = y / 2;
				}
				else {
					x = (x / 2) + (chip.getSizeX() / 2);
					y = y / 2;
				}
			}
			else {
				if ((x % 2) == 0) {
					// Upper left.
					x = x / 2;
					y = (y / 2) + (chip.getSizeY() / 2);
				}
				else {
					// Upper right.
					x = (x / 2) + (chip.getSizeX() / 2);
					y = (y / 2) + (chip.getSizeY() / 2);
				}
			}
		}

		return getPixMapIndex(x, y);
	}

	public boolean isSeparateAPSByColor() {
		return ((DavisDisplayConfigInterface) chip.getBiasgen()).isSeparateAPSByColor();
	}

	public boolean isAutoWhiteBalance() {
		return ((DavisDisplayConfigInterface) chip.getBiasgen()).isAutoWhiteBalance();
	}

	public boolean isColorCorrection() {
		return ((DavisDisplayConfigInterface) chip.getBiasgen()).isColorCorrection();
	}

	public boolean isGlobalShutter() {
		return ((DavisDisplayConfigInterface) chip.getBiasgen()).isGlobalShutter();
	}

	// Allocate memory for this only once!
	private final PolarizationFilter[] colors = new PolarizationFilter[DavisPolarizationRenderer.NEIGHBORHOOD_SIZE];

	@Override
	protected void endFrame(final int ts) {
		// No color operation makes sense if we separate APS by color!
		if (!isSeparateAPSByColor()) {
			final float[] image = pixBuffer.array();

			if (isAutoWhiteBalance()) {
				// Automatic white balance support.
				final float WRGBtotal[] = new float[4];
				final int WRGBcount[] = new int[4];

				for (int y = 0; y < chip.getSizeY(); y += 2) {
					for (int x = 0; x < chip.getSizeX(); x += 2) {
						// We always look at a full 2x2 square and update all the required counters.
						WRGBtotal[polarizationFilterSequence[0].ordinal()] += image[getPixMapIndex(x, y)];
						WRGBtotal[polarizationFilterSequence[1].ordinal()] += image[getPixMapIndex(x + 1, y)];
						WRGBtotal[polarizationFilterSequence[2].ordinal()] += image[getPixMapIndex(x + 1, y + 1)];
						WRGBtotal[polarizationFilterSequence[3].ordinal()] += image[getPixMapIndex(x, y + 1)];

						// Also count how many times, since certain colors may appear twice (RGBG for example).
						WRGBcount[polarizationFilterSequence[0].ordinal()]++;
						WRGBcount[polarizationFilterSequence[1].ordinal()]++;
						WRGBcount[polarizationFilterSequence[2].ordinal()]++;
						WRGBcount[polarizationFilterSequence[3].ordinal()]++;
					}
				}

                                
				// Normalize values, to account for double G for example (RGBG).
				// WRGBtotal[ColorFilter.W.ordinal()] /= WRGBcount[ColorFilter.W.ordinal()]; // WHITE (currently
				// unused).
				WRGBtotal[ColorFilter.R.ordinal()] /= WRGBcount[ColorFilter.R.ordinal()]; // RED
				WRGBtotal[ColorFilter.G.ordinal()] /= WRGBcount[ColorFilter.G.ordinal()]; // GREEN
				WRGBtotal[ColorFilter.B.ordinal()] /= WRGBcount[ColorFilter.B.ordinal()]; // BLUE

				// Calculate ratios between G/R and G/B. Ignore W for now.
				final float G_R = WRGBtotal[ColorFilter.G.ordinal()] / WRGBtotal[ColorFilter.R.ordinal()];
				final float G_B = WRGBtotal[ColorFilter.G.ordinal()] / WRGBtotal[ColorFilter.B.ordinal()];

				for (int y = 0; y < chip.getSizeY(); y += 2) {
					for (int x = 0; x < chip.getSizeX(); x += 2) {
						// Apply ratios to R and B pixels' R and B component values. Ignore W for now.
						if (polarizationFilterSequence[0] == ColorFilter.R) {
							image[getPixMapIndex(x, y)] *= G_R;
						}
						else if (polarizationFilterSequence[0] == ColorFilter.B) {
							image[getPixMapIndex(x, y) + 2] *= G_B;
						}

						if (polarizationFilterSequence[1] == ColorFilter.R) {
							image[getPixMapIndex(x + 1, y)] *= G_R;
						}
						else if (polarizationFilterSequence[1] == ColorFilter.B) {
							image[getPixMapIndex(x + 1, y) + 2] *= G_B;
						}

						if (polarizationFilterSequence[2] == ColorFilter.R) {
							image[getPixMapIndex(x + 1, y + 1)] *= G_R;
						}
						else if (polarizationFilterSequence[2] == ColorFilter.B) {
							image[getPixMapIndex(x + 1, y + 1) + 2] *= G_B;
						}

						if (polarizationFilterSequence[3] == ColorFilter.R) {
							image[getPixMapIndex(x, y + 1)] *= G_R;
						}
						else if (polarizationFilterSequence[3] == ColorFilter.B) {
							image[getPixMapIndex(x, y + 1) + 2] *= G_B;
						}
					}
				}
			}

			// Color interpolation support.
			for (int y = 0; y < chip.getSizeY(); y++) {
				for (int x = 0; x < chip.getSizeX(); x++) {
					// What pixel am I? Get color information and color values on pixel
					// itself and all its neighbors to pass to interpolation function.

					// Copy right array over, so that we can modify values without impacting original.
					if ((y % 2) == 0) {
						if ((x % 2) == 0) {
							// Lower left.
							System.arraycopy(colors0, 0, colors, 0, DavisPolarizationRenderer.NEIGHBORHOOD_SIZE);
						}
						else {
							// Lower right.
							System.arraycopy(colors1, 0, colors, 0, DavisPolarizationRenderer.NEIGHBORHOOD_SIZE);
						}
					}
					else {
						if ((x % 2) == 0) {
							// Upper left.
							System.arraycopy(colors3, 0, colors, 0, DavisPolarizationRenderer.NEIGHBORHOOD_SIZE);
						}
						else {
							// Upper right.
							System.arraycopy(colors2, 0, colors, 0, DavisPolarizationRenderer.NEIGHBORHOOD_SIZE);
						}
					}

					// Handle borders, by setting color filter value to NULL for pixels outside image edge.
					if (y == 0) {
						colors[6] = null;
						colors[7] = null;
						colors[8] = null;
					}
					else if (y == (chip.getSizeY() - 1)) {
						colors[0] = null;
						colors[1] = null;
						colors[2] = null;
					}

					if (x == 0) {
						colors[0] = null;
						colors[3] = null;
						colors[6] = null;
					}
					else if (x == (chip.getSizeX() - 1)) {
						colors[2] = null;
						colors[5] = null;
						colors[8] = null;
					}

					// Color values for R/G/B channels are simply based on pixel position,
					// the color filter pattern doesn't matter here. To avoid getting invalid
					// pixel indexes and values when on image edges, we simply check that the
					// color value is not NULL for that pixel. If it is, we just set the
					// corresponding value to zero.
					int index = 0;

					if (colors[0] != null) {
						index = getPixMapIndex(x - 1, y + 1);
						valuesR[0] = image[index];
						valuesG[0] = image[index + 1];
						valuesB[0] = image[index + 2];
					}

					if (colors[1] != null) {
						index = getPixMapIndex(x, y + 1);
						valuesR[1] = image[index];
						valuesG[1] = image[index + 1];
						valuesB[1] = image[index + 2];
					}

					if (colors[2] != null) {
						index = getPixMapIndex(x + 1, y + 1);
						valuesR[2] = image[index];
						valuesG[2] = image[index + 1];
						valuesB[2] = image[index + 2];
					}

					if (colors[3] != null) {
						index = getPixMapIndex(x - 1, y);
						valuesR[3] = image[index];
						valuesG[3] = image[index + 1];
						valuesB[3] = image[index + 2];
					}

					if (colors[5] != null) {
						index = getPixMapIndex(x + 1, y);
						valuesR[5] = image[index];
						valuesG[5] = image[index + 1];
						valuesB[5] = image[index + 2];
					}

					if (colors[6] != null) {
						index = getPixMapIndex(x - 1, y - 1);
						valuesR[6] = image[index];
						valuesG[6] = image[index + 1];
						valuesB[6] = image[index + 2];
					}

					if (colors[7] != null) {
						index = getPixMapIndex(x, y - 1);
						valuesR[7] = image[index];
						valuesG[7] = image[index + 1];
						valuesB[7] = image[index + 2];
					}

					if (colors[8] != null) {
						index = getPixMapIndex(x + 1, y - 1);
						valuesR[8] = image[index];
						valuesG[8] = image[index + 1];
						valuesB[8] = image[index + 2];
					}

					// CENTER PIXEL (CURRENT). Can never be NULL.
					index = getPixMapIndex(x, y);
					valuesR[4] = image[index];
					valuesG[4] = image[index + 1];
					valuesB[4] = image[index + 2];

					// Call R/G/B generators for each pixel.
					image[index] = DavisPolarizationRenderer.generateRForPixel(colors, valuesR);
					image[index + 1] = DavisPolarizationRenderer.generateGForPixel(colors, valuesG);
					image[index + 2] = DavisPolarizationRenderer.generateBForPixel(colors, valuesB);
				}
			}

			if (isColorCorrection()) {
				for (int y = 0; y < chip.getSizeY(); y++) {
					for (int x = 0; x < chip.getSizeX(); x++) {
						// Get current RGB values, since we modify them later on.
						final int index = getPixMapIndex(x, y);

						final float R_original = image[index];
						final float G_original = image[index + 1];
						final float B_original = image[index + 2];

						image[index] = (colorCorrectionMatrix[0][0] * R_original) + (colorCorrectionMatrix[0][1] * G_original)
							+ (colorCorrectionMatrix[0][2] * B_original) + colorCorrectionMatrix[0][3];
						image[index + 1] = (colorCorrectionMatrix[1][0] * R_original) + (colorCorrectionMatrix[1][1] * G_original)
							+ (colorCorrectionMatrix[1][2] * B_original) + colorCorrectionMatrix[1][3];
						image[index + 2] = (colorCorrectionMatrix[2][0] * R_original) + (colorCorrectionMatrix[2][1] * G_original)
							+ (colorCorrectionMatrix[2][2] * B_original) + colorCorrectionMatrix[2][3];
					}
				}
			}
		}

		// End frame, copy pixBuffer for display.
		super.endFrame(ts);
	}

	private static float generateRForPixel(final ColorFilter[] pixelColors, final float[] redValues) {
		// Simple for now, if we're already a pixel of this color, we don't do anything.
		// If we aren't, we just average all neighbor pixels with that color.
		if (pixelColors[4] == ColorFilter.R) {
			return (redValues[4]);
		}

		float redSum = 0;
		int redCount = 0;

		for (int i = 0; i < DavisPolarizationRenderer.NEIGHBORHOOD_SIZE; i++) {
			if (pixelColors[i] == ColorFilter.R) {
				redSum += redValues[i];
				redCount++;
			}
		}

		return (redSum / redCount);
	}

	private static float generateGForPixel(final ColorFilter[] pixelColors, final float[] greenValues) {
		// Simple for now, if we're already a pixel of this color, we don't do anything.
		// If we aren't, we just average all neighbor pixels with that color.
		if (pixelColors[4] == ColorFilter.G) {
			return (greenValues[4]);
		}

		float greenSum = 0;
		int greenCount = 0;

		for (int i = 0; i < DavisPolarizationRenderer.NEIGHBORHOOD_SIZE; i++) {
			if (pixelColors[i] == ColorFilter.G) {
				greenSum += greenValues[i];
				greenCount++;
			}
		}

		return (greenSum / greenCount);
	}

	private static float generateBForPixel(final ColorFilter[] pixelColors, final float[] blueValues) {
		// Simple for now, if we're already a pixel of this color, we don't do anything.
		// If we aren't, we just average all neighbor pixels with that color.
		if (pixelColors[4] == ColorFilter.B) {
			return (blueValues[4]);
		}

		float blueSum = 0;
		int blueCount = 0;

		for (int i = 0; i < DavisPolarizationRenderer.NEIGHBORHOOD_SIZE; i++) {
			if (pixelColors[i] == ColorFilter.B) {
				blueSum += blueValues[i];
				blueCount++;
			}
		}

		return (blueSum / blueCount);
	}
}
