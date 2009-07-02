package ch.unizh.ini.jaer.projects.tellurideomni;

import de.thesycon.usbio.PnPNotifyInterface;
import de.thesycon.usbio.PnPNotify;

import java.util.logging.Logger;
import java.util.Arrays;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import net.sf.jaer.hardwareinterface.ServoInterface;
import net.sf.jaer.hardwareinterface.HardwareInterfaceException;
import net.sf.jaer.hardwareinterface.usb.silabs.SiLabsC8051F320_USBIO_ServoController;

import javax.swing.event.ChangeEvent;
import javax.swing.*;

/**
 * User: jauerbac
 * Date: Jul 1, 2009
 * Time: 5:50:57 PM
 */
public class ServoTester extends javax.swing.JFrame implements PnPNotifyInterface {
    final int MAX_SLIDER = 1000;
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
    JButton actuateServo1Button, actuateServo2Button, actuateServo3Button;
    JButton stopServo1Button, stopServo2Button, stopServo3Button;

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

                actuateServo1Button = new JButton("Actuate Servo 1");
                actuateServo1Button.setBounds(25, 60, 150, 30);
                actuateServo1Button.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent event) {
                        log.info("Actuating servo 1");
                        setServoVal(0, startVal);
                    }
                });

               actuateServo2Button = new JButton("Actuate Servo 2");
               actuateServo2Button.setBounds(225, 60, 150, 30);
               actuateServo2Button.addActionListener(new ActionListener() {
                   public void actionPerformed(ActionEvent event) {
                       log.info("Actuating servo 2");
                        setServoVal(1, startVal);
                  }
               });

               actuateServo3Button = new JButton("Actuate Servo 3");
               actuateServo3Button.setBounds(425, 60, 150, 30);
               actuateServo3Button.addActionListener(new ActionListener() {
                   public void actionPerformed(ActionEvent event) {
                       log.info("Actuating servo 3");
                        setServoVal(2, startVal);
                  }
               });

                stopServo1Button = new JButton("Stop Servo 1");
                        stopServo1Button.setBounds(25, 120, 150, 30);
                        stopServo1Button.addActionListener(new ActionListener() {
                            public void actionPerformed(ActionEvent event) {
                                log.info("Stopping servo 1");
                                setServoVal(0, stopVal);
                            }
                        });

               stopServo2Button = new JButton("Stop Servo 2");
                        stopServo2Button.setBounds(225, 120, 150, 30);
                        stopServo2Button.addActionListener(new ActionListener() {
                            public void actionPerformed(ActionEvent event) {
                                log.info("Stopping servo 2");
                                setServoVal(1, stopVal);
                            }
                        });

                 stopServo3Button = new JButton("Stop Servo 3");
                        stopServo3Button.setBounds(425, 120, 150, 30);
                        stopServo3Button.addActionListener(new ActionListener() {
                            public void actionPerformed(ActionEvent event) {
                                log.info("Stopping servo 3");
                                setServoVal(2, stopVal);
                            }
                        });

                panel.add(actuateServo1Button);
                panel.add(actuateServo2Button);
                panel.add(actuateServo3Button);
                panel.add(stopServo1Button);
                panel.add(stopServo2Button);
                panel.add(stopServo3Button);

    }


    public void shoot() {
        setServoVal(1, stopVal);

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



    void setServo(int servo, ChangeEvent evt) {
        if (hwInterface == null) {
            log.warning("null hardware interface");
            return;
        }
        float f = (float) ((JSlider) evt.getSource()).getValue() / MAX_SLIDER;
        try {
            hwInterface.setServoValue(servo, f);
        }
        catch (HardwareInterfaceException e) {
            e.printStackTrace();
        }
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


