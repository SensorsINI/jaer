/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
/*
 * To execute this code, you need to
 * 1. download weka.jar and wekasrc.jar
 * 2. add the two jar files to the class path
 * 3. make comments start with &&From to &&To to be active
 */
package ch.unizh.ini.jaer.projects.gesture.RockScissorPaper;


import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;


import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.BasicEvent;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.FrameAnnotater;
/* &&From
 import weka.core.Instance;
 * &&To
 */

import com.jogamp.opengl.util.gl2.GLUT;

/**
 *
 * @author Eun Yeong Ahn
 */
public class RockScissorPaper extends EventFilter2D implements FrameAnnotater, Observer /*, PreferenceChangeListener*/ {
	//private boolean enableSwapxy=getPrefs().getBoolean("Gawi.enableSwapxy", false);
	// protected AEChip mychip;
	private int movingThreshold = getPrefs().getInt("RockScissorPaper.movingThreshold", 3000);
	private int directionWindow = getPrefs().getInt("RockScissorPaper.directionWindow", 5);
	private int globalFilterBin = getPrefs().getInt("RockScissorPaper.globalFilterBin", 10);
	private int localFilterBin = getPrefs().getInt("RockScissorPaper.localFilterBin", 20);
	private float globalFilterThreshold = getPrefs().getFloat("RockScissorPaper.globalFilterThreshold", 0.01f);
	private float localFilterThreshold = getPrefs().getFloat("RockScissorPaper.localFilterThreshold", 0.005f);
	private int removeWristBin = getPrefs().getInt("RockScissorPaper.removeWristBin", 10);
	private int decreaseThreshold = getPrefs().getInt("RockScissorPaper.decreaseThreshold", 2);
	private int removeWristBinSecond = getPrefs().getInt("RockScissorPaper.removeWristBin", 10);
	private int decreaseThresholdSecond = getPrefs().getInt("RockScissorPaper.decreaseThreshold", 2);
	private boolean dualDirectionWristFilter = getPrefs().getBoolean("RockScissorPaper.dualDirectionWristFilter", false);
	private boolean secondWristFilter = getPrefs().getBoolean("RockScissorPaper.secondWristFilter", false);

	private boolean printArff = getPrefs().getBoolean("RockScissorPaper.printArff", false);
	private boolean learning = getPrefs().getBoolean("RockScissorPaper.learning", false);
	// private boolean initModel = getPrefs().getBoolean("RockScissorPaper.initModel", false);
	private boolean updateModel = getPrefs().getBoolean("RockScissorPaper.initModel", false);
	private boolean clearModel = getPrefs().getBoolean("RockScissorPaper.clearModel", false);
	private boolean classification = getPrefs().getBoolean("RockScissorPaper.classification", false);
	private boolean play = getPrefs().getBoolean("RockScissorPaper.play", false);
	private int classType = getPrefs().getInt("RockScissorPaper.classType", 0);


	private int noOfInstances = getPrefs().getInt("RockScissorPaper.noOfInstances", 0);


	Boolean m_isAction = false;
	Boolean m_isUpdate = false;

	//track y axis
	ArrayList m_dirWindow = new ArrayList();

	//File output
	Boolean m_fileOut = true;
	int m_noOfFeatures = (2*CParameter.YDistFilterBin)+1;// + CParameter.YDistAvgBin;

	CData m_training = null;
	int m_lastUpdate = 0;

	//Learning Model
	CLearningModel model = new CLearningModel();
	int predictClassType = -1;
	EventPacket<?> filteredPoints = new EventPacket();
	EventPacket<?> handPoints = new EventPacket();
	EventPacket<?> fingerPoints = new EventPacket();

