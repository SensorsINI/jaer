/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.sensoryfusion.slaem;

import java.util.*;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;

/**
 *
 * @author Christian Brandli
 */
@Description("Tries to extract the optical flow on a pixel basis")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class OpticFlowExtractor extends EventFilter2D implements Observer, FrameAnnotater{
    public int sizeX, sizeY, sizeX2, sizeY2;
    public int eventNr;
	public int lastTs;
	float maxD;
    int length = 10;
    public OpticFlowVector[][] vectors;
    public Vector<OpticFlowVector> activeVectors;
    //global vectors
    public int trX, trY, trZ;
    
    /**
     * Determines the maximal time to neighboring activity for becoming active (us)
     */
    private int maxDeltaTs=getPrefs().getInt("EdgeExtractor.maxDeltaTs",5000);
    {setPropertyTooltip("maxDeltaTs","Determines the maximal time to neighboring activity for contributing to the optic flow vectors (in us)");}
    
    public OpticFlowExtractor(AEChip chip){
        super(chip);
        chip.addObserver(this);
        initFilter();
    }

    @Override
    public void resetFilter() {
        eventNr = 1;
        sizeX = chip.getSizeX();
        sizeX2 = sizeX/2;
        sizeY = chip.getSizeY();
        sizeY2 = sizeY/2;
        vectors = new OpticFlowVector[sizeX][sizeY];
        activeVectors = new Vector<OpticFlowVector>();
        for(int x=0; x<sizeX; x++){
            for(int y=0; y<sizeY; y++){
                vectors[x][y] = new OpticFlowVector(x,y);
            }
        }
        resetGlobalVectors();
    }

    @Override
    public void initFilter() {
        resetFilter();
    }
    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        checkOutputPacketEventType(in);
        cleanVectors();
        resetGlobalVectors();
        for (Object o : in) {
            TypedEvent e = (TypedEvent) o;
            updateEvent(e);
            eventNr++;
        }
        return in;
    }
    
    public void updateEvent (TypedEvent e){
        int pX = e.x;
        int pY = e.y;
        vectors[e.x][e.y].lastTs = e.timestamp;
        lastTs = e.timestamp;
		for(int dx=-1; dx<=1; dx++){
            for(int dy=-1; dy<=1; dy++){
                int x = pX+dx;
                int y = pY+dy;
                if(x>=0 && y >=0 && x<sizeX && y<sizeY && !(dx == 0 && dy == 0)){
                    int tsDiff = e.timestamp-vectors[x][y].lastTs;
                    if(tsDiff<maxDeltaTs){
                        if(tsDiff == 0)tsDiff = 1;
                        vectors[x][y].xComp+=dx/tsDiff;
                        vectors[x][y].yComp+=dy/tsDiff;
                        updateGlobalVectors(x,y,dx/tsDiff,dy/tsDiff);
                        if(!activeVectors.contains(vectors[x][y])){
                            activeVectors.add(vectors[x][y]);
                        }
                    }
                }
            }
        }
    }
    
    public void updateGlobalVectors(int x, int y, double dX, double dY){
        trX += dX;
        trY += dY;
        trZ += (Math.signum(x-sizeX2)*dX+Math.signum(y-sizeY2)*dY);
    }
    
    public void resetGlobalVectors(){
        trX = 0; 
        trY = 0; 
        trZ = 0;
    }
    
    public void cleanVectors(){
		int acceptableTs = lastTs-maxDeltaTs;
        for(int x=0; x<sizeX; x++){
            for(int y=0; y<sizeY; y++){
                if(vectors[x][y].lastUpdate<acceptableTs){
					activeVectors.remove(vectors[x][y]);
					vectors[x][y].reset();
				}
            }
        }
    }
    
    public class OpticFlowVector{
        int lastTs, lastUpdate;
        int xPos, yPos;
        float xComp, yComp;
        
        public OpticFlowVector(int x, int y){
            xPos = x;
            yPos = y;
            xComp = 0;
            yComp = 0;
            lastTs = 0;
            lastUpdate= 0;
        }
        
        public void reset(){
            xComp = 0;
            yComp = 0;
        }
        
        public void draw(GL gl){
            gl.glBegin(GL.GL_LINES);
            //UnitVector d = getUnitVector();
			NormalizedVector d = getNormalizedVector();
            gl.glVertex2f(xPos,yPos);
            gl.glVertex2f(xPos + d.x * length,yPos + d.y * length);
            gl.glEnd();
        }
        
        public UnitVector getUnitVector(){
            return new UnitVector(xComp, yComp);
        }
        
        public final class UnitVector{
            public float x, y;
            UnitVector(float x, float y){
                float l=(float)Math.sqrt(x*x+y*y);
                x=x/l;
                y=y/l;
                this.x=x;
                this.y=y;
            }
        }
		
		public NormalizedVector getNormalizedVector(){
			return new NormalizedVector(xComp, yComp);
		}
		
		public final class NormalizedVector{
			public float x, y;
			NormalizedVector(float x, float y){
				if(x > maxD){
					maxD = x;
				}
				if(y > maxD){
					maxD = y;
				}
				this.x = x/maxD;
				this.y = y/maxD;
			}
		}
        
    }
    
    public void drawGlobalVector(GL gl){
        double globalLength = Math.sqrt(trX*trX+trY*trY);
        gl.glTranslatef((float)sizeX2 , (float)sizeY2, 0);
        gl.glLineWidth(6f);
        gl.glBegin(GL.GL_LINES);
        gl.glColor3f(1,1,1);
        gl.glVertex2f(0,0);
        gl.glVertex2d(trX/globalLength*length*3,trY/globalLength*length*3);
        gl.glEnd();
    }
    
    @Override
    public void update(Observable o, Object arg) {
        
    }

    @Override
    public void annotate(GLAutoDrawable drawable) {
        if(!isFilterEnabled()) return;
        GL gl=drawable.getGL();
        
        gl.glColor3f(0,0,1);
        
        gl.glPushMatrix();

        gl.glLineWidth(3f);
        Iterator activeVectorItr = activeVectors.iterator();
        while(activeVectorItr.hasNext()){
            OpticFlowVector vector = (OpticFlowVector)activeVectorItr.next();
            vector.draw(gl);
        }
        drawGlobalVector(gl);
        gl.glPopMatrix();
    }
    
    /**
     * @return the maxDeltaTs
     */
    public int getMaxDeltaTs() {
        return maxDeltaTs;
    }

    /**
     * @param maxDeltaTs the maxDeltaTs to set
     */
    public void setMaxDeltaTs(int maxDeltaTs) {
        this.maxDeltaTs = maxDeltaTs;
        prefs().putInt("OpticFlowExtractor.maxDeltaTs", maxDeltaTs);
    }
    
}
