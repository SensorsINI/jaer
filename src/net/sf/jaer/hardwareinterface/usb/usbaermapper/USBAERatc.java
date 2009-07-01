package net.sf.jaer.hardwareinterface.usb.usbaermapper;

import java.beans.PropertyChangeSupport;
import java.io.*;
import java.util.logging.*;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.sf.jaer.aemonitor.AEListener;
import net.sf.jaer.hardwareinterface.HardwareInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.USBInterface;
import net.sf.jaer.hardwareinterface.usb.UsbIoUtilities;

/**
 * USBAERatc device class
 *
 * @author Alex Linares & Manuel Dominguez
 */

public class USBAERatc implements USBInterface,  HardwareInterface{

    PropertyChangeSupport support=new PropertyChangeSupport(this);

    static Logger log=Logger.getLogger("USBAERatc");

    public String interfacesArray[]={"imse","sil02","sil05","sil07","sil01","mac2"};
    public String detectedInterfacesArray[]= new String[interfacesArray.length];

    private boolean interfacesEx[]=new boolean[interfacesArray.length];

    static boolean libLoaded=false;
    public static final String NATIVE_DLL_FILENAME="USBaer";
    boolean isOpened=false;
    public boolean working=false;

    /** try loading the USBAERatc DLL */
    static{
            try {
                System.loadLibrary(NATIVE_DLL_FILENAME);// Load Library for interfacing to Eco-Link
                libLoaded=true;
            } catch (UnsatisfiedLinkError e) {
                String path=null;
                try{
                    path=System.getenv("PATH");
                    path=path.replace(File.pathSeparatorChar,'\n');
                    log.warning("cannot load DLL "+NATIVE_DLL_FILENAME+" to support the USBAER. Have you fully updated jAER? The PATH is set to \n"+path);
                }catch(Exception e2){
                    log.warning(e2.getMessage());
                }
        }
    }


    public USBAERatc() {
    }

    /** Native methods */
    private native boolean nativeOpen(String device, String path);
    private native void nativeUpload(String device, String filePath, boolean SelMapper, boolean SelDatalogger, boolean SelOthers, long inicio);
    private native void nativeSend(String device);
    private native void nativeReceive(String device);
    private native void nativeDownloadFromMapper(String device);
    private native void nativeSendDesc(String device);
    private native void nativeSendCommand(String device);
    public native boolean nativePrueba(String device);

    private int interfaceNumber;


    /** upload image to USBAERatc device */
    public void upload(){
        if(!libLoaded) return;

        JFileChooser jfcChooser = null;
        String path;
        jfcChooser = new JFileChooser();
        FileNameExtensionFilter f;
        f=new FileNameExtensionFilter("Binaries (*.bin)", "bin");
        jfcChooser.addChoosableFileFilter(f);
        jfcChooser.setFileFilter(f);
        int stt=jfcChooser.showOpenDialog(null);

        if(stt==-1)
        {
            System.out.println("ERROR: FILE CAN'T BE OPENED");
        }else if(stt==JFileChooser.APPROVE_OPTION )
        {
            File file = jfcChooser.getSelectedFile();
            path=file.getAbsolutePath();
            String device="";
            String devicetmp="";
            int nd=0;

            for(int i=0;i<interfacesArray.length;i++){
                devicetmp="\\\\.\\"+interfacesArray[i];
                if(nativePrueba(devicetmp))
                    device=devicetmp;
                    detectedInterfacesArray[nd]=device;
                    nd++;
                    interfaceNumber = i;
            }
            nativeUpload(device, path, true, false, false, 0);
        }
    }

    public void send(){
        //nativeSend(DevName);
    }

    public void receive(){
        //nativeReceive(DevName);
    }

    public void downloadFromMapper(){
        //nativeDownloadFromMapper(DevName);
    }

    public void sendDesc(){
        //nativeSendDesc(DevName);
    }

    public void sendCommand(){
        //nativeSendCommand(DevName);
    }

