/*
 * ChipRenderer.java
 *
 * Created on May 2, 2006, 1:49 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import java.util.Random;

import eu.seebetter.ini.chips.DavisChip;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEvent.ColorFilter;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;
import net.sf.jaer.event.orientation.OrientationEventInterface;
import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.ChipRendererDisplayMethod;
import net.sf.jaer.util.histogram.SimpleHistogram;

/**
 * Class adapted from AEFrameChipRenderer to render CDAVIS=rgbDAVIS output.
 *
 * The frame buffer is RGBA so four bytes per pixel. The rendering uses a
 * texture which is a power of two multiple of image size, so watch out for
 * getWidth and getHeight; they return this value and not the number of pixels
 * being rendered from the chip.
 *
 * @author christian, tobi
 * @see ChipRendererDisplayMethod
 */
public class DavisColorRenderer extends AEFrameChipRenderer {
	// Special pixel arrangement, where DVS is only found once every four pixels.
	private final boolean isDVSQuarterOfAPS;

	// Color filter pattern arrangement.
	// First lower left, then lower right, then upper right, then upper left.
	private final ColorFilter[] colorFilterSequence;

	// Whether the APS readout follows normal procedure (reset then signal read), or
	// the special readout: signal then readout mix.
	private final boolean isAPSSpecialReadout;

	public DavisColorRenderer(final AEChip chip, final boolean isDVSQuarterOfAPS, final ColorFilter[] colorFilterSequence,
		final boolean isAPSSpecialReadout) {
		super(chip);

		this.isDVSQuarterOfAPS = isDVSQuarterOfAPS;
		this.colorFilterSequence = colorFilterSequence;
		this.isAPSSpecialReadout = isAPSSpecialReadout;
	}

