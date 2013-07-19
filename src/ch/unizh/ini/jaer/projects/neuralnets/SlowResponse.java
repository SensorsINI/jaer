/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.unizh.ini.jaer.projects.neuralnets;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFileChooser;
import javax.swing.JPanel;

import jspikestack.AxonSparse;
import jspikestack.EngineeringFormat;
import jspikestack.KernelMaker2D;
import jspikestack.KernelMaker2D.FloatConnection;
import net.sf.jaer.Description;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.EventFilter2D;
import net.sf.jaer.graphics.ImageDisplay;

/**
 *
 * @Desc This filter attempts to fill in background shading information by 
 * remembering the most recent events that passed.  For instance, if an edge passes,
 * indicating a transition from light to dark, and you've got no on events since 
 * then, it's a good assumption that things are still dark.
 * 
 * The implementation is reasonable biologically plausible.  It uses an array of
 * Leaky Integrate-and-Fire neurons (one neuron per pixel).  The recent
 * shading information is stored in the form of slowly post-synaptic currents.  
 * When an edge passes, it adds to these currents.  The currents continue to 
 * stimulate a neuron long after the edge passes.
 * 
 * 
 * @author Peter O'Connor
 */
@Description("Reconstructs an Estimation of the Background Light level based on the streams of input events as a lowpass filter.")
public class SlowResponse extends EventFilter2D {
    
    int dimx;
    int dimy;
    
    private float contrast=1;
    
    ImageDisplay im;
    
    public float[] state1;
    public float[] state2;
    public int[] lastUpdateTime;
    
    private boolean applyLateralKernel=true;
    private boolean applyForwardKernel=false;
    
    public LoneKernelController forwardKernelControl;
    public LoneKernelController lateralKernelControl;
    
//    private float smoothingFactor=0.5f;
    
    private boolean built=false;
    
//    private int kernelWidth=3;
    
    private float timeConst= getFloat("timeConst", 500000);
    
    AxonSparse.KernelController kernelControl;
    
    
    DataOutputStream dos;
    public boolean recordToFile;
        
    float[][] fwdKernel;
    int[][] fwdTargets;
    float[][] autoKernel;
    int[][] autoTargets;
        
    boolean isTimeInitialized=false;
            
    public float getTimeConst() {
        return timeConst;
    }

    public void setTimeConst(float tcEPSC) {
        this.timeConst = tcEPSC;
        updateEpscScale();
    }
   
    
    float epscDecayRate;
    
    /** We'd like to make it so each epsc, everall, adds has an area of 1 under it. */
    public void updateEpscScale()
    {   epscDecayRate=1/timeConst;        
    }
    
    public void updateState(int toTime)
    {
        for (int i=0; i<state1.length; i++)
        {
            updateState(toTime,i);
        }       
        
        if (isApplyLateralKernel())
        {
            KernelMaker2D.weightMult(state1, autoKernel, autoTargets, state2);
            
            float[] swap=state1;            
            state1=state2;
            state2=swap;
        }
        
        
        
    }
    
    public void updateState(int toTime,int ixUnit)
    {
        state1[ixUnit]*=Math.exp((lastUpdateTime[ixUnit]-toTime)/timeConst);
        lastUpdateTime[ixUnit]=toTime;
    }
    
    public void fireEventToUnit(PolarityEvent evin)
    {
        // Old system: Fire directly to unit
        if(applyForwardKernel)
        {
            int ix=evin.y+dimy*evin.x;        
            for (int i=0; i<fwdTargets[ix].length;i++)
            {   updateState(evin.timestamp,fwdTargets[ix][i]);
                state1[ix]+=evin.getPolarity()==PolarityEvent.Polarity.On?fwdKernel[ix][i]:-fwdKernel[ix][i];
            }
        }
        else
        {
            int addr=dim2addr(evin.x,evin.y);
            updateState(evin.timestamp,addr);
//            state1[addr]+=evin.getPolarity()==PolarityEvent.Polarity.On?epscDecayRate:-epscDecayRate;
            state1[addr]+=evin.getPolarity()==PolarityEvent.Polarity.On?1:-1;
        }
        // New System: Fire through kernel.
        
        
        
    }
                
    public SlowResponse(AEChip chip)
    {   super(chip);
    
    
        setPropertyTooltip("thresh", "Firing threshold of slow-response neurons");
        setPropertyTooltip("tcEPSC", "Time-Constant of the EPSCs, in microseconds.  Longer TC means shading is more persistent");
        setPropertyTooltip("tcMem", "Time-Constant of the neuron membrane potentials, in microseconds.");
        setPropertyTooltip("eventBased", "Generate events indicating slow-response activity.");
    
        updateEpscScale();
    }
    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        
        
        
       