	public RockScissorPaper(AEChip chip) {
		super(chip);
		chip.addObserver(this);
		addObserver(this);
		/* &&From
        model.BuildModel(CParameter.TrainArff);
		 * &&To
		 */
		/****************************************************************************
		 * Parameter Setting
		 */
		final String toPlay = "Game";
		setPropertyTooltip(toPlay, "play", "to play");

		final String actionDetection = "Action Detection";
		setPropertyTooltip(actionDetection, "movingThreshold", "If the number of points is under this value, the state is regarded as bounding position.");
		setPropertyTooltip(actionDetection, "directionWindow", "Moving direction is predicted by majority voting of the direction in the window");

		final String filter = "Filter";
		setPropertyTooltip(filter, "globalFilterBin", "The number of bins used for global filtering");
		setPropertyTooltip(filter, "globalFilterThreshold", "The grid whose data points are less than total number of points * threshold are removed");
		setPropertyTooltip(filter, "localFilterBin", "The number of bins used for local filtering");
		setPropertyTooltip(filter, "localFilterThreshold", "The grid whose data points are less than total number of points * threshold are removed");

		final String extractor = "Feature Extractor";
		setPropertyTooltip(extractor, "removeWristBin", "The number of bins used to remove wrist");
		setPropertyTooltip(extractor, "decreaseThreshold", "the number of decrease points to detect the wrist");
		setPropertyTooltip(extractor, "removeWristBinSecond", "The number of bins used to remove wrist");
		setPropertyTooltip(extractor, "decreaseThresholdSecond", "the number of decrease points to detect the wrist");
		setPropertyTooltip(extractor, "dualDirectionWristFilter", "left to right & right to left wrist filter");
		setPropertyTooltip(extractor, "secondWristFilter", "left to right & right to left wrist filter");



		final String learning = "Learning";
		//    setPropertyTooltip(learning, "initModel", "initialize model");
		setPropertyTooltip(learning, "clearModel", "clear model");
		setPropertyTooltip(learning, "classification", "classification");
		setPropertyTooltip(learning, "learning", "Learning");
		setPropertyTooltip(learning, "updateModel", "update model");
		setPropertyTooltip(learning, "classType", "Rock(0), Scissor(1), Paper(2)");
		setPropertyTooltip(learning, "printArff", "printArff File");
		setPropertyTooltip(learning, "noOfInstances", "no of instances per class");
	}



	@Override
	public EventPacket<?> filterPacket(EventPacket<?> in) {
		boolean updatedCells = false;
		int lastTime = 0;

		DetectBounce(in); // at least once per packet update list
		/* &&From
        if(clearModel)
            model.ClearModel();


       // if(initModel)


        if(m_training == null && printArff)
            m_training = new CData(m_noOfFeatures +1, noOfInstances);
        if(!printArff)
            m_training = null;


        if(m_isAction && m_fileOut){
            for(BasicEvent ev:in){
                lastTime = ev.timestamp;
                updatedCells = maybeCallUpdateObservers(in, lastTime);
                if(updatedCells){
                    break;
                }
                m_lastUpdate = ev.timestamp;
            }
        }

        maybeCallUpdateObservers(in, in.getLastTimestamp());
		 * &&To
		 */
		return in;
	}

	@Override
	public void resetFilter() {
	}

	@Override
	public void initFilter() {

	}

	@Override
	public void annotate(GLAutoDrawable drawable) {
		m_isUpdate = false;
		GL2 gl=drawable.getGL().getGL2();
		int font = GLUT.BITMAP_HELVETICA_18;
		gl.glColor3f (1.0f, 1.0f, 0.0f);
		gl.glPointSize(2);

		for(BasicEvent ev:filteredPoints){
			gl.glBegin(GL.GL_POINTS);
			gl.glVertex2f(ev.x,ev.y);
			gl.glEnd();
		}

		gl.glPointSize(2);
		gl.glColor3f (0.0f, 1.0f, 1.0f);
		for(BasicEvent ev:handPoints){
			gl.glBegin(GL.GL_POINTS);
			gl.glVertex2f(ev.x,ev.y);
			gl.glEnd();
		}
		gl.glPointSize(2);
		gl.glColor3f (1.0f, 0.0f, 0.0f);
		for(BasicEvent ev:fingerPoints){
			gl.glBegin(GL.GL_POINTS);
			gl.glVertex2f(ev.x,ev.y);
			gl.glEnd();
		}

		gl.glColor3f (1.0f, 1.0f, 0.0f);
		if(learning) {
			chip.getCanvas().getGlut().glutBitmapString(font,String.format(GetAction(classType)));
		}
		else if(play) {
			chip.getCanvas().getGlut().glutBitmapString(font,String.format(GetWin(predictClassType)));
		}
		else {
			chip.getCanvas().getGlut().glutBitmapString(font,String.format(GetAction(predictClassType)));
		}
	}

