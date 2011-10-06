/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.application;

import java.awt.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Jun Haeng Lee
 */
public class ImageComponent extends GestureGUIComponent{
    protected String imgPath = "";
    protected Image img = null;

    public ImageComponent(String imgPath, int width, int height){
        Toolkit myToolkit = Toolkit.getDefaultToolkit();
        img = myToolkit.getImage(imgPath);
        this.imgPath = imgPath;

        MediaTracker mTracker = new MediaTracker(this);
        mTracker.addImage(img,1);
        try {
            mTracker.waitForID(1);
        } catch (InterruptedException ex) {
            Logger.getLogger(ImageComponent.class.getName()).log(Level.SEVERE, null, ex);
        }

        if(width <= 0)
            rect.width =  img.getWidth(null);
        else
            rect.width = width;

        if(height <= 0)
            rect.height = img.getHeight(null);
        else
            rect.height = height;
    }

    public ImageComponent(String imgPath) {
        Toolkit myToolkit = Toolkit.getDefaultToolkit();
        img = myToolkit.getImage(imgPath);
        this.imgPath = imgPath;

        MediaTracker mTracker = new MediaTracker(this);
        mTracker.addImage(img,1);
        try {
            mTracker.waitForID(1);
        } catch (InterruptedException ex) {
            Logger.getLogger(ImageComponent.class.getName()).log(Level.SEVERE, null, ex);
        }

        rect.width = img.getWidth(null);
        rect.height = img.getHeight(null);
    }

    public ImageComponent(Image img) {
        this.img = img;
        rect.width = img.getWidth(null);
        rect.height = img.getHeight(null);
    }

    public String getImagePath(){
        return imgPath;
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);
        g.drawImage(img, rect.x, rect.y, rect.width, rect.height, null);
    }

    public void replaceImage(String imgPath) {
        Toolkit myToolkit = Toolkit.getDefaultToolkit();
        img = myToolkit.getImage(imgPath);
        this.imgPath = imgPath;

        MediaTracker mTracker = new MediaTracker(this);
        mTracker.addImage(img,1);
        try {
            mTracker.waitForID(1);
        } catch (InterruptedException ex) {
            Logger.getLogger(ImageComponent.class.getName()).log(Level.SEVERE, null, ex);
        }

        rect.width = img.getWidth(null);
        rect.height = img.getHeight(null);

        setPosition(pos);
    }

    public void replaceImage(Image img) {
        this.img = img;
        rect.width = img.getWidth(null);
        rect.height = img.getHeight(null);

        setPosition(pos);
    }

    public void replaceImage(String imgPath, int width, int height) {
        Toolkit myToolkit = Toolkit.getDefaultToolkit();
        img = myToolkit.getImage(imgPath);
        this.imgPath = imgPath;

        MediaTracker mTracker = new MediaTracker(this);
        mTracker.addImage(img,1);
        try {
            mTracker.waitForID(1);
        } catch (InterruptedException ex) {
            Logger.getLogger(ImageComponent.class.getName()).log(Level.SEVERE, null, ex);
        }

        if(width <= 0)
            rect.width =  img.getWidth(null);
        else
            rect.width = width;

        if(height <= 0)
            rect.height = img.getHeight(null);
        else
            rect.height = height;

        setPosition(pos);
    }

    public void resizeImg(Dimension dim){
        rect.width = dim.width;
        rect.height = dim.height;

        setPosition(pos);
    }

    public void resizeImg(int width, int height){
        resizeImg(new Dimension(width, height));
    }
}