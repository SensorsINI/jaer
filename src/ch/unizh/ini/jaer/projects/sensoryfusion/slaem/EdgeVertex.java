/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

import net.sf.jaer.event.*;

/**
 *
 * @author Johnfn
 */
public class EdgeVertex {
	
	short posX, posY;
	float diameter;
	
	public EdgeVertex(TypedEvent e, float diameter){
		this.posX = e.x;
		this.posY = e.y;
		this.diameter = diameter;
	}
	
	public boolean checkEvent(TypedEvent e){
		if(Math.abs(posX-e.x)+Math.abs(posY-e.y)<diameter){
			posX = e.x;
			posY = e.y;
			return true;
		} else {
			return false;
		}
	}
	
}
