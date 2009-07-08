package org.ine.telluride.jaer.eyeinthesky;

import de.thesycon.usbio.PnPNotifyInterface;
import de.thesycon.usbio.PnPNotify;

import java.util.logging.Logger;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import net.sf.jaer.hardwareinterface.ServoInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.silabs.SiLabsC8051F320_USBIO_ServoController;

import javax.swing.*;

/**
 * User: jauerbac
 * Date: Jul 1, 2009
 * Time: 5:50:57 PM
 */
public class ServoTester extends javax.swing.JFrame implements PnPNotifyInterface {
    static Logger log = Logger.getLogger(ServoTester.class.getName());
    private float stopVal = (float) 0.0;
    private float startVal = (float) 1.0;

    private int delay = 100; // the delay in ms for using with setServoSlow()

    ServoInterface hwInterface = null;
    float[] servoValues;
    PnPNotify pnp = null;

    /**
     * Creates new form Shooter
     */
    public ServoTester() {

        initComponents();
        try {
            System.loadLibrary("USBIOJAVA");
            pnp = new PnPNotify(this);
            pnp.enablePnPNotification(SiLabsC8051F320_USBIO_ServoController.GUID);
            pnp.enablePnPNotification(SiLabsC8051F320_USBIO_ServoController.GUID);
        }
        catch (java.lang.UnsatisfiedLinkError e) {
            log.warning("USBIOJAVA library not available, probably because you are not running under Windows, continuing anyhow");
        }

        try {
            hwInterface = new SiLabsC8051F320_USBIO_ServoController();
            hwInterface.open();
            servoValues = new float[hwInterface.getNumServos()];
            setTitle("ServoController");
        }
        catch (HardwareInterfaceException e) {
            e.printStackTrace();
        }
    }

    public boolean initServo() {
        boolean success = false;
        try {
            hwInterface = new SiLabsC8051F320_USBIO_ServoController();
            hwInterface.open();
            servoValues = new float[hwInterface.getNumServos()];
            //setTitle("ServoController");
            success = true;
            setServoVal(0, stopVal);
            setServoVal(1, stopVal);
            setServoVal(2, stopVal);
            log.info("Servo init called");
        }
        catch (HardwareInterfaceException e) {
            e.printStackTrace();
        }
        return success;
    }


    public void close() {
        try {
            hwInterface.disableAllServos();
            hwInterface.close();
            hwInterface = null;
        } catch (HardwareInterfaceException ex) {
            ex.printStackTrace();
        }
    }


    /**
     * Constructs a new controller panel using existing hardware interface
     *
     * @param hw the interface
     */
    public ServoTester(ServoInterface hw) {
        this();
        hwInterface = hw;
        String s = hw.getClass().getSimpleName();
        setTitle(s);
    }

    Toolkit toolkit;
    JPanel panel;
    JButton goButton, stopButton, SOSButton;

    JTextField xVectorField, yVectorField, rotationField ;
    JLabel xVectorLabel, yVectorLabel, rotationLabel;