	@Override
	public void update(Observable o, Object arg) {
		if (o == this) {
			if(!m_isAction || !m_fileOut){
				return;
			}
			UpdateMessage msg = (UpdateMessage) arg;

			// Extract events that has not been considered so far
			BasicEvent[] subEvents = new BasicEvent[msg.packet.getSize()];
			int subEventsIndex = 0;
			for(BasicEvent ev:(EventPacket<?>) msg.packet){
				if(ev.getTimestamp() <= msg.timestamp){
					subEvents[subEventsIndex++] = ev;
				}
				if(ev.getTimestamp() > msg.timestamp){
					break;
				}
			}
			EventPacket subPacket = new EventPacket();
			subPacket.setElementData(subEvents);
			subPacket.setSize(subEventsIndex);

			// Extract Features
			/* &&F
            if(subPacket.getSize() > 500)
                ExtractFeatures(subPacket);
            &&To */
		} else if (o instanceof AEChip) {
			initFilter();
		}
	}

	//###########################################################################   Parameters

	public boolean isplay() {
		return play;
	}
	public void setplay(boolean play) {
		this.play = play;
		getPrefs().putBoolean("RockScissorPaper.play", play);
	}

	public boolean isprintArff() {
		return printArff;
	}
	public void setprintArff(boolean printArff) {
		this.printArff = printArff;
		getPrefs().putBoolean("RockScissorPaper.printArff", printArff);
	}

	public boolean isupdateModel() {
		return updateModel;
	}
	public void setupdateModel(boolean updateModel) {
		this.updateModel = updateModel;
		getPrefs().putBoolean("RockScissorPaper.updateModel", updateModel);
	}

	public boolean isclassification() {
		return classification;
	}
	public void setclassification(boolean classification) {
		this.classification = classification;
		getPrefs().putBoolean("RockScissorPaper.classification", classification);
	}

	public boolean islearning() {
		return learning;
	}
	public void setlearning(boolean learning) {
		this.learning = learning;
		getPrefs().putBoolean("RockScissorPaper.learning", learning);
	}
	public boolean isdualDirectionWristFilter() {
		return dualDirectionWristFilter;
	}
	public void setdualDirectionWristFilter(boolean dualDirectionWristFilter) {
		this.dualDirectionWristFilter = dualDirectionWristFilter;
		getPrefs().putBoolean("RockScissorPaper.dualDirectionWristFilter", dualDirectionWristFilter);
	}
	public boolean issecondWristFilter() {
		return secondWristFilter;
	}
	public void setsecondWristFilter(boolean secondWristFilter) {
		this.secondWristFilter = secondWristFilter;
		getPrefs().putBoolean("RockScissorPaper.secondWristFilter", secondWristFilter);
	}

	/* public boolean isinitModel() {
        return initModel;
    }
    public void setinitModel(boolean initModel) {
        this.initModel = initModel;
        getPrefs().putBoolean("RockScissorPaper.initModel", initModel);
    }*/

	public boolean isclearModel() {
		return clearModel;
	}
	public void setclearModel(boolean clearModel) {
		this.clearModel = clearModel;
		getPrefs().putBoolean("RockScissorPaper.clearModel", clearModel);
	}

	public int getclassType() {
		return classType;
	}
	public void setclassType(int classType) {
		this.classType = classType;
		getPrefs().putInt("RockScissorPaper.classType", classType);
	}

	public int getnoOfInstances() {
		return noOfInstances;
	}
	public void setnoOfInstances(int noOfInstances) {
		this.noOfInstances = noOfInstances;
		getPrefs().putInt("RockScissorPaper.noOfInstances", noOfInstances);
	}

