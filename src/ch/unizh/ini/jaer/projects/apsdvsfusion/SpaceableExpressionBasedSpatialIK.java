/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.awt.geom.Point2D;
import java.util.prefs.Preferences;

import net.sf.jaer.event.PolarityEvent.Polarity;

/**
 * @author Dennis
 *
 */
public class SpaceableExpressionBasedSpatialIK extends
		ExpressionBasedSpatialInputKernel {
	boolean outWidthBiggerThanInWidth = true, outHeightBiggerThanInHeight = true;
	
	boolean spacingAutomatic = true;
	int spacingX = 1, spacingY = 1;
	int kernelOffsetX = 0, kernelOffsetY = 0;
//	int inputWidth = 1, inputHeight = 1, outputHeight = 1, outputWidth = 1;
	
//	private int zeroKernelOffsetX = 0, zeroKernelOffsetY = 0;
	/**
	 * @param width
	 * @param height
	 */
	public SpaceableExpressionBasedSpatialIK(int width, int height, Preferences prefs) {
		super(width, height, prefs);
		setName("SpaceableExpressionBasedSpatialIK");
	}

//	@Deprecated
//	public SpaceableExpressionBasedSpatialIK(int width, int height, Preferences parentPrefs,	String nodeName) {
//		super(width, height, parentPrefs, nodeName);
//		setName("SpaceableExpressionBasedSpatialIK");
//	}
	
	
	
	public synchronized void setInputOutputSizes(int inputWidth, int inputHeight, int outputWidth, int outputHeight) {
		super.setInputSize(inputWidth, inputHeight);
		super.setOutputSize(outputWidth, outputHeight);
//		recomputeMappings();

//		changeOffset((outputWidth - inputWidth) / 2 ,(outputWidth - outputHeight) / 2);

	}


	protected synchronized void inputSizeChanged(int oldWidth, int oldHeight, int newWidth, int newHeight) {
		recomputeMappings();
	}
	
	protected synchronized void outputSizeChanged(int oldWidth, int oldHeight, int newWidth, int newHeight) {
		recomputeMappings();
	}
	
	
	/**
	 * @return the spacingAutomatic
	 */
	public boolean isSpacingAutomatic() {
		return spacingAutomatic;
	}

	/**
	 * @param spacingAutomatic the spacingAutomatic to set
	 */
	public void setSpacingAutomatic(boolean spacingAutomatic) {
		if (spacingAutomatic != this.spacingAutomatic) {
			this.spacingAutomatic = spacingAutomatic;
			getSupport().firePropertyChange("spacingAutomatic", spacingAutomatic, !spacingAutomatic);
		}
	}
	
	/**
	 * @return the spacingX
	 */
	public int getSpacingX() {
		return spacingX;
	}

	/**
	 * @param spacingX the spacingX to set
	 */
	public void setSpacingX(int spacingX) {
		int before = this.spacingX;
		this.spacingX = spacingX;
		recomputeMappings();
		getSupport().firePropertyChange("spacingX", before, this.spacingX);
	}

	/**
	 * @return the spacingY
	 */
	public int getSpacingY() {
		return spacingY;
	}

	/**
	 * @param spacingY the spacingY to set
	 */
	public void setSpacingY(int spacingY) {
		int before = this.spacingY;
		this.spacingY = spacingY;
		recomputeMappings();
		getSupport().firePropertyChange("spacingY", before, this.spacingY);
	}

	protected void computeSpacing(int inputWidth, int inputHeight, int outputWidth, int outputHeight) {
		if (isSpacingAutomatic()) {
			int before = spacingX;
			spacingX = Math.max(1,inputWidth / outputWidth);
			// make sure the input space is covered nicely, non-covered input should be smaller than non-covered output
			if (inputWidth - outputWidth * spacingX > outputWidth * (spacingX+1) - inputWidth)
				spacingX++;
			if (spacingX != before)
				getSupport().firePropertyChange("spacingX", before, spacingX);

			// same for y:
			before = spacingY;
			spacingY = Math.max(1,inputHeight / outputHeight);
			// make sure the input space is covered nicely, non-covered input should be smaller than non-covered output
			if (inputHeight - outputHeight * spacingY > outputHeight * (spacingY+1) - inputHeight)
				spacingX++;
			if (spacingY != before)
				getSupport().firePropertyChange("spacingY", before, spacingY);
		
		}
	}
	
	@Override
	protected void recomputeMappings() {
		int outputWidth = getOutputWidth();
		int outputHeight = getOutputHeight();
		if (outputWidth <= 0) outputWidth = 1;
		if (outputHeight <= 0) outputHeight = 1;
		int inputWidth = getInputWidth();
		int inputHeight = getInputHeight();
		if (inputWidth <= 0) inputWidth = 1;
		if (inputHeight <= 0) inputHeight = 1;
		
		outWidthBiggerThanInWidth = outputWidth >= inputWidth;
		outHeightBiggerThanInHeight = outputHeight >= inputHeight;
		
		// flip input and output sizes if output is bigger than input:
		if (outWidthBiggerThanInWidth) {
			int dummy = outputWidth;
			outputWidth = inputWidth;
			inputWidth = dummy;
		}
		if (outHeightBiggerThanInHeight) {
			int dummy = outputHeight;
			outputHeight = inputHeight;
			inputHeight = dummy;
		}
		
		computeSpacing(inputWidth, inputHeight, outputWidth, outputHeight);

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
		rest = inputHeight - ((outputHeight-1) * spacingY + 1);
		int startY = rest / 2;
		centeredOnMinusOne = -spacingY + startY;
		kernelOffsetY = centerY + centeredOnMinusOne
				+ (spacingY * (outputHeight + 2));
		
		
	}
	
	
	@Override
	public void signalAt(int tx, int ty, int time, double value) {
		if (isEnabled()) {
			int kx = (kernelOffsetX - tx) % spacingX;
			int ox = (getOutputWidth() + 1) - (kernelOffsetX - tx) / spacingX; 
			if (ox < 0) {
				kx -= spacingX * ox;
				ox = 0;
			}
	
			int kyInitial = (kernelOffsetY - ty) % spacingY;
			int oyInitial = (getOutputHeight() + 1) - (kernelOffsetY - ty) / spacingY;
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
								FiringModel firingModel = map.get(ox,oy);
								if (firingModel != null)
									firingModel.receiveSpike(convolutionValues[kx][ky], time);
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
								FiringModel firingModel = map.get(ox,oy);
								if (firingModel != null)
									firingModel.receiveSpike(-convolutionValues[kx][ky], time);
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
								FiringModel firingModel = map.get(ox,oy);
								if (firingModel != null)
									firingModel.receiveSpike(value * convolutionValues[kx][ky], time);
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
//		prefs.putInt(prefString+"inputWidth",inputWidth);
//		prefs.putInt(prefString+"inputHeight",inputHeight);
//		prefs.putInt(prefString+"outputWidth",outputWidth);
//		prefs.putInt(prefString+"outputHeight",outputHeight);
	}

	
	public synchronized void loadPrefs(Preferences prefs, String prefString) {
		spacingX = prefs.getInt(prefString+"spacingX",spacingX);
		spacingY = prefs.getInt(prefString+"spacingY",spacingY);
		kernelOffsetX = prefs.getInt(prefString+"kernelOffsetX",kernelOffsetX);
		kernelOffsetY = prefs.getInt(prefString+"kernelOffsetX",kernelOffsetY);
//		inputWidth = prefs.getInt(prefString+"inputWidth",inputWidth);
//		inputHeight = prefs.getInt(prefString+"inputHeight",inputHeight);
//		outputWidth = prefs.getInt(prefString+"outputWidth",outputWidth);
//		outputHeight = prefs.getInt(prefString+"outputHeight",outputHeight);
		super.loadPrefs(prefs, prefString);
	}
	public static void main(String[] args) {
		SpaceableExpressionBasedSpatialIK k = new SpaceableExpressionBasedSpatialIK(4, 4, null);
		final int[] pos = new int[2];
		final FiringModel model = new FiringModel(0,0,null) {
			@Override
			public void reset() {
			}
			
			@Override
			public void receiveSpike(double value, int timeInUs) {
				System.out.println("Signal "+value+" at ("+pos[0]+"/"+pos[1]+")");
			}
			
		};
		k.setOutputMap(new ArrayFiringModelMap(16,16,null,null) {
			@Override
			public FiringModel get(int x, int y) {
				pos[0] = x; pos[1] = y;
				return model;
			}
		});
		k.setInputOutputSizes(35, 35, 16, 16);
		k.setExpressionString("x + 1.5");
		
		k.signalAt(0, 0, 0, 1.0);
		k.signalAt(1, 0, 0, 1.0);
		k.signalAt(2, 0, 0, 1.0);
		k.signalAt(3, 0, 0, 1.0);
	}
}
