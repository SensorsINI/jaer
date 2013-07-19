/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * DrummerServer.java
 *
 * Created on Jul 15, 2009, 11:50:33 PM
 */
package org.ine.telluride.jaer.tell2009.drummer;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;

import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.ServoInterface;
import net.sf.jaer.hardwareinterface.usb.ServoInterfaceFactory;
import net.sf.jaer.hardwareinterface.usb.UsbIoUtilities;
import net.sf.jaer.hardwareinterface.usb.silabs.SiLabsC8051F320_USBIO_ServoController;
import net.sf.jaer.util.RemoteControl;
import net.sf.jaer.util.RemoteControlCommand;
import net.sf.jaer.util.RemoteControlled;
import de.thesycon.usbio.PnPNotify;
import de.thesycon.usbio.PnPNotifyInterface;
/**
 * Remotely allows control of servo to drum at some beat and tempo.
 *
 * @author tobi
 */
public class DrummerServer extends javax.swing.JFrame implements RemoteControlled,PnPNotifyInterface{
    final int MAX_SLIDER = 1000;
    PnPNotify pnp = null;
    java.util.Timer timer;
    RemoteControl remoteControl = null; // to control servos over UDP
    static Logger log = Logger.getLogger("ServoTest");
    ServoInterface hwInterface = null;
    Preferences prefs = Preferences.userNodeForPackage(DrummerServer.class);
    private int servoNumber = 0;
    private int beatMs = prefs.getInt("DrummerServer.beatMs",200);

    private void playBeats (final int reps){
        if ( lock.isLocked() ){
            log.warning("playBeats in progress");
            return;
        }
        Thread T = new Thread("Beat player"){
            @Override
            public void run (){
                try{
                    lock.lock();
                    for ( int i = 0 ; i < reps ; i++ ){
                        for ( Beat b:beats ){
                            setServo(loPos);
                            Thread.sleep(beatMs);
                            setServo(hiPos);
                            if ( b.intervalMs - beatMs >= 0 ){
                                Thread.sleep(b.intervalMs - beatMs);
                            }
                        }
                    }
                } catch ( Exception e ){
                    log.warning(e.toString());
                } finally{
                    lock.unlock();
                }

            }
        };
        T.start();
    }

    private void playBeats (){
        playBeats(1);
    }
    private class Beat{
        float amplitude = 1;
        int intervalMs = 200;
    }
    private ArrayList<Beat> beats = new ArrayList();
    private float loPos = prefs.getFloat("DrummerServer.loPos",.6f);
    private float hiPos = prefs.getFloat("DrummerServer.hiPos",.4f);
    private ReentrantLock lock = new ReentrantLock();
    private volatile boolean ignoreSliderStateChanges = true;