	public int getmovingThreshold() {
		return movingThreshold;
	}
	public void setmovingThreshold(int movingThreshold) {
		this.movingThreshold = movingThreshold;
		getPrefs().putInt("RockScissorPaper.movingThreshold", movingThreshold);
	}
	public int getdirectionWindow() {
		return directionWindow;
	}
	public void setdirectionWindow(int directionWindow) {
		this.directionWindow = directionWindow;
		getPrefs().putInt("RockScissorPaper.directionWindow", directionWindow);
	}
	public int getlocalFilterBin() {
		return localFilterBin;
	}
	public void setlocalFilterBin(int localFilterBin) {
		this.localFilterBin = localFilterBin;
		getPrefs().putInt("RockScissorPaper.localFilterBin", localFilterBin);
	}
	public int getglobalFilterBin() {
		return globalFilterBin;
	}
	public void setglobalFilterBin(int globalFilterBin) {
		this.globalFilterBin = globalFilterBin;
		getPrefs().putInt("RockScissorPaper.globalFilterBin", globalFilterBin);
	}
	public float getlocalFilterThreshold() {
		return localFilterThreshold;
	}
	public void setlocalFilterThreshold(float localFilterThreshold) {
		this.localFilterThreshold = localFilterThreshold;
		getPrefs().putFloat("RockScissorPaper.localFilterThreshold", localFilterThreshold);
	}
	public float getglobalFilterThreshold() {
		return globalFilterThreshold;
	}
	public void setglobalFilterThreshold(float globalFilterThreshold) {
		this.globalFilterThreshold = globalFilterThreshold;
		getPrefs().putFloat("RockScissorPaper.globalFilterThreshold", globalFilterThreshold);
	}
	public int getremoveWristBin() {
		return removeWristBin;
	}
	public void setremoveWristBin(int removeWristBin) {
		this.removeWristBin = removeWristBin;
		getPrefs().putInt("RockScissorPaper.removeWristBin", removeWristBin);
	}
	public int getremoveWristBinSecond() {
		return removeWristBinSecond;
	}
	public void setremoveWristBinSecond(int removeWristBinSecond) {
		this.removeWristBinSecond = removeWristBinSecond;
		getPrefs().putInt("RockScissorPaper.removeWristBinSecond", removeWristBinSecond);
	}

	public int getdecreaseThreshold() {
		return decreaseThreshold;
	}
	public void setdecreaseThreshold(int decreaseThreshold){
		this.decreaseThreshold = decreaseThreshold;
		getPrefs().putInt("RockScissorPaper.decreaseThreshold", decreaseThreshold);
	}

	public int getdecreaseThresholdSecond() {
		return decreaseThresholdSecond;
	}
	public void setdecreaseThresholdSecond(int decreaseThresholdSecond){
		this.decreaseThresholdSecond = decreaseThresholdSecond;
		getPrefs().putInt("RockScissorPaper.decreaseThresholdSecond", decreaseThresholdSecond);
	}


	/*****************************************************************
	 * Bounce position detection
	 *****************************************************************/
	private void DetectBounce(EventPacket<?> in) {
		if(in.getSize() > movingThreshold){   //hand is moving
			int idxHalf = (int)Math.floor(in.getSize()/2);
			int xMeanF = 0, xMeanL = 0;
			int idx = 0;
			for(BasicEvent ev:in){
				if(idx < idxHalf){
					xMeanF += ev.y;
				}else{
					xMeanL += ev.y;
				}
				idx++;
			}
			xMeanF = xMeanF / idxHalf;
			xMeanL = xMeanL / (in.getSize() - idxHalf);

			int movingDir = 0;
			if((xMeanF - xMeanL) > 0){
				movingDir = -1;
			}else if((xMeanF - xMeanL) < 0){
				movingDir = 1;
			}
			AddDirectionQ(movingDir);
		}else if((in.getSize() <= movingThreshold) && (GetMovingDir() == -1)){// hands not moving
			m_isAction = true;
			m_isUpdate = true;
			ClearDirectionQ();
		}
	}

	private void AddDirectionQ(int m_movingDir) {
		if(m_dirWindow.size() >= directionWindow){
			m_dirWindow.remove(0);
		}
		m_dirWindow.add(m_movingDir);
	}

	private int GetMovingDir() {
		if(m_dirWindow.size() < directionWindow){  //insufficient size of history to determine the moving direction
			return -2;
		}

		int dir = 0;
		Iterator iter = m_dirWindow.iterator();
		while(iter.hasNext()){
			dir = dir + ((Integer)iter.next()).intValue();
		}
		if(dir != 0)
		{
			return dir/Math.abs(dir); //return -1 or 1;
		}

		return 0;
	}

	private void ClearDirectionQ() {
		m_dirWindow.clear();
	}