    /** upload firmware to USBAERatc device */
    public void open() throws HardwareInterfaceException{
        if(!libLoaded) return;

        JFileChooser jfcChooser = null;
        String path="";
        jfcChooser = new JFileChooser();
        FileNameExtensionFilter f;
        f=new FileNameExtensionFilter("Binaries (*.bin)", "bin");
        jfcChooser.addChoosableFileFilter(f);
        jfcChooser.setFileFilter(f);
        int stt=jfcChooser.showOpenDialog(null);
        int nd;

        if(stt==-1)
        {
            System.out.println("ERROR: FILE CAN'T BE OPENED");
            throw new HardwareInterfaceException("open USBAERatc");
        }else if(stt==JFileChooser.APPROVE_OPTION )
        {
            File file = jfcChooser.getSelectedFile();
            path=file.getAbsolutePath();

           /*byte[] b=new byte[167040];
            try {
                File file = jfcChooser.getSelectedFile();
                path=file.getAbsolutePath();
                FileInputStream fis= new FileInputStream(file);
                BufferedInputStream bis = new BufferedInputStream(fis);
                DataInputStream dis = new DataInputStream(bis);

                fis = new FileInputStream(file);
                size=dis.read(b);


            }catch (FileNotFoundException ex) {
                Logger.getLogger(USBAERatc.class.getName()).log(Level.SEVERE, null, ex);
            }catch (IOException ex) {
                    Logger.getLogger(USBAERatc.class.getName()).log(Level.SEVERE, null, ex);
            }*/

            //for(int i=0;i<size;i++)
            //    System.out.print(b[i]+"; ");
            String device="",devicetmp="";
            nd=0;
            for(int i=0;i<interfacesArray.length;i++){
                devicetmp="\\\\.\\"+interfacesArray[i];
                if(nativePrueba(devicetmp))
                    device=devicetmp;
                    detectedInterfacesArray[nd]=device;
                    nd++;
            }

            if(!working)
            {
                working=true;
                System.out.println("antes");
                nativeOpen(device,path);
                System.out.println("despues");
                working=false;
            }
        }
    }


    @Override
    public String toString() {
        String dev="";
        for(int i=0;i<interfacesArray.length;i++)
        {
            if(nativePrueba("\\\\.\\"+interfacesArray[i]))
                dev=interfacesArray[i];
        }
        return (getTypeName() + ": Interface "+dev);
    }

    /** @return true if the device is open, false otherwise */
    public boolean isOpen() {
        return isOpened;
    }

    /** @return device name in a String */
    public String getTypeName() {
        return "USBAERatc";
    }

    /** @return interface number */
    int getInterfaceNumber() {
        return interfaceNumber;
    }

    /** set the number of the interface */
    void setInterfaceNumber(int interfaceNumber) {
        this.interfaceNumber = interfaceNumber;
    }

    public void addAEListener(AEListener listener) {
        support.addPropertyChangeListener(listener);
    }

    public void removeAEListener(AEListener listener) {
        support.removePropertyChangeListener(listener);
    }

    public int[] getVIDPID() {
        return null;
    }

    public short getVID() {
        return 0;
    }

    public short getPID() {
        return 0;
    }

    public short getDID() {
        return 0;
    }

    public String[] getStringDescriptors() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void close() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /** @return number of devices that are connected */
    int getNumDevices() throws InterruptedException{
        if(!UsbIoUtilities.usbIoIsAvailable) {
            //log.info("Usb Io not available.");
            return 0;
        }
        boolean b=false, alguno=false;
        String temp;
        int nd=0;
        if (!working)
        {
            working=true;
            for(int i=0;i<interfacesArray.length;i++)
            {
                temp="\\\\.\\"+interfacesArray[i];
                b=nativePrueba(temp);
                if(b){
                    alguno=true;
                    interfacesEx[i]=true;
                    detectedInterfacesArray[nd]=interfacesArray[i];
                    nd++;
                }
                b=false;
            }
            working=false;
        }
        if(!libLoaded || !alguno) return 0;
        else return nd;
    }
}