        // Check if Built
        if (!isBuilt())
            return in;
        else if (!isTimeInitialized) // Check if initialized
        {   if (in.isEmpty())
                return in;     
            initNeuronsOnTimeStamp(in.getFirstTimestamp());    
            isTimeInitialized=true;    
        }
        
        
        
        // Fire Events
        for (Object ev:in)
        {   fireEventToUnit((PolarityEvent)ev);
        }
        
//        if (in.isEmpty()) 
//            return out;
        
           
        // Update the state and display
        int time=in.getLastTimestamp();
        updateState(time);
        updateDisplay();
        
        // If requested, write to file.
        if (recordToFile)
            this.writeCurrentFrame();
        
        
        return in;
        
    }
    
    public boolean isBuilt()
    {
        return built;
    }

    @Override
    public void resetFilter() {
        removeDisplays();
        
        if (isTimeInitialized)
            build();
        
        isTimeInitialized=false;
    }
    
    public void doStartDisplay()
    {
        build();
        
        initDisplay();
        
    }
       
    
    public void build()
    {
        
        dimx=getChip().getSizeX();
        dimy=getChip().getSizeY();
        
        int nUnits=dimx*dimy;
        
        state1=new float[nUnits];
        state2=new float[nUnits];
        lastUpdateTime=new int[nUnits];
               
        buildKernel();
        
        built=true;
        
    }
    
    public void buildKernel()
    {
        // Lateral Kernel
        KernelMaker2D.Gaussian kern=new KernelMaker2D.Gaussian();
        kern.majorWidth=1f;
        if (lateralKernelControl==null)
            lateralKernelControl=new LateralKernelController();
        lateralKernelControl.setKernelControl(kern, 3, 3);
        lateralKernelControl.doApply_Kernel();
        this.addControls(lateralKernelControl.getControl());
        
        // Forward Kernel
        KernelMaker2D.MexiHat kernf=new KernelMaker2D.MexiHat();
        kernf.mag=1;
        kernf.ratio=3;
        kernf.majorWidth=3;
        if (forwardKernelControl==null)
            forwardKernelControl=new ForwardKernelController();
        forwardKernelControl.setKernelControl(kernf, 5, 5);
        forwardKernelControl.doApply_Kernel();
        this.addControls(forwardKernelControl.getControl());
                       
        
    }

    public float getContrast() {
        return contrast;
    }

    public void setContrast(float contrast) {
        this.contrast = contrast;
    }

    public boolean isApplyForwardKernel() {
        return applyForwardKernel;
    }

    public void setApplyForwardKernel(boolean applyForwardKernel) {
        this.applyForwardKernel = applyForwardKernel;
    }
    
    
    
    public class ForwardKernelController extends LoneKernelController
    {

        @Override
        public void doApply_Kernel() {
            float[][] ww=get2DKernel();
//                KernelMaker2D.normalizeKernel(ww);
                
                FloatConnection conn=KernelMaker2D.invert(ww, chip.getSizeX(), chip.getSizeY(), SlowResponse.this.dimx, SlowResponse.this.dimy);
                                
                fwdKernel=conn.weights;
                fwdTargets=conn.targets;
        }
        
        @Override
        public String getName()
        {   return "Forward Kernel";            
        }
        
    }
    
    
    
    public class LateralKernelController extends LoneKernelController
    {

        @Override
        public void doApply_Kernel() {
            float[][] ww=get2DKernel();
                KernelMaker2D.normalizeKernel(ww);
                
                FloatConnection conn=KernelMaker2D.invert(ww, SlowResponse.this.dimx, SlowResponse.this.dimy, SlowResponse.this.dimx, SlowResponse.this.dimy);
                                
                autoKernel=conn.weights;
                autoTargets=conn.targets;
        }
        
        @Override
        public String getName()
        {   return "Lateral Kernel";            
        }
    }
    
    
    
    public void initDisplay()
    {
        im=ImageDisplay.createOpenGLCanvas();
        
        im.setImageSize(dimx,dimy);
            
        im.setSize(400,400);
        
        
        JPanel p=new JPanel();
        p.add(im);
        
        this.addDisplay(p);
        
//        JFrame j=new JFrame();
//        j.getContentPane().add(im);        
//        j.setVisible(true);
        
    }
    
    float displayMin;
    float displayMax;
    float displayAdaptationRate=0.1f;
    boolean displaySymmetric=true;
    
    
    final EngineeringFormat myFormatter = new EngineeringFormat();
    public void updateDisplay()
    {
        if (im==null)
        {
            return;
        }
            
        
        
        float minAct=Float.MAX_VALUE;
        float maxAct=Float.MIN_VALUE;
        
        float del=(displayMax-displayMin)/contrast;
        float bottom=displayMin/contrast;
        
        for (int i=0; i<state1.length; i++)
        {
            
            float vmem=state1[i];
            float level=(vmem-bottom)/del;
            
            
            
            im.setPixmapGray(i/dimy, i%dimy, level);
            
            
            minAct=minAct<vmem?minAct:vmem;
            maxAct=maxAct>vmem?maxAct:vmem;
        }
        
        if (displaySymmetric)
        {
            float absmax=Math.max(Math.abs(minAct),Math.abs(maxAct));
            minAct=-absmax;
            maxAct=absmax;
        }
        
        
        if (displayMin==0 && displayMax==0)
        {
            displayMin=minAct;
            displayMax=maxAct;
        }
        else
        {
            displayMin=displayAdaptationRate*minAct+(1-displayAdaptationRate)*displayMin;
            displayMax=displayAdaptationRate*maxAct+(1-displayAdaptationRate)*displayMax;
        }
        
        im.setTitleLabel("range: ["+myFormatter.format(minAct)+"  "+myFormatter.format(maxAct)+"]");
        
        im.repaint();
    }
    
    @Override
    public void initFilter() {        
    }
    
    void initNeuronsOnTimeStamp(int timestamp)
    {        
        for (int i=0; i<lastUpdateTime.length; i++)
        {   lastUpdateTime[i]=timestamp;
        }
    }
    
    public int dim2addr(short xloc, short yloc)
    {   return yloc+xloc*dimy;        
    }

    
    
    public File selectFile()
    {
        
            this.setFilterEnabled(false);

            
            
            JFileChooser fileChooser = new JFileChooser(System.getProperty("user.dir"));  
            int returnVal = fileChooser.showSaveDialog(null);  



            File file=fileChooser.getSelectedFile();
            
            this.setFilterEnabled(true);
            
            return file;

            
        
    }
    
    
    public void initFile(File thisFile)
    {
        if (thisFile==null)
        {   thisFile=selectFile();
            
        }  
        
        FileOutputStream fos;
        
        if (thisFile==null)
            return;
        
        try {
            fos=new FileOutputStream(thisFile);
            dos=new DataOutputStream(fos);
            dos.writeInt(dimx);
            dos.writeInt(dimy);
            
        } catch (FileNotFoundException ex) {
            Logger.getLogger(SlowResponse.class.getName()).log(Level.SEVERE, null, ex);
        }catch (IOException ex) {
            Logger.getLogger(SlowResponse.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        
        
    }
    
    /** Write the current frame to file.  Assumes state has been updated already */
    public void writeCurrentFrame()
    {
        if (dos==null)
            initFile(null);
        try {
                
            
            for(int i=0; i<state1.length; i++)
            {   dos.writeFloat(state1[i]);
            }
        } catch (IOException ex) {
            setRecordToFile(false);
            Logger.getLogger(SlowResponse.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
        
    
    
    public boolean isApplyLateralKernel() {
        return applyLateralKernel;
    }

    public void setApplyLateralKernel(boolean doSmoothing) {
        this.applyLateralKernel = doSmoothing;
    }

//    public float getSmoothingFactor() {
//        return smoothingFactor;
//    }
//
//    public void setSmoothingFactor(float smoothingFactor) {
//        this.smoothingFactor = smoothingFactor;
//        buildKernel();
//    }

//    public int getKernelWidth() {
//        return kernelWidth;
//    }
//
//    public void setKernelWidth(int kernelWidth) {
//        this.kernelWidth = kernelWidth;
//        buildKernel();
//    }

    public boolean isRecordToFile() {
        return recordToFile;
    }

    public void setRecordToFile(boolean recordToFile) {
        this.recordToFile = recordToFile;
        
        if (!recordToFile)
        {   try {
                dos.close();
                dos=null;
            } catch (IOException ex) {
                Logger.getLogger(SlowResponse.class.getName()).log(Level.SEVERE, null, ex);
            }            
        }
        
    }
    
    
    
    
    
}
