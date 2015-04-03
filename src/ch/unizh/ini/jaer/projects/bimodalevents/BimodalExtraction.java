

/**Extraction of Bimodal Events Based on Spike Timing
 * using the combined chip class retina.DVS128andCochleaAMS1b
 *
 * @author ssommer
 */

package ch.unizh.ini.jaer.projects.bimodalevents;


import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.glu.GLU;
import java.awt.Graphics2D;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Observable;
import java.util.Observer;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.EngineeringFormat;


/**
 * Creates Mapping between auditive and visual Events
 *
 * @author Stefan Sommer
 */
@Description("Extracts Bimodal Events")
public class BimodalExtraction extends EventFilter2D implements Observer, FrameAnnotater {

	// initializations & declarations

    private int[][][][] lastTs;
    private int[][][] lastTsCursor;

    private double [][][] visualArray = new double[128][128][2];
    private double [][][] audioArray = new double[128][128][2];

    private double [][] coherenceMap = new double[128][128];
    private double audioHistogram = 0;
    private Queue CoherenceQueue = new LinkedList();

    private int LastQueueTimestamp = 0;
    private int CoherenceWindow = getPrefs().getInt("Coherent Timing Window", 1000);

    private float ClusterThresh = getPrefs().getFloat("Clustering Threshold", 0.3f);
    private float audioEventThresh = getPrefs().getFloat("Audio Event Threshold", 13f);

    private float a_decay = getPrefs().getFloat("Cochlea Decaying Factor", 0.3f);
    private float v_decay = getPrefs().getFloat("Retina Decaying Factor", 0.9f);
    private float c_decay = getPrefs().getFloat("Coherent Spikes Decaying Factor", 0.95f);;

    private double prev_timestamp = 0;
    private int LastAudioSpike = 0;

    Iterator iterator;
    private float lastWeight = 1f;
    private int avgITD;
    private float avgITDConfidence = 0;
    private float ILD;
    EngineeringFormat fmt = new EngineeringFormat();
    FileWriter fstream;
    FileWriter fstreamBins;
    FileWriter freqfstreamBins;
    BufferedWriter ITDFile;
    BufferedWriter AvgITDFile;
    BufferedWriter BinFile;
    BufferedWriter freqBinFile;
    private boolean wasMoving = false;
    private int numNeuronTypes = 1;
    private static ArrayBlockingQueue ITDEventQueue = null;
    private boolean ITDEventQueueFull = false;

    private double ConfidenceRecentMax = 0;
    private int ConfidenceRecentMaxTime = 0;
    private double ConfidenceRecentMin = 1e6;
    private int ConfidenceRecentMinTime = 0;
    private boolean ConfidenceRising = true;

    GLU glu;

    private void init_arrays(){
        for (int i=0; i<128; i++){
            for (int j=0; j<128; j++) {
                visualArray[i][j][0]=0;
                audioArray[i][j][0]=0;
            }
        }
    }

    public BimodalExtraction(AEChip chip) {
        super(chip);
        chip.addObserver(this);
        initFilter();
/*

        addPropertyToGroup("ITDWeighting", "useLaterSpikeForWeight");
        addPropertyToGroup("ITDWeighting", "usePriorSpikeForWeight");
        addPropertyToGroup("ITDWeighting", "maxWeight");
        addPropertyToGroup("ITDWeighting", "maxWeightTime");
*/
		// Tooltips for property dialog (filter properties)
        setPropertyTooltip("a_decay", "Decaying rate of audio spikes for Event-Thresholding");
        setPropertyTooltip("c_decay", "Decaying rate of the Coherent spikes (how long they stay visible)");
        setPropertyTooltip("audioEventThresh", "Spikes needed to detect Audio as Event (noise reduction)");
        setPropertyTooltip("ClusterThresh", "Clustering Threshold relative to Maximum");
        setPropertyTooltip("v_decay", "Decayingfactor of the Retina histogram map");
        setPropertyTooltip("CoherenceWindow", "Timeframe within which Spikes count as correlated");

 /*        if (chip.getRemoteControl() != null) {
            chip.getRemoteControl().addCommandListener(this, "itdfilter", "Testing remotecontrol of itdfilter.");
        }

 */
    }

