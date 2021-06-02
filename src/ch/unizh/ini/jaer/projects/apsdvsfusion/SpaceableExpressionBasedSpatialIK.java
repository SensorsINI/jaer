/**
 *
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.util.prefs.Preferences;

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

	int projectingToZeroX = 0;
	int projectingToZeroY = 0;

	int scaledCenterX = 0;
	int scaledCenterY = 0;

	float[][] scaledConvolutionValues = null;

	//	int inputWidth = 1, inputHeight = 1, outputHeight = 1, outputWidth = 1;

//	private int zeroKernelOffsetX = 0, zeroKernelOffsetY = 0;
	/**
	 * @param width
	 * @param height
	 */
	public SpaceableExpressionBasedSpatialIK(int width, int height, Preferences prefs) {
		super(width, height, prefs);
		setName("SpaceableExpressionBasedSpatialIK");
		addExcludedProperty("spacingX");
		addExcludedProperty("spacingY");
		addExcludedProperty("spacingAutomatic");
	}

//	@Deprecated
//	public SpaceableExpressionBasedSpatialIK(int width, int height, Preferences parentPrefs,	String nodeName) {
//		super(width, height, parentPrefs, nodeName);
//		setName("SpaceableExpressionBasedSpatialIK");
//	}



	protected void updateScaledConvolutionValues() {
		int xSize = width, ySize = height;
		if (outWidthBiggerThanInWidth) {
			xSize *= spacingX;
		}
		if (outHeightBiggerThanInHeight) {
			ySize *= spacingY;
		}
		this.scaledCenterX = ((xSize-1) / 2) + ((centerX - ((width-1)/2)) * spacingX);
		this.scaledCenterY = ((ySize-1) / 2) + ((centerY - ((height-1)/2)) * spacingY);
		float[][] newScaledConvolutionValues = new float[xSize][ySize];
//		synchronized (SpatioTemporalFusion.getFilteringLock(this)) {
		synchronized (convolutionValuesLock) {
			for (int xc = 0; xc < convolutionValues.length; xc++) {
				int minX = (outWidthBiggerThanInWidth?xc*spacingX:xc);
				int maxX = (outWidthBiggerThanInWidth?minX+spacingX:minX+1);
				for (int x = minX; x < maxX; x++) {
					for (int yc = 0; yc < convolutionValues[xc].length; yc++) {
						int minY = (outHeightBiggerThanInHeight?yc*spacingY:yc);
						int maxY = (outHeightBiggerThanInHeight?minY+spacingY:minY+1);
						for (int y = minY; y < maxY; y++) {
							newScaledConvolutionValues[x][y] = convolutionValues[xc][yc];
						}
					}
				}
			}
		}

//		float[][][][] newSCV = new float[][][xSize][ySize];

		this.scaledConvolutionValues = newScaledConvolutionValues;

	}

	@Override
	protected void convolutionValuesChanged() {
		updateScaledConvolutionValues();
		super.convolutionValuesChanged();
	}


	@Override
	public synchronized void setInputOutputSizes(int inputWidth, int inputHeight, int outputWidth, int outputHeight) {
		synchronized (SpatioTemporalFusion.getFilteringLock(this)) {
			super.setInputSize(inputWidth, inputHeight);
			super.setOutputSize(outputWidth, outputHeight);
		}
//		recomputeMappings();

//		changeOffset((outputWidth - inputWidth) / 2 ,(outputWidth - outputHeight) / 2);

	}


	@Override
	protected void inputSizeChanged(int oldWidth, int oldHeight, int newWidth, int newHeight) {
		recomputeMappings();
	}

	@Override
	protected void outputSizeChanged(int oldWidth, int oldHeight, int newWidth, int newHeight) {
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
			getSupport().firePropertyChange("spacingAutomatic", !spacingAutomatic, spacingAutomatic);
			if (spacingAutomatic) {
				recomputeMappings();
			}
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
			if ((inputWidth - (outputWidth * spacingX)) > ((outputWidth * (spacingX+1)) - inputWidth)) {
				spacingX++;
			}
			if (spacingX != before) {
				getSupport().firePropertyChange("spacingX", before, spacingX);
			}

			// same for y:
			before = spacingY;
			spacingY = Math.max(1,inputHeight / outputHeight);
			// make sure the input space is covered nicely, non-covered input should be smaller than non-covered output
			if ((inputHeight - (outputHeight * spacingY)) > ((outputHeight * (spacingY+1)) - inputHeight)) {
				spacingX++;
			}
			if (spacingY != before) {
				getSupport().firePropertyChange("spacingY", before, spacingY);
			}

		}
		updateScaledConvolutionValues();
	}

	@Override
	protected void recomputeMappings() {
		synchronized (SpatioTemporalFusion.getFilteringLock(this)) {
			int outputWidth = getOutputWidth();
			int outputHeight = getOutputHeight();
			if (outputWidth <= 0) {
				outputWidth = 1;
			}
			if (outputHeight <= 0) {
				outputHeight = 1;
			}
			int inputWidth = getInputWidth();
			int inputHeight = getInputHeight();
			if (inputWidth <= 0) {
				inputWidth = 1;
			}
			if (inputHeight <= 0) {
				inputHeight = 1;
			}

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

			int restX = inputWidth - (((outputWidth-1) * spacingX) + 1);
			int startX = restX / 2;

			// the input pixel that maps to the output pixel with index 0 through the first position of the kernel:
			if (scaledConvolutionValues != null) {
				projectingToZeroX = startX + (scaledConvolutionValues.length - scaledCenterX - 1);
			}
			else {
				projectingToZeroX = 0;
			}


			// which position of the input would be centered on the position -1 in
			// the output?
			int centeredOnMinusOne = -spacingX + startX;
			// starting offset in the kernel, sufficiently increased (spacingX *
			// (outputWidth +2)) to avoid problems with negative numbers
			kernelOffsetX = scaledCenterX + centeredOnMinusOne
					+ (spacingX * (outputWidth + 2));

			// now the same for y:
			int restY = inputHeight - (((outputHeight-1) * spacingY) + 1);
			int startY = restY / 2;

			if ((scaledConvolutionValues != null) && (scaledConvolutionValues.length > 0)) {
				projectingToZeroY = startY + (scaledConvolutionValues[0].length - scaledCenterY - 1);
			}
			else {
				projectingToZeroY = 0;
			}

			centeredOnMinusOne = -spacingY + startY;
			kernelOffsetY = scaledCenterY + centeredOnMinusOne
					+ (spacingY * (outputHeight + 2));

			fillPrecomputedProjectionBounds();
		}
	}


	class ProjectionBounds {
		int spacingX, spacingY;
		int ox, oy;
		int kx, ky;
		boolean nothingToDo = false;
	}

	protected ProjectionBounds computeProjectionBounds(int tx, int ty, int width, int height, int outputWidth, int outputHeight) {
		ProjectionBounds ret = new ProjectionBounds();
		if (!outWidthBiggerThanInWidth) {
			ret.spacingX = this.spacingX;
			// assign to tx the difference between target and zero-projection
			tx -= projectingToZeroX;
			if (tx >=0) {
				ret.ox = (tx / this.spacingX);//(-offsetX/spacingX);
				ret.kx = (ret.ox*this.spacingX) - tx;
				if (ret.kx < 0) {
					ret.ox++;
					ret.kx += this.spacingX;
				}
			}
			else {
				ret.ox = 0;
				ret.kx = -tx;
			}
		}
		else {
			ret.spacingX = 1;
			ret.ox = ((projectingToZeroX - width) + 1) + (this.spacingX * tx);
			ret.kx = 0;
			if (ret.ox < 0) {
				ret.kx = -ret.ox;
				ret.ox = 0;
			}
		}

		if (!outHeightBiggerThanInHeight) {
			ret.spacingY = this.spacingY;
			// assign to ty the difference between target and zero-projection
			ty -= projectingToZeroY;
			if (ty>=0) {
				ret.oy = (ty / this.spacingY);//(-offsetX/spacingX);
				ret.ky = (ret.oy*this.spacingY) - ty;
				if (ret.ky < 0) {
					ret.oy++;
					ret.ky += this.spacingY;
				}
			}
			else {
				ret.oy = 0;
				ret.ky = -ty;
			}
		}
		else {
			ret.spacingY = 1;
			ret.oy = ((projectingToZeroY - height) + 1) + (this.spacingY * ty);
			ret.ky = 0;
			if (ret.oy < 0) {
				ret.ky = -ret.oy;
				ret.oy = 0;
			}
		}
		ret.nothingToDo = !((ret.ox < outputWidth) && (ret.kx < width) && (ret.ky < height) && (ret.oy < outputHeight));
		return ret;
	}

	private ProjectionBounds[][] precomputedProjectionBounds = new ProjectionBounds[0][0];
	private int assumedKernelWidth = 0, assumedKernelHeight = 0;
	private int assumedOutputWidth = 0, assumedOutputHeight = 0;
	private int assumedInputWidth = 0, assumedInputHeight = 0;


	protected void fillPrecomputedProjectionBounds() {
		int inputWidth = getInputWidth();
		int inputHeight = getInputHeight();
		int assumedOutputWidth = getOutputWidth();
		int assumedOutputHeight = getOutputHeight();
		int assumedKernelWidth = convolutionValues.length;
		int assumedKernelHeight = (assumedKernelWidth > 0)?convolutionValues[0].length:0;
		ProjectionBounds[][] precomputedProjectionBounds = new ProjectionBounds[inputWidth][inputHeight];
		for (int x = 0; x < inputWidth; x++) {
			for (int y = 0; y < inputHeight; y++) {
				precomputedProjectionBounds[x][y] = computeProjectionBounds(x, y, assumedKernelWidth, assumedKernelHeight, assumedOutputWidth, assumedOutputHeight);
			}
		}
		synchronized (SpatioTemporalFusion.getFilteringLock(this)) {
			this.assumedOutputWidth = assumedOutputWidth;
			this.assumedOutputHeight = assumedOutputHeight;
			this.assumedInputWidth = inputWidth;
			this.assumedInputHeight = inputHeight;
			this.assumedKernelWidth = assumedKernelWidth;
			this.assumedKernelHeight = assumedKernelHeight;
			this.precomputedProjectionBounds = precomputedProjectionBounds;
		}

	}

	@Override
	public void signalAt(int tx, int ty, int time, double value) {
		if (isEnabled()) {
			if ((tx >= 0) && (ty >= 0) && (tx < assumedInputWidth) && (ty < assumedInputHeight)) {
				ProjectionBounds bounds = precomputedProjectionBounds[tx][ty];
				// copy link to make sure the convolutionValues don't change in the meantime...
				float[][] convolutionValues = scaledConvolutionValues;
				int kernelWidth = (convolutionValues != null)?convolutionValues.length:0;
				int kernelHeight = (kernelWidth > 0)?convolutionValues[0].length:0;
				int outputWidth = getOutputWidth();
				int outputHeight = getOutputHeight();
				if ((outputWidth == assumedOutputWidth) && (outputHeight == assumedOutputHeight) && (kernelWidth == assumedKernelWidth) && (kernelHeight == assumedKernelHeight)) {
					bounds = precomputedProjectionBounds[tx][ty];
				}
				else {
					bounds = computeProjectionBounds(tx, ty, kernelWidth, kernelHeight, outputWidth, outputHeight);
				}
				int spacingX = bounds.spacingX;
				int spacingY = bounds.spacingY;
				int ox = bounds.ox;
				int kx = bounds.kx;


				// now: increase x and y until o* or k* hit the boundaries
				if (!bounds.nothingToDo) {
					final FiringModelMap map = getOutputMap();
//					synchronized (map) {
						if (value == 1.0) {
							for (; (ox < outputWidth) && (kx < kernelWidth); ox++, kx+= spacingX) {
								for (int ky = bounds.ky, oy = bounds.oy; (oy < outputHeight) && (ky < kernelHeight); oy++, ky+= spacingY) {
									map.signalAt(ox, oy, convolutionValues[kx][ky], time);
								}
							}
						} else if (value == -1.0) {
							for (; (ox < outputWidth) && (kx < kernelWidth); ox++, kx+= spacingX) {
								for (int ky = bounds.ky, oy = bounds.oy; (oy < outputHeight) && (ky < kernelHeight); oy++, ky+= spacingY) {
									map.signalAt(ox, oy, -convolutionValues[kx][ky], time);
								}
							}
						} else if (value != 0.0) {
							for (; (ox < outputWidth) && (kx < kernelWidth); ox++, kx+= spacingX) {
								for (int ky = bounds.ky, oy = bounds.oy; (oy < outputHeight) && (ky < kernelHeight); oy++, ky+= spacingY) {
									map.signalAt(ox, oy, value * convolutionValues[kx][ky], time);
								}
							}
						}
//					}
				}

			}
		}
	}


