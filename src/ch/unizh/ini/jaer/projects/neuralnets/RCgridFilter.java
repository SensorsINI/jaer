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

import jspikestack.EngineeringFormat;
import jspikestack.KernelMaker2D;
import jspikestack.KernelMaker2D.FloatConnection;
import net.sf.jaer.chip.AEChip;
import net.sf.jaer.event.EventPacket;
import net.sf.jaer.event.PolarityEvent;
import net.sf.jaer.eventprocessing.MultiSourceProcessor;
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
 * @author Peter
 */
public class RCgridFilter extends MultiSourceProcessor {
    
    int dimx;
    int dimy;
    
    
    ImageDisplay im;
    
    public float[] state1;
    public float[] state2;
    public int[] lastUpdateTime;
    
    private boolean doSmoothing=true;
    
    
    private float smoothingFactor=0.5f;
    
    private int kernelWidth=3;
    
    private float timeConst= getFloat("timeConst", 500000);
    
    
    DataOutputStream dos;
    public boolean recordToFile;
        
    
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

    @Override
    public String[] getInputNames() {
        return new String[] {"retina"};
    }
    
    public void updateState(int toTime)
    {
        for (int i=0; i<state1.length; i++)
        {
            updateState(toTime,i);
        }       
        
        if (isDoSmoothing())
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
        int addr=dim2addr(evin.x,evin.y);
        updateState(evin.timestamp,addr);
        
        state1[addr]+=evin.getPolarity()==PolarityEvent.Polarity.On?epscDecayRate:-epscDecayRate;
        
    }
                
    public RCgridFilter(AEChip chip)
    {   super(chip);
    
    
        setPropertyTooltip("thresh", "Firing threshold of slow-response neurons");
        setPropertyTooltip("tcEPSC", "Time-Constant of the EPSCs, in microseconds.  Longer TC means shading is more persistent");
        setPropertyTooltip("tcMem", "Time-Constant of the neuron membrane potentials, in microseconds.");
        setPropertyTooltip("eventBased", "Generate events indicating slow-response activity.");
    
        updateEpscScale();
    }
    
    @Override
    public EventPacket<?> filterPacket(EventPacket<?> in) {
        
        if (!isTimeInitialized)
        {   if (in.isEmpty())
                return out;     
        
            build();
            initNeuronsOnTimeStamp(in.getFirstTimestamp());    
            isTimeInitialized=true;    
                        
        }
        
        for (Object ev:in)
        {
            fireEventToUnit((PolarityEvent)ev);
        }
        
        if (in.isEmpty()) 
            return out;
        
        
        
//        OutputEventIterator outItr=out.outputIterator();
               
        
        int time=in.getLastTimestamp();
        
        updateState(time);
        
        updateDisplay();
        
        if (recordToFile)
            this.writeCurrentFrame();
        
        
        return in;
        
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
    }
    
    public void buildKernel()
    {
//        KernelMaker2D.Gaussian kern=new KernelMaker2D.Gaussian();
//        kern.majorWidth=.5f;
//        kern.minorWidth=.5f;
//        float[][] ww=KernelMaker2D.makeKernel(kern, 3, 3);
//        KernelMaker2D.normalizeKernel(ww);
        
        
        KernelMaker2D.Parabola kern=new KernelMaker2D.Parabola();
        kern.mag=1;
        kern.width=2;
        float[][] ww=KernelMaker2D.makeKernel(kern, 3, 3);
        KernelMaker2D.normalizeKernel(ww);
        
        
        
//        KernelMaker2D.plot(ww);
        
        FloatConnection conn=KernelMaker2D.invert(ww, dimx, dimy, dimx, dimy);
        
        autoKernel=conn.weights;
        autoTargets=conn.targets;
                
        
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
        
        float del=displayMax-displayMin;
        
        for (int i=0; i<state1.length; i++)
        {
            
            float vmem=state1[i];
            float level=(vmem-displayMin)/del;
            
            
            
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
        
    
    
    public boolean isDoSmoothing() {
        return doSmoothing;
    }

    public void setDoSmoothing(boolean doSmoothing) {
        this.doSmoothing = doSmoothing;
    }

    public float getSmoothingFactor() {
        return smoothingFactor;
    }

    public void setSmoothingFactor(float smoothingFactor) {
        this.smoothingFactor = smoothingFactor;
        buildKernel();
    }

    public int getKernelWidth() {
        return kernelWidth;
    }

    public void setKernelWidth(int kernelWidth) {
        this.kernelWidth = kernelWidth;
        buildKernel();
    }

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