	/* &&From
    private void ExtractFeatures(EventPacket<?> in) {
        m_isAction = false;
        try {
           Thread.sleep((long) (1 * 1000));
        } catch (InterruptedException ex) {
            Logger.getLogger(RockScissorPaper.class.getName()).log(Level.SEVERE, null, ex);
        }


        int c_value = getclassType();                                //input class


        // Filtering
        CGridFilter gridFilter = new CGridFilter(in);       //global filter
        in = gridFilter.Filter(globalFilterBin,globalFilterBin, globalFilterThreshold);

        CGridFilter gridFilter2 = new CGridFilter(in);      //local filter
        in = gridFilter.Filter(localFilterBin,localFilterBin, localFilterThreshold);

        filteredPoints.clear();
        filteredPoints = in;

        // Remove data based on x or y distance
        CDistanceFilter handFilter = new CDistanceFilter();
        CDistanceFilter xDistFilter = new CDistanceFilter();
        CDistanceFilter yDistFilter = new CDistanceFilter();

        // remove wrist
        handFilter.HandDiscretization(in, EBaseAxis.Y, removeWristBin, decreaseThreshold, dualDirectionWristFilter);
        //in = handFilter.GetHand();
        in = handFilter.GetFinger();

        if(secondWristFilter){
            handFilter.HandDiscretization(in, EBaseAxis.Y, removeWristBinSecond, decreaseThresholdSecond, dualDirectionWristFilter);
            //in = handFilter.GetHand();
            in = handFilter.GetFinger();
        }

        handPoints.clear(); //fingerPoints.clear();
        handPoints = handFilter.GetHand();
        fingerPoints = handFilter.GetFinger();

        // print out data
        //CCreateActionArff arffOut2 = new CCreateActionArff();
        //arffOut2.CreateArff(in, c_value);

        // Generate an instance
        Instance instance = null;// = new Instance();
        try {
            if(CParameter.DistAxis == EBaseAxis.XY){
                xDistFilter.Filter(in, EBaseAxis.X, CParameter.YDistFilterBin);
                yDistFilter.Filter(in, EBaseAxis.Y, CParameter.YDistFilterBin);
                Instance tmp1 = yDistFilter.GetInstance(c_value);
                Instance tmp2 = xDistFilter.GetInstance(c_value);

                CXYRatioFilter xyRatioFilter = new CXYRatioFilter();
                //xyRatioFilter.FilterByVariance(handFilter.GetHand());
                xyRatioFilter.FilterByVariance(handFilter.GetFinger());
                Instance tmp3 = xyRatioFilter.GetInstance(c_value);


                CInstanceOpr opr = new CInstanceOpr();
                 //instance = opr.MergeInstance(tmp1, tmp2);
                instance = opr.MergeInstance(opr.MergeInstance(tmp1, tmp2), tmp3);

            }else if(CParameter.DistAxis == EBaseAxis.X){
                xDistFilter.Filter(in, EBaseAxis.X, CParameter.YDistFilterBin);
                instance = yDistFilter.GetInstance(c_value);
            }else if(CParameter.DistAxis == EBaseAxis.Y){
                yDistFilter.Filter(in, EBaseAxis.Y, CParameter.YDistFilterBin);
                Instance tmp1 = yDistFilter.GetInstance(c_value);

                CXYRatioFilter xyRatioFilter = new CXYRatioFilter();
                xyRatioFilter.Filter(in);
                Instance tmp2 = xyRatioFilter.GetInstance(c_value);

                CInstanceOpr opr = new CInstanceOpr();
                instance = opr.MergeInstance(tmp1, tmp2);
            }
        } catch (Exception ex) {
            Logger.getLogger(RockScissorPaper.class.getName()).log(Level.SEVERE, null, ex);
        }

        if(classification || play)
            predictClassType = (int) model.PredictModel(instance);

        if(updateModel){
//          System.out.println(instance);
            model.UpdateModel(instance);
        }
        if(printArff){
            System.out.println(instance);
            m_training.AddInstance(instance, c_value);
            if(m_training.IsNoInstances()){
                setprintArff(false);
                m_training.Print();
                m_training = null;
            }
        }
    }
     &&To*/

	private String GetAction(double c_value) {
		if(c_value == 0) {
			return "Rock";
		}
		else if(c_value == 1) {
			return "Scissor";
		}
		else if(c_value == 2) {
			return "Paper";
		}

		return "";
	}

	private String GetWin(double c_value) {
		if(c_value == 0) {
			return "Paper";
		}
		else if(c_value == 1) {
			return "Rock";
		}
		else if(c_value == 2) {
			return "Scissor";
		}

		return "";
	}
}

