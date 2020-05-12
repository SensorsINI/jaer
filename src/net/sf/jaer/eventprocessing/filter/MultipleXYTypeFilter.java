/*
 * MultipleXYTypeFilter.java
 *
 * Created on July 17, 2007, 8:55 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package net.sf.jaer.eventprocessing.filter;
import java.util.Observable;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
/**
 *
 * @author Vaibhav Garg
 */
public class MultipleXYTypeFilter extends EventFilter2D implements FrameAnnotater{
    public boolean isGeneratingFilter(){ return false;}
    //private int startX=getPrefs().getInt("MultipleXYTypeFilter.startX",0);
    //private int endX=getPrefs().getInt("MultipleXYTypeFilter.endX",0);
    private int[] startX;
    private int[] endX;
    private int[] startY;
    private int[] endY;
    private boolean xEnabled=getPrefs().getBoolean("MultipleXYTypeFilter.xEnabled",true);
    //private int startY=getPrefs().getInt("MultipleXYTypeFilter.startY",0);
    // private int endY=getPrefs().getInt("MultipleXYTypeFilter.endY",0);
    private boolean yEnabled=getPrefs().getBoolean("MultipleXYTypeFilter.yEnabled",true);
    private int startType=getPrefs().getInt("MultipleXYTypeFilter.startType",0);
    private int endType=getPrefs().getInt("MultipleXYTypeFilter.endType",0);
    private boolean typeEnabled=getPrefs().getBoolean("MultipleXYTypeFilter.typeEnabled",false);

    public short x=0, y=0;
    public byte type=0;

    private short xAnd;
    private short yAnd;
    private byte typeAnd;
    private int maxBoxNum=getPrefs().getInt("MultipleXYTypeFilter.maxBoxNum",1);
   // maxBoxNum=2;

    /** Creates a new instance of MultipleXYTypeFilter */
    public MultipleXYTypeFilter(AEChip chip){
        super(chip);

        resetFilter();
    }
    int maxEvents=0;
    int index=0;
    private short xspike,yspike;
    private byte typespike;
    private int ts,repMeasure,i;

    /**
     * filters in to out. if filtering is enabled, the number of out may be less
     * than the number putString in
     *@param in input events can be null or empty.
     *@return the processed events, may be fewer in number. filtering may occur in place in the in packet.
     */
    @Override
	synchronized public EventPacket filterPacket(EventPacket in) {
        if(in==null) {
			return null;
		}
        if(!filterEnabled) {
			return in;
		}
        if(enclosedFilter!=null) {
			in=enclosedFilter.filterPacket(in);
		}
        int i;

        // filter

        int n=in.getSize();
        if(n==0) {
			return in;
		}
        checkOutputPacketEventType(in);
        OutputEventIterator outItr=out.outputIterator();
        //System.out.println("In mxy +"+maxBoxNum);
        // for each event only write it to the tmp buffers if it matches
        for(Object obj:in){
            for(int ctr=0;ctr<maxBoxNum;ctr++) {
                BasicEvent e=(BasicEvent)obj;

                // if we pass all tests then pass event
                if(xEnabled && ((e.x<startX[ctr]) || (e.x>endX[ctr])) ) {
					continue;
				}
                if(yEnabled && ((e.y<startY[ctr]) || (e.y>endY[ctr]))) {
					continue;
				}
                if(typeEnabled){
                    TypedEvent te=(TypedEvent)e;
                    if((te.type<startType) || (te.type>endType)) {
						continue;
					}
                    outItr.nextOutput().copyFrom(te);
                }else{
                    outItr.nextOutput().copyFrom(e);
                }
            }
        }

        return out;
    }


    public Object getFilterState() {
        return null;
    }

    @Override
	synchronized public void resetFilter() {
initFilter();


    }

    @Override
	public void initFilter() {
        startX=new int[maxBoxNum];
        endX=new int[maxBoxNum];
        startY=new int[maxBoxNum];
        endY=new int[maxBoxNum];
        for(int ctr=0;ctr<maxBoxNum;ctr++) {
            startX[ctr]=0;
            endX[ctr]=0;
            startY[ctr]=0;
            endY[ctr]=0;
        }

    }

