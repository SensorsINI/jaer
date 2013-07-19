package net.sf.jaer;

/*
 * GifViewerWindow.java
 *
 * Created on October 25, 2002, 9:02 AM

Copyright 2002 Institute of Neuroinformatics, University and ETH Zurich, Switzerland

This file is part of The Physiologist's Friend.

The Physiologist's Friend is free software; you can redistribute it
and/or modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2 of
the License, or (at your option) any later version.

The Physiologist's Friend is distributed in the hope that it will be
useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with The Physiologist's Friend; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA


 * $Id: GifViewerWindow.java,v 1.1 2002/10/25 08:14:55 tobi Exp $
 *
 * <hr>
 * <pre>
 * $Log: GifViewerWindow.java,v $
 * Revision 1.1  2002/10/25 08:14:55  tobi
 * added splash screen
 *
 * </pre>
 * @author $Author: tobi $
 */
/**
Shows a GIF image.  Can be used to show a splash screen.

From java developer connection <a href="http://developer.java.sun.com/developer/qow/archive/24/index.html">http://developer.java.sun.com/developer/qow/archive/24/index.html</a>

@author Manoj Agarwala manoj@ysoftware.com

 */
import java.applet.Applet;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.Window;

import javax.swing.ImageIcon;
import javax.swing.JLabel;

public class GifViewerWindow {

    public static GifViewerWindow showGifFile(String gifFile, int imageHeight, int imageWidth) {
        GifViewerWindow gvw = new GifViewerWindow(gifFile);
        gvw.centerAndSetVisible(imageHeight, imageWidth);

        return gvw;
    }

    public static GifViewerWindow showGifFile(Image gifImage, int imageHeight, int imageWidth) {
        GifViewerWindow gvw = new GifViewerWindow(gifImage);
        gvw.centerAndSetVisible(imageHeight, imageWidth);

        return gvw;
    }

    public static void hideGifFile(GifViewerWindow gvw) {
        //gvw.setVisible(false);
        gvw.removeNotify();
    }

    public GifViewerWindow(Image gifImage) {
        initialize(gifImage);
    }

    public GifViewerWindow(Applet applet, String gifFileName) {

        Image gifImage = applet.getImage(applet.getCodeBase(), gifFileName);

        initialize(gifImage);
    }

    public GifViewerWindow(String gifFileName) {
        Image gifImage = Toolkit.getDefaultToolkit().getImage(gifFileName);

        initialize(gifImage);
    }

    public void centerAndSetVisible(int imageHeight, int imageWidth) {
        Dimension sd = Toolkit.getDefaultToolkit().getScreenSize();
        int x = (sd.width - imageWidth) / 2;
        int y = (sd.height - imageHeight) / 2;

        setLocation(x, y);
        setSize(new Dimension(imageWidth, imageHeight));
        setVisible(true);
    }

    public void setSize(Dimension d) {
        window.setSize(d);

        if (imageViewer != null) {
            imageViewer.setSize(d);
        }
    }

    public void setLocation(int x, int y) {
        window.setLocation(x, y);
    }

    public void setVisible(boolean newValue) {
        window.setVisible(newValue);
    }

    public void toFront() {
        window.toFront();
    }

    private void initialize(Image gifImage) {
        frame = new Frame();
        frame.addNotify();

        window = new Window(frame);

        window.addNotify();
        imageViewer = new JLabel(new ImageIcon(gifImage, "Splash image"));
        window.add(imageViewer);
    }

    public Frame getParent() {
        return frame;
    }

    public void removeNotify() {
        window.removeNotify();
        frame.removeNotify();
    }

    public void finalize() {
        removeNotify();
    }
    JLabel imageViewer;
    Window window;
    Frame frame;

    public static void main(String args[]) {
        GifViewerWindow.showGifFile("splash.gif", 200, 200);
    }
}
