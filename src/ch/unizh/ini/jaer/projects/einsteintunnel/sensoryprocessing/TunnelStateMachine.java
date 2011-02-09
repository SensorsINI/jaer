/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.einsteintunnel.sensoryprocessing;

import java.io.*;
import java.util.*;
import net.sf.jaer.eventprocessing.FilterFrame;

/**
 *
 * @author braendch
 */
public class TunnelStateMachine {

	private BlurringTunnelTracker tunnelTracker;
	private FilterFrame filterFrame;
	private stateTypes state;

	public enum stateTypes{
		DEFAULT, SWOOSH
	}

	public TunnelStateMachine(BlurringTunnelTracker tunnelTracker){
		this.tunnelTracker = tunnelTracker;
		filterFrame = new FilterFrame(tunnelTracker.getChip());
		state = stateTypes.DEFAULT;
	}

	public setSwooshState getSetSwooshState(){
		return new setSwooshState();
	}

	public class setSwooshState extends TimerTask{
		public setSwooshState(){}

		@Override
		public void run(){
			tunnelTracker.setMinClusterAge(1000000);
			state = stateTypes.SWOOSH;
			//File f = new File("src/ch/unizh/ini/jaer/projects/einsteintunnel/sensoryprocessing/EinsteinTunnel_swoosh.xml");
            //filterFrame.loadFile(f);
		}

	}
	
	public setDefaultState getSetDefaultState(){
		return new setDefaultState();
	}

	public class setDefaultState extends TimerTask{
		public setDefaultState(){}

		@Override
		public void run(){
			tunnelTracker.setMinClusterAge(1000);
			state = stateTypes.DEFAULT;
		}

	}

}
