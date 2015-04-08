/*
 * ChipRenderer.java
 *
 * Created on May 2, 2006, 1:49 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package eu.seebetter.ini.chips.davis;

import java.nio.FloatBuffer;
import java.util.Iterator;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEvent;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.ApsDvsEventRGBW;
import net.sf.jaer.event.ApsDvsEventRGBW.ColorFilter;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.graphics.AEFrameChipRenderer;
import net.sf.jaer.graphics.ChipRendererDisplayMethod;
import net.sf.jaer.util.histogram.SimpleHistogram;
import eu.seebetter.ini.chips.DavisChip;

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
public class DavisRGBW640Renderer extends AEFrameChipRenderer {

	protected FloatBuffer pixBuffer2;

	/**
	 * PropertyChange
	 */
	public static final String AGC_VALUES = "AGCValuesChanged";

	public DavisRGBW640Renderer(AEChip chip) {
		super(chip);
		if (chip.getNumPixels() == 0) {
			log.warning("chip has zero pixels; is the constuctor of AEFrameChipRenderer called before size of the AEChip is set?");
			return;
		}
		onColor = new float[4];
		offColor = new float[4];
		checkPixmapAllocation();
		// resetFrame(0.5f);
		// resetAnnotationFrame(0.0f); // don't call here because it depends on knowing desired rendering state, which
		// requires chip configuration, which might not be set yet
	}

	@Override
	protected void renderApsDvsEvents(EventPacket pkt) {

		if (getChip() instanceof DAVIS240BaseCamera) {
			computeHistograms = ((DAVIS240BaseCamera) chip).isShowImageHistogram()
				|| ((DavisChip) chip).isAutoExposureEnabled();
		}

		if (!accumulateEnabled) {
			resetMaps();
			if (numEventTypes > 2) {
				resetAnnotationFrame(0.0f);
			}
		}
		ApsDvsEventPacket packet = (ApsDvsEventPacket) pkt;

		checkPixmapAllocation();
		resetSelectedPixelEventCount(); // TODO fix locating pixel with xsel ysel

		this.packet = packet;
		if (!(packet.getEventPrototype() instanceof ApsDvsEvent)) {
			if ((warningCount++ % WARNING_INTERVAL) == 0) {
				log.warning("wrong input event class, got " + packet.getEventPrototype() + " but we need to have "
					+ ApsDvsEvent.class);
			}
			return;
		}
		boolean displayEvents = isDisplayEvents(), displayFrames = isDisplayFrames(), paused = chip.getAeViewer()
			.isPaused(), backwards = packet.getDurationUs() < 0;

		Iterator allItr = packet.fullIterator();
		setSpecialCount(0);
		while (allItr.hasNext()) {
			// The iterator only iterates over the DVS events
			ApsDvsEventRGBW e = (ApsDvsEventRGBW) allItr.next();
			if (e.isSpecial()) {
				setSpecialCount(specialCount + 1); // TODO optimize special count increment
				continue;
			}
			int type = e.getType();
			boolean isAdcSampleFlag = e.isSampleEvent();
			if (!isAdcSampleFlag) {
				if (displayEvents) {
					if ((xsel >= 0) && (ysel >= 0)) { // find correct mouse pixel interpretation to make sounds for
														// large pixels
						int xs = xsel, ys = ysel;
						if ((e.x == xs) && (e.y == ys)) {
							playSpike(type);
						}
					}
					updateEventMaps(e);
				}
			}
			else if (!backwards && isAdcSampleFlag && displayFrames && !paused) { // TODO need to handle single step
																					// updates here
				updateFrameBuffer(e);
			}
		}
	}

	@Override
	protected void checkPixmapAllocation() {
		super.checkPixmapAllocation();

		final int n = 4 * textureWidth * textureHeight;
		if ((pixBuffer2 == null) || (pixBuffer2.capacity() < n)) {
			pixBuffer2 = FloatBuffer.allocate(n);
		}
	}

	/**
	 * Overridden to do CDAVIS rendering
	 *
	 * @param e
	 *            the ADC sample event
	 */
	// @Override
	protected void updateFrameBuffer(ApsDvsEventRGBW e) {
		float[] buf = pixBuffer.array();
		float[] buf2 = pixBuffer2.array();

		// TODO if playing backwards, then frame will come out white because B sample comes before A
		if (e.isStartOfFrame()) {
			startFrame(e.timestamp);
		}
		else if (e.isResetRead()) {
			int index = getIndex(e);
			if ((index < 0) || (index >= buf.length)) {
				return;
			}
			int val = e.getAdcSample();
			buf[index] = val;
			// buf[index + 1] = val;
			// buf[index + 2] = val;
		}
		else if (e.isSignalRead()) {
			int index = getIndex(e);
			if ((index < 0) || (index >= buf.length)) {
				return;
			}
			int val = e.getAdcSample();
			buf2[index] = val;
			// buf2[index + 1] = val;
			// buf2[index + 2] = val;
		}
		else if (e.isCpResetRead()) {
			int index = getIndex(e);
			if ((index < 0) || (index >= buf.length)) {
				return;
			}
			int val = 0;
			if (e.getColorFilter() == ApsDvsEventRGBW.ColorFilter.W) {
				val = (int) ((buf[index] - buf2[index]) + ((7.35f / 2.13f) * (e.getAdcSample() - buf2[index])));
				// (Vreset-Vsignal)+C*(Vcpreset-Vsignal), C=7.35/2.13
			}
			else {
				val = (int) (buf[index] - buf2[index]);
			}
			if (val < minValue) {
				minValue = val;
			}
			else if (val > maxValue) {
				maxValue = val;
			}
			if (computeHistograms) {
				nextHist.add(val);
			}
			float fval = normalizeFramePixel(val);
			buf[index] = fval;
			buf[index + 1] = fval;
			buf[index + 2] = fval;
			buf[index + 3] = 1;
		}
		else if (e.isEndOfFrame()) {
			endFrame();
			SimpleHistogram tmp = currentHist;
			if (computeHistograms) {
				currentHist = nextHist;
				nextHist = tmp;
				nextHist.reset();
			}
			((DavisChip) chip).controlExposure();

		}
	}

	/**
	 * returns code that says whether this ADC sample event is RGB or White
	 * pixel
	 *
	 * @param e
	 * @return int 0-3 encoding sample type
	 */
	private int rgbwSampleType(ApsDvsEvent e) {
		return 0; // TODO fix for actual x,y RBBW mapping
	}

	/**
	 * Computes the normalized gray value from an ADC sample value using
	 * brightness (offset), contrast (multiplier), and gamma (power law). Takes
	 * account of the autoContrast setting which attempts to set value
	 * automatically to get image in range of display.
	 *
	 * @param value
	 *            the ADC value
	 * @return the gray value
	 */
	private float normalizeFramePixel(float value) {
		float v;
		if (!isUseAutoContrast()) { // fixed rendering computed here
			float gamma = getGamma();
			if (gamma == 1.0f) {
				v = ((getContrast() * value) + getBrightness()) / maxADC;
			}
			else {
				v = (float) (Math.pow((((getContrast() * value) + getBrightness()) / maxADC), gamma));
			}
		}
		else {
			java.awt.geom.Point2D.Float filter2d = autoContrast2DLowpassRangeFilter.getValue2d();
			float offset = filter2d.x;
			float range = (filter2d.y - filter2d.x);
			v = ((value - offset)) / (range);
			// System.out.println("offset="+offset+" range="+range+" value="+value+" v="+v);
		}
		if (v < 0) {
			v = 0;
		}
		else if (v > 1) {
			v = 1;
		}
		return v;
	}

	@Override
	protected int getIndex(BasicEvent e) {
		int x = e.x, y = e.y;

		if ((x < 0) || (y < 0) || (x >= sizeX) || (y >= sizeY)) {
			if ((System.currentTimeMillis() - lastWarningPrintedTimeMs) > INTERVAL_BETWEEEN_OUT_OF_BOUNDS_EXCEPTIONS_PRINTED_MS) {
				log.warning(String
					.format(
						"Event %s out of bounds and cannot be rendered in bounds sizeX=%d sizeY=%d - delaying next warning for %dms",
						e.toString(), sizeX, sizeY, INTERVAL_BETWEEEN_OUT_OF_BOUNDS_EXCEPTIONS_PRINTED_MS));
				lastWarningPrintedTimeMs = System.currentTimeMillis();
			}
			return -1;
		}

		if (isSeparateAPSByColor()) {
			ColorFilter cf = ((ApsDvsEventRGBW) e).getColorFilter();

			if (cf == ColorFilter.R) {
				x = x / 2;
				y = (y / 2) + 240;
			}
			else if (cf == ColorFilter.G) {
				x = (x / 2) + 320;
				y = (y / 2) + 240;
			}
			else if (cf == ColorFilter.B) {
				x = (x / 2) + 320;
				y = y / 2;
			}
			else { // W
				x = x / 2;
				y = y / 2;
			}
		}

		return 4 * (x + (y * textureWidth));
	}

	public boolean isSeparateAPSByColor() {
		return ((DavisDisplayConfigInterface) chip.getBiasgen()).isSeparateAPSByColor();
	}
        
        @Override
        protected void endFrame() {
            if (!isSeparateAPSByColor()) {
                //color interpolation
                float[] image = pixBuffer.array();
                for (int i = 0; i < (640 * 480); i++) {
                    int i2 = i % 1280;
                    if (i2 < 640) {
                        //row 0, 2, 4 ... 478, contianing W and B
                        if ((i2 % 2) == 0) { //W
                            //interpolating R for W
                            if (i < 640) {
                                //bottom egde of W
                                image[i * 4] = image[(i + 640) * 4];
                            } else {
                                //rest of W
                                image[i * 4] = 0.5f * (image[(i + 640) * 4] + image[(i - 640) * 4]);
                            }
                            //interpolating B for W
                            if (i2 == 0) {
                                //left edge of W
                                image[i * 4 + 2] = image[(i + 1) * 4 + 2];
                            } else {
                                //rest of W
                                image[i * 4 + 2] = 0.5f * (image[(i - 1) * 4 + 2] + image[(i + 1) * 4 + 2]);
                            }
                            //interpolating G for W
                            if (i < 640) {
                                //bottom edge of W
                                if (i2 == 0) {
                                    //bottom left corner of W
                                    image[i * 4 + 1] = image[(i + 641) * 4 + 1];
                                } else {
                                    //rest of the bottom edge of W
                                    image[i * 4 + 1] = 0.5f * (image[(i + 639) * 4 + 1] + image[(i + 641) * 4 + 1]);
                                }
                            } else if (i2 == 0) {
                                //left edge of W excluding bottom left corner
                                image[i * 4 + 1] = 0.5f * (image[(i - 639) * 4 + 1] + image[(i + 641) * 4 + 1]);
                            } else {
                                // rest of W
                                image[i * 4 + 1] = 0.25f * (image[(i - 639) * 4 + 1] + image[(i - 641) * 4 + 1]
                                        + image[(i + 639) * 4 + 1] + image[(i + 641) * 4 + 1]);
                            }
                        } else { //B
                            //interpolating R for B
                            if (i < 640) {
                                //bottom edge of B
                                if (i2 == 639) {
                                    //bottom right corner of B
                                    image[i * 4] = image[(i + 639) * 4];
                                } else {
                                    //rest of the bottom edge of B
                                    image[i * 4] = 0.5f * (image[(i + 639) * 4] + image[(i + 641) * 4]);
                                }
                            } else if (i2 == 639) {
                                //right edge of B excluding bottom right corner
                                image[i * 4] = 0.5f * (image[(i - 641) * 4] + image[(i + 639) * 4]);
                            } else {
                                // rest of W
                                image[i * 4] = 0.25f * (image[(i - 639) * 4] + image[(i - 641) * 4]
                                        + image[(i + 639) * 4] + image[(i + 641) * 4]);
                            }
                            //interpolating G for B
                            if (i < 640) {
                                //bottom egde of W
                                image[i * 4 + 1] = image[(i + 640) * 4 + 1];
                            } else {
                                //rest of W
                                image[i * 4 + 1] = 0.5f * (image[(i + 640) * 4 + 1] + image[(i - 640) * 4 + 1]);
                            }
                        }
                    } else {
                        //row 1, 3, 5 ... 479, contianing R and G
                        if ((i2 % 2) == 0) { //R
                            //interpolation B for R
                            if ((i / 640) > 478) {
                                //top edge of R
                                if (i2 == 640) {
                                    //top left corner of R
                                    image[i * 4 + 2] = image[(i - 639) * 4 + 2];
                                } else {
                                    //rest of the top edge of R
                                    image[i * 4 + 2] = 0.5f * (image[(i - 639) * 4 + 2] + image[(i - 641) * 4 + 2]);
                                }
                            } else if (i2 == 640) {
                                //left edge of R excluding top left corner
                                image[i * 4 + 2] = 0.5f * (image[(i + 641) * 4 + 2] + image[(i - 639) * 4 + 2]);
                            } else {
                                // rest of R
                                image[i * 4 + 2] = 0.25f * (image[(i - 639) * 4 + 2] + image[(i - 641) * 4 + 2]
                                        + image[(i + 639) * 4 + 2] + image[(i + 641) * 4 + 2]);
                            }
                            //interpolating G for R
                            if (i2 == 640) {
                                //left egde of R
                                image[i * 4 + 1] = image[(i + 1) * 4 + 1];
                            } else {
                                //rest of R
                                image[i * 4 + 1] = 0.5f * (image[(i + 1) * 4 + 1] + image[(i - 1) * 4 + 1]);
                            }
                        } else { //G
                            //interpolating R for G
                            if (i2 == 1279) {
                                //right egde of G
                                image[i * 4] = image[(i - 1) * 4];
                            } else {
                                //rest of R
                                image[i * 4] = 0.5f * (image[(i + 1) * 4] + image[(i - 1) * 4]);
                            }
                            //interpolating B for G
                            if ((i / 640) > 478) {
                                //top egde of G
                                image[i * 4 + 2] = image[(i - 640) * 4 + 2];
                            } else {
                                //rest of R
                                image[i * 4 + 2] = 0.5f * (image[(i + 640) * 4 + 2] + image[(i - 640) * 4 + 2]);
                            }
                        }
                    }
                    image[i * 4 + 3] = 1;
                }
                System.arraycopy(pixBuffer.array(), 0, pixmap.array(), 0, pixBuffer.array().length);
            }
            else {
                System.arraycopy(pixBuffer.array(), 0, pixmap.array(), 0, pixBuffer.array().length);
            }
            if (contrastController != null) {
                contrastController.endFrame(minValue, maxValue, timestamp);
            }
            getSupport().firePropertyChange(EVENT_NEW_FRAME_AVAILBLE, null, this); // TODO document what is sent and send something reasonable
        }
}
