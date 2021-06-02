/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.einsteintunnel.multicamera;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.awt.Font;
import java.net.InetSocketAddress;


import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.ChipRendererDisplayMethod;

import com.jogamp.opengl.util.awt.TextRenderer;

/**
 * Displays camera info on top of rendered output.
 * @author tobi
 */
class MultiUDPNetworkDVS128CameraDisplayMethod extends ChipRendererDisplayMethod {
	private boolean displayInfo=true;

	TextRenderer renderer=null;
	public MultiUDPNetworkDVS128CameraDisplayMethod(ChipCanvas parent) {
		super(parent);
	}

	@Override
	public void display(GLAutoDrawable drawable) {
		super.display(drawable);
		if(!isDisplayInfo()) {
			return;
		}
		if((getChipCanvas().getChip()!=null) && (getChipCanvas().getChip() instanceof MultiUDPNetworkDVS128Camera)){
			if(renderer==null) {
				renderer=new TextRenderer(new Font(Font.SANS_SERIF,Font.PLAIN, 10));
			}
			GL2 gl=drawable.getGL().getGL2();
			MultiUDPNetworkDVS128Camera cam=(MultiUDPNetworkDVS128Camera)getChipCanvas().getChip();
			CameraMap map=cam.getCameraMap();
			for(InetSocketAddress a:map.keySet()){
				int pos=map.get(a);
				renderer.begin3DRendering();
				renderer.draw(pos+":"+a.getHostName()+":"+a.getPort(), pos*MultiUDPNetworkDVS128Camera.CAM_WIDTH, -10);
				renderer.end3DRendering();
			}
		}

	}

	/**
	 * @return the displayInfo
	 */
	public boolean isDisplayInfo() {
		return displayInfo;
	}

	/**
	 * @param displayInfo the displayInfo to set
	 */
	public void setDisplayInfo(boolean displayInfo) {
		this.displayInfo = displayInfo;
	}


}