	@Override
	protected void updateEventMaps(final PolarityEvent e) {
		float[] map;
		if (packet.getNumCellTypes() > 2) {
			map = onMap.array();
		}
		else if (e.polarity == Polarity.On) {
			map = onMap.array();
		}
		else {
			map = offMap.array();
		}

		final int index = getIndex(e);
		if ((index < 0) || (index >= map.length)) {
			return;
		}

		// Support expanding one DVS event to cover a four pixel box, resulting in
		// an expansion to four pixels, for visualization without holes.
		final boolean expandToFour = isDVSQuarterOfAPS && !isSeparateAPSByColor();

		if (packet.getNumCellTypes() > 2) {
			checkTypeColors(packet.getNumCellTypes());

			if ((e instanceof OrientationEventInterface) && (((OrientationEventInterface) e).isHasOrientation() == false)) {
				// if event is orientation event but orientation was not set, just draw as gray level
				map[index] = 1.0f; // if(f[0]>1f) f[0]=1f;
				map[index + 1] = 1.0f; // if(f[1]>1f) f[1]=1f;
				map[index + 2] = 1.0f; // if(f[2]>1f) f[2]=1f;

				if (expandToFour) {
					map[getPixMapIndex(e.x + 1, e.y)] = 1.0f;
					map[getPixMapIndex(e.x + 1, e.y) + 1] = 1.0f;
					map[getPixMapIndex(e.x + 1, e.y) + 2] = 1.0f;
					map[getPixMapIndex(e.x, e.y + 1)] = 1.0f;
					map[getPixMapIndex(e.x, e.y + 1) + 1] = 1.0f;
					map[getPixMapIndex(e.x, e.y + 1) + 2] = 1.0f;
					map[getPixMapIndex(e.x + 1, e.y + 1)] = 1.0f;
					map[getPixMapIndex(e.x + 1, e.y + 1) + 1] = 1.0f;
					map[getPixMapIndex(e.x + 1, e.y + 1) + 2] = 1.0f;
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
					map[getPixMapIndex(e.x + 1, e.y)] = c[0];
					map[getPixMapIndex(e.x + 1, e.y) + 1] = c[1];
					map[getPixMapIndex(e.x + 1, e.y) + 2] = c[2];
					map[getPixMapIndex(e.x, e.y + 1)] = c[0];
					map[getPixMapIndex(e.x, e.y + 1) + 1] = c[1];
					map[getPixMapIndex(e.x, e.y + 1) + 2] = c[2];
					map[getPixMapIndex(e.x + 1, e.y + 1)] = c[0];
					map[getPixMapIndex(e.x + 1, e.y + 1) + 1] = c[1];
					map[getPixMapIndex(e.x + 1, e.y + 1) + 2] = c[2];
				}
			}

			final float alpha = map[index + 3] + (1.0f / colorScale);
			map[index + 3] += normalizeEvent(alpha);

			if (expandToFour) {
				map[getPixMapIndex(e.x + 1, e.y) + 3] += alpha;
				map[getPixMapIndex(e.x, e.y + 1) + 3] += alpha;
				map[getPixMapIndex(e.x + 1, e.y + 1) + 3] += alpha;
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
				map[getPixMapIndex(e.x + 1, e.y)] = timeColors[ind][0];
				map[getPixMapIndex(e.x + 1, e.y) + 1] = timeColors[ind][1];
				map[getPixMapIndex(e.x + 1, e.y) + 2] = timeColors[ind][2];
				map[getPixMapIndex(e.x, e.y + 1)] = timeColors[ind][0];
				map[getPixMapIndex(e.x, e.y + 1) + 1] = timeColors[ind][1];
				map[getPixMapIndex(e.x, e.y + 1) + 2] = timeColors[ind][2];
				map[getPixMapIndex(e.x + 1, e.y + 1)] = timeColors[ind][0];
				map[getPixMapIndex(e.x + 1, e.y + 1) + 1] = timeColors[ind][1];
				map[getPixMapIndex(e.x + 1, e.y + 1) + 2] = timeColors[ind][2];
				map[getPixMapIndex(e.x + 1, e.y) + 3] = 0.5f;
				map[getPixMapIndex(e.x, e.y + 1) + 3] = 0.5f;
				map[getPixMapIndex(e.x + 1, e.y + 1) + 3] = 0.5f;
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
				map[getPixMapIndex(e.x + 1, e.y)] = v;
				map[getPixMapIndex(e.x + 1, e.y) + 1] = v;
				map[getPixMapIndex(e.x + 1, e.y) + 2] = v;
				map[getPixMapIndex(e.x, e.y + 1)] = v;
				map[getPixMapIndex(e.x, e.y + 1) + 1] = v;
				map[getPixMapIndex(e.x, e.y + 1) + 2] = v;
				map[getPixMapIndex(e.x + 1, e.y + 1)] = v;
				map[getPixMapIndex(e.x + 1, e.y + 1) + 1] = v;
				map[getPixMapIndex(e.x + 1, e.y + 1) + 2] = v;
				map[getPixMapIndex(e.x + 1, e.y) + 3] = 1.0f;
				map[getPixMapIndex(e.x, e.y + 1) + 3] = 1.0f;
				map[getPixMapIndex(e.x + 1, e.y + 1) + 3] = 1.0f;
			}
		}
		else {

			if ((e.polarity == PolarityEvent.Polarity.On) || ignorePolarityEnabled) {
				map[index] = onColor[0];
				map[index + 1] = onColor[1];
				map[index + 2] = onColor[2];

				if (expandToFour) {
					map[getPixMapIndex(e.x + 1, e.y)] = onColor[0];
					map[getPixMapIndex(e.x + 1, e.y) + 1] = onColor[1];
					map[getPixMapIndex(e.x + 1, e.y) + 2] = onColor[2];
					map[getPixMapIndex(e.x, e.y + 1)] = onColor[0];
					map[getPixMapIndex(e.x, e.y + 1) + 1] = onColor[1];
					map[getPixMapIndex(e.x, e.y + 1) + 2] = onColor[2];
					map[getPixMapIndex(e.x + 1, e.y + 1)] = onColor[0];
					map[getPixMapIndex(e.x + 1, e.y + 1) + 1] = onColor[1];
					map[getPixMapIndex(e.x + 1, e.y + 1) + 2] = onColor[2];
				}
			}
			else {
				map[index] = offColor[0];
				map[index + 1] = offColor[1];
				map[index + 2] = offColor[2];

				if (expandToFour) {
					map[getPixMapIndex(e.x + 1, e.y)] = offColor[0];
					map[getPixMapIndex(e.x + 1, e.y) + 1] = offColor[1];
					map[getPixMapIndex(e.x + 1, e.y) + 2] = offColor[2];
					map[getPixMapIndex(e.x, e.y + 1)] = offColor[0];
					map[getPixMapIndex(e.x, e.y + 1) + 1] = offColor[1];
					map[getPixMapIndex(e.x, e.y + 1) + 2] = offColor[2];
					map[getPixMapIndex(e.x + 1, e.y + 1)] = offColor[0];
					map[getPixMapIndex(e.x + 1, e.y + 1) + 1] = offColor[1];
					map[getPixMapIndex(e.x + 1, e.y + 1) + 2] = offColor[2];
				}
			}

			final float alpha = map[index + 3] + (1.0f / colorScale);
			map[index + 3] = normalizeEvent(alpha);

			if (expandToFour) {
				map[getPixMapIndex(e.x + 1, e.y) + 3] = alpha;
				map[getPixMapIndex(e.x, e.y + 1) + 3] = alpha;
				map[getPixMapIndex(e.x + 1, e.y + 1) + 3] = alpha;
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

			if (val < 0) {
				val = 0;
			}
			if ((val >= 0) && (val < minValue)) {
				minValue = val;
			}
			else if (val > maxValue) {
				maxValue = val;
			}

			// right here sample-reset value of this pixel is in val
			if (computeHistograms) {
				if (!((DavisChip) chip).getAutoExposureController().isCenterWeighted()) {
					nextHist.add(val);
				}
				else {
					// randomly add histogram values to histogram depending on distance from center of image
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
			final ColorFilter color = ((ApsDvsEvent) e).getColorFilter();

			if (color == colorFilterSequence[0]) {
				x = x / 2;
				y = y / 2;
			}
			else if (color == colorFilterSequence[1]) {
				x = (x / 2) + (chip.getSizeX() / 2);
				y = y / 2;
			}
			else if (color == colorFilterSequence[2]) {
				x = (x / 2) + (chip.getSizeX() / 2);
				y = (y / 2) + (chip.getSizeY() / 2);
			}
			else { // No check here, only colorFilterSequence[3] possible here.
				x = x / 2;
				y = (y / 2) + (chip.getSizeY() / 2);
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

	@Override
	protected void endFrame(final int ts) {
		if (isAutoWhiteBalance()) {
			// white balance
			final float[] image = pixBuffer.array();
			float Rtotal = 0, Gtotal = 0, Btotal = 0;

			for (int y = 0; y < chip.getSizeY(); y++) {
				for (int x = 0; x < chip.getSizeX(); x++) {
					if ((y % 2) == 0) {
						// row 0, 2, 4 ... 478, from bottom of the image, containing W and B
						if ((x % 2) == 0) { // B
							Btotal = Btotal + image[getPixMapIndex(x, y)];
						}
					}
					else {
						// row 1, 3, 5 ... 479, from bottom of the image, containing R and G
						if ((x % 2) == 1) { // R
							Rtotal = Rtotal + image[getPixMapIndex(x, y)];
						}
						else { // G
							Gtotal = Gtotal + image[getPixMapIndex(x, y)];
						}
					}
				}
			}

			for (int y = 0; y < chip.getSizeY(); y++) {
				for (int x = 0; x < chip.getSizeX(); x++) {
					if ((y % 2) == 0) {
						// row 0, 2, 4 ... 478, from bottom of the image, containing W and B
						if ((x % 2) == 0) { // B
							image[getPixMapIndex(x, y)] = (Gtotal / Btotal) * image[getPixMapIndex(x, y)];
							// if (image[getPixMapIndex(x, y)] > 1) {
							// image[getPixMapIndex(x, y)] = 1;
							// }
						}
					}
					else {
						// row 1, 3, 5 ... 479, from bottom of the image, containing R and G
						if ((x % 2) == 1) { // R
							image[getPixMapIndex(x, y)] = (Gtotal / Rtotal) * image[getPixMapIndex(x, y)];
							// if (image[getPixMapIndex(x, y)] > 1) {
							// image[getPixMapIndex(x, y)] = 1;
							// }
						}
					}
				}
			}
		}

		if (!isSeparateAPSByColor()) {
			// color interpolation
			final float[] image = pixBuffer.array();

			for (int y = 0; y < chip.getSizeY(); y++) {
				for (int x = 0; x < chip.getSizeX(); x++) {
					if ((y % 2) == 0) {
						// row 0, 2, 4 ... 478, from bottom of the image, contianing W and B
						if ((x % 2) == 1) { // W
							// interpolating R for W
							if (y == 0) {
								// bottom egde of W
								image[getPixMapIndex(x, y)] = image[getPixMapIndex(x, y + 1)];
							}
							else {
								// rest of W
								image[getPixMapIndex(x, y)] = 0.5f * (image[getPixMapIndex(x, y + 1)] + image[getPixMapIndex(x, y - 1)]);
							}
							// interpolating B for W
							if (x == (chip.getSizeX() - 1)) {
								// right edge of W
								image[getPixMapIndex(x, y) + 2] = image[getPixMapIndex(x - 1, y) + 2];
							}
							else {
								// rest of W
								image[getPixMapIndex(x, y) + 2] = 0.5f
									* (image[getPixMapIndex(x - 1, y) + 2] + image[getPixMapIndex(x + 1, y) + 2]);
							}
							// interpolating G for W
							if (y == 0) {
								// bottom edge of W
								if (x == (chip.getSizeX() - 1)) {
									// bottom right corner of W
									image[getPixMapIndex(x, y) + 1] = image[getPixMapIndex(x - 1, y + 1) + 1];
								}
								else {
									// rest of the bottom edge of W
									image[getPixMapIndex(x, y) + 1] = 0.5f
										* (image[getPixMapIndex(x + 1, y + 1) + 1] + image[getPixMapIndex(x - 1, y + 1) + 1]);
								}
							}
							else if (x == (chip.getSizeX() - 1)) {
								// right edge of W excluding bottom right corner
								image[getPixMapIndex(x, y) + 1] = 0.5f
									* (image[getPixMapIndex(x - 1, y + 1) + 1] + image[getPixMapIndex(x - 1, y - 1) + 1]);
							}
							else {
								// rest of W
								image[getPixMapIndex(x, y) + 1] = 0.25f
									* (image[getPixMapIndex(x + 1, y + 1) + 1] + image[getPixMapIndex(x + 1, y - 1) + 1]
										+ image[getPixMapIndex(x - 1, y + 1) + 1] + image[getPixMapIndex(x - 1, y - 1) + 1]);
							}
						}
						else { // B
								// interpolating R for B
							if (y == 0) {
								// bottom edge of B
								if (x == 0) {
									// bottom left corner of B
									image[getPixMapIndex(x, y)] = image[getPixMapIndex(x + 1, y + 1)];
								}
								else {
									// rest of the bottom edge of B
									image[getPixMapIndex(x, y)] = 0.5f
										* (image[getPixMapIndex(x - 1, y + 1)] + image[getPixMapIndex(x + 1, y + 1)]);
								}
							}
							else if (x == 0) {
								// left edge of B excluding bottom left corner
								image[getPixMapIndex(x, y)] = 0.5f
									* (image[getPixMapIndex(x + 1, y + 1)] + image[getPixMapIndex(x + 1, y - 1)]);
							}
							else {
								// rest of B
								image[getPixMapIndex(x, y)] = 0.25f
									* (image[getPixMapIndex(x - 1, y - 1)] + image[getPixMapIndex(x - 1, y + 1)]
										+ image[getPixMapIndex(x + 1, y - 1)] + image[getPixMapIndex(x + 1, y + 1)]);
							}
							// interpolating G for B
							if (y == 0) {
								// bottom egde of B
								image[getPixMapIndex(x, y) + 1] = image[getPixMapIndex(x, y + 1) + 1];
							}
							else {
								// rest of B
								image[getPixMapIndex(x, y) + 1] = 0.5f
									* (image[getPixMapIndex(x, y - 1) + 1] + image[getPixMapIndex(x, y + 1) + 1]);
							}
						}
					}
					else {
						// row 1, 3, 5 ... 479, from bottom of the image, contianing R and G
						if ((x % 2) == 1) { // R
							// interpolation B for R
							if (y == (chip.getSizeY() - 1)) {
								// top edge of R
								if (x == (chip.getSizeX() - 1)) {
									// top right corner of R
									image[getPixMapIndex(x, y) + 2] = image[getPixMapIndex(x - 1, y - 1) + 2];
								}
								else {
									// rest of the top edge of R
									image[getPixMapIndex(x, y) + 2] = 0.5f
										* (image[getPixMapIndex(x - 1, y - 1) + 2] + image[getPixMapIndex(x + 1, y - 1) + 2]);
								}
							}
							else if (x == (chip.getSizeX() - 1)) {
								// right edge of R excluding top right corner
								image[getPixMapIndex(x, y) + 2] = 0.5f
									* (image[getPixMapIndex(x - 1, y + 1) + 2] + image[getPixMapIndex(x - 1, y - 1) + 2]);
							}
							else {
								// rest of R
								image[getPixMapIndex(x, y) + 2] = 0.25f
									* (image[getPixMapIndex(x - 1, y - 1) + 2] + image[getPixMapIndex(x - 1, y + 1) + 2]
										+ image[getPixMapIndex(x + 1, y - 1) + 2] + image[getPixMapIndex(x + 1, y + 1) + 2]);
							}
							// interpolating G for R
							if (x == (chip.getSizeX() - 1)) {
								// right egde of R
								image[getPixMapIndex(x, y) + 1] = image[getPixMapIndex(x - 1, y) + 1];
							}
							else {
								// rest of R
								image[getPixMapIndex(x, y) + 1] = 0.5f
									* (image[getPixMapIndex(x - 1, y) + 1] + image[getPixMapIndex(x + 1, y) + 1]);
							}
						}
						else { // G
								// interpolating R for G
							if (x == 0) {
								// left egde of G
								image[getPixMapIndex(x, y)] = image[getPixMapIndex(x + 1, y)];
							}
							else {
								// rest of G
								image[getPixMapIndex(x, y)] = 0.5f * (image[getPixMapIndex(x - 1, y)] + image[getPixMapIndex(x + 1, y)]);
							}
							// interpolating B for G
							if (y == (chip.getSizeY() - 1)) {
								// top egde of G
								image[getPixMapIndex(x, y) + 2] = image[getPixMapIndex(x, y - 1) + 2];
							}
							else {
								// rest of G
								image[getPixMapIndex(x, y) + 2] = 0.5f
									* (image[getPixMapIndex(x, y - 1) + 2] + image[getPixMapIndex(x, y + 1) + 2]);
							}
						}
					}

					image[getPixMapIndex(x, y) + 3] = 1;
				}
			}
		}

		if (isColorCorrection() && !isSeparateAPSByColor()) {
			final float[] image = pixBuffer.array();

			for (int y = 0; y < chip.getSizeY(); y++) {
				for (int x = 0; x < chip.getSizeX(); x++) {
					// Get current RGB values, since we modify them later on.
					final float R_original = image[getPixMapIndex(x, y)];
					final float G_original = image[getPixMapIndex(x, y) + 1];
					final float B_original = image[getPixMapIndex(x, y) + 2];

					image[getPixMapIndex(x, y)] = (((1.75f * R_original) + (-0.19f * G_original)) + (-0.56f * B_original)) + 0.15f;
					image[getPixMapIndex(x, y) + 1] = (-0.61f * R_original) + (1.39f * G_original) + (0.07f * B_original) + 0.21f;
					image[getPixMapIndex(x, y) + 2] = ((-0.42f * R_original) + (-1.13f * G_original)) + (2.87f * B_original) + 0.18f;

					// if (image[getPixMapIndex(x, y)] < 0) {
					// image[getPixMapIndex(x, y)] = 0;
					// }
					// if (image[getPixMapIndex(x, y)] > 1) {
					// image[getPixMapIndex(x, y)] = 1;
					// }
					// if (image[getPixMapIndex(x, y) + 1] < 0) {
					// image[getPixMapIndex(x, y) + 1] = 0;
					// }
					// if (image[getPixMapIndex(x, y) + 1] > 1) {
					// image[getPixMapIndex(x, y) + 1] = 1;
					// }
					// if (image[getPixMapIndex(x, y) + 2] < 0) {
					// image[getPixMapIndex(x, y) + 2] = 0;
					// }
					// if (image[getPixMapIndex(x, y) + 2] > 1) {
					// image[getPixMapIndex(x, y) + 2] = 1;
					// }
				}
			}
		}

		// End frame, copy pixBuffer for display.
		super.endFrame(ts);
	}
}
