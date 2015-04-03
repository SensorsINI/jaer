/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ine.telluride.jaer.tell2009.beecounting;

import java.util.ArrayList;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.tracking.RectangularClusterTracker;

import com.jogamp.opengl.util.gl2.GLUT;
/**
 * Subclases RectangularClusterTracker to count objects that cross marked crossing lines.
 *
 * @author tobi, Robyn Verrinder, Brian Smith, Telluride 2009
 *
 * This is part of jAER
<a href="http://jaerproject.net/">jaerproject.net</a>,
licensed under the LGPL (<a href="http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License">http://en.wikipedia.org/wiki/GNU_Lesser_General_Public_License</a>.
 */
public class BeeCounter extends RectangularClusterTracker{
	private float topLine = getPrefs().getFloat("BeeCounter.topLine",0.75f);
	private float botLine = getPrefs().getFloat("BeeCounter.botLine",0.25f);
	private int nOut = 0,  nIn = 0;

	public BeeCounter (AEChip chip){
		super(chip);
		setPropertyTooltip("BeeCounting","topLine","exit line as fraction of screen");
		setPropertyTooltip("BeeCounting","botLine","entrance line as fraction of screen");
	}
	private ArrayList<RectangularClusterTracker.Cluster> inBotZone = new ArrayList();
	private ArrayList<RectangularClusterTracker.Cluster> inTopZone = new ArrayList();
	private ArrayList<Cluster> purgeList = new ArrayList();

	/** Overrides RectangularClusterTracker.filterPacket to add functionality of marking clusters
	 * that cross one line and then the other. Depending on the order of crossing, nOut or nIn are incremented.
	 *
	 * @param in the input packet.
	 * @return the input packet, unmodified.
	 */
	@Override
	synchronized public EventPacket<?> filterPacket (EventPacket<?> in){
		super.filterPacket(in);
		final float by = botLine * chip.getSizeY(),  ty = topLine * chip.getSizeY();
		for ( Cluster c:clusters ){
			if ( !c.isVisible() ){
				continue;
			}
			// check for cluster that is in bot zone, add to list if not there already
			if ( (c.getLocation().y < by) && !inBotZone.contains(c)){
				// just crossed bot line upwards
				inBotZone.add(c);
			}

			// same for bee in top zone
			if ( (c.getLocation().y > ty) && !inTopZone.contains(c) ){
				inTopZone.add(c);
			}

			// check for cluster that crossed bot line down and is in top zone list, this bee entered
			if ( (c.getLocation().y < by) && inTopZone.contains(c)){
				// crossed crossed down
				inTopZone.remove(c);
				nIn++;
			}

			// if bee crossed top line upwards and is in bot line up list, then it exited hive
			if ( (c.getLocation().y > ty) && inBotZone.contains(c) ){
				inBotZone.remove(c);
				nOut++;
			}
		}


		//purge lists
		purgeList.clear();
		for ( Cluster cl:inBotZone ){
			if ( !clusters.contains(cl) ){
				purgeList.add(cl);
			}
		}
		inBotZone.removeAll(purgeList);

		purgeList.clear();
		for ( Cluster cl:inTopZone ){
			if ( !clusters.contains(cl) ){
				purgeList.add(cl);
			}
		}
		inTopZone.removeAll(purgeList);

		return in;
	}
	private class CrossingStats{
		Cluster cluster;

		CrossingStats (Cluster c){
			cluster = c;
		}
	}

	@Override
	synchronized public void resetFilter (){
		super.resetFilter();
		nOut = 0;
		nIn = 0;
		inBotZone.clear();
		inTopZone.clear();
	}
	private final GLUT glut = new GLUT();

	@Override
	public synchronized void annotate (GLAutoDrawable drawable){
		if ( !isFilterEnabled() ){
			return;
		}
		super.annotate(drawable);
		final int sx = chip.getSizeX(),  sy = chip.getSizeY();
		final GL2 gl = drawable.getGL().getGL2();
		gl.glLineWidth(2f);
		gl.glColor3f(0,0,1);
		gl.glRasterPos3f(0,sy * getTopLine(),0);
		glut.glutBitmapString(GLUT.BITMAP_TIMES_ROMAN_24,String.format("%d exited",nOut));
		gl.glRasterPos3f(0,sy * getBotLine(),0);
		glut.glutBitmapString(GLUT.BITMAP_TIMES_ROMAN_24,String.format("%d entered",nIn));
		gl.glBegin(GL.GL_LINES);
		gl.glVertex2f(0,sy * getTopLine());
		gl.glVertex2f(sx,sy * getTopLine());
		gl.glVertex2f(0,sy * getBotLine());
		gl.glVertex2f(sx,sy * getBotLine());
		gl.glEnd();
	}

	/**
	 * @return the topLine
	 */
	public float getTopLine (){
		return topLine;
	}

	/**
	 * @param topLine the topLine to set
	 */
	public void setTopLine (float topLine){
		if ( topLine < 0 ){
			topLine = 0;
		} else if ( topLine > 1 ){
			topLine = 1;
		}
		this.topLine = topLine;
		getPrefs().putFloat("BeeCounter.topLine",topLine);
	}

	/**
	 * @return the botLine
	 */
	public float getBotLine (){
		return botLine;
	}

	/**
	 * @param botLine the botLine to set
	 */
	public void setBotLine (float botLine){
		if ( botLine < 0 ){
			botLine = 0;
		} else if ( botLine > 1 ){
			botLine = 1;
		}
		this.botLine = botLine;
		getPrefs().putFloat("BeeCounter.botLine",botLine);
	}
}
