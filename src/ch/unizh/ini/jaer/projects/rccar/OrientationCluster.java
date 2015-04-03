/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ch.unizh.ini.jaer.projects.rccar;

import java.awt.Graphics2D;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;

import com.jogamp.opengl.GLAutoDrawable;

import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.orientation.ApsDvsOrientationEvent;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;




/**
 *
 * @author braendch
 This Filter creates for each event an orientation vector and calculates the common orientation of its neighbors.
 To pass the filter, the difference of these two orientations has to be smaller than a certain 'tolerance' (in degrees),
 and the orientation must be within a certain range around vertical (ori) and the neighborhoodvector has to
 be big enough (neighborThr) to ensure that it doesn't have the right orientation just because of random.
 To create the orientation vector for each event the receptive (width*height) field is investigated and the
 normalized orientation vectors to each past event in the receptive field that satisfies a certain actuality
 (dt) is divided by the time past between the two events.
 If two events are of different polarity (data index 3) the orientation is roatated by 90° - this is because the contrast gradient
 is perpendicular to an edge.
 To simplify calculation all vectors have an positive y-component.
 The orientation History takes account of the past orientaions of the events and of the neighbors.

 */
    public class OrientationCluster extends EventFilter2D implements Observer, FrameAnnotater {

    public boolean isGeneratingFilter(){ return true;}

    private float thrGradient=getPrefs().getFloat("OrientationCluster.thrGradient",0);
    {setPropertyTooltip("thrGradient","The slope of the neighbor-vector-gradient");}

    private float tolerance=getPrefs().getFloat("OrientationCluster.tolerance",10);
    {setPropertyTooltip("tolerance","Percentage of deviation tolerated");}

    private float neighborThr=getPrefs().getFloat("OrientationCluster.neighborThr",10);
    {setPropertyTooltip("neighborThr","Minimum Length of Neighbor Vector to be accepted");}

    private float historyFactor=getPrefs().getFloat("OrientationCluster.historyFactor",1);
    {setPropertyTooltip("historyFactor","if oriHistoryEnabled this determines how strong the actual vector gets influenced by the previous one");}

    private float attentionFactor=getPrefs().getFloat("OrientationCluster.attentionFactor",1);
    {setPropertyTooltip("attentionFactor","if useAttention this determines how strong the actual vector gets influenced by the attention of the HingeLineTracker");}

    private float ori=getPrefs().getFloat("OrientationCluster.ori",45);
    {setPropertyTooltip("ori","Orientation tolerated");}

    private float dt=getPrefs().getFloat("OrientationCluster.dt",10000);
    {setPropertyTooltip("dt","Time Criteria for selection");}

    private float factor=getPrefs().getFloat("OrientationCluster.factor",1000);
    {setPropertyTooltip("factor","Determines the excitatory synapse weight");}

    private int width=getPrefs().getInt("OrientationCluster.width",1);
    private int height=getPrefs().getInt("OrientationCluster.height",1);
    {
        setPropertyTooltip("width","width of RF, total is 2*width+1");
        setPropertyTooltip("height","length of RF, total length is height*2+1");
    }

    private boolean showAll=getPrefs().getBoolean("OrientationCluster.showAll",false);
    {setPropertyTooltip("showAll","shows all events");}

    private boolean useAttention=getPrefs().getBoolean("OrientationCluster.useAttention",false);
    {setPropertyTooltip("useAttention","should the attention values from the HingeLineTracke have an influence on the filter");}

    private boolean useOppositePolarity=getPrefs().getBoolean("OrientationCluster.useOpositePolarity",true);
    {setPropertyTooltip("useOppositePolarity","should events be used for the calculation of the orientation vector");}

     private boolean showOriEnabled=getPrefs().getBoolean("SimpleOrientationFilter.showOriEnabled",true);
    {setPropertyTooltip("showOriEnabled","Shows Orientation with color code");}

    private boolean oriHistoryEnabled=getPrefs().getBoolean("OrientationCluster.oriHistoryEnabled",false);
    {setPropertyTooltip("oriHistoryEnabled","enable use of prior orientation values to filter out events not consistent with history");}

    // VectorMap[x][y][data] -->data: 0=x-component, 1=y-component, 2=timestamp, 3=polarity (0=off, 1=on, 4=theta
    // OriHistoryMap [x][y][data] --> data 0=x-component, 1=y-component, 2/3 = components neighborvector
    private float[][][] vectorMap;
    private float[][][] oriHistoryMap;
    public float[][] attention;

    public OrientationCluster(AEChip chip) {
        super(chip);
        initFilter();
        }

    @Override synchronized public void setFilterEnabled(boolean yes){
        super.setFilterEnabled(yes);
        if(yes){
            resetFilter();
        } else{
            vectorMap=null;
            out=null;
        }
    }

    private void checkMaps(){
        //it has to be checked if the VectorMap fits on the actual chip
        if((vectorMap==null)
                || (vectorMap.length!=chip.getSizeX())
                || (vectorMap[0].length!=chip.getSizeY())) {
            allocateMaps();
        }
    }


    synchronized private void allocateMaps() {
        //the VectorMap is fitted on the chip size
        if(!isFilterEnabled()) {
			return;
		}
        log.info("OrientationCluster.allocateMaps()");
        if(chip!=null){
            vectorMap=new float[chip.getSizeX()][chip.getSizeY()][7];
            oriHistoryMap=new float[chip.getSizeX()][chip.getSizeY()][7];
            attention=new float[chip.getSizeX()][chip.getSizeY()];
        }
        resetFilter();

    }

    @Override
	synchronized public EventPacket filterPacket(EventPacket in) {
        int sizex=chip.getSizeX()-1;
        int sizey=chip.getSizeY()-1;

        //Check if the filter is active
        if(in==null) {
			return null;
		}
        if(!filterEnabled) {
			return in;
		}
        if(enclosedFilter!=null) {
			in=enclosedFilter.filterPacket(in);
		}
        int n=in.getSize();
        if(n==0) {
			return in;
		}

        //Check if the input for the filter is the right one
        Class inputClass=in.getEventClass();
        if(inputClass!=PolarityEvent.class){
            log.warning("Wrong input event type "+in.getEventClass()+", disabling filter");
            setFilterEnabled(false);
            return in;
        }

        checkOutputPacketEventType(ApsDvsOrientationEvent.class);

        OutputEventIterator outItr=out.outputIterator();


        checkMaps();

        for(Object ein:in){
            PolarityEvent e=(PolarityEvent)ein;
            int x=e.x;
            int y=e.y;
            int xx=0;
            int yy=0;
            double vectorLength;
            //the neighbor values have to be calculated for each event
            //the sum of the neighbor components
            float neighborX=0;
            float neighborY=0;
            //the resulting components
            float neighborTheta=0;
            float neighborLength=0;


            //---calculate the actual vector and the neighborhood vector---
            vectorMap[x][y][0]=0;
            vectorMap[x][y][1]=0;


            //getString the polarity of the vector
            if(e.polarity == PolarityEvent.Polarity.Off){
                vectorMap[x][y][3] = 0;
            } else {
                vectorMap[x][y][3] = 1;
            }

            //iteration trough the whole receptive field
            for(int h=-height; h<=height; h++){
                for(int w=-width; w<=width; w++){
                    if((0<(x+w)) && ((x+w)<sizex) && (0<(y+h)) && ((y+h)<sizey)){
                        //calculation of timestampdifference (+1 to avoid division trough 0)
                        float t=(e.timestamp-vectorMap[x+w][y+h][2])+1;

                        if(t<dt){
                            //one has to check if the events are of the same polarity
                        if(vectorMap[x][y][3] != vectorMap[x+w][y+h][3]){
                            //if they are of a different polarity, the values have to be rotated
                            if(useOppositePolarity){
                                if (w<0){
                                    //different polarity - left side --> 90° CW
                                    xx = h;
                                    yy = -w;
                                } else {
                                    //different polarity - right side --> 90° CCW
                                    xx = -h;
                                    yy = w;
                                }
                            }
                        } else {
                            //if they are of the same kind this doesn't have to be done
                            if (h<0){
                                //same polarity - down (unwanted) --> point inversion
                                xx = -w;
                                yy = -h;
                            } else {
                                //same polarity - up --> nothing
                                xx = w;
                                yy = h;
                            }
                        }
                        //The normalized value of the vector component gets multiplied by a factor and "decayed" (1/t) and added

                        vectorLength = Math.sqrt((xx*xx)+(yy*yy));

                        if (vectorLength != 0.0){
                        vectorMap[x][y][0] = (float)(vectorMap[x][y][0]+((xx/(vectorLength))*(factor/t)));
                        vectorMap[x][y][1] = (float)(vectorMap[x][y][1]+((yy/(vectorLength))*(factor/t)));

                        //Neighborhood vector calculation
                        if(oriHistoryEnabled){
                            neighborX = neighborX + (vectorMap[x+w][y+h][0]+(historyFactor*oriHistoryMap[x+w][y+h][0]));
                            neighborY = neighborY + (vectorMap[x+w][y+h][1]+(historyFactor*oriHistoryMap[x+w][y+h][1]));
                        } else {
                            neighborX = neighborX + vectorMap[x+w][y+h][0];
                            neighborY = neighborY + vectorMap[x+w][y+h][1];
                        }
                        }
                        }

                    }
                }
            }
            //if the attention is used the sum of the vector components gets enlarged
            if(useAttention){
                            if(attention[e.x][e.y]!=0){
                                vectorMap[x][y][0] = vectorMap[x][y][0]+(vectorMap[x][y][0]*attentionFactor*attention[x][y]);
                                vectorMap[x][y][1] = vectorMap[x][y][1]+(vectorMap[x][y][1]*attentionFactor*attention[x][y]);
                            }
                        }

            neighborLength = (float)Math.sqrt((neighborX*neighborX)+(neighborY*neighborY));
            neighborTheta = (float)Math.atan(neighborX/neighborY);

            //if the oriHistory is enabled the vector length gets modified by the acient values
            if(oriHistoryEnabled){
                vectorMap[x][y][4] = (float)(Math.atan((vectorMap[x][y][0]+(historyFactor*oriHistoryMap[x][y][0]))
                        /(vectorMap[x][y][1]+(historyFactor*oriHistoryMap[x][y][1]))));

            } else {
                vectorMap[x][y][4] = (float)(Math.atan(vectorMap[x][y][0]/vectorMap[x][y][1]));

            }

            //The historyMap is upgraded
            oriHistoryMap[x][y][0] = vectorMap[x][y][0];
            oriHistoryMap[x][y][1] = vectorMap[x][y][1];
            oriHistoryMap[x][y][2] = vectorMap[x][y][2];
            oriHistoryMap[x][y][4] = vectorMap[x][y][4];





            //---------------------------------------------------------------------------
            //Create Output
            //the three conditions have to be fulfilled
            if((vectorMap[x][y][0]!=0) && (vectorMap[x][y][1]!=0)){
                    if((Math.abs(vectorMap[x][y][4]-neighborTheta)<((Math.PI*tolerance)/180)) &&
                            (Math.abs(vectorMap[x][y][4])<((ori*Math.PI)/180)) &&
                            (neighborLength > (neighborThr*(1-((thrGradient*e.y)/(sizey)))))){
                        //the output gets displayed
                        if(showOriEnabled){
                            ApsDvsOrientationEvent eout=(ApsDvsOrientationEvent)outItr.nextOutput();
                            eout.copyFrom(e);
                            eout.orientation=(byte)Math.abs((8*90*vectorMap[x][y][4])/(ori*Math.PI));
                            eout.hasOrientation=true;
                        }
                    }
            }
                //if showAll is
                if(showAll){
                    BasicEvent eout=outItr.nextOutput();
                    eout.copyFrom(e);
                }

           vectorMap[x][y][2]=e.timestamp;
           }
        //-------------------------------------------------------

        return out;

    }

    @Override
	public void resetFilter(){
        log.info("OrientationCluster.reset!");

        if(!isFilterEnabled()) {
			return;
		}

        if(vectorMap!=null){
            for (float[][] element : vectorMap) {
				for(int j=0;j<element.length;j++){
                    Arrays.fill(element[j],0);
                }
			}
        }
        if(oriHistoryMap!=null){
            for (float[][] element : oriHistoryMap) {
				for(int j=0;j<element.length;j++) {
					Arrays.fill(element[j],0);
				}
			}
        }
    }

    public Object getFilterState() {
        return vectorMap;
    }


    @Override
	public void initFilter(){
//        System.out.println("init!");
        resetFilter();

    }

    @Override
	public void update(Observable o, Object arg){
        initFilter();
    }

    @Override
	public void annotate(GLAutoDrawable drawable) {

    }

    /** not used */
    public void annotate(float[][][] frame) {
    }

    /** not used */
    public void annotate(Graphics2D g) {
    }

    public boolean isOriHistoryEnabled() {
        return oriHistoryEnabled;
    }

    public void setOriHistoryEnabled(boolean oriHistoryEnabled) {
        this.oriHistoryEnabled = oriHistoryEnabled;
        getPrefs().putBoolean("OrientationCluster.oriHistoryEnabled",oriHistoryEnabled);
    }

    public boolean isUseOppositePolarity() {
        return useOppositePolarity;
    }

    public void setUseOppositePolarity(boolean useOppositePolarity) {
        this.useOppositePolarity = useOppositePolarity;
        getPrefs().putBoolean("OrientationCluster.useOppositePolarity",useOppositePolarity);
    }

    public boolean isUseAttention() {
        return useAttention;
    }

    public void setUseAttention(boolean useAttention) {
        this.useAttention = useAttention;
        getPrefs().putBoolean("OrientationCluster.useAttention",useAttention);
    }

    public boolean isShowOriEnabled() {
        return showOriEnabled;
    }

    public void setShowOriEnabled(boolean showOriEnabled) {
        this.showOriEnabled = showOriEnabled;
        getPrefs().putBoolean("OrientationCluster.showOriEnabled",showOriEnabled);
    }

    public boolean isShowAll() {
        return showAll;
    }

    public void setShowAll(boolean showAll) {
        this.showAll = showAll;
        getPrefs().putBoolean("OrientationCluser.showAll",showAll);
    }

     public float getTolerance() {
        return tolerance;
    }

    synchronized public void setTolerance(float tolerance) {
        this.tolerance = tolerance;
        allocateMaps();
        getPrefs().putFloat("OrientationCluster.tolerance",tolerance);
    }

     public float getNeighborThr() {
        return neighborThr;
    }

    synchronized public void setNeighborThr(float neighborThr) {
        this.neighborThr = neighborThr;
        allocateMaps();
        getPrefs().putFloat("OrientationCluster.neighborThr",neighborThr);
    }

    public float getOri() {
        return ori;
    }

    synchronized public void setOri(float ori) {
        this.ori = ori;
        allocateMaps();
        getPrefs().putFloat("OrientationCluster.ori",ori);
    }

     public float getThrGradient() {
        return thrGradient;
    }

    synchronized public void setThrGradient(float thrGradient) {
        this.thrGradient = thrGradient;
        allocateMaps();
        getPrefs().putFloat("OrientationCluster.thrGradient",thrGradient);
    }

     public float getDt() {
        return dt;
    }

    synchronized public void setDt(float dt) {
        this.dt = dt;
        allocateMaps();
        getPrefs().putFloat("OrientationCluster.dt",dt);
    }

    public float getFactor() {
        return factor;
    }

    synchronized public void setFactor(float factor) {
        this.factor = factor;
        allocateMaps();
        getPrefs().putFloat("OrientationCluster.factor",factor);
    }

    public float getHistoryFactor() {
        return historyFactor;
    }

    synchronized public void setHistoryFactor(float historyFactor) {
        this.historyFactor = historyFactor;
        allocateMaps();
        getPrefs().putFloat("OrientationCluster.historyFactor",historyFactor);
    }

    public float getAttentionFactor() {
        return attentionFactor;
    }

    synchronized public void setAttentionFactor(float attentionFactor) {
        this.attentionFactor = attentionFactor;
        allocateMaps();
        getPrefs().putFloat("OrientationCluster.attentionFactor",attentionFactor);
    }

    public int getHeight() {
        return height;
    }

    synchronized public void setHeight(int height) {
        this.height = height;
        allocateMaps();
        getPrefs().putInt("OrientationCluster.height",height);
    }

    public int getWidth() {
        return width;
    }

    synchronized public void setWidth(int width) {
        this.width = width;
        allocateMaps();
        getPrefs().putInt("OrientationCluster.width",width);
    }
    }
