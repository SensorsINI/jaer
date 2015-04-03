/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.graphics;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;

import com.jogamp.opengl.GLCapabilities;
import javax.swing.JPanel;

/**
 * Extension of Tobi's ImageDisplay class which implements DisplayWriter,
 * meaning that it can be easily added to the GlobalViwer UI.
 * 
 * Easiest way to instantiate and use:
 * ImageDisplayWriter im=new ImageDisplayWriter.createOpenGLCanvas();
 * 
 * 
 * @author Peter
 */
public class ImageDisplayWriter extends ImageDisplay implements DisplayWriter {

	boolean enabled;
	JPanel hostPanel;

	public ImageDisplayWriter(GLCapabilities caps)
	{
		super(caps);
	}

	public static ImageDisplayWriter createOpenGLCanvas() {
		// design capabilities of opengl canvas
		GLCapabilities caps = new GLCapabilities(null);
		caps.setDoubleBuffered(true);
		caps.setHardwareAccelerated(true);
		caps.setAlphaBits(8);
		caps.setRedBits(8);
		caps.setGreenBits(8);
		caps.setBlueBits(8);

		ImageDisplayWriter trackDisplay = new ImageDisplayWriter(caps);
		return trackDisplay;
	}


	@Override
	public void setPanel(JPanel imagePanel) {
		hostPanel=imagePanel;

		imagePanel.setPreferredSize(new Dimension(300,300));
		imagePanel.setLayout(new GridLayout());
		imagePanel.revalidate();
		imagePanel.add(this);
	}

	@Override
	public Component getPanel() {
		return hostPanel;
	}

	@Override
	public void setDisplayEnabled(boolean state) {
		enabled=false;
	}

	public boolean isDisplayEnabled()
	{   return enabled;
	}





}
