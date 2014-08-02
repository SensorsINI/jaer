/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.bjoernbeyer.stimulusdisplay;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import javax.swing.JPanel;

/**
 *
 * @author Bjoern
 */
public class StimulusFrame extends JPanel implements ComponentListener, MouseListener, MouseMotionListener{
    private final ArrayList<PaintableObject> objectList = new ArrayList<>();
    private MouseTrajectory mousePath;
    private boolean recordMousePathEnabled;
            
    StimulusFrame(){
        super();
        setBackground(Color.white);  
        mousePath = new MouseTrajectory();
        addComponentListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
    }

    @Override public void paintComponent(Graphics g) {
        super.paintComponent(g);     
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        if(!(objectList == null) && !objectList.isEmpty()) {
            for(PaintableObject p : objectList){
                if(p.isRequestPathPaintEnabled() && p.isHasPath()){
                    g2.setStroke(new BasicStroke(2f));
                    p.getObjectPath().paintPath(g, Color.blue, 1000, getWidth()/2, getHeight()/2);
                }
                p.paint(g);
            }
        }
        
        if(!(mousePath==null) && !mousePath.isEmpty()) {
            g2.setStroke(new BasicStroke(2f));
            mousePath.paintPath(g, Color.red, 1000,getWidth()/2,getHeight()/2);
        }
    }
    
    public int addObject(PaintableObject obj) {
        objectList.add(obj);
        
        mousePath.clear(); //clear the mousePath after object is added as it now belongs to object
        repaint(); //do delete path
        return objectList.indexOf(obj);
    }
    
    public void removeObject(int listIndex) {
        if(listIndex > objectList.size()){
            throw new IllegalArgumentException("The ObjectIndex is larger than the number of Objects!");
        }
        objectList.remove(listIndex);
        repaint();
    }
    
    public PaintableObject getObject(int listIndex) {
        if(listIndex > objectList.size()){
            throw new IllegalArgumentException("The ObjectIndex is larger than the number of Objects!");
        }
        return objectList.get(listIndex);
    }
    
    public String getObjectNameAtIndex(int index) {
        PaintableObject p = objectList.get(index);
        return String.valueOf(index)+":"+p.getObjectName();
    }
    
    /** returns the first paintable object with objectName 'name'
     *
     * @param name
     * @return
     */
    public PaintableObject getObject(String name) {
        for(PaintableObject p : objectList){
            if(p.getObjectName().equals(name)){
                return p;
            }
        }
        return null;
    }
    
    public ArrayList<PaintableObject> getObjectList() {
        return objectList;
    }
    
    public int getObjectListSize() {
        return objectList.size();
    }

    @Override public void mouseEntered(MouseEvent e) {
        setCursor(new java.awt.Cursor(java.awt.Cursor.CROSSHAIR_CURSOR));
        repaint();
    }

    @Override public void mouseExited(MouseEvent e) {
        setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
    }

    @Override public void mouseDragged(MouseEvent e) {
        if(isRecordMousePathEnabled()){
            if(mousePath.isEmpty()) mousePath.start(); 
            float x = (((float)e.getX()*2)/getWidth())-1, // normalize to half of width and center
                  y = (((float)e.getY()*2)/getHeight())-1;
            mousePath.add(x,y);
            repaint();
        }
    }

    public MouseTrajectory getMousePath() {
        return mousePath;
    }

    public void setMousePath(MouseTrajectory mousePath) {
        this.mousePath = mousePath;
        repaint();
    }

    public boolean isRecordMousePathEnabled() {
        return recordMousePathEnabled;
    }

    public void setRecordMousePathEnabled(boolean recordMousePath) {
        //if true we need to reset, as a new path should be recorded.
        // otherwise the old path will just be added to. 
        if(recordMousePath) mousePath.clear();

        this.recordMousePathEnabled = recordMousePath;
    }

    @Override public void componentResized(ComponentEvent e) {
        if(objectList == null || objectList.isEmpty()) return;
        
        for(PaintableObject p : objectList){
            p.setHalfScreenDimensions(getWidth()/2,getHeight()/2);
        }
    }

    //Need to implement those for ComponentListener. Not Using them though. Only Resize needed.
    @Override public void componentMoved(ComponentEvent e) {}
    @Override public void componentShown(ComponentEvent e) {}
    @Override public void componentHidden(ComponentEvent e) { }
    
    //Need to implement those for MouseListener and MouseMotionListener. Only MouseDragged, MouseEntered and MouseExited needed.
    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mousePressed(MouseEvent e) { }
    @Override public void mouseReleased(MouseEvent e) { }
    @Override public void mouseMoved(MouseEvent e) { }
}

    