	// function executed when spike packet arrives
    @Override
	public EventPacket<?> filterPacket(EventPacket<?> in) {

        if (!filterEnabled) {
            return in;
        }

        for (Object e : in) {

            BasicEvent ev = (BasicEvent)e;
            try {

	             if (ev.y > 128){
	                // cochlea

	                audioArray[ev.x][ev.y-128][1] = audioArray[ev.x][ev.y-128][1]+1;
	                	// set last audio spike timestamp for identifying coherent visual spikes
                        LastAudioSpike = ev.timestamp;
                        // "audioHistogram" to filter out noisy audio spikes
                        audioHistogram = audioHistogram + 1;
	            } else {
	                // retina
	                visualArray[ev.x][ev.y][1] = visualArray[ev.x][ev.y][1]+1;	// building up histogram map
	                	// is spike in the coherence time window from the last audio spike?
                        if (Math.abs(ev.timestamp-LastAudioSpike)<CoherenceWindow){
                            if (audioHistogram > audioEventThresh){
                            	// function deals with coherent spikes
                                process_coherent_spike(ev);
                            }
                        }
                        else {	// if no, build up queue for future audio spikes
                            CoherenceQueue.offer(ev);
                            LastQueueTimestamp = ev.timestamp;
                            BasicEvent CoItem = (BasicEvent)CoherenceQueue.peek();
                            // clear visual spikes later than 2*CoherenceWindow from queue
                            while ((CoItem.timestamp+(2*CoherenceWindow))<LastQueueTimestamp){
                                CoherenceQueue.remove();
                                CoItem = (BasicEvent)CoherenceQueue.peek();
                            }
                        }

	            }
	            	/*
	            	/ decaying only executed every 100000 (according to timestamp differences approx 0.1 sec
	            	*/
	            	// dissolve wrapping-problem for recordings
                    if (prev_timestamp > ev.timestamp){
                        prev_timestamp = 0;
                    }
                    if ((ev.timestamp - prev_timestamp) > 100000){
                        double time_diff = ev.timestamp - prev_timestamp;
                        prev_timestamp = ev.timestamp;
                        double decay = 0.9;

						// call decaying functions
                        decay_audio(a_decay);
                        decay_video(v_decay);
                        decay_coherence(c_decay);
                    }


	        } catch (Exception ex) {
	        	// write exeption to logfile if emerged
	            log.warning("In filterPacket caught exception " + ex + " " + ev.x + " " + ev.y);
	            ex.printStackTrace();
	        }

        }
		// execute median filter with size 3
        median_filter(3);
        return in;
    }

	/*
	/	Coherent Spikes are processed here
	*/
    public void process_coherent_spike(BasicEvent ev){

        double max = max_video();

        // linear weightening depending on timediff
        //  x = timediff/CoherenceWindow
        // -1/2 * abs(x) + 3/2
        float increase = 1.5f-((0.5f*Math.abs(ev.timestamp-LastAudioSpike))/CoherenceWindow);

        if ((ev.x>2) && (ev.x<126) && (ev.y>2) && (ev.y<126)){
            for (int i=ev.x-1; i<=(ev.x+1); i++){
                for (int j=ev.y-1; j<=(ev.y+1); j++){
                    if (visualArray[i][j][0]>(0.5*max)){
                    	// increase coherenceMap according to the timediff
                        coherenceMap[i][j]=coherenceMap[i][j]+increase;
                    }
                }
            }
        }

    }
	/*
	/	decaying functions, multiply all values by the corresponding decaying factor
	*/

    public void decay_audio(double decay_factor){
        audioHistogram = audioHistogram * decay_factor;
        for (int i=0; i<128; i++){
            for (int j=0; j<128; j++) {
                audioArray[i][j][1]=audioArray[i][j][1]*decay_factor;
            }
        }

    }
    public void decay_video(double decay_factor){
        for (int i=0; i<128; i++){
            for (int j=0; j<128; j++) {
                visualArray[i][j][1]=visualArray[i][j][1]*decay_factor;
            }
        }

    }

    public void decay_coherence(double decay_factor){
        for (int i=0; i<128; i++){
            for (int j=0; j<128; j++) {
                coherenceMap[i][j]=coherenceMap[i][j]*decay_factor;
            }
        }

    }

