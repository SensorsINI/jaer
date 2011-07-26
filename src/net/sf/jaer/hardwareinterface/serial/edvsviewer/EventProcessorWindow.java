package net.sf.jaer.hardwareinterface.serial.edvsviewer;

import java.awt.Color;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.util.LinkedList;
import java.util.prefs.Preferences;

import javax.swing.JApplet;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class EventProcessorWindow extends JApplet {

    static final long serialVersionUID = 1;
    private JFrame frame = new JFrame("DVS128 Viewer");
    private ImagePanel imagePanel;
    public final int DVSPixelSizeX = 128;
    public final int DVSPixelSizeY = 128;
    public final int pixelTotal = (DVSPixelSizeX * DVSPixelSizeY);
    private JSlider brightnessSlider = new JSlider(0, 255, 32);
    private JSlider decayFactorSlider = new JSlider(0, 100, 30);
    private JSlider PWMSlider = new JSlider(0, 10000, 5000);
    private JCheckBox flipX = new JCheckBox();
    private JCheckBox flipY = new JCheckBox();
    private JCheckBox swapXY = new JCheckBox();
    private JCheckBox displayOutputCheckBox = new JCheckBox();
    private JCheckBox displayBWOnlyCheckBox = new JCheckBox();
    private JCheckBox displayFoveated = new JCheckBox();
    private JToggleButton eventLogToggleButton = new JToggleButton("logE");
    private JToggleButton biasSettingsToggleButton = new JToggleButton("bias");
    private JToggleButton sendEventsToggleButton = new JToggleButton("!E0 !E+/-");
    private JToggleButton sendEPSToggleButton = new JToggleButton("!R+/-");
    private JToggleButton sendTrackHighFLEDToggleButton = new JToggleButton("!T+/-");
    private JToggleButton sendAutoEPSToggleButton = new JToggleButton("!A+/-");
    private final int pixelDisplayScaleFactor = 2;
    private BufferedImage image = new BufferedImage(DVSPixelSizeX, DVSPixelSizeY, BufferedImage.TYPE_INT_RGB);
    private JLabel pwmDisplay = new JLabel();
    private JLabel kepsCounterJava = new JLabel();
    private JLabel kepsCounterDVSTotal = new JLabel();
    private JLabel kepsCounterDVSOn = new JLabel();
    private JLabel kepsCounterDVSOff = new JLabel();
    private JLabel kepsText = new JLabel("keps");
    private JLabel pixelSelectText = new JLabel();
    private DisplayThread displayThread = null;
    private IOThread terminalThread = null;
    private Tools_BiasPanel biasPanel = null;
    private int imagePixelOn[][] = new int[DVSPixelSizeX][DVSPixelSizeY];
    private int imagePixelOff[][] = new int[DVSPixelSizeX][DVSPixelSizeY];
    private final int displayTimeDeltaMS = 20;
    private long lastCaptureTimeNS = 0;
    private long eventCounter = 0;
    private double epsJava = 0.0;
    private double avgEPSJava = 0.0;
    private int[] trackHighFLEDX = new int[4];				// position X
    private int[] trackHighFLEDY = new int[4];				// position Y
    private int[] trackHighFLEDC = new int[4];				// certainty
    private HWPort port;
    private int eventLogMemorySize = 100000 * 60;				// 100keps for 1 minute
    private int eventLogMemoryPointer = -1;
    private long eventLogMemory[] = new long[eventLogMemorySize];
    private File eventLogFile = null;
    private FileOutputStream eventLogFileOutStream = null;
    private long eventLogTimeReference = 0;
    private LinkedList<EventProcessor> eventProcessorList = new LinkedList<EventProcessor>();

    private void saveConfig() {
        Preferences prefs = Preferences.userNodeForPackage(EmbeddedDVS128Viewer.class);
        prefs.putInt("ViewerDimensionWidth", (int) (frame.getSize().getWidth()));
        prefs.putInt("ViewerDimensionHeight", (int) (frame.getSize().getHeight()));
        prefs.putInt("ViewerFrameLocationX", frame.getLocation().x);
        prefs.putInt("ViewerFrameLocationY", frame.getLocation().y);

        prefs.putInt("brightnessSlider", brightnessSlider.getValue());
        prefs.putInt("decayFactorSlider", decayFactorSlider.getValue());
        prefs.putInt("LEDblinkingSlider", PWMSlider.getValue());


        prefs.putBoolean("flipX", flipX.isSelected());
        prefs.putBoolean("flipY", flipY.isSelected());
        prefs.putBoolean("swapXY", swapXY.isSelected());
        prefs.putBoolean("displayOutput", displayOutputCheckBox.isSelected());
        prefs.putBoolean("displayBWOnly", displayBWOnlyCheckBox.isSelected());
        prefs.putBoolean("displayFoveated", displayFoveated.isSelected());
    }

    private void loadConfig() {
        Preferences prefs = Preferences.userNodeForPackage(EmbeddedDVS128Viewer.class);

        frame.setSize(prefs.getInt("ViewerDimensionWidth", 80 + pixelDisplayScaleFactor * DVSPixelSizeX),
                prefs.getInt("ViewerDimensionHeight", 10 + pixelDisplayScaleFactor * DVSPixelSizeY + 34));
        frame.setLocation(prefs.getInt("ViewerFrameLocationX", 10),
                prefs.getInt("ViewerFrameLocationY", 10));

        brightnessSlider.setValue(prefs.getInt("brightnessSlider", 32));
        decayFactorSlider.setValue(prefs.getInt("decayFactorSlider", 30));
        PWMSlider.setValue(prefs.getInt("LEDblinkingSlider", 30));

        flipX.setSelected(prefs.getBoolean("flipX", false));
        flipY.setSelected(prefs.getBoolean("flipY", false));
        swapXY.setSelected(prefs.getBoolean("swapXY", false));
        displayOutputCheckBox.setSelected(prefs.getBoolean("displayOutput", true));
        displayBWOnlyCheckBox.setSelected(prefs.getBoolean("displayBWOnly", true));
        displayFoveated.setSelected(prefs.getBoolean("displayFoveated", false));
    }
    private int eventProcessorDisplayY = 20 + pixelDisplayScaleFactor * DVSPixelSizeY;

    private void addEventProcessor(EventProcessor e) {
        Container cp = getContentPane();
        e.init();
        eventProcessorList.add(e);
        cp.add(e.isActive);
        e.isActive.setBounds(20, eventProcessorDisplayY, 200, 20);
        cp.add(e.callBackButton);
        e.callBackButton.setBounds(240, eventProcessorDisplayY, 60, 20);

        eventProcessorDisplayY += 30;
    }

    public EventProcessorWindow(HWPort port, IOThread terminalThread) {

        this.port = port;
        this.terminalThread = terminalThread;

        if (terminalThread != null) {
            terminalThread.registerEventDisplayWindow(this);
        }

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().add(this);
        frame.setSize(80 + pixelDisplayScaleFactor * DVSPixelSizeX, 10 + pixelDisplayScaleFactor * DVSPixelSizeY + 34);
        frame.setResizable(true);				// allow changes in size
        frame.setName("DVSViewerWindow");

        Container cp = getContentPane();
        cp.setLayout(null);

        imagePanel = new ImagePanel();
        cp.add(imagePanel);
        imagePanel.setBounds(5, 5, pixelDisplayScaleFactor * DVSPixelSizeX, pixelDisplayScaleFactor * DVSPixelSizeY);

        imagePanel.addMouseListener(new ml());

        cp.add(eventLogToggleButton);
        eventLogToggleButton.setBounds(20 + pixelDisplayScaleFactor * DVSPixelSizeX, 10, 60, 20);

        ChangeListener cl = new SliderListener();
        cp.add(brightnessSlider);
        brightnessSlider.setOrientation(JSlider.VERTICAL);
        brightnessSlider.setBounds(20 + pixelDisplayScaleFactor * DVSPixelSizeX, 40, 20, 200);
        brightnessSlider.addChangeListener(cl);
        cp.add(decayFactorSlider);
        decayFactorSlider.setOrientation(JSlider.VERTICAL);
        decayFactorSlider.setBounds(40 + pixelDisplayScaleFactor * DVSPixelSizeX, 40, 20, 200);
        decayFactorSlider.addChangeListener(cl);

        cp.add(PWMSlider);
        PWMSlider.setOrientation(JSlider.VERTICAL);
        PWMSlider.setBounds(60 + pixelDisplayScaleFactor * DVSPixelSizeX, 40, 20, 200);
        PWMSlider.addChangeListener(cl);


        cp.add(pwmDisplay);
        pwmDisplay.setBounds(15 + pixelDisplayScaleFactor * DVSPixelSizeX, 235, 75, 20);
        pwmDisplay.setHorizontalAlignment(SwingConstants.RIGHT);

        cp.add(kepsCounterJava);
        kepsCounterJava.setBounds(15 + pixelDisplayScaleFactor * DVSPixelSizeX, 250, 35, 20);
        kepsCounterJava.setHorizontalAlignment(SwingConstants.RIGHT);
        cp.add(kepsText);
        kepsText.setBounds(55 + pixelDisplayScaleFactor * DVSPixelSizeX, 250, 35, 20);

        cp.add(kepsCounterDVSTotal);
        kepsCounterDVSTotal.setBounds(15 + pixelDisplayScaleFactor * DVSPixelSizeX, 265, 42, 20);
        kepsCounterDVSTotal.setHorizontalAlignment(SwingConstants.RIGHT);

        cp.add(kepsCounterDVSOn);
        kepsCounterDVSOn.setBounds(15 + pixelDisplayScaleFactor * DVSPixelSizeX, 280, 42, 20);
        kepsCounterDVSOn.setForeground(new Color(0, 128, 0));
        kepsCounterDVSOn.setHorizontalAlignment(SwingConstants.RIGHT);

        cp.add(kepsCounterDVSOff);
        kepsCounterDVSOff.setBounds(15 + pixelDisplayScaleFactor * DVSPixelSizeX, 295, 42, 20);
        kepsCounterDVSOff.setForeground(Color.RED);
        kepsCounterDVSOff.setHorizontalAlignment(SwingConstants.RIGHT);

        cp.add(pixelSelectText);
        pixelSelectText.setBounds(15 + pixelDisplayScaleFactor * DVSPixelSizeX, 310, 70, 20);

        cp.add(displayOutputCheckBox);
        displayOutputCheckBox.setSelected(false);
        displayOutputCheckBox.setText("OE");
        displayOutputCheckBox.setBounds(20 + pixelDisplayScaleFactor * DVSPixelSizeX, 350, 80, 20);

        cp.add(displayBWOnlyCheckBox);
        displayBWOnlyCheckBox.setSelected(false);
        displayBWOnlyCheckBox.setText("B/W");
        displayBWOnlyCheckBox.setBounds(20 + pixelDisplayScaleFactor * DVSPixelSizeX, 370, 80, 20);

        cp.add(flipX);
        cp.add(flipY);
        cp.add(swapXY);
        flipX.setText("fX");
        flipY.setText("fY");
        swapXY.setText("X<=>Y");
        flipX.setBounds(20 + pixelDisplayScaleFactor * DVSPixelSizeX, 390, 60, 20);
        flipY.setBounds(20 + pixelDisplayScaleFactor * DVSPixelSizeX, 410, 60, 20);
        swapXY.setBounds(20 + pixelDisplayScaleFactor * DVSPixelSizeX, 430, 65, 20);

        cp.add(displayFoveated);
        displayFoveated.setSelected(false);
        displayFoveated.setText("Foveated");
        displayFoveated.setBounds(20 + pixelDisplayScaleFactor * DVSPixelSizeX, 450, 80, 20);

        cp.add(biasSettingsToggleButton);
        cp.add(sendEventsToggleButton);
        cp.add(sendEPSToggleButton);
        cp.add(sendTrackHighFLEDToggleButton);
        cp.add(sendAutoEPSToggleButton);

        Insets buttonMarginsTight = new Insets(0, 0, 0, 0);	// border top, left, bottom, right for tight boxes
        sendEventsToggleButton.setMargin(buttonMarginsTight);

        biasSettingsToggleButton.setBounds(20 + pixelDisplayScaleFactor * DVSPixelSizeX, 490, 60, 20);
        sendEventsToggleButton.setBounds(20 + pixelDisplayScaleFactor * DVSPixelSizeX, 510, 60, 20);
        sendEPSToggleButton.setBounds(20 + pixelDisplayScaleFactor * DVSPixelSizeX, 550, 60, 20);
        sendTrackHighFLEDToggleButton.setBounds(20 + pixelDisplayScaleFactor * DVSPixelSizeX, 570, 60, 20);
        sendAutoEPSToggleButton.setBounds(20 + pixelDisplayScaleFactor * DVSPixelSizeX, 590, 60, 20);

        ActionListener bl = new ButtonListener();
        biasSettingsToggleButton.addActionListener(bl);
        eventLogToggleButton.addActionListener(bl);
        sendEventsToggleButton.addActionListener(bl);
        sendEPSToggleButton.addActionListener(bl);
        sendTrackHighFLEDToggleButton.addActionListener(bl);
        sendAutoEPSToggleButton.addActionListener(bl);
        displayOutputCheckBox.addActionListener(bl);

        imagePanel.resetArray();

        loadConfig();

        // display what happened
        frame.setVisible(true);

        displayThread = new DisplayThread();
        displayThread.start();


//		addEventProcessor(new EP_CrossTracker());
//		addEventProcessor(new EP_CrossTrackerBGFilter());

        addEventProcessor(new EP_GridTracker());

        addEventProcessor(new EP_PencilLineTracker());
//		addEventProcessor(new EP_ParallelLineTracker());
//		addEventProcessor(new EP_IncrementalLineTracker());
//		addEventProcessor(new EP_LineTracker360());

//		addEventProcessor(new EP_GlobalOpticFlowX());
//		addEventProcessor(new EP_TobiasBoxTracker());
        addEventProcessor(new EP_PigTracker());
    }

    public void close() {
        saveConfig();

        if (eventLogFileOutStream != null) {
            try {
                eventLogFileOutStream.close();
            } catch (Exception e) {
                System.out.println("Exception closing logfile!");
            }
            System.out.println("LogFile closed");
            eventLogFileOutStream = null;
        }

        if (biasPanel != null) {
            biasPanel.close();
        }
        displayThread.terminate();
        frame.dispose();
    }

    public void processSpecialData(String specialData) {
        //		System.out.println("Received special Data: " + specialData[0] + " " + specialData[1] + " " + specialData[2] + " " + specialData[3] + " " + specialData[4]);
        //		System.out.println("Received special Data: " + specialData[0] + " " + specialData[1] + " " + specialData[2] + " " + specialData[3] + " " + specialData[4] + " " + specialData[5] + " " + specialData[6] + " " + specialData[7]);
//		System.out.println("Received special data of length: " + specialData.length());

        if ((specialData.length()) == 9) {
//			final double timeFactorDVS = 1.0 / (2.0*0.065536);			// this is for 2us resolution and full 2^16 bits timestamp
            final double timeFactorDVS = 1.0 / (2.0 * 0.05);				// this is for 2us resolution total of 100ms timestamp

            int eventCountTotalDVS = (((specialData.charAt(0)) - 32) << 12) | (((specialData.charAt(1)) - 32) << 6) | ((specialData.charAt(2)) - 32);
            double epsTotalDVS = Math.round(((double) eventCountTotalDVS) * timeFactorDVS);

            int eventCountOnDVS = (((specialData.charAt(3)) - 32) << 12) | (((specialData.charAt(4)) - 32) << 6) | ((specialData.charAt(5)) - 32);
            double epsOnDVS = Math.round(((double) eventCountOnDVS) * timeFactorDVS);
            int eventCountOffDVS = (((specialData.charAt(6)) - 32) << 12) | (((specialData.charAt(7)) - 32) << 6) | ((specialData.charAt(8)) - 32);
            double epsOffDVS = Math.round(((double) eventCountOffDVS) * timeFactorDVS);


//			System.out.printf("Received event count: %8.3fkEPS\n", (eps/1000.0));
            String s;
            s = String.format("%7.2f", (epsTotalDVS / 1000.0));
            kepsCounterDVSTotal.setText(s);

            s = String.format("%7.2f", (epsOnDVS / 1000.0));
            kepsCounterDVSOn.setText(s);

            s = String.format("%7.2f", (epsOffDVS / 1000.0));
            kepsCounterDVSOff.setText(s);
        }

        if ((specialData.length()) == 5) {
            int biasID = ((specialData.charAt(0)) - 32);
            int biasValue = (((specialData.charAt(1)) - 32) << 18) | (((specialData.charAt(2)) - 32) << 12) | (((specialData.charAt(3)) - 32) << 6) | ((specialData.charAt(4)) - 32);
            System.out.printf("Bias %2d = %8d!\n", biasID, biasValue);
            if (biasPanel != null) {
                biasPanel.setBiasFromChip(biasID, biasValue);
            }
        }

        if ((specialData.length()) == 12) {
            for (int n = 0; n < 4; n++) {
                trackHighFLEDX[n] = (specialData.charAt((3 * n) + 2));
                trackHighFLEDY[n] = (specialData.charAt((3 * n) + 1));
                trackHighFLEDC[n] = (specialData.charAt((3 * n) + 0));

                if (flipX.isSelected()) {
                    trackHighFLEDX[n] = 127 - trackHighFLEDX[n];
                }
                if (flipY.isSelected()) {
                    trackHighFLEDY[n] = 127 - trackHighFLEDY[n];
                }
                if (swapXY.isSelected()) {
                    int tmp;
                    tmp = trackHighFLEDY[n];
                    trackHighFLEDY[n] = trackHighFLEDX[n];
                    trackHighFLEDX[n] = tmp;
                }

//				if (n==1) {
//					System.out.printf("%3d,%3d (%6d)   ", trackHighFLEDX[n], trackHighFLEDY[n], trackHighFLEDC[n]);
//				}

            }
//			System.out.printf("\n");
//			System.out.printf("Tracking: %3d / %3d\n", trackHighFLEDX, trackHighFLEDY);
        }

        for (EventProcessor e : eventProcessorList) {
            if (e.isActive.isSelected()) {
                e.processSpecialData(specialData);
            }
        }

    }

    public void processNewEvent(int eventX, int eventY, int eventP) {

        eventCounter++;
        long currentCaptureTime = System.nanoTime();
        if (currentCaptureTime - lastCaptureTimeNS > 1e7) {
            epsJava = ((double) eventCounter) / ((double) (currentCaptureTime - lastCaptureTimeNS) / ((double) 1e9));
            avgEPSJava = 0.8 * avgEPSJava + 0.2 * epsJava;
            kepsCounterJava.setText(String.format("%6.1f", (avgEPSJava / 1000.0)));
            eventCounter = 0;
            lastCaptureTimeNS = currentCaptureTime;
        }

        /* *************************************************************************** possibly log raw event to memory */
        if (eventLogMemoryPointer >= 0) {
            long time = (System.nanoTime() / 1000) - eventLogTimeReference;
            long event = (time << 16) + (eventX << 8) + (eventP << 7) + (eventY);
            eventLogMemory[eventLogMemoryPointer] = event;
            eventLogMemoryPointer++;
            if (eventLogMemoryPointer == eventLogMemorySize) {
                eventLogToggleButton.doClick();				// switch logging off!
            }
        }


        /* *************************************************************************** pre-process event (flip X,Y) */
        if (flipX.isSelected()) {
            eventX = 127 - eventX;
        }
        if (flipY.isSelected()) {
            eventY = 127 - eventY;
        }
        if (swapXY.isSelected()) {
            int tmp;
            tmp = eventY;
            eventY = eventX;
            eventX = tmp;
        }

        if (displayBWOnlyCheckBox.isSelected()) {
            eventP = 1;
        }


        if (displayFoveated.isSelected()) {
            int fromX, toX, fromY, toY;
            int eX, eY, wX, wY;

            fromX = eventX;
            toX = eventX;
            fromY = eventY;
            toY = eventY;
            wX = 1;
            wY = 1;

            if (((eventX >= 0) && (eventX < 32)) || //   0.. 32  = 2x16
                    ((eventX >= 96) && (eventX < 128))) {					//  96..128  = 2x16
                fromX = (eventX & 0xF0);
                toX = fromX + 15;
                wX = 16;
            }
            if (((eventX >= 32) && (eventX < 48)) || //  32.. 48  = 2x 8
                    ((eventX >= 80) && (eventX < 96))) {					//  80.. 96  = 2x 8
                fromX = (eventX & 0xF8);
                toX = fromX + 7;
                wX = 8;
            }
            if ((eventX >= 48) && (eventX < 80)) {					//  48.. 80  =  8x4
                fromX = (eventX & 0xFC);
                toX = fromX + 3;
                wX = 4;
            }


            if (((eventY >= 0) && (eventY < 32)) || //   0.. 32  = 2x16
                    ((eventY >= 96) && (eventY < 128))) {					//  96..128  = 2x16
                fromY = (eventY & 0xF0);
                toY = fromY + 15;
                wY = 16;
            }
            if (((eventY >= 32) && (eventY < 48)) || //  32.. 48  = 2x 8
                    ((eventY >= 80) && (eventY < 96))) {					//  80.. 96  = 2x 8
                fromY = (eventY & 0xF8);
                toY = fromY + 7;
                wY = 8;
            }
            if ((eventY >= 48) && (eventY < 80)) {					//  48.. 80  =  8x4
                fromY = (eventY & 0xFC);
                toY = fromY + 3;
                wY = 4;
            }


            if (eventP == 1) {
                for (eX = fromX; eX <= toX; eX++) {
                    for (eY = fromY; eY <= toY; eY++) {
                        imagePixelOn[eX][eY] += (int) Math.ceil(((double) brightnessSlider.getValue()) / ((double) (wX * wY)));
                        if (imagePixelOn[eX][eY] > 0xFF) {
                            imagePixelOn[eX][eY] = 0xFF;
                        }
                    }
                }
            } else {
                for (eX = fromX; eX <= toX; eX++) {
                    for (eY = fromY; eY <= toY; eY++) {
                        imagePixelOff[eX][eY] += (int) Math.ceil(((double) brightnessSlider.getValue()) / ((double) (wX * wY)));
                        if (imagePixelOff[eX][eY] > 0xFF) {
                            imagePixelOff[eX][eY] = 0xFF;
                        }
                    }
                }
            }
        } else {

            /* *************************************************************************** send event to all registered processors */
            int r;

            for (EventProcessor e : eventProcessorList) {
                if (e.isActive.isSelected()) {

                    r = e.processNewEvent(eventX, eventY, eventP);

                    if (r == 1) {				// watch out, this modifies events before calling consecutive processors
                        eventP = 1;
                    }
                    if (r == 2) {
                        eventP = 0;
                    }
                }
            }

            /* *************************************************************************** display event */
            if (eventP == 1) {
                imagePixelOn[eventX][eventY] += brightnessSlider.getValue();
                if (imagePixelOn[eventX][eventY] > 0xFF) {
                    imagePixelOn[eventX][eventY] = 0xFF;
                }
            } else {
                imagePixelOff[eventX][eventY] += brightnessSlider.getValue();
                if (imagePixelOff[eventX][eventY] > 0xFF) {
                    imagePixelOff[eventX][eventY] = 0xFF;
                }
            }
        }


    }

    private boolean openLogfile(String fileName) {
        try {
            eventLogFile = new File(fileName);
            if (eventLogFile.exists()) {
                eventLogFileOutStream = new FileOutputStream(eventLogFile, true);
                System.out.println("Appending to logfile " + fileName);
            } else {
                eventLogFile.createNewFile();
                eventLogFileOutStream = new FileOutputStream(eventLogFile);
                System.out.println("Creating new logfile " + fileName);
            }
            if (eventLogFileOutStream == null) {
                System.out.println("Error... logFile == null... how comes?");
            }
        } catch (Exception e) {
            return (false);
        }
        return (true);
    }

    private class ImagePanel extends JPanel {

        static final long serialVersionUID = 1;

        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.drawImage(image, 0, 0, pixelDisplayScaleFactor * DVSPixelSizeX, pixelDisplayScaleFactor * DVSPixelSizeY, this);


            if (sendTrackHighFLEDToggleButton.isSelected()) {
                for (int n = 1; n < 3; n++) {
                    int d = trackHighFLEDC[n];
//					if (d>8) {
                    g.setColor(new Color(64 * n, 64 * (3 - n), 255));
                    g.drawLine(((int) (4 * trackHighFLEDX[n]) - 20), ((int) (4 * trackHighFLEDY[n]) - 20), ((int) (4 * trackHighFLEDX[n]) + 20), ((int) (4 * trackHighFLEDY[n]) + 20));
                    g.drawLine(((int) (4 * trackHighFLEDX[n]) - 20), ((int) (4 * trackHighFLEDY[n]) + 20), ((int) (4 * trackHighFLEDX[n]) + 20), ((int) (4 * trackHighFLEDY[n]) - 20));
                    g.fillArc(((int) (4 * trackHighFLEDX[n]) - d), ((int) (4 * trackHighFLEDY[n]) - d), 2 * d, 2 * d, 0, 360);
//					}
                }
            }

            for (EventProcessor e : eventProcessorList) {
                if (e.isActive.isSelected()) {
                    e.paintComponent(g);
                }
            }

        }

        public void resetArray() {
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    (imagePixelOn[x][y]) = 0;
                }
            }
        }
    }

    public class ml implements MouseListener {

        public void mousePressed(MouseEvent e) {
//	       saySomething("Mouse pressed; # of clicks: " + e.getClickCount(), e);
//	       System.out.println("Mouse clicked (" + e.getButton() + ") at " + e.getPoint() + "\n");

            if (e.getButton() == 1) {
                int px, py, s;
                px = e.getX() / 4;
                py = e.getY() / 4;

                if (swapXY.isSelected()) {
                    s = px;
                    px = py;
                    py = s;
                }
                if (flipX.isSelected()) {
                    px = 127 - px;
                }
                if (flipY.isSelected()) {
                    py = 127 - py;
                }

                port.sendCommand("!C" + px + "," + py);
                pixelSelectText.setText("P: " + px + " : " + py);
//	    	   System.out.println("!C" + px + "," + py);
            } else {
                port.sendCommand("!C0,0,127,127");
                pixelSelectText.setText("");
            }
        }

        public void mouseReleased(MouseEvent e) {
//	       saySomething("Mouse released; # of clicks: " + e.getClickCount(), e);
        }

        public void mouseEntered(MouseEvent e) {
//	       saySomething("Mouse entered", e);
        }

        public void mouseExited(MouseEvent e) {
//	       saySomething("Mouse exited", e);
        }

        public void mouseClicked(MouseEvent e) {
//	       saySomething("Mouse clicked (# of clicks: " + e.getClickCount() + ")", e);
        }
//	    void saySomething(String eventDescription, MouseEvent e) {
//	        System.out.println(eventDescription + " detected on "
//	                        + e.getComponent().getClass().getName()
//	                        + ".\n");
//	    }
    }

    private class DisplayThread extends Thread {

        private boolean threadRunning;

        public DisplayThread() {
        }

        public void run() {
            threadRunning = true;

            long lastPixelTime = 0;

            while (threadRunning) {

                long currentTime = System.currentTimeMillis();
                long timeMissingMS = (lastPixelTime + displayTimeDeltaMS) - currentTime;
                if (timeMissingMS > 0) {
                    try {
                        Thread.sleep(timeMissingMS);
                    } catch (Exception e) {/* ** */

                    }
                }

                if (System.currentTimeMillis() > (lastPixelTime + displayTimeDeltaMS)) {
                    lastPixelTime = System.currentTimeMillis();
//System.out.println("Called Decay at " + System.currentTimeMillis());
                    double decayFactor = 1.0 - ((double) decayFactorSlider.getValue()) / 100.0;

                    if (displayBWOnlyCheckBox.isSelected()) {
                        for (int y = 0; y < 128; y++) {
                            for (int x = 0; x < 128; x++) {
                                image.setRGB(x, y, ((imagePixelOn[x][y]) << (16)) + ((imagePixelOn[x][y]) << 8) + ((imagePixelOn[x][y])));
                                imagePixelOn[x][y] = (int) Math.floor(decayFactor * imagePixelOn[x][y]);
                            }
                        }
                    } else {
                        for (int y = 0; y < 128; y++) {
                            for (int x = 0; x < 128; x++) {
                                image.setRGB(x, y, ((imagePixelOn[x][y]) << (16)) + ((imagePixelOff[x][y]) << 8));
                                imagePixelOn[x][y] = (int) Math.floor(decayFactor * imagePixelOn[x][y]);
                                imagePixelOff[x][y] = (int) Math.floor(decayFactor * imagePixelOff[x][y]);
                            }
                        }
                    }
                    imagePanel.repaint();
                }

            }
        }

        public void terminate() {
            threadRunning = false;
        }
    }

    private class SliderListener implements ChangeListener {

        public void stateChanged(ChangeEvent e) {
            brightnessSlider.setToolTipText("brightness: " + brightnessSlider.getValue());
            decayFactorSlider.setToolTipText("decayFactor: " + (decayFactorSlider.getValue() / 100.0));

            if (e.getSource() == PWMSlider) {
                PWMSlider.setToolTipText("t: " + (PWMSlider.getValue() + "us"));
                pwmDisplay.setText(String.format("%6dus", (PWMSlider.getValue())));

                port.sendCommand("!PWMC=" + PWMSlider.getValue());
                try {
                    Thread.sleep(2);
                } catch (Exception e2) {
                }
                port.sendCommand("!PWMS=" + (PWMSlider.getValue() / 2));
            }

        }
    }

    private class ButtonListener implements ActionListener {

        public void actionPerformed(ActionEvent e) {

            saveConfig();

            if (e.getSource() == biasSettingsToggleButton) {
                if (biasPanel != null) {
                    biasPanel.close();
                }
                biasPanel = null;

                if (biasSettingsToggleButton.isSelected()) {
                    biasPanel = new Tools_BiasPanel(terminalThread);
                }
            }

            if (e.getSource() == sendEventsToggleButton) {
                if (port != null) {
                    if (sendEventsToggleButton.isSelected()) {
                        port.sendCommand("!E0");					// set to binary data format, no time stamp!
                        port.sendCommand("E+");
                    } else {
                        port.sendCommand("E-");
                    }
                }
            }

            if (e.getSource() == sendEPSToggleButton) {
                if (port != null) {
                    if (sendEPSToggleButton.isSelected()) {
                        port.sendCommand("!R+");
                    } else {
                        port.sendCommand("!R-");
                    }
                }
            }

            if (e.getSource() == sendTrackHighFLEDToggleButton) {
                if (port != null) {
                    if (sendTrackHighFLEDToggleButton.isSelected()) {
                        port.sendCommand("!T+");
                    } else {
                        port.sendCommand("!T-");
                    }
                }
            }

            if (e.getSource() == sendAutoEPSToggleButton) {
                if (port != null) {
                    if (sendAutoEPSToggleButton.isSelected()) {
                        port.sendCommand("!A+");
                    } else {
                        port.sendCommand("!A-");
                    }
                }
            }

            if (e.getSource() == displayOutputCheckBox) {
                if (terminalThread != null) {
                    terminalThread.setOutputOnScreenEnabled(displayOutputCheckBox.isSelected());
                }
            }

            if (e.getSource() == eventLogToggleButton) {
                if (eventLogToggleButton.isSelected()) {

                    eventLogMemoryPointer = 0;	// start logging
                    eventLogTimeReference = (System.nanoTime() / 1000);

                } else {
                    long lastLog = eventLogMemoryPointer;
                    eventLogMemoryPointer = -1; // stop logging NOW (otherwise other threads save data) ;)

                    if (openLogfile("C:/Documents and Settings/conradt/Desktop/Logfile.txt") == false) {
                        if (openLogfile("C:/Logfile.txt") == false) {
                            if (openLogfile("~/Logfile.txt") == false) {
                                if (openLogfile("/tmp/Logfile.txt") == false) {
                                    System.out.println("Exception opening logfile!");
                                }
                            }
                        }
                    }

                    try {
                        for (int n = 0; n < lastLog; n++) {

                            long time = eventLogMemory[n] >> 16;
                            int eventX = ((int) ((eventLogMemory[n] >> 8) & 0x7F));
                            int eventP = ((int) ((eventLogMemory[n] >> 7) & 0x01));
                            int eventY = ((int) ((eventLogMemory[n]) & 0x7F));

                            String os = String.format("%3d %3d %1d %10d\n", eventX, eventY, eventP, time);
                            eventLogFileOutStream.write(os.getBytes());
                        }
                    } catch (Exception e2) { /* */ }


                    try {
                        eventLogFileOutStream.close();
                    } catch (Exception e2) { /* */ }
                    eventLogFileOutStream = null;
                }
            }


        }
    }
}
