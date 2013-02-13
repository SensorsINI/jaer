/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.util.prefs.Preferences;

import net.sf.jaer.event.PolarityEvent.Polarity;

/**
 * @author Dennis
 *
 */
public class SpaceableExpressionBasedSpatialIK extends
		ExpressionBasedSpatialInputKernel {
	int spacingX = 1, spacingY = 1;
	int kernelOffsetX = 0, kernelOffsetY = 0;
	int inputWidth = 1, inputHeight = 1, outputHeight = 1, outputWidth = 1;
	
//	private int zeroKernelOffsetX = 0, zeroKernelOffsetY = 0;
	/**
	 * @param width
	 * @param height
	 */
	public SpaceableExpressionBasedSpatialIK(int width, int height, Preferences parentPrefs,	String nodeName) {
		super(width, height, parentPrefs, nodeName);
		setName("SpaceableExpressionBasedSpatialIK");
	}
	
	public synchronized void setInputOutputSizes(int inputWidth, int inputHeight, int outputWidth, int outputHeight) {
		this.inputHeight = inputHeight;
		this.inputWidth = inputWidth;
		this.outputHeight = outputHeight;
		this.outputWidth = outputWidth;
		recomputeMappings();

//		changeOffset((outputWidth - inputWidth) / 2 ,(outputWidth - outputHeight) / 2);

	}

	@Override
	protected void recomputeMappings() {
		if (outputWidth <= 0) outputWidth = 1;
		if (outputHeight <= 0) outputHeight = 1;
		spacingX = Math.max(1,inputWidth / outputWidth);
		int rest = inputWidth - ((outputWidth-1) * spacingX + 1);
		int startX = rest / 2;
		// which position of the input would be centered on the position -1 in
		// the output?
		int centeredOnMinusOne = -spacingX + startX;
		// starting offset in the kernel, sufficiently increased (spacingX *
		// (outputWidth +2)) to avoid problems with negative numbers
		kernelOffsetX = centerX + centeredOnMinusOne
				+ (spacingX * (outputWidth + 2));
		
		// now the same for y:
		spacingY = Math.max(1,inputHeight / outputHeight);
		rest = inputHeight - ((outputHeight-1) * spacingY + 1);
		int startY = rest / 2;
		centeredOnMinusOne = -spacingY + startY;
		kernelOffsetY = centerY + centeredOnMinusOne
				+ (spacingY * (outputHeight + 2));
	}
	
	
	@Override
	public void signalAt(int tx, int ty, int time, double value) {
		int kx = (kernelOffsetX - tx) % spacingX;
		int ox = (outputWidth + 1) - (kernelOffsetX - tx) / spacingX; 
		if (ox < 0) {
			kx -= spacingX * ox;
			ox = 0;
		}

		int kyInitial = (kernelOffsetY - ty) % spacingY;
		int oyInitial = (outputHeight + 1) - (kernelOffsetY - ty) / spacingY;
		if (oyInitial < 0) {
			kyInitial -= spacingY * oyInitial;
			oyInitial = 0;
		}

//		float[][] convolutionValues;
//		if (polarity == Polarity.On) 
//			convolutionValues = onConvolutionValues;
//		else
//			convolutionValues = offConvolutionValues;

		
		int maxOx = getOutputWidth();
		int maxOy = getOutputHeight();
		// nonsense for debugging...
		if (convolutionValues.length < width) {
			maxOx = getOutputWidth();
		}
		if (ox < maxOx && kx < width && kyInitial < height && oyInitial < maxOy) {
			final FiringModelMap map = getOutputMap();
			if (value == 1.0) {
				synchronized (map) {
					while (ox < maxOx && kx < width) {
						int ky = kyInitial, oy = oyInitial;
						while (oy < maxOy && ky < height) {
							map.get(ox,oy).receiveSpike(convolutionValues[kx][ky], time);
							ky += spacingY;
							oy ++;
						}
						kx += spacingX;
						ox ++;
					}
				}
			} else if (value == -1.0) {
				synchronized (map) {
					while (ox < maxOx && kx < width) {
						int ky = kyInitial, oy = oyInitial;
						while (oy < maxOy && ky < height) {
							map.get(ox,oy).receiveSpike(-convolutionValues[kx][ky], time);
							ky += spacingY;
							oy ++;
						}
						kx += spacingX;
						ox ++;
					}
				}
			} else if (value != 0.0) {
				synchronized (map) {
					while (ox < maxOx && kx < width) {
						int ky = kyInitial, oy = oyInitial;
						while (oy < maxOy && ky < height) {
							map.get(ox,oy).receiveSpike(value * convolutionValues[kx][ky], time);
							ky += spacingY;
							oy ++;
						}
						kx += spacingX;
						ox ++;
					}
				}
			}


		}
	}
	
//	public synchronized void apply(int tx, int ty, int time, Polarity polarity,
//			FiringModelMap map, SpikeHandler spikeHandler) {
//	}
	
	public synchronized void savePrefs(Preferences prefs, String prefString) {
		super.savePrefs(prefs, prefString);
		prefs.putInt(prefString+"spacingX",spacingX);
		prefs.putInt(prefString+"spacingY",spacingY);
		prefs.putInt(prefString+"kernelOffsetX",kernelOffsetX);
		prefs.putInt(prefString+"kernelOffsetX",kernelOffsetY);
		prefs.putInt(prefString+"inputWidth",inputWidth);
		prefs.putInt(prefString+"inputHeight",inputHeight);
		prefs.putInt(prefString+"outputWidth",outputWidth);
		prefs.putInt(prefString+"outputHeight",outputHeight);
	}

	
	public synchronized void loadPrefs(Preferences prefs, String prefString) {
		spacingX = prefs.getInt(prefString+"spacingX",spacingX);
		spacingY = prefs.getInt(prefString+"spacingY",spacingY);
		kernelOffsetX = prefs.getInt(prefString+"kernelOffsetX",kernelOffsetX);
		kernelOffsetY = prefs.getInt(prefString+"kernelOffsetX",kernelOffsetY);
		inputWidth = prefs.getInt(prefString+"inputWidth",inputWidth);
		inputHeight = prefs.getInt(prefString+"inputHeight",inputHeight);
		outputWidth = prefs.getInt(prefString+"outputWidth",outputWidth);
		outputHeight = prefs.getInt(prefString+"outputHeight",outputHeight);
		super.loadPrefs(prefs, prefString);
	}
}
