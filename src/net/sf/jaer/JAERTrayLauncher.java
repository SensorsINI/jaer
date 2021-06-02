/*
 * JAERTrayLauncher.java
 *
 * Created on January 23, 2007, 8:02 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 *
 *
 *Copyright January 23, 2007 Tobi Delbruck, Inst. of Neuroinformatics, UNI-ETH Zurich
 */

package net.sf.jaer;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Logger;

import net.sf.jaer.hardwareinterface.usb.UsbIoUtilities;
import net.sf.jaer.hardwareinterface.usb.cypressfx2.CypressFX2TmpdiffRetinaHardwareInterface;
import de.thesycon.usbio.PnPNotify;
import de.thesycon.usbio.PnPNotifyInterface;

/**
 * Launches JAEViewer when an AE device is plugged in or on menu actions.
 
 
 * @author tobi
 */
public class JAERTrayLauncher implements PnPNotifyInterface {
//TODO: keeps restarting every time user quits, because device is closed and then "added" again by reset of USB driver.    
    static Logger log=Logger.getLogger("JAERTrayLauncher");
    private TrayIcon trayIcon;
    
    PnPNotify pnp;
    JAERViewer viewer=null;
    
    ProcessBuilder processBuilder;
    
    /** Creates a new instance of JAERTrayLauncher */
    public JAERTrayLauncher() {
        init();
    }
    
    private void init(){
        
        if (!SystemTray.isSupported()) {
            log.warning("SystemTray is not supported on this platform");
            throw new RuntimeException("SystemTray is not supported on this platform");
        }
        
        
        SystemTray tray = SystemTray.getSystemTray();
//        Image image = Toolkit.getDefaultToolkit().getImage("ch/unizh/ini/caviar/jAERIcon.gif");
        URL url=getClass().getResource("/net/sf/jaer/jAERIcon.gif");
        Image image = Toolkit.getDefaultToolkit().getImage(url);
        
        MouseListener mouseListener = new MouseListener() {
            
            synchronized public void mouseClicked(MouseEvent e) {
//                System.out.println("Tray Icon - Mouse clicked!");
            }
            
            synchronized public void mouseEntered(MouseEvent e) {
//                System.out.println("Tray Icon - Mouse entered!");
            }
            
            synchronized public void mouseExited(MouseEvent e) {
//                System.out.println("Tray Icon - Mouse exited!");
            }
            
            synchronized public void mousePressed(MouseEvent e) {
//                System.out.println("Tray Icon - Mouse pressed!");
            }
            
            synchronized public void mouseReleased(MouseEvent e) {
//                System.out.println("Tray Icon - Mouse released!");
            }
        };
        
        ActionListener exitListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                log.info("Exiting...");
                System.exit(0);
            }
        };
        
        PopupMenu popup = new PopupMenu();
        MenuItem launchJaerViewerMenuItem=new MenuItem("JAERViewer");
        launchJaerViewerMenuItem.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent evt){
                startViewer();
            }
            
        });
        
        popup.add(launchJaerViewerMenuItem);
        MenuItem exitMenuItem = new MenuItem("Exit");
        exitMenuItem.addActionListener(exitListener);
        popup.add(exitMenuItem);
        
        trayIcon = new TrayIcon(image, "jAER launcher", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.setToolTip("Launches viewer when AE hardware is plugged in");
        
        ActionListener actionListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                startViewer();
                trayIcon.displayMessage("Default action",
                        "Starting viewer",
                        TrayIcon.MessageType.INFO);
            }
        };
        
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(actionListener);
//        trayIcon.addMouseListener(mouseListener);
        
        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            System.err.println("TrayIcon could not be added.");
        }
        
        UsbIoUtilities.enablePnPNotification(this,CypressFX2TmpdiffRetinaHardwareInterface.GUID);
        
    }
    
    private void startViewer() {
        File runningFile=new File("jAERViewerRunning.txt");
        if(runningFile.isFile()){
            log.warning("Viewer is running, not starting another");
            return;
        }
        
        if(processBuilder==null){
            File dir=new File(System.getProperty("user.dir")).getParentFile().getParentFile();
            String cmd=dir.getAbsolutePath()+File.separator+"jAERViewer.exe";
            processBuilder=new ProcessBuilder(cmd);
            log.info("command="+processBuilder.command()+" directory="+processBuilder.directory());
        }
        
        
        try{
            processBuilder.start();
        }catch(IOException e){
            log.warning(e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void onAdd() {
        trayIcon.displayMessage("Hardware event",
                "Hardware added, starting viewer",
                TrayIcon.MessageType.INFO);
        
        startViewer();
    }
    
    synchronized public void onRemove() {
         trayIcon.displayMessage("Hardware event",
                "Hardware removed",
                TrayIcon.MessageType.INFO);
    }
    
    public static void main(String[] args){
        new JAERTrayLauncher();
    }
    
}