//	public synchronized void savePrefs(Preferences prefs, String prefString) {
//		super.savePrefs(prefs, prefString);
//		prefs.putInt(prefString+"spacingX",spacingX);
//		prefs.putInt(prefString+"spacingY",spacingY);
//		prefs.putInt(prefString+"kernelOffsetX",kernelOffsetX);
//		prefs.putInt(prefString+"kernelOffsetX",kernelOffsetY);
//	}
//
//
//	public synchronized void loadPrefs(Preferences prefs, String prefString) {
//		spacingX = prefs.getInt(prefString+"spacingX",spacingX);
//		spacingY = prefs.getInt(prefString+"spacingY",spacingY);
//		kernelOffsetX = prefs.getInt(prefString+"kernelOffsetX",kernelOffsetX);
//		kernelOffsetY = prefs.getInt(prefString+"kernelOffsetX",kernelOffsetY);
////		inputWidth = prefs.getInt(prefString+"inputWidth",inputWidth);
////		inputHeight = prefs.getInt(prefString+"inputHeight",inputHeight);
////		outputWidth = prefs.getInt(prefString+"outputWidth",outputWidth);
////		outputHeight = prefs.getInt(prefString+"outputHeight",outputHeight);
//		super.loadPrefs(prefs, prefString);
//	}
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
		int inX = 20, inY = 19, outX = 8, outY = 8;
		k.setOutputMap(new ArrayFiringModelMap(outX,outY,null,null) {
			@Override
			public FiringModel get(int x, int y) {
				pos[0] = x; pos[1] = y;
				return model;
			}
			@Override
			public void signalAt(int x, int y, double value, int timeInUs) {
				pos[0] = x; pos[1] = y;
				model.receiveSpike(value, timeInUs);
			}
		});
		k.setInputOutputSizes(inX, inY, outX, outY);
		k.setExpressionString("x + "+((k.getWidth()-1)/2.0f));

		for (int i = 0; i < inX; i++) {
			System.out.println("Injecting Signal at "+i+":");
			k.signalAt(i, 1, 0, 1.0);
		}
	}
}
