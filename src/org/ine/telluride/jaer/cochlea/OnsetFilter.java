/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.cochlea;
import java.awt.Graphics2D;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.fixedfunc.GLMatrixFunc;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.filter.RepetitiousFilter;
import net.sf.jaer.graphics.FrameAnnotater;
import ch.unizh.ini.jaer.chip.cochlea.CochleaGramDisplayMethod;
/**
 * Filters in spikes from channels with transient increases in spike rate. Designed to filter out ongoing data and only let through
 * spikes from channels that suddenly increase their firing rate.
 *
 * @author tobi
 *
 * This is part of jAER
<a href="http://jaerproject.net/">jaerproject.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
@Description("Passes onsets, which are increases of firing rate")
public class OnsetFilter extends RepetitiousFilter implements FrameAnnotater{

	volatile private int dtCurrentPacket=1;

	public OnsetFilter (AEChip chip){
		super(chip);
	}

	@Override
	synchronized public EventPacket<?> filterPacket (EventPacket<?> in){
		in = super.filterPacket(in);
		dtCurrentPacket=in.getDurationUs();
		return in;
	}

	@Override
	public synchronized void resetFilter (){
		super.resetFilter();
	}

	@Override
	public void initFilter (){
		super.initFilter();
	}

	public void annotate (float[][][] frame){
	}

	public void annotate (Graphics2D g){
	}

	@Override
	public void annotate (GLAutoDrawable drawable){
		// if we're using CochleaGramDisplayMethod then show the average ISI's on display
		if ( chip.getCanvas().getDisplayMethod() instanceof CochleaGramDisplayMethod ){
			GL2 gl = drawable.getGL().getGL2();
			{
				if((avgDtMap==null) || (dtCurrentPacket==0)) {
					return;
				}
				// setup graphics
				gl.glPushMatrix();
				gl.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
				gl.glLoadIdentity(); // very important to load identity matrix here so this works after first resize!!!
				gl.glOrtho(-CochleaGramDisplayMethod.BORDER,drawable.getSurfaceWidth() + CochleaGramDisplayMethod.BORDER,-CochleaGramDisplayMethod.BORDER,drawable.getSurfaceHeight() + CochleaGramDisplayMethod.BORDER,10000,-10000);
				gl.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
				//                gl.glClearColor(0,0,0,0f);
				//                gl.glClear(GL2.GL_COLOR_BUFFER_BIT);
				gl.glLoadIdentity();
				// translate origin to this point
				gl.glTranslatef(0,0,0);
				// scale everything by rastergram scale
				float ys = ( drawable.getSurfaceHeight() ) / (float)chip.getSizeX();// scale vertical is draableHeight/numPixels
				float xs = ( drawable.getSurfaceWidth() ); // scale horizontal is draw
				gl.glScalef(xs,ys,1);

				//draw avg ISIs
				gl.glColor3f(1,1,1);
				gl.glLineWidth(1f);
				for ( int x = 0 ; x < chip.getSizeX() ; x++ ){
					int n = 0;
					int sum = 0;
					for ( int y = 0 ; y < chip.getSizeY() ; y++ ){
						for ( int t = 0 ; t < chip.getNumCellTypes() ; t++ ){
							sum += avgDtMap[x][y][t];
							n++;
						}
					}
					float avgisi = (float)sum / n;
					gl.glBegin(GL.GL_LINES);
					gl.glVertex2f(0,x);
					gl.glVertex2f(0,x + 1);

					// draw a tick showing the ISI using the timescale of the current packet of data and
					// scaled to the screen width

					float xx=avgisi/dtCurrentPacket;
					gl.glVertex2f(xx,x);
					gl.glVertex2f(xx,x + 1);
					gl.glEnd();
				}
				gl.glPopMatrix();
			}


		}
	}
}
