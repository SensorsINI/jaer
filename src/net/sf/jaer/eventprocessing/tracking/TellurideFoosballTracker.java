/*
 * RectangularClusterTracker.java
 *
 * Created on December 5, 2005, 3:49 AM
 */
package net.sf.jaer.eventprocessing.tracking;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Point2D;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import java.util.logging.Level;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.gl2.GLUT;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;
import net.sf.jaer.aemonitor.AEConstants;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.ApsDvsEventPacket;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.OutputEventIterator;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.event.TypedEvent;
import net.sf.jaer.event.orientation.ApsDvsOrientationEvent;
import net.sf.jaer.event.orientation.OrientationEventInterface;
import static net.sf.jaer.eventprocessing.EventFilter.log;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.ChipCanvas;
import net.sf.jaer.graphics.FrameAnnotater;
import net.sf.jaer.util.DrawGL;
import net.sf.jaer.util.filter.LowpassFilter;
import redis.clients.jedis.Jedis; 

/**
 * A simple circle tracker based on a hough transform that correctly tracks the maximum even when it's location changes out from under us.
 * @author Jan Funke
 */
@Description("Ball tracker for a foosball table")
@DevelopmentStatus(DevelopmentStatus.Status.Experimental)
public class TellurideFoosballTracker extends EventFilter2D
    implements Observer, FrameAnnotater/* , PreferenceChangeListener */ {
    
    // Jedis
    protected String host = "localhost";
    protected int port = getInt("port", 6379);
    Jedis jedis;
    long jedisTime;
	
	private static final int FIFO_LEN = 100000;//2000;
    private static final int FIFO_MAX_TS = 20000;   // us
	private static final int MAX_X = 240;
	private static final int MAX_Y = 180;
	private static final int MAX_CLUSTER = 20;
	private static final int CLUSTER_PERIOD = 1;
	private static final int CLUSTER_MAX_TS = 500000;
	private static final int CLUSTER_BALL_MAX_TS = 2000000;
	private static final int CLUSTER_START_SIZE = 8;
	private static final int CLUSTER_INHERIT_DIS = 12;
	private static final int CLUSTER_V_TS = 1000;
	private static final int BALL_REC_PERIOD = 10;
	private static final int MERGE_DISTANCE = 1;	//(CLUSTER_START_SIZE+2)
	private static final int CIRCLE_DOT = 32;
	private static final int BAR_NUM = 7;
	private static final int HOUGH_TH = 0;
	private static final int HOUGH_CREATE_TH = 25;
	private static final int HOUGH_MAX = HOUGH_TH+50;
	private static final int MAX_EVENT = 10000000;
	private static final int LINE_UP_RANGE = 8;
	private static final int LINE_UP_MIN_Y = 25;
    private static final int RANGE_DECAY_TS = 1000000;
    //private static final int SHAPE_MAP_SIZE = 8;
	private static final float A = -1f;
	private static final float B = 1;
	private static final float C = 0;
	private static final float D = 0;
    
    
	protected boolean showVelocity = getBoolean("showVelocity", true);
	protected boolean showHoughSpace = getBoolean("showHoughSpace", false);
	protected boolean showClusterNumber = getBoolean("showClusterNumber", false);
	protected boolean showNonBallCluster = getBoolean("showNonBallCluster", false);
	protected boolean showProbabilities = getBoolean("showProbabilities", false);
	protected boolean showVicinity = getBoolean("showVicinity", true);
	private boolean sendITD_UDP_Messages = getBoolean("sendITD_UDP_Messages", false);
	private String sendITD_UDP_port = getString("sendITD_UDP_port", "localhost:9999");
    
	
	// with diameter of 9
	int ballRadius = 6;
	int[] xinc = { 4, 4, 4, 3, 3, 2, 2, 1, 0, -1, -2, -2, -3, -3, -4, -4, -4, -4, -4, -3, -3, -2, -2, -1, 0, 1, 2, 2, 3, 3, 4, 4 };
	int[] yinc = { 0, -1, -2, -2, -3, -3, -4, -4, -4, -4, -4, -3, -3, -2, -2, -1, 0, 1, 2, 2, 3, 3, 4, 4, 4, 4, 4, 3, 3, 2, 2, 1 };
	int[] barX = { 25, 55, 88, 121, 152, 180, 209 };// track3.aedat
	//static int barX[BAR_NUM] = { 22, 51, 80, 112, 144, 176, 207, 236 };	// test_07_12movingplayer
	static int[] boundX = { 0, 225 };	// track3.aedat
	//static int boundX[2] = { 10, 240 };	// test_07_12movingplayer
	//static int[] boundY = { 26, 170 };	// track3.aedat
	static int[] boundY = { 15, 160 };	// track3.aedat
	//static int boundY[2] = { 7, 147 };	// test_07_12movingplayer
	
	// tracking constants
	float centroidWeight = 0.2f;
	float velocityWeight = 0.01f, velocityAveWeight = 0.0002f;

	// ball probability constant
	float vWeight = 0.1f;//0.2f;
	float lineWeight = 0f;//0.35f;
    float vicWeight = 0f;//0.25f;
    float shapeWeight = 0.6f;//0.25f;
	float ageWeight = 1 - vWeight - lineWeight - vicWeight - shapeWeight;
	float vProb, disToBar, minDisToBar, lineProb, ageProb, vicProb, shapeProb;
    float vicXScale = 0.03f, vicYScale = 0.03f, vicXOld, vicYOld, vicVXOld, vicVYOld, vicMinRange = 4;
    float vicLLX, vicLLY, vicURX, vicURY;
    float lineDis;
    int lineCount;
	int vLimit = 150, vTh = 50;
	//int lineLimit = 15, lineTh = 5;
	float lineLimit = 6, lineTh = 2;
	int ageLimit = 500, ageTh = 200;
    float shapeLimit = 50, shapeTh = 20, shapeYLimit = 70, shapeYTh = 50;
    float rangeDecayRate = 0.02f;//0.999f;//0.02f;
    /*float[][] shapeProbMap = {
        {A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A},
        {A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A},
        {A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A},
        {A, A, A, A, A, D, D, D, D, D, D, A, A, A, A, A},
        {A, A, A, A, D, D, B, B, B, B, D, D, A, A, A, A},
        {A, A, A, D, D, B, B, C, C, B, B, D, D, A, A, A},
        {A, A, A, D, B, B, C, 0, 0, C, B, B, D, A, A, A},
        {A, A, A, D, B, C, 0, 0, 0, 0, C, B, D, A, A, A},
        {A, A, A, D, B, C, 0, 0, 0, 0, C, B, D, A, A, A},
        {A, A, A, D, B, B, C, 0, 0, C, B, B, D, A, A, A},
        {A, A, A, D, D, B, B, C, C, B, B, D, D, A, A, A},
        {A, A, A, A, D, D, B, B, B, B, D, D, A, A, A, A},
        {A, A, A, A, A, D, D, D, D, D, D, A, A, A, A, A},
        {A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A},
        {A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A},
        {A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A},
        };*/
    
	
	int FIFOHeadIx = 0, FIFOTailIx = 0, FIFOSize = 0, FIFOPopFlag = 0;

	// file pointers
	int fpIx, eventNum;
    
    // event space constant
	int[][] eventSpace = new int[MAX_X][MAX_Y];

	// Hough space constant
	int[][] HoughSpace = new int[MAX_X][MAX_Y];
	int[][] HoughAboveTh = new int[MAX_X][MAX_Y];
	
	int eventIx, clusterIx, clusterIx2, onIx, ballIx = MAX_CLUSTER, barIx;
    int[] nearbyClusterFlag = new int[MAX_CLUSTER];
	int ts, x, y, xOld, yOld, temp, tsOld, tsOldV, eventOld, ballIxOld;
	int HoughChangeNum, clusterNum = 0, clusterCopyNum;
	int findClusterFlag, ballPruneFlag, findBallFlag = 0;
	float changeX, changeY, clusterX, clusterY, clusterX2, clusterY2, centroidMoveX, centroidMoveY, xDif, yDif, maxBallProb;
	int clusterXRnd, clusterYRnd, clusterX2Rnd, clusterY2Rnd, clusterSize, weight1, weight2;
	int tDif;

	// opencv related variables
	int xFrame, yFrame, imgIx;
	float vLineScale = 5, vAveLineScale = 1;//100;

	// Kalman filter
	int kfTsOld = 0, kfTsDif = 0, kfFlag = 0;
	
	Clusters[] clusterList = new Clusters[MAX_CLUSTER];
	Clusters[] clusterListCopy = new Clusters[MAX_CLUSTER];
	
	DVSEvents[] FIFO = new DVSEvents[FIFO_LEN];
	
	HoughChanges[] HoughChangeOn = new HoughChanges[CIRCLE_DOT];
    
    
	protected DatagramChannel channel = null;
	protected DatagramSocket socket = null;
	int packetSequenceNumber = 0;
	InetSocketAddress client = null;
	private final int UDP_BUFFER_SIZE = 8192;
	private ByteBuffer udpBuffer = ByteBuffer.allocateDirect(UDP_BUFFER_SIZE);
	private boolean printedFirstUdpMessage = false;
	long lastUdpMsgTime = 0;
	int MIN_UPD_PACKET_INTERVAL_MS = 100;
    int udpSequenceCnt = 0;
	
	public TellurideFoosballTracker(AEChip chip) {
		super(chip);
		chip.addObserver(this);
		resetFilter();
        jedis = new Jedis(host); 
        System.out.println("Connection to server sucessfully"); 
        jedisTime = System.currentTimeMillis();
		// <editor-fold defaultstate="collapsed" desc=" -- Property Tooltips -- ">
		final String sizing = "Sizing", mov = "Movement", life = "Lifetime", disp = "Display", global = TOOLTIP_GROUP_GLOBAL,
			update = "Update", logg = "Logging", pi = "PI Controller";
		setPropertyTooltip(disp, "showVelocity", "shows the velocity of the ball");
		setPropertyTooltip(disp, "showHoughSpace", "shows the Hough space");
		setPropertyTooltip(disp, "showClusterNumber", "shows cluster ID number");
		setPropertyTooltip(disp, "showNonBallCluster", "shows non-ball clusters");
		setPropertyTooltip(disp, "showProbabilities", "shows the ball probabilities of the cluster");
		setPropertyTooltip(disp, "showVicinity", "shows the vicinity area of the ball according to velocities");
		String udp = "UDP Messages";
		setPropertyTooltip(udp, "sendITD_UDP_Messages", "send ITD messages via UDP datagrams to a chosen host and port");
		setPropertyTooltip(udp, "sendITD_UDP_port", "hostname:port (e.g. localhost:9999) to send UDP ITD histograms to; messages are int32 seq # followed by int32 bin values");
	}
    
	public Object getFilterState() {
		return null;
	}
    
	@Override
	public void resetFilter() {
		initFilter();
	}
    
	@Override
	synchronized public EventPacket<?> filterPacket(EventPacket<?> in) {
		if (!filterEnabled) {
			return in;
		}

		// added so that packets don't use a zero length packet to set last
		// timestamps, etc, which can purge clusters for no reason
		if (in.getSize() == 0) {
			return in;
		}

		if (enclosedFilter != null) {
			in = enclosedFilter.filterPacket(in);
		}

		if (in instanceof ApsDvsEventPacket) {
			checkOutputPacketEventType(in); // make sure memory is allocated to avoid leak
		}
        
        track(in);
        
        if(System.currentTimeMillis() - jedisTime > 10){
            if (ballIx < MAX_CLUSTER){
                jedis.set("pos", Float.toString(clusterList[ballIx].x) + ';' + Float.toString(clusterList[ballIx].y));
                jedis.set("vel", Float.toString(clusterList[ballIx].vX) + ';' + Float.toString(clusterList[ballIx].vY));
                jedisTime = System.currentTimeMillis();
            }
            else{                
                jedis.set("pos", Float.toString(300) + ';' + Float.toString(300));
                jedis.set("vel", Float.toString(300) + ';' + Float.toString(300));
                jedisTime = System.currentTimeMillis();
            }
        }		// send udp messages
        
        // sending UDP packages
//        try {
//            channel = DatagramChannel.open();
//            socket = channel.socket(); // bind to any available port because we will be sending datagrams with included host:port info
//        } catch (IOException ex) {
//            log.warning("cannot open channel "+ex.toString());
//        }
//        
//		long now = System.currentTimeMillis();
//        String udpMsg;
//		if (((now - lastUdpMsgTime) > MIN_UPD_PACKET_INTERVAL_MS) && sendITD_UDP_Messages && (client != null)) {
//			lastUdpMsgTime = now;
//            try {
//                udpBuffer.clear();
//                if (ballIx < MAX_CLUSTER){
//                    udpMsg = String.format("%d;%.3f;%.3f;%.3f;%.3f",udpSequenceCnt++, clusterList[ballIx].x,clusterList[ballIx].y,clusterList[ballIx].vX,clusterList[ballIx].vY);
//                }
//                else{
//                    udpMsg = String.format("%d;%.3f;%.3f;%.3f;%.3f",udpSequenceCnt++,300f,300f,300f,300f);
//                }
//                //String udpMsg = String.format("%.3f;%.3f",0.1f,0.1f);
//                udpBuffer.put(udpMsg.getBytes());
//                //udpBuffer.putFloat(f);
////                if (!printedFirstUdpMessage) {
////                    log.info("sending buf=" + udpBuffer + " to client=" + client);
////                    printedFirstUdpMessage = true;
////                }
//                udpBuffer.flip();
//                channel.send(udpBuffer, client);
//            } 
//            catch (IOException udpEx) {
//                    log.warning(udpEx.toString());
//                    setSendITD_UDP_Messages(false);
//            } 
//            catch (BufferOverflowException boe) {
//                    log.warning(boe.toString() + ": decrease number of histogram bins or increase rendering rate for less ADC samples per UDP packet to fit "+UDP_BUFFER_SIZE+" byte datagrams");
//            }
//        }
        
        return in;
		//return filterEventsEnabled ? filteredPacket : in;
	}
    
	synchronized protected EventPacket<? extends BasicEvent> track(EventPacket<?> in) {
		int i;
        int findInheritClusterFlag, inheritClusterIx = MAX_CLUSTER;
        float inheritClusterDis;
		OutputEventIterator outItr = out.outputIterator();
		int sx = chip.getSizeX(), sy = chip.getSizeY();
        int FIFOIx;

		if (in.getSize() == 0) {
			return in; // nothing to do
		}
		for (Object ein:in ) {
            // ignore the event if it's not an polarity event
            if(!(ein instanceof PolarityEvent)) continue;
            PolarityEvent ev = (PolarityEvent)ein;
			ts = ev.timestamp;
			x = ev.x;
			y = ev.y;
			//pol = eventBuf[eventIx].pol;
            
            // if the time stamp is wrapped around, reset the filter
            if(tsOld > (ts + 1000)){
                resetFilter();
            }
            // initialize tsOldV for velocity estimation
            if(tsOldV == 0){
                tsOldV = ts;
            }

			// check the length of FIFO 
			if (FIFOSize >= FIFO_LEN) {
                FIFOPop();
			}
            else {
                FIFOIx = FIFOHeadIx;
                while (FIFOIx != FIFOTailIx){
                    // pop the FIFO data if it's too old
                    if (ts - FIFO[FIFOIx].ts >= FIFO_MAX_TS){
                        FIFOPop();
                        FIFOIx++;
                        if (FIFOIx >= FIFO_LEN) {
                            FIFOIx = 0;
                        }
                    }
                    else{
                        break;
                    }
                }
            }

			// push x, y, ts in to FIFO and increase Hough space
			FIFO[FIFOTailIx].ts = ts;
			FIFO[FIFOTailIx].x = x;
			FIFO[FIFOTailIx].y = y;
            eventSpace[x][y]++;
			//FIFO[FIFOTailIx].pol = pol;
			HoughChangeNum = HoughChange(x, y, 1);

			// wrap the tail around if exceeds length
			FIFOTailIx++;
			if (FIFOTailIx >= FIFO_LEN) {
				FIFOTailIx = 0;
			}
			FIFOSize++;
			
			// clear flags for the clusters that will be changed in the next loop
			for (clusterIx = 0; clusterIx < MAX_CLUSTER; clusterIx++) {
				nearbyClusterFlag[clusterIx] = 0;
			}

			// for each Hough event, find or establish clusters
			for (onIx = 0; onIx < HoughChangeNum; onIx++) {
				findClusterFlag = 0;
				changeX = (float)HoughChangeOn[onIx].x;
				changeY = (float)HoughChangeOn[onIx].y;
				// if the cluster list is not empty, go through all the clusters to find a nearby one with lowest index (oldest)
				for (clusterIx = 0; clusterIx < clusterNum; clusterIx++) {
					clusterX = clusterList[clusterIx].x;
					clusterY = clusterList[clusterIx].y;
					xDif = absFloat(changeX - clusterX);
					yDif = absFloat(changeY - clusterY);
					clusterSize = clusterList[clusterIx].r;
					// if the cluster contains the event, set flag
					if((xDif <= clusterSize) && (yDif <= clusterSize)){
						findClusterFlag = 1;
						break;
					}
				}
				// if the cluster list is empty or no nearby cluster is found
                // and the Hough event is above a threshold, set up a new cluster 
				if((findClusterFlag == 0) && (HoughSpace[(int)changeX][(int)changeY] >= HOUGH_CREATE_TH)){
					// if the cluster limit is not exceeded
					if ((clusterNum < MAX_CLUSTER) && (changeX >= boundX[0]) && (changeX <= boundX[1]) 
                            && (changeY >= boundY[0]) && (changeY <= boundY[1])) {
                        findInheritClusterFlag = 0;
                        inheritClusterDis = MAX_X;
                        // if there is a very close cluster, inheritate its properties
                        for (i = 0; i < clusterNum; i++){
                            clusterX = clusterList[i].x;
                            clusterY = clusterList[i].y;
                            xDif = absFloat(changeX - clusterX);
                            yDif = absFloat(changeY - clusterY);
                            if ((xDif <= CLUSTER_INHERIT_DIS) && (yDif <= CLUSTER_INHERIT_DIS)){
                                // find the smallest distance
                                if ((xDif + yDif) < inheritClusterDis){
                                    findInheritClusterFlag = 1;
                                    inheritClusterIx = i;
                                    inheritClusterDis = xDif + yDif;
                                }
                            }
                        }
                        if (findInheritClusterFlag != 0){
                                clusterList[clusterNum].x = changeX;
                                clusterList[clusterNum].y = changeY;
                                clusterList[clusterNum].xOld = changeX;
                                clusterList[clusterNum].yOld = changeY;
                                clusterList[clusterNum].xMin = clusterList[inheritClusterIx].xMin;
                                clusterList[clusterNum].xMax = clusterList[inheritClusterIx].xMax;
                                clusterList[clusterNum].yMin = clusterList[inheritClusterIx].yMin;
                                clusterList[clusterNum].yMax = clusterList[inheritClusterIx].yMax;
                                clusterList[clusterNum].xMinTs = clusterList[inheritClusterIx].xMinTs;
                                clusterList[clusterNum].xMaxTs = clusterList[inheritClusterIx].xMaxTs;
                                clusterList[clusterNum].yMinTs = clusterList[inheritClusterIx].yMinTs;
                                clusterList[clusterNum].yMaxTs = clusterList[inheritClusterIx].yMaxTs;
                                clusterList[clusterNum].r = clusterList[inheritClusterIx].r;
                                clusterList[clusterNum].ts = clusterList[inheritClusterIx].ts;
                                clusterList[clusterNum].vX = clusterList[inheritClusterIx].vX;
                                clusterList[clusterNum].vY = clusterList[inheritClusterIx].vY;
                                clusterList[clusterNum].vXAve = clusterList[inheritClusterIx].vXAve;
                                clusterList[clusterNum].ballProb = clusterList[inheritClusterIx].ballProb;
                                clusterList[clusterNum].tsGen = clusterList[inheritClusterIx].tsGen;
                                clusterList[clusterNum].vProb = clusterList[inheritClusterIx].vProb;
                                clusterList[clusterNum].lineProb = clusterList[inheritClusterIx].lineProb;
                                clusterList[clusterNum].ageProb = clusterList[inheritClusterIx].ageProb;
                                clusterList[clusterNum].vicProb = clusterList[inheritClusterIx].vicProb;
                                clusterList[clusterNum].shapeProb = clusterList[inheritClusterIx].shapeProb;
                                clusterNum++;
                        }
                        // if there is no nearby cluster, set up a new cluster
                        else {
                            clusterList[clusterNum].x = changeX;
                            clusterList[clusterNum].y = changeY;
                            clusterList[clusterNum].xOld = changeX;
                            clusterList[clusterNum].yOld = changeY;
                            clusterList[clusterNum].xMin = changeX;
                            clusterList[clusterNum].xMax = changeX;
                            clusterList[clusterNum].yMin = changeY;
                            clusterList[clusterNum].yMax = changeY;
                            clusterList[clusterNum].xMinTs = ts;
                            clusterList[clusterNum].xMaxTs = ts;
                            clusterList[clusterNum].yMinTs = ts;
                            clusterList[clusterNum].yMaxTs = ts;
                            clusterList[clusterNum].r = CLUSTER_START_SIZE;
                            clusterList[clusterNum].ts = ts;
                            clusterList[clusterNum].vX = 0;
                            clusterList[clusterNum].vY = 0;
                            clusterList[clusterNum].vXAve = 0;
                            clusterList[clusterNum].ballProb = 0;
                            clusterList[clusterNum].tsGen = ts;
                            clusterList[clusterNum].vProb = 0;
                            clusterList[clusterNum].lineProb = 0;
                            clusterList[clusterNum].ageProb = 0;
                            clusterList[clusterNum].vicProb = 0;
                            clusterList[clusterNum].shapeProb = 0;
                            clusterNum++;
                        }
					}
				}
			}

			// do the velocity estimation and timestamp updates for all the clusters that contain new hough events
            int vEstimateFlag;
            vEstimateFlag = 0;
			for (clusterIx = 0; clusterIx < clusterNum; clusterIx++) {
                clusterX = clusterList[clusterIx].x;
                clusterY = clusterList[clusterIx].y;
                xDif = absFloat(x - clusterX);
                yDif = absFloat(y - clusterY);
                clusterSize = clusterList[clusterIx].r;
                // if the cluster contains the event, update the centroid and relative information
                if((xDif <= clusterSize) && (yDif <= clusterSize)){
                    
                    // update the position of the cluster
                    clusterList[clusterIx].x = (1 - centroidWeight)*clusterX + centroidWeight*x;
                    clusterList[clusterIx].y = (1 - centroidWeight)*clusterY + centroidWeight*y;
                    
                    // update the minimum and maximum position that this cluster has been through
                    if ( clusterList[clusterIx].x > clusterList[clusterIx].xMax){
                        clusterList[clusterIx].xMax = clusterList[clusterIx].x;
                        clusterList[clusterIx].xMaxTs = ts;
                    }
                    if ( clusterList[clusterIx].x < clusterList[clusterIx].xMin){
                        clusterList[clusterIx].xMin = clusterList[clusterIx].x;
                        clusterList[clusterIx].xMinTs = ts;
                    }
                    if ( clusterList[clusterIx].y > clusterList[clusterIx].yMax){
                        clusterList[clusterIx].yMax = clusterList[clusterIx].y;
                        clusterList[clusterIx].yMaxTs = ts;
                    }
                    if ( clusterList[clusterIx].y < clusterList[clusterIx].yMin){
                        clusterList[clusterIx].yMin = clusterList[clusterIx].y;
                        clusterList[clusterIx].yMinTs = ts;
                    }
                    clusterList[clusterIx].ts = ts;
                    
				}
                
                // if after a certain period of time, estimate velocity
                if ((ts-tsOldV) > CLUSTER_V_TS){
                    float vWeightModifier;
                    vEstimateFlag = 1;
                    vWeightModifier = (float)(ts-tsOldV)/(float)CLUSTER_V_TS;
                    // update the velocities of the cluster
                    centroidMoveX = clusterList[clusterIx].x - clusterList[clusterIx].xOld;
                    centroidMoveY = clusterList[clusterIx].y - clusterList[clusterIx].yOld;
                    if (1000 * velocityWeight*centroidMoveY >= 100) {
                        vEstimateFlag = 1;
                    }
                    clusterList[clusterIx].vX = (1 - vWeightModifier*velocityWeight)*clusterList[clusterIx].vX + 1000 * velocityWeight*centroidMoveX;
                    clusterList[clusterIx].vY = (1 - vWeightModifier*velocityWeight)*clusterList[clusterIx].vY + 1000 * velocityWeight*centroidMoveY;
                    clusterList[clusterIx].vXAve = (1 - vWeightModifier*velocityAveWeight)*clusterList[clusterIx].vXAve + 1000 * velocityAveWeight*centroidMoveX;
                    clusterList[clusterIx].xOld = clusterList[clusterIx].x;
                    clusterList[clusterIx].yOld = clusterList[clusterIx].y;
                }
			}
            // if velocity estimation happened, update old timestamp
            if(vEstimateFlag == 1){
                tsOldV = ts;
            }

			// every packet of events with CLUSTER_PERIOD events, iterate through all clusters to merge or delete
			if (eventIx % CLUSTER_PERIOD == 0) {
				ballPruneFlag = 0;
				for (clusterIx = 0; clusterIx < clusterNum; clusterIx++) {
					// prune cluster without sufficient support in CLUSTER_MAX_TS time
                    // ball cluster has a different max time
					if (((clusterIx != ballIx) && ((ts - clusterList[clusterIx].ts) > CLUSTER_MAX_TS))
                            || ((clusterIx == ballIx) && ((ts - clusterList[clusterIx].ts) > CLUSTER_BALL_MAX_TS))) {
						for (i = clusterIx; i < clusterNum-1; i++){
							clusterList[i].x = clusterList[i+1].x;
							clusterList[i].y = clusterList[i+1].y;
                            clusterList[i].xOld = clusterList[i+1].xOld;
                            clusterList[i].yOld = clusterList[i+1].yOld;
                            clusterList[i].xMin = clusterList[i+1].xMin;
                            clusterList[i].xMax = clusterList[i+1].xMax;
                            clusterList[i].yMin = clusterList[i+1].yMin;
                            clusterList[i].yMax = clusterList[i+1].yMax;
                            clusterList[i].xMinTs = clusterList[i+1].xMinTs;
                            clusterList[i].xMaxTs = clusterList[i+1].xMaxTs;
                            clusterList[i].yMinTs = clusterList[i+1].yMinTs;
                            clusterList[i].yMaxTs = clusterList[i+1].yMaxTs;
							clusterList[i].r = clusterList[i+1].r;
							clusterList[i].ts = clusterList[i+1].ts;
							clusterList[i].vX = clusterList[i+1].vX;
							clusterList[i].vY = clusterList[i+1].vY;
							clusterList[i].vXAve = clusterList[i+1].vXAve;
							clusterList[i].ballProb = clusterList[i+1].ballProb;
							clusterList[i].tsGen = clusterList[i+1].tsGen;
							clusterList[i].vProb = clusterList[i+1].vProb;
							clusterList[i].lineProb = clusterList[i+1].lineProb;
							clusterList[i].ageProb = clusterList[i+1].ageProb;
							clusterList[i].vicProb = clusterList[i+1].vicProb;
							clusterList[i].shapeProb = clusterList[i+1].shapeProb;
						}
						clusterNum--;
						// if the ball cluster got pruned, set up a flag for kalman filter to initialize
						if (ballIx == clusterIx) {
							ballPruneFlag = 1;
						}
						// if the ball index is bigger than the cluster that just got pruned, adjust ballIx
						if ((ballIx > clusterIx) && (ballIx != MAX_CLUSTER)) {
							ballIx = ballIx - 1;
						}
						if ((ballIxOld > clusterIx) && (ballIxOld != MAX_CLUSTER)) {
							ballIxOld = ballIxOld - 1;
						}
						// if the cluster is pruned, there is no need to look for merging
						clusterIx--;
						continue;
					}
					// merge clusters that are too close
					clusterX = clusterList[clusterIx].x;
					clusterY = clusterList[clusterIx].y;
					for (clusterIx2 = clusterIx + 1; clusterIx2 < clusterNum; clusterIx2++) {
						clusterX2 = clusterList[clusterIx2].x;
						clusterY2 = clusterList[clusterIx2].y;
						xDif = absFloat(clusterX2 - clusterX);
						yDif = absFloat(clusterY2 - clusterY);
						clusterSize = clusterList[clusterIx].r;
						// if the clusters are touching, merge them with the weights of the sum of hough values
						if((xDif <= MERGE_DISTANCE) && (yDif <= MERGE_DISTANCE)) {
							clusterXRnd = (int)(clusterX);
							clusterYRnd = (int)(clusterY);
							clusterX2Rnd = (int)(clusterX2);
							clusterY2Rnd = (int)(clusterY2);
							//weight1 = (int) mySum((int)myMax(clusterXRnd-clusterSize,0), (int)myMin(clusterXRnd+clusterSize,MAX_X-1), (int)myMax(clusterYRnd-clusterSize,0), (int)myMin(clusterYRnd+clusterSize,MAX_Y-1));
							//weight2 = (int) mySum((int)myMax(clusterX2Rnd-clusterSize,0), (int)myMin(clusterX2Rnd+clusterSize,MAX_X-1), (int)myMax(clusterY2Rnd-clusterSize,0), (int)myMin(clusterY2Rnd+clusterSize,MAX_Y-1));
							//if ((weight1 + weight2) == 0) {
                            clusterList[clusterIx].x = (clusterX + clusterX2) / 2;
                            clusterList[clusterIx].y = (clusterY + clusterY2) / 2;
							//}
							//else {
							//	clusterList[clusterIx].x = ((float)weight1*clusterX + (float)weight2*clusterX2) / (float)(weight1 + weight2);
							//	clusterList[clusterIx].y = ((float)weight1*clusterY + (float)weight2*clusterY2) / (float)(weight1 + weight2);
							//}
							// refresh timestamp
							if (clusterList[clusterIx].ts < clusterList[clusterIx2].ts) {
								clusterList[clusterIx].ts = clusterList[clusterIx2].ts;
							}
                            
                            clusterList[clusterIx].xMin = myMinFloat(clusterList[clusterIx].xMin, clusterList[clusterIx2].xMin);
                            clusterList[clusterIx].xMax = myMaxFloat(clusterList[clusterIx].xMax, clusterList[clusterIx2].xMax);
                            clusterList[clusterIx].yMin = myMinFloat(clusterList[clusterIx].yMin, clusterList[clusterIx2].yMin);
                            clusterList[clusterIx].yMax = myMaxFloat(clusterList[clusterIx].yMax, clusterList[clusterIx2].yMax);
                            clusterList[clusterIx].xMinTs = 0;//myMax(clusterList[clusterIx].xMinTs, clusterList[clusterIx2].xMinTs);
                            clusterList[clusterIx].xMaxTs = 0;//myMax(clusterList[clusterIx].xMaxTs, clusterList[clusterIx2].xMaxTs);
                            clusterList[clusterIx].yMinTs = 0;//myMax(clusterList[clusterIx].yMinTs, clusterList[clusterIx2].yMinTs);
                            clusterList[clusterIx].yMaxTs = 0;//myMax(clusterList[clusterIx].yMaxTs, clusterList[clusterIx2].yMaxTs);
                            clusterList[clusterIx].xOld = clusterList[clusterIx].x;
                            clusterList[clusterIx].yOld = clusterList[clusterIx].y;
                            // pick the velocity that has the smaller absolute value
                            if(absFloat(clusterList[clusterIx].vX) >= absFloat(clusterList[clusterIx2].vX)){
                                clusterList[clusterIx].vX = clusterList[clusterIx2].vX;
                            }
                            if(absFloat(clusterList[clusterIx].vY) >= absFloat(clusterList[clusterIx2].vY)){
                                clusterList[clusterIx].vY = clusterList[clusterIx2].vY;
                            }
                            clusterList[clusterIx].vXAve = myMaxFloat(clusterList[clusterIx].vXAve, clusterList[clusterIx2].vXAve);
                            //clusterList[clusterIx].xMin = clusterList[clusterIx2].xMin/2 + MAX_X/2;
                            //clusterList[clusterIx].xMax = clusterList[clusterIx2].xMax/2;
                            //clusterList[clusterIx].yMin = clusterList[clusterIx2].yMin/2 + MAX_Y/2;
                            //clusterList[clusterIx].yMax = clusterList[clusterIx2].yMax/2;
                            
							// delete the merged cluster
							for (i = clusterIx2; i < clusterNum-1; i++){
								clusterList[i].x = clusterList[i+1].x;
								clusterList[i].y = clusterList[i+1].y;
								clusterList[i].xOld = clusterList[i+1].xOld;
								clusterList[i].yOld = clusterList[i+1].yOld;
								clusterList[i].xMin = clusterList[i+1].xMin;
								clusterList[i].xMax = clusterList[i+1].xMax;
								clusterList[i].yMin = clusterList[i+1].yMin;
								clusterList[i].yMax = clusterList[i+1].yMax;
								clusterList[i].xMinTs = clusterList[i+1].xMinTs;
								clusterList[i].xMaxTs = clusterList[i+1].xMaxTs;
								clusterList[i].yMinTs = clusterList[i+1].yMinTs;
								clusterList[i].yMaxTs = clusterList[i+1].yMaxTs;
								clusterList[i].r = clusterList[i+1].r;
								clusterList[i].ts = clusterList[i+1].ts;
								clusterList[i].vX = clusterList[i+1].vX;
								clusterList[i].vY = clusterList[i+1].vY;
								clusterList[i].vXAve = clusterList[i+1].vXAve;
								clusterList[i].ballProb = clusterList[i+1].ballProb;
								clusterList[i].tsGen = clusterList[i+1].tsGen;
								clusterList[i].vProb = clusterList[i+1].vProb;
								clusterList[i].lineProb = clusterList[i+1].lineProb;
								clusterList[i].ageProb = clusterList[i+1].ageProb;
								clusterList[i].vicProb = clusterList[i+1].vicProb;
								clusterList[i].shapeProb = clusterList[i+1].shapeProb;
							}
							clusterNum--;
							// if the ball cluster is the cluster that has bigger index, adjust ball cluster
							if (ballIx == clusterIx2) {
								ballIx = clusterIx;
							}
                            // or if the ball index is bigger than the cluster that just got pruned, adjust ballIx
                            else if ((ballIx > clusterIx2) && (ballIx != MAX_CLUSTER)) {
                                ballIx = ballIx - 1;
                            }
                            
							if (ballIxOld == clusterIx2) {
								ballIxOld = clusterIx;
							}
                            else if ((ballIxOld > clusterIx2) && (ballIxOld != MAX_CLUSTER)) {
                                ballIxOld = ballIxOld - 1;
                            }
							// go to the next cycle without incrementing clusterIx2
							clusterIx2--;
						}
					}
				}

			}
			
			// deciding which cluster is the ball every few events or the ball cluster got pruned
			if ((eventIx % BALL_REC_PERIOD == 0) || (ballPruneFlag == 1)) {
                int HoughSum;
				clusterIx = 0;
				maxBallProb = 0.3f;
				ballIxOld = ballIx;
                //ballIx = MAX_CLUSTER;
                // precalculate vicinity information if it's not the first ball detection
                /*if (findBallFlag == 1){
                    vicLLX = vicXOld + myMinFloat(vicVXOld*vicXScale, -vicMinRange);
                    vicLLY = vicYOld + myMinFloat(vicVYOld*vicYScale, -vicMinRange);
                    vicURX = vicXOld + myMaxFloat(vicVXOld*vicXScale, vicMinRange);
                    vicURY = vicYOld + myMaxFloat(vicVYOld*vicYScale, vicMinRange);
                }*/
                
				for (clusterIx = 0; clusterIx < clusterNum; clusterIx++) {
					// if the cluster is out of bound, it's not a ball
					if ((clusterList[clusterIx].x <= boundX[0]) || (clusterList[clusterIx].x >= boundX[1]) || (clusterList[clusterIx].y <= boundY[0]) || (clusterList[clusterIx].y >= boundY[1])) {
						clusterList[clusterIx].ballProb = 0;
					}
					// if the cluster is in the bound, calculate the probability
					// ballProb = vWeight*v + lineWeight*min(pos-barX) + ageWeight*clusterAge
					// where v is the recent average velocity of the cluster
					// pos is the x position of the cluster and barX is the x position of the bars
					// clusterAge is how long has the cluster been here
					// vWeight + lineWeight + ageWeight = 1
					else {
						vProb = vWeight*saturate(myMax((int)absFloat(clusterList[clusterIx].vX)-vTh, 0), vLimit);
						/*minDisToBar = MAX_X;
						for (barIx = 0; barIx < BAR_NUM; barIx ++) {
							disToBar = absFloat((float)barX[barIx] - clusterList[clusterIx].x);
							if (disToBar < minDisToBar) {
								minDisToBar = disToBar;
							}
						}*/
                        
                        // find the lineup probability 
                        /*lineCount = 0;
                        lineDis = 0;
                        float lineXDisTemp, lineYDisTemp;
                        for (i = 0; i < clusterNum; i++){
                            // if the Y location is close and it's not the ball itself
                            if(i != clusterIx){
                                lineXDisTemp = absFloat(clusterList[i].x-clusterList[clusterIx].x);
                                lineYDisTemp = absFloat(clusterList[i].y-clusterList[clusterIx].y);
                                if((lineXDisTemp <= LINE_UP_RANGE) && (lineYDisTemp >= LINE_UP_MIN_Y)){
                                    lineDis += lineXDisTemp;
                                    lineCount++;
                                }
                            }
                        }
                        if (lineCount > 0){
                            lineDis = lineDis/(float)lineCount;
                            lineProb = lineWeight*saturate(myMaxFloat(lineCount-lineTh, 0), lineLimit-lineTh);
                        }
                        else{
                            lineProb = lineWeight;
                        }*/
                        lineProb = 0;
						//lineProb = lineWeight*saturate(myMax((int)minDisToBar-lineTh, 0), lineLimit);
                        
                        // find the shape probabilities
                        /*int k, l, kStart, kEnd, lStart, lEnd;
                        float shapeProbSum;
                        shapeProbSum = 0;
                        kStart = -myMin((int)clusterList[clusterIx].x-SHAPE_MAP_SIZE, 0);
                        kEnd = 2*SHAPE_MAP_SIZE-myMax((int)clusterList[clusterIx].x+SHAPE_MAP_SIZE-MAX_X, 0);
                        lStart = -myMin((int)clusterList[clusterIx].y-SHAPE_MAP_SIZE, 0);
                        lEnd = 2*SHAPE_MAP_SIZE-myMax((int)clusterList[clusterIx].y+SHAPE_MAP_SIZE-MAX_Y, 0);
                        for (k = kStart; k < kEnd; k++){
                            for (l = lStart; l < lEnd; l++){
                                if (eventSpace[(int)clusterList[clusterIx].x+k-SHAPE_MAP_SIZE][(int)clusterList[clusterIx].y+l-SHAPE_MAP_SIZE] > 0){
                                    shapeProbSum += shapeProbMap[k][l];
                                }
                            }
                        }
						shapeProb = shapeWeight*shapeProbSum/shapeLimit;*/
                        
                        // decay the maximum and minimum position
                        if ((clusterList[clusterIx].xMin < clusterList[clusterIx].x) && (ts-clusterList[clusterIx].xMinTs >= RANGE_DECAY_TS)){
                            clusterList[clusterIx].xMin += rangeDecayRate;
                            //clusterList[clusterIx].xMin = clusterList[clusterIx].x - rangeDecayRate*absFloat(clusterList[clusterIx].x-clusterList[clusterIx].xMin);
                        }
                        if ((clusterList[clusterIx].xMax > clusterList[clusterIx].x) && (ts-clusterList[clusterIx].xMaxTs >= RANGE_DECAY_TS)){
                            clusterList[clusterIx].xMax -= rangeDecayRate;
                            //clusterList[clusterIx].xMax = clusterList[clusterIx].x + rangeDecayRate*absFloat(clusterList[clusterIx].xMax-clusterList[clusterIx].x);
                        }
                        if ((clusterList[clusterIx].yMin < clusterList[clusterIx].y) && (ts-clusterList[clusterIx].yMinTs >= RANGE_DECAY_TS)){
                            clusterList[clusterIx].yMin += rangeDecayRate;
                            //clusterList[clusterIx].yMin = clusterList[clusterIx].y - rangeDecayRate*absFloat(clusterList[clusterIx].y-clusterList[clusterIx].yMin);
                        }
                        if ((clusterList[clusterIx].yMax > clusterList[clusterIx].y) && (ts-clusterList[clusterIx].yMaxTs >= RANGE_DECAY_TS)){
                            clusterList[clusterIx].yMax -= rangeDecayRate;
                            //clusterList[clusterIx].yMax = clusterList[clusterIx].y + rangeDecayRate*absFloat(clusterList[clusterIx].yMax-clusterList[clusterIx].y);
                        }
                        // find the range probabilities
                        //shapeProb = 0.8f*shapeWeight*saturate(myMaxFloat(clusterList[clusterIx].xMax-clusterList[clusterIx].xMin-shapeTh, 0), shapeLimit-shapeTh)
                        //        +0.2f*shapeWeight*saturate(myMaxFloat(clusterList[clusterIx].yMax-clusterList[clusterIx].yMin-shapeYTh, 0), shapeYLimit-shapeYTh);
                        shapeProb = myMaxFloat(shapeWeight*saturate(myMaxFloat(clusterList[clusterIx].xMax-clusterList[clusterIx].xMin-shapeTh, 0), shapeLimit-shapeTh)
                                , shapeWeight*saturate(myMaxFloat(clusterList[clusterIx].yMax-clusterList[clusterIx].yMin-shapeYTh, 0), shapeYLimit-shapeYTh));
                        
						//ageProb = ageWeight*saturate(ts - clusterList[clusterIx].tsGen, ageLimit);
                        
                        // calculate the hough space probabilities
						clusterSize = 2;//clusterList[clusterIx].r;
                        HoughSum = (int) mySum((int)myMax((int)clusterList[clusterIx].x-clusterSize,0), (int)myMin((int)clusterList[clusterIx].x+clusterSize,MAX_X-1), 
                                (int)myMax((int)clusterList[clusterIx].y-clusterSize,0), (int)myMin((int)clusterList[clusterIx].y+clusterSize,MAX_Y-1));
                        ageProb = ageWeight*saturate(myMax(HoughSum-ageTh, 0), ageLimit-ageTh);
                        
                        // if it's the first ball detection, don't use vicinity information
                        /*if(findBallFlag == 0){
                            vicProb = 0;
                        }
                        // calculate the vicinity probabilities according to previous position and speed
                        else{
                            // if it's in the vicinity 
                            if ((clusterList[clusterIx].x >= vicLLX) && (clusterList[clusterIx].y >= vicLLY)
                                    && (clusterList[clusterIx].x <= vicURX) && (clusterList[clusterIx].y <= vicURY)){
                                vicProb = vicWeight;
                            }
                            else{
                                vicProb = 0;
                            }
                        }*/
                        vicProb = 0;
                        
						clusterList[clusterIx].vProb = vProb;
						clusterList[clusterIx].lineProb = lineProb;
						clusterList[clusterIx].ageProb = ageProb;
						clusterList[clusterIx].vicProb = vicProb;
						clusterList[clusterIx].shapeProb = shapeProb;
						clusterList[clusterIx].ballProb = vProb + lineProb + ageProb + vicProb + shapeProb;

						// find the maximum probabilty
						if (clusterList[clusterIx].ballProb > maxBallProb) {
							maxBallProb = clusterList[clusterIx].ballProb;
							ballIx = clusterIx;
						}
					}
				}
                
                if ((ballIx != MAX_CLUSTER) && (clusterList[ballIx].ballProb <= 0.1f)){
                    ballIx = MAX_CLUSTER;
                }
                
                // if there is no more clusters, no ball
                if(clusterNum == 0){
                    ballIx = MAX_CLUSTER;
                }
                else if(ballIx < MAX_CLUSTER){
                    findBallFlag = 1;
                    // save the location and speeds for vicinity probability calculation
                    vicXOld = clusterList[ballIx].x;
                    vicYOld = clusterList[ballIx].y;
                    vicVXOld = clusterList[ballIx].vX;
                    vicVYOld = clusterList[ballIx].vY;
                }
                else{
                    findBallFlag = 0;
                }
                
				
				// if there are clusters
				/*if (clusterNum != 0) {
					// if it's not the first time trying to find cluster, calculate dT
					if (kfTsOld != 0) {
						kfTsDif = clusterList[ballIx].ts - kfTsOld;
					}
					kfTsOld = clusterList[ballIx].ts;

					// if the ball cluster changed or the ball cluster was pruned, initialize Kalman filter 
					if ((ballIx != ballIxOld) || (ballPruneFlag == 1)) {
					}
					else if (ballIx != MAX_CLUSTER) {

					}
				}*/
			}
            
        tsOld = ts;

		}
        
        if(eventIx >= MAX_EVENT-1){
            eventIx = 0;
        }
        else{
            eventIx++;
        }
		return out;
	}

	@Override
	synchronized public void annotate(GLAutoDrawable drawable) {
        int i, j;
        float HoughColor;
        
		if (!isFilterEnabled()) {
			return;
		}

		GL2 gl = drawable.getGL().getGL2(); // when we getString this we are already set up with updateShape 1=1 pixel,
											// at LL corner
		GLUT cGLUT = chip.getCanvas().getGlut();
        
        if(showHoughSpace){
            gl.glPointSize(5.0f);
            gl.glClear(GL2.GL_COLOR_BUFFER_BIT);
            for (i = 0; i < MAX_X; i++){
                for (j = 0; j < MAX_Y; j++){
                    HoughColor = saturate(myMax(HoughAboveTh[i][j]-HOUGH_TH, 0), HOUGH_MAX-HOUGH_TH);
                    gl.glBegin(GL2.GL_POINTS);
                    gl.glColor3f(HoughColor,HoughColor,HoughColor);
                    gl.glVertex2f(i,j);
                    gl.glEnd();
                }
            }
        }


        //gl.glClear(GL2.GL_COLOR_BUFFER_BIT);
        gl.glLineWidth(5.0f);
        gl.glColor3f(1.0f,0.0f,0.0f);

        //gl.glPolygonMode(GL2.GL_FRONT_AND_BACK, GL2.GL_LINE);
        
        final int font = GLUT.BITMAP_HELVETICA_18;			
        for (i = 0; i < clusterNum; i++){
            if(i == ballIx){
                gl.glColor3f(0.0f,1.0f,0.0f);
                gl.glBegin(GL2.GL_LINE_LOOP);
                gl.glVertex2f(clusterList[i].x-ballRadius,clusterList[i].y-ballRadius);
                gl.glVertex2f(clusterList[i].x-ballRadius,clusterList[i].y+ballRadius);
                gl.glVertex2f(clusterList[i].x+ballRadius,clusterList[i].y+ballRadius);
                gl.glVertex2f(clusterList[i].x+ballRadius,clusterList[i].y-ballRadius);
                gl.glEnd();
                // text annoations on clusters, setup
                if (showClusterNumber) {
                    gl.glRasterPos2f(clusterList[i].x-ballRadius, clusterList[i].y-ballRadius-5);
                    cGLUT.glutBitmapString(font, String.format("%d ", i)); // annotate the cluster with hash ID
                }
                
                if (showVicinity){
                    gl.glColor3f(0.0f,0.5f,0.5f);
                    gl.glBegin(GL2.GL_LINE_LOOP);
                    gl.glVertex2f(vicLLX, vicLLY);
                    gl.glVertex2f(vicLLX, vicURY);
                    gl.glVertex2f(vicURX, vicURY);
                    gl.glVertex2f(vicURX, vicLLY);
                    gl.glEnd();
                }
                
                if (showVelocity){
                    gl.glColor3f(0.0f,0.0f,1.0f);
                    gl.glBegin(GL2.GL_LINES);
                    gl.glVertex2f(clusterList[i].x, clusterList[i].y);
                    gl.glVertex2f(clusterList[i].x+clusterList[i].vX/vLineScale, clusterList[i].y);
                    gl.glVertex2f(clusterList[i].x, clusterList[i].y);
                    gl.glVertex2f(clusterList[i].x, clusterList[i].y+clusterList[i].vY/vLineScale);
                    gl.glEnd();
                }
//                gl.glColor3f(0.0f,1.0f,0.0f);
//                gl.glBegin(GL2.GL_LINES);
//                gl.glVertex2f(clusterList[i].x, clusterList[i].y);
//                gl.glVertex2f(clusterList[i].xMin, clusterList[i].y);
//                gl.glVertex2f(clusterList[i].x, clusterList[i].y);
//                gl.glVertex2f(clusterList[i].xMax, clusterList[i].y);
//                gl.glVertex2f(clusterList[i].x, clusterList[i].y);
//                gl.glVertex2f(clusterList[i].x, clusterList[i].yMin);
//                gl.glVertex2f(clusterList[i].x, clusterList[i].y);
//                gl.glVertex2f(clusterList[i].x, clusterList[i].yMax);
//                gl.glEnd();
            }
            else if (showNonBallCluster){
                gl.glColor3f(1.0f,0.0f,0.0f);
                gl.glBegin(GL2.GL_LINE_LOOP);
                gl.glVertex2f(clusterList[i].x-ballRadius,clusterList[i].y-ballRadius);
                gl.glVertex2f(clusterList[i].x-ballRadius,clusterList[i].y+ballRadius);
                gl.glVertex2f(clusterList[i].x+ballRadius,clusterList[i].y+ballRadius);
                gl.glVertex2f(clusterList[i].x+ballRadius,clusterList[i].y-ballRadius);
                gl.glEnd();
                // text annoations on clusters, setup
                if (showClusterNumber) {
                    gl.glRasterPos2f(clusterList[i].x-ballRadius, clusterList[i].y-ballRadius-5);
                    cGLUT.glutBitmapString(font, String.format("%d ", i)); // annotate the cluster number
                }
                if (showVelocity){
                    gl.glColor3f(0.0f,0.0f,1.0f);
                    gl.glBegin(GL2.GL_LINES);
                    gl.glVertex2f(clusterList[i].x, clusterList[i].y);
                    gl.glVertex2f(clusterList[i].x+clusterList[i].vX/vLineScale, clusterList[i].y);
                    gl.glVertex2f(clusterList[i].x, clusterList[i].y);
                    gl.glVertex2f(clusterList[i].x, clusterList[i].y+clusterList[i].vY/vLineScale);
                    gl.glEnd();
                }
            }
            
            gl.glColor3f(1.0f,0.0f,0.0f);
            // text annoations on clusters, setup
            if (showProbabilities) {
                gl.glRasterPos2f(clusterList[i].x+ballRadius, clusterList[i].y+ballRadius+5);
                cGLUT.glutBitmapString(font, String.format("%.3f ", clusterList[i].ballProb)); // annotate the cluster ball probabilities
                //gl.glRasterPos2f(clusterList[i].x+ballRadius, clusterList[i].y+ballRadius+8);
                //cGLUT.glutBitmapString(font, String.format("%.3f ", clusterList[i].vProb)); // annotate the cluster velocity probabilities
                gl.glRasterPos2f(clusterList[i].x+ballRadius, clusterList[i].y+ballRadius+8);
                cGLUT.glutBitmapString(font, String.format("%.3f ", clusterList[i].ageProb)); // annotate the cluster position probabilities
                gl.glRasterPos2f(clusterList[i].x+ballRadius, clusterList[i].y+ballRadius+12);
                cGLUT.glutBitmapString(font, String.format("%.3f ", clusterList[i].shapeProb)); // annotate the cluster age probabilities
                gl.glRasterPos2f(clusterList[i].x+ballRadius, clusterList[i].y+ballRadius+15);
                cGLUT.glutBitmapString(font, String.format("%.3f ", clusterList[i].vProb)); // annotate the cluster age probabilities
            }
        }


        //gl.glPolygonMode(GL2.GL_FRONT_AND_BACK, GL2.GL_FILL);

	}
    
    @Override
    public void initFilter() {
		int i, j;

		for (i = 0; i < FIFO_LEN; i++){
			FIFO[i] = new DVSEvents();
		}
		
		for (i = 0; i < CIRCLE_DOT; i++){
			HoughChangeOn[i] = new HoughChanges();
		}

		for (i = 0; i < MAX_CLUSTER; i++){
			clusterList[i] = new Clusters();
			clusterListCopy[i] = new Clusters();
		}
        
        FIFOHeadIx = 0;
        FIFOTailIx = 0;
        FIFOSize = 0;
        FIFOPopFlag = 0;
        clusterNum = 0;
        eventIx = 0;
        tsOld = 0;
        tsOldV = 0;
        ballIx = MAX_CLUSTER;
        findBallFlag = 0;
        lastUdpMsgTime = 0;
        udpSequenceCnt = 0;
        
        
        for (i = 0; i < MAX_X; i++){
            for (j = 0; j < MAX_Y; j++){
                HoughSpace[i][j] = 0;
                HoughAboveTh[i][j] = 0;
                eventSpace[i][j] = 0;
            }
        }
        
    }
	
	public class DVSEvents {
		public int ts;
		public int x;
		public int y;
		//public int pol;
	};

	// {'x':center x, 'y':center y, 'r':radius,  'ts':time stamp of the last Hough change, 'vX': X velocity estimation, 'vY': Y velocity estimation
	//  'ballProb': the probability of this cluster being the ball, 'tsGen': the time this cluster was generated, 
	//  'vProb': , 'lineProb':, 'ageProb'}
	public class Clusters {
		public float x;
		public float y;
        public float xOld;
        public float yOld;
        public float xMin;
        public float yMin;
        public float xMax;
        public float yMax;
        public int xMinTs;
        public int yMinTs;
        public int xMaxTs;
        public int yMaxTs;
		public int r;
		public int ts;
		public float vX;
		public float vY;
		public float vXAve;
		public float ballProb;
		public int tsGen;
		public float vProb;
		public float lineProb;
		public float ageProb;
		public float vicProb;
		public float shapeProb;
	};

	public class HoughChanges {
		public int x;
		public int y;
	};
	
	private int HoughChange(int x, int y, int sign) {
		/*
		change Hough space according to events
		sign = 1: increase hough space values
		sign = 0 : decrease hough space values
		*/
		int i, H_x, H_y;
		int changeIx = 0;
		for (i = 0; i < CIRCLE_DOT; i++) {
			H_x = x + xinc[i];
			H_y = y + yinc[i];
			// if it's in the bound of the graph, change hough space
			if ((H_x < MAX_X) && (H_y < MAX_Y) && (H_x >= 0) && (H_y >= 0)) {
				if (sign == 1) {
					HoughSpace[H_x][H_y] += 1;
					// update hough space that is above threshold into HoughAboveTh
					if (HoughSpace[H_x][H_y] > HOUGH_CREATE_TH) {
						HoughAboveTh[H_x][H_y] = HoughSpace[H_x][H_y] - HOUGH_CREATE_TH;
						HoughChangeOn[changeIx].x = H_x;
						HoughChangeOn[changeIx].y = H_y;
						changeIx++;
					}
				}
				else if (sign == 0) {
					HoughSpace[H_x][H_y] -= 1;
					if (HoughSpace[H_x][H_y] >= HOUGH_CREATE_TH) {
						// update hough space that is below threshold into HoughAboveTh
						if (HoughSpace[H_x][H_y] <= HOUGH_CREATE_TH) {
							HoughAboveTh[H_x][H_y] = 0;
						}
					}
				}
			}
		}
		return changeIx;
	}

	private int mySum(int minX, int maxX, int minY, int maxY) {
		/*
		Sum the values in a 2D array
		*/
		int my_sum = 0;
		int myX, myY;
		for (myX = minX; myX <= maxX; myX++) {
			for (myY = minY; myY <= maxY; myY++) {
				my_sum += HoughSpace[myX][myY];
			}
		}
		return my_sum;
	}

	private int myMax(int a, int b) {
		return (a > b) ? a : b;
	}

	private int myMin(int a, int b) {
		return (a > b) ? b : a;
	}
    
	private float myMaxFloat(float a, float b) {
		return (a > b) ? a : b;
	}

	private float myMinFloat(float a, float b) {
		return (a > b) ? b : a;
	}

	private float saturate(float myInput, float limit) {
		float myOutput;
		if (myInput < limit) {
			myOutput = myInput / limit;
		}
		else {
			myOutput = 1;
		}

		return myOutput;
	}

	private float absFloat(float a) {
		return (a > 0) ? a : -a;
	}

	private int absInt(int a) {
		return (a > 0) ? a : -a;
	}
    
	private void FIFOPop() {
        // pop the oldest data
        xOld = FIFO[FIFOHeadIx].x;
        yOld = FIFO[FIFOHeadIx].y;
        // subtract Hough space values 
        temp = HoughChange(xOld, yOld, 0);
        eventSpace[xOld][yOld]--;
        // "pop" the oldest item by increasing head index
        FIFOHeadIx++;
        if (FIFOHeadIx >= FIFO_LEN) {
            FIFOHeadIx = 0;
        }
        FIFOSize--;
	}
    
	@Override
	public void update(Observable o, Object arg) {
		if (o == this) {
			UpdateMessage msg = (UpdateMessage) arg;
		}
		else if (o instanceof AEChip) {
			initFilter();
		}
	}   
    
    /**
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * @param host the host to set
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * @param port the port to set
     */
    public void setPort(int port) {
        this.port = port;
        putInt("port", port);
    }

    
    // <editor-fold defaultstate="collapsed" desc="getter/setter for --ShowClusterNumber--">
	/**
	 * @return the showClusterNumber
	 */
	public boolean isShowClusterNumber() {
		return showClusterNumber;
	}

	/**
	 * @param showClusterNumber
	 *            the showClusterNumber to set
	 */
	public void setShowClusterNumber(boolean showClusterNumber) {
		this.showClusterNumber = showClusterNumber;
		putBoolean("showClusterNumber", showClusterNumber);
	}
    
	// <editor-fold defaultstate="collapsed" desc="getter/setter for --ShowClusterNumber--">
	/**
	 * @return the showNonBallCluster
	 */
	public boolean isShowNonBallCluster() {
		return showNonBallCluster;
	}

	/**
	 * @param showNonBallCluster
	 *            the showClusterNumber to set
	 */
	public void setShowNonBallCluster(boolean showNonBallCluster) {
		this.showNonBallCluster = showNonBallCluster;
		putBoolean("showClusterNumber", showNonBallCluster);
	}
        
	// <editor-fold defaultstate="collapsed" desc="getter/setter for --showProbabilities--">
	/**
	 * @return the showProbabilities
	 */
	public boolean isShowProbabilities() {
		return showProbabilities;
	}

	/**
	 * @param showProbabilities
	 *            the showProbabilities to set
	 */
	public void setShowProbabilities(boolean showProbabilities) {
		this.showProbabilities = showProbabilities;
		putBoolean("showProbabilities", showProbabilities);
	}
            
	// <editor-fold defaultstate="collapsed" desc="getter/setter for --showVelocity--">
	/**
	 * @return the showVelocity
	 */
	public boolean isShowVelocity() {
		return showVelocity;
	}

	/**
	 * @param showVelocity
	 *            the showVelocity to set
	 */
	public void setShowVelocity(boolean showVelocity) {
		this.showVelocity = showVelocity;
		putBoolean("showVelocity", showVelocity);
	}
                
	// <editor-fold defaultstate="collapsed" desc="getter/setter for --showVicinity--">
	/**
	 * @return the showVicinity
	 */
	public boolean isShowVicinity() {
		return showVicinity;
	}

	/**
	 * @param showVicinity
	 *            the showVicinity to set
	 */
	public void setShowVicinity(boolean showVicinity) {
		this.showVicinity = showVicinity;
		putBoolean("showVicinity", showVicinity);
	}
                    
	// <editor-fold defaultstate="collapsed" desc="getter/setter for --showHoughSpace--">
	/**
	 * @return the showHoughSpace
	 */
	public boolean isShowHoughSpace() {
		return showHoughSpace;
	}

	/**
	 * @param showHoughSpace
	 *            the showHoughSpace to set
	 */
	public void setShowHoughSpace(boolean showHoughSpace) {
		this.showHoughSpace = showHoughSpace;
		putBoolean("showHoughSpace", showHoughSpace);
	}
    
    /**
	 * @return the sendITD_UDP_port
	 */
	public String getSendITD_UDP_port() {
		return sendITD_UDP_port;
	}

	/**
	 * @param sendITD_UDP_port the sendITD_UDP_port to set
	 */
	public final void setSendITD_UDP_port(String sendITD_UDP_port) { // TODO call in constructor
		try {
			String[] parts = sendITD_UDP_port.split(":");
			if (parts.length != 2) {
				log.warning(sendITD_UDP_port + " is not a valid hostname:port address");
				return;
			}
			String host = parts[0];
			try {
				int port = Integer.parseInt(parts[1]);
				client = new InetSocketAddress(host, port);
			} catch (NumberFormatException e) {
				log.warning(parts[1] + " is not a valid port number in " + sendITD_UDP_port);
				return;
			}
			this.sendITD_UDP_port = sendITD_UDP_port;
			putString("sendITD_UDP_port", sendITD_UDP_port);
			log.info("set client to "+client);
		} catch (Exception e) {
			log.warning("caught exception " + e.toString());
		}
	}

	/**
	 * @return the sendITD_UDP_Messages
	 */
	public boolean isSendITD_UDP_Messages() {
		return sendITD_UDP_Messages;
	}

	/**
	 * @param sendITD_UDP_Messages the sendITD_UDP_Messages to set
	 */
	public final synchronized void setSendITD_UDP_Messages(boolean sendITD_UDP_Messages) { // TODO call this in constructor to set up socket
		boolean old = this.sendITD_UDP_Messages;
		if (sendITD_UDP_Messages) {
			try {
				channel = DatagramChannel.open();
				socket = channel.socket(); // bind to any available port because we will be sending datagrams with included host:port info
				this.sendITD_UDP_Messages = sendITD_UDP_Messages;
				putBoolean("sendITD_UDP_Messages", sendITD_UDP_Messages);
				packetSequenceNumber=0;

			} catch (IOException ex) {
				log.warning("couldn't get datagram channel: " + ex.toString());
			}

		} else {
			this.sendITD_UDP_Messages=sendITD_UDP_Messages;
			if(socket!=null) {
				socket.close();
			}
			try {
				if(channel!=null) {
					channel.close();
				}
			} catch (IOException ex) {
				log.warning(ex.toString());
			}
		}
		getSupport().firePropertyChange("sendITD_UDP_Messages", old, this.sendITD_UDP_Messages);
		printedFirstUdpMessage=false;
	}

}