    /** Creates new form DrummerServer */
    public DrummerServer (){
        initComponents();
        highPosSlider.setMaximum(MAX_SLIDER);
        lowPosSlider.setMaximum(MAX_SLIDER);
        UsbIoUtilities.enablePnPNotification(this,SiLabsC8051F320_USBIO_ServoController.GUID);
        try{
            remoteControl = new RemoteControl();
            remoteControl.addCommandListener(this,"beat intMs0 intMs1 ....","defines a beat pattern for a single drumstick");
            remoteControl.addCommandListener(this,"play beats","play the beat pattern");
        } catch ( SocketException ex ){
            log.warning("couldn't contruct remote control for ServoTest: " + ex);
        }
        ignoreSliderStateChanges = false;
        lowPosSlider.setValue(sliderFromServo(loPos)); // will set servo from callback
        highPosSlider.setValue(sliderFromServo(hiPos));
        beatMsSpinner.setValue(beatMs);
        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run (){
                if ( hwInterface != null ){
                    hwInterface.close();
                }
                log.info("closed servo interface");
            }
        });
    }

    private int sliderFromServo (float f){
        return (int)( MAX_SLIDER * f );
    }

    private float servoFromSlider (int i){
        return (float)i / MAX_SLIDER;
    }

    private void beatOnceInBgThread (){
        Thread T = new Thread(){
            @Override
            public void run (){
                try{
                    lock.lock();
                    setServo(loPos);
                    Thread.sleep(beatMs);
                    setServo(hiPos);
                } catch ( InterruptedException ex ){
                } finally{
                    lock.unlock();
                }
            }
        };
        T.start();
    }

    private boolean checkHardware (){
        if ( hwInterface != null && hwInterface.isOpen() ){
            return true;
        }
        try{
//                        hwInterface=new SiLabsC8051F320_USBIO_ServoController();
            hwInterface = (ServoInterface)ServoInterfaceFactory.instance().getFirstAvailableInterface(); //new SiLabsC8051F320_USBIO_ServoController();
            if ( hwInterface == null ){
                return false;
            }
            hwInterface.open();
            return true;
        } catch ( HardwareInterfaceException e ){
            log.warning("error opening servo: " + e);
            return false;
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings ("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        highPosSlider = new javax.swing.JSlider();
        lowPosSlider = new javax.swing.JSlider();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        beatButton = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        beatMsSpinner = new javax.swing.JSpinner();
        playBeatsButton = new javax.swing.JButton();
        menuBar = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        openMenuItem = new javax.swing.JMenuItem();
        saveMenuItem = new javax.swing.JMenuItem();
        saveAsMenuItem = new javax.swing.JMenuItem();
        exitMenuItem = new javax.swing.JMenuItem();
        helpMenu = new javax.swing.JMenu();
        contentsMenuItem = new javax.swing.JMenuItem();
        aboutMenuItem = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Drummer");

        highPosSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                highPosSliderStateChanged(evt);
            }
        });

        lowPosSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                lowPosSliderStateChanged(evt);
            }
        });

        jLabel4.setLabelFor(highPosSlider);
        jLabel4.setText("High pos");

        jLabel5.setLabelFor(lowPosSlider);
        jLabel5.setText("Low pos");

        beatButton.setText("Beat once");
        beatButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                beatButtonActionPerformed(evt);
            }
        });

        jLabel1.setLabelFor(beatMsSpinner);
        jLabel1.setText("beatMs");

        beatMsSpinner.setModel(new javax.swing.SpinnerNumberModel(50, 50, 400, 25));
        beatMsSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                beatMsSpinnerStateChanged(evt);
            }
        });

        playBeatsButton.setText("Play beats");
        playBeatsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                playBeatsButtonActionPerformed(evt);
            }
        });

        fileMenu.setText("File");

        openMenuItem.setText("Open");
        fileMenu.add(openMenuItem);

        saveMenuItem.setText("Save");
        fileMenu.add(saveMenuItem);

        saveAsMenuItem.setText("Save As ...");
        fileMenu.add(saveAsMenuItem);

        exitMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_X, 0));
        exitMenuItem.setText("Exit");
        exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(exitMenuItem);

        menuBar.add(fileMenu);

        helpMenu.setText("Help");

        contentsMenuItem.setText("Contents");
        helpMenu.add(contentsMenuItem);

        aboutMenuItem.setText("About");
        helpMenu.add(aboutMenuItem);

        menuBar.add(helpMenu);

        setJMenuBar(menuBar);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(29, 29, 29)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel4)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(jLabel1)
                                .addComponent(jLabel5)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(highPosSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(lowPosSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGap(27, 27, 27)
                                .addComponent(beatButton))
                            .addGroup(layout.createSequentialGroup()
                                .addGap(10, 10, 10)
                                .addComponent(beatMsSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 89, javax.swing.GroupLayout.PREFERRED_SIZE))))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(141, 141, 141)
                        .addComponent(playBeatsButton)))
                .addContainerGap(18, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(highPosSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel4))
                        .addGap(17, 17, 17)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel5)
                            .addComponent(lowPosSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel1)
                            .addComponent(beatMsSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(31, 31, 31)
                        .addComponent(beatButton)))
                .addGap(40, 40, 40)
                .addComponent(playBeatsButton)
                .addContainerGap(107, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
        if ( hwInterface != null ){
            hwInterface.close();
        }
        System.exit(0);
    }//GEN-LAST:event_exitMenuItemActionPerformed

    private void highPosSliderStateChanged (javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_highPosSliderStateChanged
        if ( ignoreSliderStateChanges || highPosSlider.getValueIsAdjusting() ){
            return;
        }
        hiPos = servoFromSlider(highPosSlider.getValue());
        beatOnceInBgThread();
        prefs.putFloat("DrummerServer.hiPos",hiPos);
        log.info("set hiPos=" + hiPos);
    }//GEN-LAST:event_highPosSliderStateChanged

    private void lowPosSliderStateChanged (javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_lowPosSliderStateChanged
        if ( ignoreSliderStateChanges || lowPosSlider.getValueIsAdjusting() ){
            return;
        }
        loPos = servoFromSlider(lowPosSlider.getValue());
        beatOnceInBgThread();
        prefs.putFloat("DrummerServer.loPos",loPos);
        log.info("set loPos=" + loPos);
    }//GEN-LAST:event_lowPosSliderStateChanged

    private void beatButtonActionPerformed (java.awt.event.ActionEvent evt) {//GEN-FIRST:event_beatButtonActionPerformed
        beatOnceInBgThread();
    }//GEN-LAST:event_beatButtonActionPerformed

    private void beatMsSpinnerStateChanged (javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_beatMsSpinnerStateChanged
        try{
            beatMs = (Integer)beatMsSpinner.getValue();
            prefs.putInt("DrummerServer.beatMs",beatMs);
        } catch ( Exception e ){
            log.warning(e.toString());
        }
    }//GEN-LAST:event_beatMsSpinnerStateChanged

    private void playBeatsButtonActionPerformed (java.awt.event.ActionEvent evt) {//GEN-FIRST:event_playBeatsButtonActionPerformed
        playBeats();
    }//GEN-LAST:event_playBeatsButtonActionPerformed

    private void setServo (float f){
        if ( !checkHardware() ){
            return;
        }
        try{
            hwInterface.setServoValue(servoNumber,f);
        } catch ( HardwareInterfaceException e ){
            log.warning(e.toString());
        }

    }

    private void setServo (ChangeEvent evt){
        if ( !checkHardware() ){
            return;
        }
        if ( !( evt.getSource() instanceof JSlider ) ){
            return;
        }
        int val = ( (JSlider)evt.getSource() ).getValue();
        try{
            hwInterface.setServoValue(servoNumber,servoFromSlider(val));
        } catch ( HardwareInterfaceException e ){
            log.warning(e.toString());
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main (String args[]){
        java.awt.EventQueue.invokeLater(new Runnable(){
            public void run (){
                new DrummerServer().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem aboutMenuItem;
    private javax.swing.JButton beatButton;
    private javax.swing.JSpinner beatMsSpinner;
    private javax.swing.JMenuItem contentsMenuItem;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JMenu helpMenu;
    private javax.swing.JSlider highPosSlider;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JSlider lowPosSlider;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JMenuItem openMenuItem;
    private javax.swing.JButton playBeatsButton;
    private javax.swing.JMenuItem saveAsMenuItem;
    private javax.swing.JMenuItem saveMenuItem;
    // End of variables declaration//GEN-END:variables

    public String processRemoteControlCommand (RemoteControlCommand command,String input){
        try{
            String[] toks = command.getTokens();
            if ( toks == null ){
                return "?";
            }
            String[] inpToks = input.split("\\s");
            if ( inpToks.length < 1 ){
                return "?";
            }

            if ( inpToks[0].equals("beat") ){
                beats.clear();
                for ( int i = 1 ; i < inpToks.length ; i++ ){
                    if ( inpToks[i].isEmpty() ){
                        continue;
                    }
                    Beat beat = new Beat();
                    beat.amplitude = 1;
                    beat.intervalMs = Integer.parseInt(inpToks[i]);
                    beats.add(beat);
                    System.out.println("beat " + beat.intervalMs);
                }
                playBeats();
            } else if ( inpToks[0].equals("play") ){
                if ( inpToks.length == 1 ){
                    playBeats();
                    System.out.println("play");
                } else{
                    int count = Integer.parseInt(inpToks[1]);
                    playBeats(count);
                    System.out.println("play " + count);
                }
            }
            return "";
        } catch ( Exception e ){
            log.warning("caught " + e + " for string=" + input);
        }
        return "";
    }

    /** called when device added */
    public void onAdd (){
        log.info("device added");
    }

    public void onRemove (){
        log.info("device removed, closing it");
        if ( hwInterface != null && hwInterface.isOpen() ){
            hwInterface.close();
        }
    }
}
