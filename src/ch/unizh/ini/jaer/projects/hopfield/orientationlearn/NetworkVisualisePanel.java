package ch.unizh.ini.jaer.projects.hopfield.orientationlearn;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import javax.swing.*;

public class NetworkVisualisePanel extends JPanel {
    /**
	 * 
	 */
	private static final long serialVersionUID = -3293270283695616559L;
	BufferedImage image;

    public NetworkVisualisePanel(BufferedImage image) {
        this.image = image;
    }
    public void setImage(BufferedImage image){
    	this.image = image;
    }

    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // Draw image centered.
        int x = (getWidth() - image.getWidth())/2;
        int y = (getHeight() - image.getHeight())/2;
        g.drawImage(image, x, y, this);
    }

    
}