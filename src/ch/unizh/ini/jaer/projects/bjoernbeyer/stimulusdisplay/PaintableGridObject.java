
package ch.unizh.ini.jaer.projects.bjoernbeyer.stimulusdisplay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RectangularShape;
import javax.swing.JPanel;
import java.io.Serializable;

/** Allows painting of any Shape in a Grid of arbitrary rows and columns.
 *
 * @author Bjoern
 */
public class PaintableGridObject extends PaintableObject implements Serializable{
    private static final long serialVersionUID = 43L;
    private int numberGridColumns = 0, numberGridRows = 0;
    private float[] columnFractions, rowFractions;
    private float gridObjectWidth = 0f, gridObjectHeight = 0f;

    public PaintableGridObject(String objectName, RectangularShape objectShape, JPanel canvas, int numberRows, int numberColumns) {
        super(objectName, objectShape, canvas);
        setNumberGridRows(numberRows);
        setNumberGridColumns(numberColumns);
    }
    
    public PaintableGridObject(String objectName, RectangularShape objectShape, JPanel canvas, float width, float height, int numberRows, int numberColumns) {
        super(objectName, objectShape, canvas, width, height);
        setNumberGridRows(numberRows);
        setNumberGridColumns(numberColumns);
    }
    
    @Override public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        if(!getFlash()) return;
        
        g2.setStroke(new BasicStroke(getStroke()));
        
        g2.rotate(Math.toRadians(getAngle()), getX()+getWidth()/2, getY()+getHeight()/2);
        for(int i=0;i<numberGridColumns;i++) {
            for(int j=0;j<numberGridRows;j++) {
                getObjectShape().setFrame(getX() + getWidth() *columnFractions[i], 
                                          getY() + getHeight()*rowFractions[j], 
                                          getWidth() *getGridObjectWidth(), 
                                          getHeight()*getGridObjectHeight());
                
                if(getStroke() == 0){
                    g2.setPaint(Color.white); //if the stroke is 0 we dont want to see a hairline but nothing.
                }else{
                    g2.setPaint(Color.black);
                }
                
                g2.draw(getObjectShape());
                g2.setPaint(super.getUpdatedPaint());
                g2.fill(getObjectShape());
            }
        }
        g2.dispose();
        
        firePropertyChangeIfUpdated();
    }
    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --NumberGridColumns--">
    public int getNumberGridColumns() {
        return numberGridColumns;
    }

    public final void setNumberGridColumns(int numberStripes) {
        int setValue = numberStripes;
        if(setValue<1)setValue=1;
        
        this.numberGridColumns = setValue;
        this.gridObjectWidth = (1/(float)(setValue*2-1));
        
        columnFractions = new float[setValue];
        for(int i=0;i<setValue;i++) {
            columnFractions[i]   = (2*i)*gridObjectWidth; 
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter/setter for --NumberGridRows--">
    public int getNumberGridRows() {
        return numberGridRows;
    }

    public final void setNumberGridRows(int numberGridRows) {
        int setValue = numberGridRows;
        if(setValue<1)setValue=1;
        
        this.numberGridRows = setValue;
        this.gridObjectHeight = (1/(float)(setValue*2-1));
        
        rowFractions = new float[setValue];
        for(int i=0;i<setValue;i++) {
            rowFractions[i]   = (2*i)*gridObjectHeight; 
        } 
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getter for --GridObjectWidth/Height--">
    public float getGridObjectWidth() {
        return gridObjectWidth;
    }

    public float getGridObjectHeight() {
        return gridObjectHeight;
    }
    // </editor-fold>
    
}