    private int clip(int val, int limit){
        if((val>limit) && (limit != 0)) {
			return limit;
		}
		else if(val<0) {
			return 0;
		}
        return val;
    }

    public int getStartX(int ctr) {
        return startX[ctr];
    }

    public void setStartX(int startX,int ctr) {
        this.startX[ctr]=clip(startX,chip.getSizeX());
        //this.startX[ctr] = startX;
        //getPrefs().putInt("XYTypeFilter.startX",startX);
    }

    public int getEndX(int ctr) {
        return endX[ctr];
    }

    public void setEndX(int endX,int ctr) {
        this.endX[ctr]=clip(endX,chip.getSizeX());
        //this.endX = endX;
        //getPrefs().putInt("XYTypeFilter.endX",endX);
    }

    public boolean isXEnabled() {
        return xEnabled;
    }

    public void setXEnabled(boolean xEnabled) {
        this.xEnabled = xEnabled;
        getPrefs().putBoolean("XYTypeFilter.xEnabled",xEnabled);
    }

    public int getStartY(int ctr) {
        return startY[ctr];
    }

    public void setStartY(int startY,int ctr) {
        this.startY[ctr]=clip(startY,chip.getSizeY());
        //this.startY = startY;
        //getPrefs().putInt("XYTypeFilter.startY",startY);
    }

    public int getEndY(int ctr) {
        return endY[ctr];
    }

    public void setEndY(int endY, int ctr) {
        this.endY[ctr]=clip(endY,chip.getSizeY());
        //this.endY = endY;
        //getPrefs().putInt("XYTypeFilter.endY",endY);
    }

    public boolean isYEnabled() {
        return yEnabled;
    }

    public void setYEnabled(boolean yEnabled) {
        this.yEnabled = yEnabled;
        getPrefs().putBoolean("XYTypeFilter.yEnabled",yEnabled);
    }

    public int getStartType() {
        return startType;
    }

    public void setStartType(int startType) {
        startType=clip(startType,chip.getNumCellTypes());
        this.startType = startType;
        getPrefs().putInt("XYTypeFilter.startType",startType);
    }

    public int getEndType() {
        return endType;
    }

    public void setEndType(int endType) {
        endType=clip(endType,chip.getNumCellTypes());
        this.endType = endType;
        getPrefs().putInt("XYTypeFilter.endType",endType);
    }

    public boolean isTypeEnabled() {
        return typeEnabled;
    }


    public void setTypeEnabled(boolean typeEnabled) {
        this.typeEnabled = typeEnabled;
        getPrefs().putBoolean("XYTypeFilter.typeEnabled",typeEnabled);
    }

    @Override
	public void annotate(GLAutoDrawable drawable) {
        if(!isAnnotationEnabled() || !isFilterEnabled()) {
			return;
		}
        if(!isFilterEnabled()) {
			return;
		}
        GL2 gl=drawable.getGL().getGL2();

            gl.glPushMatrix();
            {
                for(int ctr=0;ctr<maxBoxNum;ctr++) {
                gl.glColor3f(0,0,ctr);
                gl.glLineWidth(2f);
                gl.glBegin(GL.GL_LINE_LOOP);
                gl.glVertex2i(startX[ctr],startY[ctr]);
                gl.glVertex2i(endX[ctr],startY[ctr]);
                gl.glVertex2i(endX[ctr],endY[ctr]);
                gl.glVertex2i(startX[ctr],endY[ctr]);
                gl.glEnd();
            }
            gl.glPopMatrix();
        }
    }

    public int getMaxBoxNum() {
        return maxBoxNum;
    }

    public void setMaxBoxNum(int maxBoxNum) {
        this.maxBoxNum = maxBoxNum;
        resetFilter();
        getPrefs().putInt("MultipleXYTypeFilter.maxBoxNum",maxBoxNum);
    }

}
