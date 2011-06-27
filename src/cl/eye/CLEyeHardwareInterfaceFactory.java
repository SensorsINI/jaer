/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cl.eye;

import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.HardwareInterfaceFactoryInterface;
import net.sf.jaer.hardwareinterface.usb.UsbIoUtilities;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.awt.Graphics;
/**
 * Constructs CLEye hardware interfaces.
 * 
 * @author tobi
 */
public class CLEyeHardwareInterfaceFactory implements HardwareInterfaceFactoryInterface{
    final static Logger log=Logger.getLogger("CLEye");
    private static CLEyeHardwareInterfaceFactory instance = new CLEyeHardwareInterfaceFactory(); // singleton
    private HardwareInterface[] interfaces;
    
   private CLEyeHardwareInterfaceFactory(){
       if(CLCamera.isLibraryLoaded()) {
           this.interfaces = new HardwareInterface[CLCamera.cameraCount()];
           for ( int i = 0; i < this.interfaces.length; i++ ) {
               this.interfaces[i] = new CLRetinaHardwareInterface(i);
           }
       }
    }
    
    /** @return singleton instance used to construct CLCameras. */
    public static HardwareInterfaceFactoryInterface instance() {
        return instance;
    }

 
    
    @Override
    public int getNumInterfacesAvailable() {
        return this.interfaces.length;
    }

    /** Returns the first camera
     * 
     * @return first camera, or null if none available.
     * @throws HardwareInterfaceException 
     */
    @Override
    public HardwareInterface getFirstAvailableInterface() throws HardwareInterfaceException {
        return getInterface(0);
    }

    /** Returns the n'th camera (0 based) 
     * 
     * @param n
     * @return the camera
     * @throws HardwareInterfaceException 
     */
    @Override
    public HardwareInterface getInterface(int n) throws HardwareInterfaceException {
        if(!CLCamera.isLibraryLoaded()) return null;
        if(getNumInterfacesAvailable() == 0 || getNumInterfacesAvailable() < n + 1) return null;
        return (CLCamera) this.interfaces[n];
    }

    @Override
    public String getGUID() {
        return "{4b4803fb-ff80-41bd-ae22-1d40defb0d01}"; // taken from working installation
    }
    
    
         /** Test program
     * 
     * @param args ignored
     */
    
    public static void main(String[] args) {  
        class ImagePanel extends JPanel {
            BufferedImage image;
            
            public ImagePanel(BufferedImage image) {
                this.image = image;
            }
            
        
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // Draw image centered.
                
                g.drawImage(image, 0, 0, this);
            }
        }
        
        try {
            if(!UsbIoUtilities.isLibraryLoaded()){
                log.warning("no USBIO libraries found");
            }
            if(!CLCamera.isLibraryLoaded()) {
                log.warning("no library found");
                return;
            }
            int camCount = 0;
            if ((camCount = CLCamera.cameraCount()) == 0) {
                log.warning("no cameras found");
                return;
            }
            log.info(camCount + " CLEye cameras found");
            CLCamera cam = new CLCamera();
            cam.open();
            
            BufferedImage image = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
            WritableRaster raster = (WritableRaster) image.getData();

            JFrame frame = new JFrame("PSEYE");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(320,240);
            ImagePanel contentPane = new ImagePanel(image);
            contentPane.setOpaque(true);
            frame.setContentPane(contentPane);

            //Display the window.
             frame.setVisible(true);
            
            int[] imgData = new int[320*240];
            for(int f=0;f<9000;f++){
                cam.getCameraFrame(imgData, 100);
                // filter to test colour channels
                int RMASK = 0xff0000;
                int GMASK = 0x00ff00;
                int BMASK = 0x0000ff;
                for (int i=0;i<imgData.length;i++) {
                    imgData[i] = imgData[i] & RMASK;
                }
                raster.setDataElements(0, 0, 320, 240, imgData);
                image.setData(raster);
                frame.repaint();
            }
            cam.close();
        } catch (HardwareInterfaceException ex) {
            Logger.getLogger(CLEyeHardwareInterfaceFactory.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
}
