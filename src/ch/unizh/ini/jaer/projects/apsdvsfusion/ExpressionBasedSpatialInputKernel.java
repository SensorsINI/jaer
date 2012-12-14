/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

import java.util.HashMap;

import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.PolarityEvent.Polarity;

import ch.unizh.ini.jaer.projects.apsdvsfusion.mathexpression.ExpressionTreeBuilder;
import ch.unizh.ini.jaer.projects.apsdvsfusion.mathexpression.ExpressionTreeNode;
import ch.unizh.ini.jaer.projects.apsdvsfusion.mathexpression.IllegalExpressionException;

/**
 * @author Dennis
 *
 */
public class ExpressionBasedSpatialInputKernel implements InputKernel {
	int width = -1, height = -1;

	int centerX, centerY;

	String onExpressionString = "0.01";
	String offExpressionString = "0.01";
	
	ExpressionTreeNode onExpressionTree = null;
	ExpressionTreeNode offExpressionTree = null;
	
	boolean evaluateExpressionAsReceptiveField = true;
	float[][] onConvolutionValues = null;
	public synchronized float[][] getOnConvolutionValues() {
		return onConvolutionValues;
	}

	public synchronized float[][] getOffConvolutionValues() {
		return offConvolutionValues;
	}

	float[][] offConvolutionValues = null;

	/**
	 * 
	 */
	public ExpressionBasedSpatialInputKernel(int width, int height) {
		changeSize(width, height);
	}
	
	public String getOnExpressionString() {
		return onExpressionString;
	}

	public void setOnExpressionString(String onExpressionString) {
		try {
			this.onConvolutionValues = evaluateExpression(onExpressionString, onConvolutionValues, this.onExpressionString);
			this.onExpressionString = onExpressionString;
		} catch (IllegalExpressionException e) {
		}
	}

	public String getOffExpressionString() {
		return offExpressionString;
	}

	public void setOffExpressionString(String offExpressionString) {
		try {
			this.offConvolutionValues = evaluateExpression(offExpressionString, offConvolutionValues, this.offExpressionString);
			this.offExpressionString = offExpressionString;
		} catch (IllegalExpressionException e) {
		}
	}
	
	protected synchronized float[][] evaluateExpression(String expressionString, float[][] oldConvolutionValues, String oldString) throws IllegalExpressionException {
		ExpressionTreeNode et = ExpressionTreeBuilder.parseString(expressionString);
		float[][] newValues = oldConvolutionValues.clone();
		try {
			HashMap<String, Double> map = new HashMap<String, Double>();
			for (int x = 0; x < width; x++) {
				map.put("x", (double)(x - centerX));
				for (int y = 0; y < height; y++) {
					map.put("y", (double)(y - centerY));
					newValues[x][y] = (float)et.evaluate(map);
				}
			}
			
			if (evaluateExpressionAsReceptiveField) {
				// reflect if in receptive field mode:
				for (int x = 0; x < width; x++) {
					for (int y = 0; y < height; y++) {
						oldConvolutionValues[width-x-1][height-y-1] = newValues[x][y];
					}
				}
				return oldConvolutionValues;
			}
			else
				return newValues;
		}
		catch (RuntimeException e) {
			throw new IllegalExpressionException("Runtime Exception returned while evaluating expression tree!: "+e.toString());
		}
	}
	
	public synchronized void setWidth(int width) {
		changeSize(width, height);
	}
	public synchronized void setHeight(int height) {
		changeSize(width, height);
	}

	public synchronized int getWidth() {
		return width;
	}

	public synchronized int getHeight() {
		return height;
	}
	
	public synchronized void changeSize(int width, int height) {
		if (width != this.width || height != this.height && width >= 0 && height >= 0) {
			this.width = width;
			this.height = height;
			onConvolutionValues = new float[width][height];
			offConvolutionValues = new float[width][height];
			this.centerX = -width/2;
			this.centerY = -height/2;
		}
	}
	
	public synchronized int getCenterX() {
		return centerX;
	}

	public synchronized int getCenterY() {
		return centerY;
	}

	public synchronized void setCenterX(int centerX) {
		this.centerX = centerX;
	}
	
	public synchronized void setCenterY(int centerY) {
		this.centerY = centerY;
	}

	@Override
	public void apply(int tx, int ty, int time, Polarity polarity,
			FiringModelMap map, SpikeHandler spikeHandler) {
		tx += centerX;
		ty += centerY;
		short minx = (short)Math.max(0, -tx), maxx = (short)Math.min(width, map.getSizeX()-tx);
		short miny = (short)Math.max(0, -ty), maxy = (short)Math.min(height, map.getSizeY()-ty);
		tx += minx; ty += miny;
		float[][] convolutionValues;
		if (polarity == Polarity.On) 
			convolutionValues = onConvolutionValues;
		else
			convolutionValues = offConvolutionValues;
		for (short x = minx; x < maxx; x++, tx++) {
			for (short y = miny, ity = (short)ty; y < maxy; y++, ity++) {
				if (map.get(tx,ity).receiveSpike(convolutionValues[x][y])) {
					spikeHandler.spikeAt(tx,ity,time, Polarity.On);
				}
			}
		}
	}

	
	

}
