package cl.eye;

import ch.unizh.ini.jaer.projects.opticalflow.Chip2DMotion;
import ch.unizh.ini.jaer.projects.opticalflow.MotionData;
import ch.unizh.ini.jaer.projects.opticalflow.mdc2d.MDC2D;
import ch.unizh.ini.jaer.projects.opticalflow.mdc2d.MotionDataMDC2D;
import ch.unizh.ini.jaer.projects.opticalflow.usbinterface.MotionChipInterface;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import javax.swing.JPanel;
import net.sf.jaer.biasgen.Biasgen;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;

/**
 * OpticalFlowHardwareInterface for the playstation3 psEYE camera built on
 * top of CodeLaboratories CLCamera() class
 * <br /><br />
 * since this camera is only used for comparison reasons with the MDC2D system,
 * it uses the same MotionDataMDC2D class and masquerades as a MDC2D chip
 *
 * @author andstein
 */
public class PSeye_OpticalFlowHardwareInterface extends CLCamera
    implements MotionChipInterface 
{
    static final Logger log=Logger.getLogger(PSeye_OpticalFlowHardwareInterface.class.getName());

    Chip2DMotion chip;
    MotionData[] motionDatas; // we need >=2 to calculate motion
    int frameCounter = 0;
    int[] frameBuffer = new int[320*240]; // QVGA resolution
    long whenStartedMs= System.currentTimeMillis();
    
    PSeye_ConfigurationPanel configPanel;
    
    
    public PSeye_OpticalFlowHardwareInterface(int num)
    {
        super(num,CLCamera.CameraMode.QVGA_MONO_30);
        
        configPanel= new PSeye_ConfigurationPanel(this);
        chip= new MDC2D(); //TODO implement PSeyeChip
        motionDatas= new MotionData[2];
        motionDatas[0]= chip.getEmptyMotionData();
        motionDatas[1]= chip.getEmptyMotionData();
    }
    
    int x0,y0; // UR corner of displayed window
    int mapPixels; // width=height of area that is to be mapped into chip canvas
    /**
     * defines which area from the camera buffer is mapped to the MotionData
     * 
     * @param x0 upper left corner
     * @param y0 upper left corner
     * @param sz a rectangle of size sz*sz will be mapped
     */
    public void setInterpolationParameters(int x0,int y0,int sz) {
        this.x0= x0;
        this.y0= y0;
        mapPixels= sz;
    }
    
    int interpolatePixel(int x,int y) {
        float dx= 1f*x*mapPixels/chip.getSizeX();
        float dy= 1f*y*mapPixels/chip.getSizeY();
        x= (int) dx;
        dx-= x;
        x+= x0;
        y= (int) dy;
        dy-= y;
        y+= y0;
        
        if (x<0 || x>=319) return 0;
        if (y<0 || y>=219) return 0;
        
        return (int) (
                frameBuffer[ y*320 +x]
                + dx*(frameBuffer[ y*320 +(x+1)]-frameBuffer[ y*320 +x])
                + dy*(frameBuffer[ (y+1)*320 +x]-frameBuffer[ y*320 +x])
                );
    }
    
    void doAutoContrast() {
        int max=Integer.MIN_VALUE,min=Integer.MAX_VALUE;
        for(int i=0; i<frameBuffer.length; i++)
            if (frameBuffer[i]<min) min= frameBuffer[i];
            else if (frameBuffer[i]>max) max= frameBuffer[i];
        
        offset= min;
        gain= 1f/(max-min);
    }
    
    float convertPixelValue(int value) {
        return (value-offset)*gain;
    }
    
    float offset= 0f;
    float gain= 0f;
    boolean autoContrast= true;
    /**
     * whether to automatically maximize contrast or not
     * <br />
     * note:
     * you must call #setContrastParameters after setting autoContrast to false
     * @param val 
     */
    public void setAutoContrast(boolean val) {
        autoContrast= val;
        //DBG
        if (val==false)
            log.info("auto contrast was : offset="+offset+"; gain="+gain);
    }
    /**
     * sets contrast parameters explicitely
     * @param offset
     * @param gain 
     */
    public void setContrastParameters(float offset,float gain) {
        this.offset= offset;
        this.gain= gain;
    }
    
    public float getCurrentOffset() { return offset; }
    public float getCurrentGain() { return gain; }

    @Override
    public MotionData getData() throws TimeoutException {
        try {
            getCameraFrame(frameBuffer, 300); // TODO acquire multiple frames between calls, pack into single packet and return that packet with multiple frame timestamps
            frameCounter++; // TODO notify AE listeners here or in thread acquiring frames
        } catch(HardwareInterfaceException e) {
            System.err.println("could not get frame : "+e);
            e.printStackTrace();
            return null;
        }
        
        if (autoContrast)
            doAutoContrast();
        
        MotionData currentData= motionDatas[frameCounter %motionDatas.length];
        float[][][] rawData= currentData.getRawDataPixel();
        for(int y=0; y<chip.getSizeY(); y++)
            for(int x=0; x<chip.getSizeX(); x++)
                
                rawData[0][y][x]= convertPixelValue(interpolatePixel(x, y));
        
        currentData.setSequenceNumber(frameCounter);
        currentData.setTimeCapturedMs(System.currentTimeMillis() - whenStartedMs);
        currentData.setLastMotionData(motionDatas[(frameCounter -1) %motionDatas.length]);
        currentData.collectMotionInfo();
        
        return currentData;
    }
    
    @Override
    public void setChip(Chip2DMotion chip) {
        this.chip= chip;
    }

    ///////////////////////////   boring   ///////////////////////////////////

    // open, close, isOpen are already implemented in CLCamera...
    /*
    @Override
    public void close() {
    }

    @Override
    public void open() throws HardwareInterfaceException {
    }
    
    @Override
    public boolean isOpen() {
        return true;
    }
     */

    @Override
    public JPanel getConfigPanel() {
        return configPanel;
    }

    @Override
    public String getTypeName() {
        return "PSeye";
    }

    /////////////////////////// do nothing ///////////////////////////////////
    @Override
    public void setCaptureMode(int mask) throws HardwareInterfaceException {
    }

    @Override
    public int getRawDataIndex(int bit) {
        return 0;
    }

    @Override
    public void setChannel(int bit, boolean onChip) throws HardwareInterfaceException {
    }

    @Override
    public void setPowerDown(boolean powerDown) throws HardwareInterfaceException {
    }

    @Override
    public void sendConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
    }

    @Override
    public void flashConfiguration(Biasgen biasgen) throws HardwareInterfaceException {
    }

    @Override
    public byte[] formatConfigurationBytes(Biasgen biasgen) {
        return null;
    }

}
