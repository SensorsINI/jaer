/*
 * CochleaEventRate.java
 *
 * Created on July 14, 2007, 4:43 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.chip.cochlea;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.awt.Graphics2D;


import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.EngineeringFormat;

import com.jogamp.opengl.util.gl2.GLUT;
import net.sf.jaer.event.BasicEvent;

/**
 * Computes cross corr between binaural cochleas.
 * This is a JAVA version of jtapson's MATLAB code iatdout.m
 */
public class CochleaEventRate extends EventFilter2D implements FrameAnnotater
{
	/**
	 * Creates a new instance of CochleaEventRate
	 */
	public CochleaEventRate(AEChip chip)
	{
		super(chip);
	}

	private int numEvents = 0;
	private int coch0Events = 0;
	private int coch1Events = 0;
	private int deltaT = -1;
	private float rate = -1;
	private float coch0Rate = -1;
	private float coch1Rate = -1;

	@Override
	public EventPacket<? extends BasicEvent> filterPacket(EventPacket<? extends BasicEvent> in)
	{
		numEvents = 0;
		coch0Events = 0;
		coch1Events = 0;
		deltaT = 0;
		rate = 0;
		coch0Rate = 0;
		coch1Rate = 0;

		if(!isFilterEnabled() || (in.getSize() == 0)) {
			return in;
		}

		for(Object o:in)
		{
			TypedEvent e = (TypedEvent) o;
			if(e.type == 0) {
				coch0Events++;
			}
			else {
				coch1Events++;
			}
		}
		deltaT = in.getLastTimestamp() - in.getFirstTimestamp();

		rate = (1e6f*numEvents)/deltaT;
		coch0Rate = (1e6f*coch0Events)/deltaT;
		coch1Rate = (1e6f*coch1Events)/deltaT;

		return in;
	}

	public Object getFilterState()
	{
		return null;
	}

	@Override
	public void resetFilter()
	{
	}

	@Override
	public void initFilter()
	{
	}

	public void annotate(float[][][] frame)
	{
	}

	public void annotate(Graphics2D g)
	{
	}

	EngineeringFormat fmt=new EngineeringFormat();

	@Override
	public void annotate(GLAutoDrawable drawable)
	{
		if(!isFilterEnabled()) {
			return;
		}
		GL2 gl=drawable.getGL().getGL2();
		gl.glPushMatrix();
		final GLUT glut=new GLUT();
		gl.glColor3f(1,1,1);
		gl.glRasterPos3f(0,0,0);
		glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("Total = %s", fmt.format(rate)));
		glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("; Coch0 = %s", fmt.format(coch0Rate)));
		glut.glutBitmapString(GLUT.BITMAP_HELVETICA_18, String.format("; Coch1 = %s", fmt.format(coch1Rate)));
		gl.glPopMatrix();
	}

	public int getNumEvents()
	{
		return numEvents;
	}

	public int getCoch0Events()
	{
		return coch0Events;
	}

	public int getCoch1Events()
	{
		return coch1Events;
	}

	public int getDeltaT()
	{
		return deltaT;
	}

	public float getRate()
	{
		return rate;
	}

	public float getCoch0Rate()
	{
		return coch0Rate;
	}

	public float getCoch1Rate()
	{
		return coch1Rate;
	}
}