    private void initComponents() {

        setTitle("Servo Tester");
                setSize(600, 250);

                toolkit = getToolkit();
                Dimension size = toolkit.getScreenSize();
                setLocation((size.width - getWidth())/2, (size.height - getHeight())/2);
                setDefaultCloseOperation(EXIT_ON_CLOSE);

                panel = new JPanel();
                getContentPane().add(panel);

                panel.setLayout(null);

                goButton = new JButton("GO");
                goButton.setBounds(125, 10, 150, 30);
                goButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent event) {
                        double x = Double.parseDouble(xVectorField.getText());
                        double y = Double.parseDouble(yVectorField.getText());
                        double rotation = Double.parseDouble(rotationField.getText());


                        log.info("Actuating servos... x = " + x + " y = " + y + " r = " + rotation);
                        setComplexDrivingSignal(x,y,rotation);
                    }
                });

                stopButton = new JButton("STOP");
                stopButton.setBounds(325, 10, 150, 30);
                stopButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent event) {

                        log.info("Stopping");
                        setComplexDrivingSignal(0.0, 0.0, 0.0);

                    }
                });
                
                SOSButton = new JButton("SOS");
                SOSButton.setBounds(225, 60, 150, 30);
                SOSButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent event) {

                        log.info("Stopping");
                        disableAllServos();

                    }
                });

                xVectorField = new JTextField("0");
                yVectorField = new JTextField("0");
                rotationField = new JTextField("0");

                xVectorField.setBounds(25, 180, 150, 30);
                yVectorField.setBounds(225, 180, 150, 30);
                rotationField.setBounds(425, 180, 150, 30);

                xVectorLabel = new JLabel("X Vector");
                yVectorLabel = new JLabel("Y Vector");
                rotationLabel = new JLabel("Rotation");

                xVectorLabel.setBounds(25, 120, 150, 30);
                yVectorLabel.setBounds(225, 120, 150, 30);
                rotationLabel.setBounds(425, 120, 150, 30);

                panel.add(goButton);
                panel.add(stopButton);
                panel.add(xVectorField);
                panel.add(yVectorField);
                panel.add(rotationField);
                panel.add(xVectorLabel);
                panel.add(yVectorLabel);
                panel.add(rotationLabel);
                panel.add(SOSButton);

    }

    public void setComplexDrivingSignal(double vectX, double vectY, double rotation) {

      double scalingFactor = 0.06;        // -128..+128 --> -1536 ... +1536
      double alpha, beta, gamma;

      alpha = 0.5 + ( (-2.0/3.0) * (vectX) +                        rotation) * scalingFactor;
      beta  = 0.5 + ( (1.0/3.0) * (vectX) + (vectY) / (Math.PI/2.0)    + rotation) * scalingFactor;
      gamma = 0.5 + ( (1.0/3.0) * (vectX) - (vectY) / (Math.PI/2.0)    + rotation) * scalingFactor;
      
      log.info("alpha: " + alpha + "\tbeta: " + beta + "\tgamma: " + gamma);

      setServoVal(1, (float) alpha);
      setServoVal(2, (float) beta);
      setServoVal(3, (float) gamma);
      // alpha, beta and gamma go to the three motors
    }


    public void shooterSlowReset() {
        delayMs(500);
        setServoSlow(1, stopVal, startVal, delay);
    }

    private void setServoVal(int servo, float value) {
        try {
            hwInterface.setServoValue(servo, value);
        }
        catch (HardwareInterfaceException e) {
            e.printStackTrace();
        }
    }

    private void disableAllServos() {
        try {
            hwInterface.disableAllServos();
        }
        catch (HardwareInterfaceException e) {
            e.printStackTrace();
        }
    }

    private void disableServo(int servo) {
        try {
            hwInterface.disableServo(servo);
        }
        catch (HardwareInterfaceException e) {
            e.printStackTrace();
        }
    }

    private void setServoSlow(int servo, float fromVal, float toVal, int msPerTick) {
        float inc = (float) 0.01;
        int direction;

        if (toVal > fromVal)
            direction = 1;
        else
            direction = -1;

        for (float currentVal = fromVal; Math.abs(currentVal - toVal) > inc; currentVal += inc * direction) {
            setServoVal(servo, currentVal);
            delayMs(msPerTick);
        }

        setServoVal(servo, toVal);
    }

    public void delayMs(int ms) {
        try {
            Thread.currentThread().sleep(ms);
        }
        catch (InterruptedException e) {
        }
    }


    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new ServoTester().setVisible(true);
            }
        });
    }

    /**
     * called when device added
     */
    public void onAdd() {
        log.info("device added, opening it");
        try {
            hwInterface.open();
        }
        catch (HardwareInterfaceException e) {
            log.warning(e.getMessage());
        }
    }

    public void onRemove() {
        log.info("device removed, closing it");
        if (hwInterface != null && hwInterface.isOpen())
            hwInterface.close();
    }

    public float getStopVal() {
        return stopVal;
    }

    public void setStopVal(float stopVal) {
        this.stopVal = stopVal;
    }

    public float getStartVal() {
        return startVal;
    }

    public void setStartVal(float startVal) {
        setServoSlow(1, this.startVal, startVal, delay);
        this.startVal = startVal;
    }

}