	/*
	/	median filter
	*/
    public void median_filter(int win_size){
        double[] frame = new double[win_size*win_size];
        int half_win = win_size/2;
        // clear border
/*        for (int i=0; i<128; i++){
            for (int j=0; j<128; j++){
                if (i<half_win || j<half_win || i>128-half_win || j>128-half_win){
                    visualArray[i][j][0]=0;
                    visualArray[i][j][1]=0;
                }
            }
        }
*/
        for (int i=half_win; i<(128-half_win); i++){
            for (int j=half_win; j<(128-half_win); j++) {
                int a=0;
                for (int k=i-half_win; k<=(i+half_win); k++){
                    for (int l=j-half_win; l<=(j+half_win); l++) {
                        frame[a] = visualArray[k][l][1];
                        a = a + 1;
                    }
                }
                // sort array to figure out median value of frame
                Arrays.sort(frame);
                visualArray[i][j][0] = frame[(win_size*win_size)/2];
            }
        }
        //flip_zeronone();
    }

    public void flip_zeronone(){
        double[][] temp = new double[128][128];
        for (int i=0; i<128; i++){
            for (int j=0; j<128; j++) {
                temp[i][j]=visualArray[i][j][1];
                visualArray[i][j][1] = visualArray[i][j][0];
                visualArray[i][j][0] = temp[i][j];
            }
        }
    }

	/*
	/	functions which return maximum
	*/

    public double max_video(){
        double max = 0;
        for (int i=0; i<128; i++){
            for (int j=0; j<128; j++) {
                if (visualArray[i][j][1]>max){
                    max = visualArray[i][j][1];
                }
            }
        }
        return max;
    }

    public double max_audio(){
        double max = 0;
        for (int i=0; i<128; i++){
            for (int j=0; j<128; j++) {
                if (audioArray[i][j][1]>max){
                    max = audioArray[i][j][1];
                }
            }
        }
        return max;
    }

      public double max_coherence(){
        double max = 0;
        for (int i=0; i<128; i++){
            for (int j=0; j<128; j++) {
                if (coherenceMap[i][j]>max){
                    max = coherenceMap[i][j];
                }
            }
        }
        return max;
    }

    /**
	*	Drawing routine
	*/
    @Override
	public void annotate(GLAutoDrawable drawable) {


        double maximum = max_video();
        double a_maximum = max_audio();
        double c_maximum = max_coherence();
        double threshold = maximum*0.5;
        GL2 gl=drawable.getGL().getGL2();

        gl.glLineWidth(1);
        gl.glPushMatrix();

        gl.glBegin(GL.GL_POINTS);
        gl.glColor4f(0,0,1,.3f);

        // visual histogram (lower right) with color gradients
        for (int i=0; i<128; i++){
            for (int j=0; j<128; j++) {
                double dratio = visualArray[i][j][1]/maximum;
                float ratio = (float)dratio;
                if (ratio<0.25){
                    gl.glColor4f(0,ratio*4,1,.8f);
                }
                if ((ratio<0.5) && (ratio>=0.25)){
                    gl.glColor4f(0,1,(1/(2*ratio))-1,.8f);
                }
                if ((ratio<0.75) && (ratio>=0.5)){
                    gl.glColor4f((4*ratio)-2,1,0,.8f);
                }
                if (ratio>=0.65){
                    gl.glColor4f(1,(3/ratio)-3,0,.8f);
                }
                gl.glVertex2d(130+i, j);
            }
        }
        gl.glColor4f(0,1,0,.3f);
/*        for (int i=0; i<128; i++){
            for (int j=0; j<128; j++) {
                if (audioArray[i][j][1]>a_maximum*0.1){
                    gl.glVertex2d(-130+i, j);
                }
            }
        }*/

        // Clusters (thresholded histogram), upper left
        for (int i=0; i<128; i++){
            for (int j=0; j<128; j++) {
                if (visualArray[i][j][0]>(maximum*ClusterThresh)){
                    gl.glVertex2d(-130+i, 130+j);
                }
            }
        }

		// display coherent spikes (upper right) if they appear in a cluster
        for (int i=0; i<128; i++){
            for (int j=0; j<128; j++) {
                if (c_maximum > 1){
                    float c_ratio = (float)(coherenceMap[i][j]/c_maximum);
                    //gl.glColor4f(c_ratio,0,0,1f);
                   if (c_ratio >0.2){
                        gl.glColor4f(c_ratio/2+0.5f,0,0,1f);
                        gl.glVertex2d(130+i, 130+j);
                   }

                }

            }
        }

        gl.glEnd();

        gl.glPopMatrix();

        gl.glFlush();
//        gl.glDisable(GL.GL_BLEND);
/*        gl.glPushMatrix();
        gl.glTranslatef(30,30,0);
        gl.glColor4f(0,0,1,.3f);

*/
    }




