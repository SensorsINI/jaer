/**
 * 
 */
package ch.unizh.ini.jaer.projects.apsdvsfusion;

/**
 * @author Dennis
 *
 */
public class SpatialInputKernel {
	int width, height;
	int offsetX, offsetY;
	
	float[][] convolutionValues = null;

	/**
	 * 
	 */
	public SpatialInputKernel(int width, int height) {
		
	}
	
	public synchronized void setWidth(int width) {
		changeSize(width, height);
	}
	public synchronized void setHeight(int height) {
		changeSize(width, height);
	}
	public synchronized void changeSize(int width, int height) {
		if (width != this.width || height != this.height && width >= 0 && height >= 0) {
			this.width = width;
			this.height = height;
			convolutionValues = new float[width][height];
		}
	}
	public synchronized void setOffsetX(int offsetX) {
		this.offsetX = offsetX;
	}
	
	public synchronized void setOffsetY(int offsetY) {
		this.offsetY = offsetY;
	}
	
	

}
