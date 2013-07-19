/*
 * UioFoveatedImagerDisplayMethod.java
 *
 * Created on 12. mai 2006, 13:00
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package no.uio.ifi.jaer.chip.foveated;


import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import net.sf.jaer.graphics.AEChipRenderer;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.ChipRendererDisplayMethod;
import net.sf.jaer.graphics.DisplayMethod2D;

/**
 * This DisplayMethod draws a foveated image with higher density of pixels in
 * the center than in the periphery. Periphery pixels are 'adaptive' (i.e.
 * motion-sensitive. The DisplayMethod is particular to the UioFoveatedImager
 * and it contains some globals so it needs rewriting to be used by other foveated
 * cameras.
 *
 * Future plans (TODO's):
 * Optimize the main for-loops so no isInFovea() test is done.
 *
 * @author hansbe@ifi.uio.no
 */
public class UioFoveatedImagerDisplayMethod extends ChipRendererDisplayMethod implements DisplayMethod2D {

	private int startX = 8;
	private int endX = 83 - 8;
	private int startY = 8;
	private int endY = 87 - 9;

	/** Creates a new instance of UioFoveatedImagerDisplayMethod */
	public UioFoveatedImagerDisplayMethod(ChipCanvas chipCanvas) {
		super(chipCanvas);
		// marks out a fovea rectangle
		// surrounding pixels are 4x the size
		//        startX=8; endX = 83-8;
		//        startY=8; endY = 87-9;

	}

	@Override
	public void display(GLAutoDrawable drawable) {
		GL2 gl = setupGL(drawable);
		AEChipRenderer renderer = (AEChipRenderer) (getChipCanvas().getRenderer());
		// renderer.grayValue = 0f; //grayquest
		float[] fr = renderer.getPixmapArray();
		if (fr == null) {
			return;
		}


		float gray = renderer.getGrayValue();
		// gl.glClearColor(gray,gray,gray,0f);
		gl.glClearColor(0f, 0f, 0f, 0.0f);
		gl.glClear(GL.GL_COLOR_BUFFER_BIT);

		try {
			//        for(int i=0;i<fr.length;i++){
			//            for(int j=0;j<fr[i].length;j++){

			// now iterate over the frame (fr)
			for (int x = getChipCanvas().getZoom().getStartPoint().x; x < getChipCanvas().getZoom().getEndPoint().x; x++) {
				for (int y = getChipCanvas().getZoom().getStartPoint().y; y < getChipCanvas().getZoom().getEndPoint().y; y++) {
					int ind = getRenderer().getPixMapIndex(x, y);
					if ((fr[ind] == gray) && (fr[ind + 1] == gray) && (fr[ind + 2] == gray)) {
						continue;
					}

					if (!isInFovea(x, y)) {
						float sx, sy, ex, ey;
						// 42 pixels surround the fovea on top and bottom
						// 44 pixels surround the fovea on left and right
						// 83 total x pixels
						// 87 total y pixels
						//                        if(f[0]==gray && f[1]==gray && f[2]==gray)
						//                            {gl.glColor3f(0f,0f,0f);}
						//                        else
						sx = (x / 83f * 41f * 2f) - 0.5f;
						ex = ((x + 2) / 83f * 41f * 2f) - 0.5f;
						sy = (y / 87f * 43f * 2f) - 0.5f;
						ey = ((y + 2) / 87f * 43f * 2f) - 0.5f;
						//if(incy==0) incy=incx;
						//if(incx==0) incx=incy;
						gl.glColor3f(fr[ind], fr[ind + 1], fr[ind + 2]);
						gl.glRectf(sx, sy, ex, ey);
						fr[ind] = gray;
						fr[ind + 1] = gray;
						fr[ind + 2] = gray;
					} else {
						//                    int x = i,  y = j; // dont flip y direction because retina coordinates are from botton to top (in user space, after lens) are the same as default OpenGL coordinates
						int fixY = y;
						if (y == startY) {
							fixY++;
						}
						//gl.glColor3f(0.9f-1.6f*f[0],0.9f-1.6f*f[1],0.9f-1.6f*f[2]);
						gl.glColor3f(fr[ind], fr[ind], fr[ind]);
						gl.glRectf(x - .5f, fixY - 1f, x + .5f, fixY);
					}
				}
			}



		} catch (ArrayIndexOutOfBoundsException e) {
			log.warning("while drawing frame buffer");
			e.printStackTrace();
			getChipCanvas().unzoom(); // in case it was some other chip had set the zoom
			gl.glPopMatrix();
		}

		// outline frame
		gl.glColor4f(0f, 1f, 0f, 0f);
		gl.glLineWidth(2f);
		gl.glBegin(GL.GL_LINE_LOOP);
		final float o = .5f;
		final float w = renderer.getChip().getSizeX() - 1;
		final float h = renderer.getChip().getSizeY() - 1;
		gl.glVertex2f(-o, -o);
		gl.glVertex2f(w + o, -o);
		gl.glVertex2f(w + o, h + o);
		gl.glVertex2f(-o, h + o);
		gl.glEnd();


		// following are apparently not needed, this happens anyhow before buffer swap
		//        gl.glFlush();
		//        gl.glFinish();

	}

	private boolean isInFovea(int aex, int aey) {
		if (aex > endX) {
			return false;
		}
		if (aex < startX) {
			return false;
		}
		if (aey > endY) {
			return false;
		}
		if (aey < startY) {
			return false;
		}
		return true;
	}
}