    public Object getFilterState() {
        return null;
    }

    @Override
	public void resetFilter() {
        initFilter();
    }

    @Override
    public void initFilter() {

        log.info("Cochlea - Retina Mapper Filter started\n");

    }

    @Override
    public synchronized void setFilterEnabled(boolean yes) {
//        log.info("ITDFilter.setFilterEnabled() is called");
        super.setFilterEnabled(yes);
        if (yes) {
            initFilter();
        }
    }

    @Override
	public void update(Observable o, Object arg) {
        if (arg != null) {
//            log.info("ITDFilter.update() is called from " + o + " with arg=" + arg);
/*            if (arg.equals("eventClass")) {
                if (chip.getEventClass() == CochleaAMSEvent.class) {
                    hasMultipleGanglionCellTypes = true;
                    this.numNeuronTypes = 4;
                } else {
                    hasMultipleGanglionCellTypes = false;
                    this.numNeuronTypes = 1;
                }
                this.initFilter();
            }*/
        }
    }

    /*
    *	methods are needed to adjust variables in property dialog
    */
    public float getV_decay(){
        return this.v_decay;
    }
    public void setV_decay(float v_decay){
        getPrefs().putFloat("Distinguish.v_decay", v_decay);
        getSupport().firePropertyChange("v_decay", this.v_decay, v_decay);
        this.v_decay = v_decay;
    }


    public float getA_decay(){
        return this.a_decay;
    }
    public void setA_decay(float a_decay){
        getPrefs().putFloat("Distinguish.a_decay", a_decay);
        getSupport().firePropertyChange("a_decay", this.a_decay, a_decay);
        this.a_decay = a_decay;
    }

    public float getC_decay(){
        return this.c_decay;
    }
    public void setC_decay(float c_decay){
        getPrefs().putFloat("Distinguish.c_decay", c_decay);
        getSupport().firePropertyChange("c_decay", this.c_decay, c_decay);
        this.c_decay = c_decay;
    }

    public float getClusterThresh(){
        return this.ClusterThresh;
    }
    public void setClusterThresh(float ClusterThresh){
        getPrefs().putFloat("Distinguish.ClusterThresh", ClusterThresh);
        getSupport().firePropertyChange("ClusterThresh", this.ClusterThresh, ClusterThresh);
        this.ClusterThresh = ClusterThresh;
    }
    public float getAudioEventThresh(){
        return this.audioEventThresh;
    }
    public void setAudioEventThresh(float audioEventThresh){
        getPrefs().putFloat("Distinguish.audioEventThresh", audioEventThresh);
        getSupport().firePropertyChange("audioEventThresh", this.audioEventThresh, audioEventThresh);
        this.audioEventThresh = audioEventThresh;
    }

    public int getCoherenceWindow() {
        return this.CoherenceWindow;
    }
    public void setCoherenceWindow(int CoherenceWindow) {
        getPrefs().putInt("Distinguish.CoherenceWindow", CoherenceWindow);
        getSupport().firePropertyChange("CoherenceWindow", this.CoherenceWindow, CoherenceWindow);
        this.CoherenceWindow = CoherenceWindow;
    }



   public void annotate(float[][][] frame) {
        throw new UnsupportedOperationException("Not supported yet, use openGL rendering.");
    }

    public void annotate(Graphics2D g) {
        throw new UnsupportedOperationException("Not supported yet, use openGL rendering..");
    }

}