/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.gesture.application;

import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.Timer;

/**
 *
 * @author Jun Haeng Lee
 */
public class GestureGUIComponent extends JComponent  {
    protected Point2D.Float pos = new Point2D.Float();
    protected Rectangle rect = new Rectangle();

    protected POSITION_OPTION posOpt = POSITION_OPTION.TOPLEFT;

    protected boolean onDispaly = false;
    protected Integer renderingLayer = JLayeredPane.DEFAULT_LAYER;

    protected Timer timer = null;

    public enum POSITION_OPTION {CENTER, TOPLEFT, BOTTOMLEFT, TOPRIGHT, BOTTOMRIGHT}

    public GestureGUIComponent(){
    }

    public void setPositionOption(POSITION_OPTION posOpt){
        this.posOpt = posOpt;
    }

    public POSITION_OPTION getPositionOption(){
        return posOpt;
    }

    public Point2D.Float getPosition(){
        return pos;
    }

    public int getPositionX(){
        return (int) pos.x;
    }

    public int getPositionY(){
        return (int) pos.y;
    }

    public Rectangle getRect(){
        return rect;
    }

    public int getLeft(){
        return rect.x;
    }

    public int getRight(){
        return (int) rect.getMaxX();
    }

    public int getTop(){
        return rect.y;
    }

    public int getBottom(){
        return (int) rect.getMaxY();
    }

    public int getImgWidth(){
        return rect.width;
    }

    public int getImgHeight(){
        return rect.height;
    }

    public javax.swing.Timer getTimer(){
        return timer;
    }

    public Integer getRenderingLayer() {
        return renderingLayer;
    }

    public boolean isOnDispaly() {
        return onDispaly;
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);
    }

    public void setPosition(int x, int y, POSITION_OPTION posOpt)
    {
        setPositionOption(posOpt);
        setPosition(new Point2D.Float(x, y));
    }

    public void setPosition(Point2D.Float pos, POSITION_OPTION posOpt){
        setPositionOption(posOpt);
        setPosition(pos);
    }

    public void setPosition(int x, int y)
    {
        setPosition(new Point2D.Float(x, y));
    }

    public void setPosition(Point2D.Float pos)
    {
        this.pos.setLocation(pos);

        switch(posOpt){
            case CENTER:
                rect.x = (int) pos.x - rect.width/2;
                rect.y = (int) pos.y - rect.height/2;
                break;
            case BOTTOMRIGHT:
                rect.x = (int) pos.x - rect.width;
                rect.y = (int) pos.y - rect.height;
                break;
            case TOPRIGHT:
                rect.x = (int) pos.x - rect.width;
                rect.y = (int) pos.y;
                break;
            case BOTTOMLEFT:
                rect.x = (int) pos.x;
                rect.y = (int) pos.y - rect.height;
                break;
            case TOPLEFT:
            default:
                rect.x = (int) pos.x;
                rect.y = (int) pos.y;
                break;
        }

        repaint();
    }

    public void setRenderingLayer(Integer renderingLayer) {
        this.renderingLayer = renderingLayer;
    }

    public void setOnDispaly(boolean onDispaly) {
        this.onDispaly = onDispaly;
    }

    public void hookTimer(javax.swing.Timer timer){
        this.timer = timer;
    }
}
